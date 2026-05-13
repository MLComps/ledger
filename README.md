# Ledger

An on-device AI bookkeeping assistant for Android. Ledger uses Google's LiteRT-LM runtime to run Gemma 4 models entirely on-device — no cloud, no data leaving the phone.

You talk to Ledger in plain language (text, voice, SMS, or photo), and it extracts transactions and stock updates automatically, maintaining a running ledger with a live dashboard.

Built for the [Gemma 4 Good Hackathon](https://www.kaggle.com/competitions/gemma-4-good-hackathon) on Kaggle.

---

## Features

- **Natural language entry** — type, speak, forward an SMS, or photograph a receipt
- **On-device inference** — Gemma 4 via LiteRT-LM; no internet required after model download
- **Live dashboard** — expandable transaction history and inventory view with low-stock alerts
- **CSV & PDF export** — shareable A4 PDF report and a structured CSV (transactions, summary, inventory)
- **Currency selection** — choose from 11 currencies (KES, NGN, GHS, UGX, TZS, ETB, ZAR, RWF, USD, EUR, GBP); persisted across sessions and applied to dashboard, TTS, and exports
- **Onboarding walkthrough** — 4-page first-launch guide covering voice entry, SMS monitoring, and image input
- **TTS coaching** — spoken daily summary of revenue, expenses and profit
- **Daily digest** — WorkManager notification at 18:00 with the day's totals
- **Transaction correction** — delete individual entries directly from the dashboard
- **Persistent storage** — Room database; survives app restarts

---

## Models

Both models are Gemma 4 — multimodal (text + image + audio), 32K context, thinking mode, speculative decoding.

| Model | Size | Best for |
|---|---|---|
| Gemma 4 E2B | ~2.4 GB | Mid-range devices, faster inference |
| Gemma 4 E4B | ~3.7 GB | Higher-end devices, better accuracy |

Models are downloaded from HuggingFace on first use. Gemma 4 is gated — a HuggingFace account and acceptance of the model licence are required before downloading.

---

## Getting started

### Prerequisites

- Android Studio Meerkat or later
- Android SDK 35, min SDK 31 (Android 12)
- A HuggingFace account with Gemma 4 access ([request here](https://huggingface.co/google/gemma-4))

### Setup

1. Clone the repo:
   ```bash
   git clone git@github.com:MLComps/ledger.git
   cd ledger/ledger_standalone/Android/src
   ```

2. Copy the secrets template and fill in your values:
   ```bash
   cp local.properties.example local.properties
   ```
   Edit `local.properties`:
   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   HF_TOKEN=hf_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```
   `HF_TOKEN` is your HuggingFace [read token](https://huggingface.co/settings/tokens). It is injected at build time and used only for the initial model download — it is never stored on-device after the download completes.

3. Open `ledger_standalone/Android/src` in Android Studio and run on a device or emulator (API 31+).

4. On first launch, select a model and tap **Download**. Once downloaded, the model stays on-device.

---

## Installing a pre-built APK

Pre-built APKs are published under [Releases](https://github.com/MLComps/ledger/releases). Two variants are provided per release:

| APK | Target |
|---|---|
| `ledger-arm64-v8a-release.apk` | Physical Android devices (most phones) |
| `ledger-x86_64-release.apk` | Android emulator (x86_64 AVD) |

Install via ADB:
```bash
adb install ledger-arm64-v8a-release.apk   # real device
adb install ledger-x86_64-release.apk       # emulator
```

---

## Project structure

```
ledger_standalone/Android/src/
├── app/src/main/
│   ├── java/com/ledger/app/
│   │   ├── data/          # Model metadata, download, DataStore
│   │   ├── db/            # Room database, DAOs, repository
│   │   ├── di/            # Hilt modules
│   │   ├── llm/           # LiteRT-LM chat helper
│   │   ├── runtime/       # Model lifecycle (load/unload)
│   │   ├── ui/
│   │   │   ├── ledger/    # Main chat + dashboard screen, tools, PDF export
│   │   │   ├── modelsetup/# Model download screen
│   │   │   └── common/    # Voice input, audio animation
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

Release builds have R8 minification and resource shrinking enabled. Debug builds (`./gradlew assembleDebug`) produce a universal APK with the `.debug` application ID suffix so both can be installed side-by-side.

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

- [ ] HuggingFace OAuth login flow (in-app token acquisition)
- [ ] Multi-account / multi-business support
- [ ] Background sync to Google Drive
- [ ] Recurring transaction detection and reminders
- [ ] Unit and instrument tests for core logic

---

## License

Licensed under the [Apache License 2.0](LICENSE).

This means you are free to use, modify, and distribute this project — including for commercial purposes — provided you retain the copyright notice and licence text. An explicit patent grant is included.

The Gemma 4 model weights are separately licensed under the [Gemma Terms of Use](https://ai.google.dev/gemma/terms). You must accept those terms on HuggingFace before downloading the models.
