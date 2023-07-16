# Bridge Pattern
브리지 패턴(Bridge pattern)이란 구현부에서 추상층을 분리하여 각자 독립적으로 변형할 수 있게 하는 패턴이다.

## 언제 사용 하는가?
- 무분별한 상속을 피하기 위해 사용

## 어떻게 구현 하면 좋은가?
- 브리지 디자인 패턴의 기본 개념은 클래스 계층 구조를 평평하게 하고 시스템에서 특수 클래스의 수를 줄이는 것입니다.
- 슈퍼클래스를 수정하면 이를 상속하는 클래스에 미묘한 버그가 발생할 때 취약한 베이스 클래스 문제를 방지하는 데 도움이 됩니다.

## Sample
은하 제국을 위해 다양한 종류의 트루퍼를 관리하는 시스템을 구축하고자 한다고 가정해 봅시다.

### [General Abusing Inheritance](./GeneralAbusing.kt)
- 새로운 병사들을 구현하기 위해 상속을하고 상속의 상속을해서 유지보수가 어려워짐
- 새로운 기능을 추가할때 interface 에 함수가 추가되면 이 인터페이스를 구현하는 모든 클래스의 컴파일이 중지됩니다.
- 현재 구현중인 클래스 모두 변경해야 합니다. (더 많이 상속중이라면 더 많은 클래스의 수정이 필요)

### [Using Bridge Pattern](./Bridge.kt)

### TypeAlias
기존 타입에 이름을 지정해서 type 으로 사용

**장점**
- 반환하는 값의 의미를 정확히 알 수 있습니다.
- 복잡한 일반 표현식을 숨겨 간결해집니다.

## 참고
- [브리지 패턴 위키](https://ko.wikipedia.org/wiki/%EB%B8%8C%EB%A6%AC%EC%A7%80_%ED%8C%A8%ED%84%B4)