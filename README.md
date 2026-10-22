<p align="center">
  <img src="logo.svg" width="120" height="120" alt="DoseFlow Logo" />
</p>

<h1 align="center">DoseFlow</h1>

<p align="center">
  <strong>Intelligent, offline-first medication adherence for patients & real-time zero-trust monitoring for caregivers.</strong>
</p>

<p align="center">
  <a href="#-key-features"><img src="https://img.shields.io/badge/Platform-Android%2014+-006A6A?style=for-the-badge&logo=android&logoColor=white" alt="Platform" /></a>
  <a href="#-architecture"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-008988?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose" /></a>
  <a href="https://4kaurora07.github.io/DocFlow/"><img src="https://img.shields.io/badge/Live%20Dashboard-GitHub%20Pages-10B981?style=for-the-badge&logo=github&logoColor=white" alt="Live Dashboard" /></a>
  <a href="DOCUMENTATION.md"><img src="https://img.shields.io/badge/Docs-Technical%20Spec-8B5CF6?style=for-the-badge&logo=googledocs&logoColor=white" alt="Documentation" /></a>
  <a href="#-quick-start"><img src="https://img.shields.io/badge/Build-1--Command%20APK-10B981?style=for-the-badge&logo=gradle&logoColor=white" alt="APK Build" /></a>
</p>

---

## 🌟 Overview

**DoseFlow** is designed around a single medical premise: *missed medications shouldn't happen because of poor software, lost network connections, or aggressive OS battery savers.*

Built as a native Android application paired with a modern real-time caregiver web dashboard, DoseFlow guarantees dose delivery offline while giving family members and healthcare providers audited adherence insight with strict data freshness guarantees.

```
       PATIENT DEVICE                                     CAREGIVER DASHBOARD
 ┌──────────────────────────┐                      ┌──────────────────────────────┐
 │   Android Native App     │                      │   Real-Time Web Portal       │
 │  • Jetpack Compose M3    │    HMAC Pairing      │  • Live Dose Timeline        │
 │  • Local Room SQLite     │ ───────────────────> │  • 7 & 30-Day Adherence      │
 │  • Exact Alarms Engine   │   Outbox Sync Hub    │  • Clinical CSV Export       │
 │  • Zero-Network Core     │                      │  • Strict Freshness Guard    │
 │  • Senior Reminder Popup │                      │  • Live GitHub Pages Demo    │
 └──────────────────────────┘                      └──────────────────────────────┘
```

---

## ✨ Key Features

### 📱 Patient Mobile Experience (Android Native)
* **100% Offline Resilience:** Dose schedules, occurrence calculations, and dose confirmation records live in local Room SQLite. No network required to trigger alarms or record doses.
* **Exact Alarm Guarantee:** Uses `AlarmManager.setExactAndAllowWhileIdle` with chained scheduling to bypass Android Doze Mode and background execution limits.
* **Senior-Friendly Home Screen Popup:**
  * Displays a prominent, high-contrast modal directly in front of the phone's Home Screen when a scheduled dose time has passed.
  * Oversized touch targets (`✓ I TOOK THIS MEDICINE`, `⏰ SNOOZE 10M`, `✕ SKIP DOSE`) tailored for seniors and motor-impaired individuals.
  * **Strict Lock Screen Privacy:** Automatically suppressed when the device is locked to protect sensitive health data from passersby.
  * **Instant Post-Unlock Delivery:** If a dose is due while locked, the popup surfaces the exact millisecond the user unlocks their phone (`ACTION_USER_PRESENT`).
  * **Zero Complex Settings:** No dangerous or confusing `SYSTEM_ALERT_WINDOW` ("Display over other apps") permissions required—works out of the box on Android 10 through 15 and OEM skins (MIUI, HyperOS, OneUI).
  * Fully customizable with a user toggle in **Settings**.
* **Compliant Android Notification Architecture:**
  * Uses a clean, pure white flat vector icon (`ic_stat_doseflow.xml`) on transparent alpha paths, eliminating fallback to default Android robot icons on Xiaomi/MIUI and custom OEM notification shades.
  * High-priority Heads-Up Notification channel (`CHANNEL_DOSES_HIGH`) with single-tap quick action buttons (`✓ I TOOK IT`, `⏰ SNOOZE 10M`, `✕ SKIP DOSE`).
  * Tapping the notification opens the full reminder popup immediately.
* **Smart Reconciliation:** Idempotently handles DST shifts, timezone travels, phone reboots (`BOOT_COMPLETED`), and system time adjustments without duplicate alarms.
* **Auto-Escalation Engine:** Automatically escalates neglected doses at +15 minutes and transitions unacknowledged doses to missed at +45 minutes.

