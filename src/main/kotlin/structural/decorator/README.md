# Decorator Pattern
> 데코레이터 패턴은 객체를 다른 객체로 래핑하여 객체에 동적으로 추가 책임을 부여합니다.<br/>
> 데코레이터는 기능 확장을 위해 서브클래싱에 대한 유연한 대안을 제공합니다.<br/>
> 래퍼 패턴이라고도 하는 구조적 디자인 패턴의 한 유형입니다.<br/> 
> 데코레이터는 이러한 객체를 새 동작을 포함하는 래퍼에 배치하여 객체에 새 동작을 첨부합니다.

## 패턴/문제를 해결하는 동기는 무엇입니까?
1. 서브클래스의 과부하로부터 우리를 구하기 위해 이것은 우리가 많은 서브클래스를 생성해야 하는 디자인 결정에 있고 여기에서 많은 서브클래스를 만드는 것이 현명한 결정이 아닐 때 데코레이터 패턴은 서브클래싱이 컴파일 타임에 이루어지고 데코레이터가 런타임에 실행
2. 객체에 대한 책임을 동적이고 투명하게 추가하려면 다른 객체에 영향을 주지 않고 Decorator Pattern 사용을 고려하십시오.
3. 개방/폐쇄 원칙을 정의하여 런타임에 개체에 추가 동작을 할당할 수 있어야 하는 경우 데코레이터 패턴을 사용합니다.
4. 상속을 사용하여 개체 동작을 확장할 수 없을 때 사용합니다.

## 디자인 문제:
기본 아이스크림에 추가하는 토핑에 따라 각기 다른 여러 유형의 아이스크림이 있는 솔루션을 설계합니다.

- Basic Icecreme = $ 0.60
- Chocolate toppings = + $1
- Mint toppings = + $1.50
- Nuts toppings = + $1

미래에 더 많은 토핑이 있을 가능성이 높고 새로운 기본 변형이 있을 수도 있습니다. 

솔루션은 확장 가능하고 확장 가능해야 합니다.

## 해결책
- 3가지 토핑과 1가지 기본 변형과 마찬가지로 7가지 조합이 있습니다.
- 4가지 기본 변형과 10가지 다른 토핑이 있다면 얼마나 많은 조합을 가질 수 있을까요?
- 이것은 하위 클래스 과부하의 경우입니다. 여기서 많은 하위 클래스를 만드는 것은 실용적이지 않습니다.
- 이러한 종류의 디자인 문제는 추가 동작을 제공하기 위해 개체를 다른 개체로 감싸는 데코레이터 패턴을 사용하여 해결할 수 있습니다.

## Solution design structure
<img src="https://cdn.hashnode.com/res/hashnode/image/upload/v1647767685835/4f1S9j0tU.jpg">

## 패턴 구성요소
- Component:
  - 책임을 동적으로 추가할 수 있는 개체에 대한 인터페이스를 정의합니다.
  - 래퍼 및 래핑된 개체에 대한 공통 인터페이스를 정의합니다.
- ConcreteComponent:
  - 기본적인 행동을 정의합니다.
  - 데코레이터를 사용하여 변경할 수 있는 객체입니다.
- Decorator:
  - 구성 요소 객체(래핑된 객체)에 대한 참조를 유지하고 구체 데코레이터가 구현할 기본 데코레이터를 유지합니다.
- ConcreteDecorator:
  - 런타임에 구성 요소에 동적으로 책임을 추가합니다.
- Client:
  - 각 구성 요소가 다른 구성 요소에 의해 래핑될 수 있는 계층 구조에서 여러 구성 요소를 래핑할 수 있습니다.


## 참고
- [완성 코드](https://github.com/adityachaudhari/DesignPatternsJava/tree/master/decorator-pattern)
- [Decorator Pattern — get the gist in 2 min.](https://medium.com/javadeveloperdiary-jdd/decorator-pattern-get-the-gist-in-2-min-3a3f84d85f38)
- [Decorator pattern - Java - Explained](https://ecommercearchitect.hashnode.dev/decorator-pattern-java-explained)