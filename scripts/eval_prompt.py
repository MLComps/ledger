"""
Ledger prompt accuracy evaluator.

Sends each case from eval_corpus.json to the Gemma API (Google AI Studio),
compares the response against expected fields, and reports per-field and
overall accuracy.

Usage:
    uv run eval_prompt.py --api-key YOUR_KEY
    uv run eval_prompt.py --api-key YOUR_KEY --case-id S01  # single case

Requirements:
    GEMINI_API_KEY env var OR --api-key flag
    pip install google-genai  (already in pyproject.toml)
"""

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

CORPUS_PATH = Path(__file__).parent / "eval_corpus.json"
DEFAULT_CURRENCY = "KES"

SYSTEM_PROMPT_TEMPLATE = """You are Ledger, an offline bookkeeping assistant for market vendors.

For every input — whether typed text, spoken audio, or image — extract any financial transaction or stock update mentioned, then respond with ONLY a valid JSON object. Output no other text.

JSON schema:
{{
  "action": "add_transaction" | "update_stock" | "get_health" | "clarify" | "unknown",
  "transactions": [
    {{
      "transaction_type": "sale" | "purchase" | "expense" | "income",
      "item": "product or service name",
      "amount": <number>,
      "currency": "<3-letter code e.g. KES RWF GHS NGN ZAR>",
      "quantity": <number>,
      "unit": "<kg pieces sack litre etc>",
      "cost": <cost-of-goods, 0 if unknown>,
      "confidence": "high" | "medium" | "low"
    }}
  ],
  "stock_updates": [
    {{ "item": "<name>", "quantity_delta": <number>, "unit": "<unit>" }}
  ],
  "question": "<one short question in the user's language — only present when action=clarify>",
  "message": "<one sentence in the user's language confirming what was recorded — only present when action is not clarify>"
}}

Rules:
- action="add_transaction" for sales, purchases, income, or expenses
- action="update_stock" for restocking or stock level changes
- action="get_health" when asked for totals, summary, or financial health
- action="unknown" if no financial action is found
- action="clarify" ONLY when the amount is completely absent AND cannot be inferred from context, OR when the direction (receiving vs paying money) is genuinely ambiguous with no context clues; do NOT clarify for unclear item names — use confidence="low" instead; do NOT clarify for stock-only updates
- When action="clarify": transactions and stock_updates must be empty arrays; include a "question" field with one short question; omit the "message" field
- transactions and stock_updates may be empty arrays []
- currency defaults to {currency} if not mentioned
- cost defaults to 0 if not mentioned
- transaction_type="sale" or "income" when the vendor RECEIVES money; "purchase" or "expense" when the vendor PAYS money
- For "paid 500 for previous sale", "customer paid", "received payment" → transaction_type="income"
- confidence="high" when all details are explicit; "medium" when most details are clear but some were inferred; "low" when the transaction is ambiguous or key details were guessed
- Output a single JSON object. Start your response with {{ and end with }}. No other text."""


@dataclass
class FieldResult:
    field: str
    expected: object
    actual: object
    match: bool


@dataclass
class CaseResult:
    case_id: str
    input: str
    raw_response: str
    parsed: dict
    field_results: list[FieldResult] = field(default_factory=list)
    error: str = ""

    @property
    def passed(self) -> bool:
        return not self.error and all(r.match for r in self.field_results)


def extract_json(text: str) -> dict:
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1:
        raise ValueError("No JSON object found in response")
    return json.loads(text[start : end + 1])


def check_case(case: dict, parsed: dict) -> list[FieldResult]:
    results = []
    expected = case.get("expected", {})

    # For multi-item cases, check action and count only
    if case.get("expected_count"):
        tx = parsed.get("transactions", [])
        results.append(FieldResult("action", "add_transaction", parsed.get("action"), parsed.get("action") == "add_transaction"))
        results.append(FieldResult("transaction_count", case["expected_count"], len(tx), len(tx) == case["expected_count"]))
        return results

    # Single expected dict
    if isinstance(expected, list):
        expected = expected[0]

    # Always check action
    results.append(FieldResult("action", expected.get("action"), parsed.get("action"), parsed.get("action") == expected.get("action")))

    if expected.get("action") == "clarify":
        results.append(FieldResult("question_present", True, bool(parsed.get("question")), bool(parsed.get("question"))))
        return results

    if expected.get("action") in ("unknown", "get_health", "update_stock"):
        if expected.get("action") == "update_stock":
            updates = parsed.get("stock_updates", [])
            if updates:
                upd = updates[0]
                if "item" in expected:
                    results.append(FieldResult("item", expected["item"].lower(), upd.get("item", "").lower(), expected["item"].lower() in upd.get("item", "").lower()))
                if "quantity_delta" in expected:
                    results.append(FieldResult("quantity_delta", expected["quantity_delta"], upd.get("quantity_delta"), upd.get("quantity_delta") == expected["quantity_delta"]))
        return results

    txs = parsed.get("transactions", [])
    tx = txs[0] if txs else {}

    check_fields = ["transaction_type", "item", "amount", "currency", "confidence"]
    for f in check_fields:
        if f not in expected:
            continue
        actual_val = tx.get(f)
        exp_val = expected[f]
        if f == "item":
            match = exp_val.lower() in str(actual_val).lower() or str(actual_val).lower() in exp_val.lower()
        elif f == "amount":
            match = abs(float(actual_val or 0) - float(exp_val)) < 0.01
        else:
            match = str(actual_val).lower() == str(exp_val).lower()
        results.append(FieldResult(f, exp_val, actual_val, match))

    return results


