# 옵저버(Observer) 패턴
옵저버 패턴은 `행위(Behavioral)` 패턴의 하나로, 
`객체 간의 일대다(one-to-many) 의존 관계`를 정의하여 한 객체의 상태 변화가 다른 객체들에게 **자동으로 통지되고 갱신되는 패턴**입니다.

## 문제점
- 객체 간 강한 결합
- 새로운 구독자 추가 시 발행자 코드 수정 필요
- 확장성과 유연성 부족

## 옵저버 패턴의 주요 장점
1. 느슨한 결합 
   - 주제(Subject)와 옵저버(Observer) 간 독립적 변경 가능 
   - 새로운 구독자 쉽게 추가 가능
2. 개방-폐쇄 원칙 준수
   - 기존 코드 수정 없이 새로운 옵저버 추가 가능
3. 동적 관계 설정
   - 런타임에 구독/구독 취소 가능

## 실제 활용 사례
1. 이벤트 처리 시스템 
2. GUI 컴포넌트 업데이트 
3. 분산 이벤트 핸들링 실시간 데이터 스트리밍 
4. 모델-뷰-컨트롤러(MVC) 아키텍처

## 주의사항
- 너무 많은 옵저버로 인한 성능 저하 가능성
- 순환 참조 및 예기치 못한 업데이트 주의
- 메모리 누수 방지를 위해 구독 해제 중요

## 결론
옵저버 패턴은 객체 간 일대다 종속성을 정의하고, 한 객체의 상태 변경이 다른 객체들에게 자동으로 통보되는 유연한 디자인 패턴입니다.