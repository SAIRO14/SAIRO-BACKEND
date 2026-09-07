package com.sairo.sairo_backend.common;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

@Component
@Order(1)
public class AdminAuthFilter implements Filter {

    private final String adminToken;

    public AdminAuthFilter(@Value("${app.admin-token:}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (!request.getRequestURI().startsWith("/admin")) {
            chain.doFilter(req, res);
            return;
        }

        // ADMIN_TOKEN 미설정 시 비활성화
        if (adminToken.isBlank()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Admin token not configured");
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Basic ")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(auth.substring(6)));
                if (decoded.equals("admin:" + adminToken)) {
                    chain.doFilter(req, res);
                    return;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        response.setHeader("WWW-Authenticate", "Basic realm=\"Sairo Admin\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
