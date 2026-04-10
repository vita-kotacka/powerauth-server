/*
 * PowerAuth Server and related software components
 * Copyright (C) 2025 Wultra s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wultra.security.powerauth.app.server.interceptor;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that propagates the X-Correlation-ID request header into OTel Baggage,
 * making it available as a span attribute across the entire trace.
 * This allows searching traces by correlation ID directly in Tempo.
 *
 * @author Copilot
 */
@Component
@ConditionalOnProperty(value = "powerauth.service.correlation-header.enabled", havingValue = "true")
@Slf4j
public class BaggagePropagationFilter extends OncePerRequestFilter {

    private static final String BAGGAGE_KEY = "correlation.id";

    @Value("${powerauth.service.correlation-header.name:X-Correlation-ID}")
    private String correlationHeaderName;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String correlationId = request.getHeader(correlationHeaderName);
        if (correlationId == null || correlationId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        final Baggage baggage = Baggage.current().toBuilder()
                .put(BAGGAGE_KEY, correlationId)
                .build();

        try (Scope ignored = baggage.storeInContext(Context.current()).makeCurrent()) {
            filterChain.doFilter(request, response);
        }
    }
}
