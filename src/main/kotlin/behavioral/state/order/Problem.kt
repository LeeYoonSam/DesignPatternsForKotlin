package behavioral.state.order

/**
 * 문제점
 * - 복잡한 조건문(if-else)의 남용
 * - 상태 관련 코드의 중복
 * - 새로운 상태 추가 시 코드 수정 필요
 * - 상태 전이 로직의 불명확성
 */
class Problem {
    // // 문제가 있는 코드: 조건문을 사용한 상태 관리
    class OrderWithoutState {
        private var status = "CREATED"

        fun processOrder() {
            when (status) {
                "CREATED" -> {
                    if (validatePayment()) {
                        status = "PAID"
                        println("주문이 결제되었습니다.")
                    }
                }
                "PAID" -> {
                    if (checkInventory()) {
                        status = "PROCESSING"
                        println("주문을 처리중입니다.")
                    }
                }
                "PROCESSING" -> {
                    if (prepareForShipment()) {
                        status = "SHIPPED"
                        println("주문이 발송되었습니다.")
                    }
                }
                "SHIPPED" -> {
                    if (confirmDelivery()) {
                        status = "DELIVERED"
                        println("배송이 완료되었습니다.")
                    }
                }
                else -> println("잘못된 상태입니다.")
            }
        }

        private fun validatePayment() = true
        private fun checkInventory() = true
        private fun prepareForShipment() = true
        private fun confirmDelivery() = true
    }
}

fun main() {
    val order = Problem.OrderWithoutState()
    order.processOrder()
    order.processOrder()
    order.processOrder()
    order.processOrder()
    order.processOrder()
}