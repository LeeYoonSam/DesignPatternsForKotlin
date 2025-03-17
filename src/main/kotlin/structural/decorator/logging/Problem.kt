package structural.decorator.logging

import java.util.concurrent.ConcurrentHashMap

/**
 * Problem.kt에서는 비즈니스 로직과 로깅/성능 측정/예외 처리 코드가 모두 혼합되어 있어 다음과 같은 문제가 발생합니다:
 *
 * 코드 가독성이 떨어집니다.
 * 유지보수가 어렵습니다.
 * 코드 중복이 많습니다.
 * 기능 확장이 어렵습니다.
 */
class Problem {
    // 문제가 있는 UserService 구현체
    class UserServiceImpl : UserService {
        private val users = ConcurrentHashMap<String, User>()

        override fun getUser(id: String): User? {
            println("로그: getUser 호출됨 - id: $id")
            try {
                println("로그: 사용자 검색 시작")
                val startTime = System.currentTimeMillis()
                val user = users[id]
                val endTime = System.currentTimeMillis()
                println("로그: 사용자 검색 완료 - 소요시간: ${endTime - startTime}ms")
                return user
            } catch (e: Exception) {
                println("로그: 오류 발생 - ${e.message}")
                throw e
            }
        }

        override fun createUser(user: User): Boolean {
            println("로그: createUser 호출됨 - user: $user")
            try {
                println("로그: 사용자 생성 시작")
                val startTime = System.currentTimeMillis()
                val result = users.putIfAbsent(user.id, user) == null
                val endTime = System.currentTimeMillis()
                println("로그: 사용자 생성 완료 - 소요시간: ${endTime - startTime}ms")
                return result
            } catch (e: Exception) {
                println("로그: 오류 발생 - ${e.message}")
                throw e
            }
        }

        override fun updateUser(user: User): Boolean {
            println("로그: updateUser 호출됨 - user: $user")
            try {
                println("로그: 사용자 업데이트 시작")
                val startTime = System.currentTimeMillis()
                val result = if (users.containsKey(user.id)) {
                    users[user.id] = user
                    true
                } else {
                    false
                }
                val endTime = System.currentTimeMillis()
                println("로그: 사용자 업데이트 완료 - 소요시간: ${endTime - startTime}ms")
                return result
            } catch (e: Exception) {
                println("로그: 오류 발생 - ${e.message}")
                throw e
            }
        }

        override fun deleteUser(id: String): Boolean {
            println("로그: deleteUser 호출됨 - id: $id")
            try {
                println("로그: 사용자 삭제 시작")
                val startTime = System.currentTimeMillis()
                val result = users.remove(id) != null
                val endTime = System.currentTimeMillis()
                println("로그: 사용자 삭제 완료 - 소요시간: ${endTime - startTime}ms")
                return result
            } catch (e: Exception) {
                println("로그: 오류 발생 - ${e.message}")
                throw e
            }
        }
    }
}

fun main() {
    val userService: UserService = Problem.UserServiceImpl()

    println("===== 사용자 생성 =====")
    val user1 = User("1", "홍길동", "hong@email.com")
    userService.createUser(user1)

    println("\n===== 사용자 조회 =====")
    val foundUser = userService.getUser("1")
    println("조회된 사용자: $foundUser")

    println("\n===== 사용자 업데이트 =====")
    val updatedUser = User("1", "홍길동(수정)", "hong_updated@email.com")
    userService.updateUser(updatedUser)

    println("\n===== 업데이트된 사용자 조회 =====")
    val foundUpdatedUser = userService.getUser("1")
    println("업데이트된 사용자: $foundUpdatedUser")

    println("\n===== 사용자 삭제 =====")
    userService.deleteUser("1")

    println("\n===== 삭제 후 사용자 조회 =====")
    val deletedUser = userService.getUser("1")
    println("삭제된 사용자 조회 결과: $deletedUser")
}