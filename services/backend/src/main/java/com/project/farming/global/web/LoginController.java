package com.project.farming.global.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String showLoginPage() {
        return "auth/login";
    }

    @GetMapping("/denied")
    public String showAccessDeniedPage() {
        return "auth/denied";
    }

    @GetMapping("/expired")
    public String showSessionExpiredPage() {
        return "auth/expired";
    }
}
