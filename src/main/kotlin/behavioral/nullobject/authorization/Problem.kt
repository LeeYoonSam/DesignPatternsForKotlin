package behavioral.nullobject.authorization

class Problem {
    // 사용자 객체
    class User(val id: String, val name: String, val role: String) {
        fun getPermission(): Permission? {
            return when(role) {
                "admin" -> AdminPermission()
                "user" -> UserPermission()
                "guest" -> GuestPermission()
                else -> null
            }
        }
    }

    // 문서 클래스
    class Document(val name: String, val content: String) {
        fun read(permission: Permission?): String {
            return if (permission != null && permission.hasReadAccess()) {
                "읽기 성공: $content"
            } else {
                "읽기 권한이 없습니다."
            }
        }

        fun write(permission: Permission?, newContent: String): String {
            return if (permission != null && permission.hasWriteAccess()) {
                "쓰기 성공: $newContent"
            } else {
                "쓰기 권한이 없습니다."
            }
        }

        fun delete(permission: Permission?): String {
            return if (permission != null && permission.hasDeleteAccess()) {
                "삭제 성공"
            } else {
                "삭제 권한이 없습니다."
            }
        }
    }

    // 인증 서비스
    class AuthorizationService(private val userRepository: UserRepository) {
        fun getUserPermission(userId: String): Permission? {
            val user = userRepository.findById(userId)
            return user?.getPermission()
        }
    }
}

fun main() {
    val userRepository = UserRepository()
    val authService = Problem.AuthorizationService(userRepository)
    val document = Problem.Document("중요 문서", "이 문서는 매우 중요한 내용을 담고 있습니다.")

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

    // 알 수 없는 사용자 테스트 - 여기서 문제 발생!
    val unknownPermission = authService.getUserPermission("4")
    println("\n알 수 없는 사용자 권한: $unknownPermission")

    // null 체크를 잊어버리면 NullPointerException이 발생할 수 있음
    try {
        // 이 코드는 안전하게 null 체크를 수행하지만...
        println(document.read(unknownPermission))
        println(document.write(unknownPermission, "알 수 없는 사용자가 수정한 내용"))
        println(document.delete(unknownPermission))
    } catch (e: Exception) {
        println("예외 발생: ${e.message}")
    }

//    // 다른 모듈에서 null 체크를 잊어버릴 경우
//    println("\n===== null 체크 누락 예시 =====")
//
//    // 새로운 문서 접근 함수 (null 체크 누락)
//    fun accessDocument(doc: Problem.Document, permission: Problem.Permission?) {
//        // 여기서 permission이 null인지 확인하지 않음
//        if (permission.hasReadAccess()) {  // NullPointerException 발생 가능
//            println("문서에 접근 가능: ${doc.name}")
//        } else {
//            println("문서에 접근 불가")
//        }
//    }
//
//    try {
//        accessDocument(document, adminPermission)  // 성공
//        accessDocument(document, unknownPermission)  // 실패 - NullPointerException
//    } catch (e: Exception) {
//        println("예외 발생: ${e.javaClass.simpleName} - ${e.message}")
//    }
}