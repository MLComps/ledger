# Ledger

An on-device AI bookkeeping assistant for Android. Ledger uses Google's LiteRT-LM runtime to run Gemma 4 models entirely on-device — no cloud, no data leaving the phone.

You talk to Ledger in plain language (text, voice, SMS, or photo), and it extracts transactions and stock updates automatically, maintaining a running ledger with a live dashboard.

Built for the [Gemma 4 Good Hackathon](https://www.kaggle.com/competitions/gemma-4-good-hackathon) on Kaggle.

---

## Features

- **Natural language entry** — type, speak, forward an SMS, or photograph a receipt; audio clips and WAV files also supported
- **On-device inference** — Gemma 4 via LiteRT-LM; no internet required after model download
- **Multi-tab UI** — Home (chat + balance), History, Inventory, Settings; smooth fade/slide transitions
- **Hero balance card** — animated revenue, cost, and net profit with a gradient card; privacy mode masks all amounts app-wide
- **Transaction history** — date-grouped list with swipe-to-delete and confidence indicators
- **Inventory tracking** — per-item stock levels with low-stock badges and alerts
- **Confidence-based validation** — configurable confirmation dialog for low-confidence or all extractions before writing to the database
- **SMS monitoring** — opt-in background listener auto-extracts transactions from incoming messages
- **CSV & PDF export** — shareable A4 PDF report and a structured CSV (transactions, summary, inventory)
- **Currency selection** — 11 currencies (KES, NGN, GHS, UGX, TZS, ETB, ZAR, RWF, USD, EUR, GBP); persisted and applied everywhere
- **TTS coaching** — spoken summary of revenue, expenses, and profit
- **Daily digest** — WorkManager notification at 18:00 with the day's totals
- **Persistent storage** — Room database with migrations; survives app restarts
- **Onboarding walkthrough** — 4-page first-launch guide covering voice entry, SMS monitoring, and image input

---

## Models

Both models are Gemma 4 — multimodal (text + image + audio), 32K context, thinking mode, speculative decoding.

| Model | Size | Best for |
|---|---|---|
| Gemma 4 E2B | ~2.4 GB | Mid-range devices, faster inference |
| Gemma 4 E4B | ~3.7 GB | Higher-end devices, better accuracy |

Models are downloaded from HuggingFace on first use via the `litert-community` organisation. No account or token required — the files are publicly hosted.

---

## Getting started

### Prerequisites

- Android Studio Meerkat or later
- Android SDK 35, min SDK 31 (Android 12)

### Setup

1. Clone the repo:
   ```bash
   git clone git@github.com:MLComps/ledger.git
   cd ledger/ledger_standalone/Android/src
   ```

2. Copy the SDK path template:
   ```bash
   cp local.properties.example local.properties
   ```
   Edit `local.properties` and set your SDK path:
   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   ```

3. Open `ledger_standalone/Android/src` in Android Studio and run on a device or emulator (API 31+).

4. On first launch, select a model and tap **Download**. The `litert-community` model files are publicly hosted on HuggingFace — no account or token required. Once downloaded, the model stays on-device.

---

## Installing a pre-built APK

Pre-built APKs are published under [Releases](https://github.com/MLComps/ledger/releases). Two variants are provided per release:

| APK | Target |
|---|---|
| `ledger-v1.1.0-arm64-v8a.apk` | Physical Android devices (most phones) |
| `ledger-v1.1.0-x86_64.apk` | Android emulator (x86_64 AVD) |

Install via ADB:
```bash
adb install ledger-v1.1.0-arm64-v8a.apk   # real device
adb install ledger-v1.1.0-x86_64.apk       # emulator
```

---

## Project structure

```
ledger_standalone/Android/src/
├── app/src/main/
│   ├── java/com/ledger/app/
│   │   ├── common/        # HapticManager and other app-wide utilities
│   │   ├── data/          # Model metadata, download, DataStore
│   │   ├── db/            # Room database, DAOs, repository
│   │   ├── di/            # Hilt modules
│   │   ├── llm/           # LiteRT-LM chat helper
│   │   ├── runtime/       # Model lifecycle (load/unload)
│   │   ├── ui/
│   │   │   ├── nav/       # Bottom nav scaffold, type-safe routes
│   │   │   ├── ledger/    # Home tab — chat, hero card, tools, exports
│   │   │   ├── history/   # History tab — date-grouped transaction list
│   │   │   ├── inventory/ # Inventory tab — stock levels and alerts
│   │   │   ├── settings/  # Settings tab — privacy, currency, validation, HF login
│   │   │   ├── modelsetup/# Model download screen
│   │   │   ├── onboarding/# First-launch walkthrough
│   │   │   ├── theme/     # Material 3 theme, typography, privacy CompositionLocal
│   │   │   └── common/    # Shared composables: splash, background, chat types, voice input
│   │   └── worker/        # DownloadWorker, DailyDigestWorker
│   └── res/
├── local.properties.example
└── proguard-rules.pro
```

---

## Build variants

Two ABI-split release APKs are produced by `./gradlew assembleRelease`:

| Output file | ABI | Use |
|---|---|---|
| `app-arm64-v8a-release.apk` | arm64-v8a | Physical devices |
| `app-x86_64-release.apk` | x86_64 | Emulator |

Release builds have R8 minification and resource shrinking enabled. Debug builds (`./gradlew assembleDebug`) are also ABI-split and share the same application ID (`com.ledger.app`) — installing a debug build replaces the release build and vice versa.

To build:
```bash
# Debug (universal, for quick install during development)
./gradlew assembleDebug

# Release (ABI-split, optimised)
./gradlew assembleRelease
```

APKs are written to `app/build/outputs/apk/`.

---

## Emulator notes

LiteRT-LM inference is memory-intensive. For stable emulator sessions:

- Use at least 4 GB RAM in the AVD config (`hw.ramSize=4096`)
- Clear host swap before a session: `sudo swapoff -a && sudo swapon -a`
- The app auto-resets the model engine every 8 inferences to prevent KV cache growth crashes

Real devices are strongly recommended for extended use.

---

## Roadmap

**Shipped**
- [x] Natural language transaction entry (text, voice, SMS, image, audio)
- [x] On-device Gemma 4 inference via LiteRT-LM
- [x] Room database with full transaction and stock history
- [x] CSV and PDF export
- [x] TTS daily summary and WorkManager digest notification
- [x] Confidence-based validation before writing to the database
- [x] Multi-tab UI with History, Inventory, and Settings screens
- [x] Privacy mode — hide all amounts app-wide
- [x] HuggingFace OAuth2 PKCE login (wired in Settings; not required for current public models)

**Next**
- [ ] Prompt accuracy hardening — edge case testing, currency inference, multi-item extractions
- [ ] Multi-turn clarification — ask a follow-up when a transaction is ambiguous rather than guessing
- [ ] Smarter context window — summarise older turns to stay within the 32K token limit

**Later**
- [ ] Multi-account / multi-business support
- [ ] Recurring transaction detection
- [ ] Background sync to Google Drive or local backup
- [ ] Unit and integration tests for core logic

---
