package structural.decorator.logging

data class User(val id: String, val name: String, val email: String)

// 사용자 서비스 인터페이스
interface UserService {
    fun getUser(id: String): User?
    fun createUser(user: User): Boolean
    fun updateUser(user: User): Boolean
    fun deleteUser(id: String): Boolean
}