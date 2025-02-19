package architecture.bff.multiclient

data class ProductDetails(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val specifications: Map<String, String>,
    val images: List<String>,
    val inventory: InventoryDetails
)

data class InventoryDetails(
    val available: Int,
    val reserved: Int,
    val inTransit: Int
)