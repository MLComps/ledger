# Ledger

An on-device AI business partner for Android. Ledger uses Google's LiteRT-LM runtime to run Gemma 4 entirely on-device — no cloud, no data leaving the phone.

Speak a transaction, forward a mobile money SMS, or photograph a receipt. Gemma 4 extracts the structured data, updates the ledger, and when asked, delivers specific business recommendations based on that day's real numbers. No forms. No fields. Just conversation.

Built for the [Gemma 4 Good Hackathon](https://www.kaggle.com/competitions/gemma-4-good-hackathon) on Kaggle.

---

## Features

- **Five input modes** — type, speak, forward an SMS, photograph a receipt, or attach a WAV audio file; all wired into one extraction pipeline
- **Multilingual voice** — handles English, Swahili, mixed-language input, regional accents, filler words, and Swahili number words (mia mbili = 200, elfu tano = 5000)
- **Multi-turn clarification** — when an amount is missing the model asks one follow-up question rather than guessing; conversation context is maintained via the LiteRT-LM KV cache
- **Business recommendations** — dedicated recommendations engine pulls live revenue, cost, profit, and inventory data and returns 3 to 5 specific actionable recommendations referencing the user's actual item names and amounts
- **On-device inference** — Gemma 4 via LiteRT-LM; no internet required after model download
- **Multi-tab UI** — Home (chat + balance card), History, Inventory, Settings
- **Hero balance card** — live revenue, cost, and net profit with a gradient card; privacy mode masks all amounts app-wide with one toggle
- **Transaction history** — date-grouped list with swipe-to-delete and confidence indicators
- **Inventory tracking** — per-item stock levels with low-stock badges and alerts
- **Confidence-based validation** — configurable confirmation dialog for low-confidence extractions before writing to the database
- **SMS monitoring** — opt-in background listener auto-extracts transactions from incoming mobile money confirmations (M-Pesa, MTN MoMo)
- **CSV and PDF export** — shareable A4 PDF report and structured CSV (transactions, summary, inventory); designed for microloan and tax use cases
- **11 currencies** — KES, NGN, GHS, UGX, TZS, ETB, ZAR, RWF, USD, EUR, GBP; persisted and applied everywhere including exports
- **Daily digest** — WorkManager notification at 18:00 with spoken TTS summary of the day's revenue, expenses, and profit
- **Onboarding walkthrough** — 4-page first-launch guide covering voice entry, SMS monitoring, and image input
- **Persistent storage** — Room database with migrations; full transaction and stock history survives app restarts

---

## Models

Both models are Gemma 4 — multimodal (text + image + audio), 32K context, thinking mode, speculative decoding.

| Model | Size | Best for |
|---|---|---|
| Gemma 4 E2B | ~2.4 GB | Mid-range devices, faster inference |
| Gemma 4 E4B | ~3.7 GB | Higher-end devices, better accuracy |

Models are downloaded from HuggingFace on first use via the `litert-community` organisation. No account or token required — the files are publicly hosted.

---

## Prompt Accuracy

The extraction pipeline is evaluated against a 42-case corpus covering sales, purchases, expenses, income, mobile money SMS, Swahili input, multi-item messages, corrections, clarification sequences, stock updates, and off-topic inputs.

A semantic LLM judge handles language equivalences (sukari = sugar, bob = KES, mia moja = 100) so regional phrasing does not cause false failures.

| Eval | Score |
|---|---|
| Single-turn (32 cases) | 100% |
| Combined purchase + stock (5 cases) | 100% |
| Multi-turn clarification (5 cases) | 100% |
| **Overall (42 cases, with judge)** | **97%** |

---

## Getting Started

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
   ```
   sdk.dir=/path/to/your/Android/Sdk
   ```

3. Open `ledger_standalone/Android/src` in Android Studio and run on a device or emulator (API 31+).

4. On first launch, select a model and tap **Download**. Model files are publicly hosted on HuggingFace — no account or token required. Once downloaded the model stays on-device.

---

## Installing a Pre-Built APK

Pre-built APKs are published under [Releases](https://github.com/MLComps/ledger/releases). Two variants are provided per release:

| APK | Target |
|---|---|
| `ledger-v1.2.0-arm64-v8a.apk` | Physical Android devices |
| `ledger-v1.2.0-x86_64.apk` | Android emulator (x86_64 AVD) |

Install via ADB:
```bash
adb install ledger-v1.2.0-arm64-v8a.apk   # physical device
adb install ledger-v1.2.0-x86_64.apk       # emulator
```

---

## Project Structure

```
ledger_standalone/Android/src/
├── app/src/main/
│   ├── java/com/ledger/app/
│   │   ├── common/        # HapticManager and app-wide utilities
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
│   │   │   ├── settings/  # Settings tab — currency, privacy, validation
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

## Build Variants

Two ABI-split APKs are produced per build:

| Output | ABI | Use |
|---|---|---|
| `app-arm64-v8a-release.apk` | arm64-v8a | Physical devices |
| `app-x86_64-release.apk` | x86_64 | Emulator |

Release builds have R8 minification and resource shrinking enabled.

```bash
./gradlew assembleDebug    # debug
./gradlew assembleRelease  # release, ABI-split
```

APKs are written to `app/build/outputs/apk/`.

---

## Emulator Notes

LiteRT-LM inference is memory-intensive. For stable emulator sessions:

- Use at least 4 GB RAM in the AVD config (`hw.ramSize=4096`)
- Clear host swap before a session: `sudo swapoff -a && sudo swapon -a`
- Use the `x86_64` APK — the arm64 APK runs through Houdini translation on x86_64 emulators and crashes LiteRT-LM

Real devices are recommended for extended use.

---

## Eval Scripts

The `scripts/` directory contains the prompt evaluation harness. See [`scripts/README.md`](scripts/README.md) for full usage.

```bash
cd scripts
uv sync
uv run eval_comprehensive.py --backend openrouter --api-key YOUR_KEY --judge-key YOUR_KEY
```

---

## Stack

Kotlin, Jetpack Compose, Hilt DI, Room, WorkManager, DataStore, LiteRT-LM, Gemma 4
