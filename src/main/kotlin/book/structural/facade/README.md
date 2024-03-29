# Facade Design Pattern

> 디자인 패턴을 지칭하는 용어로 파사드를 사용하는 것은 건물 건축에서 직접 유래했습니다. 즉, 파사드는 일반적으로 건물의 나머지 부분보다 더 매력적으로 보이도록 만들어진 건물의 얼굴입니다. 프로그래밍에서 파사드는 구현의 지저분한 세부 사항을 숨기는 데 도움이 될 수 있습니다.

> 파사드 디자인 패턴 자체는 클래스 또는 인터페이스 제품군으로 작업하는 더 멋지고 간단한 방법을 제공하는 것을 목표로 합니다. 앞서 추상 팩토리 디자인 패턴을 다룰 때 클래스 패밀리의 개념에 대해 설명했습니다. 추상 팩토리 디자인 패턴은 관련 클래스를 만드는 데 중점을 두는 반면, 파사드 디자인 패턴은 클래스가 생성된 후 작업하는 데 중점을 둡니다.

이를 더 잘 이해하기 위해 `Abstract Factory` 디자인 패턴에 사용한 예제로 돌아가 보겠습니다. `Abstract Factory`를 사용하는 구성에서 서버를 시작할 수 있도록 라이브러리 사용자에게 일련의 지침을 제공할 수 있습니다:
- 주어진 파일이 `JSON` 파서로 파싱을 시도하여 `.json` 또는 `.yaml`인지 확인합니다.
- 오류가 발생하면 `YAML` 구문 분석기를 사용하여 구문 분석을 시도합니다.
- 오류가 없으면 결과를 추상 팩토리에 전달하여 필요한 객체를 생성합니다.

구성을 로드하려면 최소 3개의 서로 다른 인터페이스와 상호 작용해야 합니다:
- JSON 파서(2장, 생성 패턴으로 작업하기의 `추상 팩토리` 섹션에서 다룸)
- YAML 구문 분석기(2장, 생성 패턴으로 작업하기의 `추상 팩토리` 섹션에서 다룸)
- 서버 팩토리(2장, 생성 패턴으로 작업하기의 `추상 팩토리` 섹션에서 다룸)

구성 파일의 경로를 가져와 구문 분석하고 프로세스에 오류가 없으면 서버를 시작하는 단일 함수(startFromConfiguration())가 있으면 좋을 것입니다.

## 정리
- `Facade` 디자인 패턴은 복잡한 클래스 그룹을 사용하기 쉽게 만드는 것이 목표입니다.