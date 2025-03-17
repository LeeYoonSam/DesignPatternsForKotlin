package structural.decorator.logging

import java.util.concurrent.ConcurrentHashMap

/**
 * 단일 책임 원칙 적용:
 *
 * SimpleUserServiceImpl: 순수 비즈니스 로직만 담당
 * LoggingDecorator: 로깅만 담당
 * PerformanceDecorator: 성능 측정만 담당
 * ExceptionHandlingDecorator: 예외 처리만 담당
 *
 *
 * 데코레이터 패턴 적용:
 *
 * 기본 데코레이터인 UserServiceDecorator를 생성하여 공통 로직을 정의
 * 각 데코레이터는 특정 기능만 담당하도록 구현
 *
 *
 * 유연한 구성:
 *
 * 데코레이터를 조합하여 다양한 기능 구성 가능 (예: 로깅만, 성능 측정만, 또는 모든 기능 함께)
 * 필요에 따라 데코레이터를 추가하거나 제거하여 기능 확장 가능
 *
 * -----------------------------------------------------------------------
 *
 * 이해 포인트
 * 1. 중첩 데코레이터의 흐름:
 * ```
 * val fullyDecoratedUserService = LoggingDecorator(
 *     PerformanceDecorator(
 *         ExceptionHandlingDecorator(baseUserService)
 *     )
 * )
 * ```
 * 이와 같이 중첩된 데코레이터는 안쪽에서 바깥쪽으로 순서대로 실행됩니다:
 *
 * - 먼저 ExceptionHandlingDecorator가 예외를 처리
 * - 그 다음 PerformanceDecorator가 성능을 측정
 * - 마지막으로 LoggingDecorator가 로깅을 수행
 *
 * 2. 데코레이터 순서의 중요성:
 *
 * 데코레이터 적용 순서에 따라 결과가 달라질 수 있습니다.
 * 예를 들어, 예외 처리 데코레이터를 가장 안쪽에 두면 다른 데코레이터에서 발생한 예외도 처리할 수 있습니다.
 *
 *
 * 3. 개방-폐쇄 원칙:
 *
 * 기존 코드(SimpleUserServiceImpl)를 수정하지 않고 새로운 기능을 추가할 수 있습니다.
 * 예를 들어, 새로운 캐싱 데코레이터가 필요하면 기존 코드를 변경하지 않고 추가할 수 있습니다.
 *
 *
 * 4. 객체 조합의 장점:
 *
 * 상속보다 유연한 구조를 제공합니다.
 * 런타임에 객체의 행동을 동적으로 변경할 수 있습니다.
 */
class Solution {
    // 순수 비즈니스 로직만 포함하는 UserService 구현체
    class SimpleUserServiceImpl : UserService {
        private val users = ConcurrentHashMap<String, User>()

        override fun getUser(id: String): User? {
            Thread.sleep(100)
            return users[id]
        }

        override fun createUser(user: User): Boolean {
            Thread.sleep(200)
            return users.putIfAbsent(user.id, user) == null
        }

        override fun updateUser(user: User): Boolean {
            Thread.sleep(200)
            return if (users.containsKey(user.id)) {
                users[user.id] = user
                true
            } else {
                false
            }
        }

        override fun deleteUser(id: String): Boolean {
            Thread.sleep(100)
            return users.remove(id) != null
        }
    }

    // 기본 데코레이터 클래스
    abstract class UserServiceDecorator(protected val userService: UserService) : UserService {
        override fun getUser(id: String): User? {
            return userService.getUser(id)
        }

        override fun createUser(user: User): Boolean {
            return userService.createUser(user)
        }

        override fun updateUser(user: User): Boolean {
            return userService.updateUser(user)
        }

        override fun deleteUser(id: String): Boolean {
            return userService.deleteUser(id)
        }
    }

    // 로깅 데코레이터
    class LoggingDecorator(userService: UserService) : UserServiceDecorator(userService) {
        override fun getUser(id: String): User? {
            println("로그: getUser 호출됨 - id: $id")
            val result = super.getUser(id)
            println("로그: getUser 결과 - $result")
            return result
        }

        override fun createUser(user: User): Boolean {
            println("로그: createUser 호출됨 - user: $user")
            val result = super.createUser(user)
            println("로그: createUser 결과 - $result")
            return result
        }

