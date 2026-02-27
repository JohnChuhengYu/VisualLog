# Contributing to VisualLog

Thank you for your interest in contributing to **VisualLog**! 🎉  
This project is a local-first, interactive visual journal built with Kotlin and Compose Multiplatform. We welcome bug reports, feature requests, and pull requests of all kinds.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Features](#suggesting-features)
  - [Submitting a Pull Request](#submitting-a-pull-request)
- [Development Setup](#development-setup)
- [Coding Guidelines](#coding-guidelines)
- [Commit Message Convention](#commit-message-convention)

---

## Code of Conduct

By participating in this project, you agree to be respectful and constructive. Please be kind to other contributors.

---

## Getting Started

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/VisualLog.git
   cd VisualLog
   ```
3. Create a new branch for your work:
   ```bash
   git checkout -b feat/your-feature-name
   ```

---

## How to Contribute

### Reporting Bugs

If you find a bug, please [open an issue](https://github.com/JohnChuhengYu/VisualLog/issues) and include:

- A clear, descriptive title.
- Steps to reproduce the problem.
- Expected vs. actual behaviour.
- Your OS and JDK version.
- Any relevant logs or screenshots.

### Suggesting Features

Feature requests are welcome! Open an issue and label it `enhancement`. Describe:

- The problem you're trying to solve.
- Your proposed solution.
- Any alternatives you considered.

### Submitting a Pull Request

1. Ensure your branch is up to date with `main`:
   ```bash
   git fetch origin
   git rebase origin/main
   ```
2. Make your changes, following the [Coding Guidelines](#coding-guidelines) below.
3. Run the app locally to verify nothing is broken:
   ```bash
   ./gradlew run
   ```
4. Push your branch and open a Pull Request against `main`.
5. Fill in the PR template, describe your changes, and link any related issues.

---

## Development Setup

### Prerequisites

- **JDK 18+** — [Adoptium](https://adoptium.net/)
- **Gradle** — managed via the included `gradlew` wrapper (no separate install needed).

### Running Locally

```bash
# macOS / Linux
./gradlew run

# Windows
gradlew.bat run
```

### Building a Native Installer

```bash
./gradlew package
```

Output is placed in `build/compose/binaries/main-release/`.

---

## Coding Guidelines

- **Language**: Kotlin. Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **UI**: All UI code uses **Compose Multiplatform**. Keep composables small and focused.
- **State management**: Prefer `ViewModel`-style classes and Kotlin coroutines (`Dispatchers.IO` for DB/file work, `Dispatchers.Main` for UI updates).
- **No cloud calls**: VisualLog is 100% offline. Do not introduce network dependencies.
- **Database**: Use the existing **JetBrains Exposed** ORM layer for all SQLite interactions — do not write raw SQL strings.
- **Formatting**: Run `./gradlew ktlintFormat` (if configured) or manually ensure 4-space indentation and no trailing whitespace.

---

## Commit Message Convention

We follow a simplified [Conventional Commits](https://www.conventionalcommits.org/) style:

```
<type>: <short summary>
```

| Type | When to use |
|------|-------------|
| `feat` | A new feature |
| `fix` | A bug fix |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `docs` | Documentation only changes |
| `chore` | Build process or tooling changes |
| `test` | Adding or updating tests |

**Example:**
```
feat: add weather tracking widget to daily canvas
fix: prevent crash when importing large photos
docs: update README screenshots
```

---

<div align="center">
  <p>Built with ❤️ using Compose Multiplatform — happy hacking!</p>
</div>
