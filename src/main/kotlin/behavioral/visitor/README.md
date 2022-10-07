# Visitor Pattern

> 알고리즘을 오브젝트 구조로 분리하는 방법</br>
> 그 결과로 이미 있던 오브젝트에 수정없이 새로운 동작을 추가 할 수 있다.</br>
> 개방/폐쇄 원칙을 따르는 하나의 방법</br>
> 기존 클래스를 수정하지 않고 새로운 기능을 추가하려고 할때 사용

## 구현

- Object 가 존재
- 이 Object 를 다루는 알고리즘 클래스를 분리하는것 (Visitor)
    - 기존 오브젝트에 방문하고 알고리즘 수행
    - 이미 존재하는 클래스나 오브젝트의 수정 없이 새로운 알고리즘을 기존 오브젝트에 적용 가능

### Visitor 패턴은 별로 중요하지 않다?

- 프로퍼티만 존재하는 오브젝트를 만든다.
- interface 를 사용해서 오브젝트를 생성
- 그 오브젝트를 다룰 함수를 생성하면 Visitor 패턴과 똑같이 구현 가능
- 오브젝트의 구현을 변경하지 않고 여러 알고리즘을 적용할수 있다.
- 기존 클래스가 존재할 경우 해당 클래스를 받는 새로운 클래스를 만들면 된다.
- 오브젝트에 멤버함수로 호출하는 방식도 가능
    - accept 함수를 선언하고 함수를 전달

## 참고

- [강의 영상](https://www.youtube.com/watch?v=rbtyXGDL0eo)
- [파이썬 코드](https://colab.research.google.com/github/NoCodeProgram/DesignPatterns/blob/main/Behavioral/visitorP.ipynb)
- [코틀린 코드](https://pl.kotl.in/96EN8asmg)
- [Simple Visitor Pattern Sample](./SimpleVisitorPattern.kt)