package behavioral.nullobject.authorization

import behavioral.nullobject.authorization.Problem.User

// 사용자 권한 인터페이스
interface Permission {
    fun hasReadAccess(): Boolean
    fun hasWriteAccess(): Boolean
    fun hasDeleteAccess(): Boolean
    fun getPermissionLevel(): Int
}

// 관리자 권한
class AdminPermission : Permission {
    override fun hasReadAccess() = true
    override fun hasWriteAccess() = true
    override fun hasDeleteAccess() = true
    override fun getPermissionLevel() = 3

    override fun toString() = "AdminPermission"
}

// 일반 사용자 권한
class UserPermission : Permission {
    override fun hasReadAccess() = true
    override fun hasWriteAccess() = true
    override fun hasDeleteAccess() = false
    override fun getPermissionLevel() = 2

    override fun toString() = "UserPermission"
}

// 게스트 권한
class GuestPermission : Permission {
    override fun hasReadAccess() = true
    override fun hasWriteAccess() = false
    override fun hasDeleteAccess() = false
    override fun getPermissionLevel() = 1

    override fun toString() = "GuestPermission"
}

// 사용자 저장소
class UserRepository {
    private val users = mapOf(
        "1" to User("1", "Admin Smith", "admin"),
        "2" to User("2", "John Doe", "user"),
        "3" to User("3", "Guest User", "guest"),
        "4" to User("4", "Unknown User", "unknown")
    )

    fun findById(id: String): User? {
        return users[id]
    }
}