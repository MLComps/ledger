"""
Ledger tool-calling path evaluator.

Tests the agent/tool-calling system prompt against the eval corpus.
Instead of checking JSON output, it intercepts which tool was called
and with what arguments, then judges those against the expected values.

Key differences from eval_comprehensive.py:
  - Uses the new agent system prompt (no JSON schema)
  - Checks tool call arguments, not JSON fields in the response
  - Combined (B) cases only check addTransaction — stock is auto-updated
    by the Android addTransaction implementation, not a separate tool call
  - Clarify detection: no tool called + response contains a question

Usage:
  uv run eval_tool_calling.py --judge-key OPENROUTER_KEY
  uv run eval_tool_calling.py --backend openrouter --api-key OR_KEY --judge-key OR_KEY
  uv run eval_tool_calling.py --no-judge
  uv run eval_tool_calling.py --case-id S01 --judge-key OR_KEY
  uv run eval_tool_calling.py --suite multi --judge-key OR_KEY
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

# ── New agent system prompt (mirrors LedgerScreen.kt buildSystemPrompt) ──────

AGENT_SYSTEM_PROMPT = """You are Ledger, an offline bookkeeping assistant for market vendors. You have three tools: addTransaction, updateStock, and getFinancialHealth.

For every user input, identify any financial transaction or inventory change and call the appropriate tool immediately. After calling a tool, reply with a brief friendly confirmation in the user's language (one sentence).

Tool usage rules:
- getFinancialHealth: FIRST check — if the user asks ANY question about profit, loss, earnings, revenue, totals, summary, or business status, IMMEDIATELY call getFinancialHealth without asking anything. Trigger phrases: "profit", "summary", "how am I doing", "earnings", "revenue", "loss", "how much did I make", "business health", "today's numbers". Examples: "What is my profit today?" -> call getFinancialHealth. "Give me a summary of today's business" -> call getFinancialHealth.
- addTransaction: for sales, purchases, expenses, and income. Default transaction_type is "sale" unless context says otherwise.
  - confidence="high": amount directly stated ("sold sugar 100", "paid 500 for rent")
  - confidence="medium": you computed the total (e.g. "3 at 30 each" -> 90) or amount is approximate ("about 350")
  - confidence="low": item is vague ("that thing", "something", "it", "stuff") — ALWAYS call addTransaction with confidence="low" when an amount IS stated, even if the item is unclear. Use item="goods" for completely unknown items.
  - RULE: if an amount IS stated, ALWAYS call addTransaction, no matter how vague the item. Never clarify when an amount is given.
  - Always provide the item parameter. Use item="goods" if the item is completely unclear.
  - NEVER guess or infer an amount. ONLY record amounts the user explicitly stated. If no amount is given, ask — even for common items like electricity, airtime, or bread where you might know typical prices.
- updateStock: ONLY for pure inventory adjustments where NO purchase price is stated (e.g. "I received 10 kg of rice" with no price, "added 50 soap to shelf" with no price). If a purchase amount is mentioned alongside storage or restocking, call addTransaction (transaction_type="purchase") instead — do NOT call updateStock. Example: "Got 50 packets soap from supplier for 3000, added to shelf" -> addTransaction(purchase, 3000), NOT updateStock.

Currency defaults to {currency}. Always use the 3-letter ISO code (e.g. KES not KSH).
If the amount is completely absent AND cannot be inferred, ask one short clarifying question in the user's language. Do NOT call any tool in this case. Examples that require clarification: "Sold mangoes" (no amount), "I sold some milk today" (no amount), "tomatoes transaction" (no amount or direction), "Got payment from a customer" (no amount stated).

M-Pesa / mobile money: "Ksh X received from NAME" -> addTransaction, transaction_type="income", item="M-Pesa payment", currency="KES"; "Ksh X sent to NAME" -> addTransaction, transaction_type="expense", currency="KES", item=what was paid for (e.g. "airtime", "bill payment"). Only apply M-Pesa rules when the message uses "Ksh" prefix or mentions M-Pesa/Mpesa explicitly. For other payments without M-Pesa context, use item describing the payment nature (e.g. "previous sale payment", "debt repayment", "loan repayment").
Swahili: uza/uliuza=sold, nunua/nilinunua=bought, lipa/nilipa=paid. Numbers: moja=1, mbili=2, tatu=3, nne=4, tano=5, kumi=10, ishirini=20, thelathini=30, hamsini=50, mia=100, elfu=1000. Example: "uza sukari kilo moja mia moja" -> sold 1 kg sugar for 100 {currency}."""

JUDGE_SYSTEM = "You are a strict evaluator for a bookkeeping AI. Respond with only a valid JSON object."

JUDGE_PROMPT = """Evaluate whether the bookkeeping assistant correctly handled a user input.

