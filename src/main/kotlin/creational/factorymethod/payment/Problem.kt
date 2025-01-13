package creational.factorymethod.payment

/**
 * 문제점
 * - 객체 생성과 사용 코드의 결합
 * - 객체 생성 로직 중복
 * - 조건문 기반 객체 생성
 * - 테스트 어려움
 * - 확장 시 코드 수정 필요
 */
class Problem {
    class PaymentProcessor {
        fun processPayment(type: String, amount: Double) {
            when (type) {
                "CREDIT" -> {
                    val payment = CreditCardPayment()
                    payment.processPayment(amount)
                }
                "BANK" -> {
                    val payment = BankTransferPayment()
                    payment.processPayment(amount)
                }
                // 새로운 결제 방식 추가 시 여기에 계속 조건문 추가 필요
            }
        }
    }

    class CreditCardPayment {
        fun processPayment(amount: Double) {
            println("신용카드 결제 처리: ${amount}원")
        }
    }

    class BankTransferPayment {
        fun processPayment(amount: Double) {
            println("계좌이체 결제 처리: ${amount}원")
        }
    }
}

fun main() {
    val processor = Problem.PaymentProcessor()
    processor.processPayment("CREDIT", 50000.0)
    processor.processPayment("BANK", 30000.0)
}