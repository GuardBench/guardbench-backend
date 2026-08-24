# 커밋 컨벤션

> Status: APPROVED
> Owner: Team
> Last reviewed: 2026-08-23
> Canonical source: GitHub
> Origin: [Notion 커밋 컨벤션](https://app.notion.com/p/3c0eeed6b62d813382bdd56fd910c66e)

기본 형식은 `<type>[optional scope]: <description>`이다.

| type | 용도 | 예시 |
| --- | --- | --- |
| `feat` | 새 기능 | `feat(testcase): 테스트 케이스 등록 API 추가` |
| `fix` | 버그 수정 | `fix(evaluation): 중복 판정 저장 방지` |
| `docs` | 문서만 변경 | `docs(api): 오류 응답 예시 추가` |
| `style` | 동작 없는 서식 변경 | `style: formatter 결과 반영` |
| `refactor` | 기능 변화 없는 구조 개선 | `refactor(testrun): 실행 준비 책임 분리` |
| `test` | 테스트 추가·수정 | `test(evaluation): 회귀 분류 테스트 추가` |
| `perf` | 성능 개선 | `perf(testrun): 목록 조회 쿼리 최적화` |
| `build` | 빌드·의존성 | `build: Bedrock SDK 의존성 추가` |
| `ci` | CI/CD | `ci: OpenAPI 검증 추가` |
| `chore` | 기타 유지보수 | `chore: 사용하지 않는 설정 제거` |
| `revert` | 이전 변경 되돌림 | `revert: 응답 캐시 적용 취소` |

scope는 현재 패키지·도메인 용어와 맞춘다. 예: `testdefinition`, `testrun`, `evaluation`, `guardrail`, `api`, `infra`.

본문은 변경 이유가 제목만으로 불명확할 때 쓴다. 관련 Issue는 footer에 `Refs: #42`로 연결한다. 자동 종료가 의도된 경우에만 `Closes: #42`를 사용한다.

호환성이 깨지면 `feat(api)!: 응답 필드명 변경`처럼 `!`를 붙이거나 footer에 다음처럼 기록한다.

```text
BREAKING CHANGE: resultStatus를 status로 변경했다.
```

한 커밋에는 한 목적만 담고, 커밋 전 diff와 관련 테스트를 확인한다. 기준은 [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)이다.