### 🌐 Remote Caregiver Portal (Web App)
* **Zero-Trust Freshness Guard:** If a patient's mobile phone goes offline for >15 minutes, unconfirmed doses are instantly labeled **"Awaiting Sync"** rather than assumed taken.
* **Live GitHub Pages Dashboard:** View the caregiver dashboard online at **[https://4kaurora07.github.io/DoseFlow/](https://4kaurora07.github.io/DoseFlow/)**.
* **Instant Pairing:** Patient taps *Share Caregiver Code* on phone &rarr; Caregiver enters 6-character code &rarr; Connected immediately worldwide.
* **Adherence Gauges:** Dynamic 7-day and 30-day compliance tracking calculated straight from cryptographic event logs.
* **Physician Export:** One-click CSV export ready for medical appointments and clinical audits.

---

## 🔬 System Architecture

```mermaid
flowchart TD
    subgraph MobileApp["📱 DoseFlow Android App"]
        direction TB
        UI["Jetpack Compose UI\n(Timeline, Meds, Adherence)"]
        Popup["DoseReminderPopupActivity\n(Home Screen Overlay for Seniors)"]
        VM["Architecture ViewModels\n(MVI / StateFlow)"]
        Room[("Room SQLite DB\nSchedules • Doses • Outbox")]
        Scheduler["AlarmScheduler\n(Exact Alarms & Reconciler)"]
        UnlockReceiver["BootAndReconcileReceiver\n(BOOT, TIME_SET, USER_PRESENT)"]
        NotificationHelper["NotificationHelper\n(Flat Vector ic_stat_doseflow)"]
        Engine["OutboxSyncEngine\n(Exponential Backoff & Jitter)"]

        UI --> VM
        VM --> Room
        Scheduler --> Room
        UnlockReceiver --> Popup
        Scheduler --> NotificationHelper
        NotificationHelper --> Popup
        Room --> Engine
    end

    subgraph CloudPortal["☁️ DoseFlow Cloud & Caregiver Portal"]
        direction TB
        API["Node / Express API Server"]
        SqliteDB[("Encrypted SQLite Registry\nCaregivers • Patients • Sessions")]
        Guard["Freshness Guard Engine\n(15-min Staleness Window)"]
        WebUI["Caregiver Web Dashboard\n(Responsive Live SPA • GitHub Pages)"]

        API --> SqliteDB
        API --> Guard
        Guard --> WebUI
    end

    Engine -- "POST /api/sync/events\n(When Online)" --> API
    Room -- "POST /api/access-codes/generate\n(HMAC-SHA256)" --> API
```

---

## 🛡️ Zero-Trust Freshness Protocol

Medical monitoring applications often dangerously display old data as if everything is fine. DoseFlow implements a **Zero-Trust Freshness Contract**:

| Device Sync Heartbeat | Portal Display State | Dose Treatment |
| :--- | :--- | :--- |
| **< 15 minutes** | 🟢 **Live Synced** | Normal schedule display with live status |
| **> 15 minutes** | 🟡 **Offline Stale Warning** | Unconfirmed doses flagged as *Awaiting Sync* |
| **> 12 hours** | 🔴 **Prolonged Disconnect** | Red banner prompting phone check-in |

> [!NOTE]  
> Under stale conditions, caregivers are visually notified that the device is offline, preventing incorrect assumptions about whether a dose was actually taken.

---

## 🚀 Quick Start

### Prerequisites
* **Android Studio Ladybug (or newer)** with Android SDK 34+
* **JDK 17+**
* **Node.js 18+**

### 1. Build the Android App
Build the APK directly with a single command:

```bash
# Build debug APK with instant console feedback
npm run apk
```

The resulting binary will be ready at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Launch Caregiver Portal Locally
```bash
# Start the web portal server
npm run portal
```
Open **[http://localhost:3000](http://localhost:3000)** in your browser to access the local caregiver dashboard. Alternatively, access the online version at **[https://4kaurora07.github.io/DocFlow/](https://4kaurora07.github.io/DocFlow/)**.

---

## 🧪 Testing & Quality Assurance

DoseFlow maintains a comprehensive unit and integration test suite across both Android and backend services:

```bash
# Run Android Unit & Architecture Tests (75+ tests)
./gradlew testDebugUnitTest

# Run Android Compose UI & Instrumentation Tests
./gradlew compileDebugAndroidTestKotlin

# Run Caregiver Portal & Cryptographic Security Tests
cd doseflow-portal && npm test
```

---

## 🔒 Security & Privacy

* **Lock Screen Medical Privacy:** Dose popups are intentionally silenced from appearing on lock screens, preventing accidental exposure of sensitive prescription names to coworkers or bystanders.
* **Hashed Code Registry:** Access codes generated on the patient's device are hashed server-side with HMAC-SHA256. Plaintext access codes are never stored as primary registry keys.
* **Read-Only Remote Model:** Caregivers have strict read-only observation rights. Dose alterations and schedule changes can **only** be executed physically on the patient's primary device.
* **Zero Bloat:** Completely clean of tracking pixels, advertising SDKs, and third-party analytics.

---

## 📁 Repository Structure

```
DoseFlow/
├── app/                        # Android Native Application
│   ├── src/main/java/          # Jetpack Compose UI, Room DB, Alarm Scheduler
│   │   └── com/doseflow/app/
│   │       ├── data/           # Repositories, Room SQLite entities & DAOs
│   │       ├── domain/         # Reconciler, Occurrence Calculator, Models
│   │       ├── platform/       # AlarmScheduler, NotificationHelper, Receivers
│   │       └── ui/             # Compose screens & DoseReminderPopupActivity
│   ├── src/main/res/           # Material themes, vector drawables (ic_stat_doseflow.xml)
│   └── src/test/               # Exhaustive unit test suite (Alarm, Zone, Sync)
├── doseflow-portal/            # Caregiver Remote Dashboard & Sync Backend
│   ├── data/                   # SQLite database directory (clean, zero demo rows)
│   ├── public/                 # Caregiver SPA (HTML, CSS, JS, SVG assets)
│   ├── server.js               # Express API (Sync, Pairing, Auth, Dashboard)
│   └── test/                   # API integration & crypto verification tests
├── logo.svg                    # Official DoseFlow Vector Mark
├── build-apk.js                # One-command CLI build runner
└── package.json                # Project script definitions
```

---

<p align="center">
  Crafted with care for patient wellness and caregiver peace of mind. ❤️
</p>
