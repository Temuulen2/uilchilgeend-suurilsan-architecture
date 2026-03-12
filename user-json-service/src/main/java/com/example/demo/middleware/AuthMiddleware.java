package com.example.demo.middleware;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

public class AuthMiddleware implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;

        String token = req.getHeader("Authorization");

        if(token == null){
            throw new ServletException("Unauthorized");
        }

        chain.doFilter(request, response);
    }
}