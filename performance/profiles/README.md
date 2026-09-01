# Performance Profiles

Profile은 `무엇을 실행하는가`가 아니라 `얼마나 실행하는가`를 정의한다. Dataset은
`performance/datasets/`에서 별도로 선택하므로 같은 `baseline-v1`을 `SMOKE`, `LOAD` 또는
향후 결정될 `PEAK` Profile에 재사용할 수 있다.

`small.yaml`은 실행 경계를 검증하기 위한 보수적인 초기 Smoke Profile이다. `target`, `peak`,
`stress`, `soak`의 최종 Capacity Target 숫자는 별도 결정 전까지 추가하지 않는다. Profile을
복사할 때는 `test.type`, workload, acceptance criteria를 실행 전에 확정하고 결과에 사용한
원본 Profile을 함께 보존한다.
