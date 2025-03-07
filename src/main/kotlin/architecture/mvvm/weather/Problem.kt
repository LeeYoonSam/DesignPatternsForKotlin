package architecture.mvvm.weather

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class Problem {
    // 문제가 있는 안드로이드 액티비티 (MainActivity 가정)
    class WeatherActivity : CoroutineScope {
        // 코루틴 컨텍스트
        private val job = Job()
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Default + job

        // 의존성
        private val weatherApiService = WeatherApiService()
        private val locationService = LocationService()

        // UI 상태
        private var isLoading = false
        private var weatherData: WeatherData? = null
        private var errorMessage: String? = null

        // 검색할 도시 이름
        private var cityName: String = ""

        // 액티비티 생성 시 호출
        fun onCreate() {
            println("WeatherActivity created")
            loadCurrentLocationWeather()
        }

        // 현재 위치 날씨 로드
        private fun loadCurrentLocationWeather() {
            isLoading = true
            updateUI() // 로딩 UI 표시

            launch {
                try {
                    val city = locationService.getCurrentCity()
                    cityName = city
                    val weather = weatherApiService.getWeatherForCity(city)
                    weatherData = weather
                    errorMessage = null
                } catch (e: Exception) {
                    errorMessage = "Failed to load weather: ${e.message}"
                    weatherData = null
                } finally {
                    isLoading = false
                    updateUI() // 결과 UI 업데이트
                }
            }
        }

        // 특정 도시 날씨 검색
        fun searchWeather(city: String) {
            cityName = city
            isLoading = true
            updateUI() // 로딩 UI 표시

            launch {
                try {
                    val weather = weatherApiService.getWeatherForCity(city)
                    weatherData = weather
                    errorMessage = null
                } catch (e: Exception) {
                    errorMessage = "Failed to load weather: ${e.message}"
                    weatherData = null
                } finally {
                    isLoading = false
                    updateUI() // 결과 UI 업데이트
                }
            }
        }

        // UI 업데이트 (안드로이드에서는 View 업데이트 코드)
        private fun updateUI() {
            if (isLoading) {
                println("Loading weather data...")
                return
            }

            if (errorMessage != null) {
                println("Error: $errorMessage")
                return
            }

            weatherData?.let {
                println("===== Weather Information =====")
                println("City: ${it.city}")
                println("Temperature: ${it.temperature}°C")
                println("Condition: ${it.condition}")
                println("Humidity: ${it.humidity}%")
                println("Wind Speed: ${it.windSpeed} m/s")
                println("==============================")
            }
        }

        // 화면 회전 시 상태 저장 (안드로이드에서는 onSaveInstanceState)
        fun onSaveInstanceState(): Map<String, Any?> {
            val state = mutableMapOf<String, Any?>()
            state["city"] = cityName
            state["weatherData"] = weatherData
            state["isLoading"] = isLoading
            state["errorMessage"] = errorMessage
            return state
        }

        // 화면 회전 후 상태 복원 (안드로이드에서는 onRestoreInstanceState)
        fun onRestoreInstanceState(state: Map<String, Any?>) {
            cityName = state["city"] as String? ?: ""
            weatherData = state["weatherData"] as WeatherData?
            isLoading = state["isLoading"] as Boolean? ?: false
            errorMessage = state["errorMessage"] as String?
            updateUI()
        }

        // 액티비티 소멸 시 호출
        fun onDestroy() {
            job.cancel() // 코루틴 작업 취소
            println("WeatherActivity destroyed")
        }
    }
}

// 실행 함수
fun main() {
    // 액티비티 생성 및 실행
    val activity = Problem.WeatherActivity()
    activity.onCreate()

    // 사용자 상호작용 시뮬레이션
    runBlocking {
        delay(2000) // 초기 로딩 기다림

        println("\n사용자가 'Tokyo' 검색")
        activity.searchWeather("Tokyo")
        delay(1500)

        println("\n사용자가 'New York' 검색")
        activity.searchWeather("New York")
        delay(1500)

        println("\n화면 회전 시뮬레이션")
        val savedState = activity.onSaveInstanceState()

        activity.onDestroy() // 기존 액티비티 파괴

        println("\n새 액티비티 생성")
        val newActivity = Problem.WeatherActivity()
        newActivity.onCreate()

        println("\n상태 복원")
        newActivity.onRestoreInstanceState(savedState)

        delay(1000)

        println("\n앱 종료")
        newActivity.onDestroy()
    }
}