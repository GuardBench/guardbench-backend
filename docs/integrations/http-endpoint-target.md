# HTTP Endpoint Application Target Adapter

> Status: APPROVED
> Owner: Backend
> Related Issue: #115, #125
> Target architecture: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)

## 역할

이 Adapter는 `TestCaseSnapshot.input`을 AI Application에 전달하고 자연어 `ApplicationResponse`를
TestRun이 소유한 `TargetExecutionPort` 값으로 반환한다. Application 실행에서는 `ALLOW`나 `BLOCK`을
만들지 않는다. 그 판정은 후속 Evaluator Adapter의 책임이다.

## MVP HTTP 계약

```http
POST {endpoint_url}
Content-Type: application/json
Accept: application/json

{"input":"<snapshot input>"}
```

성공 응답은 `2xx`와 `application/json` Content-Type을 사용하며 다음 객체만 허용한다.

```json
{"response":"<non-blank natural language response>"}
```

추가 필드, 배열·문자열·null 응답, 빈 문자열, 빈 본문, 1 MiB 초과 본문은
`PROVIDER_RESPONSE_INVALID`다. URL redirect는 따라가지 않는다.

## 오류·retry 경계

| 상황 | `TargetFailureCode` | Worker terminal 의미 |
| --- | --- | --- |
| URL이 없거나 등록된 Target이 없음 | `TARGET_NOT_FOUND` | `FAILED` |
| userinfo/fragment/비 HTTP URL 또는 접근 정책 위반 | `TARGET_CONFIGURATION_INVALID` | `FAILED` |
| HTTP 404 | `TARGET_NOT_FOUND` | `FAILED` |
| HTTP 401/403 | `TARGET_ACCESS_DENIED` | `FAILED` |
| 그 밖의 HTTP 4xx | `TARGET_CONFIGURATION_INVALID` | `FAILED` |
| HTTP 5xx 또는 transport I/O 실패 | `PROVIDER_UNAVAILABLE` | `FAILED` (Worker retry 소진 후) |
| request timeout 또는 interrupt | `PROVIDER_TIMEOUT` | `TIMED_OUT` (Worker retry 소진 후) |
| redirect, Content-Type, JSON shape 위반 | `PROVIDER_RESPONSE_INVALID` | `FAILED` |

Adapter 자체는 retry하지 않는다. 한 SQS 메시지 수신당 Application 호출을 한 번만 수행하고,
기존 execution claim의 최대 3회 재전달 경계가 at-least-once 재시도를 담당한다. Claim lease가
만료된 Worker의 결과 저장 차단은 기존 Worker 계약이 담당한다.

## URL 안전성

등록 값은 absolute `http`/`https` URL이며 host가 필요하고 userinfo와 fragment를 허용하지 않는다.
Worker는 기본적으로 DNS 결과의 loopback, private/site-local, link-local, multicast와 IPv6 unique-local
주소를 차단한다. 내부망 Application을 연결해야 하는 환경은 `allow-private-addresses`를 명시적으로
활성화해야 한다. 이 설정은 인증이나 네트워크 ACL을 대체하지 않으며, 배포 네트워크 egress 정책을
함께 적용해야 한다.

입력·응답 본문, endpoint URL, 인증 정보와 provider 원문은 일반 로그나 오류 결과에 기록하지 않는다.

## OpenAI-compatible Adapter — #125

`target.model`이 있으면 OpenAI-compatible Adapter를 선택한다. `model`이 없으면 위 generic HTTP
Adapter를 선택한다. 이 선택 기준은 사용자 API의 단일 `HTTP_ENDPOINT` 타입을 유지하면서 request
형식만 명시적으로 고정한다.

```http
POST {endpoint_url}
Content-Type: application/json
Accept: application/json

{"model":"<target.model>","messages":[{"role":"user","content":"<snapshot input>"}]}
```

성공 응답은 OpenAI-compatible response object에서 `choices[0].message.content`를 추출한다.
`choices`가 비어 있거나 첫 항목의 `message` 또는 `content`가 없거나, `content`가 문자열이
아니거나 비어 있으면 `PROVIDER_RESPONSE_INVALID`다. 응답 object의 다른 metadata는 허용하지만
streaming/SSE, tool/function calling과 multimodal content는 지원하지 않는다.

`model`은 TestRun 접수 시 사용자에게 받아 Target reference와 함께 저장하며, idempotency fingerprint에도
포함한다. 조회 응답의 generic Target은 `model: null`이고 OpenAI-compatible Target은 저장된 모델을
반환한다. API Key·Secret과 custom header는 이 계약에 포함하지 않는다.
