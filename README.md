<h1 align="center">Binot (Maintained Fork)</h1>

<p align="center">
  <strong>An actively-maintained fork of the open-source voice note-taking app — fixed, refreshed, and rebuilt for the current AI landscape.</strong>
</p>

<p align="center">
  <a href="https://github.com/DENSLnetion/Binot">
    <img src="https://img.shields.io/badge/upstream-DENSLnetion%2FBinot-2ea44f?style=for-the-badge&logo=github&logoColor=white" alt="Upstream Repository" />
  </a>
  <img src="https://img.shields.io/github/stars/LexicoON/Binot-fix?style=for-the-badge&color=gold" alt="Fork Stars" />
  <img src="https://img.shields.io/github/forks/LexicoON/Binot-fix?style=for-the-badge&color=lightgray" alt="Fork Forks" />
  <img src="https://img.shields.io/github/v/release/LexicoON/Binot-fix?style=for-the-badge&color=blue" alt="Latest Release" />
  <img src="https://img.shields.io/github/license/LexicoON/Binot-fix?style=for-the-badge" alt="License" />
</p>

<br>

<p align="center">
  <em>👉 Please support the original creator: <a href="https://github.com/DENSLnetion/Binot">github.com/DENSLnetion/Binot</a></em>
</p>

<br>

## Image

<img width="1920" height="3234" alt="ResizedImage_2026-06-21_18-10-20_4381" src="https://github.com/user-attachments/assets/f6ea2734-ae8b-4cf1-a493-3ede4b031b11" />

---

## About This Fork

**Binot** is a native Android app that turns spoken words into clean, structured Markdown notes. It listens, transcribes, and then lets an LLM tidy, summarize, or translate the result — all locally stored, all under your control.

This repository, **`LexicoON/Binot-fix`**, is a community-maintained fork of the original project by **[@DENSLnetion](https://github.com/DENSLnetion)**. The original app quietly stopped working as the AI providers retired the model names that were hardcoded into it. This fork exists to keep the app alive, ship releases while the upstream is paused, and — whenever possible — send the fixes back through pull requests so the entire community benefits.

> 🛠️ **What's different here vs. the original?**
> - Migrated to current, supported AI model names so the app actually works again
> - Verified the full pipeline (record → transcribe → process → save) end-to-end on free-tier API keys
> - Releases are published from this fork so you don't have to build from source
> - No proprietary changes — every fix here is offered upstream first

If the upstream maintainer accepts our pull requests, all the work here will be merged into the original project. **In the meantime, please consider starring, following, and supporting [@DENSLnetion on the original repo](https://github.com/DENSLnetion/Binot)** — that's where the project truly lives.

---

## Key Features

- 🎙️ **Intelligent Voice Dictation** — Real-time transcription using Android's native `SpeechRecognizer`, with an automatic system-volume override so notification beeps never make it into your recording.
- 🤖 **Multi-Provider AI Processing** — Choose between **Google Gemini** or **Groq** as your AI backend. Both work on free tiers; the app stays useful even if one provider has an outage.
- 🧠 **Tidy · Summarize · Translate** — One tap cleans up grammar, distills long notes into summaries, or translates the result into English, Indonesian, Spanish, Chinese, or Japanese.
- ➗ **Math Recognition** — Spoken math like "kuadrat" or "integral" is converted into proper Unicode symbols (², ∫) inside the rendered output.
- 🎵 **Audio Import & Playback** — Drop in an existing MP3/audio file for AI transcription, or play back your own recording right inside the note.
- 🗂️ **Local-First Organization** — Custom labels, multi-select, pin, duplicate, and a trash bin with recovery. Your notes never leave your device unless you back them up yourself.
- 📖 **Custom Markdown Renderer** — Headers, lists, bold, italic, and search-highlight — all rendered by an in-house engine tuned for note-taking.
- 💾 **JSON Backup & Restore** — Export your entire database to a single JSON file and bring it back on another device.
- 🔄 **In-App Update Checker** — Notifies you when a new release is out and lets you download the APK directly.

---

## Tech Stack

<p align="left">
  <img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Room_Database-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Room Database" />
  <img src="https://img.shields.io/badge/Retrofit-FF0000?style=for-the-badge&logo=square&logoColor=white" alt="Retrofit" />
  <img src="https://img.shields.io/badge/Gemini_API-8E75B2?style=for-the-badge&logo=googlebard&logoColor=white" alt="Gemini API" />
  <img src="https://img.shields.io/badge/Groq_API-F55036?style=for-the-badge&logo=groq&logoColor=white" alt="Groq API" />
</p>

- **Language:** 100% Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Persistence:** Room Database
- **Networking:** Retrofit + OkHttp + Moshi
- **AI:** Google Gemini API · Groq Cloud API
- **Architecture:** MVVM with unidirectional data flow

---

## How the AI Layer Works

You bring your own API key, your data never leaves the providers you choose, and there's no telemetry. Two providers, both free-tier friendly:

| Provider | What it does here | Models used in this fork |
|---|---|---|
| **Google Gemini** | Long-audio transcription (via Files API) and text processing | `gemini-flash-latest` (auto-tracks Google's current Flash model) |
| **Groq** | Fast Whisper transcription and LLM text processing | `whisper-large-v3-turbo` · `openai/gpt-oss-20b` · `openai/gpt-oss-120b` |

You can switch providers in **Settings → AI Provider**. Both options are valid on a free account.

---

## Releases

APKs for this fork are published on the [Releases page](https://github.com/LexicoON/Binot-fix/releases). The in-app updater will also notify you when a new build is available.

For builds of the original project, see the upstream [releases](https://github.com/DENSLnetion/Binot/releases).

---

## Building From Source

Want to compile it yourself? You'll need Android Studio (Koala or newer) and a free API key from either Google AI Studio or Groq Cloud.

### 1. Clone

```bash
git clone https://github.com/LexicoON/Binot-fix.git
cd Binot-fix
