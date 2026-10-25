# RetroForge Emulator

A lightweight Android emulator app for Game Boy, Game Boy Color, Game Boy Advance, Nintendo DS, and related retro systems.

This project is built with:
- Kotlin
- Android Jetpack Compose
- Room
- Firebase
- Gradle

## Features
- Android app build
- ROM library management
- ROM scanning and metadata detection
- Save state support
- Firebase-backed cloud sync / auth features
- Custom controller skins
- Built-in demo ROM library

## Supported systems
- GB
- GBC
- GBA
- NDS
- N64

## Project structure
```text
RetroForgeEmulator/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   ├── src/
│   │   ├── androidTest/
│   │   ├── main/
│   │   └── test/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── .gitignore
├── .env.example
└── README.md
