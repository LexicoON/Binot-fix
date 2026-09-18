<h1 align="center">Obinot</h1>

<p align="center">
  <strong>An actively-maintained community fork of Binot — refreshed, modernized, and rebuilt for the current Material 3 Expressive + AI landscape.</strong>
</p>

<p align="center">
  <a href="https://github.com/DENSLnetion/Binot">
    <img src="https://img.shields.io/badge/upstream-DENSLnetion%2FBinot-2ea44f?style=for-the-badge&logo=github&logoColor=white" alt="Upstream Repository" />
  </a>
  <img src="https://img.shields.io/badge/fork-LexicoON%2FBinot--fix-blue?style=for-the-badge&logo=github&logoColor=white" alt="This Fork" />
  <img src="https://img.shields.io/github/v/release/LexicoON/Binot-fix?style=for-the-badge&color=blue" alt="Latest Release" />
  <img src="https://img.shields.io/github/license/LexicoON/Binot-fix?style=for-the-badge" alt="License" />
</p>

<br>

<p align="center">
  <em>👉 Please support the original creator: <a href="https://github.com/DENSLnetion/Binot">github.com/DENSLnetion/Binot</a></em>
</p>

<br>

## Image

<img width="1920" height="3234" alt="Screenshot" src="https://github.com/user-attachments/assets/f6ea2734-ae8b-4cf1-a493-3ede4b031b11" />

---

## About This Fork

**Obinot** is a native Android app that turns spoken words into clean, structured Markdown notes. It listens, transcribes, and then lets an AI backend tidy, summarize, or translate the result — all stored locally, all under your control.

