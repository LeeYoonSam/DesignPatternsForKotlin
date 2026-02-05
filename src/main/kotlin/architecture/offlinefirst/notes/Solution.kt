package architecture.offlinefirst.notes

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Offline-First Pattern - 해결책
 *
 * 메모 앱을 Offline-First로 설계하여:
 * - 로컬 DB를 Single Source of Truth로 사용
 * - 오프라인에서도 읽기/쓰기 모두 가능
 * - 온라인 복귀 시 자동 동기화
 * - 충돌 발생 시 해결 전략 적용
 *
 * 핵심 구성:
 * - LocalStore: 로컬 DB (Single Source of Truth)
 * - RemoteStore: 서버 API
 * - SyncEngine: 로컬 ↔ 서버 동기화 엔진
 * - ConflictResolver: 충돌 해결 전략
 * - ChangeTracker: 오프라인 변경 사항 추적
 * - ConnectivityMonitor: 네트워크 상태 감시
 */

// ============================================================
// 1. 동기화 가능한 모델
// ============================================================

/**
 * 동기화 상태
 */
enum class SyncStatus {
    SYNCED,         // 서버와 동기화 완료
    PENDING_CREATE, // 로컬에서 생성됨 (서버에 아직 없음)
    PENDING_UPDATE, // 로컬에서 수정됨 (서버 동기화 필요)
    PENDING_DELETE, // 로컬에서 삭제됨 (서버 반영 필요)
    CONFLICT        // 충돌 발생 (수동 해결 필요)
}

/**
 * 동기화 가능한 메모 엔티티
 */
data class SyncableNote(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    val version: Long = 1,                    // 낙관적 잠금용 버전
    val lastSyncedAt: LocalDateTime? = null,  // 마지막 동기화 시각
    val isDeleted: Boolean = false             // Soft Delete (Tombstone)
)

/**
 * 변경 로그 - 오프라인 변경 사항을 추적
 */
data class ChangeLog(
    val id: String = UUID.randomUUID().toString(),
    val entityId: String,
    val operation: Operation,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val payload: Map<String, String> = emptyMap(),  // 변경된 필드 값
    val synced: Boolean = false
) {
    enum class Operation { CREATE, UPDATE, DELETE }
}

// ============================================================
// 2. 로컬 저장소 (Single Source of Truth)
// ============================================================

/**
 * 로컬 DB 시뮬레이션
 * 실제로는 Room, SQLDelight 등 사용
 */
class LocalStore {
    private val notes = mutableMapOf<String, SyncableNote>()
    private val changeLogs = mutableListOf<ChangeLog>()

    /** 메모 저장 (로컬) */
    fun save(note: SyncableNote): SyncableNote {
        notes[note.id] = note
        return note
    }

    /** 메모 조회 */
    fun getById(id: String): SyncableNote? = notes[id]

    /** 활성 메모 전체 조회 (Soft Delete 제외) */
    fun getAll(): List<SyncableNote> =
        notes.values.filter { !it.isDeleted }.sortedByDescending { it.updatedAt }

    /** Soft Delete */
    fun markDeleted(id: String): SyncableNote? {
        val note = notes[id] ?: return null
        val deleted = note.copy(
            isDeleted = true,
            syncStatus = SyncStatus.PENDING_DELETE,
            updatedAt = LocalDateTime.now()
        )
        notes[id] = deleted
        return deleted
    }

    /** 동기화 대기 중인 메모 조회 */
    fun getPendingSync(): List<SyncableNote> =
        notes.values.filter { it.syncStatus != SyncStatus.SYNCED }

    /** 충돌 상태 메모 조회 */
    fun getConflicts(): List<SyncableNote> =
        notes.values.filter { it.syncStatus == SyncStatus.CONFLICT }

    /** 동기화 완료 처리 */
    fun markSynced(id: String, serverVersion: Long): SyncableNote? {
        val note = notes[id] ?: return null
        val synced = note.copy(
            syncStatus = SyncStatus.SYNCED,
            version = serverVersion,
            lastSyncedAt = LocalDateTime.now()
        )
        notes[id] = synced
        return synced
    }

