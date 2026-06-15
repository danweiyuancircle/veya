# Veya Source Scoring And Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Veya from a single-source detail flow into a unified multi-source playback system with normalized parser outputs, local source scoring, local history/favorites, and a recoverable detail playback state machine.

**Architecture:** Keep the current parser registry and KMP UI structure, but insert a normalized domain layer between parsers and screens. Search and detail flows will no longer consume parser-specific models directly. Instead, parsers return unified source models, repositories persist local scoring/history/favorites in `Settings`, and the detail screen drives playback through a single state machine that can recover across routes and sources.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx.coroutines, kotlinx.serialization, russhwolf Settings, Media3 / ExoPlayer callbacks on Android

---

## File Structure

### Existing files to modify

- `composeApp/src/commonMain/kotlin/com/watchvideo/data/SiteParser.kt`
  Purpose: normalize parser interface outputs
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/model/Models.kt`
  Purpose: replace lightweight source models with normalized parser-facing models
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/ParserRegistry.kt`
  Purpose: expose parser list for scoring-aware orchestration
- `composeApp/src/commonMain/kotlin/com/watchvideo/ui/search/SearchViewModel.kt`
  Purpose: drive grouped search results ordered by local source score
- `composeApp/src/commonMain/kotlin/com/watchvideo/ui/search/SearchScreen.kt`
  Purpose: render grouped results with lightweight score labels
- `composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/DetailViewModel.kt`
  Purpose: own aggregated detail loading, playback state machine, and history restoration
- `composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/DetailScreen.kt`
  Purpose: render unified source / route / episode playback UI
- `composeApp/src/commonMain/kotlin/com/watchvideo/ui/settings/SettingsScreen.kt`
  Purpose: show local data controls and source diagnostics
- `composeApp/src/androidMain/kotlin/com/watchvideo/ui/detail/VideoPlayerArea.android.kt`
  Purpose: report playback start, first frame timing, and actual resolution

### New files to create

- `composeApp/src/commonMain/kotlin/com/watchvideo/data/model/SourceModels.kt`
  Purpose: normalized parser output models
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/model/PlaybackUiModels.kt`
  Purpose: aggregated detail and playback UI state models
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/model/LocalLibraryModels.kt`
  Purpose: source score, history, favorites, failure types
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/local/JsonStore.kt`
  Purpose: shared JSON encode / decode helpers over `Settings`
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/local/SourceScoreStore.kt`
  Purpose: persist and rank source score records
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/local/WatchHistoryStore.kt`
  Purpose: persist and restore watch history
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/local/FavoritesStore.kt`
  Purpose: persist favorites
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/SourceSearchService.kt`
  Purpose: orchestrate parser search, validation, scoring side effects
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/AggregatedDetailService.kt`
  Purpose: load, merge, and rank multiple source details for one content
- `composeApp/src/commonMain/kotlin/com/watchvideo/data/PlaybackRecoveryPolicy.kt`
  Purpose: decide next route / source when playback candidate or playback start fails
- `composeApp/src/commonMain/kotlin/com/watchvideo/ui/history/HistoryScreen.kt`
  Purpose: render local watch history list
- `composeApp/src/commonMain/kotlin/com/watchvideo/ui/history/HistoryViewModel.kt`
  Purpose: expose history data and clear actions
- `composeApp/src/commonMain/kotlin/com/watchvideo/ui/favorites/FavoritesScreen.kt`
  Purpose: render local favorites list
- `composeApp/src/commonMain/kotlin/com/watchvideo/ui/favorites/FavoritesViewModel.kt`
  Purpose: expose favorites data and remove actions

### Tests to create

- `composeApp/src/commonTest/kotlin/com/watchvideo/data/SourceSearchServiceTest.kt`
- `composeApp/src/commonTest/kotlin/com/watchvideo/data/AggregatedDetailServiceTest.kt`
- `composeApp/src/commonTest/kotlin/com/watchvideo/data/PlaybackRecoveryPolicyTest.kt`
- `composeApp/src/commonTest/kotlin/com/watchvideo/data/local/SourceScoreStoreTest.kt`
- `composeApp/src/commonTest/kotlin/com/watchvideo/data/local/WatchHistoryStoreTest.kt`
- `composeApp/src/commonTest/kotlin/com/watchvideo/data/local/FavoritesStoreTest.kt`

