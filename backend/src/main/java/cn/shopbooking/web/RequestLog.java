package cn.shopbooking.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestLog extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLog.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        long started = System.nanoTime();
        response.setHeader("X-Request-Id", id);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", id)) {
            try {
                chain.doFilter(request, response);
            } finally {
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                if (request.getRequestURI().startsWith("/api/")) {
                    log.info("{} {} {} {}ms", request.getMethod(), route == null ? "unmatched" : route,
                            response.getStatus(), (System.nanoTime() - started) / 1_000_000);
                }
            }
        }
    }
}