    /** 서버 데이터로 덮어쓰기 */
    fun overwriteFromServer(note: SyncableNote): SyncableNote {
        val synced = note.copy(
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = LocalDateTime.now()
        )
        notes[synced.id] = synced
        return synced
    }

    /** 완전 삭제 (동기화 완료된 Tombstone 정리) */
    fun purgeDeleted() {
        val toRemove = notes.values
            .filter { it.isDeleted && it.syncStatus == SyncStatus.SYNCED }
            .map { it.id }
        toRemove.forEach { notes.remove(it) }
    }

    // --- ChangeLog 관련 ---

    fun addChangeLog(log: ChangeLog) {
        changeLogs.add(log)
    }

    fun getUnsyncedChangeLogs(): List<ChangeLog> =
        changeLogs.filter { !it.synced }.sortedBy { it.timestamp }

    fun markChangeLogSynced(logId: String) {
        val index = changeLogs.indexOfFirst { it.id == logId }
        if (index >= 0) {
            changeLogs[index] = changeLogs[index].copy(synced = true)
        }
    }

    fun clearSyncedChangeLogs() {
        changeLogs.removeAll { it.synced }
    }
}

// ============================================================
// 3. 원격 저장소 (서버 API)
// ============================================================

/**
 * 서버 API 시뮬레이션
 */
class RemoteStore {
    private val serverNotes = mutableMapOf<String, SyncableNote>()
    var isAvailable = true
        private set

    fun simulateDown() { isAvailable = false }
    fun simulateUp() { isAvailable = true }

    /** 서버에 메모 생성/수정 */
    fun upsert(note: SyncableNote): Result<SyncableNote> {
        if (!isAvailable) return Result.failure(NetworkException("서버에 연결할 수 없습니다"))

        val existing = serverNotes[note.id]

        // 버전 충돌 감지
        if (existing != null && existing.version > note.version) {
            return Result.failure(
                ConflictException(
                    "버전 충돌: 서버(v${existing.version}) > 로컬(v${note.version})",
                    serverNote = existing,
                    localNote = note
                )
            )
        }

        val serverNote = note.copy(version = (existing?.version ?: 0) + 1)
        serverNotes[serverNote.id] = serverNote
        return Result.success(serverNote)
    }

    /** 서버에서 메모 삭제 */
    fun delete(noteId: String): Result<Unit> {
        if (!isAvailable) return Result.failure(NetworkException("서버에 연결할 수 없습니다"))
        serverNotes.remove(noteId)
        return Result.success(Unit)
    }

    /** 특정 시점 이후 변경된 메모 조회 (Delta Sync) */
    fun getChangesSince(since: LocalDateTime?): Result<List<SyncableNote>> {
        if (!isAvailable) return Result.failure(NetworkException("서버에 연결할 수 없습니다"))
        return if (since == null) {
            Result.success(serverNotes.values.toList())
        } else {
            Result.success(
                serverNotes.values.filter { it.updatedAt.isAfter(since) }
            )
        }
    }

    /** 서버 데이터를 직접 설정 (시뮬레이션용) */
    fun seedData(note: SyncableNote) {
        serverNotes[note.id] = note.copy(syncStatus = SyncStatus.SYNCED)
    }
}

class NetworkException(message: String) : Exception(message)

class ConflictException(
    message: String,
    val serverNote: SyncableNote,
    val localNote: SyncableNote
) : Exception(message)

// ============================================================
// 4. 충돌 해결 전략 (Conflict Resolution)
// ============================================================

/**
 * 충돌 해결 전략 인터페이스
 */
interface ConflictResolver {
    fun resolve(local: SyncableNote, server: SyncableNote): ConflictResolution
}

sealed class ConflictResolution {
    data class KeepLocal(val note: SyncableNote) : ConflictResolution()
    data class KeepServer(val note: SyncableNote) : ConflictResolution()
    data class Merge(val mergedNote: SyncableNote) : ConflictResolution()
    data class KeepBoth(val local: SyncableNote, val server: SyncableNote) : ConflictResolution()
    data class Manual(val local: SyncableNote, val server: SyncableNote) : ConflictResolution()
}

