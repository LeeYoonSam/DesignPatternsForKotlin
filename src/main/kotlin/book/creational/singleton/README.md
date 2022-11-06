# Singleton
> 가장 인기있고 모두가 알고있는 패턴<br>
디자인 패턴을 사용하는 것을 좋아하지 않는 사람들조차도 이름으로 싱글톤을 알 것입니다.
한때는 안티패턴으로 선언되기도 했지만, 그 이유는 대중적 인기 때문이었습니다.

그래서 처음 접하는 사람들에게 이 디자인 패턴은 무엇일까요?

일반적으로 클래스가 있는 경우 원하는 만큼 인스턴스를 만들 수 있습니다. 

예를 들어, 우리 둘 다 좋아하는 영화를 모두 나열하도록 요청받았다고 가정해 보겠습니다.

```kotlin
val myFavoriteMovies = listOf("Black Hawk Down", "Blade Runner")
val yourFavoriteMovies = listOf(...)
```

List의 인스턴스를 원하는 만큼 생성할 수 있으며 문제는 없습니다. 대부분의 클래스에는 여러 인스턴스가 있을 수 있습니다.

다음으로, 우리 둘 다 빠르고 화난 시리즈에서 최고의 영화를 나열하고 싶다면 어떻게 해야 할까요?

```kotlin
val myFavoriteQuickAndAngryMovies = listOf<String>()
val yourFavoriteQuickAndAngryMovies = listOf<String>()
```
이 두 목록은 비어 있기 때문에 정확히 동일합니다. 그리고 그것들은 변경 불가능하고 Quick and Angry 시리즈가 단순히 끔찍하기 때문에 비어 있는 상태로 유지됩니다.

클래스의 이 두 인스턴스는 정확히 동일하기 때문에 equal 메서드에 따르면 메모리에 여러 번 유지하는 것은 의미가 없습니다. 

빈 목록에 대한 모든 참조가 개체의 동일한 인스턴스를 가리키면 좋을 것입니다. 그리고 사실 모든 null은 동일합니다.

이것이 `Singleton 디자인 패턴`의 주요 아이디어입니다.

Singleton 디자인 패턴에는 몇 가지 요구 사항이 있습니다.
- 우리 시스템에는 정확히 하나의 인스턴스가 있어야 합니다.
- 이 인스턴스는 시스템 어디에서나 접근할 수 있어야 합니다.

Java 및 일부 다른 언어에서 이 작업은 매우 복잡합니다. 

먼저 private 클래스에 대한 생성자를 만들어 객체의 새 인스턴스가 생성되는 것을 금지해야 합니다.

그런 다음 다음 요구 사항과 함께 인스턴스화가 가급적 지연되고 스레드로부터 안전하며 성능이 좋은지 확인해야 합니다.

- Lazy: 프로그램이 시작될 때 싱글톤 개체를 인스턴스화하고 싶지 않을 수 있습니다. 이는 비용이 많이 드는 작업일 수 있기 때문에 처음 필요할 때만 인스턴스화하고 싶습니다.
- Thread-safe: 두 스레드가 동시에 싱글톤 개체를 인스턴스화하려고 시도하는 경우 둘 다 동일한 인스턴스를 수신해야 하며 두 개의 다른 인스턴스를 수신해서는 안 됩니다.
- Performant: 많은 스레드가 동시에 싱글톤 개체를 인스턴스화하려고 시도하는 경우 실행을 중지하므로 오랜 기간 동안 스레드를 차단해서는 안 됩니다.

Java 또는 C++에서 이러한 모든 요구 사항을 충족하는 것은 상당히 어렵거나 매우 장황합니다.

Kotlin에서는 `object`라는 키워드를 도입하여 싱글톤을 쉽게 만들 수 있습니다. Scala에서 이 키워드를 알 수 있습니다. 

이 키워드를 사용하여 모든 요구 사항을 수용하는 싱글톤 개체의 구현을 얻을 수 있습니다.

`object` 키워드는 싱글톤을 만드는 것 이상의 용도로 사용됩니다. 이 장의 뒷부분에서 이에 대해 자세히 설명합니다.

싱글톤 객체는 우리가 인스턴스화할 수 없기 때문에 일반 클래스처럼 객체를 선언하지만 생성자는 없습니다.
```kotlin
object NoMoviesList
```

이제부터는 코드의 어느 곳에서나 `NoMoviesList`에 액세스할 수 있으며 정확히 하나의 인스턴스가 있습니다.
```kotlin
val myFavoriteQuickAndAngryMovies = NoMoviesList
val yourFavoriteQuickAndAngryMovies = NoMoviesList

// 참조 타입의 주소값을 비교(reference comparison)
println(myFavoriteQuickAndAngryMovies === yourFavoriteQuickAndAngryMovies)
```
- 두 변수가 메모리의 동일한 개체를 가리키는지 확인하는 참조 등호에 유의하십시오.

영화 목록을 인쇄하는 함수를 만들어 보겠습니다.
```kotlin
fun printMovies(movies: List<String>) {
    for (movie in movies) {
        println(movie)
    }
}
```

영화의 초기 목록을 전달하면 코드가 잘 컴파일됩니다.
```kotlin
printMovies(myFavoriteMovies)
```

하지만 빈 영화 목록을 전달하면 코드가 컴파일되지 않습니다.
```kotlin
val myFavoriteQuickAndAngryMovies2 = NoMoviesList
printMovies(myFavoriteQuickAndAngryMovies2)

---
Type mismatch: inferred type is NoMoviesList but List<String> was expected
```
그 이유는 함수가 문자열 목록 유형의 인수만 수락하는 반면 `NoMoviesList`가 이 유형임을 함수에 알리는 것이 없기 때문입니다(이름에서 알 수 있음에도 불구하고).

다행히 Kotlin에서는 싱글톤 객체가 인터페이스를 구현할 수 있으며 일반 List 인터페이스를 사용할 수 있습니다.
```kotlin
object NoMoviesList : List<String>
```
이제 컴파일러는 필요한 기능을 구현하라는 메시지를 표시합니다. 
object에 body를 추가합니다. 

```kotlin
// generic List interface
object NoMoviesList : List<String> {
    override val size = 0
    override fun contains(element: String): Boolean = false
        ...
}
```
원하는 경우 다른 기능을 구현하는 것은 사용자에게 맡기겠습니다. 
이것은 지금까지 Kotlin에 대해 배운 모든 것을 잘 연습해야 합니다. 그러나 이렇게 할 필요는 없습니다. 
Kotlin은 이미 모든 유형의 빈 목록을 만드는 기능을 제공합니다.
```kotlin
printMovies(emptyList())
```

Kotlin 객체는 클래스의 주요 차이점은 생성자를 가질 수 없다는 것입니다. 
구성 파일에서 데이터를 처음 로드하는 것과 같이 `Singleton`에 대한 초기화를 구현해야 하는 경우 `init` 블록을 사용할 수 있습니다.
```kotlin
object Logger {
    init {
        println("I was accessed for the first time")
        // Initialization logic goes here
    }
    // More code goes here
}
```
- `Singleton`이 호출되지 않으면 초기화 로직이 실행되지 않으므로 리소스가 절약됩니다. 이것을 `지연 초기화`라고 합니다.

이제 객체 생성을 제한하는 방법을 배웠으므로 생성자를 직접 사용하지 않고 객체를 생성하는 방법에 대해 논의해 보겠습니다.
