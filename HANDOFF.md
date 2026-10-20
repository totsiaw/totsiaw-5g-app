# Handoff: NR/5G toggle + signal app (build in a NEW session)

Written 2026-10-20 at the end of the Zong 5G investigation session. Full evidence: `~/Zong5G_evidence/` (README.txt inside).

## What the user asked for (verbatim intent)
- Android app, **MIUIX (HyperOS) style UI** (Compose MIUIX library).
- **Info/About page** crediting **totsiaw**, with "Made with ❤️".
- Show **current connectivity type** (LTE / LTE+ / 5G NSA / 5G SA) and **technical signal**: RSRP, RSRQ, SINR/RSSNR, EARFCN/NR-ARFCN, band, PCI, NR state (NOT_RESTRICTED/CONNECTED), override network type (NR_NSA etc.), per SIM.
- **Shizuku support** so the user can **trigger 5G (NSA) themselves** and **reset settings to default**.
- May copy **Bunny Lock**'s UI/name idea; the user will share UI references in the new session — ask for them.
- **Smooth, 120 fps** capable (Compose, no jank), do real **QA** (build, install, test on device).
- The phone is currently REVERTED to stock (mode 1, NSA off) on purpose: the app must be what flips it, so the user can verify the app works.

## The mechanism the app must drive (verified on this phone)
Device: realme GT 8 China RMX6699, ColorOS, Qualcomm SM8750, Android 16 (API 36/37). Bootloader locked, temp root via KernelSU (lost on reboot). **App must not need root.**

Oplus NR mode selection (decompiled `/system_ext/framework/oplus-telephony-common.jar`,
`OplusNrModeRusCarrierControl.calculateDefaultNrMode`; sources in `~/Zong5G_evidence/oplus_decompiled/`):
1. `Settings.System "user_nr_mode"` (global key; per-sub key `user_nr_mode<subId>` only if `ro.oplus.radio.user_nr_mode_by_sub>0`, which is unset here). Valid values 0–4. **If set, it wins.**
2. else project default `ro.oplus.radio.auto_nr_mode` (=3), overridden by RUS `Settings.System key_rus_nr_mode_cfg` (13 `k=v` fields, unset here) or carrier config `carrier_oplus_auto_nr_mode` / `_sub` (=**1** for all Pakistani carriers → NSA OFF).
- Empirical meaning on this phone: **1 = no NSA/EN-DC, 3 = NSA works** (code calls 3 "sa pre"). Don't claim more than that in UI copy.
- **Enable 5G NSA (via Shizuku = shell uid 2000):** `settings put system user_nr_mode 3`, then make the phone re-apply: toggling airplane mode works (`cmd connectivity airplane-mode enable` / `disable`, shell can run it). Verified: logcat `calculateDefaultNrMode user prefer is 3`, `mode from modem = 3`, then `isEnDcAvailable=true`, `overrideNetwork=NR_NSA`.
- **Reset to default:** `settings delete system user_nr_mode` + airplane toggle.
- Logcat tags useful for an in-app "verify" panel (shell can read logcat): `OplusNrModeRusCarrierControl`, `OplusNrModeUpdater` (`mode from modem = N`), `NetworkTypeController` (`Endc: true/false`).

## Signal data (public APIs, no root)
- `TelephonyManager.registerTelephonyCallback` per subscription: `DisplayInfoListener` (TelephonyDisplayInfo: network + overrideNetwork NR_NSA/NR_ADVANCED/LTE_CA), `SignalStrengthsListener` (CellSignalStrengthLte rsrp/rsrq/rssnr, CellSignalStrengthNr ssRsrp/ssRsrq/ssSinr), `ServiceStateListener` (NetworkRegistrationInfo → DataSpecificRegistrationInfo is hidden; nrState via ServiceState is hidden — get via Shizuku `dumpsys telephony.registry` parse if needed).
- `getAllCellInfo()` needs ACCESS_FINE_LOCATION; CellIdentityLte earfcn/pci/bands, CellIdentityNr nrarfcn/pci.
- `isEnDcAvailable` / `isNrAvailable` / `nrState` aren't public → read via Shizuku: `dumpsys telephony.registry` (patterns in `~/5gcheck.sh`).
- Two SIMs: slot 0 Zong (41004, subId 3) — the only PK network whose towers here broadcast 5G; slot 1 ONIC/Ufone (41003). Jazz also launched NSA 5G (Mar 2026). No SA in Pakistan yet.

## Constraints
- User is on metered mobile data: avoid huge dependency downloads where possible; no git clones.
- Commits: no AI co-author trailers. For totsiaw-account work set repo-local git identity to totsiaw.
- Never write to system/vendor/boot partitions on this phone (locked bootloader → bootloop).
- Toolchain on device: Termux has jadx, adb (localhost adbd connected as emulator-5554). Check whether a local Android build (gradle/aapt2/JDK) works in Termux, or use an existing build path (see memory: morphe-ai workspace has a verified gradle build).
