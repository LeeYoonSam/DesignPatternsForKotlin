# Pagination Pattern

## 개요

Pagination(페이지네이션)은 대용량 데이터를 **페이지 단위**로 나누어 요청/로드하여 메모리, 네트워크, 응답 시간을 최적화하는 패턴입니다. **Cursor 기반** 페이지네이션과 **PagingSource/Pager** 추상화를 통해 무한 스크롤을 체계적으로 관리합니다.

## Offset vs Cursor 비교

| 특성 | Offset 기반 | Cursor 기반 |
|------|-------------|-------------|
| **쿼리 예시** | `LIMIT 20 OFFSET 100` | `WHERE created_at < :cursor LIMIT 20` |
| **성능** | O(n) - OFFSET만큼 스캔 | O(1) - 인덱스로 바로 접근 |
| **데이터 변경 시** | 페이지 밀림/중복 발생 | 일관된 결과 보장 |
| **구현 복잡도** | 단순 (page 번호) | 중간 (cursor 값 관리) |
| **임의 페이지 접근** | 가능 (page=5) | 불가 (순차 접근만) |
| **적합한 경우** | 소규모, 정적 데이터 | 대규모, 실시간 피드 |

### Offset의 문제 (페이지 밀림)

```
1페이지 조회: [A, B, C, D, E]  (OFFSET 0)
         ↓
    새 게시물 X 추가 (맨 앞에)
         ↓
2페이지 조회: [E, F, G, H, I]  (OFFSET 5)
         ↑
      E가 중복 표시!
```

### Cursor의 해결

```
1페이지 조회: [A, B, C, D, E]  cursor=E의 timestamp
         ↓
    새 게시물 X 추가 (맨 앞에)
         ↓
2페이지 조회: [F, G, H, I, J]  WHERE created_at < E.timestamp
         ↑
      중복 없음!
```

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer                                 │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │   LazyColumn / RecyclerView                             │   │
│   │   ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────────────────┐  │   │
│   │   │Post1│ │Post2│ │Post3│ │ ... │ │ LoadingItem     │  │   │
│   │   └─────┘ └─────┘ └─────┘ └─────┘ └─────────────────┘  │   │
│   └────────────────────────────┬────────────────────────────┘   │
│                                │ onScrolled(lastVisible)         │
│                                ▼                                 │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                    ViewModel                             │   │
│   │   uiState: StateFlow<FeedUiState>                       │   │
│   │   - posts: List<Post>                                   │   │
│   │   - isRefreshing / isLoadingMore                        │   │
│   │   - error / hasMorePages / isEmpty                      │   │
│   └────────────────────────────┬────────────────────────────┘   │
└────────────────────────────────┼────────────────────────────────┘
                                 │
┌────────────────────────────────┼────────────────────────────────┐
│                         Data Layer                               │
│                                ▼                                 │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │                      Pager                               │   │
│   │   config: PagingConfig (pageSize, prefetchDistance)     │   │
│   │   flow: Flow<PagingData<T>>                             │   │
│   │   - refresh() / loadMore() / retry()                    │   │
│   └────────────────────────────┬────────────────────────────┘   │
│                                │                                 │
│          ┌─────────────────────┴─────────────────────┐          │
│          ▼                                           ▼          │
│   ┌──────────────────┐                    ┌──────────────────┐  │
│   │   PagingSource   │                    │  RemoteMediator  │  │
│   │  (Network Only)  │                    │ (Network+Cache)  │  │
│   │                  │                    │                  │  │
│   │  load(params)    │                    │  load(loadType)  │  │
│   │  → LoadResult    │                    │  → MediatorResult│  │
│   └────────┬─────────┘                    └────────┬─────────┘  │
│            │                                       │            │
│            ▼                                       ▼            │
│   ┌──────────────────┐                    ┌──────────────────┐  │
│   │   Remote API     │                    │   Local Cache    │  │
│   │   (Server)       │                    │   (Room DB)      │  │
│   └──────────────────┘                    └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## 핵심 구성 요소