def run_eval(api_key: str, filter_id: str | None = None, model: str = "gemini-2.0-flash") -> None:
    try:
        from google import genai
        from google.genai import types
    except ImportError:
        print("ERROR: google-genai not installed. Run: pip install google-genai")
        sys.exit(1)

    corpus_text = CORPUS_PATH.read_text()
    # Strip JS-style comments before parsing
    corpus_text = re.sub(r"//[^\n]*", "", corpus_text)
    corpus = json.loads(corpus_text)
    cases = corpus["cases"]
    if filter_id:
        cases = [c for c in cases if c["id"] == filter_id]
        if not cases:
            print(f"Case {filter_id} not found.")
            sys.exit(1)

    client = genai.Client(api_key=api_key)
    system_prompt = SYSTEM_PROMPT_TEMPLATE.format(currency=corpus.get("default_currency", DEFAULT_CURRENCY))

    results: list[CaseResult] = []
    for case in cases:
        case_id = case["id"]
        user_input = case["input"]
        print(f"  [{case_id}] {user_input[:60]}...", end="", flush=True)

        try:
            response = client.models.generate_content(
                model=model,
                contents=user_input,
                config=types.GenerateContentConfig(
                    system_instruction=system_prompt,
                    temperature=0.1,
                    max_output_tokens=512,
                ),
            )
            raw = response.text or ""
            parsed = extract_json(raw)
            field_results = check_case(case, parsed)
            result = CaseResult(case_id=case_id, input=user_input, raw_response=raw, parsed=parsed, field_results=field_results)
        except Exception as e:
            result = CaseResult(case_id=case_id, input=user_input, raw_response="", parsed={}, error=str(e))

        results.append(result)
        status = "✓" if result.passed else ("ERR" if result.error else "✗")
        print(f" {status}")

    # ── Report ────────────────────────────────────────────────────────────────
    print("\n" + "═" * 60)
    print("RESULTS")
    print("═" * 60)

    all_field_results: list[FieldResult] = []
    for r in results:
        if r.error:
            print(f"  [{r.case_id}] ERROR: {r.error}")
            continue
        if not r.passed:
            print(f"  [{r.case_id}] FAIL")
            for fr in r.field_results:
                if not fr.match:
                    print(f"         {fr.field}: expected={fr.expected!r}  got={fr.actual!r}")
        all_field_results.extend(r.field_results)

    passed = sum(1 for r in results if r.passed)
    total = len(results)
    print(f"\nCases:  {passed}/{total} passed  ({100*passed//total if total else 0}%)")

    if all_field_results:
        by_field: dict[str, list[bool]] = {}
        for fr in all_field_results:
            by_field.setdefault(fr.field, []).append(fr.match)
        print("\nPer-field accuracy:")
        for fname, matches in sorted(by_field.items()):
            pct = 100 * sum(matches) // len(matches)
            bar = "█" * (pct // 10) + "░" * (10 - pct // 10)
            print(f"  {fname:<20} {bar}  {pct}%  ({sum(matches)}/{len(matches)})")

    target = 90
    overall = 100 * passed // total if total else 0
    print(f"\nTarget: ≥{target}%  |  Actual: {overall}%  |  {'✓ PASS' if overall >= target else '✗ BELOW TARGET'}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Ledger prompt accuracy evaluator")
    parser.add_argument("--api-key", default=os.getenv("GEMINI_API_KEY"), help="Google AI Studio API key")
    parser.add_argument("--case-id", help="Run a single case by ID (e.g. S01)")
    parser.add_argument("--model", default="gemini-2.0-flash", help="Gemini model to use")
    args = parser.parse_args()

    if not args.api_key:
        print("ERROR: provide --api-key or set GEMINI_API_KEY env var")
        sys.exit(1)

    print(f"Running Ledger prompt eval  |  model={args.model}  |  cases={'all' if not args.case_id else args.case_id}\n")
    run_eval(api_key=args.api_key, filter_id=args.case_id, model=args.model)
