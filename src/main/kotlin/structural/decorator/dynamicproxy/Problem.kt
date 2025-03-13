package structural.decorator.dynamicproxy

fun main() {
    println("=== 문제 상황 시연 ===")

    // 사용자 서비스 사용
    val userService = UserServiceImpl()

    try {
        println("사용자 생성 중...")
        val user = userService.createUser("홍길동", "hong@example.com")
        println("생성된 사용자: $user")

        println("\n사용자 조회 중...")
        val foundUser = userService.getUser(user.id)
        println("조회된 사용자: $foundUser")

        println("\n사용자 업데이트 중...")
        val updatedUser = userService.updateUser(user.id, "홍길동2", "hong2@example.com")
        println("업데이트된 사용자: $updatedUser")

        println("\n존재하지 않는 사용자 조회 시도...")
        userService.getUser(999) // 예외 발생
    } catch (e: Exception) {
        println("예외 발생: ${e.message}")
    }

    println("\n=== 문제점 ===")
    println("1. 각 서비스 메서드 호출 시간을 측정하고 싶지만, 매번 코드를 수정해야 함")
    println("2. 모든 메서드 호출과 오류를 로깅하려면 각 메서드마다 로깅 코드를 추가해야 함")
    println("3. 인증/인가 체크를 모든 메서드에 추가하려면 코드 중복이 발생함")
    println("4. 여러 서비스(UserService, OrderService 등)에 동일한 기능을 추가하려면 모든 클래스를 수정해야 함")
    println("5. 기존 서비스 코드를 변경하지 않고 이러한 기능을 추가하는 방법이 필요함")
}