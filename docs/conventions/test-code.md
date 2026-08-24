# 테스트 코드 작성 지침

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: Issue #40

이 문서는 GuardBench의 단위·통합·계약·아키텍처 테스트에 공통으로 적용할 최소 작성 기준이다. 새 테스트와 수정하는 테스트부터 적용하며, 기존 테스트 전체를 소급해 정리하지 않는다.

## 이름과 표시 이름

- 테스트 클래스 이름은 영어 `PascalCase`로 작성하고, 검증 대상과 목적 또는 테스트 종류를 드러낸 뒤 `Test`로 끝낸다. 예: `TestRunTest`, `ApiResponseContractTest`, `GuardBenchArchitectureTest`.
- 테스트 메서드 이름은 영어 `lowerCamelCase`로 작성하고 `검증 조건 또는 대상 + 기대 결과`가 드러나게 한다. `test1`, `success`, `works`처럼 검색만으로 의도를 알 수 없는 이름은 사용하지 않는다.
- 새로 추가하거나 의미를 변경하는 모든 JUnit 테스트 메서드에는 한글 문장형 `@DisplayName`을 작성한다. `@Test`뿐 아니라 다른 annotation으로 실행되는 테스트와 단순한 context load 또는 smoke test도 예외로 두지 않는다.
- `@DisplayName`은 조건과 기대 결과를 함께 표현한다. 필요한 HTTP status, 오류 코드, 클래스명 같은 기술 용어는 코드 표기와 같은 영문을 유지한다.
- 최상위 테스트 클래스의 `@DisplayName`은 선택 사항이다. 클래스 이름만으로 대상을 식별할 수 없을 때만 추가한다.
- `@Nested` 클래스에는 보고서의 상위 문맥을 나타내는 명사구형 `@DisplayName`을 반드시 작성한다.

메서드 이름은 코드 검색과 실패 위치 식별을 위한 계약이고, `@DisplayName`은 사람이 읽는 실행 보고서를 위한 계약이다. 둘 중 하나로 다른 하나를 대신하지 않는다.

## `@Nested` 사용

같은 대상이나 선행 조건을 공유하는 테스트가 둘 이상이고, 다음 중 하나 이상을 만족할 때 `@Nested`를 사용한다.

- 그룹 이름이 실행 보고서에 의미 있는 문맥을 추가한다.
- 그룹 안의 fixture와 lifecycle을 다른 그룹과 분리할 필요가 있다.

테스트가 하나뿐이거나 파일을 짧게 보이게 하는 목적이라면 사용하지 않는다. 계층은 검증 대상 → 상황 또는 기능 → 기대 동작을 읽을 수 있게 구성하고, 보통 한 단계로 제한한다. 더 깊은 계층이 필요하면 테스트 클래스의 책임이 지나치게 넓지 않은지 먼저 검토한다.

## 테스트 본문

- 본문은 Given–When–Then 또는 Arrange–Act–Assert 흐름으로 읽혀야 한다. 준비가 필요 없는 테스트는 Given 또는 Arrange를 생략할 수 있다.
- 기본 구분 수단은 빈 줄이다. 코드만으로 경계가 불명확할 때만 `// given`, `// when`, `// then` 또는 `// arrange`, `// act`, `// assert` 주석을 사용한다. MockMvc처럼 실행과 검증을 하나의 fluent chain으로 표현하는 API는 억지로 분리하지 않는다.
- 한 테스트 안에서 두 표현을 섞지 않는다. 팀의 Java 테스트 예시는 Given–When–Then을 기본 용어로 사용한다.
- 한 테스트는 하나의 동작을 검증한다. 같은 결과 계약을 설명하는 여러 assertion은 함께 둘 수 있다.
- 테스트 간 실행 순서와 공유된 변경 가능 상태에 의존하지 않는다.