/**
 * Last Write Wins (LWW) - 마지막 수정이 승리
 * 가장 단순하지만 데이터 손실 가능성 있음
 */
class LastWriteWinsResolver : ConflictResolver {
    override fun resolve(local: SyncableNote, server: SyncableNote): ConflictResolution {
        return if (local.updatedAt.isAfter(server.updatedAt)) {
            println("    [LWW] 로컬이 더 최신 → 로컬 유지")
            ConflictResolution.KeepLocal(local)
        } else {
            println("    [LWW] 서버가 더 최신 → 서버 유지")
            ConflictResolution.KeepServer(server)
        }
    }
}

/**
 * Field-Level Merge - 필드별 병합
 * 각 필드의 마지막 수정 시각을 비교하여 필드별로 최신 값 선택
 */
class FieldLevelMergeResolver : ConflictResolver {
    override fun resolve(local: SyncableNote, server: SyncableNote): ConflictResolution {
        // 필드별로 최신 값을 선택하여 병합
        val mergedTitle = if (local.title != server.title) {
            // 제목이 다르면 더 최근 수정을 선택
            if (local.updatedAt.isAfter(server.updatedAt)) local.title else server.title
        } else {
            local.title
        }

        val mergedContent = if (local.content != server.content) {
            // 내용이 다르면 더 최근 수정을 선택
            if (local.updatedAt.isAfter(server.updatedAt)) local.content else server.content
        } else {
            local.content
        }

        val merged = local.copy(
            title = mergedTitle,
            content = mergedContent,
            version = maxOf(local.version, server.version) + 1,
            updatedAt = LocalDateTime.now()
        )

        println("    [Merge] 필드별 병합: title=${merged.title}, content 길이=${merged.content.length}")
        return ConflictResolution.Merge(merged)
    }
}

/**
 * Keep Both - 양쪽 모두 보존 (복사본 생성)
 * 데이터 손실 없지만 중복 발생
 */
class KeepBothResolver : ConflictResolver {
    override fun resolve(local: SyncableNote, server: SyncableNote): ConflictResolution {
        val localCopy = local.copy(
            title = "${local.title} (내 디바이스)",
            id = UUID.randomUUID().toString()
        )
        println("    [KeepBoth] 양쪽 모두 보존: 서버 버전 + 로컬 복사본 생성")
        return ConflictResolution.KeepBoth(localCopy, server)
    }
}

/**
 * Manual Resolution - 사용자에게 선택 위임
 * 가장 안전하지만 UX 비용이 높음
 */
class ManualResolver : ConflictResolver {
    override fun resolve(local: SyncableNote, server: SyncableNote): ConflictResolution {
        println("    [Manual] 충돌을 사용자에게 위임")
        println("      로컬: '${local.title}' (${local.updatedAt})")
        println("      서버: '${server.title}' (${server.updatedAt})")
        return ConflictResolution.Manual(local, server)
    }
}

// ============================================================
// 5. 네트워크 상태 모니터
// ============================================================

/**
 * 네트워크 상태 감시
 * 실제로는 ConnectivityManager (Android), NWPathMonitor (iOS) 사용
 */
class ConnectivityMonitor {
    private val listeners = mutableListOf<(Boolean) -> Unit>()
    var isOnline = true
        private set

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun simulateOnline() {
        if (!isOnline) {
            isOnline = true
            println("  [Network] 온라인 복귀")
            listeners.forEach { it(true) }
        }
    }

    fun simulateOffline() {
        if (isOnline) {
            isOnline = false
            println("  [Network] 오프라인 전환")
            listeners.forEach { it(false) }
        }
    }
}

// ============================================================
// 6. 동기화 엔진 (Sync Engine)
// ============================================================

/**
 * 동기화 결과
 */
data class SyncResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val conflicts: Int = 0,
    val errors: List<String> = emptyList()
)

/**
 * 동기화 엔진 - 로컬과 서버 간 데이터 동기화
 */
