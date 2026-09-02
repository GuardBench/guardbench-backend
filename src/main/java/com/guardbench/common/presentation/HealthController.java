package com.guardbench.common.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 애플리케이션 프로세스가 HTTP 요청을 받을 수 있는지 확인하는 운영용 endpoint를 제공한다.
 *
 * <p>이 endpoint는 외부 의존성의 상태가 아니라 HTTP 서버의 응답 가능 여부만 확인한다.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Void> health() {
        return ResponseEntity.ok().build();
    }
}
