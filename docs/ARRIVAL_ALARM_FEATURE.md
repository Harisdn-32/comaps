# Destination Arrival Alarm Feature

This document explains the architecture, design decisions, and maintenance workflow for the **Destination Arrival Alarm** feature implemented in CoMaps.

---

## 1. Feature Overview

The **Destination Arrival Alarm** acts as an alarm clock / proximity alert for commuters and travelers:
- When active turn-by-turn navigation is running, the app continuously monitors remaining route distance to destination.
- When the remaining distance drops below a user-configured radius (e.g., 200m, 500m, 1km, 2km, 5km), the feature triggers a **full-screen waking alarm**:
  - Continuous audible alarm tone (using `AudioManager.STREAM_ALARM`).
  - Pulsing vibration pattern.
  - High-priority full-screen intent notification that turns on the device display and shows over the lock screen.
  - Interactive full-screen UI (`ArrivalAlarmActivity`) displaying the destination name, remaining distance, a large "Dismiss Alarm" button, and a "View Map" button.
  - Automatic single-trigger state management to prevent continuous ringing once acknowledged.

---

## 2. Modular Architecture & Upstream Sync Protection

To ensure future updates from upstream (`comaps/comaps`) can be integrated automatically via GitHub Actions without merge conflicts or losing the feature, this implementation follows a **Zero-Invasive Isolation Pattern**:

### Self-Contained Files (100% Conflict-Free)
All custom feature code resides in dedicated files that do not exist upstream:
- `android/app/src/main/java/app/organicmaps/alarm/ArrivalAlarmManager.java` (Core lifecycle & distance tracker)
- `android/app/src/main/java/app/organicmaps/alarm/ArrivalAlarmService.java` (Audio loop, vibration, notification channel)
- `android/app/src/main/java/app/organicmaps/alarm/ArrivalAlarmActivity.java` (Full-screen display, screen wake, UI)
- `android/app/src/main/java/app/organicmaps/alarm/ArrivalAlarmReceiver.java` (Notification action broadcast receiver)
- `android/app/src/main/java/app/organicmaps/alarm/ArrivalAlarmHelper.java` (Settings access and distance unit math)
- `android/app/src/main/res/layout/activity_arrival_alarm.xml` (Full-screen alarm layout)
- `android/app/src/main/res/drawable/ic_alarm_bell.xml` (Alarm bell vector icon)
- `android/app/src/main/res/drawable/bg_alarm_pulse.xml` (Pulsing background drawable)
- `android/app/src/main/res/drawable/bg_alarm_card.xml` (Destination card drawable)
- `android/app/src/main/res/values/alarm_strings.xml` (Isolated strings file)
- `android/app/src/main/res/values/alarm_arrays.xml` (Isolated radius arrays)
- `.github/workflows/upstream-sync.yml` (Automated daily upstream sync workflow)

### Minimal Upstream Touchpoints
Only single-line hook calls were placed in upstream files:
1. **`NavigationService.java`**:
   - `ArrivalAlarmManager.getInstance(this).onRouteFollowingInfo(this, routingInfo, location);`
   - `ArrivalAlarmManager.getInstance(this).onNavigationStopped(this);`
2. **`MwmActivity.java`**:
   - `ArrivalAlarmManager.getInstance(this).onRouteFollowingInfo(this, info, location);`
   - `ArrivalAlarmManager.getInstance(this).onNavigationStopped(this);`
3. **`AndroidManifest.xml`**:
   - Permissions: `VIBRATE`, `USE_FULL_SCREEN_INTENT`.
   - Declarations for `ArrivalAlarmActivity`, `ArrivalAlarmService`, `ArrivalAlarmReceiver`.
4. **`prefs_navigation.xml`**:
   - Settings category for Arrival Alarm toggle and distance selector.

---

## 3. GitHub Actions Upstream Sync Workflow

Located at `.github/workflows/upstream-sync.yml`:
- **Automated Schedule**: Runs every morning at 04:00 UTC.
- **Manual Trigger**: Can be run on-demand via GitHub Actions `workflow_dispatch`.
- **Merge Strategy**: Fetches `upstream/main` and merges into your fork's `main` branch. Because all core logic is isolated in `alarm/`, merges execute cleanly without conflicts.
- **Conflict Notification**: If an upstream change ever alters one of the 4 touchpoints, the workflow aborts cleanly and opens a GitHub Issue with resolution instructions.

---

## 4. How to Configure the Feature in App

1. Open CoMaps.
2. Go to **Settings** -> **Navigation Settings**.
3. Under **Destination Arrival Alarm**:
   - Toggle **Arrival alarm** on or off (enabled by default).
   - Tap **Alarm trigger distance** to choose your preferred proximity:
     - 200 m
     - 500 m (Default)
     - 1 km
     - 2 km
     - 5 km
