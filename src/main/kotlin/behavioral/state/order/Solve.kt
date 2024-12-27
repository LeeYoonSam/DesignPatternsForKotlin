package behavioral.state.order

class Solve {
    interface OrderState {
        fun processOrder(order: Order)
        fun cancelOrder(order: Order)
        fun getDescription(): String
    }

    class Order {
        private var currentState: OrderState = CreatedState()
        private var orderData: String = ""

        fun setState(state: OrderState) {
            currentState = state
        }

        fun processOrder() {
            currentState.processOrder(this)
        }

        fun cancelOrder() {
            currentState.cancelOrder(this)
        }

        fun getStateDescription(): String = currentState.getDescription()
    }

    // Concrete States
    class CreatedState : OrderState {
        override fun processOrder(order: Order) {
            println("결제를 진행합니다...")
            if (validatedPayment()) {
                order.setState(PaidState())
                println("결제가 완료 되었습니다.")
            }
        }

        override fun cancelOrder(order: Order) {
            order.setState(CancelledState())
            println("주문이 취소되었습니다.")
        }

        override fun getDescription(): String = "주문 생성됨"

        private fun validatedPayment() = true
    }

    class PaidState : OrderState {
        override fun processOrder(order: Order) {
            println("재고를 확인합니다...")
            if (checkInventory()) {
                order.setState(ProcessingState())
                println("주문 처리를 시작합니다.")
            }
        }

        override fun cancelOrder(order: Order) {
            order.setState(RefundState())
            println("환불을 진행합니다.")
        }

        override fun getDescription(): String = "결제 완료"

        private fun checkInventory() = true
    }

    class ProcessingState : OrderState {
        override fun processOrder(order: Order) {
            println("발송 준비를 진행합니다...")
            if (prepareForShipment()) {
                order.setState(ShippedState())
                println("주문이 발송되었습니다.")
            }
        }

        override fun cancelOrder(order: Order) {
            println("처리 중인 주문은 취소할 수 없습니다.")
        }

        override fun getDescription(): String = "처리 중"

        private fun prepareForShipment() = true
    }

    class ShippedState : OrderState {
        override fun processOrder(order: Order) {
            println("배송 상태를 확인합니다...")
            if (confirmDelivery()) {
                order.setState(DeliveredState())
                println("배송이 완료되었습니다.")
            }
        }

        override fun cancelOrder(order: Order) {
            println("배송 중인 주문은 취소할 수 없습니다.")
        }

        override fun getDescription(): String = "배송 중"

        private fun confirmDelivery() = true
    }

    class DeliveredState : OrderState {
        override fun processOrder(order: Order) {
            println("이미 배송이 완료된 주문입니다.")
        }

        override fun cancelOrder(order: Order) {
            println("배송 완료된 주문은 취소할 수 없습니다.")
        }

        override fun getDescription(): String = "배송 완료"
    }

    class CancelledState : OrderState {
        override fun processOrder(order: Order) {
            println("취소된 주문은 처리할 수 없습니다.")
        }

        override fun cancelOrder(order: Order) {
            println("이미 취소된 주문입니다.")
        }

        override fun getDescription(): String = "주문 취소"
    }

    class RefundState: OrderState {
        override fun processOrder(order: Order) {
            println("환불 처리 중인 주문입니다.")
        }

        override fun cancelOrder(order: Order) {
            println("이미 환불 처리 중입니다.")
        }

        override fun getDescription(): String = "환불 진행 중"
    }
}

fun main() {
    val order = Solve.Order()

    println("\n1. 정상적인 주문 처리 흐름:")
    println("현재 상태: ${order.getStateDescription()}")
    order.processOrder() // Created -> Paid
    println("현재 상태: ${order.getStateDescription()}")
    order.processOrder() // Paid -> Processing
    println("현재 상태: ${order.getStateDescription()}")
    order.processOrder() // Processing -> Shipped
    println("현재 상태: ${order.getStateDescription()}")
    order.processOrder() // Shipped -> Delivered
    println("현재 상태: ${order.getStateDescription()}")

    println("\n2. 주문 취소 시나리오:")
    val orderToCancel = Solve.Order()
    println("현재 상태: ${orderToCancel.getStateDescription()}")
    orderToCancel.cancelOrder() // Created -> Cancelled
    println("현재 상태: ${orderToCancel.getStateDescription()}")
    orderToCancel.processOrder() // 취소된 주문 처리 시도
}