class SyncEngine(
    private val localStore: LocalStore,
    private val remoteStore: RemoteStore,
    private val conflictResolver: ConflictResolver
) {
    private var lastSyncTime: LocalDateTime? = null
    private val syncQueue = ConcurrentLinkedQueue<ChangeLog>()

    /**
     * 전체 동기화 실행
     * Push (로컬 → 서버) → Pull (서버 → 로컬) 순서
     */
    fun sync(): SyncResult {
        println("\n  [Sync] 동기화 시작...")
        var uploaded = 0
        var downloaded = 0
        var conflicts = 0
        val errors = mutableListOf<String>()

        // Phase 1: Push - 로컬 변경 사항을 서버에 반영
        val pendingNotes = localStore.getPendingSync()
        println("  [Sync] Push: ${pendingNotes.size}개 대기 중")

        for (note in pendingNotes) {
            when (note.syncStatus) {
                SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE -> {
                    val result = remoteStore.upsert(note)
                    result.onSuccess { serverNote ->
                        localStore.markSynced(note.id, serverNote.version)
                        uploaded++
                        println("    ✓ Push 성공: '${note.title}' (v${serverNote.version})")
                    }.onFailure { error ->
                        when (error) {
                            is ConflictException -> {
                                println("    ⚠ 충돌 감지: '${note.title}'")
                                val resolution = conflictResolver.resolve(error.localNote, error.serverNote)
                                handleConflictResolution(resolution)
                                conflicts++
                            }
                            is NetworkException -> {
                                errors.add("Push 실패 (${note.title}): ${error.message}")
                                println("    ✗ Push 실패: ${error.message}")
                            }
                            else -> errors.add("Push 오류: ${error.message}")
                        }
                    }
                }
                SyncStatus.PENDING_DELETE -> {
                    val result = remoteStore.delete(note.id)
                    result.onSuccess {
                        localStore.markSynced(note.id, note.version)
                        uploaded++
                        println("    ✓ Delete 동기화: '${note.title}'")
                    }.onFailure { error ->
                        errors.add("Delete 실패 (${note.title}): ${error.message}")
                    }
                }
                else -> {}
            }
        }

        // Phase 2: Pull - 서버 변경 사항을 로컬에 반영 (Delta Sync)
        println("  [Sync] Pull: since=${lastSyncTime ?: "처음"}")
        val changesResult = remoteStore.getChangesSince(lastSyncTime)
        changesResult.onSuccess { serverNotes ->
            println("  [Sync] Pull: ${serverNotes.size}개 서버 변경")
            for (serverNote in serverNotes) {
                val localNote = localStore.getById(serverNote.id)
                if (localNote == null) {
                    // 로컬에 없는 새 메모 → 다운로드
                    localStore.overwriteFromServer(serverNote)
                    downloaded++
                    println("    ✓ 새 메모 다운로드: '${serverNote.title}'")
                } else if (localNote.syncStatus == SyncStatus.SYNCED) {
                    // 로컬에서 수정 안 한 메모 → 서버 버전으로 갱신
                    if (serverNote.version > localNote.version) {
                        localStore.overwriteFromServer(serverNote)
                        downloaded++
                        println("    ✓ 메모 갱신: '${serverNote.title}' (v${serverNote.version})")
                    }
                }
                // 로컬에서도 수정 중인 메모는 Push 단계에서 처리됨
            }
        }.onFailure { error ->
            errors.add("Pull 실패: ${error.message}")
        }

        // Tombstone 정리
        localStore.purgeDeleted()

        lastSyncTime = LocalDateTime.now()

        val result = SyncResult(uploaded, downloaded, conflicts, errors)
        println("  [Sync] 완료: ↑${result.uploaded} ↓${result.downloaded} ⚠${result.conflicts} ✗${result.errors.size}")
        return result
    }

    private fun handleConflictResolution(resolution: ConflictResolution) {
        when (resolution) {
            is ConflictResolution.KeepLocal -> {
                val note = resolution.note.copy(
                    version = resolution.note.version + 1,
                    syncStatus = SyncStatus.PENDING_UPDATE
                )
                localStore.save(note)
            }
            is ConflictResolution.KeepServer -> {
                localStore.overwriteFromServer(resolution.note)
            }
            is ConflictResolution.Merge -> {
                val note = resolution.mergedNote.copy(syncStatus = SyncStatus.PENDING_UPDATE)
                localStore.save(note)
            }
            is ConflictResolution.KeepBoth -> {
                localStore.overwriteFromServer(resolution.server)
                localStore.save(resolution.local.copy(syncStatus = SyncStatus.PENDING_CREATE))
            }
            is ConflictResolution.Manual -> {
                val note = resolution.local.copy(syncStatus = SyncStatus.CONFLICT)
                localStore.save(note)
            }
        }
    }
}

