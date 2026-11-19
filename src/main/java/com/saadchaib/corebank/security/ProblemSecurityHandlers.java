package com.saadchaib.corebank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saadchaib.corebank.common.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Errors raised by the security filters happen before Spring MVC, so the
 * RestControllerAdvice never sees them. These write the same problem+json body.
 */
@Component
public class ProblemSecurityHandlers {

    private final ObjectMapper objectMapper;

    public ProblemSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            write(response, GlobalExceptionHandler.problem(
                    HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized",
                    "A valid bearer token is required."));
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, GlobalExceptionHandler.problem(
                HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "You are not allowed to do this."));
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
