# DTO 네이밍

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-23
> Canonical source: GitHub
> Origin: [Notion DTO 네이밍](https://app.notion.com/p/3c0eeed6b62d8153b1f4fdffc39e328e)

기본 형식은 `{Domain}{UseCase}{Req|Res}`다.

- 생성 요청/결과: `TestSuiteCreateReq`, `TestSuiteCreateRes`
- 수정 요청/결과: `TestCaseUpdateReq`, `TestCaseUpdateRes`
- 상세 조회: `TestSuiteDetailRes`
- 목록 조회: `TestRunListRes`
- 상태 조회: `TestRunStatusRes`
- 결과 조회: `TestRunResultRes`
- 개별 결과 상세 조회: `TestRunResultDetailRes`

CRUD 단어보다 실제 유스케이스를 쓴다. `TestRunReadRes`나 `TestRunGetRes` 대신 `TestRunResultRes`를 사용한다. `Request`, `Response`, `Req`, `Res`를 혼용하지 않고 `Req`/`Res`로 통일한다.

`Dto`, `DTO`, `ApiRequest`, `ApiResponse` 같은 중복 접미사를 붙이지 않는다. 예: `TestRunApiCreateRequestDto`가 아니라 `TestRunCreateReq`다. 204 삭제처럼 Body가 없으면 `TestCaseDeleteRes`를 만들지 않는다. Domain 객체에는 `Req`/`Res`를 사용하지 않는다.

공통 envelope는 `ApiResponse<TestRunResultRes>`처럼 개별 Response DTO를 감싼다.
