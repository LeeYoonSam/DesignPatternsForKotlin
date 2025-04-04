package structural.unitofwork.transaction

import java.util.*

class Solution {
    // Unit of Work 패턴 적용을 위한 인터페이스
    interface UnitOfWork {
        fun registerNew(entity: Any)
        fun registerModified(entity: Any)
        fun registerDeleted(entity: Any)
        fun commit()
        fun rollback()
    }

    // 구체적인 Unit of Work 구현
    class TransactionUnitOfWork(
        private val userRepository: GenericRepository<User, UUID>,
        private val productRepository: GenericRepository<Product, UUID>,
        private val orderRepository: GenericRepository<Order, UUID>
    ) : UnitOfWork {
        private val newEntities = mutableListOf<Any>()
        private val modifiedEntities = mutableListOf<Any>()
        private val deletedEntities = mutableListOf<Any>()
        private val snapshotsByEntity = mutableMapOf<Any, Any>()

        override fun registerNew(entity: Any) {
            if (!newEntities.contains(entity) && !modifiedEntities.contains(entity) && !deletedEntities.contains(entity)) {
                newEntities.add(entity)
            }
        }

        override fun registerModified(entity: Any) {
            if (deletedEntities.contains(entity)) {
                return
            }

            if (!newEntities.contains(entity)) {
                if (!modifiedEntities.contains(entity)) {
                    // 변경 전 상태 스냅샷 저장
                    if (!snapshotsByEntity.containsKey(entity)) {
                        snapshotsByEntity[entity] = entity.deepCopy()
                    }
                    modifiedEntities.add(entity)
                }
            }
        }

        override fun registerDeleted(entity: Any) {
            if (newEntities.remove(entity)) {
                return
            }

            modifiedEntities.remove(entity)

            if (!deletedEntities.contains(entity)) {
                deletedEntities.add(entity)
            }
        }

        override fun commit() {
            println("트랜잭션 커밋 시작...")

            try {
                // 새 엔티티 저장
                for (entity in newEntities) {
                    when (entity) {
                        is User -> userRepository.save(entity)
                        is Product -> productRepository.save(entity)
                        is Order -> orderRepository.save(entity)
                    }
                }

                // 변경된 엔티티 저장
                for (entity in modifiedEntities) {
                    when (entity) {
                        is User -> userRepository.save(entity)
                        is Product -> productRepository.save(entity)
                        is Order -> orderRepository.save(entity)
                    }
                }

                // 삭제된 엔티티 제거
                for (entity in deletedEntities) {
                    when (entity) {
                        is User -> userRepository.delete(entity)
                        is Product -> productRepository.delete(entity)
                        is Order -> orderRepository.delete(entity)
                    }
                }

                println("트랜잭션 커밋 완료!")
                clear()
            } catch (e: Exception) {
                println("트랜잭션 커밋 실패: ${e.message}")
                rollback()
                throw e
            }
        }

        override fun rollback() {
            println("트랜잭션 롤백 시작...")

            // 새 엔티티는 무시하면 됨

            // 변경된 엔티티는 원래 상태로 복원
            for (entity in modifiedEntities) {
                val snapshot = snapshotsByEntity[entity] ?: continue

                when {
                    entity is User && snapshot is User -> {
                        entity.username = snapshot.username
                        entity.email = snapshot.email
                    }
                    entity is Product && snapshot is Product -> {
                        entity.name = snapshot.name
                        entity.price = snapshot.price
                        entity.stockQuantity = snapshot.stockQuantity
                    }
                    entity is Order && snapshot is Order -> {
                        entity.status = snapshot.status
                        entity.items.clear()
                        entity.items.addAll(snapshot.items)
                    }
                }
            }

            println("트랜잭션 롤백 완료!")
            clear()
        }

        private fun clear() {
            newEntities.clear()
            modifiedEntities.clear()
            deletedEntities.clear()
            snapshotsByEntity.clear()
        }

        // 깊은 복사 확장 함수
        private fun Any.deepCopy(): Any {
            return when (this) {
                is User -> this.copy()
                is Product -> this.copy()
                is Order -> this.copy(items = this.items.map { it.copy() }.toMutableList())
                else -> this
            }
        }
    }

    // 개선된 제네릭 Repository 인터페이스
    interface GenericRepository<T, ID> {
        fun findById(id: ID): T?
        fun findAll(): List<T>
        fun save(entity: T): T
        fun delete(entity: T)
    }

    // 구체적인 Repository 구현
    class GenericUserRepository : GenericRepository<User, UUID> {
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

    class GenericProductRepository : GenericRepository<Product, UUID> {
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

    class GenericOrderRepository : GenericRepository<Order, UUID> {
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

