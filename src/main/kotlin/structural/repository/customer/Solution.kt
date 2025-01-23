package structural.repository.customer

/**
 * 해결책: Repository 패턴을 사용한 고객 관리 시스템
 * - 데이터 접근 계층을 추상화
 * - 비즈니스 로직과 데이터 접근 로직을 분리
 * - 테스트 용이성 향상
 * - 다양한 데이터 저장소 지원 가능
 */
class Solution {
    // Repository 인터페이스 정의
    interface CustomerRepository {
        fun save(customer: Customer): Customer
        fun findById(id: String): Customer?
        fun delete(id: String)
        fun findAll(): List<Customer>
        fun findByCity(city: String): List<Customer>
    }

    // In-Memory 구현체
    class InMemoryCustomerRepository : CustomerRepository {
        private val customers = mutableMapOf<String, Customer>()

        override fun save(customer: Customer): Customer {
            customers[customer.id] = customer
            return customer
        }

        override fun findById(id: String): Customer? {
            return customers[id]
        }

        override fun delete(id: String) {
            customers.remove(id)
        }

        override fun findAll(): List<Customer> {
            return customers.values.toList()
        }

        override fun findByCity(city: String): List<Customer> {
            return customers.values.filter { it.city == city }
        }
    }

    // 비즈니스 로직을 담당하는 서비스 계층
    class CustomerService(private val customerRepository: CustomerRepository) {
        fun createCustomer(customer: Customer): Customer {
            validateCustomer(customer)
            return customerRepository.save(customer)
        }

        fun getCustomer(id: String): Customer? {
            return customerRepository.findById(id)
        }

        fun updateCustomer(customer: Customer): Customer {
            validateCustomer(customer)
            getCustomer(customer.id) ?: throw IllegalArgumentException("Customer not found")
            return customerRepository.save(customer)
        }

        fun deleteCustomer(id: String) {
            getCustomer(id) ?: throw IllegalArgumentException("Customer not found")
            customerRepository.delete(id)
        }

        fun findCustomersByCity(city: String): List<Customer> {
            return customerRepository.findByCity(city)
        }

        private fun validateCustomer(customer: Customer) {
            require(customer.email.contains("@")) { "Invalid email format" }
            require(customer.name.isNotBlank()) { "Name cannot be blank" }
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
    // Repository 패턴 사용 예제
    val customerRepository = Solution.InMemoryCustomerRepository()
    val customerService = Solution.CustomerService(customerRepository)

    // 고객 생성
    val customer = Solution.Customer(
        id = "1",
        name = "John Doe",
        email = "john@example.com",
        city = "New York"
    )

    println("Creating customer...")
    customerService.createCustomer(customer)

    println("Finding customer...")
    val foundCustomer = customerService.getCustomer("1")
    println("Found customer: $foundCustomer")

    println("Finding customers by city...")
    val customersInNewYork = customerService.findCustomersByCity("New York")
    println("Customers in New York: $customersInNewYork")

    println("Updating customer...")
    customerService.updateCustomer(customer.copy(name = "John Doe 2"))
    println("Updated customer: ${customerService.getCustomer("1")}")

    println("Deleting customer...")
    customerService.deleteCustomer("1")
    println("Customer deleted")
}