package architecture.cqrs.inventory

/**
 * 해결책: CQRS 패턴을 사용한 재고 관리 시스템
 */
class Solution {
    // 명령(Command) 모델
    data class ProductCommand(
        val id: String,
        val name: String,
        val quantity: Int,
        val price: Double
    )

    // 조회(Query) 모델
    data class ProductQuery(
        val id: String,
        val name: String,
        val quantity: Int,
        val price: Double,
        val value: Double,
        val status: StockStatus
    )

    enum class StockStatus {
        IN_STOCK, LOW_STOCK, OUT_OF_STOCK
    }

    // 이벤트 정의
    sealed class InventoryEvent {
        data class ProductCreated(
            val id: String,
            val name: String,
            val quantity: Int,
            val price: Double
        ) : InventoryEvent()

        data class StockAdjusted(
            val id: String,
            val quantity: Int
        ) : InventoryEvent()
    }

    // 명령 처리기
    class CommandHandler(private val eventStore: EventStore) {
        fun handleCreateProduct(command: ProductCommand) {
            val event = InventoryEvent.ProductCreated(
                command.id,
                command.name,
                command.quantity,
                command.price
            )
            eventStore.store(event)
        }

        fun handleAdjustStock(productId: String, quantity: Int) {
            val event = InventoryEvent.StockAdjusted(productId, quantity)
            eventStore.store(event)
        }
    }

    // 조회 처리기
    class QueryHandler(private val readModel: ReadModel) {
        fun getProduct(productId: String): ProductQuery? {
            return readModel.getProduct(productId)
        }

        fun getStockReport(): List<ProductQuery> {
            return readModel.getAllProducts()
        }

        fun getLowStockProducts(): List<ProductQuery> {
            return readModel.getAllProducts()
                .filter { it.status == StockStatus.LOW_STOCK}
        }
    }

    // 이벤트 저장소
    class EventStore {
        private val events = mutableListOf<InventoryEvent>()
        private val listeners = mutableListOf<(InventoryEvent) -> Unit>()

        fun store(event: InventoryEvent) {
            events.add(event)
            listeners.forEach { it(event) }
        }

        fun addListener(listener: (InventoryEvent) -> Unit) {
            listeners.add(listener)
        }
    }

    // 읽기 모델
    class ReadModel {
        private val products = mutableMapOf<String, ProductQuery>()

        fun handleEvent(event: InventoryEvent) {
            when (event) {
                is InventoryEvent.ProductCreated -> {
                    products[event.id] = ProductQuery(
                        id = event.id,
                        name = event.name,
                        quantity = event.quantity,
                        price =  event.price,
                        value = event.price * event.quantity,
                        status = calculateStatus(event.quantity)
                    )
                }
                is InventoryEvent.StockAdjusted -> {
                    products[event.id]?.let { product ->
                        val newQuantity = product.quantity + event.quantity
                        products[event.id] = product.copy(
                            quantity =  newQuantity,
                            value = product.price * newQuantity,
                            status = calculateStatus(newQuantity)
                        )
                    }
                }
            }
        }

        private fun calculateStatus(quantity: Int): StockStatus = when {
            quantity <= 0 -> StockStatus.OUT_OF_STOCK
            quantity < 10 -> StockStatus.LOW_STOCK
            else -> StockStatus.IN_STOCK
        }

        fun getProduct(productId: String): ProductQuery? = products[productId]
        fun getAllProducts(): List<ProductQuery> = products.values.toList()
    }

    // CQRS 파사드
    class InventorySystem {
        private val eventStore = EventStore()
        private val readModel = ReadModel()
        private val commandHandler = CommandHandler(eventStore)
        private val queryHandler = QueryHandler(readModel)

        init {
            eventStore.addListener { event ->
                readModel.handleEvent(event)
            }
        }

        // 명령 메서드
        fun createProduct(command: ProductCommand) {
            commandHandler.handleCreateProduct(command)
        }

        fun adjustStock(productId: String, quantity: Int) {
            commandHandler.handleAdjustStock(productId, quantity)
        }

        // 조회 메서드
        fun getProduct(productId: String): ProductQuery? {
            return queryHandler.getProduct(productId)
        }

        fun getStockReport(): List<ProductQuery> {
            return queryHandler.getStockReport()
        }

        fun getLowStockProducts(): List<ProductQuery> {
            return queryHandler.getLowStockProducts()
        }
    }
}

fun main() {
    println("CQRS Implementation:")
    // CQRS 구현
    val cqrsSystem = Solution.InventorySystem()

    // 제품 생성 (명령)
    cqrsSystem.createProduct(
        Solution.ProductCommand("1", "Product A", 10, 100.0)
    )

    // 재고 조정 (명령)
    cqrsSystem.adjustStock("1", -5)

    // 재고 조회 (쿼리)
    println("Product Details: ${cqrsSystem.getProduct("1")}")
    println("Stock Report: ${cqrsSystem.getStockReport()}")
    println("Low Stock Products: ${cqrsSystem.getLowStockProducts()}")
}