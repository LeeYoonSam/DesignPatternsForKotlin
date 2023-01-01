# Static Factory Method

종종 `Factory Method` 디자인 패턴과 혼동되는 유사한 이름의 디자인 패턴이 있으며, 이는 `Gang of Four` 책인 `Static Factory Method` 디자인 패턴에 설명되어 있습니다.

`Static Factory Method` 디자인 패턴은 Joshua Bloch가 그의 저서 Effective Java에서 대중화했습니다. 

이를 더 잘 이해하기 위해 Java 표준 라이브러리의 몇 가지 예인 `valueOf()` 메서드를 살펴보겠습니다. 

문자열에서 `Long`(즉, 64비트 정수)을 구성하는 방법에는 적어도 두 가지가 있습니다.

```java
Long 11 = new Long("1"); // constructor
Long 12 = Long.valueOf("1"); // static factory method 
```
- 생성자와 valueOf() 메서드 모두 문자열을 입력으로 받고 Long을 출력으로 생성합니다.

그렇다면 간단한 생성자보다 `Static Factory Method` 디자인 패턴을 선호해야 하는 이유는 무엇입니까?<br/>

생성자를 사용하는것에 비해 `Static Factory Method`를 사용할 때의 장점
- 다른 개체 생성자의 이름을 명시적으로 지정할 수 있는 기회를 제공합니다.
- 우리는 일반적으로 생성자에서 예외를 기대하지 않습니다. 그렇다고 클래스의 인스턴스화가 실패할 수 없다는 의미는 아니지만 일반 방법의 예외는 훨씬 더 많이 허용됩니다.
- 기대치에 대해 말하자면, 우리는 생성자가 빠를 것으로 기대하지만 일부 객체의 생성은 본질적으로 느리기 때문에 `Static Factory Method` 사용을 고려하십시오.

이들은 대부분 스타일 장점입니다. 그러나 이 접근 방식에는 기술적 이점도 있습니다.

## Caching
Static Factory Method 디자인 패턴은 Long이 실제로 하는 것처럼 캐싱을 제공할 수 있습니다.<br/>
값에 대해 항상 새 인스턴스를 반환하는 대신 valueOf()는 이 값이 이미 구문 분석되었는지 여부를 캐시에서 확인합니다. 그렇다면 캐시된 인스턴스를 반환합니다.<br/> 
동일한 값으로 Static Factory 메서드를 반복해서 호출하면 생성자를 항상 사용하는 것보다 수집에 대한 가비지가 적게 생성될 수 있습니다.

## Subclassing
생성자를 호출할 때 항상 지정한 클래스를 인스턴스화합니다.<br/>
반면에 Static Factory Method를 호출하는 것은 덜 제한적이며 클래스 자체의 인스턴스 또는 하위 클래스 중 하나를 생성할 수 있습니다.

## Static Factory Method in Kotlin
이 장 앞부분의 Singleton 섹션에서 object 키워드에 대해 논의했습니다.<br/>
이제 컴패니언 개체로 사용하는 또 다른 방법을 살펴보겠습니다.

<br/>
Java에서 Static Factory Method는 정적으로 선언됩니다.<br/>
그러나 Kotlin에는 그러한 키워드가 없습니다.</br> 
대신 클래스의 인스턴스에 속하지 않는 메서드는 컴패니언 객체 내에서 선언할 수 있습니다.

```text
중요 사항:
컴패니언 개체에는 이름이 있을 수 있습니다(예: 컴패니언 개체 파서). 그러나 이것은 개체의 목표가 무엇인지에 대한 명확성을 제공하기 위한 것일 뿐입니다.
```

보시다시피 이번에는 `companion` 키워드가 접두사로 붙은 객체를 선언했습니다. 또한 `Singleton Design` 패턴에서 보았던 패키지 수준이 아니라 클래스 내부에 있습니다.

이 개체에는 자체 메서드가 있으며 이것이 어떤 이점이 있는지 궁금할 수 있습니다.<br/>
Java 정적 메소드와 마찬가지로 컴패니언 객체를 호출하면 포함 클래스에 처음 액세스할 때 느리게 인스턴스화됩니다.

```text
중요 사항:
클래스에는 하나의 컴패니언 객체만 있을 수 있습니다.
```

때로는 Static Factory Method가 개체를 인스턴스화하는 유일한 방법이 되기를 원할 수도 있습니다.<br/>
이를 위해 객체의 기본 생성자를 비공개로 선언할 수 있습니다.

```kotlin
class Server private constructor(port: Long) {
    ...
}
```