package behavioral.nullobject.authorization

class Solution {
    // Null Object 패턴 적용: 권한 없음을 나타내는 객체
    class NullPermission : Permission {
        override fun hasReadAccess() = false
        override fun hasWriteAccess() = false
        override fun hasDeleteAccess() = false
        override fun getPermissionLevel() = 0

        override fun toString() = "NullPermission"
    }

    // 사용자 객체
    class User(val id: String, val name: String, val role: String) {
        fun getPermission(): Permission {
            return when (role) {
                "admin" -> AdminPermission()
                "user" -> UserPermission()
                "guest" -> GuestPermission()
                else -> NullPermission()  // null 대신 NullPermission 객체 반환
            }
        }
    }

    // 문서 클래스
    class Document(val name: String, val content: String) {
        // null 체크 제거
        fun read(permission: Permission): String {
            return if (permission.hasReadAccess()) {
                "읽기 성공: $content"
            } else {
                "읽기 권한이 없습니다."
            }
        }

        // null 체크 제거
        fun write(permission: Permission, newContent: String): String {
            return if (permission.hasWriteAccess()) {
                "쓰기 성공: $newContent"
            } else {
                "쓰기 권한이 없습니다."
            }
        }

        // null 체크 제거
        fun delete(permission: Permission): String {
            return if (permission.hasDeleteAccess()) {
                "삭제 성공"
            } else {
                "삭제 권한이 없습니다."
            }
        }
    }

    // 인증 서비스
    class AuthorizationService(private val userRepository: UserRepository) {
        fun getUserPermission(userId: String): Permission {
            val user = userRepository.findById(userId)
            // user가 null이면 NullPermission을 반환
            return user?.getPermission() ?: NullPermission()
        }
    }

    // 팩토리 방식으로 정적 인스턴스 제공
    object Permissions {
        val NULL: Permission = NullPermission()
        val ADMIN: Permission = AdminPermission()
        val USER: Permission = UserPermission()
        val GUEST: Permission = GuestPermission()
    }
}

fun main() {
    val userRepository = UserRepository()
    val authService = Solution.AuthorizationService(userRepository)
    val document = Solution.Document("중요 문서", "이 문서는 매우 중요한 내용을 담고 있습니다.")

    println("===== 사용자별 권한 테스트 =====")

    // 관리자 테스트
    val adminPermission = authService.getUserPermission("1")
    println("관리자 권한: $adminPermission")
    println(document.read(adminPermission))
    println(document.write(adminPermission, "관리자가 수정한 내용"))
    println(document.delete(adminPermission))

    // 일반 사용자 테스트
    val userPermission = authService.getUserPermission("2")
    println("\n일반 사용자 권한: $userPermission")
    println(document.read(userPermission))
    println(document.write(userPermission, "사용자가 수정한 내용"))
    println(document.delete(userPermission))

    // 게스트 테스트
    val guestPermission = authService.getUserPermission("3")
    println("\n게스트 권한: $guestPermission")
    println(document.read(guestPermission))
    println(document.write(guestPermission, "게스트가 수정한 내용"))
    println(document.delete(guestPermission))

    // 알 수 없는 사용자 테스트 - 이제 NullPermission이 반환됨
    val unknownPermission = authService.getUserPermission("4")
    println("\n알 수 없는 사용자 권한: $unknownPermission")

    // null 체크가 필요 없음
    println(document.read(unknownPermission))
    println(document.write(unknownPermission, "알 수 없는 사용자가 수정한 내용"))
    println(document.delete(unknownPermission))

    // 다른 모듈에서도 안전하게 사용 가능
    println("\n===== null 체크 누락 예시 (이제 안전함) =====")

    // 새로운 문서 접근 함수 (null 체크 필요 없음)
    fun accessDocument(doc: Solution.Document, permission: Permission) {
        // permission이 null일 수 없으므로 안전함
        if (permission.hasReadAccess()) {
            println("문서에 접근 가능: ${doc.name}")
        } else {
            println("문서에 접근 불가")
        }
    }

    accessDocument(document, adminPermission)  // 성공
    accessDocument(document, unknownPermission)  // 안전하게 실패 메시지 출력

    // 정적 인스턴스 사용 예시
    println("\n===== 정적 인스턴스 사용 예시 =====")
    println("NULL 권한 읽기: " + document.read(Solution.Permissions.NULL))
    println("ADMIN 권한 삭제: " + document.delete(Solution.Permissions.ADMIN))

    // 확장 기능: 권한 수준에 따른 처리 예시
    println("\n===== 권한 수준에 따른 처리 =====")
    fun processBasedOnLevel(permission: Permission) {
        when (permission.getPermissionLevel()) {
            0 -> println("권한 없음: 기본 페이지로 리다이렉트")
            1 -> println("게스트 권한: 읽기만 가능")
            2 -> println("사용자 권한: 읽기/쓰기 가능")
            3 -> println("관리자 권한: 모든 기능 사용 가능")
        }
    }

    processBasedOnLevel(adminPermission)
    processBasedOnLevel(userPermission)
    processBasedOnLevel(guestPermission)
    processBasedOnLevel(unknownPermission)
}