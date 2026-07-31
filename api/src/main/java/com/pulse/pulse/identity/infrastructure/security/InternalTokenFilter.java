package com.pulse.pulse.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates {@code /internal/**} against the shared secret in {@code pulse.internal.token}.
 * Fails closed: a blank expected token rejects every request, so a misconfigured deployment cannot
 * accidentally serve profile data. The token value is never logged.
 */
@Slf4j
public class InternalTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Token";

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"));

    private final byte[] expectedToken;

    public InternalTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken == null || expectedToken.isBlank()
                ? null
                : expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!matches(request.getHeader(HEADER))) {
            log.warn("Rejected {} {}: missing or invalid {}", request.getMethod(), request.getRequestURI(), HEADER);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Missing or invalid " + HEADER + "\"}");
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("internal-agent", null, AUTHORITIES));
        SecurityContextHolder.setContext(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean matches(String presented) {
        if (expectedToken == null || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedToken);
    }
}
