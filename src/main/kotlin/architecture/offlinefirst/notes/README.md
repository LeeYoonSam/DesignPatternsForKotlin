# Offline-First Pattern

## 개요

Offline-First는 **로컬 저장소를 Single Source of Truth(SSOT)**로 사용하고, 네트워크가 가용할 때 서버와 **백그라운드 동기화**를 수행하는 패턴입니다. 오프라인에서도 읽기/쓰기 모두 가능하며, 충돌 발생 시 명시적인 해결 전략을 적용합니다.

## 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **Local First** | 로컬 DB가 SSOT, UI는 항상 로컬 데이터를 표시 |
| **즉각 반응** | 사용자 작업은 네트워크 무관하게 즉시 로컬에 반영 |
| **백그라운드 동기화** | 네트워크 가용 시 자동으로 서버와 동기화 |
| **충돌 해결** | 동시 수정 감지 후 명시적 전략으로 해결 |
| **Delta Sync** | 마지막 동기화 이후 변경분만 전송 |
| **Soft Delete** | Tombstone으로 삭제 추적, 동기화 후 정리 |

## 아키텍처 다이어그램

```
┌──────────────────────────────────────────────────────────────┐
│                          UI Layer                             │
│   메모 목록 / 편집 / 동기화 상태 표시                          │
└──────────────────────┬───────────────────────────────────────┘
                       │ (항상 로컬에서 읽기/쓰기)
┌──────────────────────┴───────────────────────────────────────┐
│               OfflineFirstRepository                          │
│  createNote() → 로컬 저장 → (온라인이면) 즉시 동기화          │
│  updateNote() → 로컬 저장 → (온라인이면) 즉시 동기화          │
│  deleteNote() → Soft Delete → (온라인이면) 즉시 동기화        │
│  getNotes()   → 항상 로컬에서 즉시 반환                       │
└───────────┬──────────────────────────────┬───────────────────┘
            │                              │
┌───────────┴───────────┐    ┌─────────────┴──────────────────┐
│     LocalStore        │    │        SyncEngine               │
│  (Single Source of    │    │  Push: 로컬 → 서버              │
│   Truth)              │    │  Pull: 서버 → 로컬              │
│                       │    │  Conflict Resolution            │
│  - SyncableNote       │◄──►│  - LastWriteWins               │
│  - ChangeLog          │    │  - FieldLevelMerge             │
│  - SyncStatus         │    │  - KeepBoth                    │
│                       │    │  - ManualResolution             │
└───────────────────────┘    └─────────────┬──────────────────┘
                                           │
                             ┌─────────────┴──────────────────┐
                             │        RemoteStore              │
                             │  (Server API)                   │
                             │  - upsert(note)                 │
                             │  - delete(id)                   │
                             │  - getChangesSince(timestamp)   │
                             └─────────────────────────────────┘
                                           ▲
                             ┌─────────────┴──────────────────┐
                             │    ConnectivityMonitor           │
                             │  네트워크 복귀 → 자동 동기화     │
                             └─────────────────────────────────┘
```

## 데이터 흐름

### 쓰기 흐름 (Create/Update/Delete)

```
사용자 액션
    │
    ▼
로컬 DB에 즉시 저장 (SyncStatus: PENDING_*)
    │
    ├─ 온라인? ──Yes──► 서버에 즉시 동기화 시도
    │                      ├─ 성공 → SyncStatus: SYNCED
    │                      └─ 실패 → 나중에 재시도
    │
    └─ 오프라인? ──► ChangeLog에 기록 → 나중에 동기화
    │
    ▼
UI에 즉시 반영 (낙관적 업데이트)
```

### 읽기 흐름

```
UI 데이터 요청
    │
    ▼
로컬 DB에서 즉시 반환 (0ms 지연)
    │
    └─ 백그라운드에서 서버 데이터와 동기화 (선택적)
```

### 동기화 흐름

```
동기화 트리거 (네트워크 복귀 / 수동 / 주기적)
    │
    ▼
Phase 1: Push (로컬 → 서버)
    │ - PENDING_CREATE → upsert
    │ - PENDING_UPDATE → upsert (버전 충돌 감지)
    │ - PENDING_DELETE → delete
    │
    ▼
Phase 2: Pull (서버 → 로컬)
    │ - getChangesSince(lastSyncTime)
    │ - 새 메모 → 로컬에 추가
    │ - 수정된 메모 → 로컬 갱신 (충돌 없는 경우)
    │
    ▼
Phase 3: 정리
    │ - Tombstone 정리 (동기화 완료된 Soft Delete)
    │ - ChangeLog 정리
    │
    ▼
lastSyncTime 갱신
```

## 충돌 해결 전략

### 전략 비교

| 전략 | 데이터 안전성 | UX 비용 | 복잡도 | 적합한 경우 |
|------|:---:|:---:|:---:|------|
| **Last Write Wins** | 낮음 | 없음 | 낮음 | 단일 사용자, 빈번한 변경 |
| **Field-Level Merge** | 중간 | 없음 | 중간 | 서로 다른 필드 수정이 많은 경우 |
| **Keep Both** | 높음 | 낮음 | 낮음 | 데이터 손실이 치명적인 경우 |
| **Manual Resolution** | 최고 | 높음 | 중간 | 정확성이 중요한 비즈니스 데이터 |

### Last Write Wins (LWW)

```kotlin
class LastWriteWinsResolver : ConflictResolver {
    override fun resolve(local: SyncableNote, server: SyncableNote): ConflictResolution {
        return if (local.updatedAt.isAfter(server.updatedAt))
            ConflictResolution.KeepLocal(local)
        else
            ConflictResolution.KeepServer(server)
    }
}
```

