package com.guardbench.target.infrastructure.http;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/** HTTP Application Target URL의 구문과 worker egress 주소 정책을 검증한다. */
public final class HttpEndpointUrlValidator {

    private HttpEndpointUrlValidator() {
    }

    public static URI parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HTTP endpoint URL must not be blank");
        }
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("HTTP endpoint URL is invalid", exception);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getPort() > 65_535) {
            throw new IllegalArgumentException("HTTP endpoint URL must be an http(s) URL with a host");
        }
        return uri;
    }

    public static void validateResolvedAddress(URI uri, boolean allowPrivateAddresses)
            throws UnknownHostException {
        if (allowPrivateAddresses) {
            return;
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (isPrivateOrLocal(address)) {
                throw new IllegalArgumentException("HTTP endpoint resolves to a private or local address");
            }
        }
    }

    private static boolean isPrivateOrLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        boolean ipv6UniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || ipv6UniqueLocal;
    }
}
