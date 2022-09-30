# Factory Method Pattern

| 팩토리를 더 확장시킨 패턴
| 팩토리에 여러 기능을 추가 시키고 싶을 때 사용
| 팩토리에 필요한 메소드를 추가하는 형태
| 각각의 다른 기능이 있는 팩토리로 객체를 생성

## 구현
- Factory의 기본이 되는 Interface 추가
- 각각의 팩토리에서 상속받고 해당 팩토리만의 공한 기능을 가진다.
- 핵심은 팩토리 인터페이스
  - 클래스의 전체적인 기능은 팩토리가 아닌 경우가 많다.
  - 클래스 이름은 xxFactory 등이 아니라 xxCreator, xxManager 등으로 네이밍
  - 클래스별로 팩토리 인터페이스를 상속받아서 같은 인터페이스를 사용해서 객체를 생성한다면 Factory를 사용한다고 추측 할 수 있다.

## 참고
- [강의영상](https://www.youtube.com/watch?v=ejXUhFKcbIU&list=PLDV-cCQnUlIYcAmW4j27i8aYPbja9HePm&index=3)
- [파이썬 코드](https://colab.research.google.com/github/NoCodeProgram/DesignPatterns/blob/main/Creational/FactoryMethodP.ipynb)
