package ru.arzer0.issueisekai.panel.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaFallbackController {
    // ponytail: exact legacy routes win until the frontend build supplies index.html.
    @GetMapping({
        "/",
        "/login",
        "/board",
        "/timeline",
        "/{route:reports|users|servers}",
        "/reports/{*path}"
    })
    public String index() {
        return "forward:/index.html";
    }
}
