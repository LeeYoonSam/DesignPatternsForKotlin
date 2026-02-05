package architecture.offlinefirst.notes

import java.time.LocalDateTime
import java.util.UUID

/**
 * Offline-First Pattern - 문제 상황
 *
 * 메모 앱을 개발하고 있습니다.
 * 사용자가 메모를 작성/수정/삭제하면 서버에 바로 저장하는 구조인데,
 * 네트워크가 불안정하거나 오프라인 상태에서 다양한 문제가 발생합니다.
 */

// ============================================================
// 공통 모델
// ============================================================

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

// ============================================================
// ❌ 문제 1: 네트워크에 직접 의존하는 구조
// ============================================================

/**
 * 모든 작업이 네트워크 호출에 직접 의존
 * → 오프라인에서 앱이 완전히 동작 불가
 */
class OnlineOnlyNoteRepository {
    // 서버 API 시뮬레이션
    private var isNetworkAvailable = true

    fun simulateNetworkDown() { isNetworkAvailable = false }
    fun simulateNetworkUp() { isNetworkAvailable = true }

    fun createNote(note: Note): Result<Note> {
        // 항상 서버에 직접 요청
        if (!isNetworkAvailable) {
            return Result.failure(Exception("네트워크 연결 없음 - 메모를 저장할 수 없습니다"))
        }
        println("  [서버] 메모 저장: ${note.title}")
        return Result.success(note)
    }

    fun updateNote(note: Note): Result<Note> {
        if (!isNetworkAvailable) {
            return Result.failure(Exception("네트워크 연결 없음 - 메모를 수정할 수 없습니다"))
        }
        println("  [서버] 메모 수정: ${note.title}")
        return Result.success(note)
    }

    fun deleteNote(noteId: String): Result<Unit> {
        if (!isNetworkAvailable) {
            return Result.failure(Exception("네트워크 연결 없음 - 메모를 삭제할 수 없습니다"))
        }
        println("  [서버] 메모 삭제: $noteId")
        return Result.success(Unit)
    }

    fun getNotes(): Result<List<Note>> {
        if (!isNetworkAvailable) {
            return Result.failure(Exception("네트워크 연결 없음 - 메모를 불러올 수 없습니다"))
        }
        return Result.success(emptyList())
    }
}

// ============================================================
// ❌ 문제 2: 단순 캐시 방식의 한계
// ============================================================

/**
 * 서버 응답을 캐시하지만 오프라인 쓰기가 불가능하고
 * 충돌 해결 전략이 없음
 */
class SimpleCacheNoteRepository {
    private val cache = mutableMapOf<String, Note>()
    private var isNetworkAvailable = true

    fun simulateNetworkDown() { isNetworkAvailable = false }

    fun getNotes(): List<Note> {
        if (isNetworkAvailable) {
            // 서버에서 가져와서 캐시 갱신
            val serverNotes = fetchFromServer()
            cache.clear()
            serverNotes.forEach { cache[it.id] = it }
        }
        // 오프라인이면 캐시 반환 → 읽기는 가능
        return cache.values.toList()
    }

    fun createNote(note: Note): Result<Note> {
        if (!isNetworkAvailable) {
            // 캐시에만 저장 → 서버와 동기화 불가
            cache[note.id] = note
            // ❌ 문제: 다시 온라인이 되었을 때 이 변경을 서버에 보낼 방법이 없음
            // ❌ 문제: 앱을 재시작하면 캐시가 날아감 (메모리 캐시)
            println("  [캐시] 메모 임시 저장 (동기화 불가): ${note.title}")
            return Result.success(note)
        }
        // 온라인이면 서버에 저장 후 캐시 갱신
        cache[note.id] = note
        return Result.success(note)
    }

    private fun fetchFromServer(): List<Note> = emptyList()
}

// ============================================================
// ❌ 문제 3: 충돌 해결 전략 없음
// ============================================================