### Field-Level Merge

```kotlin
class FieldLevelMergeResolver : ConflictResolver {
    override fun resolve(local: SyncableNote, server: SyncableNote): ConflictResolution {
        val mergedTitle = if (local.title != server.title) {
            if (local.updatedAt.isAfter(server.updatedAt)) local.title else server.title
        } else local.title

        val mergedContent = if (local.content != server.content) {
            if (local.updatedAt.isAfter(server.updatedAt)) local.content else server.content
        } else local.content

        return ConflictResolution.Merge(local.copy(title = mergedTitle, content = mergedContent))
    }
}
```

## 동기화 상태 모델

```kotlin
enum class SyncStatus {
    SYNCED,          // 서버와 동기화 완료
    PENDING_CREATE,  // 로컬에서 생성됨
    PENDING_UPDATE,  // 로컬에서 수정됨
    PENDING_DELETE,  // 로컬에서 삭제됨 (Tombstone)
    CONFLICT         // 충돌 발생 (수동 해결 필요)
}

data class SyncableNote(
    val id: String,
    val title: String,
    val content: String,
    val syncStatus: SyncStatus,
    val version: Long,             // 낙관적 잠금
    val lastSyncedAt: LocalDateTime?,
    val isDeleted: Boolean         // Soft Delete
)
```

## UI 동기화 상태 표시

```kotlin
data class SyncStatusUi(
    val isOnline: Boolean,
    val pendingChanges: Int,
    val conflicts: Int,
    val statusMessage: String
    // "오프라인 - 3개 변경 대기 중"
    // "동기화 중... (2개)"
    // "모든 변경 동기화 완료"
    // "충돌 1개 해결 필요"
)
```

## Soft Delete (Tombstone)

```
삭제 요청
    │
    ▼
isDeleted = true, syncStatus = PENDING_DELETE
    │ (UI에서는 즉시 안 보임)
    │
    ├─ 온라인 → 서버에 삭제 반영 → syncStatus = SYNCED
    │
    ▼
purgeDeleted() → 동기화 완료된 Tombstone 실제 삭제

Why Soft Delete?
• 오프라인 삭제 → 온라인 복귀 시 서버에 삭제 사실 전달 필요
• Hard Delete하면 서버에 삭제 요청을 보낼 엔티티 정보가 사라짐
• 다른 디바이스에서도 삭제를 인식할 수 있음
```

## 장점

1. **항상 동작**: 네트워크 무관하게 읽기/쓰기 가능
2. **즉각 반응**: 로컬 우선이므로 네트워크 대기 없음
3. **데이터 안전**: 변경 사항이 로컬에 보존, 네트워크 실패로 데이터 손실 없음
4. **UX 향상**: 로딩/타임아웃 없이 즉시 결과 표시
5. **배터리/데이터 절약**: 필요할 때만 네트워크 사용 (Delta Sync)
6. **신뢰성**: 부분 동기화 실패 시에도 다음 동기화에서 재시도

## 단점

1. **충돌 복잡성**: 동시 수정 충돌 감지/해결 로직 필요
2. **저장 공간**: 로컬에 전체 데이터 + ChangeLog 저장
3. **일관성 지연**: 서버와 로컬 데이터 간 일시적 불일치 (Eventual Consistency)
4. **초기 동기화 비용**: 첫 설치 시 서버 데이터 전체 다운로드
5. **구현 복잡도**: SyncEngine, ConflictResolver, Tombstone 관리 등

## 적용 시점

- 메모/노트 앱 (Notion, Apple Notes, Google Keep)
- 할일 관리 (Todoist, Things)
- 메시징 앱 (메시지 큐잉)
- 지도/내비게이션 (오프라인 맵)
- 문서 편집 (Google Docs 오프라인 모드)
- 의료/현장 앱 (네트워크 불안정 환경)
- IoT 디바이스 데이터 수집

## 실제 사례

| 앱/기술 | 동기화 전략 |
|---------|-------------|
| **Notion** | Block 단위 CRDT + Operational Transform |
| **Google Docs** | Operational Transform (OT) |
| **Apple Notes** | CloudKit + 자동 충돌 해결 |
| **Todoist** | Last Write Wins + 큐 기반 동기화 |
| **CouchDB/PouchDB** | Revision Tree + MVCC |
| **Realm Sync** | Object-level 충돌 해결 |
| **Firebase Firestore** | 오프라인 캐시 + 실시간 동기화 |

## 관련 패턴

- **Repository Pattern**: Offline-First Repository가 로컬/원격 접근을 추상화
- **CQRS Pattern**: 읽기(로컬)와 쓰기(로컬→동기화) 경로 분리
- **Event Sourcing**: ChangeLog가 이벤트 소싱과 유사
- **Outbox Pattern**: 로컬 저장 + 동기화 큐가 Outbox와 동일 개념
- **Observer Pattern**: 네트워크 상태 변경 감지
- **Strategy Pattern**: 충돌 해결 전략 교체

## 참고 자료

- [Offline First - offlinefirst.org](https://offlinefirst.org/)
- [CRDTs - Conflict-free Replicated Data Types](https://crdt.tech/)
- [Local-first software (Ink & Switch)](https://www.inkandswitch.com/local-first/)
- [Android Room + WorkManager Offline Guide](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [CouchDB Replication Protocol](https://docs.couchdb.org/en/stable/replication/protocol.html)