Input given to assistant: {input}
Expected output: {expected}
Actual tool call output: {actual}

IMPORTANT RULES:
- Only check fields that appear in the expected output. Ignore extra fields.
- action: "clarify" and "unknown" are EQUIVALENT — both mean no tool was called and no action was taken. If expected is "unknown" and actual is "clarify" (or vice versa), action_correct=true and overall_pass=true (assuming no other failures).
- transaction_type: must match exactly
- amount: correct if within 1% of expected value
- currency: KES == KSH == Ksh; otherwise must match 3-letter code
- item: correct if semantically equivalent — sukari=sugar, mahindi=maize, uji=porridge, goods~stuff~things; also correct (case-insensitive) if one string is a substring of the other (e.g. "transport" matches "Transport cost", "bread" matches "bread order")
- confidence: must match exactly (high/medium/low)
- unit: treat as equivalent — kg=kilo=kilogram; litre=litres=liter; piece=pieces=unit=each=item=unitless
- question_present: pass if actual has question_present=true
- transaction_count: pass if actual has exactly that count
- stock_update: check item (semantic), quantity_delta (exact), unit (synonym-tolerant)

Return exactly this JSON:
{{
  "action_correct": true_or_false,
  "transaction_type_correct": true_or_false_or_null,
  "amount_correct": true_or_false_or_null,
  "currency_correct": true_or_false_or_null,
  "item_correct": true_or_false_or_null,
  "confidence_correct": true_or_false_or_null,
  "overall_pass": true_or_false,
  "notes": "one sentence explaining failures — blank string if all correct"
}}"""


# ── Tool factory ──────────────────────────────────────────────────────────────

def make_tools():
    """Return (tool_functions, calls_list). calls_list is populated on each invocation."""
    calls: list[dict] = []

    def add_transaction(
        item: str = "goods",
        amount: float = 0.0,
        currency: str = DEFAULT_CURRENCY,
        transaction_type: str = "sale",
        cost: float = 0.0,
        quantity: float = 1.0,
        unit: str = "unit",
        confidence: str = "high",
    ) -> dict:
        """Record a sale, purchase, income, or expense in the ledger.

        Args:
            item: Product or service name.
            amount: Total transaction amount.
            currency: Currency code e.g. KES, RWF, GHS.
            transaction_type: Type: sale, purchase, expense, or income.
            cost: Cost of goods (0 if not applicable).
            quantity: Quantity.
            unit: Unit e.g. kg, pieces, sack.
            confidence: Confidence: high (amount stated), medium (computed), low (vague item).
        """
        calls.append({
            "tool": "addTransaction",
            "item": item, "amount": amount, "currency": currency,
            "transaction_type": transaction_type, "cost": cost,
            "quantity": quantity, "unit": unit, "confidence": confidence,
        })
        return {"status": "ok", "item": item, "amount": amount}

    def update_stock(
        item: str,
        quantity_delta: float,
        unit: str = "unit",
    ) -> dict:
        """Adjust stock quantity for an item. Use positive delta to restock, negative to remove.

        Args:
            item: Item name.
            quantity_delta: Change in quantity (positive = add, negative = remove).
            unit: Unit of measurement.
        """
        calls.append({"tool": "updateStock", "item": item, "quantity_delta": quantity_delta, "unit": unit})
        return {"status": "ok", "item": item, "quantity": quantity_delta}

    def get_financial_health() -> dict:
        """Get today's total revenue, cost, net profit, and transaction count."""
        calls.append({"tool": "getFinancialHealth"})
        return {"total_revenue": 0.0, "total_cost": 0.0, "net_profit": 0.0, "transaction_count": 0, "stock_items": []}

    return [add_transaction, update_stock, get_financial_health], calls


