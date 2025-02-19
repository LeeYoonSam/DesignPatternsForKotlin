package architecture.bff.multiclient

class Problem {
    // Generic backend service that servers all clients
    class ProductService {
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
}

fun main() {
    val productService = Problem.ProductService()

    // Mobile client request
    println("=== Mobile Client Request ===")
    val mobileResult = productService.getProductDetails("prod123")
    // Mobile client needs to process and filter unnecessary data
    println("Mobile display: ${mobileResult.name}, ${mobileResult.price}")
    println("Mobile image: ${mobileResult.images.firstOrNull()}")

    // Desktop web client request
    println("\n=== Desktop Web Client Request ===")
    val webResult = productService.getProductDetails("prod123")
    // Web client receives full data but needs different image formats
    println("Web display: Full details with all ${webResult.images.size} images")

    // IoT device request
    println("\n=== IoT Device Request ===")
    val iotResult = productService.getProductDetails("prod123")
    // IoT device only needs inventory status but receives full product details
    println("IoT display: Available: ${iotResult.inventory.available}")
}