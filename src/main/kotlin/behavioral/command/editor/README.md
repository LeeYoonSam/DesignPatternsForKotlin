# 커맨드(Command) 패턴

## 설명
커맨드 패턴은 행위(Behavioral) 디자인 패턴으로, 요청을 객체로 캡슐화하여 매개변수화된 클라이언트를 만들고, 요청을 대기시키거나 로깅하며, 취소 가능한 연산을 지원합니다.

## 문제점
- 작업 요청과 실행의 강한 결합
- 실행 취소/재실행 구현의 어려움
- 작업 이력 관리의 복잡성
- 새로운 기능 추가 시 코드 수정 필요

## 커맨드 패턴의 주요 장점
1. 단일 책임 원칙 준수
    - 작업 실행 객체와 호출 객체의 분리
    - 각 커맨드의 독립적인 책임
2. 확장성 향상
    - 기존 코드 수정 없이 새로운 커맨드 추가
    - 복합 커맨드 구현 용이
3. 작업 제어 향상
    - 실행 취소/재실행 기능 구현 용이
    - 작업 이력 관리 및 로깅 가능

## 주요 구성 요소
1. Command
    - 작업 실행을 위한 인터페이스
    - execute(), undo() 등의 메서드 정의
2. ConcreteCommand
    - 실제 작업을 수행하는 구현체
    - Receiver와 작업을 연결
3. Invoker
    - 커맨드 실행을 요청하는 객체
    - 작업 이력 관리 가능
4. Receiver
    - 실제 작업을 수행하는 객체

## 실제 활용 사례
1. 텍스트 에디터의 실행 취소/재실행
2. 리모컨 버튼 설정
3. 트랜잭션 관리
4. 매크로 기능 구현
5. 게임의 입력 처리

## 주의사항
- 커맨드 객체 수 증가
- 복잡한 실행 취소 로직
- 메모리 사용량 고려
- 동시성 처리 주의

## 결론
커맨드 패턴은 작업의 실행을 객체화하여 유연한 실행 제어와 확장성을 제공하는 유용한 디자인 패턴입니다.