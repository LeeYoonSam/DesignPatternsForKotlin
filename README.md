# DesignPattern For Kotlin
> 다양한 패턴을 코틀린 예제를 통해서 알아갈 수 있도록 구성할 예정

# 디자인 패턴
> 디자인 패턴은 과거의 소프트웨어 개발 과정에서 발견한 설계의 노하우를 일종의 패턴으로 정리해 놓은것이다.

## 디자인 패턴이 왜 필요한가?
- 복잡한 구조를 한 단어로 정의함으로써 개발자들이 협업을 할 때에 의사소통을 효율적으로 할 수 있다.
- 기존 코드의 문제점에 대해서 검증된 방법으로 해결 방안을 찾을 수 있어 효율적으로 코드를 개선할 수 있다.

## 디자인 패턴을 둘러싼 다양한 오해
- 디자인 패턴에는 언어 기능이 없습니다.
- 동적 언어에서는 디자인 패턴이 필요하지 않습니다.
- 디자인 패턴은 객체 지향 언어에만 관련이 있습니다.
- 디자인 패턴은 기업에서만 사용됩니다.

실제로 디자인 패턴은 일반적인 문제를 해결하는 입증된 방법일 뿐입니다.

개념으로서, 그것들은 특정 프로그래밍 언어(자바)나 언어군(예를 들어, C 계열)에 국한되지 않으며, 일반적인 프로그래밍에 국한되지 않습니다.

다른 시스템이 서로 효율적으로 통신할 수 있는 방법을 논의하는 소프트웨어 아키텍처의 디자인 패턴에 대해 들어본 적이 있을 것입니다.

서비스 지향 아키텍처(SOA)라고 알려진 서비스 지향 아키텍처 패턴과 SOA에서 발전하여 지난 몇 년 동안 등장한 마이크로서비스 디자인 패턴이 있습니다.

미래는 확실히 더 많은 디자인 패턴 제품군을 가져올 것입니다.

물리적 세계, 외부 소프트웨어 개발에서도 우리는 특정 문제에 대해 일반적으로 수용되는 솔루션과 디자인 패턴으로 둘러싸여 있습니다.

## 디자인 패턴의 장점
커뮤니케이션, 학습 및 향상된 통찰력
- 패턴 사용으로 인해 설계뿐만 아니라 배경의 근거를 쉽게 파악 할 수 있다.

시스템을 개체로 분해
- 디자인의 어려운 점은 적절한 개체를 찾고 시스템을 분해하는 것
- 캡슐화, 세분화, 의존성, 유연성, 성능, 진화, 재사용 가능성 등을 고려
- 부족한 추상화를 식별하는 데 도움이 된다.

개체 세분성 결정
- 여러 가지 세분화된 수준의 개체를 만드는 데 유용

객체 인터페이스 지정
- 올바른 인터페이스와 다양한 인터페이스 간의 관계를 식별하는 것
- 인터페이스의 올바른 구성을 식별하려면 여러 번 반복해야한다.

객체 구현 지정
- 구현(구체적 클래스)이 아닌 인터페이스(유형)에 프로그램과 같은 지침을 제공하며, 이로 인해 객체 지향 코드에 가까워진다.

올바른 재사용 매커니즘 보장
- 상속 사용 시기, 구성 사용 시기, 매개 변수 유형을 사용할때, 올바른 설계 결정인지등, 재사용 가능하고 유지 관리가 가능한 코드를 설계에 대한 결정을 내릴 때 유용

런타임 및 컴파일 시간 구조 관련
- 디자인 패턴에 대한 지식은 숨겨진 일부를 명백하게 만들어 줄 수 있다.

변화를 위한 설계
- 어떤 행동의 요소가 가장 변화할 가능성이 높다고 생각한다면, 그 행동을 한 곳에서 추상화
- 시스템 구조의 일부 측면이 다른 측면과 독립적으로 변화하도록 지원하므로 강력한 시스템을 구족 할 수 있다.

## 디자인 패턴 유형
1. 생성 패턴(Creational Pattern)
    - 객체의 생성에 관련된 패턴
    - 객체의 생성과 조합을 캡슐화 해서 특정 객체가 생성되거나 변경되어도 프로그램 구조에 영향을 크게 받지 않도록 유연성을 제공하는 패턴
2. 구조 패턴(Structural Pattern)
    - 클래스나 객체를 조합해서 더 큰 구조를 만드는 패턴
    - 예를 들어 서로 다른 인터페이스를 지닌 2개의 객체를 묶어 단일 인터페이스를 제공하거나 객체들을 서로 묶어 새로운 기능을 제공하는 패턴