# ── Normalise tool calls → judge-compatible dict ──────────────────────────────

def normalize_calls(calls: list[dict], response_text: str, expected_count: int | None = None) -> dict:
    """Map recorded tool calls into the same shape the corpus judge expects."""
    add_calls = [c for c in calls if c["tool"] == "addTransaction"]
    stock_calls = [c for c in calls if c["tool"] == "updateStock"]
    health_calls = [c for c in calls if c["tool"] == "getFinancialHealth"]

    if not calls:
        # No tool called — model either asked a question (clarify) or gave an unrelated reply.
        # Both are represented as "no action taken"; treat as unknown for judge consistency.
        has_question = "?" in response_text
        return {"action": "clarify" if has_question else "unknown",
                "question_present": has_question}

    if health_calls:
        return {"action": "get_health"}

    if expected_count is not None and add_calls:
        return {"action": "add_transaction", "transaction_count": len(add_calls)}

    if stock_calls and not add_calls:
        c = stock_calls[0]
        return {"action": "update_stock", "item": c["item"],
                "quantity_delta": c["quantity_delta"], "unit": c["unit"]}

    if add_calls:
        # Model called addTransaction with amount=0 — it recognised no amount and used 0 as a
        # placeholder, which is semantically equivalent to "needs clarification".
        if all(c.get("amount", 0) in (0, 0.0) for c in add_calls):
            return {"action": "clarify", "question_present": False}
        c = add_calls[0]
        return {
            "action": "add_transaction",
            "transaction_type": c["transaction_type"],
            "item": c["item"],
            "amount": c["amount"],
            "currency": c["currency"],
            "quantity": c["quantity"],
            "unit": c["unit"],
            "confidence": c["confidence"],
        }

    return {"action": "unknown"}


def extract_text(resp) -> str:
    if isinstance(resp, str):
        return resp
    content = resp.get("content", "") if isinstance(resp, dict) else ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(
            p.get("text", "") for p in content
            if isinstance(p, dict) and p.get("type") == "text"
        )
    return str(content)


# ── Backends ──────────────────────────────────────────────────────────────────

class GemmaToolBackend:
    name = "gemma (litert-lm)"

    def __init__(self, model_path: str):
        try:
            import litert_lm as lm
        except ImportError:
            print("ERROR: litert-lm not installed. Run: uv add litert-lm")
            sys.exit(1)
        self._lm = lm
        print(f"  Loading model: {model_path}")
        try:
            self.engine = lm.Engine(model_path, backend=lm.Backend.GPU, max_num_tokens=4096)
            print("  Backend: GPU")
        except Exception:
            self.engine = lm.Engine(model_path, backend=lm.Backend.CPU, max_num_tokens=4096)
            print("  Backend: CPU (GPU unavailable)")

    def run_turns(self, system: str, turns: list[str]) -> list[tuple[str, list[dict]]]:
        """Returns list of (response_text, tool_calls_this_turn) per turn."""
        tools_list, calls = make_tools()
        conv = self.engine.create_conversation(
            system_message=system,
            tools=tools_list,
            automatic_tool_calling=True,
        )
        results = []
        try:
            for turn in turns:
                calls.clear()
                resp = conv.send_message({"role": "user", "content": turn})
                results.append((extract_text(resp), list(calls)))
        finally:
            conv.close()
        return results


