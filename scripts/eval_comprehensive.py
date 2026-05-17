"""
Ledger comprehensive evaluator.

Tests the actual on-device Gemma 4 model via litert-lm, covering:
  - Single-turn text cases (all existing corpus cases)
  - Combined purchase+stock cases (B01-B05)
  - Multi-turn clarification sequences (MT01-MT05)

Uses Gemini as a semantic judge so item name equivalences (sukari=sugar,
stuff=goods, etc.) don't generate false failures.

Backends:
  gemma       — litert-lm local inference (default, tests actual shipped model)
  openrouter  — OpenRouter API (fast, for prompt iteration)
  gemini      — Google AI Studio

Usage:
  uv run eval_comprehensive.py --judge-key GEMINI_KEY
  uv run eval_comprehensive.py --backend openrouter --api-key OR_KEY --judge-key GEMINI_KEY
  uv run eval_comprehensive.py --suite multi --judge-key GEMINI_KEY
  uv run eval_comprehensive.py --case-id MT01 --judge-key GEMINI_KEY
"""

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

CORPUS_PATH = Path(__file__).parent / "eval_corpus.json"
DEFAULT_MODEL_PATH = str(Path(__file__).parent / "models" / "gemma-4-E2B-it.litertlm")
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
- action="update_stock" for restocking or stock level changes with no purchase amount
- action="get_health" when asked for totals, summary, or financial health
- action="unknown" if no financial action is found
- action="clarify" ONLY when the amount is completely absent AND cannot be inferred from any context; do NOT clarify for unclear item names — use confidence="low" instead; do NOT clarify for stock-only updates; do NOT clarify when the direction is clear from words like "sold", "bought", "paid", "received", "someone paid me", "customer bought"
- If the amount IS present and the direction IS clear, ALWAYS record the transaction even if the item name is vague (use item="goods") — never clarify in this case
- Corrections like "actually it was X not Y" always resolve to action="add_transaction" with the corrected amount; never clarify corrections when an amount is stated
- When action="clarify": transactions and stock_updates must be empty arrays; include a "question" field with one short question; omit the "message" field
- transactions and stock_updates may be empty arrays []
- currency defaults to {currency} if not mentioned; always output the 3-letter ISO code (e.g. KES not KSH)
- cost defaults to 0 if not mentioned
- transaction_type="sale" or "income" when the vendor RECEIVES money; "purchase" or "expense" when the vendor PAYS money
- For "paid 500 for previous sale", "customer paid", "received payment", "someone paid me" → transaction_type="income"
- For M-Pesa confirmations: "Ksh X received from NAME" → transaction_type="income", item="M-Pesa payment", currency="KES"; "Ksh X sent to NAME" → transaction_type="expense"
- When a purchase involves adding goods to inventory for resale, include BOTH a transactions entry (transaction_type="purchase") AND a stock_updates entry in the same response
- item: if the user says vague words like "stuff", "things", "something", "that thing" use item="goods"
- confidence levels — these are strict rules, not guidelines:
  - "high": item name is clearly specific (tomatoes, rice, bread, rent, electricity) AND the amount is a number directly spoken/written by the user — currency defaulting is fine and does NOT reduce confidence
  - "medium": EITHER the total amount was calculated by you (e.g. "3 at 30 each" → you computed 90), OR the amount is hedged/approximate ("about three fifty", "around 200"), OR the input is a correction ("actually it was X not Y"), OR the item is a category placeholder like "goods" or "stuff" — if ANY of these apply, confidence MUST be "medium" even if the item is known
  - "low": the item has no meaningful name at all — user said "that thing", "something", "it", or a bare pronoun with zero category hint; the item field will be "goods" but you have no idea what was transacted
- Output a single JSON object. Start your response with {{ and end with }}. No other text."""

JUDGE_SYSTEM = "You are a strict evaluator for a JSON-output bookkeeping AI. Respond with only a valid JSON object."

JUDGE_PROMPT = """Evaluate whether the actual output from a bookkeeping assistant is correct.

Input given to assistant: {input}
Expected output: {expected}
Actual output: {actual}