3. 행위 패턴(Behavioral Pattern)
    - 객체나 클래스 사이의 알고리즘이나 책임 분배에 관련된 패턴
    - 한 객체가 혼자 수행할 수 없는 작업을 여러 개의 객체로 어떻게 분배하는지, 또 그렇게 하면서도 객체 사이의 결합도를 최소화하는 것에 중점을 둔다.

## 디자인 패턴 샘플(문제/해결)
- [Iterator Pattern based Library](src/main/kotlin/behavioral/iterator/library/README.md)
- [TemplateMethod based Build System](src/main/kotlin/behavioral/templatemethod/buildsystem/README.md)
- [Composite based Employee Organization](src/main/kotlin/structural/composite/employee/README.md)
- [Proxy pattern based DatabaseProxy](src/main/kotlin/structural/proxy/database/README.md)
- [Singleton based DB Connection, Application Config](src/main/kotlin/creational/singleton/database/README.md)
- [Adapter Pattern based Unified PaymentSystem](src/main/kotlin/structural/adapter/payment/README.md)
- [Decorator Pattern based Coffee](src/main/kotlin/structural/decorator/coffee/README.md)
- [Observer Pattern based News Publish](src/main/kotlin/behavioral/observer/news/README.md)
- [Add the payment process with the strategy pattern](src/main/kotlin/behavioral/strategy/payment/README.md)
- [FactoryMethod based Lotto generator](src/main/kotlin/creational/factorymethod/README.md)
- [Bridge Pattern based Cross Platform UI Elements](src/main/kotlin/structural/bridge/uielement/README.md)
- [Command Pattern based Text Editor](src/main/kotlin/behavioral/command/editor/README.md)
- [State Pattern based OrderProcess](src/main/kotlin/behavioral/state/order/README.md)
- [Chain of Responsibility Pattern based Logger System](src/main/kotlin/behavioral/chain/logger/README.md)
- [Builder Pattern based HTMLDocument](src/main/kotlin/creational/builder/html/README.md)
- [Mediator Pattern based Smart Home](src/main/kotlin/behavioral/mediator/smarthome/README.md)
- [Memento Pattern based Text Editor Undo System](src/main/kotlin/behavioral/memento/README.md)
- [Visitor Pattern based File System](src/main/kotlin/behavioral/visitor/file/README.md)
- [Flyweight Pattern based Font Rendering System](src/main/kotlin/structural/flyweight/texteditor/README.md)
- [Interpreter Pattern based Calculator](src/main/kotlin/behavioral/interpreter/calculator/README.md)
- [Abstract Factory Pattern based GUI Application](src/main/kotlin/creational/abstractfactory/gui/README.md)
- [FactoryMethod Pattern based Payment System](src/main/kotlin/creational/factorymethod/payment/README.md)
- [Prototype Pattern based Game Character System](src/main/kotlin/creational/prototype/character/README.md)
- [Composite Pattern based File System](src/main/kotlin/structural/composite/filesystem/README.md)
- [Publish-Subscribe Pattern based Event Bus System](src/main/kotlin/behavioral/pubsub/eventbus/README.md)
- [Producer-Consumer Pattern based Job Queue System](src/main/kotlin/concurrent/producerconsumer/jobqueue/README.md)
- [Cache Pattern based Expensive Operating System](src/main/kotlin/structural/cache/expensive/README.md)
- [Circuit Breaker Pattern based Remote Service System](src/main/kotlin/stability/circuitbreaker/service/README.md)
- [CQRS Pattern based Inventory Management System](src/main/kotlin/architecture/cqrs/inventory/README.md)
- [Repository Pattern based Customer Management System](src/main/kotlin/structural/repository/customer/README.md)
- [Unit of Work Pattern based Order Processing System](src/main/kotlin/structural/unitofwork/order/README.md)
- [Specification Pattern based Product Filter System](src/main/kotlin/behavioral/specification/product/README.md)
- [Module Pattern based Http Configuration](src/main/kotlin/structural/module/configuration/README.md)
- [Resource Acquisition Is Initialization(RAII) Pattern based Manage Resource](src/main/kotlin/raii/resource/README.md)
- [Facade Pattern based Video Conversion System](src/main/kotlin/structural/facade/videoconversion/README.md)
- [Service Locator Pattern based Notification System](src/main/kotlin/structural/servicelocator/notification/README.md)
- [Event Sourcing Pattern based Bank Account System](src/main/kotlin/architecture/eventsourcing/bank/README.md)
