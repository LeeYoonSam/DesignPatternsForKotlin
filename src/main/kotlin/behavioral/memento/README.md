# 메멘토(Memento) 패턴

## 설명
메멘토 패턴은 행위(Behavioral) 디자인 패턴으로, 객체의 이전 상태를 저장하고 복원할 수 있게 해주는 패턴입니다. 캡슐화를 위반하지 않으면서 객체의 상태를 외부에 저장하고 필요할 때 복원할 수 있습니다.

## 문제점
- 객체 상태 저장/복원의 어려움
- 캡슐화 위반 위험
- 실행 취소 기능 구현의 복잡성
- 상태 히스토리 관리의 어려움
- 메모리 사용량 증가

## 메멘토 패턴의 주요 장점
1. 캡슐화 유지
    - 객체의 내부 상태를 외부로 노출하지 않음
    - 객체의 정보 은닉 보장
2. 상태 복원 용이성
    - 객체의 이전 상태를 쉽게 복원
    - 실행 취소 기능 구현 용이
3. 리팩토링 유연성
    - 상태 저장 로직 분리
    - 코드 유지보수성 향상

## 주요 구성 요소
1. Originator
    - 상태를 가지는 객체
    - 메멘토 생성 및 복원 담당
2. Memento
    - 상태 스냅샷을 저장하는 객체
    - 불변성 유지
3. Caretaker
    - 메멘토 객체 관리
    - 상태 히스토리 보관

## 실제 활용 사례
1. 텍스트 에디터 실행 취소
2. 게임 세이브 포인트
3. 데이터베이스 트랜잭션
4. 그래픽 편집기 히스토리
5. 계산기 메모리 기능

## 주의사항
- 메모리 사용량 관리
- 상태 저장 비용
- 저장 대상 상태 선정
- 복원 시점 관리

## 결론
메멘토 패턴은 객체의 상태를 안전하게 저장하고 복원할 수 있게 해주는 유용한 디자인 패턴입니다.