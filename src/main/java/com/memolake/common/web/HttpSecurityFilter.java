package com.memolake.common.web;

import com.memolake.common.exception.ErrorCode;
import com.memolake.common.exception.ErrorResponse;
import com.memolake.config.MemoLakeProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal access control for an application without user accounts (applies to REST, MCP and web).
 *
 * <ol>
 *   <li>Host check: only host names in {@code memolake.security.allowed-hosts} are served
 *       (default: localhost only). Blocks DNS-rebinding attacks against a local server.</li>
 *   <li>Origin check: browser requests that change data (any non-GET request, and every MCP request)
 *       must come from the same origin. Blocks other web sites from writing memos via the user's browser.</li>
 *   <li>Optional shared access token ({@code memolake.security.access-token}): when set, every request needs
 *       {@code Authorization: Bearer <token>} (or HTTP Basic with the token as password, for browsers).</li>
 * </ol>
 * It also adds security headers (CSP, nosniff, ...) to every response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpSecurityFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    private final MemoLakeProperties.Security security;
    private final Set<String> allowedHosts;
    private final JsonMapper jsonMapper;

    public HttpSecurityFilter(MemoLakeProperties properties, JsonMapper jsonMapper) {
        this.security = properties.security();
        this.allowedHosts = security.allowedHosts().stream()
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);

        if (!security.anyHostAllowed() && !allowedHosts.contains(serverName(request))) {
            reject(response, ErrorCode.FORBIDDEN, "Host not allowed. Add it to memolake.security.allowed-hosts.");
            return;
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean needsSameOrigin = !SAFE_METHODS.contains(request.getMethod()) || isMcp(request);
        if (origin != null && needsSameOrigin && !isSameOrigin(origin, request)) {
            reject(response, ErrorCode.FORBIDDEN, "Cross-origin request rejected");
            return;
        }
        if (security.accessTokenEnabled() && !hasValidToken(request)) {
            response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"memolake\"");
            if (request.getRequestURI().startsWith("/web")) {
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"memolake\", charset=\"UTF-8\"");
            }
            reject(response, ErrorCode.UNAUTHORIZED, "Missing or invalid access token");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isMcp(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/mcp") || path.startsWith("/mcp/");
    }

    /** Host name of the request (from the Host header, or X-Forwarded-Host when forward headers are enabled). */
    private static String serverName(HttpServletRequest request) {
        String name = request.getServerName();
        if (name == null) {
            return "";
        }
        if (name.startsWith("[") && name.endsWith("]")) {
            name = name.substring(1, name.length() - 1);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean isSameOrigin(String origin, HttpServletRequest request) {
        try {
            URI uri = URI.create(origin);
            if (uri.getHost() == null) {
                return false; // e.g. "null" from sandboxed frames or file:// pages
            }
            String host = uri.getHost();
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
            }
            int port = uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            return host.equalsIgnoreCase(serverName(request)) && port == request.getServerPort();
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean hasValidToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            return false;
        }
        String presented = null;
        if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            presented = authorization.substring(7).strip();
        }
        else if (authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).strip()),
                        StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                presented = colon >= 0 ? decoded.substring(colon + 1) : null;
            }
            catch (IllegalArgumentException e) {
                return false;
            }
        }
        return presented != null && MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8),
                security.accessToken().getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message));
    }
}
