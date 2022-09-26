# Command Pattern
> **행위(behavioral) 패턴**

> 여러 명령들을 추상화해서 클래스로 정의하고 오브젝트로 만들어서 사용하는 패턴 <br>
> 객체나 클래스 사이의 알고리즘이나 책임 분배에 관련된 패턴 <br>
> 한 객체가 혼자 수행할 수 없는 작업을 여러 개의 객체로 어떻게 분배하는지, 또 그렇게 하면서도 객체 사이의 결합도를 최소화하는 것에 중점을 둔다.<br> 
> 실행될 기능을 캡슐화함으로써 주어진 여러 기능을 실행할 수 있는 재사용성이 높은 클래스를 설계하는 패턴 <br>
> 즉, 이벤트가 발생했을 때 실행될 기능이 다양하면서도 변경이 필요한 경우에 이벤트를 발생시키는 클래스를 변경하지 않고 재사용하고자 할때 유용<br> 
> 실행될 기능을 캡슐화함으로써 기능의 실행을 요구하는 호출자 클래스와 실제 기능을 실행하는 수신자 클래스 사이의 의존성을 제거 <br>
> 실행될 기능의 변경에도 호출자 클래스를 수정 없이 그대로 사용 할 수 있도록 해준다.<br>

### 역할이 수행하는 작업
`Command`
- 실행될 기능에 대한 인터페이스 
- 실행될 기능을 execute 메서드로 선언

`ConcreateCommand`
- 실제로 실행되는 기능을 구현
- 즉, Command 라는 인터페이스를 구현

`Invoker`
- 기능의 실행을 요청하는 호출자 클래스

`Receiver`
- ConcreteCommand 에서 execute 메서드를 구현할 때 필요한 클래스 
- 즉, ConcreteCommand 의 기능을 실행하기 위해 사용하는 수신자 클래스

### 명령을 추상화 하면 좋은점
- 오브젝트처럼 관리가 가능
- 모아서 정해진 시간에 실행시키는 등의 동작이 가능

### 필요 요소

- 명령을 추상화
- 명령을 상속받은 객체를 구현
- 명령들을 실행할 Invoker

### 결론

- 명령을 추상화해서 객체로 다룬다.
    - 필요에 따라 커맨드 패턴을 변형 및 응용이 가능

### 참고

- [https://ko.wikipedia.org/wiki/커맨드_패턴](https://ko.wikipedia.org/wiki/%EC%BB%A4%EB%A7%A8%EB%93%9C_%ED%8C%A8%ED%84%B4)
- [영상 강의](https://www.youtube.com/watch?v=bUULgkwaicQ)
- [구현 코드](https://colab.research.google.com/github/NoCodeProgram/DesignPatterns/blob/main/Behavioral/commandP.ipynb)
- [코틀린 구현 코드](https://pl.kotl.in/VDAGXWlbe)
- [커맨드 패턴](https://gmlwjd9405.github.io/2018/07/07/command-pattern.html)