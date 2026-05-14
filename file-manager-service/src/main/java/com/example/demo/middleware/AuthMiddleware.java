package com.example.demo.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Component
public class AuthMiddleware implements Filter {

    @Value("${soap.service.url:http://localhost:8080}")
    private String soapServiceUrl;

    // Set SOAP_AUTH_REQUIRED=false to skip SOAP validation when SOAP service is down
    @Value("${soap.auth.required:true}")
    private boolean soapAuthRequired;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req  = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // CORS headers
        res.setHeader("Access-Control-Allow-Origin",  "*");
        res.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type");

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            res.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // Public routes
        String path = req.getRequestURI();
        if (path.equals("/health") || path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        // If SOAP auth is disabled, skip token validation entirely
        if (!soapAuthRequired) {
            chain.doFilter(request, response);
            return;
        }

        String token = req.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Missing token. Please login first.\"}");
            return;
        }

        if (!validateToken(token)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Invalid or expired token.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean validateToken(String token) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> entity = new HttpEntity<>(token, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    soapServiceUrl + "/validate", entity, String.class);
            return "true".equals(resp.getBody());
        } catch (Exception e) {
            return false;
        }
    }
}
