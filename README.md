# 🎮 Multiplatform Kotlin Emulator

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-98.5%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/JavaScript-1.5%25-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black" alt="JavaScript" />
  <img src="https://img.shields.io/badge/Compose_Multiplatform-Enabled-000000?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform" />
  <img src="https://img.shields.io/badge/Build-GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white" alt="GitHub Actions" />
</p>

<p align="center">
  <b>A lightweight, high-performance cross-platform emulator engine powered by Kotlin Multiplatform (KMP) and Compose Multiplatform.</b>
  <br />
  Designed to bring seamless emulation to Desktop and Android from a single shared codebase.
</p>

---

## 🌟 Key Features

* 📱 **Native Android Support:** Responsive touch controls, hardware acceleration, and optimized mobile UI.
* 💻 **Desktop Native (Windows / macOS / Linux):** High-framerate rendering via Skia with full keyboard and gamepad integration.
* ⚡ **Shared Engine Architecture:** Core CPU emulation, instruction sets, bus routing, and memory mapping written **100% in Kotlin**.
* 🎨 **Modern Declarative UI:** Built with **Compose Multiplatform** for consistent, clean user interfaces across all screen sizes.
* 🔄 **Automated CI/CD:** Powered by **GitHub Actions** for instant cloud builds of `.apk` binaries and Desktop executables.

---

## 🏗️ Architecture & Project Structure

This project leverages Kotlin Multiplatform's `expect`/`actual` design pattern to separate shared core logic from platform-specific APIs:

```text
├── androidApp/               # Android-specific entry point & Manifest
├── desktopApp/               # Desktop main entry point & windowing configuration
├── shared/                   # Core Emulator Logic (Shared KMP Module)
│   ├── commonMain/           # 🧠 Shared CPU cores, memory bus, and Compose UI
│   ├── androidMain/          # 🤖 Android-specific audio & rendering drivers
│   └── desktopMain/          # 🖥️ Desktop file pickers, keyboard mapping & audio
├── gradle/                   # Gradle wrapper binaries & configuration
├── .github/workflows/        # Automated GitHub Actions build pipelines
├── build.gradle.kts          # Project-level dependencies
└── settings.gradle.kts       # Module declarations
