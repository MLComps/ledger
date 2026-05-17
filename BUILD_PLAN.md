# Ledger — Build Plan

Full implementation plan for the Ledger standalone Android app, from scaffold to release.

---

## Phase 6 — UI Modernization [ in progress ]

Goal: replace the utilitarian shell with a polished, production-quality app — multi-tab layout, Material You theming, financial-app UX patterns, smooth motion, and first-class dark mode.

**New dependencies (add to `libs.versions.toml`):**

| Lib | Artifact | Version |
|---|---|---|
| Lottie Compose | `com.airbnb.android:lottie-compose` | `6.6.0` |
| Vico charts (M3) | `com.patrykandpatrick.vico:compose-m3` | `2.x` |
| Shimmer | `com.valentinilk.shimmer:compose-shimmer` | `1.3.3` |
| Reorderable list | `sh.calvin.reorderable:reorderable` | `2.x` |
| Accompanist permissions | `com.google.accompanist:accompanist-permissions` | `0.36.x` |

### 6a — Navigation & layout
- [x] Replace single-screen nav with a `NavigationBar` (bottom nav) with four tabs: **Home** (dashboard + chat), **History** (full transaction log), **Inventory**, **Settings**
- [x] Type-safe routes using `@Serializable` data objects — `androidx.navigation:navigation-compose:2.8+` with `kotlin.serialization`; no more string route literals
- [ ] Per-tab back stacks via nested nav graphs; `rememberSaveable` scroll state per tab
- [ ] Collapsing `TopAppBar` on Home — `LargeTopAppBar` with `enterAlwaysScrollBehavior()` so it shrinks on scroll and snaps back on fling
- [ ] Enable predictive back on API 35+; add `android:enableOnBackInvokedCallback="true"` to manifest

### 6b — Design system & theming
- [x] Full dark mode — `isSystemInDarkTheme()` branch in `LedgerTheme`; dark and light colour schemes defined
- [x] Custom M3 colour palette — deep forest teal-green seed colour; consistent across light and dark (palette swap by Gemini pending revert — deferred)
- [x] Consistent type scale — `displaySmall` for dashboard totals, `titleMedium` for card headers, `bodySmall` for list rows, `labelSmall` for metadata
- [ ] Revert `LedgerTheme.kt` to teal-green palette — Gemini swapped to neon lime / electric cyan which is misaligned with SME target market (deferred by user request)
- [ ] Apply `MaterialColors.harmonizeWithPrimary()` to profit-green / loss-red so they shift toward the user's wallpaper hue
- [ ] `WindowCompat.setDecorFitsSystemWindows(false)` + `Modifier.systemBarsPadding()` for true edge-to-edge

### 6c — Dashboard redesign (fintech style)
- [x] Hero balance card — `ElevatedCard` with gradient showing Net Profit, Revenue, and Cost
- [x] Privacy mode as `CompositionLocal` — masks all amounts app-wide including History tab
- [x] Animated splash screen — spring scale-in with `SensoryBackground`, 1200 ms display
- [x] Contextual quick-action FAB on hero card — time-aware label (Quick Sale / Restock / Close Day)
- [x] Day-close `RitualSummaryDialog` — revenue, profit, and transaction count summary with share action
- [ ] Animated count-up on numbers — `animateFloatAsState(targetValue, tween(800, FastOutSlowInEasing))` so amounts count up smoothly on change
- [ ] Sparkline chart inside the hero card — `CartesianChartHost` from Vico with a `LineCartesianLayer` over the last 7 days of net profit; new `getDailyTotals()` DAO query
- [ ] Shimmer skeleton loading while Room data first loads — `Modifier.shimmer()` on placeholder rows
- [ ] Model setup: segmented arc download progress — `Canvas` drawArc replacing the flat `LinearProgressIndicator`

### 6d — Chat inference UX
- [x] Redesigned input bar — single attach button with contextual menu (image / WAV), full-width text field, Enter-to-send, animated mic↔send toggle
- [x] Per-message long-press menu — Copy, Delete via `DropdownMenu`
- [x] Human-readable agent messages — `message` field extracted from JSON response; raw JSON no longer shown in chat
- [x] Quick-action suggestion chips — Sale, Expense, Stock, Summary pre-fill input
- [ ] Streaming token display — tokens appended to `StringBuilder` via `SharedFlow<String>` as they arrive; `LazyColumn` with `reverseLayout = true`
- [ ] Typing / thinking indicator — three-dot pulse with staggered `InfiniteTransition` while model is running
- [ ] Timestamps on every message — `labelSmall` below each bubble; relative format via `DateUtils.getRelativeTimeSpanString`
- [ ] Lottie success animation on transaction commit — ~48 dp checkmark plays for 1.5 s then auto-dismisses
- [ ] Send button morphs to stop/cancel while processing — icon swap with `AnimatedContent`

