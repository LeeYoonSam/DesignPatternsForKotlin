# Abstract Factory
> Abstract Factory는 크게 오해되고 있는 디자인 패턴입니다.<br/> 
> 매우 복잡하고 기괴하기로 악명이 높게 알려져 있지만, 사실 아주 간단합니다.<br/>
> `Factory Method` 디자인 패턴을 이해했다면 이 패턴도 금방 이해할 수 있을 것입니다.<br/>
> 팩토리는 다른 클래스를 생성할 수 있는 함수 또는 클래스이며 공장 중의 공장입니다.<br/>
> 즉, 추상 팩토리는 여러 팩토리 메소드를 구성하는 클래스입니다.

이것을 이해하면서도 한편으로는 여전히 이 디자인 패턴의 용도가 무엇인지 궁금할 것입니다.<br/>
실제 세계에서 `Abstract Factory` 디자인 패턴은 파일에서 구성을 가져오는 프레임워크 및 라이브러리에서 자주 사용됩니다.<br/>
`Spring Framework`는 이들 중 하나의 예일뿐입니다.

디자인 패턴의 작동 방식을 더 잘 이해하기 위해 `YAML` 파일로 작성된 서버 구성이 있다고 가정해 보겠습니다.
```yaml
server:
  port: 8080
environment: production
```
우리가 하려는 작업은 이 구성에서 개체를 구성하는 것입니다.

이전 섹션에서는 `Factory Method`를 사용하여 동일한 패밀리에서 개체를 구성하는 방법에 대해 설명했습니다. 그러나 여기에는 서로 관련이 있지만 형제가 아닌 두 개의 개체군이 있습니다.

먼저 인터페이스로 설명하겠습니다.
```kotlin
interface Property {
    val name: String
    val value: Any
}
```
데이터 클래스 대신 인터페이스를 반환합니다. 이 섹션의 뒷부분에서 이것이 어떻게 도움이 되는지 확인할 수 있습니다.

```kotlin
interface ServerConfiguration {
    val properties: List<Property>
}
```
그런 다음 나중에 사용할 기본 구현을 제공할 수 있습니다.

```kotlin
data class PropertyImpl(
    override val name: String,
    override val value: Any
) : Property

data class ServerConfigurationImpl(
    override val properties: List<Property>
) : ServerConfiguration
```
서버 구성에는 단순히 속성 목록이 포함되며 속성은 name 개체와 value 개체로 구성된 쌍입니다.

`Any` 유형은 Java 객체의 Kotlin 버전이지만 한 가지 중요한 차이점은 null일 수 없다는 것입니다.

이제 문자열로 주어진 `Property`를 생성하는 첫 번째 `Factory Method`를 작성해 보겠습니다.

```kotlin
fun property(prop: String): Property {
    val (name, value) = prop.split(":")
    return when (name) {
        "port" -> PropertyImpl(name, value.trim().toInt())
        "environment" -> PropertyImpl(name, value.trim())
        else -> throw RuntimeException("Unknown property: $name")
    }
}
```

다른 많은 언어와 마찬가지로 trim()은 문자열에서 공백을 제거하는 문자열에 선언된 함수입니다.<br/> 
이제 서비스의 `포트(port)`와 `환경(environment)`을 나타내는 두 개의 속성을 생성해 보겠습니다.
```kotlin
val portProperty = property("port: 8080")
val environment = property("environment: production")
```

이 코드에는 약간의 문제가 있습니다. 이것이 무엇인지 이해하기 위해 포트 속성 값을 다른 변수에 저장해 보겠습니다.
```kotlin
val port: Int = portProperty.value
// Type mismatch: inferred type is Any but Int was expected
```

우리는 이미 `Factory Method`에서 port가 `Int`로 구문 분석되었음을 확인했습니다.
그러나 지금은 값의 유형이 `Any`로 선언되었기 때문에 이 정보가 손실됩니다. 
`String`, `Int` 또는 다른 유형이 될 수 있습니다. 
이 문제를 해결하려면 새로운 도구가 필요하므로 잠시 우회하여 `Kotlin`의 `casts`에 대해 논의해 보겠습니다.

## Casts