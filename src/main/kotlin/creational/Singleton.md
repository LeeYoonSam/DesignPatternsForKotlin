# Singleton
| 프로세스가 생성중에 오직 하나의 오브젝트만 생성이 되도록 강제하는 디자인 패턴
| 객체의 인스턴스를 1개만 생성하여 계속 재사용 하는 패턴

- 아무것에나 쓰는것은 권장하지 않지만 꼭 필요한 경우가 있기 때문에 알아두면 좋다.

### 특징
- 일반적인 클래스에 고양이 클래스가 있고 클래스로 고양이 객체를 생성하면 서로 다른 고양이를 생성한다.
- 서로 다른 고양이 객체는 프로세스에서 다른 메모리 공간을 가진다.
- 싱글톤으로 만들게 되면 전체 프로세스에서 고양이 객체는 하나만 생성이 된다.

## 구현
- 싱글톤 클래스 구현
- 정적 변수로 Instace를 생성
- 생성자에서 인스턴스가 객체 자체를 할당하고 이미 할당 되어있다면 해당 인스턴스를 반환 해준다.


## 참고
- [강의 영상](https://www.youtube.com/watch?v=-oy5jOd5PBg&list=PLDV-cCQnUlIYcAmW4j27i8aYPbja9HePm&index=6)
- [자바스크립트 코드](https://github.com/NoCodeProgram/DesignPatterns/blob/main/Creational/singletonP.js)
- [코틀린 코드](https://pl.kotl.in/9KekRwSXJ)
