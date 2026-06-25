# Security Policy

## Reporting a Vulnerability

We take the security of DebianDroid seriously. If you discover a security vulnerability, please **do not** open a public issue.

Instead, report it privately by emailing the maintainers or opening a security advisory at:

https://github.com/highoncomputers/DebianDroid/security/advisories/new

Please include as much detail as possible:

- Type of vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if known)

## What to expect

- We will acknowledge receipt within 48 hours
- We will provide an estimated timeline for a fix
- Once the fix is released, we will disclose the vulnerability publicly

## Scope

- The Android app (Kotlin/Compose code)
- The build and CI/CD pipeline
- The rootfs download mechanism
- Any native binaries shipped with the app

## Out of scope

- The Debian packages installed inside the rootfs (report those to Debian Security)
- Third-party libraries (report to their respective maintainers)
