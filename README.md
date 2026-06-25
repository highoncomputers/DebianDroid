# DebianDroid

Run a full Debian Trixie desktop environment on your Android device — no root required.

## Features

- **Full Debian Trixie** environment with XFCE4 desktop
- **Embedded VNC viewer** — the desktop renders directly inside the app
- **Integrated terminal** for command-line access
- **Persistent storage** — your files survive app restarts
- **File sharing** between Android and Debian via shared folder
- **Multi-window desktop** managed by XFCE4's window manager
- **One-time setup** — download the rootfs once, use offline forever
- **No root required** — uses PRoot for filesystem isolation
- **Lightweight** — runs on any device with 4GB+ RAM

## Quick Start

1. Download the latest APK from [Releases](https://github.com/highoncomputers/DebianDroid/releases)
2. Install the APK
3. Launch the app and follow the onboarding
4. Download the Debian rootfs (~300-400MB)
5. Start your desktop!

## Screenshots

*(Coming soon)*

## Architecture

```
┌─────────────────────────────────────────────┐
│              DebianDroid App                 │
│  ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ VNC      │ │ Terminal │ │ File       │  │
│  │ Viewer   │ │          │ │ Manager    │  │
│  └────┬─────┘ └────┬─────┘ └─────┬──────┘  │
│       │            │              │          │
│  ┌────┴────────────┴──────────────┴──────┐  │
│  │        DesktopService (Foreground)     │  │
│  │  ┌──────────┐  ┌──────────────────┐   │  │
│  │  │  PRoot   │  │  TigerVNC (Xvnc) │   │  │
│  │  └────┬─────┘  └───────┬──────────┘   │  │
│  └───────┼────────────────┼───────────────┘  │
│          │                │                   │
│  ┌───────┴────────────────┴───────────────┐  │
│  │         Debian Trixie Rootfs            │  │
│  │  XFCE4 + TigerVNC + apt + coreutils    │  │
│  └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

## Building from Source

```bash
git clone https://github.com/highoncomputers/DebianDroid.git
cd DebianDroid
./gradlew assembleDebug
```

For release builds, set up the signing secrets:
```bash
keytool -genkey -v -keystore app/release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=release
export KEY_PASSWORD=your_password
./gradlew assembleRelease
```

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **VNC:** Custom RFB protocol implementation (Tight/CopyRect encoding)
- **Isolation:** PRoot (rootless chroot)
- **Desktop:** XFCE4 + TigerVNC server
- **CI/CD:** GitHub Actions
- **Build:** Gradle + AGP 8.5 | Kotlin 2.0 | Compose BOM 2024.10

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is open source. See `LICENSE` for details.

## Security

Report vulnerabilities privately via [Security Advisories](https://github.com/highoncomputers/DebianDroid/security/advisories/new).