### 6e — Motion & onboarding
- [x] Onboarding walkthrough — 4-page first-launch guide with slide+fade transitions
- [x] Staggered model card entry animations — `fadeIn + slideInVertically` with per-index delay in `ModelSetupScreen`
- [x] Haptic feedback — sale clink, expense thud, interaction tick via `HapticManager`
- [ ] Screen transitions — `slideInHorizontally + fadeIn` / `slideOutHorizontally + fadeOut` in `NavHost` `enterTransition`/`exitTransition`
- [ ] Staggered transaction list entry — `tween(200)` with 30 ms per-index offset
- [ ] Onboarding illustrations — replace `Icons.Rounded` placeholders with Lottie files; auto-play, loop=false

### 6f — Performance
- [ ] Replace all `runBlocking` in `DataStoreRepository` with proper `suspend` functions — removes IO blocking on the main thread
- [ ] Baseline Profile — `benchmark-macro-junit4` test walking app open → dashboard → chat send; commit generated `baseline-prof.txt`
- [ ] Enable Compose compiler reports in debug builds; audit unstable composables; wrap `List<T>` in `@Immutable` data wrappers

---

## Phase 7 — Prompt Accuracy & Multi-turn Verification [ next ]

Goal: close the gap between what the user says and what gets written to the database; use conversation to resolve ambiguity instead of silently guessing.

### 7a — System prompt hardening
- [ ] Build a test corpus of ≥ 30 labelled `{input, expected_json}` pairs covering: partial amounts, multi-item receipts, currency ambiguity, SMS M-Pesa shorthand, voice fillers, non-English input, negative corrections ("actually it was 500 not 5000")
- [ ] Automated eval script (`scripts/eval_prompt.py`) — sends each input to the model, diffs actual vs expected JSON, reports per-field accuracy (item, amount, currency, type, quantity, cost, confidence) and overall record accuracy
- [ ] Prompt iteration loop: target ≥ 90 % record accuracy before shipping; document which failure modes required rule changes

### 7b — Multi-turn clarification flow
- [ ] Extend JSON schema with `"action": "clarify"` and `"question": "<one sentence>"` — model emits this when a critical field (amount or transaction direction) is truly ambiguous; must NOT clarify when a reasonable `confidence="medium"` guess is possible
- [ ] In `parseResponse`: if `action == "clarify"`, extract `question`, render as agent bubble with a distinct `⚠` icon, set `clarificationPending = true` — no DB write, no confirmation dialog
- [ ] While `clarificationPending`, the next user message continues the clarification context; model re-processes with prior partial context prepended
- [ ] **Skip button** on the clarification bubble — dismisses pending entry without replying; resets `clarificationPending`
- [ ] System prompt rule additions: clarify when amount is missing entirely or direction is genuinely ambiguous; guess + `confidence="low"` when amount exists but item name is unclear; never clarify for stock-only updates

### 7c — Dashboard accuracy audit
- [ ] Debug overlay (debug builds only) — floating `ElevatedCard` toggled by triple-tap on dashboard; shows raw JSON from last inference, field diff against what was committed, per-field confidence
- [ ] Audit `LedgerViewModel.syncFromTools` revenue/cost/profit formula against all four transaction types plus mixed-batch inputs; add `check(...)` assertions for impossible states
- [ ] Verify Room migration 1→2 (confidence column) runs cleanly on fresh install, upgrade from v1, and after `clearTransactions()`

---

## Phase 1 — Core (complete)

Goal: working chat interface with on-device LiteRT-LM inference and Room persistence.

- [x] Scaffold Gradle project (AGP 8, Kotlin 2, Compose BOM, Hilt, KSP, Room)
- [x] `AndroidManifest.xml` — permissions, FileProvider, WorkManager service
- [x] `LedgerApplication` — Hilt entry point
- [x] `MainActivity` — single-activity Compose nav host
- [x] Data layer — `Model`, `ModelAllowlist`, `Config`, `DataStoreRepository`
- [x] Room database — `TransactionEntity`, `StockEntity`, `LedgerDao`, `LedgerRepository`
- [x] Hilt DI module — `AppModule`
- [x] LiteRT-LM runtime — `LlmChatModelHelper`, `LlmModelHelper`, `LlmModelInstance`
- [x] Common UI — `TextAndVoiceInput`, `HoldToDictate`, `AudioRecorderPanel`, `AudioAnimation`
- [x] `LedgerTools` — tool-call handler (add transaction, update stock, query balance)
- [x] `LedgerViewModel` — inference loop, tool dispatch, Room sync
- [x] `LedgerScreen` — chat UI
- [x] `ModelSetupScreen` / `ModelSetupViewModel` — model list, download, delete
- [x] `DownloadWorker` — resumable HTTP download with Range header support
- [x] `model_allowlist.json` — Gemma 4 E2B and E4B model definitions
- [x] Resources — strings, themes, drawables, `file_paths.xml`