```java
@Test
@DisplayName("201 응답의 httpStatus는 실제 HTTP Status와 같다")
void createdResponseMatchesHttpStatus() throws Exception {
    mockMvc.perform(post(BASE + "/probes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"safety\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.httpStatus").value(201))
            .andExpect(jsonPath("$.data.name").value("safety"));
}
```

## fixture와 test double

fixture는 테스트에 필요한 실제 값과 객체 상태를 뜻한다. test double은 협력 객체를 대신하며 목적에 따라 다음 용어를 사용한다.

| 종류 | 용도 | 이름 예시 |
| --- | --- | --- |
| fixture | 입력·상태·위반 구조 구성 | `testRunFixture`, `architectureFixture` |
| fake | 동작하는 단순 인메모리 구현 | `testRunRepositoryFake` |
| stub | 정해진 값이나 오류 반환 | `clockStub`, `providerStub` |
| mock | 호출·인자·횟수 상호작용 검증 | `eventPublisherMock` |

- 한 테스트에서만 쓰는 fixture는 해당 메서드의 지역 변수로 둔다.
- 같은 클래스에서 반복되면 의미 있는 factory method 또는 `@BeforeEach`로 올린다. 값 차이를 숨기는 거대한 공통 setup은 만들지 않는다.
- 여러 테스트 클래스가 공유할 때만 `src/test/java` 아래 대상 bounded context의 `support.fixture`, `support.fake`, `support.stub`처럼 역할을 드러내는 패키지로 분리한다. framework mock은 이를 사용하는 테스트 클래스에 선언한다. production source에는 테스트 지원 코드를 두지 않는다.
- 아키텍처 규칙을 의도적으로 위반하는 클래스는 기존 구조처럼 `architecturefixture` 패키지에 격리하고, 정상 production 구조 검사 대상에 포함하지 않는다.
- 변수와 클래스 이름에는 실제 역할을 반영한다. fake를 mock이라 부르거나 단순 입력 객체를 mock이라 부르지 않는다.
- 프레임워크 mock은 상호작용이 계약일 때만 사용한다. 상태와 반환값으로 동작을 설명할 수 있으면 실제 객체, fixture, stub 또는 fake를 우선한다.

## 테스트 종류별 적용

| 종류 | 공통 규칙에서 특히 강조할 점 |
| --- | --- |
| 단위 테스트 | 검증 대상과 협력 객체의 역할을 이름에 드러내고 필요한 최소 fixture만 구성한다. |
| 통합 테스트 | 통합 경계, 실제로 사용하는 infrastructure와 대체한 경계를 클래스 설명이나 annotation 구성으로 드러낸다. |
| 계약 테스트 | 승인 계약 문서를 Javadoc `@see` 또는 가까운 주석으로 연결하고 외부 관찰 결과를 검증한다. |
| 아키텍처 테스트 | 규칙 이름과 실패 메시지로 위반 대상을 식별할 수 있게 하고, 위반 감지 fixture를 정상 검사 대상과 격리한다. |

Spring slice test나 context load test 같은 smoke test도 이름과 `@DisplayName`, 독립 실행 규칙을 따른다. 테스트 종류에 맞는 annotation과 도구 선택은 이 문서의 범위가 아니다.

## 리뷰 체크리스트

- 클래스와 메서드 이름만으로 검증 대상과 기대 결과를 검색할 수 있는가?
- 모든 새 테스트 메서드와 `@Nested` 그룹에 기준에 맞는 `@DisplayName`이 있는가?
- `@Nested`가 실제 공유 문맥을 나타내며 불필요하게 깊지 않은가?
- 본문이 하나의 Given–When–Then 흐름으로 읽히는가?
- fixture와 test double의 위치·이름·종류가 실제 역할과 일치하는가?
- 테스트가 실행 순서나 공유된 변경 가능 상태에 의존하지 않는가?
- 계약·아키텍처 테스트가 근거와 실패 원인을 식별 가능하게 하는가?
