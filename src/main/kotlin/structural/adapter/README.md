# Adapter Pattern
> 하나의 인터페이스를 다른 인터페이스로 변환하는 패턴</br>
> 맞지 않는 기존의 인터페이스를 현재 기준에 맞게 변환 시키는 역할

- 어떤 클래스가 십자 모양의 인터페이스를 가지고 있으며 클라이언트는 별모양의 인터페이스만 받을 수 있게 구현 되어있을때, 하나의 어댑터가 십자 모양의 인터페이스를 별모양으로 변경해준다면 인터페이스가 다르기 때문에 사용할 수 없었던 클래스를 사용할 수 있게 해준다.
- 어댑터 구조나 관점에 따라서 클래스를 감싸고 별 형태의 인터페이스만 내놓는걸로 보이기도 해서 Wrapper라고 불리기도 한다.

## 구현
- Base 클래스 선언
- 객체에서 인터페이스 상속

## 참고
- [강의 영상](https://www.youtube.com/watch?v=IHU-wDglGM0&list=PLDV-cCQnUlIaOFXCUv8vEMGxqzrrkGv_P&index=2)
- [코드](https://colab.research.google.com/github/NoCodeProgram/DesignPatterns/blob/main/Structural/adapterP.ipynb)
