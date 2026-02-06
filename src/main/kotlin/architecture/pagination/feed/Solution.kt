package architecture.pagination.feed

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Pagination Pattern - 해결책
 *
 * SNS 피드 앱에 Cursor 기반 페이지네이션과 Paging 라이브러리 패턴을 적용하여:
 * - 대용량 데이터를 효율적으로 로드
 * - 데이터 변경에도 일관된 페이지 제공
 * - 무한 스크롤 상태를 체계적으로 관리
 * - 네트워크/캐시/UI 간 데이터 흐름 표준화
 *
 * 핵심 구성:
 * - PagingSource: 데이터 소스 추상화 (네트워크/로컬)
 * - Pager: PagingSource로부터 페이지를 요청하는 엔트리포인트
 * - PagingData: 페이지 단위 데이터 스트림
 * - LoadState: 로딩/성공/에러 상태 관리
 * - RemoteMediator: 네트워크 + 로컬 캐시 동기화
 */

// ============================================================
// 1. 페이지네이션 모델
// ============================================================

/**
 * Cursor 기반 페이지 키
 * Offset 대신 마지막 항목의 고유 값을 기준으로 다음 페이지 조회
 */
sealed class PageKey {
    /** 첫 페이지 */
    object Initial : PageKey()

    /** 특정 ID 이후의 데이터 (시간순 정렬 시 마지막 항목의 createdAt 사용) */
    data class After(val lastId: Long, val lastTimestamp: LocalDateTime) : PageKey()

    /** 특정 ID 이전의 데이터 (위로 스크롤 시) */
    data class Before(val firstId: Long, val firstTimestamp: LocalDateTime) : PageKey()
}

/**
 * 페이지 로드 결과
 */
sealed class LoadResult<out T> {
    /** 성공 - 데이터와 다음/이전 페이지 키 포함 */
    data class Page<T>(
        val data: List<T>,
        val prevKey: PageKey?,     // null이면 이전 페이지 없음
        val nextKey: PageKey?      // null이면 다음 페이지 없음
    ) : LoadResult<T>()

    /** 에러 */
    data class Error(val exception: Throwable) : LoadResult<Nothing>()
}

/**
 * 로딩 상태
 */
sealed class LoadState {
    object NotLoading : LoadState()
    object Loading : LoadState()
    data class Error(val error: Throwable) : LoadState()

    val isLoading: Boolean get() = this is Loading
    val isError: Boolean get() = this is Error
}

/**
 * 로딩 상태 집합 (prepend/append/refresh)
 */
data class CombinedLoadStates(
    val refresh: LoadState = LoadState.NotLoading,   // 전체 새로고침
    val prepend: LoadState = LoadState.NotLoading,   // 앞쪽 로딩 (위로 스크롤)
    val append: LoadState = LoadState.NotLoading     // 뒤쪽 로딩 (아래로 스크롤)
) {
    val isAnyLoading: Boolean
        get() = refresh.isLoading || prepend.isLoading || append.isLoading

    val hasError: Boolean
        get() = refresh.isError || prepend.isError || append.isError
}

// ============================================================
// 2. PagingSource - 데이터 소스 추상화
// ============================================================

/**
 * 페이지 단위로 데이터를 로드하는 소스
 * 네트워크 또는 로컬 DB에서 데이터를 가져오는 방법을 정의
 */
abstract class PagingSource<Key, Value> {
    /**
     * 주어진 키로 페이지 로드
     * @param params 로드 파라미터 (키, 사이즈)
     */
    abstract suspend fun load(params: LoadParams<Key>): LoadResult<Value>

    /**
     * 새로고침 시 시작할 키 반환
     */
    abstract fun getRefreshKey(state: PagingState<Key, Value>): Key?

    data class LoadParams<Key>(
        val key: Key?,
        val loadSize: Int
    )
}

/**
 * 현재 페이징 상태
 */
data class PagingState<Key, Value>(
    val pages: List<LoadResult.Page<Value>>,
    val anchorPosition: Int?  // 현재 보고 있는 위치
) {
    val isEmpty: Boolean get() = pages.all { it.data.isEmpty() }
}

