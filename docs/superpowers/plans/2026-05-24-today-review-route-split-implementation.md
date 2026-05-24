# Today / Review Route Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Separate the user-facing `今天` and `回看` runtime paths so Review no longer depends on the Today/Flow ViewModel or model conversion.

**Architecture:** Keep historical `flow/*` route strings and `ACTION_OPEN_FLOW` intent compatibility, but replace the shared route with `TodayRoute` and `ReviewHomeRoute`. Rename `FlowViewModel`/`FlowUiState` to `TodayViewModel`/`TodayUiState`, and let Review collect only saved review-chat summary state.

**Tech Stack:** Kotlin, Jetpack Compose Navigation, AndroidX Lifecycle ViewModel, Kotlin Flow, Gradle unit tests, release APK emulator validation.

---

### Task 1: Lock The Split With Tests

**Files:**
- Modify: `app/src/test/java/com/mindflow/app/ui/screens/flow/TodayDesignModelTest.kt`
- Create: `app/src/test/java/com/mindflow/app/ui/screens/flow/ReviewHomeRouteContractTest.kt`

- [x] **Step 1: Rename Today model tests to use `TodayUiState`**

Replace each `FlowUiState(...)` fixture in `TodayDesignModelTest` with `TodayUiState(...)`. This should fail before production code is renamed.

- [x] **Step 2: Add a Review home dependency contract test**

Add:

```kotlin
package com.mindflow.app.ui.screens.flow

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewHomeRouteContractTest {
    @Test
    fun reviewHomeRouteDoesNotDependOnTodayState() {
        assertThat(reviewHomeRouteDependencyLabels())
            .containsExactly("saved-review-chat-summary", "review-navigation")
            .inOrder()
    }
}
```

- [x] **Step 3: Run the focused tests and verify they fail**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.mindflow.app.ui.screens.flow.TodayDesignModelTest' --tests 'com.mindflow.app.ui.screens.flow.ReviewHomeRouteContractTest'
```

Expected: compile/test failure because `TodayUiState` and `reviewHomeRouteDependencyLabels()` do not exist yet.

### Task 2: Introduce ReviewHomeRoute

**Files:**
- Create: `app/src/main/java/com/mindflow/app/ui/screens/flow/ReviewHomeRoute.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt`

- [x] **Step 1: Create ReviewHomeRoute**

Create a route that collects `ReviewChatSavedConversationRepository.observeLatestSavedSessionSummary()` and renders `ReviewChatEntryCard` inside the same screen background/bottom-bar padding pattern used by the old review branch.

- [x] **Step 2: Add the dependency contract function**

Add:

```kotlin
internal fun reviewHomeRouteDependencyLabels(): List<String> =
    listOf("saved-review-chat-summary", "review-navigation")
```

- [x] **Step 3: Wire `FLOW_REVIEW` to ReviewHomeRoute**

In `MindFlowApp.kt`, replace the review `FlowRoute(...)` call with `ReviewHomeRoute(...)`. It should pass review-chat navigation callbacks only, with no Today ViewModel parameter.

### Task 3: Rename Flow Runtime To Today Runtime

**Files:**
- Move: `app/src/main/java/com/mindflow/app/ui/screens/flow/FlowViewModel.kt` to `app/src/main/java/com/mindflow/app/ui/screens/flow/TodayViewModel.kt`
- Move: `app/src/main/java/com/mindflow/app/ui/screens/flow/FlowScreen.kt` to `app/src/main/java/com/mindflow/app/ui/screens/flow/TodayScreen.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/flow/TodayDesignModel.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/flow/IncubationSurfaceState.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/flow/TodaySecondaryScreens.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt`

- [x] **Step 1: Rename ViewModel and state types**

Rename `FlowViewModel` to `TodayViewModel` and `FlowUiState` to `TodayUiState`, including factory creation and test fixtures.

- [x] **Step 2: Rename route and screen types**

Rename `FlowRoute` to `TodayRoute` and `FlowScreen` to `TodayScreen`. Remove `initialFocus`, `FlowPage`, and `FlowFocus.toPage()` from the Today screen path.

- [x] **Step 3: Update Today secondary routes**

Change `TodayDiscoveryRoute` and `TodayTaskDetailRoute` to accept `TodayViewModel`.

### Task 4: Remove Dead Flow UI Branches

**Files:**
- Modify: `app/src/main/java/com/mindflow/app/ui/screens/flow/TodayScreen.kt`

- [x] **Step 1: Delete the review branch from TodayScreen**

Remove the old `FlowPage.REVIEW` branch and all imports used only by it.

- [x] **Step 2: Delete obsolete private Flow-era cards**

Delete private composables that are no longer reachable from `TodayRoute`, including `TodayOverviewCard`, `RecentAbsorptionCard`, `MainlineFocusCard`, `SettledKnowledgeCard`, `FeedGapCard`, `KnowledgeTrailCard`, `DirectionCard`, `KnowledgePayoffCard`, `ConnectionCard`, `ThreadRow`, `WeeklyReviewCard`, `TodayNoteCard`, `ExplorationPromptCard`, and `GentleReconnectCard`.

- [x] **Step 3: Search for old callable types**

Run:

```bash
rg -n 'FlowRoute|FlowViewModel|FlowUiState|FlowScreen\\(' app/src/main/java app/src/test/java app/src/androidTest/java
```

Expected: no production/test references to the removed callable types.

### Task 5: Verify And Validate On Emulator

**Files:**
- Modify: `implementation-notes.html`

- [x] **Step 1: Run focused tests**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.mindflow.app.ui.screens.flow.*'
```

Expected: build success.

- [x] **Step 2: Run full debug unit tests**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
```

Expected: build success.

- [x] **Step 3: Build release APK**

Run:

```bash
./gradlew --no-daemon :app:assembleRelease
```

Expected: `BUILD SUCCESSFUL`; known R8 Kotlin metadata warnings are acceptable.

- [x] **Step 4: Install release APK to emulator and launch**

Run:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
adb -s emulator-5554 shell am start -n com.mindflow.app/.SplashActivity
```

Expected: install succeeds and focus reaches `com.mindflow.app/.MainActivity`.

- [x] **Step 5: Capture evidence**

Check:

```bash
adb -s emulator-5554 shell dumpsys window | rg 'mCurrentFocus|mFocusedApp'
adb -s emulator-5554 logcat -d -t 300 | rg 'FATAL EXCEPTION|AndroidRuntime'
```

Expected: current focus is MindFlow MainActivity; crash search returns no matches.
