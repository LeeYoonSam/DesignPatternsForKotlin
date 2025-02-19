package architecture.bff.multiclient

class Solution {
    // Core backend service
    class CoreProductService {
        fun getProductDetails(id: String): ProductDetails {
            return ProductDetails(
                id = id,
                name = "Sample Product",
                description = "Full description of the product...",
                price = 99.99,
                specifications = mapOf(
                    "weight" to "2.5kg",
                    "dimensions" to "30x20x10cm",
                    "material" to "aluminum"
                ),
                images = listOf(
                    "full_size_1.jpg",
                    "full_size_2.jpg",
                    "full_size_3.jpg"
                ),
                inventory = InventoryDetails(
                    available = 150,
                    reserved = 30,
                    inTransit = 20
                )
            )
        }
    }

    // Mobile BFF
    class MobileProductBFF(private val coreService: CoreProductService) {
        fun getProductDetails(id: String): MobileProductView {
            val product = coreService.getProductDetails(id)
            return MobileProductView(
                id = product.id,
                name = product.name,
                price = product.price,
                thumbnailImage = optimizeImageForMobile(product.images.firstOrNull())
            )
        }

        private fun optimizeImageForMobile(image: String?): String {
            return image?.replace("full_size", "mobile_optimized") ?: "default_mobile.jpg"
        }
    }

    // Web BFF
    class WebProductBFF(private val coreService: CoreProductService) {
        fun getProductDetails(id: String): WebProductView {
            val product = coreService.getProductDetails(id)
            return WebProductView(
                id = product.id,
                name = product.name,
                description = product.description,
                price = product.price,
                specifications = product.specifications,
                images = optimizeImagesForWeb(product.images),
                inventory = product.inventory
            )
        }

        private fun optimizeImagesForWeb(images: List<String>): WebImageSet {
            return WebImageSet(
                thumbnails = images.map { it.replace("full_size", "thumb") },
                regular = images.map { it.replace("full_size", "regular") },
                fullSize = images
            )
        }
    }

    // IoT BFF
    class IoTProductBFF(private val coreService: CoreProductService) {
        fun getInventoryStatus(id: String): IoTInventoryStatus {
            val product = coreService.getProductDetails(id)
            return IoTInventoryStatus(
                productId = product.id,
                available = product.inventory.available,
                needsRestock = product.inventory.available < 100
            )
        }
    }

    // Client-specific view models
    data class MobileProductView(
        val id: String,
        val name: String,
        val price: Double,
        val thumbnailImage: String
    )

    data class WebProductView(
        val id: String,
        val name: String,
        val description: String,
        val price: Double,
        val specifications: Map<String, String>,
        val images: WebImageSet,
        val inventory: InventoryDetails
    )

    data class WebImageSet(
        val thumbnails: List<String>,
        val regular: List<String>,
        val fullSize: List<String>
    )

    data class IoTInventoryStatus(
        val productId: String,
        val available: Int,
        val needsRestock: Boolean
    )
}

fun main() {
    val coreService = Solution.CoreProductService()

    // Mobile client request through Mobile BFF
    println("=== Mobile Client Request ===")
    val mobileBFF = Solution.MobileProductBFF(coreService)
    val mobileResult = mobileBFF.getProductDetails("prod123")
    println("Mobile display: $mobileResult")

    // Desktop web client request through Web BFF
    println("\n=== Desktop Web Client Request ===")
    val webBFF = Solution.WebProductBFF(coreService)
    val webResult = webBFF.getProductDetails("prod123")
    println("Web display: $webResult")

    // IoT device request through IoT BFF
    println("\n=== IoT Device Request ===")
    val iotBFF = Solution.IoTProductBFF(coreService)
    val iotResult = iotBFF.getInventoryStatus("prod123")
    println("IoT display: $iotResult")
}