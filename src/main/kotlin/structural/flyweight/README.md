# Flyweight Pattern

> 다수의 오브젝트를 사용할 때 메모리 사용량을 줄여주는 패턴</br>
> 오브젝트간 공통된 데이터를 공유함으로써 메모리 사용량을 줄여주는 패턴

## 구현

```kotlin
data class Dog(
	val name: String,
	val age: Int,
	val gender: String,
	val breed: String,
	val dna: DNA
)
```

- name - 20글자를 받는다고 가정하면 20byte
- age - 64비트 Integer 를 사용한다면 2byte 필요
- gender - 10byte
- breed - 10byte
- DNA - 100MB

Dog 클래스를 사용해서 객체를 만들면 DNA 때문에 메모리 필요양이 많아진다.

강아지들간의 `공통된 속성을 공유`하게되면 메모리 소비를 획기적으로 줄일수 있다.

DNA 정보를 모든 강아지가 공유하게 되면 강아지를 많이 만들어도 하나의 DNA 정보를 공유하고 있기 때문에 메모리 사용량을 줄일수 있게된다.

```kotlin
data class Dog(
	val name: String,
	val age: Int,
	val gender: String,
	val breed: String
) {
	val DNA: String = "ATASDFGGSSASASGGGG.."
}
```

- Class Attribute(파이썬) 를 사용(다른 언어에서는 Static variable)
    - DNA 시퀀스가 오브젝트간 공유가 되기때문에 메모리 사용량을 줄일수 있다.

### 참고

- [강의 영상](https://www.youtube.com/watch?v=oRThakO5o-Q&list=PLDV-cCQnUlIaOFXCUv8vEMGxqzrrkGv_P&index=7)
- [파이썬 코드](https://colab.research.google.com/github/NoCodeProgram/DesignPatterns/blob/main/Structural/flyweightP.ipynb)