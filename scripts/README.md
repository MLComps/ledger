# Ledger — Eval Scripts

Prompt accuracy evaluation for the Ledger on-device bookkeeping assistant.

---

## Setup

```bash
# From the scripts/ directory
uv sync          # installs all dependencies from pyproject.toml
```

You need one of:
- **OpenRouter API key** — recommended; works for all scripts; supports Gemini, Claude, GPT models
- **Google AI Studio key** — alternative for `eval_prompt.py` and `eval_comprehensive.py`

---

## Scripts

### `eval_prompt.py` — Single-turn accuracy

Tests the JSON inference path (single-turn). Sends each case from `eval_corpus.json` to the model and checks action + field values against expected output.

```bash
# OpenRouter (recommended)
uv run eval_prompt.py --api-key YOUR_OR_KEY --openrouter --model google/gemini-2.5-flash

# Google AI Studio
uv run eval_prompt.py --api-key YOUR_GEMINI_KEY

# Single case
uv run eval_prompt.py --api-key YOUR_KEY --openrouter --case-id S03
```

What it checks per case:
- `action` (add_transaction / update_stock / get_health / clarify / unknown)
- `transaction_type`, `item`, `amount`, `currency`, `confidence`
- Combined cases (B01-B05): transaction fields + stock_update fields
- Clarify cases: presence of `question` field

**Target:** >= 90%. Current baseline: **100% (37/37)**.

---

### `eval_comprehensive.py` — Full suite with semantic judge

Runs all 42 cases across three suites. Uses an LLM as semantic judge so language equivalences like "sukari" = "sugar" or "bob" = "KES" don't cause false failures. Also handles multi-turn clarification sequences.

```bash
# OpenRouter for both inference and judge (same key works for both)
uv run eval_comprehensive.py \
  --backend openrouter \
  --api-key YOUR_OR_KEY \
  --judge-key YOUR_OR_KEY \
  --judge-backend openrouter \
  --model google/gemini-2.5-flash

# Run only multi-turn cases
uv run eval_comprehensive.py --backend openrouter --api-key YOUR_KEY --suite multi

# Run only combined purchase+stock cases
uv run eval_comprehensive.py --backend openrouter --api-key YOUR_KEY --suite combined

# Single case by ID
uv run eval_comprehensive.py --backend openrouter --api-key YOUR_KEY --case-id MT02

# Skip judge — faster, exact matching only
uv run eval_comprehensive.py --backend openrouter --api-key YOUR_KEY --no-judge

# Test actual on-device Gemma model via litert-lm (requires model file)
uv run eval_comprehensive.py --judge-key YOUR_OR_KEY --model-path ./models/gemma-4-E2B-it.litertlm
```

Suites:

| Suite | Cases | What it tests |
|---|---|---|
| `single` | S01-L02 (32 cases) | Single-turn text inputs |
| `combined` | B01-B05 (5 cases) | Purchase + stock update in one message |
| `multi` | MT01-MT05 (5 cases) | Clarification then follow-up two-turn sequences |

**Target:** >= 90%. Current baseline: **97% (41/42)** — one genuinely ambiguous case (L02).

---

### `eval_tool_calling.py` — Tool-calling path (future)

Tests the automatic tool-calling path (`@Tool` / `ToolSet` / `automaticToolCalling`). Only relevant when the app is running in tool-calling mode (Phase 9b in the build plan). Not applicable to the current JSON-based inference path.

---

## Corpus — `eval_corpus.json`

42 labelled `{input, expected}` pairs covering the full range of inputs the app encounters.
Comments use `//` syntax and are stripped before JSON parsing.

| Prefix | Category | Count |
|---|---|---|
| S | Explicit sales | 5 |
| E | Expenses & purchases | 4 |
| I | Income / debt repayment | 2 |
| M | Multi-item in one message | 2 |
| C | Currency inference | 3 |
| P | M-Pesa / mobile money SMS | 2 |
| V | Voice fillers & vague items | 2 |
| R | Corrections | 2 |
| T | Stock-only updates | 2 |
| H | Financial health / summary | 2 |
| Q | Clarify-required (no amount stated) | 2 |
| U | Unknown / off-topic | 2 |
| L | Low-confidence deictic references | 2 |
| B | Combined purchase + restock | 5 |
| MT | Multi-turn clarification sequences | 5 |

### Adding a new case

**Single-turn:**
```json
{
  "id": "S06",
  "input": "sold avocados 5 pieces for 150 bob",
  "expected": {
    "action": "add_transaction",
    "transaction_type": "sale",
    "item": "avocados",
    "amount": 150,
    "currency": "KES",
    "confidence": "high"
  }
}
```

**Combined purchase + stock:**
```json
{
  "id": "B06",
  "type": "combined",
  "input": "Bought 20 litres cooking oil for 3000, put in store",
  "expected_transaction": {
    "action": "add_transaction",
    "transaction_type": "purchase",
    "item": "cooking oil",
    "amount": 3000,
    "currency": "KES",
    "confidence": "high"
  },
  "expected_stock": { "item": "cooking oil", "quantity_delta": 20, "unit": "litres" }
}
```

**Multi-turn clarification:**
```json
{
  "id": "MT06",
  "type": "multi_turn",
  "turns": [
    { "input": "Sold beans", "expected_action": "clarify" },
    {
      "input": "2 kg for 180 shillings",
      "expected": {
        "action": "add_transaction",
        "transaction_type": "sale",
        "item": "beans",
        "amount": 180,
        "currency": "KES"
      }
    }
  ]
}
```

---

## Prompt iteration workflow

1. Run `eval_comprehensive.py` to get a baseline score
2. Identify failing cases from the FAIL output
3. Edit `buildSystemPrompt()` in `LedgerScreen.kt`
4. Copy the updated prompt text into `SYSTEM_PROMPT_TEMPLATE` in both eval scripts
5. Re-run and compare
6. Commit when the >= 90% target is met
