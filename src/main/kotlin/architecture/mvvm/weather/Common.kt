package architecture.mvvm.weather

import kotlinx.coroutines.delay

// 날씨 데이터 모델
data class WeatherData(
    val city: String,
    val temperature: Double,
    val condition: String,
    val humidity: Int,
    val windSpeed: Double
)

// 날씨 API 서비스 (Model 계층 - 데이터 소스)
class WeatherApiService {
    // 실제로는 네트워크 요청을 하지만, 여기서는 가상 데이터를 반환
    suspend fun getWeatherForCity(cityName: String): WeatherData {
        delay(1000) // 네트워크 지연 시뮬레이션
        return when (cityName.lowercase()) {
            "seoul" -> WeatherData("Seoul", 25.5, "Sunny", 60, 5.0)
            "tokyo" -> WeatherData("Tokyo", 28.0, "Cloudy", 70, 8.0)
            "new york" -> WeatherData("New York", 22.0, "Rainy", 80, 10.0)
            else -> WeatherData(cityName, 20.0, "Unknown", 50, 5.0)
        }
    }
}

// 위치 서비스 (가상)
class LocationService {
    suspend fun getCurrentCity(): String {
        delay(500) // 위치 확인 지연 시뮬레이션
        return "Seoul" // 기본 위치
    }
}