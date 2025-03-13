package structural.decorator.dynamicproxy

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.system.measureTimeMillis

class Solution {
    // 메서드 실행 전후에 동작을 추가하는 인터셉터 인터페이스
    interface MethodInterceptor {
        fun intercept(obj: Any, method: Method, args: Array<out Any?>?, chain: () -> Any?): Any?
    }

    // 로킹 인터셉터
    class LoggingInterceptor : MethodInterceptor {
        override fun intercept(obj: Any, method: Method, args: Array<out Any?>?, chain: () -> Any?): Any? {
            println("[로깅] ${obj.javaClass.interfaces[0].simpleName}.${method.name} 메서드 호출 시작")
            try {
                val result = chain()
                println("[로깅] ${obj.javaClass.interfaces[0].simpleName}.${method.name} 메서드 호출 성공: 결과 = $result")
                return result
            } catch (e: Exception) {
                println("[로깅] ${obj.javaClass.interfaces[0].simpleName}.${method.name} 메서드 호출 실패: ${e.message}")
                throw e
            }
        }
    }

    // 성능 측정 인터셉터
    @Suppress("UNREACHABLE_CODE")
    class PerformanceInterceptor : MethodInterceptor {
        override fun intercept(obj: Any, method: Method, args: Array<out Any?>?, chain: () -> Any?): Any? {
            val startTime = System.currentTimeMillis()

            try {
                // 메서드 실행
                return chain()
            } finally {
                val endTime = System.currentTimeMillis()
                val executionTime = endTime - startTime
                println("[성능] ${obj.javaClass.interfaces[0].simpleName}.${method.name} 메서드 실행 시간: ${executionTime}ms")
            }
        }
    }

    // 예외 처리 인터셉터
    class ExceptionHandlingInterceptor : MethodInterceptor {
        override fun intercept(obj: Any, method: Method, args: Array<out Any?>?, chain: () -> Any?): Any? {
            try {
                return chain()
            } catch (e: NoSuchElementException) {
                println("[예외처리] 리소스를 찾을 수 없습니다: ${e.message}")
                throw e
            } catch (e: Exception) {
                println("[예외처리] 예상치 못한 오류 발생: ${e.message}")
                throw RuntimeException("서비스 처리 중 오류가 발생했습니다", e)
            }
        }
    }


    // 동적 프록시 생성을 위한 팩토리 클래스
    class ProxyFactory {
        private val interceptors = mutableListOf<MethodInterceptor>()

        fun addInterceptor(interceptor: MethodInterceptor) {
            interceptors.add(interceptor)
        }

        fun <T : Any> create(serviceClass: Class<T>, target: T): T {
            val handler = ProxyInvocationHandler(target, interceptors)

            @Suppress("UNCHECKED_CAST")
            return Proxy.newProxyInstance(
                serviceClass.classLoader,
                arrayOf(serviceClass),
                handler
            ) as T
        }

        private class ProxyInvocationHandler(
            private val target: Any,
            private val interceptors: List<MethodInterceptor>
        ) : InvocationHandler {
            override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
                // Object 클래스의 메서드는 그대로 호출
                if (method.declaringClass == Any::class.java) {
                    return method.invoke(target, *(args ?: emptyArray()))
                }

                // 인터셉터 체인 생성
                return executeInterceptorChain(0, target, method, args)
            }

            private fun executeInterceptorChain(index: Int, obj: Any, method: Method, args: Array<out Any?>?): Any? {
                // 모든 인터셉터를 실행한 후 타겟 메서드 호출
                if (index >= interceptors.size) {
                    return method.invoke(target, *(args ?: emptyArray()))
                }

                // 현재 인터셉터 실행 및 다음 인터셉터로 체인 연결
                val interceptor = interceptors[index]
                return interceptor.intercept(obj, method, args) {
                    executeInterceptorChain(index + 1, obj, method, args)
                }
            }
        }
    }
}

fun main() {
    println("=== 동적 프록시 데코레이터 패턴 해결책 ===")

    // 프록시 팩토리 설정
    val proxyFactory = Solution.ProxyFactory()
    proxyFactory.addInterceptor(Solution.LoggingInterceptor())
    proxyFactory.addInterceptor(Solution.PerformanceInterceptor())
    proxyFactory.addInterceptor(Solution.ExceptionHandlingInterceptor())

    // 사용자 서비스 프록시 생성
    val userServiceTarget = UserServiceImpl()
    val userService = proxyFactory.create(UserService::class.java, userServiceTarget)

    try {
        println("\n=== 사용자 서비스 테스트 ===")
        println("사용자 생성 중...")
        val user = userService.createUser("홍길동", "hong@example.com")
        println("생성된 사용자: $user")

        println("\n사용자 조회 중...")
        val foundUser = userService.getUser(user.id)
        println("조회된 사용자: $foundUser")

        println("\n사용자 업데이트 중...")
        val updatedUser = userService.updateUser(user.id, "홍길동2", "hong2@example.com")
        println("업데이트된 사용자: $updatedUser")

        println("\n존재하지 않는 사용자 조회 시도...")
        userService.getUser(999) // 예외 발생
    } catch (e: Exception) {
        println("최종 예외 처리: ${e.message}")
    }

    // 주문 서비스에도 동일한 프록시 적용
    println("\n=== 주문 서비스 테스트 ===")
    val orderServiceTarget = OrderServiceImpl()
    val orderService = proxyFactory.create(OrderService::class.java, orderServiceTarget)

    try {
        println("주문 생성 중...")
        val order = orderService.placeOrder(1, 100, 2)
        println("생성된 주문: $order")

        println("\n주문 조회 중...")
        val foundOrder = orderService.getOrder(order.id)
        println("조회된 주문: $foundOrder")

        println("\n주문 취소 중...")
        val canceled = orderService.cancelOrder(order.id)
        println("주문 취소 결과: $canceled")
    } catch (e: Exception) {
        println("최종 예외 처리: ${e.message}")
    }

    println("\n=== 장점 ===")
    println("1. 코드 중복 없이 여러 서비스에 횡단 관심사(로깅, 성능 측정, 예외 처리)를 적용할 수 있음")
    println("2. 기존 서비스 코드를 수정하지 않고 새로운 기능을 추가할 수 있음")
    println("3. 필요에 따라 인터셉터를 조합하여 다양한 기능을 동적으로 추가할 수 있음")
    println("4. 새로운 인터셉터를 추가하기 쉬움 (예: 캐싱, 인증, 트랜잭션 등)")
}