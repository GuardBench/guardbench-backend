# GuardBench Backend

> Amazon Bedrock Guardrails 정책 변경을 안전하게 검증하기 위한 AI Security Regression Test Platform

GuardBench는 운영 중인 **Baseline Guardrail**과 배포 후보 **Candidate Guardrail**에 동일한 Safety Test Suite를 실행하고, 정책 변경으로 인해 발생한 기대 동작 위반과 회귀를 분석하는 테스트 플랫폼입니다.

단순히 두 결과의 차이를 비교하는 데 그치지 않고, 사람이 정의한 Expected Result를 기준으로 Candidate의 요구사항 충족 여부를 검증하고, Baseline 대비 변화가 보안 회귀인지 사용성 회귀인지 구분합니다.

MVP는 Amazon Bedrock Guardrails를 System Under Test(SUT)로 사용하며, 고객센터 챗봇을 Reference Domain으로 제공합니다. GuardBench Core 자체는 특정 비즈니스 도메인에 종속되지 않도록 설계되었습니다.

---

## 주요 기능

- **Safety Test Suite 관리**
  - Test Suite, Test Case, Test Case Revision 기반 테스트 정의
  - 입력, Expected Result, Severity, Category 등 테스트 정책 관리

- **재현 가능한 Test Run**
  - 실행 시점의 Test Case Revision을 Snapshot으로 고정
  - Baseline과 Candidate가 동일한 Snapshot을 공유
  - 실제 실행할 Guardrail Target을 Test Run 시작 전에 고정

- **Candidate Materialization**
  - 배포 후보 Working Draft(DRAFT)를 numbered Guardrail Version으로 materialize
  - 테스트한 Candidate와 실제 배포 Candidate가 동일하도록 `Test what you deploy` 원칙 적용

- **Candidate Assertion**
  - Expected Result와 Candidate Actual Result 비교
  - Candidate가 정의된 기대 동작을 충족하는지 PASS / FAIL 판정

- **Regression Analysis**
  - Baseline과 Candidate의 비교 가능 여부를 별도로 확인
  - Comparable한 결과에 대해 보안 회귀, 사용성 회귀, 개선 등의 변화 유형 분류

- **Execution Reliability 관리**
  - Guardrail API 오류, 네트워크 오류, timeout 등 실행 실패를 정책 판정과 분리
  - 신뢰할 수 없는 Test Run은 Quality Gate의 정책 품질 PASS / FAIL로 오판하지 않음

- **Quality Gate**
  - Assertion 및 Regression Metrics를 집계
  - Severity와 정책 임계값을 기준으로 최종 PASS / FAIL / NOT_EVALUATED 판정

---

## 핵심 개념

| 개념 | 설명 |
| --- | --- |
| **Baseline** | 현재 운영 기준으로 사용하는 immutable numbered Guardrail Version |
| **Candidate** | 이번 Test Run에서 검증하는 배포 후보 Target |
| **Test Case Revision** | 특정 시점의 실행 가능한 테스트 정의 버전 |
| **Test Case Snapshot** | Test Run 시작 시 Revision의 내용을 고정한 실행용 Snapshot |
| **Assertion** | Expected Result와 Candidate Actual Result 비교 |
| **Comparability** | Baseline과 Candidate를 공정하게 직접 비교할 수 있는지 판단 |
| **Change Classification** | Comparable한 경우 변화의 의미를 분류 |
| **Quality Gate** | Test Run 전체 결과를 기준으로 배포 가능 여부를 판단 |

---

## 동작 흐름

```text
Test Run 생성
    ↓
Baseline Target Resolution
    ↓
Candidate Materialization
    ↓
Test Case Revision 선택 및 Snapshot 생성
    ↓
Baseline / Candidate Execution
    ↓
Result Normalization
    ├─ Candidate Assertion
    └─ Comparability Check
           ↓
       Change Classification
    ↓
Metrics Aggregation
    ↓
Execution Reliability 확인
    ↓
Quality Gate
```

GuardBench는 Amazon Bedrock의 원본 응답을 판정 로직에서 직접 사용하지 않습니다. Bedrock Adapter와 Result Normalizer를 통해 Core가 사용하는 `ActualResult`로 변환한 뒤 Assertion과 Change Classification을 수행합니다.

---

## 기술 구성

### Backend

- Java
- Spring Boot
- Gradle

### AWS

- Amazon Bedrock Guardrails
- AWS SDK for Java
- Amazon SQS

### Persistence

- Relational Database
- Test Run, Snapshot, Execution 및 평가 결과에 대한 Audit History 보존

비동기 실행 인프라와 Core Domain 로직을 분리하여, 실행 방식이 변경되더라도 Assertion, Comparability, Change Classification, Quality Gate 규칙이 영향을 받지 않도록 구성합니다.

---

## 사용 방법

### 1. 사전 준비

GuardBench를 실행하기 전에 다음 항목이 필요합니다.

