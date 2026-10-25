# 5G Switch

A small HyperOS/MIUIX-style Android app for realme/ColorOS phones that switches
the phone's NR (5G) mode and shows the live technical signal per SIM. No root:
it drives the change through [Shizuku](https://shizuku.rikka.app/) (shell UID 2000).

Made with ❤️ by **totsiaw**.

> **Read this first: realme GT 8 China (RMX6699) users in Pakistan**
>
> On the China GT 8 this app can bring up the **5G icon**, but it **cannot give
> you 5G NSA data**. The China GT 8's RF configuration (RF card 0862) has no
> EN-DC (NSA) combo table, so the modem reports "ENDC is not supported" and never
> tells the tower it can do EN-DC. Jazz and Zong currently run NSA only, so the
> tower never adds the 5G leg. The icon only means the tower says 5G is
> available there (`nrState` NOT_RESTRICTED), not that you're connected.
>
> **5G SA does work on the GT 8** (n1, n3, n5, n8, n18, n20, n26, n28, n38, n40,
> n41, n48, n66, n77, n78). Once a Pakistani operator turns on SA, the phone
> should connect with no mods. Keep that SIM as your mobile-data SIM, since the
> modem only allows SA on the data SIM.
>
> Use the signal panel to see what's real: **NR state CONNECTED** with an
> NR-ARFCN/band means real 5G; NOT_RESTRICTED alone is just the icon.

## What it does
- **NR mode switch:** `settings put system user_nr_mode 3`, then re-applies the
  radio with an airplane-mode toggle. On ColorOS, `1` keeps NSA off (what the
  Pakistani carrier config picks) and `3` allows NSA. On phones whose modem
  supports EN-DC this turns NSA 5G on. On the China GT 8 it only changes the icon
  (see above).
- **Reset to default:** `settings delete system user_nr_mode` + re-apply.
- **Signal panel (per SIM):** network actually in use (LTE / LTE+ / 5G NSA / 5G
  SA), RSRP, RSRQ, SINR/RSSNR, EARFCN/NR-ARFCN, band, PCI, NR state, EN-DC
  available, with live graphs.
- **Verify:** reads `telephony.registry` and the modem's NR-mode logcat, so you
  can check the result yourself.

## Requirements on device
- Shizuku installed and running, with permission granted to this app.
- Phone and location permissions (location is required to read cell identity).
- For 5G to actually attach: a 5G cell in range **and** a modem that supports
  that 5G mode (NSA needs EN-DC support; SA needs the band).

It only changes a public system setting through Shizuku. It never writes to the
system, vendor or boot partitions, and "Reset to default" undoes it.

## Build
Built in CI (GitHub Actions, `.github/workflows/build.yml`). The APK is uploaded
as the `app-debug` artifact.

Toolchain: AGP 8.13.0 · Gradle 8.14.3 · Kotlin 2.3.20 · Compose Multiplatform
1.10.3 · MIUIX `miuix-android` 0.8.8 · Shizuku 13.1.5. `minSdk 31`, `compileSdk 36`.

## Disclaimer
Provided as is. Changing radio settings can affect connectivity until you reset
them. Use at your own risk.
