package com.guardbench.target.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpEndpointUrlValidatorTest {

    @Test
    @DisplayName("public HTTPS endpoint는 기존처럼 허용한다")
    void allowsPublicHttpsEndpoint() throws Exception {
        assertDoesNotThrow(() -> validate(
                "https://api.example.com/v1/chat/completions",
                "93.184.216.34"));
    }

    @Test
    @DisplayName("승인된 internal ALB hostname은 private 주소로 해석되어도 허용한다")
    void allowsApprovedPrivateHostname() throws Exception {
        assertDoesNotThrow(() -> validate(
                "https://internal-performance-api.example.com/v1/chat/completions",
                "10.20.30.40",
                List.of("internal-performance-api.example.com")));
    }

    @Test
    @DisplayName("allowlist에 없는 RFC1918 private 주소는 거부한다")
    void rejectsUnapprovedPrivateAddress() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> validate(
                "https://unapproved.example.com/v1/chat/completions",
                "10.20.30.40"));
    }

    @Test
    @DisplayName("loopback 주소는 private hostname allowlist가 있어도 거부한다")
    void rejectsLoopbackAddress() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> validate(
                "http://internal-performance-api.example.com/v1/chat/completions",
                "127.0.0.1",
                List.of("internal-performance-api.example.com")));
    }

    @Test
    @DisplayName("link-local과 AWS metadata 주소는 private hostname allowlist가 있어도 거부한다")
    void rejectsLinkLocalAndMetadataAddresses() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> validate(
                "http://internal-performance-api.example.com/v1/chat/completions",
                "169.254.169.254",
                List.of("internal-performance-api.example.com")));
    }

    @Test
    @DisplayName("private IP literal은 hostname allowlist에 넣어도 거부한다")
    void rejectsPrivateIpLiteralAllowlistEntry() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> validate(
                "https://10.20.30.40/v1/chat/completions",
                "10.20.30.40",
                List.of("10.20.30.40")));
    }

    private static void validate(String url, String address) throws Exception {
        validate(url, address, List.of());
    }

    private static void validate(String url, String address, List<String> allowedHostnames) throws Exception {
        URI uri = HttpEndpointUrlValidator.parse(url);
        HttpEndpointUrlValidator.validateResolvedAddresses(
                uri,
                false,
                allowedHostnames,
                new InetAddress[]{InetAddress.getByName(address)});
    }
}
