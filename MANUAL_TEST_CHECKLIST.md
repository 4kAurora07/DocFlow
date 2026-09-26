# DoseFlow — Manual Testing & Device Verification Checklist

This checklist documents critical platform and device-level scenarios for **DoseFlow** (`com.doseflow.app`). In compliance with engineering guidelines, simulated/unit test validations are distinguished from checks requiring a physical device or specific OEM hardware.

---

## 1. System & Physical Verification Matrix

| Test Scenario | Test Method | Physical Device Required? | Verification Status | Notes / Behavioral Limitations |
| :--- | :--- | :---: | :---: | :--- |
| **Swiping app from Recents** | Physical / Emulator | No (Emulator / Device) | **Verified (Design & Unit tested)** | Swiping from recents removes the app task but leaves registered AlarmManager exact alarms intact. Does **not** equal Force Stop. |
| **Android Force Stop** | Physical / Emulator | No (Device / Settings) | **Documented Limitation** | Under Android platform design, Force Stop places app in a stopped state where no broadcasts (including alarms and `BOOT_COMPLETED`) will fire until the user manually relaunches the app. |
| **Physical Device Reboot (`BOOT_COMPLETED`)** | Physical Device | **Yes** | **Requires Physical Device** | Emulated `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED` tests the receiver logic, but physical reboot verifies hardware startup ordering, disk decryption, and OEM autostart policies. |
| **Doze Mode & Deep Idle (`setExactAndAllowWhileIdle`)** | Physical / `adb shell dumpsys deviceidle` | No (Can simulate with adb) | **Verified (API & Unit tested)** | Alarms use `setExactAndAllowWhileIdle`. When device is in deep Doze, alarms wake the device within the platform-allowed idle maintenance window. |
| **Airplane Mode Toggle** | Physical / Emulator | No | **Verified (Offline Architecture)** | DoseFlow operates 100% offline-first via local Room SQLite and local AlarmManager. Network state changes have zero impact on reminders. |
| **Notification Permission Denied -> Granted** | Physical / Android 13+ (API 33+) | No (Emulator / Device) | **Verified (Diagnostics implemented)** | If notification permission is denied, alarms continue to execute in Room and log diagnostics; once granted, notifications post immediately. |
| **Exact Alarm Permission Denied -> Granted** | Physical / Android 12+ (API 31+) | No (Settings toggle) | **Verified (`BootAndReconcileReceiver`)** | Reconciler listens for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` and immediately reschedules exact alarms when granted. |
| **Overlapping Schedules for 1 Medicine** | Automated Unit Tests | No | **Verified (Automated in `OccurrenceCalculatorTest`)** | Verified with distinct occurrence IDs (`occ_${scheduleId}_...`). |
| **Delete / Archive with Pending Alarms** | Room Cascade / Scheduler | No | **Verified (Cascade & Cancellation)** | Room foreign key cascade and `AlarmScheduler.cancelDoseAlarms()` remove alarms and pending records. |
| **Device Time & Timezone Change** | BroadcastReceiver | No | **Verified (Automated in `DstAndZoneTest`)** | Listens for `TIME_SET` and `TIMEZONE_CHANGED`, idempotently reconciling alarms without duplicate records. |
| **Snooze, Escalation, & Missed Transitions** | Receiver / Domain Logic | No | **Verified (Automated in `AlarmAndNotificationTest`)** | Snooze 10m pushes alarm by 10 minutes without creating duplicate record; escalation fires at +15m; auto-missed triggers at +45m. |
| **Repeated Action Button Taps** | BroadcastReceiver / Room | No | **Verified (Idempotent updates)** | `NotificationActionReceiver` executes idempotent Room status updates and dismisses the notification on the first tap. |

---

## 2. OEM-Specific Aggressive Power Management Checklist

> [!WARNING]
> Exact-alarm permissions (`SCHEDULE_EXACT_ALARM`) and `setExactAndAllowWhileIdle` do **not** grant immunity against non-standard OEM process killers. Physical testing on target OEM hardware is required for guaranteed background survival.

### Samsung (One UI)
- [ ] Verify DoseFlow is added to **"Never sleeping apps"** (`Settings -> Battery and device care -> Background usage limits`).
- [ ] Confirm notifications appear on lock screen with full action buttons.

### Xiaomi / Redmi / Poco (MIUI / HyperOS)
- [ ] Open `Settings -> Apps -> Manage Apps -> DoseFlow`.
- [ ] Verify **"Autostart"** is explicitly enabled.
- [ ] Under **"Battery saver"**, ensure **"No restrictions"** is selected.

### OnePlus / Oppo / Realme (OxygenOS / ColorOS)
- [ ] Navigate to `Settings -> Battery -> App battery management -> DoseFlow`.
- [ ] Enable **"Allow background activity"** and **"Allow auto-launch"**.

### Huawei / Honor (EMUI / MagicOS)
- [ ] Navigate to `Settings -> Battery -> App launch`.
- [ ] Disable **"Manage automatically"** and verify **"Auto-launch"**, **"Secondary launch"**, and **"Run in background"** are all enabled.

---

## 3. Step-by-Step Manual Device Execution Steps

1. **Exact-Alarm Settings Verification**:
   ```bash
   adb shell am start -a android.settings.REQUEST_SCHEDULE_EXACT_ALARM -d package:com.doseflow.app
   ```
2. **Simulate Doze Mode During Scheduled Dose**:
   ```bash
   # Unplug USB battery emulation
   adb shell dumpsys battery unplug
   # Step into deep doze
   adb shell dumpsys deviceidle step
   adb shell dumpsys deviceidle step
   # Confirm state is IDLE
   adb shell dumpsys deviceidle get
   ```
3. **Simulate Boot Completed Broadcast**:
   ```bash
   adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.doseflow.app
   ```
4. **Inspect Active Alarms Scheduled in Android System**:
   ```bash
   adb shell dumpsys alarm | findstr "com.doseflow.app"
   ```