This repository, **`LexicoON/Binot-fix`**, is a community-maintained fork of the original project by **[@DENSLnetion](https://github.com/DENSLnetion)**. The original app quietly stopped working when the AI providers retired the model names that were hardcoded into it, and the upstream repository has been largely inactive since. This fork exists to:

1. **Keep the app alive** — Migrate to current, supported AI models and verify the full pipeline end-to-end.
2. **Modernize the UX** — Adopt Material 3 Expressive, add color palette customization, improve animations, and refresh the overall experience.
3. **Add quality-of-life features** — Auto audio compression, cloud-free diagram rendering, offline-first behavior, and more.
4. **Give back** — Every change that makes sense upstream is offered as a pull request to the original repository. When the upstream maintainer accepts our PRs, the work here will be merged back.

> 🛠️ **What's different here vs. the original Binot?**
> - Migrated to current, supported AI model names (Gemini Flash, Groq Whisper, etc.)
> - **Material 3 Expressive** — bouncy buttons, spring physics, morphing FABs, animated toggle groups
> - **Color palette styles** — Tonal Spot, Vibrant, Expressive, Rainbow, Neutral, generated locally via MaterialKolor
> - **Label colors** — assign a color to each label so they're easy to scan in History
> - **Auto Compression** — long recordings are compressed automatically before upload so they fit provider limits
> - **Mix Mode 2.0** — smart provider routing that spreads load across Gemini and Groq to make free quotas last longer
> - **Enhanced Markdown reader** — supports tables, code blocks, blockquotes, links, KaTeX math, and Mermaid diagrams (all bundled offline)
> - **Per-task model selection** — Lite models for titles and explanations, full models for heavy lifting
> - **Foreground recording notification** — shows elapsed time while recording in the background

**A note on the name:** The upstream project is *Binot*. This fork is *Obinot* — a distinct name chosen deliberately so both apps can coexist on a device without collisions in the launcher, in file associations, or in future distributions. The internal package ID, file format (`.binot`), and database schema remain compatible, so data flows freely between both.

**In the meantime, please consider starring, following, and supporting [@DENSLnetion on the original repo](https://github.com/DENSLnetion/Binot)** — that's where the project truly lives.

---

## Key Features

- 🎙️ **Intelligent Voice Dictation** — Real-time transcription using Android's native `SpeechRecognizer`, with an automatic system-volume override so notification beeps never make it into your recording.
- 🤖 **Multi-Provider AI Processing** — Choose between **Google Gemini**, **Groq**, or **Mix Mode** as your AI backend. Mix automatically routes each task to the best provider so free quotas last longer.
- 🧠 **Tidy · Summarize · Translate** — One tap cleans up grammar, distills long notes into summaries, or translates the result into English, Indonesian, Spanish, Chinese, Japanese, and more.
- ➗ **Math & Chemistry Rendering** — KaTeX bundled offline renders inline and block equations, including `\ce{}` chemical formulas.
- 📊 **Mermaid Diagrams** — Flowcharts, sequence diagrams, and more, rendered locally with no CDN dependency.
- 🎵 **Audio Import & Playback** — Drop in an existing MP3/audio file for AI transcription, or play back your own recording right inside the note.
- 🗜️ **Auto Compression** — Long recordings are compressed with `MediaCodec` before upload so they fit provider limits without user intervention.
- 🎨 **Color Palette Styles** — Pick between Tonal Spot, Vibrant, Expressive, Rainbow, or Neutral. Every style generates a full Material 3 color scheme locally.
- 🏷️ **Colored Labels** — Assign a color to each label. Colors are stored per-label, so tagging a note with several labels never conflicts.
- 🗂️ **Local-First Organization** — Custom labels, multi-select, pin, duplicate, and a trash bin with recovery. Your notes never leave your device unless you back them up yourself.
- 📖 **Custom Markdown Renderer** — Headers, lists, tables, code blocks, blockquotes, links, LaTeX, Mermaid — all rendered by an in-house engine tuned for note-taking.
- 💾 **JSON Backup & Restore** — Export your entire database to a single file and bring it back on another device.
- 🔄 **In-App Update Checker** — Notifies you when a new release is out and lets you download the APK directly.
- ✨ **Material 3 Expressive** — Every button, chip, and toggle uses spring physics and haptic feedback. Tapping a button — even the tiniest tap — always produces a visible reaction.

---

## Tech Stack

<p align="left">
  <img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Material_3_Expressive-7B1FA2?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material 3 Expressive" />
  <img src="https://img.shields.io/badge/Room_Database-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Room Database" />
  <img src="https://img.shields.io/badge/Retrofit-FF0000?style=for-the-badge&logo=square&logoColor=white" alt="Retrofit" />
  <img src="https://img.shields.io/badge/Gemini_API-8E75B2?style=for-the-badge&logo=googlebard&logoColor=white" alt="Gemini API" />
  <img src="https://img.shields.io/badge/Groq_API-F55036?style=for-the-badge&logo=groq&logoColor=white" alt="Groq API" />
</p>

- **Language:** 100% Kotlin
- **UI:** Jetpack Compose with Material 3 Expressive
- **Color:** MaterialKolor (dynamic palette generation)
- **Persistence:** Room Database
- **Networking:** Retrofit + OkHttp + Moshi
- **AI:** Google Gemini API · Groq Cloud API
- **Architecture:** MVVM with unidirectional data flow

---

## How the AI Layer Works

You bring your own API key, your data never leaves the providers you choose, and there's no telemetry. Two providers, both free-tier friendly, plus a smart routing mode:

| Provider | What it does here | Models used in this fork |
|---|---|---|
| **Google Gemini** | Long-audio transcription (via Files API), long-context text processing, math/chemistry analysis | `gemini-3.5-flash-lite` (titles, explanations) · `gemini-3.8-flash` (transcription, processing) |
| **Groq** | Fast Whisper transcription and quick LLM tasks | `whisper-large-v3-turbo` · `openai/gpt-oss-20b` (titles) · `openai/gpt-oss-120b` (processing, explanations) |
| **Mix Mode** | Automatically picks the best provider for each task | See routing table below |

**Mix Mode routing logic:**

- Short audio (< 20 MB) → Groq Whisper (fastest)
- Long audio (≥ 20 MB) → Gemini (no size limit)
- Long text (> 600 chars) → Gemini Flash (better context)
- Short text → alternates between providers
- Titles & explanations → alternates between providers

The goal is to spread load across both free quotas so the app stays usable longer without hitting rate limits.

---

## Offline-First

Once a note has been processed, everything about it works offline:

- Title, transcript, summary, highlights, and labels are all stored in Room
- Audio playback works entirely from local storage
- KaTeX and Mermaid assets are bundled in `assets/`, so math and diagrams render with zero network access
- Color palette generation happens locally

The only things that require a network connection are the initial transcription and processing of new notes, and the update checker. Everything else is local.

---

## Releases

APKs for this fork are published on the [Releases page](https://github.com/LexicoON/Binot-fix/releases). The in-app updater will notify you when a new build is available.

For builds of the original project, see the upstream [releases](https://github.com/DENSLnetion/Binot/releases).

---

## Building From Source

Want to compile it yourself? You'll need Android Studio (Koala or newer) and a free API key from either Google AI Studio or Groq Cloud.

### 1. Clone

```bash
git clone https://github.com/LexicoON/Binot-fix.git
cd Binot-fix