// ============================================================
// 7. Offline-First Repository (앱에서 사용하는 통합 인터페이스)
// ============================================================

/**
 * Offline-First Note Repository
 * UI 레이어는 이 Repository만 사용
 * 항상 로컬 우선 읽기/쓰기, 백그라운드 동기화
 */
class OfflineFirstNoteRepository(
    private val localStore: LocalStore,
    private val remoteStore: RemoteStore,
    private val syncEngine: SyncEngine,
    private val connectivity: ConnectivityMonitor
) {
    init {
        // 네트워크 복귀 시 자동 동기화
        connectivity.addListener { isOnline ->
            if (isOnline) {
                println("  [Repository] 온라인 복귀 감지 → 자동 동기화 트리거")
                syncEngine.sync()
            }
        }
    }

    /**
     * 메모 생성 - 항상 즉시 성공 (로컬 우선)
     */
    fun createNote(title: String, content: String): SyncableNote {
        val note = SyncableNote(
            title = title,
            content = content,
            syncStatus = SyncStatus.PENDING_CREATE
        )

        // 로컬에 즉시 저장
        localStore.save(note)

        // ChangeLog 기록
        localStore.addChangeLog(
            ChangeLog(
                entityId = note.id,
                operation = ChangeLog.Operation.CREATE,
                payload = mapOf("title" to title, "content" to content)
            )
        )

        println("  [Local] 메모 생성: '${note.title}' (${note.syncStatus})")

        // 온라인이면 즉시 동기화 시도
        if (connectivity.isOnline) {
            trySyncSingle(note)
        }

        return note
    }

    /**
     * 메모 수정 - 항상 즉시 성공 (로컬 우선)
     */
    fun updateNote(id: String, title: String? = null, content: String? = null): SyncableNote? {
        val existing = localStore.getById(id) ?: return null

        val updated = existing.copy(
            title = title ?: existing.title,
            content = content ?: existing.content,
            updatedAt = LocalDateTime.now(),
            syncStatus = if (existing.syncStatus == SyncStatus.PENDING_CREATE)
                SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE,
            version = existing.version
        )

        localStore.save(updated)

        localStore.addChangeLog(
            ChangeLog(
                entityId = id,
                operation = ChangeLog.Operation.UPDATE,
                payload = buildMap {
                    title?.let { put("title", it) }
                    content?.let { put("content", it) }
                }
            )
        )

        println("  [Local] 메모 수정: '${updated.title}' (${updated.syncStatus})")

        if (connectivity.isOnline) {
            trySyncSingle(updated)
        }

        return updated
    }

    /**
     * 메모 삭제 - Soft Delete 후 동기화
     */
    fun deleteNote(id: String): Boolean {
        val deleted = localStore.markDeleted(id) ?: return false

        localStore.addChangeLog(
            ChangeLog(
                entityId = id,
                operation = ChangeLog.Operation.DELETE
            )
        )

        println("  [Local] 메모 삭제 (Soft): '${deleted.title}' (${deleted.syncStatus})")

        if (connectivity.isOnline) {
            trySyncSingle(deleted)
        }

        return true
    }

    /**
     * 메모 목록 조회 - 항상 로컬에서 읽기 (즉시 반환)
     */
    fun getNotes(): List<SyncableNote> {
        return localStore.getAll()
    }

    /**
     * 메모 상세 조회
     */
    fun getNote(id: String): SyncableNote? {
        return localStore.getById(id)?.takeIf { !it.isDeleted }
    }

    /**
     * 동기화 대기 중인 변경 수
     */
    fun getPendingChangesCount(): Int {
        return localStore.getPendingSync().size
    }

    /**
     * 충돌 목록 조회
     */
    fun getConflicts(): List<SyncableNote> {
        return localStore.getConflicts()
    }

    /**
     * 수동 충돌 해결
     */
    fun resolveConflict(noteId: String, resolution: SyncableNote) {
        val resolved = resolution.copy(syncStatus = SyncStatus.PENDING_UPDATE)
        localStore.save(resolved)
        println("  [Local] 충돌 해결: '${resolved.title}'")

        if (connectivity.isOnline) {
            trySyncSingle(resolved)
        }
    }

    /**
     * 전체 동기화 실행
     */
    fun sync(): SyncResult {
        return syncEngine.sync()
    }

    private fun trySyncSingle(note: SyncableNote) {
        when (note.syncStatus) {
            SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE -> {
                remoteStore.upsert(note).onSuccess { serverNote ->
                    localStore.markSynced(note.id, serverNote.version)
                    println("    → 즉시 동기화 성공 (v${serverNote.version})")
                }.onFailure {
                    println("    → 즉시 동기화 실패, 나중에 재시도")
                }
            }
            SyncStatus.PENDING_DELETE -> {
                remoteStore.delete(note.id).onSuccess {
                    localStore.markSynced(note.id, note.version)
                    println("    → 삭제 동기화 성공")
                }
            }
            else -> {}
        }
    }
}