class ConflictProblem {
    fun demonstrate() {
        println("--- 동시 편집 충돌 시나리오 ---")
        println()

        // 디바이스 A: 오프라인에서 메모 수정 (제목 변경)
        println("  디바이스 A (오프라인): 제목을 '회의록 v2'로 변경")
        println("  디바이스 B (온라인):   제목을 '회의록 최종'으로 변경")
        println()

        // 디바이스 A가 다시 온라인
        println("  디바이스 A가 온라인에 복귀 → 동기화 시도")
        println("  ❌ 누구의 변경이 맞는가? 어떤 것을 유지해야 하는가?")
        println("  ❌ Last Write Wins? → 디바이스 B의 변경이 소실될 수 있음")
        println("  ❌ 병합? → 어떤 필드를 병합할지 전략이 없음")
        println()

        // 삭제 충돌
        println("  디바이스 A (오프라인): 메모 수정")
        println("  디바이스 B (온라인):   같은 메모 삭제")
        println("  ❌ 수정 vs 삭제 충돌 → 처리 전략 없음")
    }
}

// ============================================================
// ❌ 문제 4: 동기화 순서 문제
// ============================================================

class SyncOrderProblem {
    fun demonstrate() {
        println("--- 동기화 순서 문제 ---")
        println()

        // 오프라인에서 여러 작업 수행
        println("  오프라인에서 수행한 작업 순서:")
        println("    1. 메모 A 생성")
        println("    2. 메모 A 제목 수정")
        println("    3. 메모 B 생성")
        println("    4. 메모 A 삭제")
        println()

        // 온라인 복귀 시
        println("  온라인 복귀 → 어떻게 동기화?")
        println("  ❌ 작업 1~4를 순서대로 전송? → 메모 A는 결국 삭제되므로 1,2는 불필요")
        println("  ❌ 최종 상태만 전송? → 중간 이력이 소실됨")
        println("  ❌ 대량 오프라인 작업 후 동기화 → 네트워크 부담")
        println("  ❌ 동기화 중 네트워크 끊김 → 부분 동기화 문제")
    }
}

// ============================================================
// ❌ 문제 5: UI 상태 관리 혼란
// ============================================================

class UiStateProblem {
    fun demonstrate() {
        println("--- UI 상태 관리 문제 ---")
        println()
        println("  사용자가 메모 저장 버튼을 누름")
        println("  → 로딩 표시... (네트워크 요청)")
        println("  → 5초... 10초... 타임아웃!")
        println("  → '저장 실패' 에러 표시")
        println()
        println("  ❌ 사용자 경험: 저장 버튼 누를 때마다 불안")
        println("  ❌ 네트워크 느리면 앱 전체가 블로킹")
        println("  ❌ 저장 실패 시 사용자가 다시 시도해야 함")
        println("  ❌ 여러 화면에서 동시에 같은 데이터 편집 시 UI 불일치")
    }
}

fun main() {
    println("=== Offline-First Pattern - 문제 상황 ===\n")

    // 문제 1: 네트워크 직접 의존
    println("--- 1. 네트워크에 직접 의존하는 구조 ---")
    val repo = OnlineOnlyNoteRepository()

    val note = Note(title = "회의록", content = "오늘 회의 내용...")
    println("온라인 상태:")
    repo.createNote(note).onSuccess { println("  ✓ 저장 성공") }

    repo.simulateNetworkDown()
    println("오프라인 상태:")
    repo.createNote(note).onFailure { println("  ✗ ${it.message}") }
    repo.getNotes().onFailure { println("  ✗ ${it.message}") }

    // 문제 2: 단순 캐시
    println("\n--- 2. 단순 캐시 방식의 한계 ---")
    val cacheRepo = SimpleCacheNoteRepository()
    cacheRepo.simulateNetworkDown()
    cacheRepo.createNote(Note(title = "임시 메모", content = "동기화 불가"))
    println("  ❌ 앱 재시작 시 캐시 소실, 서버 동기화 전략 없음")

    // 문제 3: 충돌 해결
    println("\n--- 3. 충돌 해결 전략 없음 ---")
    ConflictProblem().demonstrate()

    // 문제 4: 동기화 순서
    println("\n--- 4. 동기화 순서 문제 ---")
    SyncOrderProblem().demonstrate()

    // 문제 5: UI 상태
    println("\n--- 5. UI 상태 관리 혼란 ---")
    UiStateProblem().demonstrate()

    println("\n핵심 문제:")
    println("• 오프라인에서 앱이 동작하지 않음 (읽기/쓰기 모두 불가)")
    println("• 동기화 전략이 없어 데이터 충돌 시 데이터 손실 위험")
    println("• 네트워크 상태에 따라 UX가 크게 저하됨")
    println("• 멀티 디바이스 환경에서 데이터 일관성을 보장할 수 없음")
}