### 1. PagingSource - 데이터 소스 추상화

```kotlin
abstract class PagingSource<Key, Value> {
    abstract suspend fun load(params: LoadParams<Key>): LoadResult<Value>
    abstract fun getRefreshKey(state: PagingState<Key, Value>): Key?
}

sealed class LoadResult<out T> {
    data class Page<T>(
        val data: List<T>,
        val prevKey: Key?,
        val nextKey: Key?
    ) : LoadResult<T>()

    data class Error(val exception: Throwable) : LoadResult<Nothing>()
}
```

### 2. Cursor 기반 PagingSource 구현

```kotlin
class FeedPagingSource(
    private val api: FeedApiService
) : PagingSource<PageKey, FeedPost>() {

    override suspend fun load(params: LoadParams<PageKey>): LoadResult<FeedPost> {
        val response = when (val key = params.key) {
            null -> api.getFeed(limit = params.loadSize)
            is PageKey.After -> api.getFeed(
                afterTimestamp = key.lastTimestamp,
                limit = params.loadSize
            )
        }

        val nextKey = if (response.hasNextPage) {
            val last = response.posts.last()
            PageKey.After(last.id, last.createdAt)
        } else null

        return LoadResult.Page(
            data = response.posts,
            prevKey = null,
            nextKey = nextKey
        )
    }
}
```

### 3. LoadState - 로딩 상태 관리

```kotlin
sealed class LoadState {
    object NotLoading : LoadState()
    object Loading : LoadState()
    data class Error(val error: Throwable) : LoadState()
}

data class CombinedLoadStates(
    val refresh: LoadState,  // 전체 새로고침
    val prepend: LoadState,  // 위로 스크롤 (이전 페이지)
    val append: LoadState    // 아래로 스크롤 (다음 페이지)
)
```

### 4. Pager - 페이지 요청 관리

```kotlin
val pager = Pager(
    config = PagingConfig(
        pageSize = 20,
        initialLoadSize = 40,
        prefetchDistance = 5
    ),
    pagingSourceFactory = { FeedPagingSource(api) }
)

// Flow로 PagingData 구독
pager.flow.collect { pagingData ->
    adapter.submitData(pagingData)
}
```

### 5. RemoteMediator - 네트워크 + 캐시

```kotlin
class FeedRemoteMediator(
    private val api: FeedApiService,
    private val cache: FeedLocalCache
) {
    suspend fun load(loadType: LoadType): MediatorResult {
        return when (loadType) {
            LoadType.REFRESH -> {
                val response = api.getFeed(limit = 40)
                cache.clear()
                cache.insert(response.posts)
                MediatorResult.Success(!response.hasNextPage)
            }
            LoadType.APPEND -> {
                val lastPost = cache.getAll().lastOrNull()
                val response = api.getFeed(afterTimestamp = lastPost?.createdAt)
                cache.insert(response.posts)
                MediatorResult.Success(!response.hasNextPage)
            }
        }
    }
}
```

## 무한 스크롤 데이터 흐름

```
스크롤 이벤트
    │
    ▼
onScrolled(lastVisibleIndex)
    │
    ├─ lastVisible >= totalCount - prefetchDistance?
    │       │
    │       ├─ Yes → loadMore() 호출
    │       │           │
    │       │           ▼
    │       │    PagingSource.load(nextKey)
    │       │           │
    │       │           ▼
    │       │    LoadResult.Page 반환
    │       │           │
    │       │           ▼
    │       │    PagingData Flow 업데이트
    │       │           │
    │       │           ▼
    │       │    UI 자동 갱신
    │       │
    │       └─ No → 대기
    │
    ▼
LoadStates 업데이트
    │
    ├─ Loading → 하단 로딩 인디케이터 표시
    ├─ Error → 재시도 버튼 표시
    └─ NotLoading → 로딩 완료
```

## 리스트 아이템 타입