// ============================================================
// 3. Cursor 기반 PagingSource 구현 (네트워크)
// ============================================================

/**
 * 서버 API 시뮬레이션
 */
class FeedApiService {
    private val allPosts = (1..500).map { i ->
        FeedPost(
            id = i.toLong(),
            authorId = "user_${i % 50}",
            authorName = "사용자 ${i % 50}",
            content = "게시물 #$i - " + listOf(
                "오늘 날씨가 좋네요!",
                "맛있는 점심 먹었습니다",
                "새로운 프로젝트 시작!",
                "주말에 여행 갈 예정",
                "코틀린 공부 중..."
            ).random(),
            createdAt = LocalDateTime.now().minusMinutes(i.toLong() * 30),
            likeCount = (0..500).random(),
            commentCount = (0..50).random()
        )
    }.sortedByDescending { it.createdAt }

    /**
     * Cursor 기반 피드 조회
     * SQL 예시: SELECT * FROM posts WHERE created_at < :cursor ORDER BY created_at DESC LIMIT :limit
     */
    suspend fun getFeed(
        afterTimestamp: LocalDateTime? = null,
        beforeTimestamp: LocalDateTime? = null,
        limit: Int = 20
    ): FeedResponse {
        delay(500) // 네트워크 지연 시뮬레이션

        val filtered = when {
            afterTimestamp != null -> allPosts.filter { it.createdAt.isBefore(afterTimestamp) }
            beforeTimestamp != null -> allPosts.filter { it.createdAt.isAfter(beforeTimestamp) }.reversed()
            else -> allPosts
        }

        val data = filtered.take(limit)
        val hasMore = filtered.size > limit

        println("    [API] Cursor=${afterTimestamp ?: "initial"}, 반환=${data.size}개, hasMore=$hasMore")

        return FeedResponse(
            posts = data,
            hasNextPage = hasMore,
            hasPrevPage = afterTimestamp != null
        )
    }
}

data class FeedPost(
    val id: Long,
    val authorId: String,
    val authorName: String,
    val content: String,
    val createdAt: LocalDateTime,
    val likeCount: Int,
    val commentCount: Int
)

data class FeedResponse(
    val posts: List<FeedPost>,
    val hasNextPage: Boolean,
    val hasPrevPage: Boolean
)

/**
 * 피드 PagingSource - Cursor 기반
 */