IMPORTANT RULES:
- Only check fields that appear in the expected output. IGNORE any extra fields in the actual output that are not in expected — extra fields are fine.
- action: must match exactly
- transaction_type: must match exactly
- amount: correct if within 1% of expected value
- currency: KES == KSH == Ksh; otherwise must match 3-letter code
- item: correct if semantically equivalent — sukari=sugar, mahindi=maize, uji=porridge, goods≈stuff≈things, "payment from John"≈"M-Pesa payment"≈"debt repayment"
- confidence: must match exactly (high/medium/low)
- unit: treat these as equivalent — kg=kilo=kilogram; litre=litres=liter=liters; piece=pieces=unit=each=item; accept any reasonable unit string if semantically correct
- question_present: pass if actual has a non-empty "question" field
- transaction_count: pass if actual transactions array has exactly that count
- stock_update: check item (semantic), quantity_delta (exact), unit (synonym-tolerant)
- note fields in expected are documentation only — never check them

Return exactly this JSON:
{{
  "action_correct": true_or_false,
  "transaction_type_correct": true_or_false_or_null,
  "amount_correct": true_or_false_or_null,
  "currency_correct": true_or_false_or_null,
  "item_correct": true_or_false_or_null,
  "confidence_correct": true_or_false_or_null,
  "stock_update_correct": true_or_false_or_null,
  "overall_pass": true_or_false,
  "notes": "one sentence explaining failures — blank string if all correct"
}}"""


# ── Helpers ──────────────────────────────────────────────────────────────────

def extract_json(text: str) -> dict:
    text = re.sub(r"^```(?:json)?\s*", "", text.strip())
    text = re.sub(r"\s*```$", "", text)
    start = text.find("{")
    end = text.rfind("}")
    if start == -1 or end == -1:
        raise ValueError(f"No JSON found in: {text[:120]!r}")
    return json.loads(text[start:end + 1])


def normalize_for_judge(parsed: dict, expected_count: Optional[int] = None) -> dict:
    """Flatten output so the judge sees the same shape as the expected dict."""
    action = parsed.get("action")
    result: dict = {"action": action}

    if expected_count is not None:
        result["transaction_count"] = len(parsed.get("transactions", []))
        return result

    txs = parsed.get("transactions", [])
    if txs:
        tx = txs[0]
        for k in ("transaction_type", "item", "amount", "currency", "confidence", "quantity", "unit"):
            if k in tx:
                result[k] = tx[k]

    updates = parsed.get("stock_updates", [])
    if updates:
        upd = updates[0]
        if action == "update_stock":
            # For pure stock updates, flatten to top level to match corpus expected shape
            for k in ("item", "quantity_delta", "unit"):
                if k in upd:
                    result[k] = upd[k]
        else:
            # For combined purchase+stock, nest under stock_update key
            result["stock_update"] = upd

    if parsed.get("question"):
        result["question_present"] = True

    return result


# ── Backends ─────────────────────────────────────────────────────────────────

class GemmaBackend:
    name = "gemma (litert-lm)"

    def __init__(self, model_path: str):
        try:
            import litert_lm
        except ImportError:
            print("ERROR: litert-lm not installed. Run: uv add litert-lm")
            sys.exit(1)
        self._lm = litert_lm
        print(f"  Loading model: {model_path}")
        try:
            self.engine = litert_lm.Engine(
                model_path,
                backend=litert_lm.Backend.GPU,
                max_num_tokens=2048,
            )
            print("  Backend: GPU")
        except Exception:
            self.engine = litert_lm.Engine(
                model_path,
                backend=litert_lm.Backend.CPU,
                max_num_tokens=2048,
            )
            print("  Backend: CPU (GPU unavailable)")

    def run_turns(self, system: str, turns: list[str]) -> list[str]:
        conv = self.engine.create_conversation(system_message=system)
        responses = []
        try:
            for turn in turns:
                resp = conv.send_message({"role": "user", "content": turn})
                responses.append(self._text(resp))
        finally:
            conv.close()
        return responses

    def _text(self, resp) -> str:
        content = resp.get("content", "")
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            return "".join(
                p.get("text", "") for p in content
                if isinstance(p, dict) and p.get("type") == "text"
            )
        return str(content)


class OpenRouterBackend:
    name = "openrouter"

    def __init__(self, api_key: str, model: str = "google/gemini-2.5-flash"):
        from openai import OpenAI
        self.client = OpenAI(
            api_key=api_key,
            base_url="https://openrouter.ai/api/v1",
            default_headers={"HTTP-Referer": "https://github.com/ledger-app", "X-Title": "Ledger eval"},
        )
        self.model = model

    def run_turns(self, system: str, turns: list[str]) -> list[str]:
        messages: list[dict] = []
        responses = []
        for turn in turns:
            messages.append({"role": "user", "content": turn})
            resp = self.client.chat.completions.create(
                model=self.model,
                messages=[{"role": "system", "content": system}] + messages,
                temperature=0.1,
                max_tokens=512,
            )
            text = resp.choices[0].message.content or ""
            responses.append(text)
            messages.append({"role": "assistant", "content": text})
        return responses


class GeminiBackend:
    name = "gemini"

    def __init__(self, api_key: str, model: str = "gemini-2.5-flash"):
        from google import genai
        from google.genai import types
        self.client = genai.Client(api_key=api_key)
        self.types = types
        self.model = model

    def run_turns(self, system: str, turns: list[str]) -> list[str]:
        # Gemini API doesn't natively support multi-turn with system prompt in one call,
        # so we build the history manually in the prompt for turn 2+
        responses = []
        history = []
        for turn in turns:
            if history:
                # Append prior exchanges into the user message as context
                context = "\n".join(
                    f"[User]: {h['u']}\n[Assistant]: {h['a']}" for h in history
                )
                contents = f"Prior conversation:\n{context}\n\n[User]: {turn}"
            else:
                contents = turn
            resp = self.client.models.generate_content(
                model=self.model,
                contents=contents,
                config=self.types.GenerateContentConfig(
                    system_instruction=system,
                    temperature=0.1,
                    max_output_tokens=512,
                ),
            )
            text = resp.text or ""
            responses.append(text)
            history.append({"u": turn, "a": text})
        return responses


# ── Judge ─────────────────────────────────────────────────────────────────────

class OpenRouterJudge:
    """Uses OpenRouter (no free-tier quota issues) to semantically score outputs."""

    def __init__(self, api_key: str, model: str = "google/gemini-2.5-flash"):
        from openai import OpenAI
        self.client = OpenAI(
            api_key=api_key,
            base_url="https://openrouter.ai/api/v1",
            default_headers={"HTTP-Referer": "https://github.com/ledger-app", "X-Title": "Ledger judge"},
        )
        self.model = model

    def judge(self, case_input: str, expected: dict, actual: dict) -> dict:
        prompt = JUDGE_PROMPT.format(
            input=json.dumps(case_input),
            expected=json.dumps(expected, indent=2),
            actual=json.dumps(actual, indent=2),
        )
        for attempt in range(3):
            try:
                resp = self.client.chat.completions.create(
                    model=self.model,
                    messages=[
                        {"role": "system", "content": JUDGE_SYSTEM},
                        {"role": "user", "content": prompt},
                    ],
                    temperature=0,
                    max_tokens=256,
                )
                return extract_json(resp.choices[0].message.content or "")
            except Exception as e:
                if "429" in str(e) and attempt < 2:
                    time.sleep(5 * (attempt + 1))
                else:
                    return {"overall_pass": False, "notes": f"judge error: {e}"}
        return {"overall_pass": False, "notes": "judge exhausted retries"}


class GeminiJudge:
    """Gemini direct API judge — only use when OpenRouter key unavailable."""

    def __init__(self, api_key: str):
        from google import genai
        from google.genai import types
        self.client = genai.Client(api_key=api_key)
        self.types = types

    def judge(self, case_input: str, expected: dict, actual: dict) -> dict:
        prompt = JUDGE_PROMPT.format(
            input=json.dumps(case_input),
            expected=json.dumps(expected, indent=2),
            actual=json.dumps(actual, indent=2),
        )
        for attempt in range(3):
            try:
                resp = self.client.models.generate_content(
                    model="gemini-2.0-flash",
                    contents=prompt,
                    config=self.types.GenerateContentConfig(
                        system_instruction=JUDGE_SYSTEM,
                        temperature=0,
                        max_output_tokens=256,
                    ),
                )
                return extract_json(resp.text or "")
            except Exception as e:
                if "429" in str(e) and attempt < 2:
                    time.sleep(15 * (attempt + 1))
                else:
                    return {"overall_pass": False, "notes": f"judge error: {e}"}
        return {"overall_pass": False, "notes": "judge exhausted retries"}


# ── Case checkers ─────────────────────────────────────────────────────────────

@dataclass
class CaseResult:
    case_id: str
    case_type: str
    raw_responses: list[str]
    judge_results: list[dict] = field(default_factory=list)
    error: str = ""

    @property
    def passed(self) -> bool:
        if self.error:
            return False
        return all(j.get("overall_pass", False) for j in self.judge_results)


def run_single(case: dict, backend, judge: Optional[GeminiJudge], system: str) -> CaseResult:
    case_id = case["id"]
    user_input = case["input"]
    try:
        responses = backend.run_turns(system, [user_input])
        raw = responses[0]
        parsed = extract_json(raw)
    except Exception as e:
        return CaseResult(case_id=case_id, case_type="single", raw_responses=[], error=str(e))

    expected = case.get("expected", {})
    if isinstance(expected, list):
        expected = expected[0]

    # Build simplified expected dict for judge
    judge_expected: dict = {}
    if case.get("expected_count"):
        judge_expected = {"action": "add_transaction", "transaction_count": case["expected_count"]}
    else:
        judge_expected = expected

    actual_normalized = normalize_for_judge(parsed, expected_count=case.get("expected_count"))
    result = CaseResult(case_id=case_id, case_type="single", raw_responses=[raw])
    if judge:
        jresult = judge.judge(user_input, judge_expected, actual_normalized)
        result.judge_results = [jresult]
    else:
        action_ok = parsed.get("action") == judge_expected.get("action")
        result.judge_results = [{"overall_pass": action_ok, "notes": "" if action_ok else f"action: expected {judge_expected.get('action')!r} got {parsed.get('action')!r}"}]
    return result


def run_combined(case: dict, backend, judge: Optional[GeminiJudge], system: str) -> CaseResult:
    case_id = case["id"]
    user_input = case["input"]
    try:
        responses = backend.run_turns(system, [user_input])
        raw = responses[0]
        parsed = extract_json(raw)
    except Exception as e:
        return CaseResult(case_id=case_id, case_type="combined", raw_responses=[], error=str(e))

    result = CaseResult(case_id=case_id, case_type="combined", raw_responses=[raw])

    # Check 1: transaction part
    tx_expected = case.get("expected_transaction", {})
    actual_tx_normalized = normalize_for_judge(parsed)
    if judge:
        j1 = judge.judge(user_input, tx_expected, actual_tx_normalized)
    else:
        j1 = {"overall_pass": parsed.get("action") == "add_transaction", "notes": ""}

    # Check 2: stock_update part — judge sees {stock_update: {item, quantity_delta, unit}}
    stock_expected = case.get("expected_stock", {})
    updates = parsed.get("stock_updates", [])
    stock_actual = updates[0] if updates else {}
    if judge:
        j2 = judge.judge(user_input, {"stock_update": stock_expected}, {"stock_update": stock_actual})
    else:
        item_ok = stock_expected.get("item", "").lower() in str(stock_actual.get("item", "")).lower()
        qty_ok = stock_actual.get("quantity_delta") == stock_expected.get("quantity_delta")
        j2 = {"overall_pass": bool(updates) and item_ok and qty_ok,
              "notes": "" if (bool(updates) and item_ok and qty_ok) else f"stock_updates missing or wrong: {stock_actual}"}

    result.judge_results = [j1, j2]
    return result


def run_multi_turn(case: dict, backend, judge: Optional[GeminiJudge], system: str) -> CaseResult:
    case_id = case["id"]
    turns_def = case["turns"]
    inputs = [t["input"] for t in turns_def]

    try:
        responses = backend.run_turns(system, inputs)
    except Exception as e:
        return CaseResult(case_id=case_id, case_type="multi_turn", raw_responses=[], error=str(e))

    result = CaseResult(case_id=case_id, case_type="multi_turn", raw_responses=responses)

    for i, (turn_def, raw) in enumerate(zip(turns_def, responses)):
        try:
            parsed = extract_json(raw)
        except Exception as e:
            result.judge_results.append({"overall_pass": False, "notes": f"turn {i+1} parse error: {e}"})
            continue

        # Intermediate turns: just check expected_action
        if "expected_action" in turn_def:
            expected_action = turn_def["expected_action"]
            ok = parsed.get("action") == expected_action
            result.judge_results.append({
                "overall_pass": ok,
                "notes": "" if ok else f"turn {i+1}: expected action={expected_action!r} got {parsed.get('action')!r}",
            })

        # Final turn: judge full expected output
        elif "expected" in turn_def:
            actual_normalized = normalize_for_judge(parsed)
            if judge:
                j = judge.judge(turn_def["input"], turn_def["expected"], actual_normalized)
            else:
                ok = parsed.get("action") == turn_def["expected"].get("action")
                j = {"overall_pass": ok, "notes": ""}
            result.judge_results.append(j)

        elif "expected_count" in turn_def:
            count = turn_def["expected_count"]
            actual_normalized = normalize_for_judge(parsed, expected_count=count)
            ok = actual_normalized.get("transaction_count") == count and parsed.get("action") == "add_transaction"
            result.judge_results.append({
                "overall_pass": ok,
                "notes": "" if ok else f"turn {i+1}: expected {count} transactions, got {actual_normalized.get('transaction_count')}",
            })

    return result


# ── Main eval loop ────────────────────────────────────────────────────────────

def run_eval(
    backend,
    judge,
    filter_id: Optional[str],
    suite: str,
) -> None:
    corpus_text = CORPUS_PATH.read_text()
    corpus_text = re.sub(r"//[^\n]*", "", corpus_text)
    corpus = json.loads(corpus_text)
    cases = corpus["cases"]

    # Filter by suite
    suite_filters = {
        "single": lambda c: c.get("type", "single") == "single" or "type" not in c,
        "combined": lambda c: c.get("type") == "combined",
        "multi": lambda c: c.get("type") == "multi_turn",
        "all": lambda c: True,
    }
    filter_fn = suite_filters.get(suite, suite_filters["all"])
    cases = [c for c in cases if filter_fn(c)]

    if filter_id:
        cases = [c for c in cases if c["id"] == filter_id]
        if not cases:
            print(f"Case {filter_id} not found.")
            sys.exit(1)

    system = SYSTEM_PROMPT_TEMPLATE.format(currency=corpus.get("default_currency", DEFAULT_CURRENCY))

    results: list[CaseResult] = []
    for case in cases:
        case_id = case["id"]
        case_type = case.get("type", "single")
        user_display = case.get("input", case.get("turns", [{}])[0].get("input", ""))
        print(f"  [{case_id}:{case_type}] {user_display[:55]}...", end="", flush=True)

        t0 = time.time()
        if case_type == "combined":
            result = run_combined(case, backend, judge, system)
        elif case_type == "multi_turn":
            result = run_multi_turn(case, backend, judge, system)
        else:
            result = run_single(case, backend, judge, system)

        elapsed = time.time() - t0
        status = "✓" if result.passed else ("ERR" if result.error else "✗")
        print(f" {status}  ({elapsed:.1f}s)")
        results.append(result)

        # Small delay for API-backed runs to respect rate limits
        if not isinstance(backend, GemmaBackend):
            time.sleep(0.5)

    # ── Report ────────────────────────────────────────────────────────────────
    print("\n" + "═" * 65)
    print("RESULTS")
    print("═" * 65)

    for r in results:
        if r.error:
            print(f"  [{r.case_id}] ERROR: {r.error[:120]}")
            continue
        if not r.passed:
            print(f"  [{r.case_id}:{r.case_type}] FAIL")
            for j in r.judge_results:
                if not j.get("overall_pass"):
                    notes = j.get("notes", "")
                    if notes:
                        print(f"         {notes}")
                    # Print any false fields from judge
                    for k, v in j.items():
                        if k not in ("overall_pass", "notes") and v is False:
                            print(f"         {k}: FAIL")

    by_type: dict[str, list[bool]] = {}
    for r in results:
        by_type.setdefault(r.case_type, []).append(r.passed)

    passed_total = sum(1 for r in results if r.passed)
    total = len(results)

    print(f"\nBy suite:")
    for t, outcomes in sorted(by_type.items()):
        p = sum(outcomes)
        n = len(outcomes)
        bar = "█" * (10 * p // n) + "░" * (10 - 10 * p // n) if n else ""
        print(f"  {t:<12} {bar}  {100*p//n if n else 0}%  ({p}/{n})")

    overall = 100 * passed_total // total if total else 0
    target = 90
    print(f"\nOverall: {passed_total}/{total} cases  ({overall}%)")
    print(f"Target:  ≥{target}%  |  {'✓ PASS' if overall >= target else '✗ BELOW TARGET'}")
    judge_label = type(judge).__name__ if judge else "exact match"
    print(f"Scoring: {judge_label}  |  backend: {backend.name}")


# ── Entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Ledger comprehensive evaluator")
    parser.add_argument("--backend", choices=["gemma", "openrouter", "gemini"], default="gemma")
    parser.add_argument("--model-path", default=DEFAULT_MODEL_PATH, help="Path to .litertlm model (gemma backend)")
    parser.add_argument("--api-key", default=os.getenv("GEMINI_API_KEY"), help="API key for openrouter/gemini backend")
    parser.add_argument("--judge-key", default=os.getenv("OPENROUTER_KEY"), help="OpenRouter API key for judge")
    parser.add_argument("--judge-backend", choices=["openrouter", "gemini"], default="openrouter", help="Judge backend (default: openrouter)")
    parser.add_argument("--model", default="google/gemini-2.5-flash", help="Model name (openrouter/gemini)")
    parser.add_argument("--no-judge", action="store_true", help="Skip judge, use exact matching")
    parser.add_argument("--suite", choices=["all", "single", "combined", "multi"], default="all")
    parser.add_argument("--case-id", help="Run a single case by ID")
    args = parser.parse_args()

    # Build backend
    if args.backend == "gemma":
        backend = GemmaBackend(args.model_path)
    elif args.backend == "openrouter":
        if not args.api_key:
            print("ERROR: --api-key required for openrouter backend")
            sys.exit(1)
        backend = OpenRouterBackend(args.api_key, model=args.model)
    else:
        if not args.api_key:
            print("ERROR: --api-key required for gemini backend")
            sys.exit(1)
        backend = GeminiBackend(args.api_key, model=args.model)

    # Build judge — prefer OpenRouter (no free-tier limits); fall back to Gemini direct
    judge = None
    if not args.no_judge:
        # If no judge-key given explicitly, try the api-key (works if backend=openrouter)
        jkey = args.judge_key or args.api_key
        if jkey:
            if args.judge_backend == "openrouter":
                judge = OpenRouterJudge(jkey)
            else:
                judge = GeminiJudge(jkey)
        else:
            print("WARNING: no --judge-key provided, falling back to exact matching")

    judge_label = f"{type(judge).__name__}" if judge else "exact match"
    print(f"Ledger comprehensive eval")
    print(f"  backend : {backend.name}")
    print(f"  judge   : {judge_label}")
    print(f"  suite   : {args.suite}")
    print(f"  cases   : {args.case_id or 'all'}\n")

    run_eval(backend=backend, judge=judge, filter_id=args.case_id, suite=args.suite)
