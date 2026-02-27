# Security Policy

## Supported Versions

VisualLog is currently in active development. Security fixes are applied to the latest version only.

| Version | Supported |
|---------|-----------|
| Latest (`main`) | ✅ Yes |
| Older releases | ❌ No |

---

## Reporting a Vulnerability

VisualLog is a **100% offline, local-first** desktop application — it makes no network requests and stores all data locally in an embedded SQLite database on your machine. This significantly limits the attack surface.

However, if you discover a security vulnerability (e.g. path traversal when importing files, malicious sticker/image files leading to code execution, etc.), please **do not open a public GitHub issue**.

Instead, report it privately:

1. **Email**: Open a [GitHub Security Advisory](https://github.com/JohnChuhengYu/VisualLog/security/advisories/new) via the GitHub UI (preferred), or
2. **Direct contact**: Reach out to the maintainer directly through GitHub.

Please include:
- A clear description of the vulnerability.
- Steps to reproduce.
- Potential impact and severity assessment.
- Any suggested fix if you have one.

---

## What to Expect

- **Acknowledgement** within **48 hours** of your report.
- A patch or mitigation plan communicated back to you within **7 days**.
- Credit in the release notes (unless you prefer to remain anonymous).

---

## Scope

Because VisualLog is offline-only, the following are **out of scope**:

- Network-based attacks (no server, no API).
- Authentication/session issues (no accounts or login).
- Third-party cloud infrastructure (none used).

**In scope** examples:

- Malicious file imports causing unintended code execution or data access.
- Local privilege escalation through the app.
- Data corruption or unintended data exposure of local SQLite files.

---

## Security Best Practices for Users

- Only import media files (images, stickers) from trusted sources.
- Keep your JDK up to date, as VisualLog runs on the JVM.
- Ensure your operating system is patched and up to date.

---

<div align="center">
  <p>Thank you for helping keep VisualLog safe! 🔒</p>
</div>