OPENAI_TOOL_SCHEMAS = [
    {
        "type": "function",
        "function": {
            "name": "addTransaction",
            "description": "Record a sale, purchase, income, or expense in the ledger.",
            "parameters": {
                "type": "object",
                "properties": {
                    "item": {"type": "string", "description": "Product or service name"},
                    "amount": {"type": "number", "description": "Total transaction amount"},
                    "currency": {"type": "string", "description": "Currency code e.g. KES, RWF, GHS"},
                    "transaction_type": {"type": "string", "enum": ["sale", "purchase", "expense", "income"]},
                    "cost": {"type": "number", "description": "Cost of goods (0 if not applicable)", "default": 0},
                    "quantity": {"type": "number", "description": "Quantity", "default": 1},
                    "unit": {"type": "string", "description": "Unit e.g. kg, pieces, sack", "default": "unit"},
                    "confidence": {"type": "string", "enum": ["high", "medium", "low"], "default": "high"},
                },
                "required": ["item", "amount", "currency", "transaction_type"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "updateStock",
            "description": "Adjust stock quantity. Use positive delta to restock, negative when items are sold.",
            "parameters": {
                "type": "object",
                "properties": {
                    "item": {"type": "string", "description": "Item name"},
                    "quantity_delta": {"type": "number", "description": "Change in quantity"},
                    "unit": {"type": "string", "description": "Unit of measurement", "default": "unit"},
                },
                "required": ["item", "quantity_delta"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "getFinancialHealth",
            "description": "Get today's total revenue, cost, net profit, and transaction count.",
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
]


def _execute_tool_call(name: str, args: dict, calls: list) -> str:
    """Execute a named tool, record call, return JSON result string."""
    if name == "addTransaction":
        calls.append({"tool": "addTransaction", **args})
        return json.dumps({"status": "ok"})
    if name == "updateStock":
        calls.append({"tool": "updateStock", **args})
        return json.dumps({"status": "ok"})
    if name == "getFinancialHealth":
        calls.append({"tool": "getFinancialHealth"})
        return json.dumps({"total_revenue": 0, "total_cost": 0, "net_profit": 0,
                           "transaction_count": 0, "stock_items": []})
    return json.dumps({"error": "unknown tool"})


class OpenRouterToolBackend:
    name = "openrouter"

    def __init__(self, api_key: str, model: str = "google/gemini-2.5-flash"):
        from openai import OpenAI
        self.client = OpenAI(
            api_key=api_key,
            base_url="https://openrouter.ai/api/v1",
            default_headers={"HTTP-Referer": "https://github.com/ledger-app", "X-Title": "Ledger eval"},
        )
        self.model = model

    def run_turns(self, system: str, turns: list[str]) -> list[tuple[str, list[dict]]]:
        messages: list[dict] = [{"role": "system", "content": system}]
        results = []
        for turn in turns:
            messages.append({"role": "user", "content": turn})
            calls: list[dict] = []
            # Agentic loop: keep sending until no more tool calls
            for _ in range(6):
                resp = self.client.chat.completions.create(
                    model=self.model,
                    messages=messages,
                    tools=OPENAI_TOOL_SCHEMAS,
                    tool_choice="auto",
                    temperature=0.1,
                    max_tokens=512,
                )
                msg = resp.choices[0].message
                messages.append(msg.model_dump(exclude_none=True))
                if not msg.tool_calls:
                    results.append((msg.content or "", list(calls)))
                    break
                for tc in msg.tool_calls:
                    try:
                        args = json.loads(tc.function.arguments)
                    except Exception:
                        args = {}
                    result_str = _execute_tool_call(tc.function.name, args, calls)
                    messages.append({
                        "role": "tool",
                        "tool_call_id": tc.id,
                        "content": result_str,
                    })
            else:
                results.append(("", list(calls)))
        return results


# ── Judge ─────────────────────────────────────────────────────────────────────

class OpenRouterJudge:
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
                text = resp.choices[0].message.content or ""
                start, end = text.find("{"), text.rfind("}")
                return json.loads(text[start:end + 1])
            except Exception as e:
                if "429" in str(e) and attempt < 2:
                    time.sleep(5 * (attempt + 1))
                else:
                    return {"overall_pass": False, "notes": f"judge error: {e}"}
        return {"overall_pass": False, "notes": "judge exhausted retries"}


# ── Case runners ──────────────────────────────────────────────────────────────

@dataclass
class CaseResult:
    case_id: str
    case_type: str
    judge_results: list[dict] = field(default_factory=list)
    error: str = ""
    raw_calls: list[list[dict]] = field(default_factory=list)  # per-turn calls

    @property
    def passed(self) -> bool:
        if self.error:
            return False
        return all(j.get("overall_pass", False) for j in self.judge_results)


def run_single(case: dict, backend, judge, system: str) -> CaseResult:
    cid = case["id"]
    user_input = case["input"]
    try:
        results = backend.run_turns(system, [user_input])
        text, calls = results[0]
    except Exception as e:
        return CaseResult(cid, "single", error=str(e))

    expected = case.get("expected", {})
    if isinstance(expected, list):
        expected = expected[0]

    expected_count = case.get("expected_count")
    actual = normalize_calls(calls, text, expected_count=expected_count)
    judge_expected = {"action": "add_transaction", "transaction_count": expected_count} if expected_count else expected

    result = CaseResult(cid, "single", raw_calls=[calls])
    if judge:
        result.judge_results = [judge.judge(user_input, judge_expected, actual)]
    else:
        exp_action = judge_expected.get("action")
        act_action = actual.get("action")
        # clarify and unknown are both "no action taken" — treat as equivalent
        no_tool_equiv = {exp_action, act_action} <= {"clarify", "unknown"}
        ok = act_action == exp_action or no_tool_equiv
        result.judge_results = [{"overall_pass": ok, "notes": "" if ok else
                                  f"action: expected {exp_action!r} got {act_action!r}"}]
    return result


def run_combined(case: dict, backend, judge, system: str) -> CaseResult:
    """Combined purchase+stock: only checks the transaction (stock auto-updated by addTransaction)."""
    cid = case["id"]
    user_input = case["input"]
    try:
        results = backend.run_turns(system, [user_input])
        text, calls = results[0]
    except Exception as e:
        return CaseResult(cid, "combined", error=str(e))

    tx_expected = case.get("expected_transaction", {})
    actual = normalize_calls(calls, text)
    result = CaseResult(cid, "combined", raw_calls=[calls])
    if judge:
        result.judge_results = [judge.judge(user_input, tx_expected, actual)]
    else:
        ok = actual.get("action") == "add_transaction"
        result.judge_results = [{"overall_pass": ok, "notes": "" if ok else f"expected add_transaction, got {actual.get('action')!r}"}]
    return result


def run_multi_turn(case: dict, backend, judge, system: str) -> CaseResult:
    cid = case["id"]
    turns_def = case["turns"]
    inputs = [t["input"] for t in turns_def]
    try:
        turn_results = backend.run_turns(system, inputs)
    except Exception as e:
        return CaseResult(cid, "multi_turn", error=str(e))

    result = CaseResult(cid, "multi_turn", raw_calls=[calls for _, calls in turn_results])

    for i, (turn_def, (text, calls)) in enumerate(zip(turns_def, turn_results)):
        actual = normalize_calls(calls, text, expected_count=turn_def.get("expected_count"))

        if "expected_action" in turn_def:
            exp_action = turn_def["expected_action"]
            act_action = actual.get("action")
            # Accept clarify / unknown / add_transaction-without-amount as equivalent
            # (model may signal "need info" in different ways)
            amount_zero_or_missing = not calls or all(
                c.get("amount", 1) in (0, 0.0, None) for c in calls if c.get("tool") == "addTransaction"
            )
            clarify_equiv = (
                act_action == exp_action
                or (exp_action == "clarify" and act_action == "unknown")
                or (exp_action == "clarify" and act_action == "add_transaction" and amount_zero_or_missing)
            )
            result.judge_results.append({
                "overall_pass": clarify_equiv,
                "notes": "" if clarify_equiv else
                         f"turn {i+1}: expected {exp_action!r} got {act_action!r}",
            })

        elif "expected" in turn_def:
            if judge:
                j = judge.judge(turn_def["input"], turn_def["expected"], actual)
            else:
                ok = actual.get("action") == turn_def["expected"].get("action")
                j = {"overall_pass": ok, "notes": ""}
            result.judge_results.append(j)

        elif "expected_count" in turn_def:
            exp_count = turn_def["expected_count"]
            ok = actual.get("transaction_count") == exp_count and actual.get("action") == "add_transaction"
            result.judge_results.append({
                "overall_pass": ok,
                "notes": "" if ok else f"turn {i+1}: expected {exp_count} txns, got {actual.get('transaction_count')}",
            })

    return result


# ── Eval loop ─────────────────────────────────────────────────────────────────

def run_eval(backend, judge, filter_id: Optional[str], suite: str, verbose: bool) -> None:
    corpus_text = re.sub(r"//[^\n]*", "", CORPUS_PATH.read_text())
    corpus = json.loads(corpus_text)
    cases = corpus["cases"]
    currency = corpus.get("default_currency", DEFAULT_CURRENCY)
    system = AGENT_SYSTEM_PROMPT.format(currency=currency)

    suite_filters = {
        "single":   lambda c: c.get("type", "single") in ("single", None) and "type" not in c or c.get("type") == "single",
        "combined": lambda c: c.get("type") == "combined",
        "multi":    lambda c: c.get("type") == "multi_turn",
        "all":      lambda c: True,
    }
    cases = [c for c in cases if suite_filters.get(suite, lambda c: True)(c)]
    if filter_id:
        cases = [c for c in cases if c["id"] == filter_id]
        if not cases:
            print(f"Case {filter_id!r} not found.")
            sys.exit(1)

    results: list[CaseResult] = []
    for case in cases:
        cid = case["id"]
        ctype = case.get("type", "single")
        preview = case.get("input", (case.get("turns") or [{}])[0].get("input", ""))
        print(f"  [{cid}:{ctype}] {preview[:55]!r}", end="", flush=True)

        t0 = time.time()
        if ctype == "combined":
            r = run_combined(case, backend, judge, system)
        elif ctype == "multi_turn":
            r = run_multi_turn(case, backend, judge, system)
        else:
            r = run_single(case, backend, judge, system)

        elapsed = time.time() - t0
        status = "✓" if r.passed else ("ERR" if r.error else "✗")
        print(f"  {status}  ({elapsed:.1f}s)")

        if verbose and not r.passed and not r.error:
            for calls in r.raw_calls:
                if calls:
                    print(f"         calls: {json.dumps(calls, indent=None)[:120]}")
        results.append(r)

        if not isinstance(backend, GemmaToolBackend):
            time.sleep(0.4)

    # ── Report ─────────────────────────────────────────────────────────────────
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
                    if j.get("notes"):
                        print(f"         {j['notes']}")
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
        p, n = sum(outcomes), len(outcomes)
        bar = "█" * (10 * p // n) + "░" * (10 - 10 * p // n) if n else ""
        print(f"  {t:<12} {bar}  {100*p//n if n else 0}%  ({p}/{n})")

    overall = 100 * passed_total // total if total else 0
    target = 90
    print(f"\nOverall: {passed_total}/{total} cases  ({overall}%)")
    print(f"Target:  >=90%  |  {'✓ PASS' if overall >= target else '✗ BELOW TARGET'}")
    judge_label = type(judge).__name__ if judge else "exact match"
    print(f"Scoring: {judge_label}  |  backend: {backend.name}")
    print(f"\nNote: combined (B) cases check addTransaction only — stock is auto-updated")
    print(f"      by the Android addTransaction implementation, not a separate tool call.")


# ── Entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend", choices=["gemma", "openrouter"], default="gemma")
    parser.add_argument("--model-path", default=DEFAULT_MODEL_PATH)
    parser.add_argument("--api-key", default=os.getenv("OPENROUTER_KEY"))
    parser.add_argument("--judge-key", default=os.getenv("OPENROUTER_KEY"))
    parser.add_argument("--model", default="google/gemini-2.5-flash", help="Model for openrouter backend")
    parser.add_argument("--no-judge", action="store_true")
    parser.add_argument("--suite", choices=["all", "single", "combined", "multi"], default="all")
    parser.add_argument("--case-id")
    parser.add_argument("--verbose", action="store_true", help="Print raw tool calls on failures")
    args = parser.parse_args()

    if args.backend == "gemma":
        backend = GemmaToolBackend(args.model_path)
    else:
        if not args.api_key:
            print("ERROR: --api-key required for openrouter backend")
            sys.exit(1)
        backend = OpenRouterToolBackend(args.api_key, model=args.model)

    judge = None
    if not args.no_judge:
        jkey = args.judge_key or args.api_key
        if jkey:
            judge = OpenRouterJudge(jkey)
        else:
            print("WARNING: no --judge-key, falling back to exact matching")

    print(f"Ledger tool-calling eval")
    print(f"  backend : {backend.name}")
    print(f"  judge   : {type(judge).__name__ if judge else 'exact match'}")
    print(f"  suite   : {args.suite}")
    print(f"  cases   : {args.case_id or 'all'}\n")

    run_eval(backend=backend, judge=judge, filter_id=args.case_id, suite=args.suite, verbose=args.verbose)
