# Ledger — Build Plan

Full implementation plan for the Ledger standalone Android app, from scaffold to release.

---

## Phase 6 — UI Modernization [ next ]

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
- [ ] Replace single-screen nav with a `NavigationBar` (bottom nav) with four tabs: **Home** (dashboard + chat), **History** (full transaction log), **Inventory**, **Settings**
- [ ] Type-safe routes using `@Serializable` data objects — `androidx.navigation:navigation-compose:2.8+` with `kotlin.serialization`; no more string route literals
- [ ] Per-tab back stacks via nested nav graphs; `rememberSaveable` scroll state per tab
- [ ] Collapsing `TopAppBar` on Home — `LargeTopAppBar` with `enterAlwaysScrollBehavior()` so it shrinks on scroll and snaps back on fling
- [ ] Enable predictive back on API 35+ (already free with Navigation Compose 2.8); add `android:enableOnBackInvokedCallback="true"` to manifest

### 6b — Design system & theming
- [ ] Material You dynamic color — `dynamicDarkColorScheme(ctx)` / `dynamicLightColorScheme(ctx)` on API 31+; curated teal/indigo seed palette fallback for older devices
- [ ] Full dark mode — `isSystemInDarkTheme()` branch in `LedgerTheme`; audit every hard-coded `Color(0xFF...)` constant in `LedgerScreen` and replace with M3 color roles (`MaterialTheme.colorScheme.error`, `tertiary`, etc.)
- [ ] Apply `MaterialColors.harmonizeWithPrimary()` to the semantic profit-green / loss-red colours so they shift toward the user's wallpaper hue without clashing
- [ ] Consistent type scale — `displaySmall` for dashboard totals, `titleMedium` for card headers, `bodySmall` for list rows, `labelSmall` for metadata
- [ ] Icon audit — all icons already `Icons.Rounded`; verify no `Icons.Default` or `Icons.Filled` stragglers
- [ ] `WindowCompat.setDecorFitsSystemWindows(false)` + `Modifier.systemBarsPadding()` for true edge-to-edge; remove Accompanist `SystemUiController` if still present

### 6c — Dashboard redesign (fintech style)
- [ ] Hero balance card at the top: single `ElevatedCard` with subtle gradient (`Brush.linearGradient`) showing Net Profit in `displaySmall` typography; Revenue and Cost as secondary rows below
- [ ] Animated count-up on numbers: `animateFloatAsState(targetValue, tween(800, FastOutSlowInEasing))` so amounts count up smoothly whenever they change
- [ ] Privacy blur: upgrade current `••••` replacement to `Modifier.blur(12.dp)` (API 31+) with the `••••` fallback for API < 31; move `numbersHidden` to a `CompositionLocal` so it propagates into History tab without prop-drilling
- [ ] Sparkline chart inside the hero card: `CartesianChartHost` from Vico with a `LineCartesianLayer` over the last 7 days of net profit — renders from Room data via a new `getDailyTotals()` DAO query
- [ ] Transaction feed in History tab grouped by date: `stickyHeader {}` in `LazyColumn` with date section headers; `SwipeToDismissBox` (built-in M3) for swipe-delete with red background and trash icon revealed
- [ ] Leading colour stripe on each transaction row: a 3 dp wide `Box` with green (`Color(0xFF2E7D32)`) for sale/income, red (`Color(0xFFC62828)`) for purchase/expense
- [ ] Shimmer skeleton loading while Room data first loads: `Modifier.shimmer()` from compose-shimmer on placeholder rows
- [ ] Model setup: segmented arc download progress — `Canvas` drawArc sweeping from 270° proportional to download progress, replacing the flat `LinearProgressIndicator`

### 6d — Chat inference UX
- [ ] Streaming token display — as each token arrives (via a `SharedFlow<String>` from the inference coroutine), append to a `StringBuilder` in state and recompose; use `LazyColumn` with `reverseLayout = true` so new tokens appear at the bottom naturally
- [ ] Typing / thinking indicator while model is running: three-dot pulse using `InfiniteTransition` with staggered `animateFloat` (0 ms / 160 ms / 320 ms delay on each dot), shown in the model's chat bubble slot
- [ ] Per-message action row on long-press: **Copy** (`LocalClipboardManager`), **Retry** (re-sends last user message), **Delete** — appears as a `DropdownMenu` anchored to the bubble
- [ ] Timestamps on every message: shown as `labelSmall` below each bubble; relative format ("just now", "2 min ago") using `DateUtils.getRelativeTimeSpanString`
- [ ] Lottie success animation on transaction commit: a small (~48 dp) checkmark Lottie plays for 1.5 s over the chat area after a successful `add_transaction` — then auto-dismisses
- [ ] Message input area: `OutlinedTextField` replaced by a custom pill-shaped container matching the chat bubble style; send button morphs to a stop/cancel button while processing (icon swap with `AnimatedContent`)

### 6e — Motion & onboarding
- [ ] Screen transitions: `slideInHorizontally + fadeIn` / `slideOutHorizontally + fadeOut` wired into `NavHost`'s `enterTransition`/`exitTransition`
- [ ] Staggered list entry: items in transaction feed fade+translateY(20dp→0) with `tween(200)` and a 30 ms per-index offset using `LaunchedEffect(index)`
- [ ] Onboarding illustrations: replace static `Icons.Rounded` placeholders with Lottie files (finance/accounting themed — free ones at lottiefiles.com); auto-play on each page, loop=false
- [ ] Haptic feedback: `HapticFeedbackType.LongPress` on transaction commit; `HapticFeedbackType.TextHandleMove` on swipe-delete confirmation

