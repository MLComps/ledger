# Ledger

An on-device AI bookkeeping assistant for Android. Ledger uses Google's LiteRT-LM runtime to run Gemma models entirely on-device — no cloud, no data leaving the phone.

You talk to Ledger in plain language (text, voice, SMS, or photo), and it extracts transactions and stock updates automatically, maintaining a running ledger with a live dashboard.

---

## Features

- **Natural language entry** — type, speak, forward an SMS, or photograph a receipt
- **On-device inference** — Gemma 3 / 3n / 4 via LiteRT-LM; no internet required after model download
- **Live dashboard** — expandable transaction history and inventory view with low-stock alerts
- **PDF export** — shareable A4 report with transaction and inventory tables
- **TTS coaching** — spoken daily summary of revenue, expenses and profit
- **Daily digest** — WorkManager notification at 18:00 with the day's totals
- **Transaction correction** — delete individual entries directly from the dashboard
- **Persistent storage** — Room database; survives app restarts

---

## Models

| Model | Size | Modalities | Notes |
|---|---|---|---|
| Gemma 3 1B IT q4 | ~530 MB | Text | Fast, works on entry-level devices |
| Gemma 3n E2B int4 | ~3 GB | Text + Image | 4096 context |
| Gemma 4 E2B | ~2.4 GB | Text + Image + Audio | 32K context, thinking mode |

Models are downloaded from HuggingFace on first use. Gemma models are gated — a HuggingFace account and acceptance of the model licence are required.

---

## Getting started

### Prerequisites

- Android Studio Meerkat or later
- Android SDK 35, min SDK 31 (Android 12)
- A HuggingFace account with access to the Gemma model family

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
   REDIRECT_URL_SCHEME=com.ledger.app
   ```
   `HF_TOKEN` is your HuggingFace [read token](https://huggingface.co/settings/tokens). It is injected at build time as `BuildConfig.HF_TOKEN` and used only for the model download — it is never stored on-device after the download completes.

3. Open `ledger_standalone/Android/src` in Android Studio and run on a device or emulator (API 31+).

4. On first launch, select a model and tap **Download**. Once downloaded, the model stays on-device.

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
└── local.properties.example
```

---

## Emulator notes

LiteRT-LM inference is memory-intensive. For stable emulator sessions:

- Use at least 4 GB RAM in the AVD config (`hw.ramSize=4096`)
- Clear host swap before a session: `sudo swapoff -a && sudo swapon -a`
- The app auto-resets the model engine every 8 inferences to prevent KV cache growth crashes

Real devices are strongly recommended for extended use.

---

## Licence

See [LICENSE](LICENSE).
