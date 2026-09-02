# HTTP Endpoint Application Target Adapter

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-01
> Related Issue: #115, #125, #128
> Target architecture: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)

## 역할

이 Adapter는 `TestCaseSnapshot.input`을 OpenAI-compatible HTTP AI Application에 전달하고 자연어 `ApplicationResponse`를 TestRun이 소유한 `TargetExecutionPort` 값으로 반환한다. Application 실행에서는 `ALLOW`나 `BLOCK`을 만들지 않는다. 그 판정은 Evaluator Adapter의 책임이다.

MVP의 `HTTP_ENDPOINT`는 **OpenAI-compatible chat completions 계약만 지원한다.** 과거 generic `{"input": ...}` / `{"response": ...}` 계약은 지원하지 않는다.

## 공개 Target 계약

TestRun 생성 요청의 HTTP Target은 다음 값을 사용한다.

```json
{
  "type": "HTTP_ENDPOINT",
  "identifier": "https://example.com/v1/chat/completions",
  "model": "example-model",
  "revision": "optional-application-revision"
}
```

- `identifier`는 호출할 full HTTP/HTTPS endpoint URL이다.
- `model`은 필수이며 OpenAI-compatible request body에 전달된다.
- `revision`은 배포 버전, 모델 revision, commit 등 사용자가 Application을 재식별하기 위한 선택 문자열이다.
- API Key·Secret, OAuth, 범용 custom header는 MVP 계약에 포함하지 않는다.

## OpenAI-compatible 실행 계약

```http
POST {target.identifier}
Content-Type: application/json
Accept: application/json

{"model":"<target.model>","messages":[{"role":"user","content":"<snapshot input>"}]}
```

성공 응답은 HTTP `2xx`, `Content-Type: application/json`이어야 하며 `choices[0].message.content`의 비어 있지 않은 문자열을 자연어 Application response로 사용한다. 응답 object의 다른 metadata는 허용한다.

다음은 `PROVIDER_RESPONSE_INVALID`다.

- 빈 `choices`
- 첫 choice의 `message` 또는 `content` 누락
- 문자열이 아닌 `content`
- blank `content`
- malformed JSON 또는 JSON이 아닌 성공 응답
- 1 MiB를 초과하는 응답 본문
- redirect 응답

streaming/SSE, tool/function calling, multimodal content, Responses API는 지원하지 않는다.

## 오류·retry 경계

| 상황 | `TargetFailureCode` | Worker terminal 의미 |
| --- | --- | --- |
| URL이 없거나 등록된 Target이 없음 | `TARGET_NOT_FOUND` | `FAILED` |
| userinfo/fragment/비 HTTP URL 또는 접근 정책 위반 | `TARGET_CONFIGURATION_INVALID` | `FAILED` |
| model 누락 또는 blank | `TARGET_CONFIGURATION_INVALID` | `FAILED` |
| HTTP 404 | `TARGET_NOT_FOUND` | `FAILED` |
| HTTP 401/403 | `TARGET_ACCESS_DENIED` | `FAILED` |
| 그 밖의 HTTP 4xx | `TARGET_CONFIGURATION_INVALID` | `FAILED` |
| HTTP 5xx 또는 transport I/O 실패 | `PROVIDER_UNAVAILABLE` | `FAILED` (Worker retry 소진 후) |
| request timeout 또는 interrupt | `PROVIDER_TIMEOUT` | `TIMED_OUT` (Worker retry 소진 후) |
| redirect, Content-Type, JSON shape 위반 | `PROVIDER_RESPONSE_INVALID` | `FAILED` |

Adapter 자체는 retry하지 않는다. 한 SQS 메시지 수신당 Application 호출을 한 번만 수행하고 기존 execution claim의 최대 3회 재전달 경계가 at-least-once 재시도를 담당한다. Claim lease가 만료된 Worker의 결과 저장 차단은 기존 Worker 계약이 담당한다.

## URL 안전성

등록 값은 absolute `http`/`https` URL이며 host가 필요하고 userinfo와 fragment를 허용하지 않는다. Worker는 기본적으로 DNS 결과의 loopback, private/site-local, link-local, multicast와 IPv6 unique-local 주소를 차단한다. 내부망 Application을 연결해야 하는 환경은 `allowed-private-hostnames`에 승인된 hostname을 정확히 지정해야 한다. allowlist는 hostname에만 적용되고 private IP literal, wildcard, loopback, link-local, multicast와 AWS metadata endpoint는 계속 차단한다. `allow-private-addresses`는 로컬 통합 테스트와 같은 명시적 예외에만 사용하며 운영 성능 환경에서는 활성화하지 않는다. 이 설정은 인증이나 네트워크 ACL을 대체하지 않으며 배포 네트워크 egress 정책을 함께 적용해야 한다.

성능 환경 예시:

```yaml
guardbench:
  http-endpoint:
    allow-private-addresses: false
    allowed-private-hostnames:
      - internal-performance-api-123.ap-northeast-2.elb.amazonaws.com
```

입력·응답 본문, endpoint URL, 인증 정보와 provider 원문은 일반 로그나 오류 결과에 기록하지 않는다.

## 저장과 재현성

`model`은 Target reference와 함께 저장하며 Idempotency-Key fingerprint에도 포함한다. HTTP Target 신규 저장 계약에서 `model`은 `NOT NULL`이다. TestRun 조회 응답도 저장된 model을 항상 반환한다.
