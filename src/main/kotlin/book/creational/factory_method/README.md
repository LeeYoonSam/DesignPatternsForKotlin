# Factory Method
> Factory Method 디자인 패턴은 객체 생성에 관한 것입니다.<br> 
> 그러나 객체를 생성하는 방법은 누구에게 필요합니까? 생성자가 해야할 일이 아닌가요?<br> 
> 생성자에는 한계가 있습니다.

예를 들어, 우리가 체스 게임을 만들고 있다고 상상해보십시오. 
우리는 플레이어가 게임 상태를 텍스트 파일에 저장한 다음 해당 위치에서 게임을 복원할 수 있도록 하고 싶습니다.

판의 크기는 미리 정해져 있기 때문에 각 조각의 위치와 종류만 기록하면 됩니다. 
이를 위해 대수적 표기법을 사용할 것입니다. 예를 들어 C3의 Queen 조각은 파일에 qc3으로 저장되고 A8의 폰 조각은 pa8로 저장되는 식입니다.

이 파일을 이미 문자열 목록으로 읽었다고 가정해 보겠습니다.
(그런데 이것은 앞에서 논의한 Singleton 디자인 패턴의 훌륭한 적용이 될 것입니다).

표기법 목록이 주어지면 게시판에 표기법을 채우고자 합니다.
```kotlin
// More pieces here
val notations = listOf("pa8", "qc3")
val pieces = mutableListOf<ChessPiece>()

for (n in notations) {
    pieces.add(createPiece(n))
}

println(pieces)
```

`createPiece` 기능을 구현하기 전에 모든 체스 말에 공통적인 사항을 결정해야 합니다. 이를 위한 인터페이스를 만들겠습니다.
```kotlin
interface ChessPiece {
    val file: Char
    val rank: Char
}
```

Kotlin의 인터페이스는 매우 강력한 기능인 속성을 선언할 수 있습니다.
각 체스 말은 인터페이스를 구현하는 데이터 클래스가 됩니다.
```kotlin
data class Pawn(
    override val file: Char,
    override val rank: Char
): ChessPiece

data class Queen(
    override val file: Char,
    override val rank: Char
): ChessPiece
```
- 다른 체스 말의 구현은 당신이 할 연습으로 남겨둡니다.

이제 남은 것은 createPiece 함수를 구현하는 것입니다.
```kotlin
fun createPiece(notation: String): ChessPiece {
    val (type, file, rank) = notation.toCharArray()

    return when(type) {
        'q' -> Queen(file, rank)
        'p' -> Pawn(file, rank)
        // ...
        else -> throw RuntimeException("Unknown piece: $type")
    }
}
```
이 함수가 무엇을 달성하는지 논의하기 전에 이전에 보지 못한 세 가지 새로운 구문 요소를 살펴보겠습니다.

먼저 toCharArray 함수는 문자열을 문자 배열로 분할합니다. 
모든 표기법이 3자 길이라고 가정하기 때문에 요소의 요소는 순위라고도 하는 수평 열을 나타냅니다.

다음으로, 괄호로 둘러싸인 type, file, rank의 세 가지 값을 볼 수 있습니다. 이것을 `구조분해 선언`이라고 하며, 예를 들어 JavaScript에서 익숙할 것입니다. 모든 데이터 클래스는 구조화될 수 있습니다.

이전 코드 예제는 다음과 유사하며 훨씬 더 자세한 코드입니다.
```kotlin
val type = notation.toCharArray()[0]
val file = notation.toCharArray()[1]
val rank = notation.toCharArray()[2]
```

이제 when 표현에 집중합시다. 

유형을 나타내는 문자를 기반으로 `ChessPiece` 인터페이스 구현 중 하나를 인스턴스화합니다. 

이것이 `Factory Method` 디자인 패턴의 전부라는 것을 기억하십시오.

629 / 5,000
번역 결과
이 디자인 패턴을 잘 이해하려면 다른 체스 말에 대한 클래스와 논리를 연습으로 자유롭게 구현하십시오.

마지막으로, throw 표현식의 첫 번째 사용을 볼 수 있는 함수의 맨 아래를 살펴보겠습니다.

이름에서 알 수 있듯이 이 표현식은 예외를 발생시켜 간단한 프로그램의 정상적인 실행을 중지합니다.

실제 세계에서 Factory Method 디자인 패턴은 `XML`, `JSON` 또는 `YAML` 형식의 `구성 파일을 런타임 개체로 구문 분석`해야 하는 라이브러리에서 자주 사용됩니다.