# Ledger — Build Plan

Full implementation plan for the Ledger standalone Android app, from scaffold to release.

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

## Phase 5 — Pending

### 5a — UX polish
- [ ] Currency and locale selection (stored in DataStore, applied to all formatting)
- [ ] Onboarding walkthrough on first launch (explain voice entry, SMS forwarding, image input)
- [ ] Empty-state illustrations for dashboard when no transactions recorded

### 5b — Features
- [ ] CSV export alongside PDF
- [ ] In-app HuggingFace OAuth login (replace manual token entry)
- [ ] Multi-account / multi-business support (separate Room databases per account)
- [ ] Background sync to Google Drive (WorkManager + Drive REST API)
- [ ] Recurring transaction detection and reminder

### 5c — Quality
- [ ] Unit tests for `LedgerTools` JSON parsing and transaction classification
- [ ] Instrument tests for Room DAO operations
- [ ] Replace deprecated `-Xcontext-receivers` with `-Xcontext-parameters` (Kotlin 2.2+)
- [ ] Migrate Moshi kapt codegen to KSP (kapt deprecation warning in current build)
