# Today / Review Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate the app's Today, Review, and Graph code boundaries while preserving existing user-visible navigation and app data.

**Architecture:** Keep legacy route strings such as `flow/today` stable for compatibility, but rename internal route constants and package boundaries to the product concepts users see: Today, Review, and Graph. Today should consume a small review-preview model instead of depending directly on review chat persistence details.

**Tech Stack:** Kotlin, Jetpack Compose, Android Navigation, JUnit/Truth, Gradle Android plugin.

---

### Task 1: Add Boundary Tests

**Files:**
- Modify: `app/src/test/java/com/mindflow/app/ui/navigation/MindFlowDestinationsTest.kt`
- Modify: `app/src/test/java/com/mindflow/app/ui/screens/flow/ReviewHomeRouteContractTest.kt`
- Modify imports in Today/Review tests after package moves.

- [ ] Add tests that assert product-named destinations (`TODAY`, `REVIEW`, `GRAPH`) still map to legacy route strings.
- [ ] Add tests that assert Review home depends only on review preview/navigation labels.
- [ ] Run the targeted tests and confirm they fail before production changes.

### Task 2: Split UI Packages

**Files:**
- Move: `ui/screens/flow/TodayScreen.kt` -> `ui/screens/today/TodayScreen.kt`
- Move: `ui/screens/flow/TodayViewModel.kt` -> `ui/screens/today/TodayViewModel.kt`
- Move: `ui/screens/flow/TodayDesignModel.kt` -> `ui/screens/today/TodayDesignModel.kt`
- Move: `ui/screens/flow/TodaySecondaryScreens.kt` -> `ui/screens/today/TodaySecondaryScreens.kt`
- Move: `ui/screens/flow/IncubationSurfaceState.kt` -> `ui/screens/today/IncubationSurfaceState.kt`
- Move: `ui/screens/flow/ReviewHomeRoute.kt` -> `ui/screens/review/ReviewHomeRoute.kt`
- Move: `ui/screens/flow/ReviewChatEntryCard.kt` -> `ui/screens/review/ReviewChatEntryCard.kt`
- Leave graph files in `ui/screens/flow` for this pass only if moving the 4k-line graph screen would make the diff too noisy.

- [ ] Update package declarations and imports.
- [ ] Keep behavior unchanged.
- [ ] Run targeted unit tests.

### Task 3: Decouple Today From Review Storage

**Files:**
- Create: `app/src/main/java/com/mindflow/app/ui/screens/today/TodayReviewPreview.kt`
- Modify: `TodayScreen.kt`
- Modify: `TodayDesignModel.kt`

- [ ] Introduce `TodayReviewPreview(title, description, savedSessionId)` as the only review object Today consumes.
- [ ] Convert `SavedReviewChatSessionSummary` to this preview at the route boundary.
- [ ] Keep the Today card wording and click behavior unchanged.
- [ ] Run `TodayDesignModelTest`.

### Task 4: Rename Internal Navigation Constants

**Files:**
- Modify: `app/src/main/java/com/mindflow/app/ui/navigation/MindFlowDestinations.kt`
- Modify: `app/src/main/java/com/mindflow/app/ui/MindFlowApp.kt`
- Modify affected tests.

- [ ] Add product-named constants `TODAY`, `REVIEW`, and `GRAPH`.
- [ ] Preserve legacy route values `flow/today`, `flow/review`, and `flow/graph`.
- [ ] Replace app-internal `FLOW_*` usages where compatibility does not require the old names.
- [ ] Keep `ACTION_OPEN_FLOW` and `FlowFocus` as legacy external intent compatibility for now.

### Task 5: Remove Dead Flow Knowledge Compression Shell

**Files:**
- Rename or simplify `app/src/main/java/com/mindflow/app/data/flow/FlowKnowledgeCompressionPlanner.kt`
- Modify `TodayViewModel.kt`
- Modify `AppContainer.kt`
- Modify AI task labels only if no behavior change is needed.

- [ ] Remove unused AI client/settings dependencies from the planner if it still only returns fallback state.
- [ ] Rename Today-facing types away from `Flow*` where practical.
- [ ] Keep task enum values stable unless migration is clearly safe.

### Task 6: Verification

**Commands:**
- `./gradlew --no-daemon :app:testDebugUnitTest`
- `./gradlew --no-daemon :app:assembleRelease`

- [ ] Install the release APK on emulator only if UI validation is needed.
- [ ] Do not uninstall, clear data, wipe the AVD, or install a debug APK.
