package com.sairo.sairo_backend.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 하나마다 추적 ID를 발급해 MDC와 응답 헤더에 넣는다.
 *
 * <p>오류 응답의 {@code traceId}와 서버 로그를 이어 붙이는 유일한 수단이므로
 * 로그 패턴에서도 {@code %X{traceId}}로 함께 출력한다.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 잘라 쓰지 않는다. 장기 로그에서 유일한 추적 키이므로 충돌 여지를 남기지 않는다.
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    /** 필터 밖(예: 예외 핸들러)에서 현재 요청의 추적 ID를 읽는다. */
    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID);
        return traceId != null ? traceId : "untraced";
    }
}
