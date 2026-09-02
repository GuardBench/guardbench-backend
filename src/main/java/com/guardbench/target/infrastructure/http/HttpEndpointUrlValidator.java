package com.guardbench.target.infrastructure.http;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;

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
        validateResolvedAddress(uri, allowPrivateAddresses, java.util.List.of());
    }

    public static void validateResolvedAddress(
            URI uri,
            boolean allowPrivateAddresses,
            Collection<String> allowedPrivateHostnames
    ) throws UnknownHostException {
        validateResolvedAddresses(
                uri,
                allowPrivateAddresses,
                allowedPrivateHostnames,
                InetAddress.getAllByName(uri.getHost()));
    }

    static void validateResolvedAddresses(
            URI uri,
            boolean allowPrivateAddresses,
            Collection<String> allowedPrivateHostnames,
            InetAddress[] addresses
    ) {
        boolean allowedHostname = isAllowedPrivateHostname(uri.getHost(), allowedPrivateHostnames);
        for (InetAddress address : addresses) {
            if (isAlwaysBlocked(address)) {
                if (!allowPrivateAddresses) {
                    throw new IllegalArgumentException("HTTP endpoint resolves to a private or local address");
                }
                continue;
            }
            if (isPrivateAddress(address) && !allowPrivateAddresses && !allowedHostname) {
                throw new IllegalArgumentException("HTTP endpoint resolves to a private or local address");
            }
        }
    }

    private static boolean isAllowedPrivateHostname(String host, Collection<String> allowedPrivateHostnames) {
        if (host == null || isIpLiteral(host) || allowedPrivateHostnames == null) {
            return false;
        }
        String normalizedHost = withoutTrailingDot(host).toLowerCase(Locale.ROOT);
        return allowedPrivateHostnames.stream()
                .filter(value -> value != null)
                .map(String::trim)
                .map(value -> withoutTrailingDot(value).toLowerCase(Locale.ROOT))
                .anyMatch(normalizedHost::equals);
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}");
    }

    private static String withoutTrailingDot(String host) {
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static boolean isAlwaysBlocked(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress();
    }

    private static boolean isPrivateAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        boolean ipv6UniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        return address.isSiteLocalAddress() || ipv6UniqueLocal;
    }
}
