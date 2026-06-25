# Contributing to DebianDroid

We welcome contributions! Here's how you can help:

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USER/DebianDroid.git`
3. Open the project in Android Studio
4. Run the app on your device or emulator

## Development Setup

- Android Studio Ladybug or later
- JDK 17
- Android SDK 35
- Gradle 8.9

## How to Contribute

### Reporting Bugs

Open a [Bug Report](https://github.com/highoncomputers/DebianDroid/issues/new?template=bug_report.md) with:
- Your device and Android version
- Steps to reproduce
- Expected vs actual behavior
- Logs from Settings > Advanced > Logs

### Suggesting Features

Open a [Feature Request](https://github.com/highoncomputers/DebianDroid/issues/new?template=feature_request.md) with:
- What problem you're solving
- How your solution works
- Alternative approaches considered

### Pull Requests

1. Create a branch: `git checkout -b feature/your-feature`
2. Make your changes
3. Run tests: `./gradlew test`
4. Commit with clear messages
5. Push and open a PR

## Code Style

- Follow Kotlin coding conventions
- Use 4-space indentation
- Keep functions focused and small
- Add unit tests for business logic
- No hardcoded secrets or tokens

## Project Structure

```
DebianDroid/
├── app/src/main/java/com/debiandroid/desktop/
│   ├── ui/          # Compose screens and components
│   ├── vnc/         # VNC/RFB protocol implementation
│   ├── proot/       # PRoot runner and rootfs management
│   ├── service/     # Foreground services
│   ├── terminal/    # Terminal emulation
│   └── data/        # Session persistence
├── .github/         # CI/CD and issue templates
└── scripts/         # Rootfs build scripts
```

## License

By contributing, you agree that your contributions will be licensed under the project's license.