        override fun updateUser(user: User): Boolean {
            println("로그: updateUser 호출됨 - user: $user")
            val result = super.updateUser(user)
            println("로그: updateUser 결과 - $result")
            return result
        }

        override fun deleteUser(id: String): Boolean {
            println("로그: deleteUser 호출됨 - id: $id")
            val result = super.deleteUser(id)
            println("로그: deleteUser 결과 - $result")
            return result
        }
    }

    // 성능 측정 데코레이터
    class PerformanceDecorator(userService: UserService): UserServiceDecorator(userService) {
        override fun getUser(id: String): User? {
            val startTime = System.currentTimeMillis()
            val result = super.getUser(id)
            val endTime = System.currentTimeMillis()
            println("성능: getUser 작업 소요시간 - ${endTime - startTime}ms")
            return result
        }

        override fun createUser(user: User): Boolean {
            val startTime = System.currentTimeMillis()
            val result = super.createUser(user)
            val endTime = System.currentTimeMillis()
            println("성능: createUser 작업 소요시간 - ${endTime - startTime}ms")
            return result
        }

        override fun updateUser(user: User): Boolean {
            val startTime = System.currentTimeMillis()
            val result = super.updateUser(user)
            val endTime = System.currentTimeMillis()
            println("성능: updateUser 작업 소요시간 - ${endTime - startTime}ms")
            return result
        }

        override fun deleteUser(id: String): Boolean {
            val startTime = System.currentTimeMillis()
            val result = super.deleteUser(id)
            val endTime = System.currentTimeMillis()
            println("성능: deleteUser 작업 소요시간 - ${endTime - startTime}ms")
            return result
        }
    }

    // 예외 처리 데코레이터
    class ExceptionHandlingDecorator(userService: UserService): UserServiceDecorator(userService) {
        override fun getUser(id: String): User? {
            return try {
                super.getUser(id)
            } catch (e: Exception) {
                println("예외: getUser 수행 중 오류 발생 - ${e.message}")
                null
            }
        }

        override fun createUser(user: User): Boolean {
            return try {
                super.createUser(user)
            } catch (e: Exception) {
                println("예외: createUser 수행 중 오류 발생 - ${e.message}")
                false
            }
        }

        override fun updateUser(user: User): Boolean {
            return try {
                super.updateUser(user)
            } catch (e: Exception) {
                println("예외: updateUser 수행 중 오류 발생 - ${e.message}")
                false
            }
        }

        override fun deleteUser(id: String): Boolean {
            return try {
                super.deleteUser(id)
            } catch (e: Exception) {
                println("예외: deleteUser 수행 중 오류 발생 - ${e.message}")
                false
            }
        }
    }
}

// 데코레이터 패턴을 적용한 코드를 실행하는 메인 함수
fun main() {
    // 1. 기본 서비스 생성
    val baseUserService: UserService = Solution.SimpleUserServiceImpl()

    // 2. 다양한 데코레이터 조합
    // 로깅만 적용
    val loggedUserService = Solution.LoggingDecorator(baseUserService)

    // 성능 측정만 적용
    val performanceUserService = Solution.PerformanceDecorator(baseUserService)

    // 예외 처리만 적용
    val exceptionHandlingUserService = Solution.ExceptionHandlingDecorator(baseUserService)

    // 여러 데코레이터 조합(로깅 + 성능 측정)
    val loggedAndPerformanceUserService = Solution.LoggingDecorator(Solution.PerformanceDecorator(baseUserService))

    // 모든 데코레이터 조합(로깅 + 성능 측정 + 예외 처리)
    val fullyDecoratedUserService = Solution.LoggingDecorator(
        Solution.PerformanceDecorator(
            Solution.ExceptionHandlingDecorator(baseUserService)
        )
    )

    // 선택한 데코레이터 조합 사용
    println("===== 모든 데코레이터 적용 =====")
    val userService = fullyDecoratedUserService

    println("\n===== 사용자 생성 =====")
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

    println("\n===== 예외 처리 테스트 =====")
    try {
        // 예외를 발생시키는 코드 (여기서는 간단히 null 사용)
        val nullUser: User? = null
        userService.updateUser(nullUser!!) // 강제로 NPE 발생
    } catch (e: Exception) {
        println("예외가 발생했지만 메인 함수에서 처리됨: ${e.message}")
    }
}
