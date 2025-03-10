package structural.decorator.authentication

// User entity
data class User(val id: String, val username: String, val password: String = "")

// Represents authentication result
data class AuthResult(
    val success: Boolean,
    val user: User? = null,
    val token: String? = null,
    val message: String = ""
)