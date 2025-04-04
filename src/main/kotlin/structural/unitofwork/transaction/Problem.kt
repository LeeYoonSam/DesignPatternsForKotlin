package structural.unitofwork.transaction

import java.util.*

// Repository 인터페이스 정의
interface Repository<T, ID> {
    fun findById(id: ID): T?
    fun findAll(): List<T>
    fun save(entity: T): T
    fun delete(entity: T)
}

// 구체적인 Repository 구현
class UserRepository : Repository<User, UUID> {
    private val users = mutableMapOf<UUID, User>()

    override fun findById(id: UUID): User? = users[id]

    override fun findAll(): List<User> = users.values.toList()

    override fun save(entity: User): User {
        println("저장: $entity")
        users[entity.id] = entity
        return entity
    }

    override fun delete(entity: User) {
        println("삭제: $entity")
        users.remove(entity.id)
    }
}

class ProductRepository : Repository<Product, UUID> {
    private val products = mutableMapOf<UUID, Product>()

    override fun findById(id: UUID): Product? = products[id]

    override fun findAll(): List<Product> = products.values.toList()

    override fun save(entity: Product): Product {
        println("저장: $entity")
        products[entity.id] = entity
        return entity
    }

    override fun delete(entity: Product) {
        println("삭제: $entity")
        products.remove(entity.id)
    }
}

class OrderRepository : Repository<Order, UUID> {
    private val orders = mutableMapOf<UUID, Order>()

    override fun findById(id: UUID): Order? = orders[id]

    override fun findAll(): List<Order> = orders.values.toList()

    override fun save(entity: Order): Order {
        println("저장: $entity")
        orders[entity.id] = entity
        return entity
    }

    override fun delete(entity: Order) {
        println("삭제: $entity")
        orders.remove(entity.id)
    }
}

// 서비스 레이어
class OrderService(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository
) {
    // 주문 생성 로직
    fun createOrder(userId: UUID, orderItems: List<Pair<UUID, Int>>): Order {
        // 사용자 검증
        val user = userRepository.findById(userId) ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다.")

        // 새 주문 생성
        val order = Order(userId = userId)

        // 주문 항목 추가 및 재고 확인/업데이트
        for ((productId, quantity) in orderItems) {
            val product = productRepository.findById(productId) ?: throw IllegalArgumentException("상품을 찾을 수 없습니다.")

            // 재고 확인
            if (product.stockQuantity < quantity) {
                throw IllegalStateException("상품 '${product.name}'의 재고가 부족합니다.")
            }

            // 주문 항목 생성
            val orderItem = OrderItem(
                productId = productId,
                quantity = quantity,
                priceAtOrder = product.price
            )
            order.items.add(orderItem)

            // 재고 업데이트
            product.stockQuantity -= quantity
            productRepository.save(product)
        }

        // 주문 저장
        orderRepository.save(order)

        return order
    }

    // 주문 취소 로직
    fun cancelOrder(orderId: UUID) {
        val order = orderRepository.findById(orderId) ?: throw IllegalArgumentException("주문을 찾을 수 없습니다.")

        // 주문 상태가 이미 취소되었거나 배송된 경우 취소 불가
        if (order.status == OrderStatus.CANCELLED) {
            throw IllegalStateException("이미 취소된 주문입니다.")
        }
        if (order.status == OrderStatus.DELIVERED) {
            throw IllegalStateException("이미 배송된 주문은 취소할 수 없습니다.")
        }

        // 재고 원복
        for (item in order.items) {
            val product = productRepository.findById(item.productId) ?: continue
            product.stockQuantity += item.quantity
            productRepository.save(product)
        }

        // 주문 상태 변경
        order.status = OrderStatus.CANCELLED
        orderRepository.save(order)
    }
}

// 문제점을 시연하는 메인 함수
fun main() {
    try {
        // 리포지토리 생성
        val userRepository = UserRepository()
        val productRepository = ProductRepository()
        val orderRepository = OrderRepository()

        // 서비스 생성
        val orderService = OrderService(userRepository, productRepository, orderRepository)

        // 테스트 데이터 생성
        val user = userRepository.save(User(username = "test_user", email = "test@example.com"))
        val product1 = productRepository.save(Product(name = "노트북", price = 1200000.0, stockQuantity = 5))
        val product2 = productRepository.save(Product(name = "모니터", price = 350000.0, stockQuantity = 10))

        println("주문 생성 프로세스 시작...")

        // 문제 상황: 중간에 예외 발생하여 데이터 불일치 발생
        try {
            // 주문 생성 시도
            val orderItems = listOf(
                Pair(product1.id, 2),  // 노트북 2개
                Pair(product2.id, 3),  // 모니터 3개
                Pair(UUID.randomUUID(), 1)  // 존재하지 않는 상품 - 오류 발생
            )

            val order = orderService.createOrder(user.id, orderItems)
            println("주문 생성 완료: $order")
        } catch (e: Exception) {
            orderService.cancelOrder(user.id)
            println("주문 생성 실패: ${e.message}")
        }

        // 현재 상품 재고 확인 - 일부 상품의 재고가 감소했지만 주문은 완료되지 않은 상태
        println("노트북 현재 재고: ${productRepository.findById(product1.id)?.stockQuantity}")
        println("모니터 현재 재고: ${productRepository.findById(product2.id)?.stockQuantity}")

        // 데이터 불일치 문제 발생!
        println("문제 확인: 주문은 생성되지 않았지만 일부 상품의 재고가 감소했습니다!")

    } catch (e: Exception) {
        println("예상치 못한 오류 발생: ${e.message}")
    }
}