---

### Task 1: Introduce normalized parser models

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/model/SourceModels.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/data/SiteParser.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/data/model/Models.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/SourceSearchServiceTest.kt`

- [ ] **Step 1: Write the failing model-usage test**

```kotlin
package com.watchvideo.data

import com.watchvideo.data.model.SourceSearchItem
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceSearchServiceTest {
    @Test
    fun normalized_search_item_keeps_source_identity() {
        val item = SourceSearchItem(
            sourceKey = "modu",
            sourceName = "模板影视",
            contentKey = "the-hero-2026",
            sourceContentId = "123",
            title = "The Hero",
            coverUrl = null,
            subtitle = null,
            tags = emptyList(),
            detailUrl = null,
        )

        assertEquals("modu", item.sourceKey)
        assertEquals("123", item.sourceContentId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.SourceSearchServiceTest.normalized_search_item_keeps_source_identity`
Expected: FAIL because `SourceSearchItem` does not exist

- [ ] **Step 3: Write normalized source models and update parser interface**

```kotlin
package com.watchvideo.data.model

data class SourceSearchItem(
    val sourceKey: String,
    val sourceName: String,
    val contentKey: String,
    val sourceContentId: String,
    val title: String,
    val coverUrl: String?,
    val subtitle: String?,
    val tags: List<String>,
    val detailUrl: String?,
)

data class MetaBadge(
    val label: String,
    val value: String
)

data class PlaybackEpisode(
    val episodeKey: String,
    val episodeLabel: String,
    val playPageUrl: String
)

data class PlaybackRoute(
    val routeKey: String,
    val routeName: String,
    val episodes: List<PlaybackEpisode>
)

data class SourceDetail(
    val sourceKey: String,
    val sourceName: String,
    val contentKey: String,
    val sourceContentId: String,
    val title: String,
    val coverUrl: String?,
    val summary: String?,
    val metadata: List<MetaBadge>,
    val routes: List<PlaybackRoute>,
)

data class PlaybackCandidate(
    val sourceKey: String,
    val routeKey: String,
    val episodeKey: String,
    val title: String,
    val streamUrl: String,
    val headers: Map<String, String>,
)
```

```kotlin
package com.watchvideo.data

import com.watchvideo.data.model.PlaybackCandidate
import com.watchvideo.data.model.SourceDetail
import com.watchvideo.data.model.SourceSearchItem

interface SiteParser {
    val siteKey: String
    val siteName: String
    val baseUrl: String

    suspend fun search(keyword: String): List<SourceSearchItem>
    suspend fun detail(id: String): SourceDetail
    suspend fun playInfo(playPageUrl: String): PlaybackCandidate
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.SourceSearchServiceTest.normalized_search_item_keeps_source_identity`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/watchvideo/data/SiteParser.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/data/model/Models.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/data/model/SourceModels.kt \
  composeApp/src/commonTest/kotlin/com/watchvideo/data/SourceSearchServiceTest.kt
git commit -m "feat: normalize parser output models"
```

### Task 2: Persist local score, history, and favorites

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/model/LocalLibraryModels.kt`
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/local/JsonStore.kt`
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/local/SourceScoreStore.kt`
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/local/WatchHistoryStore.kt`
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/local/FavoritesStore.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/local/SourceScoreStoreTest.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/local/WatchHistoryStoreTest.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/local/FavoritesStoreTest.kt`

- [ ] **Step 1: Write the failing score ranking test**

```kotlin
package com.watchvideo.data.local

import com.russhwolf.settings.MapSettings
import com.watchvideo.data.model.SourceScoreRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceScoreStoreTest {
    @Test
    fun higher_stable_resolution_and_success_rate_ranks_first() {
        val store = SourceScoreStore(MapSettings())

        store.save(
            SourceScoreRecord(
                sourceKey = "a",
                baseScore = 50,
                searchSuccessCount = 10,
                searchFailureCount = 0,
                detailSuccessCount = 10,
                detailFailureCount = 0,
                playCandidateSuccessCount = 10,
                playCandidateFailureCount = 0,
                playbackStartSuccessCount = 10,
                playbackStartFailureCount = 0,
                consecutiveFailureCount = 0,
                avgFirstFrameMs = 1200,
                stableObservedHeight = 1080,
                maxObservedHeight = 1080,
                lastFailureType = null,
                lastFailureAtEpochMs = null,
                cooldownUntilEpochMs = null,
            )
        )

        store.save(
            SourceScoreRecord(
                sourceKey = "b",
                baseScore = 50,
                searchSuccessCount = 10,
                searchFailureCount = 2,
                detailSuccessCount = 8,
                detailFailureCount = 2,
                playCandidateSuccessCount = 8,
                playCandidateFailureCount = 2,
                playbackStartSuccessCount = 8,
                playbackStartFailureCount = 2,
                consecutiveFailureCount = 1,
                avgFirstFrameMs = 2500,
                stableObservedHeight = 720,
                maxObservedHeight = 1080,
                lastFailureType = null,
                lastFailureAtEpochMs = null,
                cooldownUntilEpochMs = null,
            )
        )

        assertEquals(listOf("a", "b"), store.rank(listOf("a", "b")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.local.SourceScoreStoreTest.higher_stable_resolution_and_success_rate_ranks_first`
Expected: FAIL because `SourceScoreStore` and `SourceScoreRecord` do not exist

- [ ] **Step 3: Implement local persistence models and stores**

```kotlin
package com.watchvideo.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class FailureType {
    SEARCH_EMPTY,
    SEARCH_ERROR,
    DETAIL_ERROR,
    DETAIL_EMPTY,
    PLAY_CANDIDATE_ERROR,
    PLAY_CANDIDATE_EMPTY,
    PLAYBACK_START_ERROR,
    PLAYBACK_INTERRUPTED,
}

@Serializable
data class SourceScoreRecord(
    val sourceKey: String,
    val baseScore: Int = 50,
    val searchSuccessCount: Int = 0,
    val searchFailureCount: Int = 0,
    val detailSuccessCount: Int = 0,
    val detailFailureCount: Int = 0,
    val playCandidateSuccessCount: Int = 0,
    val playCandidateFailureCount: Int = 0,
    val playbackStartSuccessCount: Int = 0,
    val playbackStartFailureCount: Int = 0,
    val consecutiveFailureCount: Int = 0,
    val avgFirstFrameMs: Long? = null,
    val stableObservedHeight: Int? = null,
    val maxObservedHeight: Int? = null,
    val lastFailureType: FailureType? = null,
    val lastFailureAtEpochMs: Long? = null,
    val cooldownUntilEpochMs: Long? = null,
)

@Serializable
data class WatchHistoryItem(
    val contentKey: String,
    val sourceKey: String,
    val sourceContentId: String,
    val routeKey: String,
    val episodeKey: String,
    val title: String,
    val coverUrl: String? = null,
    val episodeLabel: String,
    val progressMs: Long,
    val durationMs: Long,
    val lastWatchedAtEpochMs: Long,
)

@Serializable
data class FavoriteItem(
    val contentKey: String,
    val title: String,
    val coverUrl: String? = null,
    val summary: String? = null,
    val preferredSourceKey: String? = null,
    val sourceContentId: String? = null,
    val favoriteAtEpochMs: Long,
)
```

- [ ] **Step 4: Run store tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.local.SourceScoreStoreTest --tests com.watchvideo.data.local.WatchHistoryStoreTest --tests com.watchvideo.data.local.FavoritesStoreTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/watchvideo/data/model/LocalLibraryModels.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/data/local/JsonStore.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/data/local/SourceScoreStore.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/data/local/WatchHistoryStore.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/data/local/FavoritesStore.kt \
  composeApp/src/commonTest/kotlin/com/watchvideo/data/local/SourceScoreStoreTest.kt \
  composeApp/src/commonTest/kotlin/com/watchvideo/data/local/WatchHistoryStoreTest.kt \
  composeApp/src/commonTest/kotlin/com/watchvideo/data/local/FavoritesStoreTest.kt
git commit -m "feat: add local score history and favorites stores"
```

### Task 3: Add scoring-aware search orchestration

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/SourceSearchService.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/search/SearchViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/search/SearchScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/SourceSearchServiceTest.kt`

- [ ] **Step 1: Write the failing grouped-search ranking test**

```kotlin
@Test
fun search_groups_are_sorted_by_local_source_rank() = runTest {
    val parsers = listOf(
        FakeParser(siteKey = "slow", siteName = "慢源", searchResults = listOf(sampleItem("slow"))),
        FakeParser(siteKey = "fast", siteName = "快源", searchResults = listOf(sampleItem("fast"))),
    )
    val scoreStore = SourceScoreStore(MapSettings()).apply {
        save(SourceScoreRecord(sourceKey = "slow", stableObservedHeight = 720, playbackStartSuccessCount = 3))
        save(SourceScoreRecord(sourceKey = "fast", stableObservedHeight = 1080, playbackStartSuccessCount = 8))
    }

    val service = SourceSearchService(parsers, scoreStore)

    val groups = service.searchGrouped("hero")

    assertEquals(listOf("fast", "slow"), groups.map { it.siteKey })
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.SourceSearchServiceTest.search_groups_are_sorted_by_local_source_rank`
Expected: FAIL because `SourceSearchService.searchGrouped` does not exist

- [ ] **Step 3: Implement grouped search service and wire SearchViewModel**

```kotlin
class SourceSearchService(
    private val parsers: List<SiteParser>,
    private val scoreStore: SourceScoreStore,
) {
    suspend fun searchGrouped(keyword: String): List<SiteResultGroup> {
        val groups = parsers.mapNotNull { parser ->
            val results = parser.search(keyword)
                .filter { it.title.isNotBlank() && it.sourceContentId.isNotBlank() }
            if (results.isEmpty()) null
            else SiteResultGroup(
                siteKey = parser.siteKey,
                siteName = parser.siteName,
                results = results,
            )
        }

        return groups.sortedBy { scoreStore.rankValue(it.siteKey) * -1 }
    }
}
```

- [ ] **Step 4: Run search tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.SourceSearchServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/watchvideo/data/SourceSearchService.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/search/SearchViewModel.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/search/SearchScreen.kt \
  composeApp/src/commonTest/kotlin/com/watchvideo/data/SourceSearchServiceTest.kt
git commit -m "feat: rank grouped search results by source score"
```

### Task 4: Build aggregated detail loading and playback recovery policy

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/model/PlaybackUiModels.kt`
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/AggregatedDetailService.kt`
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/data/PlaybackRecoveryPolicy.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/AggregatedDetailServiceTest.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/PlaybackRecoveryPolicyTest.kt`

- [ ] **Step 1: Write the failing aggregated detail merge test**

```kotlin
package com.watchvideo.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AggregatedDetailServiceTest {
    @Test
    fun merged_detail_keeps_all_source_variants() = runTest {
        val aggregated = AggregatedDetailService(
            parsers = emptyList(),
            scoreStore = fakeScoreStore(),
        ).merge(
            listOf(
                sampleSourceDetail(sourceKey = "a"),
                sampleSourceDetail(sourceKey = "b"),
            )
        )

        assertEquals(2, aggregated.sourceDetails.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.AggregatedDetailServiceTest.merged_detail_keeps_all_source_variants`
Expected: FAIL because `AggregatedDetailService` does not exist

- [ ] **Step 3: Implement aggregated detail merge and fallback policy**

```kotlin
data class AggregatedDetail(
    val contentKey: String,
    val title: String,
    val preferredCoverUrl: String?,
    val preferredSummary: String?,
    val metadata: List<MetaBadge>,
    val sourceDetails: List<SourceDetail>,
)

class AggregatedDetailService(
    private val parsers: List<SiteParser>,
    private val scoreStore: SourceScoreStore,
) {
    fun merge(details: List<SourceDetail>): AggregatedDetail {
        val ordered = details.sortedByDescending { scoreStore.rankValue(it.sourceKey) }
        val head = ordered.first()
        return AggregatedDetail(
            contentKey = head.contentKey,
            title = head.title,
            preferredCoverUrl = ordered.firstNotNullOfOrNull { it.coverUrl },
            preferredSummary = ordered.firstNotNullOfOrNull { it.summary },
            metadata = head.metadata,
            sourceDetails = ordered,
        )
    }
}
```

- [ ] **Step 4: Run aggregation and recovery tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.AggregatedDetailServiceTest --tests com.watchvideo.data.PlaybackRecoveryPolicyTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/watchvideo/data/model/PlaybackUiModels.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/data/AggregatedDetailService.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/data/PlaybackRecoveryPolicy.kt \
  composeApp/src/commonTest/kotlin/com/watchvideo/data/AggregatedDetailServiceTest.kt \
  composeApp/src/commonTest/kotlin/com/watchvideo/data/PlaybackRecoveryPolicyTest.kt
git commit -m "feat: add aggregated detail and recovery policy"
```

### Task 5: Replace DetailViewModel with a playback state machine

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/DetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/DetailScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/VideoPlayerArea.ios.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/watchvideo/ui/detail/VideoPlayerArea.android.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/PlaybackRecoveryPolicyTest.kt`

- [ ] **Step 1: Write the failing history-restore state test**

```kotlin
@Test
fun detail_state_prefers_history_source_route_and_episode() = runTest {
    val vm = DetailViewModel(
        aggregatedDetailService = fakeAggregatedDetailService(),
        historyStore = fakeHistoryStore(
            sourceKey = "modu",
            routeKey = "route-2",
            episodeKey = "ep-8",
        ),
        favoritesStore = fakeFavoritesStore(),
        scoreStore = fakeScoreStore(),
    )

    vm.loadDetail(contentKey = "hero-2026")

    val ready = vm.state.value as DetailPlaybackState.Ready
    assertEquals("modu", ready.selectedSourceKey)
    assertEquals("route-2", ready.selectedRouteKey)
    assertEquals("ep-8", ready.selectedEpisodeKey)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.PlaybackRecoveryPolicyTest.detail_state_prefers_history_source_route_and_episode`
Expected: FAIL because `DetailPlaybackState` and new `DetailViewModel` API do not exist

- [ ] **Step 3: Implement playback state machine and UI bindings**

```kotlin
sealed interface DetailPlaybackState {
    data object Idle : DetailPlaybackState
    data object LoadingDetail : DetailPlaybackState
    data class Ready(
        val detail: AggregatedDetail,
        val selectedSourceKey: String,
        val selectedRouteKey: String?,
        val selectedEpisodeKey: String?,
    ) : DetailPlaybackState
    data class ResolvingStream(
        val sourceKey: String,
        val routeKey: String,
        val episodeKey: String,
    ) : DetailPlaybackState
    data class Buffering(
        val sourceKey: String,
        val routeKey: String,
        val episodeKey: String,
    ) : DetailPlaybackState
    data class Playing(
        val sourceKey: String,
        val routeKey: String,
        val episodeKey: String,
        val resolutionHeight: Int?,
    ) : DetailPlaybackState
    data class Recovering(
        val attemptedSourceKeys: List<String>,
        val attemptedRouteKeys: List<String>,
    ) : DetailPlaybackState
    data class Failed(
        val message: String
    ) : DetailPlaybackState
}
```

- [ ] **Step 4: Run detail and playback tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.PlaybackRecoveryPolicyTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/DetailViewModel.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/DetailScreen.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/VideoPlayerArea.ios.kt \
  composeApp/src/androidMain/kotlin/com/watchvideo/ui/detail/VideoPlayerArea.android.kt \
  composeApp/src/commonTest/kotlin/com/watchvideo/data/PlaybackRecoveryPolicyTest.kt
git commit -m "feat: add detail playback state machine"
```

### Task 6: Record real playback resolution and update source score

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/watchvideo/ui/detail/VideoPlayerArea.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/DetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/data/local/SourceScoreStore.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/local/SourceScoreStoreTest.kt`

- [ ] **Step 1: Write the failing stable-resolution scoring test**

```kotlin
@Test
fun stable_playback_resolution_updates_score_record() {
    val store = SourceScoreStore(MapSettings())
    store.recordPlaybackResolution(sourceKey = "modu", firstFrameMs = 1200, stableHeight = 1080)

    val record = store.get("modu")!!

    assertEquals(1080, record.stableObservedHeight)
    assertEquals(1200, record.avgFirstFrameMs)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.local.SourceScoreStoreTest.stable_playback_resolution_updates_score_record`
Expected: FAIL because `recordPlaybackResolution` does not exist

- [ ] **Step 3: Implement Android callback bridge and score update**

```kotlin
player.addListener(object : Player.Listener {
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (videoSize.height > 0) {
            onResolutionObserved(videoSize.height)
        }
    }
})
```

```kotlin
fun recordPlaybackResolution(sourceKey: String, firstFrameMs: Long, stableHeight: Int) {
    val current = get(sourceKey) ?: SourceScoreRecord(sourceKey = sourceKey)
    save(
        current.copy(
            avgFirstFrameMs = firstFrameMs,
            stableObservedHeight = stableHeight,
            maxObservedHeight = maxOf(current.maxObservedHeight ?: 0, stableHeight),
            playbackStartSuccessCount = current.playbackStartSuccessCount + 1,
            consecutiveFailureCount = 0,
        )
    )
}
```

- [ ] **Step 4: Run score tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.data.local.SourceScoreStoreTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/watchvideo/ui/detail/VideoPlayerArea.android.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/detail/DetailViewModel.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/data/local/SourceScoreStore.kt \
  composeApp/src/commonTest/kotlin/com/watchvideo/data/local/SourceScoreStoreTest.kt
git commit -m "feat: score sources by real playback resolution"
```

### Task 7: Add History, Favorites, and Settings UI entry points

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/history/HistoryScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/history/HistoryViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/favorites/FavoritesScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/favorites/FavoritesViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/watchvideo/ui/settings/SettingsScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/local/WatchHistoryStoreTest.kt`
- Test: `composeApp/src/commonTest/kotlin/com/watchvideo/data/local/FavoritesStoreTest.kt`

- [ ] **Step 1: Write the failing app-tab presence test**

```kotlin
package com.watchvideo

import kotlin.test.Test
import kotlin.test.assertTrue

class AppNavigationModelTest {
    @Test
    fun app_tabs_include_search_history_favorites_and_settings() {
        val routes = appTabs().map { it.route }
        assertTrue(routes.containsAll(listOf("search", "history", "favorites", "settings")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest --tests com.watchvideo.AppNavigationModelTest.app_tabs_include_search_history_favorites_and_settings`
Expected: FAIL because the helper and tabs do not exist

- [ ] **Step 3: Add history and favorites screens, and wire App navigation**

```kotlin
private val tabs = listOf(
    TabItem("search", "搜索", Icons.Default.Search),
    TabItem("history", "历史", Icons.Default.History),
    TabItem("favorites", "收藏", Icons.Default.Favorite),
    TabItem("settings", "设置", Icons.Default.Settings),
)
```

- [ ] **Step 4: Run app and store tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :composeApp:testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/watchvideo/App.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/history/HistoryScreen.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/history/HistoryViewModel.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/favorites/FavoritesScreen.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/favorites/FavoritesViewModel.kt \
  composeApp/src/commonMain/kotlin/com/watchvideo/ui/settings/SettingsScreen.kt
git commit -m "feat: add history favorites and local controls"
```

## Self-Review

### Spec coverage

- Unified parser output models: covered by Task 1
- Local source scoring: covered by Tasks 2, 3, and 6
- History and favorites persistence: covered by Tasks 2 and 7
- Aggregated detail and playback state machine: covered by Tasks 4 and 5
- Real playback resolution scoring: covered by Task 6
- Search ordering by score: covered by Task 3

No spec gaps found.

### Placeholder scan

No `TODO`, `TBD`, “implement later”, or dangling placeholders.

### Type consistency

- Parser-facing models consistently use `sourceKey`, `sourceContentId`, `routeKey`, and `episodeKey`
- Aggregated detail consistently uses `contentKey`
- Playback state machine consistently uses `selectedSourceKey`, `selectedRouteKey`, and `selectedEpisodeKey`

No naming mismatches found.