- AWS 계정 및 사용 가능한 자격 증명
- Amazon Bedrock Guardrails 사용 권한
- 테스트할 Guardrail과 운영 Baseline Version
- Candidate로 사용할 Guardrail configuration
- 애플리케이션에서 사용할 Database 및 AWS 환경 설정

### 2. 프로젝트 실행

저장소를 clone한 후 Gradle을 이용해 애플리케이션을 실행합니다.

```bash
git clone https://github.com/GuardBench/guardbench-backend.git
cd guardbench-backend
./gradlew bootRun
```

Windows 환경에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat bootRun
```

### 3. Safety Test Suite 준비

검증하려는 정책에 맞는 Test Suite와 Test Case를 등록합니다.

각 Test Case Revision에는 최소한 다음과 같은 테스트 정보가 포함됩니다.

- 입력 데이터
- Expected Result
- Severity
- Category

도메인별 정책은 GuardBench Core의 조건문으로 구현하지 않고 Test Case 데이터로 표현합니다.

### 4. Test Run 실행

Test Run을 생성하면 GuardBench는 다음 과정을 수행합니다.

1. Baseline Guardrail Version을 확인합니다.
2. Candidate configuration을 immutable numbered Version으로 고정합니다.
3. 실행 대상 Test Case Revision을 Snapshot으로 생성합니다.
4. 동일 Snapshot을 Baseline과 Candidate에 각각 실행합니다.
5. Candidate Assertion과 Baseline 대비 변화 분석을 수행합니다.
6. 결과 Metrics를 집계하고 Quality Gate를 계산합니다.

### 5. 결과 확인

Test Run 결과에서는 다음 항목을 확인할 수 있습니다.

- Candidate Assertion 결과
- Baseline / Candidate Execution 상태
- Comparability Status
- Change Type
- Security / Usability Regression Metrics
- Execution Reliability
- 최종 Quality Gate 결과

---

## 판정 모델

GuardBench는 실행 결과를 하나의 PASS / FAIL 또는 Regression 상태로 단순화하지 않습니다.

### Assertion

```text
Expected Result + Candidate Actual Result
→ PASS / FAIL
```

Candidate 자체가 요구사항을 만족하는지 판단합니다.

### Comparability

```text
Baseline Actual Result + Candidate Actual Result
→ COMPARABLE / NOT_COMPARABLE
```

두 결과를 직접 비교할 수 있는지를 판단합니다.

### Change Classification

```text
Expected Result
+ Baseline Actual Result
+ Candidate Actual Result
→ Change Type
```

Comparable한 경우에만 다음과 같은 변화의 의미를 분류합니다.

- `NO_CHANGE`
- `SECURITY_REGRESSION`
- `USABILITY_REGRESSION`
- `IMPROVEMENT`
- `POLICY_BEHAVIOR_CHANGED`

---

## 설계 원칙

### Test what you deploy

Quality Gate를 통과한 Candidate와 실제 배포되는 Guardrail Version이 동일해야 합니다. 따라서 배포 승인용 Test Run에서는 mutable DRAFT를 직접 테스트 Target으로 사용하지 않습니다.

### Reproducible Test Run

하나의 Test Run이 시작되면 다음 요소는 변경되지 않습니다.

- Test Case Snapshot
- Resolved Baseline Target
- Resolved Candidate Target

이를 통해 테스트 실행 도중 Test Case나 Working Draft가 수정되더라도 이미 시작된 Test Run의 기준이 바뀌지 않도록 합니다.

### Domain-Agnostic Core

고객센터 챗봇은 GuardBench를 검증하기 위한 Reference Domain일 뿐입니다. 특정 산업의 정책은 Test Case Revision 데이터로 표현하며, Core Domain은 다른 Safety Test Suite에도 동일하게 적용할 수 있도록 유지합니다.

### Execution과 Policy Evaluation의 분리

API 오류나 timeout으로 결과를 얻지 못한 상태와 Candidate가 정책을 위반한 상태는 서로 다르게 취급합니다.

```text
Execution Error ≠ Assertion Failure ≠ NOT_COMPARABLE
```

실행 신뢰성이 충분하지 않은 Test Run은 정책 품질 실패로 처리하지 않고 `NOT_EVALUATED` 상태로 관리할 수 있습니다.

---

## Project Scope

GuardBench MVP의 핵심 범위는 Amazon Bedrock Guardrails 정책 변경에 대한 회귀 검증입니다.

- 동일 Safety Test Suite의 Baseline / Candidate 실행
- Candidate Assertion
- Regression 및 Change Classification
- Metrics Aggregation
- Quality Gate
- Test Run Audit History
- 비동기 Test Execution

GuardBench는 Guardrail의 출력 자체를 정답으로 간주하지 않습니다. **Expected Result는 테스트 작성자가 정의하고, GuardBench는 이를 기준으로 실행 결과를 평가합니다.**

---

## Repository

이 저장소는 GuardBench의 Backend 애플리케이션을 관리합니다.

GuardBench 프로젝트의 테스트 정의, 실행 오케스트레이션, Amazon Bedrock Guardrails 연동, 결과 정규화, Assertion / Regression 분석 및 Quality Gate 계산을 담당합니다.
