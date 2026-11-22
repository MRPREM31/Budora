# Budora for Windows 🎧

A lightweight, native Windows desktop tool to monitor wireless earbuds (AirPods & Google Fast Pair) battery levels right from your laptop's System Tray and floating flyout drawer.

---

## ✨ Features

- **Live 3-Component Readout:** Real-time Left Pod, Right Pod, and Charging Case battery percentages.
- **Smart Case Memory:** Remembers the last verified charging case battery level even when the lid is shut.
- **Active Charging Indicators:** Instant `⚡` indicators when buds or the case are receiving power.
- **Windows 11 Fluent Floating Card:** Auto-slides up near the taskbar when your earbuds connect or when you click the tray icon.
- **System Tray Integration:** Sits quietly in the notification area next to the clock with live tooltip battery readouts.
- **Zero Internet Permissions:** 100% offline, local Bluetooth Low Energy (BLE) scanning only.

---

## 🚀 How to Run

Simply double-click:
```
Budora.exe
```

- When running, look for the green headphone icon in your **Windows Taskbar System Tray** (bottom right near the clock).
- Click or double-click the icon anytime to open the battery flyout.
- Right-click the icon to view the menu or exit.

---

## 🛠️ Source Files

- `models.py` — Data models and device identifier logic.
- `proximity_payload.py` — Apple Proximity Pairing (0x004C) BLE beacon decoder.
- `fast_pair_payload.py` — Google Fast Pair (0xFE2C) multi-component decoder.
- `tracker.py` — Signal RSSI hysteresis and Smart Case Memory manager.
- `scanner.py` — Asynchronous background BLE advertisement listener.
- `ui_popup.py` — Modern Windows 11 rounded floating flyout widget.
- `ui_tray.py` — System Tray icon, dynamic tooltips, and context menu.
- `main.py` — Application entry point.
- `test_budora.py` — Automated verification unit tests.
