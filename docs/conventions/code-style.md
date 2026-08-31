# 코드 컨벤션

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-31
> Canonical source: GitHub
> Origin: [Notion 코드 컨벤션](https://app.notion.com/p/3c0eeed6b62d816a8028cc3261c3edf3)

- 백엔드는 Java·Spring Boot만 사용한다. 예: AWS SDK 호출은 Java Infrastructure Adapter에 둔다.
- Domain은 Spring MVC, AWS SDK, JPA, HTTP DTO를 import하지 않는다. 예: Bedrock response를 Domain method 인자로 전달하지 않는다.
- Controller는 Application Service를 호출한다. 예: Controller에서 JPA repository를 직접 호출하지 않는다.
- Domain 객체를 API DTO로 직접 노출하지 않는다. 예: `TestRun` 대신 `TestRunResultRes`를 반환한다.
- 응답 Body가 있으면 `ApiResponse<T>`를 사용하고 204는 Body가 없다.
- 실행 오류와 정책 판정을 분리한다. 예: timeout을 `AssertionStatus.FAIL`로 변환하지 않는다.
- 폐기된 `TestCaseRevision`을 다시 도입하지 않는다. 과거 Run 내부 동시 비교용 `ChangeResult`를 완료 TestRun 비교 모델로 그대로 재사용하지 않고 #119의 Regression 계약을 따른다.
- 미래 확장을 이유로 MVP를 과도하게 일반화하지 않는다. 예: 두 번째 provider가 없는데 공통 provider hierarchy를 만들지 않는다.

Formatter, line length, import 정렬, Lombok, `record`는 아직 강제하지 않는다. 테스트 fixture와 test double 스타일은 [테스트 코드 작성 지침](test-code.md)을 따른다.