class FeedPagingSource(
    private val apiService: FeedApiService
) : PagingSource<PageKey, FeedPost>() {

    override suspend fun load(params: LoadParams<PageKey>): LoadResult<FeedPost> {
        return try {
            val response = when (val key = params.key) {
                null, PageKey.Initial -> {
                    apiService.getFeed(limit = params.loadSize)
                }
                is PageKey.After -> {
                    apiService.getFeed(
                        afterTimestamp = key.lastTimestamp,
                        limit = params.loadSize
                    )
                }
                is PageKey.Before -> {
                    apiService.getFeed(
                        beforeTimestamp = key.firstTimestamp,
                        limit = params.loadSize
                    )
                }
            }

            val nextKey = if (response.hasNextPage && response.posts.isNotEmpty()) {
                val last = response.posts.last()
                PageKey.After(last.id, last.createdAt)
            } else null

            val prevKey = if (response.hasPrevPage && response.posts.isNotEmpty()) {
                val first = response.posts.first()
                PageKey.Before(first.id, first.createdAt)
            } else null

            LoadResult.Page(
                data = response.posts,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<PageKey, FeedPost>): PageKey? {
        // 새로고침 시 현재 보고 있는 위치 근처에서 시작
        return state.anchorPosition?.let { anchor ->
            val page = state.pages.flatMap { it.data }.getOrNull(anchor)
            page?.let { PageKey.After(it.id, it.createdAt) }
        }
    }
}

// ============================================================
// 4. Pager - 페이지 요청 관리
// ============================================================

/**
 * Pager 설정
 */
data class PagingConfig(
    val pageSize: Int = 20,
    val initialLoadSize: Int = 40,        // 첫 로드는 더 많이
    val prefetchDistance: Int = 5,        // 미리 로드 시작 거리
    val maxSize: Int = Int.MAX_VALUE,     // 메모리에 유지할 최대 항목 수
    val enablePlaceholders: Boolean = false
)

/**
 * 페이징 데이터 - 페이지들의 스냅샷
 */
data class PagingData<T>(
    val items: List<T>,
    val loadStates: CombinedLoadStates,
    val itemCount: Int
) {
    companion object {
        fun <T> empty() = PagingData<T>(
            items = emptyList(),
            loadStates = CombinedLoadStates(),
            itemCount = 0
        )
    }
}

/**
 * Pager - PagingSource로부터 페이지를 요청하고 PagingData 스트림 생성
 */
class Pager<Key, Value>(
    private val config: PagingConfig,
    private val pagingSourceFactory: () -> PagingSource<Key, Value>
) {
    private var currentSource: PagingSource<Key, Value>? = null
    private val pages = mutableListOf<LoadResult.Page<Value>>()
    private var nextKey: Key? = null
    private var prevKey: Key? = null

    private val _loadStates = MutableStateFlow(CombinedLoadStates())
    private val _pagingData = MutableStateFlow(PagingData.empty<Value>())

    val flow: Flow<PagingData<Value>> = _pagingData.asStateFlow()

    /**
     * 초기 로드
     */
    suspend fun refresh() {
        currentSource = pagingSourceFactory()
        pages.clear()

        _loadStates.value = _loadStates.value.copy(refresh = LoadState.Loading)
        emitCurrentState()

        val params = PagingSource.LoadParams<Key>(
            key = null,
            loadSize = config.initialLoadSize
        )

        when (val result = currentSource!!.load(params)) {
            is LoadResult.Page -> {
                pages.add(result)
                nextKey = result.nextKey as Key?
                prevKey = result.prevKey as Key?
                _loadStates.value = _loadStates.value.copy(refresh = LoadState.NotLoading)
            }
            is LoadResult.Error -> {
                _loadStates.value = _loadStates.value.copy(
                    refresh = LoadState.Error(result.exception)
                )
            }
        }
        emitCurrentState()
    }

    /**
     * 다음 페이지 로드 (아래로 스크롤)
     */
    suspend fun loadMore() {
        val key = nextKey ?: return
        if (_loadStates.value.append.isLoading) return

        _loadStates.value = _loadStates.value.copy(append = LoadState.Loading)
        emitCurrentState()

        val params = PagingSource.LoadParams(
            key = key,
            loadSize = config.pageSize
        )

        when (val result = currentSource?.load(params)) {
            is LoadResult.Page -> {
                pages.add(result)
                nextKey = result.nextKey as Key?
                _loadStates.value = _loadStates.value.copy(append = LoadState.NotLoading)
            }
            is LoadResult.Error -> {
                _loadStates.value = _loadStates.value.copy(
                    append = LoadState.Error(result.exception)
                )
            }
            null -> {}
        }
        emitCurrentState()
    }

    /**
     * 재시도
     */
    suspend fun retry() {
        val loadStates = _loadStates.value
        when {
            loadStates.refresh.isError -> refresh()
            loadStates.append.isError -> loadMore()
        }
    }

    private fun emitCurrentState() {
        val allItems = pages.flatMap { it.data }
        _pagingData.value = PagingData(
            items = allItems,
            loadStates = _loadStates.value,
            itemCount = allItems.size
        )
    }

    /**
     * 메모리에서 오래된 페이지 제거 (메모리 관리)
     */
    fun trimPages(maxPages: Int) {
        if (pages.size > maxPages) {
            val toRemove = pages.size - maxPages
            repeat(toRemove) { pages.removeAt(0) }
            emitCurrentState()
        }
    }
}

// ============================================================
// 5. RemoteMediator - 네트워크 + 로컬 캐시 동기화
// ============================================================

/**
 * 로컬 캐시 (Room DB 시뮬레이션)
 */
class FeedLocalCache {
    private val cache = ConcurrentHashMap<Long, FeedPost>()
    private var lastRefreshTime: LocalDateTime? = null

    fun insert(posts: List<FeedPost>) {
        posts.forEach { cache[it.id] = it }
    }

    fun getAll(): List<FeedPost> {
        return cache.values.sortedByDescending { it.createdAt }
    }

    fun clear() {
        cache.clear()
    }

    fun getLastRefreshTime(): LocalDateTime? = lastRefreshTime

    fun setLastRefreshTime(time: LocalDateTime) {
        lastRefreshTime = time
    }

    fun isEmpty(): Boolean = cache.isEmpty()
}

/**
 * RemoteMediator 로드 타입
 */
enum class LoadType {
    REFRESH,  // 전체 새로고침
    PREPEND,  // 앞쪽 로드
    APPEND    // 뒤쪽 로드
}

/**
 * RemoteMediator 결과
 */
sealed class MediatorResult {
    data class Success(val endOfPaginationReached: Boolean) : MediatorResult()
    data class Error(val exception: Throwable) : MediatorResult()
}

/**
 * RemoteMediator - 네트워크 데이터를 로컬 캐시로 동기화
 *
 * 동작 흐름:
 * 1. 로컬 캐시에서 데이터 표시 (빠른 응답)
 * 2. 백그라운드에서 서버 데이터 fetch
 * 3. 서버 데이터를 로컬 캐시에 저장
 * 4. 캐시 변경이 UI에 자동 반영
 */
class FeedRemoteMediator(
    private val apiService: FeedApiService,
    private val localCache: FeedLocalCache
) {
    suspend fun load(loadType: LoadType): MediatorResult {
        return try {
            println("    [Mediator] $loadType 시작")

            // REFRESH일 때만 캐시 클리어 (APPEND는 추가)
            if (loadType == LoadType.REFRESH) {
                val response = apiService.getFeed(limit = 40)
                localCache.clear()
                localCache.insert(response.posts)
                localCache.setLastRefreshTime(LocalDateTime.now())
                println("    [Mediator] 캐시 갱신 완료: ${response.posts.size}개")
                MediatorResult.Success(endOfPaginationReached = !response.hasNextPage)
            } else {
                // APPEND - 마지막 항목 기준으로 더 로드
                val lastPost = localCache.getAll().lastOrNull()
                val response = if (lastPost != null) {
                    apiService.getFeed(afterTimestamp = lastPost.createdAt, limit = 20)
                } else {
                    apiService.getFeed(limit = 20)
                }
                localCache.insert(response.posts)
                println("    [Mediator] 추가 로드: ${response.posts.size}개")
                MediatorResult.Success(endOfPaginationReached = !response.hasNextPage)
            }
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    /**
     * 캐시가 오래되었는지 확인 (예: 5분)
     */
    fun isCacheStale(): Boolean {
        val lastRefresh = localCache.getLastRefreshTime() ?: return true
        return lastRefresh.plusMinutes(5).isBefore(LocalDateTime.now())
    }
}

// ============================================================
// 6. UI 레이어 - ViewModel 통합
// ============================================================

/**
 * 피드 UI 상태
 */
data class FeedUiState(
    val posts: List<FeedPost> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMorePages: Boolean = true,
    val isEmpty: Boolean = false
)

/**
 * 피드 ViewModel
 */
class FeedViewModel(
    private val apiService: FeedApiService = FeedApiService()
) {
    private val pager = Pager(
        config = PagingConfig(
            pageSize = 20,
            initialLoadSize = 40,
            prefetchDistance = 5
        ),
        pagingSourceFactory = { FeedPagingSource(apiService) }
    )

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        // PagingData를 UI State로 변환
        CoroutineScope(Dispatchers.Default).launch {
            pager.flow.collect { pagingData ->
                _uiState.value = FeedUiState(
                    posts = pagingData.items,
                    isRefreshing = pagingData.loadStates.refresh.isLoading,
                    isLoadingMore = pagingData.loadStates.append.isLoading,
                    error = (pagingData.loadStates.refresh as? LoadState.Error)?.error?.message
                        ?: (pagingData.loadStates.append as? LoadState.Error)?.error?.message,
                    hasMorePages = pagingData.loadStates.append !is LoadState.Error,
                    isEmpty = pagingData.items.isEmpty() && !pagingData.loadStates.refresh.isLoading
                )
            }
        }
    }

    suspend fun refresh() {
        pager.refresh()
    }

    suspend fun loadMore() {
        pager.loadMore()
    }

    suspend fun retry() {
        pager.retry()
    }

    /**
     * 스크롤 위치에 따라 자동 로드 트리거
     * @param lastVisibleItemIndex 현재 보이는 마지막 아이템 인덱스
     */
    suspend fun onScrolled(lastVisibleItemIndex: Int) {
        val state = _uiState.value
        val prefetchDistance = 5

        // 끝에서 prefetchDistance 이내에 도달하면 더 로드
        if (!state.isLoadingMore &&
            state.hasMorePages &&
            lastVisibleItemIndex >= state.posts.size - prefetchDistance
        ) {
            loadMore()
        }
    }
}

// ============================================================
// 7. 리스트 아이템 타입 (헤더/푸터/에러 포함)
// ============================================================

/**
 * 리스트에 표시할 아이템 타입
 * RecyclerView.Adapter나 LazyColumn에서 사용
 */
sealed class FeedListItem {
    /** 실제 게시물 */
    data class PostItem(val post: FeedPost) : FeedListItem()

    /** 로딩 인디케이터 */
    object LoadingItem : FeedListItem()

    /** 에러 (재시도 버튼 포함) */
    data class ErrorItem(val message: String, val onRetry: () -> Unit) : FeedListItem()

    /** 빈 상태 */
    object EmptyItem : FeedListItem()

    /** 헤더 */
    data class HeaderItem(val title: String) : FeedListItem()

    /** 페이지 끝 표시 */
    object EndOfListItem : FeedListItem()
}

/**
 * UI State를 리스트 아이템으로 변환
 */
fun FeedUiState.toListItems(onRetry: () -> Unit): List<FeedListItem> {
    return buildList {
        // 헤더
        add(FeedListItem.HeaderItem("최신 피드"))

        // 게시물
        addAll(posts.map { FeedListItem.PostItem(it) })

        // 로딩/에러/빈 상태
        when {
            isLoadingMore -> add(FeedListItem.LoadingItem)
            error != null -> add(FeedListItem.ErrorItem(error, onRetry))
            isEmpty -> add(FeedListItem.EmptyItem)
            !hasMorePages -> add(FeedListItem.EndOfListItem)
        }
    }
}

// ============================================================
// 데모
// ============================================================

fun main() = runBlocking {
    println("=== Pagination Pattern - SNS 피드 앱 ===\n")

    val apiService = FeedApiService()

    // --- 시나리오 1: Cursor 기반 PagingSource ---
    println("--- 1. Cursor 기반 페이지네이션 ---")
    val pagingSource = FeedPagingSource(apiService)

    // 첫 페이지 로드
    println("  첫 페이지 로드:")
    val firstPage = pagingSource.load(
        PagingSource.LoadParams(key = null, loadSize = 5)
    )

    if (firstPage is LoadResult.Page) {
        println("  결과: ${firstPage.data.size}개")
        firstPage.data.forEach { post ->
            println("    - [${post.id}] ${post.content.take(30)}...")
        }
        println("  nextKey: ${firstPage.nextKey}")

        // 두 번째 페이지 로드
        println("\n  두 번째 페이지 로드:")
        val secondPage = pagingSource.load(
            PagingSource.LoadParams(key = firstPage.nextKey, loadSize = 5)
        )
        if (secondPage is LoadResult.Page) {
            println("  결과: ${secondPage.data.size}개")
            secondPage.data.forEach { post ->
                println("    - [${post.id}] ${post.content.take(30)}...")
            }
        }
    }

    // --- 시나리오 2: Pager 사용 ---
    println("\n--- 2. Pager를 통한 페이지 관리 ---")
    val pager = Pager(
        config = PagingConfig(pageSize = 10, initialLoadSize = 20),
        pagingSourceFactory = { FeedPagingSource(apiService) }
    )

    // 초기 로드
    println("  Refresh 호출:")
    pager.refresh()

    // Flow에서 상태 수집
    val pagingData = pager.flow.first()
    println("  로드된 항목: ${pagingData.itemCount}개")
    println("  LoadStates: refresh=${pagingData.loadStates.refresh}, append=${pagingData.loadStates.append}")

    // 더 로드
    println("\n  LoadMore 호출:")
    pager.loadMore()
    val moreData = pager.flow.first()
    println("  총 항목: ${moreData.itemCount}개")

    // --- 시나리오 3: RemoteMediator (캐시 동기화) ---
    println("\n--- 3. RemoteMediator - 네트워크 + 캐시 ---")
    val localCache = FeedLocalCache()
    val mediator = FeedRemoteMediator(apiService, localCache)

    // 캐시가 비어있으므로 REFRESH
    println("  캐시 상태: 비어있음=${localCache.isEmpty()}, 오래됨=${mediator.isCacheStale()}")
    println("  REFRESH 실행:")
    mediator.load(LoadType.REFRESH)
    println("  캐시 후: ${localCache.getAll().size}개")

    // APPEND로 더 로드
    println("\n  APPEND 실행:")
    mediator.load(LoadType.APPEND)
    println("  캐시 후: ${localCache.getAll().size}개")

    // --- 시나리오 4: ViewModel + UI State ---
    println("\n--- 4. ViewModel + UI State ---")
    val viewModel = FeedViewModel()

    // UI State 구독 (시뮬레이션)
    launch {
        viewModel.uiState.take(3).collect { state ->
            println("  UI State: posts=${state.posts.size}, refreshing=${state.isRefreshing}, loading=${state.isLoadingMore}")
        }
    }

    delay(100)
    viewModel.refresh()
    delay(1000)
    viewModel.loadMore()
    delay(1000)

    // --- 시나리오 5: 리스트 아이템 변환 ---
    println("\n--- 5. 리스트 아이템 변환 ---")
    val currentState = viewModel.uiState.value
    val listItems = currentState.toListItems(onRetry = { println("재시도!") })

    println("  리스트 아이템 구성:")
    listItems.take(5).forEach { item ->
        when (item) {
            is FeedListItem.HeaderItem -> println("    [Header] ${item.title}")
            is FeedListItem.PostItem -> println("    [Post] ${item.post.content.take(25)}...")
            is FeedListItem.LoadingItem -> println("    [Loading]")
            is FeedListItem.ErrorItem -> println("    [Error] ${item.message}")
            is FeedListItem.EmptyItem -> println("    [Empty]")
            is FeedListItem.EndOfListItem -> println("    [End]")
        }
    }
    println("    ... 총 ${listItems.size}개 아이템")

    // --- 시나리오 6: 스크롤 기반 자동 로드 ---
    println("\n--- 6. 스크롤 기반 자동 로드 ---")
    val state = viewModel.uiState.value
    println("  현재 ${state.posts.size}개 로드됨")
    println("  마지막 아이템 근처 스크롤 시뮬레이션...")
    viewModel.onScrolled(state.posts.size - 3)  // 끝에서 3번째
    delay(1000)
    println("  자동 로드 후: ${viewModel.uiState.value.posts.size}개")

    println("\n=== Pagination Pattern 핵심 원칙 ===")
    println("1. Cursor 기반: ID/timestamp 기준으로 페이지 구분, 데이터 변경에 강건")
    println("2. PagingSource: 데이터 소스 추상화, 테스트 용이")
    println("3. LoadState: 로딩/에러/성공 상태 체계적 관리")
    println("4. Prefetch: 스크롤 끝 도달 전에 미리 로드")
    println("5. RemoteMediator: 네트워크 + 로컬 캐시 자동 동기화")
    println("6. 단방향 데이터 흐름: Pager → PagingData Flow → UI")
}