### 6f — Performance
- [ ] Baseline Profile: add `androidx.benchmark:benchmark-macro-junit4` test that walks app open → dashboard → chat send; run `./gradlew generateBaselineProfile`; commit generated `baseline-prof.txt`
- [ ] Enable Compose compiler reports (`reportsDestination`) in debug builds; audit any unstable/non-skippable composables and wrap `List<T>` in `@Immutable` data wrappers
- [ ] Replace all `runBlocking` in `DataStoreRepository` with proper `suspend` functions (use `Flow.first()` in calling coroutine instead); this removes IO blocking on the main thread

---

## Phase 7 — Prompt Accuracy & Multi-turn Verification [ next ]

Goal: close the gap between what the user says and what gets written to the database; use conversation to resolve ambiguity instead of silently guessing.

### 7a — System prompt hardening
- [ ] Build a test corpus of ≥ 30 labelled `{input, expected_json}` pairs covering: partial amounts, multi-item receipts, currency ambiguity, SMS M-Pesa shorthand, voice fillers, non-English input, negative corrections ("actually it was 500 not 5000")
- [ ] Automated eval script (`scripts/eval_prompt.py`) — sends each input to the model, diffs actual vs expected JSON, reports per-field accuracy (item, amount, currency, type, quantity, cost, confidence) and overall record accuracy
- [ ] Prompt iteration loop: target ≥ 90 % record accuracy before shipping; document which failure modes required rule changes

### 7b — Multi-turn clarification flow
- [ ] Extend JSON schema with `"action": "clarify"` and `"question": "<one sentence>"` — model emits this when a critical field (amount or transaction direction) is truly ambiguous; it must NOT clarify when it can make a reasonable `confidence="medium"` guess
- [ ] In `parseResponse`: if `action == "clarify"`, extract `question`, render it as an agent chat bubble with a distinct `⚠` icon, and set a `clarificationPending = true` flag — no DB write, no confirmation dialog
- [ ] While `clarificationPending`, the next user message is treated as a continuation of the clarification context; the model re-processes with the prior partial context prepended
- [ ] **Skip button** on the clarification bubble — dismisses pending entry without replying; resets `clarificationPending`
- [ ] System prompt rule additions: clarify when amount is missing entirely or direction is genuinely ambiguous; guess+`confidence="low"` when amount exists but item name is unclear; never clarify for stock-only updates

### 7c — Dashboard accuracy audit
- [ ] Debug overlay (debug builds only): floating `ElevatedCard` toggled by a triple-tap on the dashboard that shows the raw JSON from the last inference, field diff against what was committed, and per-field confidence — makes testing prompt changes fast without logcat
- [ ] Audit `LedgerViewModel.syncFromTools` revenue/cost/profit formula against all four transaction types plus mixed-batch inputs; add inline assertions (`check(...)`) for impossible states (negative revenue from sales, etc.)
- [ ] Verify Room migration 1→2 (confidence column) runs cleanly on a fresh install, an upgrade from v1, and after a `clearTransactions()` call

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
- [x] `local.properties` secrets — `HF_TOKEN` injected as `BuildConfig` field at build time
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
- [x] Empty-state illustrations for dashboard — faded receipt icon + hint icons (Voice/Photo/SMS) when ledger is empty; inline empty message in Transaction section

### 5b — Features (complete)
- [x] CSV export alongside PDF — `LedgerCsvExporter` produces UTF-8 CSV (Transactions, Summary, Inventory sections); grid icon button added to dashboard header
- [x] Remove `HF_TOKEN` — `litert-community` models are publicly hosted; no account or token required; removed from `BuildConfig`, `DownloadRepository`, and `local.properties.example`

### 5c — Quality (complete)
- [x] Remove dead Moshi dependency — `moshi-kotlin` and `moshi-kotlin-codegen` were unused (project uses `org.json.JSONObject`); eliminates kapt deprecation warning
- [x] Remove `-Xcontext-receivers` compiler flag — no context receiver syntax in codebase; build is now warning-clean

---

## Phase 6g — Gemini UI integration cleanup [ complete ]

Fixes to Gemini's UI/UX additions before continuing the build plan.

- [x] Remove duplicate `HapticManager` instantiation — `LedgerMainScreen` had a dead `hapticManager` allocation; only `LedgerMainUi` needs it
- [x] Throttle `SensoryBackground` animation — tween cycle increased from 12 s to 24 s to halve canvas redraws and reduce battery drain on mid-range devices
- [x] Fix time-based contextual FAB — `remember {}` without a key would lock the label at first-compose time; changed to `remember(currentHour)` so it recomputes when the hour changes
- [x] Trim splash screen delay — 2500 ms forced wait reduced to 1200 ms
- [x] Restore explicit imports in `LedgerScreen.kt` — replaced Gemini's wildcard star imports (`animation.*`, `material3.*`, `runtime.*`, `layout.*`, `rounded.*`, `graphics.*`) with named imports matching the rest of the codebase
- [ ] Revert `LedgerTheme.kt` color palette — "Modern Air" / "Deep Obsidian" (black + neon lime + electric cyan) is misaligned with the SME target market; restore forest teal-green M3 palette (deferred by user request)

---

## Phase 8 — Advanced Features (after Phases 6 & 7)

- [ ] Multi-account / multi-business support (separate Room databases per account)
- [ ] Background sync to Google Drive (WorkManager + Drive REST API)
- [ ] Recurring transaction detection and reminder
- [ ] Unit tests for `LedgerTools` JSON parsing and transaction classification
- [ ] Instrument tests for Room DAO operations
