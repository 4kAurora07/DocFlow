# 📚 DoseFlow — Comprehensive Technical Documentation

> **Intelligent, offline-first medication adherence system for patients & real-time zero-trust monitoring for caregivers.**

---

## 📑 Table of Contents
1. [Introduction & Philosophy](#1-introduction--philosophy)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Android Mobile Platform (Patient App)](#3-android-mobile-platform-patient-app)
   - [Domain & Scheduling Engines](#31-domain--scheduling-engines)
   - [Senior-Friendly Home Screen Popup](#32-senior-friendly-home-screen-popup)
   - [Notification Architecture](#33-notification-architecture)
   - [Broadcast Receivers & Lifecycle](#34-broadcast-receivers--lifecycle)
   - [Data Storage & Room SQLite](#35-data-storage--room-sqlite)
   - [Offline Sync & Outbox Engine](#36-offline-sync--outbox-engine)
4. [Caregiver Web Portal](#4-caregiver-web-portal)
   - [Zero-Trust Freshness Protocol](#41-zero-trust-freshness-protocol)
   - [Cryptographic Access & HMAC Pairing](#42-cryptographic-access--hmac-pairing)
   - [Live Dashboard & Clinical Export](#43-live-dashboard--clinical-export)
5. [Security, Privacy & Safety Model](#5-security-privacy--safety-model)
6. [Developer & Build Guide](#6-developer--build-guide)
7. [Verification & Test Suite](#7-verification--test-suite)

---

## 1. Introduction & Philosophy

Medication non-adherence is a leading contributor to preventable health complications, hospital readmissions, and clinical trial failures. Common software failure points in existing medication reminder apps include:
- **Aggressive OEM Battery Optimization:** Background processes killed by aggressive power managers (Xiaomi MIUI/HyperOS, Samsung OneUI, OnePlus OxygenOS).
- **Network Dependency:** Alarms failing or being delayed when cell coverage or Wi-Fi drops.
- **Complex UI Barriers for Seniors:** Confusing navigation, tiny buttons, or requiring complex system settings (e.g., granting "Display over other apps" / restricted settings).
- **False Assumptions for Caregivers:** Remote dashboards displaying outdated data as "Normal" when the patient's device is actually disconnected.

**DoseFlow was architected from the ground up to eliminate each of these failure modes.**

---

## 2. High-Level Architecture

```
                       ┌────────────────────────────────────────────────────────┐
                       │                   PATIENT MOBILE DEVICE                │
                       │                                                        │
                       │  ┌──────────────────┐         ┌─────────────────────┐  │
                       │  │   Jetpack M3 UI  │         │ DoseReminderPopup   │  │
                       │  │   (Compose/MVI)  │         │ (Home Screen Modal) │  │
                       │  └────────┬─────────┘         └──────────▲──────────┘  │
                       │           │                              │             │
                       │  ┌────────▼──────────────────────────────┴──────────┐  │
                       │  │       AlarmScheduler & Reconciler Engine         │  │
                       │  └────────┬──────────────────────────────▲──────────┘  │
                       │           │                              │             │
                       │  ┌────────▼─────────┐         ┌──────────┴──────────┐  │
                       │  │   Room SQLite    │         │ BootAndReconcile    │  │
                       │  │   (Offline Core) │         │ (USER_PRESENT, etc.)│  │
                       │  └────────┬─────────┘         └─────────────────────┘  │
                       │           │                                            │
                       │  ┌────────▼─────────┐                                  │
                       │  │ OutboxSyncEngine │                                  │
                       │  └────────┬─────────┘                                  │
                       └───────────┼────────────────────────────────────────────┘
                                   │ HMAC-SHA256 Encrypted Sync (When Online)
                                   ▼
                       ┌────────────────────────────────────────────────────────┐
                       │               REMOTE CAREGIVER PORTAL (CLOUD)          │
                       │                                                        │
                       │  ┌──────────────────┐         ┌─────────────────────┐  │
                       │  │ Express REST API │ ──────> │ Encrypted Registry  │  │
                       │  └────────┬─────────┘         │ (SQLite Outbox Log) │  │
                       │           │                   └─────────────────────┘  │
                       │  ┌────────▼─────────────────────────────────────────┐  │
                       │  │     Zero-Trust Freshness Guard Engine            │  │
                       │  │   (<15m: Live | >15m: Stale | >12h: Disconnected) │  │
                       │  └────────┬─────────────────────────────────────────┘  │
                       │           │                                            │
                       │  ┌────────▼─────────────────────────────────────────┐  │
                       │  │     Responsive Real-Time Web Dashboard           │  │
                       │  │  (Live Timeline • Adherence % • Clinical CSV)    │  │
                       │  └──────────────────────────────────────────────────┘  │
                       └────────────────────────────────────────────────────────┘
```

---

## 3. Android Mobile Platform (Patient App)

### 3.1 Domain & Scheduling Engines
- **`OccurrenceCalculator`**: Computes discrete dose occurrence timestamps for complex medication regimens:
  - *Daily / Multi-daily* (e.g., 08:00, 14:00, 20:00).
  - *Specific Days of Week* (e.g., Mon, Wed, Fri).
  - *Custom Day Interval* (e.g., every 3 days).
  - *As-Needed (PRN)* with safety cooldown timers.
- **`ScheduleReconciler`**: Idempotent reconciler that synchronizes schedules with existing occurrences. Safely handles:
  - Clock shifts and Daylight Saving Time (DST).
  - Timezone jumps during travel.
  - Device reboots and offline periods.
- **Chained Exact Alarms (`AlarmScheduler`)**:
  - Employs `AlarmManager.setExactAndAllowWhileIdle()` using `AlarmManager.RTC_WAKEUP`.
  - Rather than flooding the system with hundreds of future alarms, DoseFlow chains exact alarms to trigger only the *immediate next upcoming dose*, rescheduling upon completion. This conforms to Android 12+ exact alarm battery guidelines.

### 3.2 Senior-Friendly Home Screen Popup
* **Component:** `com.doseflow.app.ui.popup.DoseReminderPopupActivity`
* **Purpose:** Ensures elderly patients never forget medication while browsing their phone or unlocking to their home screen.
* **Key Design Decisions:**
  1. **Strict Lock Screen Privacy:** Configured with `showWhenLocked="false"` and `turnScreenOn="false"`. Sensitive prescription names are never revealed over the lock screen to coworkers, family, or passersby.
  2. **Instant Delivery on Phone Unlock:** When an alarm triggers while the phone is locked, the notification shade alerts the patient. When the patient unlocks the phone, `BootAndReconcileReceiver` captures `Intent.ACTION_USER_PRESENT`, introduces a brief 350ms delay for keyguard dismissal animation, and smoothly displays the popup directly over the Home Screen.
  3. **Modern BAL (Background Activity Launch) Compliance:** Uses `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED` on Android 14+ (API 34) and Android 15 (API 35), guaranteeing execution without being blocked by system security policies.
  4. **Zero Restricted Settings:** Elderly users are **never** forced to navigate deep into Android settings to enable `SYSTEM_ALERT_WINDOW` ("Display over other apps").
  5. **Oversized High-Contrast Controls:**
     - `✓ I TOOK THIS MEDICINE` (Primary high-contrast button).
     - `⏰ SNOOZE 10M` (Secondary action button).
     - `✕ SKIP DOSE` (Tertiary action button).
  6. **User Choice:** Fully togglable in **Settings** (*Home Screen Reminder Popup*).

### 3.3 Notification Architecture
* **Component:** `com.doseflow.app.platform.notification.NotificationHelper`
* **Notification Channels:**
  - `doses_high_v1`: `IMPORTANCE_MAX` with high-priority audio, vibration, and lights.
  - `info_general_v1`: `IMPORTANCE_DEFAULT` for pairing and sync updates.
* **Vector Small Icon Identity (`ic_stat_doseflow.xml`):**
  - Designed as a pure single-color `#FFFFFF` vector drawable on transparent paths.
  - Complies with Android status bar icon specifications, preventing OEM skins (such as Xiaomi MIUI/HyperOS, Samsung OneUI) from falling back to generic Android robot icons.
* **Actionable Intent Actions:**
  - Content click opens the full reminder popup dialog.
  - Single-tap actions for `✓ I TOOK IT`, `⏰ SNOOZE 10M`, and `✕ SKIP DOSE` operate directly in the background via `NotificationActionReceiver`.

### 3.4 Broadcast Receivers & Lifecycle
* **`AlarmReceiver`**: Processes exact alarm callbacks (`ACTION_DOSE_ALARM`, `ACTION_ESCALATION_ALARM`, `ACTION_SNOOZE_ALARM`, `ACTION_MISSED_ALARM`). Evaluates lock state via `KeyguardManager` to respect privacy.
* **`BootAndReconcileReceiver`**: Statically declared in manifest to capture:
  - `BOOT_COMPLETED` & `MY_PACKAGE_REPLACED`: Restores alarms after reboot or update.
  - `TIME_SET` & `TIMEZONE_CHANGED`: Reconciles schedules on system time change.
  - `ACTION_USER_PRESENT`: Triggers overdue reminder popups immediately upon unlock.
* **`NotificationActionReceiver`**: Executes background database updates when notification action buttons are tapped.

### 3.5 Data Storage & Room SQLite
Room SQLite database schema (`AppDatabase`):
- **`MedicineEntity`**: Name, dosage, unit, instructions, color tag, active status.
- **`ScheduleEntity`**: Recurrence type, target dose times, day intervals, active date bounds.
- **`DoseOccurrenceEntity`**: Unique ID, medicine foreign key, scheduled epoch millis, state (`PENDING`, `TAKEN`, `SKIPPED`, `MISSED`, `SNOOZED`), actual taken epoch millis, escalation count.
- **`SyncEventEntity`**: Outbox event queue containing serialized change events and sync attempt tallies.

### 3.6 Offline Sync & Outbox Engine
* **Component:** `com.doseflow.app.data.sync.OutboxSyncEngine`
* **Transactional Reliability:** Every dose action writes both a state change and a pending outbox record within a single Room transaction.
* **Exponential Backoff & Jitter:** Network failures trigger retries with truncated exponential backoff ($2^n \times 1000\text{ms}$) with randomized jitter to prevent thundering herd problems.

---

## 4. Caregiver Web Portal

### 4.1 Zero-Trust Freshness Protocol
Unlike traditional dashboards that assume "silence equals compliance," DoseFlow's freshness guard audits the time delta between the current timestamp and the device's last cryptographic heartbeat:

$$\Delta t = \text{currentTime} - \text{lastHeartbeatTimestamp}$$

| $\Delta t$ Condition | Portal State | Medical Meaning | UI Presentation |
| :--- | :--- | :--- | :--- |
| $\Delta t \le 15\text{ minutes}$ | 🟢 **Live Synced** | Device is actively connected; records are fresh. | Green pulse badge, normal timeline. |
| $15\text{m} < \Delta t \le 12\text{h}$ | 🟡 **Stale Warning** | Phone is offline, asleep, or out of cell coverage. | Yellow warning banner; unconfirmed doses flagged **"Awaiting Sync"**. |
| $\Delta t > 12\text{ hours}$ | 🔴 **Prolonged Disconnect** | Extended outage; requires immediate phone check-in. | Red alert card urging caregiver to call the patient. |

### 4.2 Cryptographic Access & HMAC Pairing
1. Patient taps **"Share Caregiver Code"** on the mobile device.
2. Device generates a cryptographically secure 6-character alphanumeric code.
3. Code is transmitted over HTTPS to the portal and stored as an **HMAC-SHA256** hash:
   $$\text{Hash} = \text{HMAC-SHA256}(\text{SecretKey}, \text{AccessCode})$$
4. Plaintext codes are never stored as primary keys in the database.
5. Once paired, an encrypted session token is established for read-only access.

### 4.3 Live Dashboard & Clinical Export
* **Live Deployment:** [https://4kaurora07.github.io/DocFlow/](https://4kaurora07.github.io/DocFlow/)
* **Adherence Gauges:** Rolling 7-day and 30-day compliance calculations:
  $$\text{Adherence Rate} = \frac{\text{Taken Doses}}{\text{Total Scheduled Doses} - \text{Skipped Doses}} \times 100\%$$
* **Clinical CSV Export:** Exports sanitized chronological logs with dose name, scheduled time, actual taken time, latency (minutes early/late), and status for physician consultations.

---

## 5. Security, Privacy & Safety Model

1. **Lock Screen Confidentiality:** Prescriptions and medical alerts are classified as protected health information (PHI). DoseFlow guarantees no prescription names or popups are displayed over locked screens.
2. **Read-Only Caregiver Model:** Caregivers cannot alter schedules, delete doses, or modify prescriptions remotely. All medication regimen alterations must physically happen on the patient's authenticated device.
3. **Zero Third-Party Trackers:** No analytics SDKs, advertising beacons, or third-party telemetry libraries are included in the mobile app.

---

## 6. Developer & Build Guide

### 6.1 Prerequisites
- **Android Studio Ladybug (2024.2.1+)** or command-line SDK tools
- **Android SDK:** Compile SDK 35, Target SDK 35, Minimum SDK 26 (Android 8.0 Oreo)
- **Java Development Kit:** JDK 17 (LTS)
- **Node.js:** v18.0.0 or higher

### 6.2 Building the Android APK
DoseFlow provides a one-command build tool that manages environment variables, Gradle daemons, and compiler flags:

```bash
# Build the Debug APK
npm run apk
```

The output binary is produced at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To run a direct release build with ProGuard/R8 shrinking:
```bash
./gradlew assembleRelease
```

### 6.3 Running the Caregiver Portal
```bash
# Run local caregiver dashboard and Express sync server
npm run portal
```
Accessible at: `http://localhost:3000`

---

## 7. Verification & Test Suite

The project maintains an automated test suite verifying domain calculations, database migrations, alarm logic, and security:

```bash
# Run all unit tests (75+ tests covering reconciler, alarms, and occurrences)
./gradlew testDebugUnitTest

# Run Compose UI & Instrumentation tests
./gradlew compileDebugAndroidTestKotlin

# Run Caregiver Portal security & API verification tests
cd doseflow-portal && npm test
```

---

<p align="center">
  <em>DoseFlow — Engineered for patient health, built with software integrity.</em>
</p>