```kotlin
sealed class FeedListItem {
    data class PostItem(val post: FeedPost) : FeedListItem()
    object LoadingItem : FeedListItem()
    data class ErrorItem(val message: String, val onRetry: () -> Unit) : FeedListItem()
    object EmptyItem : FeedListItem()
    object EndOfListItem : FeedListItem()
}

// UI State를 리스트 아이템으로 변환
fun FeedUiState.toListItems(): List<FeedListItem> = buildList {
    addAll(posts.map { FeedListItem.PostItem(it) })
    when {
        isLoadingMore -> add(FeedListItem.LoadingItem)
        error != null -> add(FeedListItem.ErrorItem(error, onRetry))
        !hasMorePages -> add(FeedListItem.EndOfListItem)
    }
}
```

## PagingConfig 설정

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `pageSize` | 20 | 한 페이지당 로드할 항목 수 |
| `initialLoadSize` | 40 | 첫 로드 시 항목 수 (보통 pageSize * 2) |
| `prefetchDistance` | 5 | 끝에서 N개 전에 미리 로드 시작 |
| `maxSize` | MAX_VALUE | 메모리에 유지할 최대 항목 수 |
| `enablePlaceholders` | false | 로드되지 않은 항목에 null 표시 |

## 장점

1. **성능 최적화**: 필요한 만큼만 로드하여 메모리/네트워크 절약
2. **빠른 응답**: 첫 페이지만 로드하여 초기 로딩 시간 단축
3. **일관성**: Cursor 기반으로 데이터 변경에도 중복/누락 방지
4. **상태 관리**: LoadState로 로딩/에러/완료 상태 체계적 관리
5. **캐싱**: RemoteMediator로 오프라인 지원 및 빠른 재방문
6. **추상화**: PagingSource로 데이터 소스 교체 용이
7. **테스트**: FakePagingSource로 UI 테스트 용이

## 단점

1. **구현 복잡도**: Pager, PagingSource, LoadState 등 개념 학습 필요
2. **임의 접근 불가**: Cursor 기반은 "5페이지로 바로 이동" 불가
3. **동기화 복잡성**: RemoteMediator 사용 시 캐시 무효화 전략 필요
4. **디버깅**: 데이터 흐름이 비동기로 분산되어 추적 어려움

## 적용 시점

- SNS 피드 (Instagram, Twitter, Facebook)
- 검색 결과 목록 (Google, Amazon)
- 채팅 메시지 목록
- 이메일 목록
- 뉴스 피드
- 상품 목록 (무한 스크롤 e-commerce)
- 댓글 목록

## 실제 사례

| 플랫폼 | 라이브러리 |
|--------|------------|
| **Android** | Jetpack Paging 3 |
| **iOS** | UICollectionView Prefetching, Combine |
| **Web** | React Query, SWR, Apollo Client |
| **Flutter** | infinite_scroll_pagination |
| **Backend** | GraphQL Connections (Relay Cursor) |

## API 설계 (Cursor 기반)

```kotlin
// 요청
GET /api/feed?after=1234567890&limit=20

// 응답
{
  "data": [...],
  "pageInfo": {
    "hasNextPage": true,
    "hasPreviousPage": false,
    "startCursor": "MTIzNDU2Nzg5MA==",
    "endCursor": "OTg3NjU0MzIxMA=="
  }
}
```

## 관련 패턴

- **Repository Pattern**: PagingSource가 Repository 역할
- **Observer Pattern**: Flow/LiveData로 페이지 데이터 구독
- **Strategy Pattern**: 다양한 PagingSource 구현 (네트워크/로컬/Mock)
- **Offline-First Pattern**: RemoteMediator로 캐시 우선 로드
- **State Pattern**: LoadState로 로딩 상태 관리

## 참고 자료

- [Android Paging 3 공식 문서](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)
- [Relay Cursor Connections Spec](https://relay.dev/graphql/connections.htm)
- [Cursor-based Pagination (Slack Engineering)](https://slack.engineering/evolving-api-pagination-at-slack/)
- [GraphQL Pagination Best Practices](https://graphql.org/learn/pagination/)
