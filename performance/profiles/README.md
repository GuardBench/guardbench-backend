# Performance Profiles

성능 실험은 다음 네 개의 개념을 분리한다.

1. **Dataset / Dataset Size**: 한 TestRun이 처리할 TestCase 집합과 개수다. 현재 확정된 Dataset은 `baseline-v1` 78건뿐이며, `SMALL/MEDIUM/LARGE`의 경계값은 아직 정하지 않는다.
2. **Test Type**: 실험 목적이다. Runner는 `SMOKE`, `LOAD`, `PEAK`, `STRESS`, `SOAK`를 허용한다.
3. **Workload**: 시스템에 가하는 부하 값이다. Profile의 `concurrent_test_runs`, `ramp_up_seconds`, `duration_seconds`, `max_iterations_per_vu`, timeout/polling 값이 여기에 속한다.
4. **Infrastructure Capacity**: ECS task 수, RDS 사양, SageMaker endpoint capacity처럼 시스템이 공급하는 자원이다. Performance Profile이 소유하지 않고 Terraform/배포 설정이 소유한다.

따라서 Profile은 **Test Type + Workload + acceptance criteria + Application Target**을 정의한다. Dataset은 `performance/datasets/`에서 별도로 선택하고, Infrastructure Capacity는 배포 입력과 Infrastructure revision으로 별도 추적한다.

현재 TestRun은 Application Target 응답을 SageMaker Response Behavior Classifier로 분류한 뒤 Assertion/Quality Gate까지 비동기로 처리한다. Profile은 classifier 설정을 입력으로 받지 않으며 classifier endpoint/prompt 등은 Backend 배포 설정이 소유한다.

## 현재 확정된 Profile

`smoke.yaml`이 현재 유일하게 수치까지 확정된 canonical Profile이다.

- Test Type: `SMOKE`
- Dataset: 별도 선택, 현재 기본값 `baseline-v1` 78 TestCases
- concurrent TestRuns: `1`
- max iterations per VU: `1`
- completion timeout: `300s`
- polling interval: `2s`

`small.yaml`은 기존 Runner/문서 참조와의 호환을 위해 같은 설정을 유지하는 alias다. 새 실행과 새 문서는 `smoke.yaml`을 사용한다. 여기서 `small`은 Dataset Size를 뜻하지 않는다.

LOAD/PEAK/STRESS/SOAK의 구체적인 workload 값과 Dataset Size의 `SMALL/MEDIUM/LARGE` 경계는 사전에 고정하지 않는다. Smoke 결과와 이후 단계적 실험 결과를 보고 정한다. 새 Profile은 Runner 핵심 로직에 타입별 분기문을 추가하지 않고 동일 schema로 추가한다.
