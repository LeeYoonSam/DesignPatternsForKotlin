package architecture.mvvm.weather

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext

class Solution {
    // API 응답을 나타내는 봉인 클래스 (Model 계층)
    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String) : ApiResult<Nothing>()
        object Loading : ApiResult<Nothing>()
    }

    // 날씨 리포지토리 (Model 계층 - 비즈니스 로직)
    class WeatherRepository(
        private val weatherApiService: WeatherApiService,
        private val locationService: LocationService
    ) {
        suspend fun getCurrentLocationWeather(): Flow<ApiResult<WeatherData>> = flow {
            emit(ApiResult.Loading)
            try {
                val city = locationService.getCurrentCity()
                val weatherData = weatherApiService.getWeatherForCity(city)
                emit(ApiResult.Success(weatherData))
            } catch (e: Exception) {
                emit(ApiResult.Error("Failed to load weather: ${e.message}"))
            }
        }.flowOn(Dispatchers.IO)

        suspend fun getWeatherForCity(city: String): Flow<ApiResult<WeatherData>> = flow {
            emit(ApiResult.Loading)
            try {
                val weatherData = weatherApiService.getWeatherForCity(city)
                emit(ApiResult.Success(weatherData))
            } catch (e: Exception) {
                emit(ApiResult.Error("Failed to load weather: ${e.message}"))
            }
        }.flowOn(Dispatchers.IO)
    }

    data class WeatherUiState(
        val isLoading: Boolean = false,
        val weatherData: WeatherData? = null,
        val errorMessage: String? = null,
        val currentCity: String = "",
    )

    // 날씨 ViewModel (ViewModel 계층)
    class WeatherViewModel(private val repository: WeatherRepository) : CoroutineScope {
        private val job = Job()
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Default + job

        // UI 상태를 위한 MutableStateFlow
        private val _uiState = MutableStateFlow(WeatherUiState())
        val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

        // 초기 데이터 로드
        fun loadCurrentLocationWeather() {
            launch {
                repository.getCurrentLocationWeather()
                    .collect { result ->
                        when (result) {
                            is ApiResult.Loading -> {
                                _uiState.update { it.copy(isLoading = true) }
                            }
                            is ApiResult.Success -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        weatherData = result.data,
                                        errorMessage = null,
                                        currentCity = result.data.city
                                    )
                                }
                            }
                            is ApiResult.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        weatherData = null,
                                        errorMessage = result.message
                                    )
                                }
                            }
                        }
                    }

            }
        }

        // 특정 도시 날씨 검색
        fun searchWeather(city: String) {
            _uiState.update { it.copy(currentCity = city) }

            launch {
                repository.getWeatherForCity(city)
                    .collect { result ->
                        when (result) {
                            is ApiResult.Loading -> {
                                _uiState.update { it.copy(isLoading = true) }
                            }
                            is ApiResult.Success -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        weatherData = result.data,
                                        errorMessage = null
                                    )
                                }
                            }
                            is ApiResult.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        weatherData = null,
                                        errorMessage = result.message
                                    )
                                }
                            }
                        }
                    }
            }
        }

        // 리소스 정리
        fun onCleared() {
            job.cancel()
        }
    }

    // 안드로이드 액티비티 (View 계층)
    class WeatherActivity {
        // 의존성 주입 (실제로는 Hilt/Dagger 또는 Koin 등의 DI 프레임워크 사용)
        private val weatherApiService = WeatherApiService()
        private val locationService = LocationService()
        private val weatherRepository = WeatherRepository(weatherApiService, locationService)
        private val viewModel = WeatherViewModel(weatherRepository)

        // UI 상태 수집을 위한 Job
        private var uiStateJob: Job? = null

        // 액티비티 생성 시 호출
        @OptIn(DelicateCoroutinesApi::class)
        fun onCreate() {
            println("WeatherActivity created")

            // UI 상태 관찰 시작
            uiStateJob = GlobalScope.launch(Dispatchers.Default) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }

            // 초기 데이터 로드
            viewModel.loadCurrentLocationWeather()
        }

        // UI 렌더링 (View 계층)
        private fun renderUi(state: WeatherUiState) {
            if (state.isLoading) {
                println("Loading weather data...")
                return
            }

            if (state.errorMessage != null) {
                println("Error: ${state.errorMessage}")
                return
            }

            state.weatherData?.let {
                println("===== Weather Information =====")
                println("City: ${it.city}")
                println("Temperature: ${it.temperature}°C")
                println("Condition: ${it.condition}")
                println("Humidity: ${it.humidity}%")
                println("Wind Speed: ${it.windSpeed} m/s")
                println("==============================")
            }
        }

        // 사용자 액션 처리 (검색 버튼 클릭 등)
        fun onSearchButtonClicked(city: String) {
            viewModel.searchWeather(city)
        }

        // 액티비티 소멸 시 호출
        fun onDestroy() {
            uiStateJob?.cancel()
            viewModel.onCleared()
            println("WeatherActivity destroyed")
        }
    }
}

// 실행 함수
fun main() {
    // 액티비티 생성 및 실행
    val activity = Solution.WeatherActivity()
    activity.onCreate()

    // 사용자 상호작용 시뮬레이션
    runBlocking {
        delay(2000) // 초기 로딩 기다림

        println("\n사용자가 'Tokyo' 검색")
        activity.onSearchButtonClicked("Tokyo")
        delay(1500)

        println("\n사용자가 'New York' 검색")
        activity.onSearchButtonClicked("New York")
        delay(1500)

        println("\n화면 회전 시뮬레이션")
        activity.onDestroy() // 기존 액티비티 파괴

        println("\n새 액티비티 생성 (ViewModel은 유지됨)")
        val newActivity = Solution.WeatherActivity()
        newActivity.onCreate() // ViewModel의 상태는 자동으로 유지됨

        delay(1000)

        println("\n앱 종료")
        newActivity.onDestroy()
    }
}