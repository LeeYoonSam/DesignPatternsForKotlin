package behavioral.interceptor.http

data class Request(
    val path: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?
)