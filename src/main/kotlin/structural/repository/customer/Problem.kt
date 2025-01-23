package structural.repository.customer

/**
 * 문제점
 * - 데이터 접근 로직이 비즈니스 로직과 강하게 결합됨
 * - 데이터 저장소 변경 시 전체 코드 수정 필요
 * - 테스트가 어려움
 * - 코드 중복 발생
 */
class Problem {

    // 데이터 접근 로직이 비즈니스 로직과 혼재된 고객 관리 시스템
    class CustomerService {
        private val customers = mutableMapOf<String, Customer>()

        fun createCustomer(customer: Customer) {
            // 데이터베이스 연결 로직
            customers[customer.id] = customer
        }

        fun getCustomer(id: String): Customer? {
            // 직접적인 데이터 접근
            return customers[id]
        }

        fun updateCustomer(customer: Customer) {
            // 검증 로직과 데이터 접근 로직이 혼재
            if (!customers.containsKey(customer.id)) {
                throw IllegalArgumentException("Customer not found")
            }
            customers[customer.id] = customer
        }

        fun deleteCustomer(id: String) {
            customers.remove(id)
        }

        fun findCustomersByCity(city: String): List<Customer> {
            // 비즈니스 로직과 데이터 필터링이 혼재
            return customers.values.filter { it.city == city }
        }
    }

    data class Customer(
        val id: String,
        val name: String,
        val email: String,
        val city: String
    )
}

fun main() {
    val customerService = Problem.CustomerService()
    val customer1 = Problem.Customer("1", "Customer1", "customer1@test.com", "Seoul")
    val customer2 = Problem.Customer("2", "Customer2", "customer2@test.com", "Tokyo")
    customerService.createCustomer(customer1)
    customerService.createCustomer(customer2)
    println("Customer1 = ${customerService.getCustomer("1")}")
    println("Customer2 = ${customerService.getCustomer("2")}")
    customerService.updateCustomer(customer1.copy(name = "Customer-1"))
    println("Customer1 Updated = ${customerService.getCustomer("1")}")
    println("find customers by city: ${customerService.findCustomersByCity("Tokyo")}")
    customerService.deleteCustomer("2")
    println("find customers by city: ${customerService.findCustomersByCity("Tokyo")}")

}