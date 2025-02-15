# 책임 연쇄(Chain of Responsibility) 패턴

## 설명
책임 연쇄 패턴은 행위(Behavioral) 디자인 패턴으로, 요청을 보내는 쪽과 요청을 처리하는 쪽을 분리하여 요청을 처리할 수 있는 객체가 둘 이상 존재하는 경우, 이를 연쇄적으로 연결해 처리합니다.

## 문제점
- 요청 처리 로직의 중앙 집중화
- 처리기 변경의 어려움
- 처리 순서 변경의 어려움
- 처리 과정의 모니터링 부족

## 책임 연쇄 패턴의 주요 장점
1. 결합도 감소
    - 요청자와 처리자의 분리
    - 처리자 간의 느슨한 결합
2. 유연성 증가
    - 처리 순서 동적 변경 가능
    - 새로운 처리자 쉽게 추가
3. 단일 책임 원칙 준수
    - 각 처리자가 특정 처리에만 집중
    - 처리 로직의 명확한 분리

## 주요 구성 요소
1. Handler
    - 요청을 처리하는 인터페이스
    - 다음 처리자 참조 관리
2. ConcreteHandler
    - 실제 요청 처리 구현
    - 처리 불가 시 다음 처리자에게 전달
3. Client
    - 요청을 처리 체인에 전달

## 실제 활용 사례
1. 로깅 시스템의 로그 레벨
2. 인증/인가 처리
3. 이벤트 처리 시스템
4. 필터 체인
5. 결제 처리 파이프라인

## 주의사항
- 처리 지연 가능성
- 처리 보장성 확인 필요
- 순환 참조 방지
- 체인 길이 관리

## 결론
책임 연쇄 패턴은 요청 처리의 유연성을 높이고 시스템의 확장성을 개선하는 효과적인 디자인 패턴입니다.