---

## Phase 2 — Dashboard & Export (complete)

Goal: visible financial summary that updates after every transaction.

- [x] Dashboard redesign — expandable Transactions and Inventory sections with `AnimatedVisibility`
- [x] Low-stock alerts — items below threshold highlighted in inventory section
- [x] `LedgerPdfExporter` — A4 PDF (summary + transaction table + inventory table) via `android.graphics.pdf.PdfDocument`, shared through FileProvider
- [x] Robust JSON extraction — find first `{` / last `}` in model output to handle extra prose
- [x] Room persistence fix — pass `CoroutineScope` from `MainActivity` to `LedgerTools`
- [x] System prompt improvements — explicit income/expense direction rules with examples

---

## Phase 3 — Intelligence & Reliability (complete)

Goal: proactive coaching, background work, and crash resilience.

- [x] TTS coaching — `TextToSpeech` with `UtteranceProgressListener`; language fallback; error dialog
- [x] `DailyDigestWorker` + `DailyDigestScheduler` — WorkManager periodic job at 18:00; reads today's transactions from Room; posts channel notification with revenue/profit/count
- [x] Transaction correction loop — delete individual entries from dashboard edit mode
- [x] Auto KV cache reset — `checkAutoReset()` resets LiteRT-LM engine every 8 inferences to prevent native OOM crashes from KV cache growth
- [x] SMS monitoring — `LedgerSmsReceiver` auto-extracts transactions from incoming messages

---

## Phase 4 — Distribution (complete)

Goal: clean, shippable builds with proper secrets management and GitHub releases.

- [x] ABI splits — `arm64-v8a` (devices) and `x86_64` (emulator), no universal APK
- [x] R8 minification + resource shrinking for release builds (~66% smaller than debug)
- [x] `proguard-rules.pro` — keep rules for LiteRT, Room, Hilt, Moshi, WorkManager
- [x] Remove AppAuth dead dependency
- [x] Resume + retry downloads — `PARTIALLY_DOWNLOADED` UI branch with Resume/Discard; FAILED shows error + Retry; IN_PROGRESS shows speed and ETA
- [x] `.gitignore` — excludes `.gradle/`, `build/`, `*.apk`, `.venv/`, `scripts/models/`, large test assets
- [x] `local.properties.example` — onboarding template for contributors
- [x] GitHub pre-release `v1.0.0-beta` — arm64 and x86_64 APKs attached

---

## Phase 5 — UX, Features & Quality (complete)

### 5a — UX polish (complete)
- [x] Currency and locale selection — DataStore-persisted currency picker (11 currencies); propagates to dashboard, TTS, PDF, CSV; system prompt updated dynamically
- [x] Onboarding walkthrough on first launch — 4-page `ModalBottomSheet` (Welcome, Voice/Type, SMS, Photo); slide+fade transitions; `hasSeenOnboarding` flag in DataStore
- [x] Empty-state illustrations for dashboard — faded receipt icon + hint icons (Voice/Photo/SMS) when ledger is empty

### 5b — Features (complete)
- [x] CSV export alongside PDF — `LedgerCsvExporter` produces UTF-8 CSV (Transactions, Summary, Inventory sections); grid icon button added to dashboard header
- [x] Remove `HF_TOKEN` — `litert-community` models are publicly hosted; no account or token required

### 5c — Quality (complete)
- [x] Remove dead Moshi dependency — eliminates kapt deprecation warning
- [x] Remove `-Xcontext-receivers` compiler flag — build is now warning-clean

---

## Phase 6g — Gemini UI integration cleanup (complete)

Fixes to Gemini's UI/UX additions before continuing the build plan.

- [x] Remove duplicate `HapticManager` instantiation — dead allocation in `LedgerMainScreen` removed
- [x] Throttle `SensoryBackground` animation — tween cycle 12 s → 24 s; halves canvas redraws on mid-range devices
- [x] Fix time-based contextual FAB — `remember {}` → `remember(currentHour)` so label recomputes when hour changes
- [x] Trim splash screen delay — 2500 ms → 1200 ms
- [x] Restore explicit imports in `LedgerScreen.kt` — replaced Gemini's six wildcard star imports with named imports

---

## Phase 8 — Advanced Features (after Phases 6 & 7)

- [ ] Multi-account / multi-business support (separate Room databases per account)
- [ ] Background sync to Google Drive (WorkManager + Drive REST API)
- [ ] Recurring transaction detection and reminder
- [ ] Unit tests for `LedgerTools` JSON parsing and transaction classification
- [ ] Instrument tests for Room DAO operations
