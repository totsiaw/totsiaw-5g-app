# 5G Switch

A small HyperOS/MIUIX-style Android app that flips **5G (NSA)** on and off on
realme/ColorOS phones where the carrier default disables EN-DC, and shows the
live technical signal per SIM. No root — it drives the change through
[Shizuku](https://shizuku.rikka.app/) (shell UID 2000).

Made with ❤️ by **totsiaw**.

## What it does
- **Enable 5G (NSA):** `settings put system user_nr_mode 3`, then re-applies the
  radio with an airplane-mode toggle. On this device, `1 = no NSA`, `3 = NSA on`.
- **Reset to default:** `settings delete system user_nr_mode` + re-apply.
- **Signal panel (per SIM):** connectivity type (LTE / LTE+ / 5G NSA / 5G SA),
  RSRP, RSRQ, SINR/RSSNR, EARFCN/NR-ARFCN, band, PCI, NR state, EN-DC available.
- **Verify:** reads `telephony.registry` and the modem's NR-mode logcat.

## Build
Built in CI (GitHub Actions → `.github/workflows/build.yml`). The debug APK is
uploaded as the `app-debug` artifact.

Toolchain: AGP 8.13.0 · Gradle 8.14.3 · Kotlin 2.3.20 · Compose Multiplatform
1.10.3 · MIUIX `miuix-android` 0.8.8 · Shizuku 13.1.5. `minSdk 31`, `compileSdk 36`.

## Requirements on device
- Shizuku installed and running, permission granted to this app.
- Phone + location permission (location is required to read cell identity).
- A 5G (NSA) cell in range for 5G to actually attach.

It only changes a public system setting via Shizuku; it never writes to system,
vendor or boot partitions.