// ============================================================
// 8. 동기화 상태 UI 모델
// ============================================================

/**
 * UI에 표시할 동기화 상태
 */
data class SyncStatusUi(
    val isOnline: Boolean,
    val pendingChanges: Int,
    val conflicts: Int,
    val lastSyncTime: LocalDateTime?,
    val statusMessage: String
) {
    companion object {
        fun from(
            connectivity: ConnectivityMonitor,
            repository: OfflineFirstNoteRepository,
            lastSync: LocalDateTime?
        ): SyncStatusUi {
            val pending = repository.getPendingChangesCount()
            val conflicts = repository.getConflicts().size
            val online = connectivity.isOnline

            val message = when {
                !online && pending > 0 -> "오프라인 - ${pending}개 변경 대기 중"
                !online -> "오프라인"
                conflicts > 0 -> "충돌 ${conflicts}개 해결 필요"
                pending > 0 -> "동기화 중... (${pending}개)"
                else -> "모든 변경 동기화 완료"
            }

            return SyncStatusUi(
                isOnline = online,
                pendingChanges = pending,
                conflicts = conflicts,
                lastSyncTime = lastSync,
                statusMessage = message
            )
        }
    }
}

// ============================================================
// 데모
// ============================================================

fun main() {
    println("=== Offline-First Pattern - 메모 앱 ===\n")

    // 초기화
    val localStore = LocalStore()
    val remoteStore = RemoteStore()
    val connectivity = ConnectivityMonitor()
    val conflictResolver = LastWriteWinsResolver()
    val syncEngine = SyncEngine(localStore, remoteStore, conflictResolver)
    val repository = OfflineFirstNoteRepository(localStore, remoteStore, syncEngine, connectivity)

    // --- 시나리오 1: 온라인에서 기본 CRUD ---
    println("--- 1. 온라인 상태에서 메모 CRUD ---")
    val note1 = repository.createNote("회의록", "오늘 회의 내용입니다")
    val note2 = repository.createNote("할일 목록", "장보기, 운동하기")
    repository.updateNote(note1.id, content = "오늘 회의 내용 수정본")

    println("\n현재 메모 목록:")
    repository.getNotes().forEach { note ->
        println("  [${note.syncStatus}] ${note.title} (v${note.version})")
    }

    // --- 시나리오 2: 오프라인에서 작업 ---
    println("\n--- 2. 오프라인 전환 후 작업 ---")
    connectivity.simulateOffline()
    remoteStore.simulateDown()

    val note3 = repository.createNote("오프라인 메모", "네트워크 없이 작성")
    repository.updateNote(note2.id, title = "할일 목록 (업데이트)")
    repository.deleteNote(note1.id)

    println("\n오프라인 메모 목록:")
    repository.getNotes().forEach { note ->
        println("  [${note.syncStatus}] ${note.title}")
    }
    println("대기 중인 변경: ${repository.getPendingChangesCount()}개")

    // --- 시나리오 3: 온라인 복귀 → 자동 동기화 ---
    println("\n--- 3. 온라인 복귀 → 자동 동기화 ---")
    remoteStore.simulateUp()
    connectivity.simulateOnline()  // 자동 동기화 트리거

    println("\n동기화 후 메모 목록:")
    repository.getNotes().forEach { note ->
        println("  [${note.syncStatus}] ${note.title} (v${note.version})")
    }

    // --- 시나리오 4: 충돌 시나리오 ---
    println("\n--- 4. 충돌 해결 시나리오 ---")

    // 서버에 직접 데이터 변경 (다른 디바이스에서 수정한 것처럼)
    val conflictNote = repository.createNote("공유 메모", "원본 내용")
    // 오프라인 전환
    connectivity.simulateOffline()
    remoteStore.simulateDown()

    // 로컬에서 수정
    repository.updateNote(conflictNote.id, content = "로컬에서 수정한 내용")

    // 서버에서도 수정 (다른 디바이스)
    remoteStore.simulateUp()
    remoteStore.seedData(
        conflictNote.copy(
            content = "다른 디바이스에서 수정한 내용",
            version = conflictNote.version + 1,
            updatedAt = LocalDateTime.now().plusSeconds(10)
        )
    )

    // 온라인 복귀 → 동기화 → 충돌!
    connectivity.simulateOnline()

    // --- 시나리오 5: 다양한 충돌 해결 전략 비교 ---
    println("\n--- 5. 충돌 해결 전략 비교 ---")

    val localVersion = SyncableNote(
        id = "conflict-demo",
        title = "회의록 v2",
        content = "로컬에서 수정한 내용",
        version = 1,
        updatedAt = LocalDateTime.now()
    )
    val serverVersion = SyncableNote(
        id = "conflict-demo",
        title = "회의록 최종",
        content = "서버에서 수정한 내용",
        version = 2,
        updatedAt = LocalDateTime.now().plusMinutes(5)
    )

    println("\n  로컬: '${localVersion.title}' - ${localVersion.content}")
    println("  서버: '${serverVersion.title}' - ${serverVersion.content}\n")

    println("  전략 1: Last Write Wins")
    LastWriteWinsResolver().resolve(localVersion, serverVersion)

    println("\n  전략 2: Field-Level Merge")
    FieldLevelMergeResolver().resolve(localVersion, serverVersion)

    println("\n  전략 3: Keep Both")
    KeepBothResolver().resolve(localVersion, serverVersion)

    println("\n  전략 4: Manual Resolution")
    ManualResolver().resolve(localVersion, serverVersion)

    // --- 시나리오 6: UI 동기화 상태 ---
    println("\n--- 6. UI 동기화 상태 표시 ---")

    val statusOnline = SyncStatusUi.from(connectivity, repository, LocalDateTime.now())
    println("  온라인 상태: ${statusOnline.statusMessage}")

    connectivity.simulateOffline()
    // 오프라인에서 작업 추가
    repository.createNote("긴급 메모", "오프라인 작성")

    val statusOffline = SyncStatusUi.from(connectivity, repository, null)
    println("  오프라인 상태: ${statusOffline.statusMessage}")

    // 복귀
    remoteStore.simulateUp()
    connectivity.simulateOnline()

    println("\n=== Offline-First 핵심 원칙 ===")
    println("1. Local First: 로컬 DB가 Single Source of Truth")
    println("2. 즉각 반응: 사용자 작업은 항상 즉시 로컬에 반영")
    println("3. 백그라운드 동기화: 네트워크 가용 시 자동 동기화")
    println("4. 충돌 해결: 명시적인 충돌 감지 및 해결 전략")
    println("5. Soft Delete: Tombstone으로 삭제 추적, 동기화 후 정리")
    println("6. Delta Sync: 마지막 동기화 이후 변경분만 전송")
    println("7. 연결 감지: 네트워크 복귀 시 자동 동기화 트리거")
}
