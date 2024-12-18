# Composite Pattern

> 한 오브젝트의 그룹과 그 오브젝트의 싱글 인스턴스가 같은 타입으로 취급되는 패턴</br>
> 컴포지트 패턴을 통해서 오브젝트들을 트리 구조로 구성할수 있다.

## 패턴 설명

- 컴포지트 패턴에서는 `Base Interface` 를 `Component` 라고 부릅니다.
- 이를 상속받은 객체를 `Leaf` 라고 부릅니다.
- 상속받는 그룹을 `composite` 라고 부릅니다.
    - 오브젝트의 리스트
        - component 들이 들어갑니다.
        - 리스트 안에는 `leaf, composite` 모두 들어갈수 있습니다.
- 컴포지트 패턴이 트리구조를 나타내기 쉽습니다.

## 구현

- Base Interface Component 구현
- Component 를 상속받는 Leaf 클래스를 생성
- Component 를 상속받는 Composite 클래스를 생성
    - component 리스트를 가짐
    - component 를 추가하는 add 함수
    - component 에서 제공하는 fn을 구현하고 모든 component 의 fn 을 호출
- 모든 오브젝트에 대해 함수 fn 이 퍼져 나가서 Tree 구조를 만들었습니다.

## 장점

- 트리 구조가 아주 복잡할때 그 트리의 root 에서 함수 하나만 호출하면 컴포지트 패턴을 따라서 Leaf 까지 그 함수가 자동으로 호출이 됩니다.

## 결론

- 오브젝트를 같은 그룹으로 묶고 같은 인터페이스를 제공하는 컴포지트 패턴
- 핵심은 그룹과 오브젝트가 같은 인터페이스를 제공함으로써 root 에서 시작되는 함수콜이 트리안의 모든 콜이 함수로 퍼지는 구조 입니다.

## 참고

- [강의 영상](https://www.youtube.com/watch?v=XXvrHAsfTso&list=PLDV-cCQnUlIaOFXCUv8vEMGxqzrrkGv_P&index=8)
- [파이썬 코드](https://colab.research.google.com/github/NoCodeProgram/DesignPatterns/blob/main/Structural/compositeP.ipynb)
- 코틀린 코드
    - [Simple Composite Pattern](https://pl.kotl.in/6JCMyP5BM)
    - [동물원 예제](https://pl.kotl.in/ABGda5wDY)