    // 개선된 서비스 레이어 - Unit of Work 패턴 적용
    class TransactionalOrderService(
        private val userRepository: GenericRepository<User, UUID>,
        private val productRepository: GenericRepository<Product, UUID>,
        private val orderRepository: GenericRepository<Order, UUID>,
        private val unitOfWorkFactory: () -> UnitOfWork
    ) {
        // 주문 생성 로직
        fun createOrder(userId: UUID, orderItems: List<Pair<UUID, Int>>): Order {
            val unitOfWork = unitOfWorkFactory()

            try {
                // 사용자 검증
                val user = userRepository.findById(userId) ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다.")

                // 새 주문 생성
                val order = Order(userId = userId)
                unitOfWork.registerNew(order)

                // 주문 항목 추가 및 재고 확인/업데이트
                for ((productId, quantity) in orderItems) {
                    val product = productRepository.findById(productId) ?: throw IllegalArgumentException("상품 ID: $productId 를 찾을 수 없습니다.")

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
                    unitOfWork.registerModified(product)
                }

                // 모든 작업이 성공적으로 완료되면 커밋
                unitOfWork.commit()

                return order
            } catch (e: Exception) {
                // 오류 발생 시 롤백
                unitOfWork.rollback()
                throw e
            }
        }

        // 주문 취소 로직
        fun cancelOrder(orderId: UUID) {
            val unitOfWork = unitOfWorkFactory()

            try {
                val order = orderRepository.findById(orderId) ?: throw IllegalArgumentException("주문을 찾을 수 없습니다.")

                // 주문 상태가 이미 취소되었거나 배송된 경우 취소 불가
                if (order.status == OrderStatus.CANCELLED) {
                    throw IllegalStateException("이미 취소된 주문입니다.")
                }
                if (order.status == OrderStatus.DELIVERED) {
                    throw IllegalStateException("이미 배송된 주문은 취소할 수 없습니다.")
                }

                // 주문 상태 변경
                order.status = OrderStatus.CANCELLED
                unitOfWork.registerModified(order)

                // 재고 원복
                for (item in order.items) {
                    val product = productRepository.findById(item.productId) ?: continue
                    product.stockQuantity += item.quantity
                    unitOfWork.registerModified(product)
                }

                // 모든 작업이 성공적으로 완료되면 커밋
                unitOfWork.commit()
            } catch (e: Exception) {
                // 오류 발생 시 롤백
                unitOfWork.rollback()
                throw e
            }
        }
    }
}

// 해결책을 시연하는 메인 함수
fun main() {
    try {
        // 리포지토리 생성
        val userRepository = Solution.GenericUserRepository()
        val productRepository = Solution.GenericProductRepository()
        val orderRepository = Solution.GenericOrderRepository()

        // Unit of Work 팩토리 함수
        val unitOfWorkFactory = {
            Solution.TransactionUnitOfWork(userRepository, productRepository, orderRepository)
        }

        // 서비스 생성
        val orderService = Solution.TransactionalOrderService(
            userRepository, productRepository, orderRepository, unitOfWorkFactory
        )

        // 테스트 데이터 생성
        val user = userRepository.save(User(username = "test_user", email = "test@example.com"))
        val product1 = productRepository.save(Product(name = "노트북", price = 1200000.0, stockQuantity = 5))
        val product2 = productRepository.save(Product(name = "모니터", price = 350000.0, stockQuantity = 10))

        println("초기 상품 재고 상태:")
        println("노트북 현재 재고: ${productRepository.findById(product1.id)?.stockQuantity}")
        println("모니터 현재 재고: ${productRepository.findById(product2.id)?.stockQuantity}")

        println("\n주문 생성 프로세스 시작...")

        // 문제 상황: 중간에 예외 발생하여 데이터 불일치 발생 시도
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
            println("주문 생성 실패: ${e.message}")
        }

        // 현재 상품 재고 확인 - Unit of Work 패턴으로 인해 재고가 원래대로 유지됨
        println("\n주문 실패 후 상품 재고 상태:")
        println("노트북 현재 재고: ${productRepository.findById(product1.id)?.stockQuantity}")
        println("모니터 현재 재고: ${productRepository.findById(product2.id)?.stockQuantity}")

        println("\n성공 케이스 시도...")

        // 성공 케이스
        try {
            // 주문 생성 시도
            val orderItems = listOf(
                Pair(product1.id, 2),  // 노트북 2개
                Pair(product2.id, 3)   // 모니터 3개
            )

            val order = orderService.createOrder(user.id, orderItems)
            println("주문 생성 완료: $order")

            // 현재 상품 재고 확인 - 성공적으로 재고가 감소함
            println("\n주문 성공 후 상품 재고 상태:")
            println("노트북 현재 재고: ${productRepository.findById(product1.id)?.stockQuantity}")
            println("모니터 현재 재고: ${productRepository.findById(product2.id)?.stockQuantity}")

            // 주문 취소 테스트
            println("\n주문 취소 시도...")
            orderService.cancelOrder(order.id)

            // 주문 취소 후 재고 확인 - 재고가 원복됨
            println("\n주문 취소 후 상품 재고 상태:")
            println("노트북 현재 재고: ${productRepository.findById(product1.id)?.stockQuantity}")
            println("모니터 현재 재고: ${productRepository.findById(product2.id)?.stockQuantity}")

        } catch (e: Exception) {
            println("오류 발생: ${e.message}")
        }

    } catch (e: Exception) {
        println("예상치 못한 오류 발생: ${e.message}")
    }
}