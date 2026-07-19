package io.github.connellite.proxy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Host pages for Spring Security form login and the GWT admin SPA.
 */
@Controller
public class AdminController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping({"/", "/admin", "/admin/"})
    public String adminApp() {
        return "forward:/admin.html";
    }
}
