package com.example.smartjobsearch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /**
     * React SPA entrypoint.
     *
     * For any non-API, non-static route, forward to the SPA so client-side routing works.
     */
    @GetMapping({
            "/",
            "/login",
            "/post-job",
            "/profile",
            "/applied-jobs",
            "/dashboard"
    })
    public String home() {
        return "forward:/index.html";
    }

    // Fallback: forward any other GET route to the SPA.
    //
    // Notes:
    // - We deliberately do NOT try to use regex-style path patterns here because
    //   Spring's default path matcher (since Spring Boot 3) rejects invalid
    //   capture patterns.
    // - /api/** and /assets/** are handled by Spring before reaching this handler
    //   due to controller/asset precedence.
    @GetMapping("/**")
    public String spaForward(jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();

        if (uri.startsWith("/api/") || uri.equals("/api") || uri.startsWith("/assets/") || uri.startsWith("/static/")) {
            return "404";
        }
        return "forward:/index.html";
    }

}

