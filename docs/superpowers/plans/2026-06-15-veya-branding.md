# Veya Branding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the app-facing WatchVideo branding with Veya in the local KMP repository, wire the Android app icon, and update the public README branding.

**Architecture:** Keep code changes minimal. Android branding is applied through `AndroidManifest.xml` and `res/mipmap-xxxhdpi` launcher assets. Repository-facing branding is updated in `README.md` and `settings.gradle.kts`. iOS icon integration is deferred because this repository has no Xcode wrapper or `.xcassets` target yet.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android app resources, Markdown docs

---

### Task 1: Record the branding entry points

**Files:**
- Create: `docs/superpowers/plans/2026-06-15-veya-branding.md`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`
- Modify: `settings.gradle.kts`
- Modify: `README.md`

- [ ] Confirm Android app name comes from `composeApp/src/androidMain/AndroidManifest.xml`
- [ ] Confirm repository display name comes from `README.md` and `settings.gradle.kts`
- [ ] Confirm iOS wrapper files do not exist in the repository

### Task 2: Apply Android app icon and app name

**Files:**
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`
- Create: `composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png`
- Create: `composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png`

- [ ] Set `android:label="Veya"` in `composeApp/src/androidMain/AndroidManifest.xml`
- [ ] Add `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"` to the `<application>` node
- [ ] Copy the generated `192x192` PNG into the two Android launcher asset paths

### Task 3: Update repository-facing branding

**Files:**
- Modify: `README.md`
- Modify: `settings.gradle.kts`

- [ ] Change the README title from `WatchVideo` to `Veya`
- [ ] Insert the generated logo image near the README title using `design/app_icon/veya-ios-1024.png`
- [ ] Update the README subtitle to mention `Veya`
- [ ] Rename `rootProject.name` to `Veya`

### Task 4: Verify and report external blockers

**Files:**
- Review: `composeApp/src/androidMain/AndroidManifest.xml`
- Review: `README.md`
- Review: `settings.gradle.kts`

- [ ] Verify the Android icon files exist at the expected paths
- [ ] Verify the updated README renders a valid local image path
- [ ] Report that GitHub repository rename is blocked by invalid `gh` authentication
