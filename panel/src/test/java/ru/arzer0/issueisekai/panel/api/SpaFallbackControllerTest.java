package ru.arzer0.issueisekai.panel.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpaFallbackControllerTest {
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new SpaFallbackController()).build();

    @Test
    void forwardsOnlyKnownClientRoutes() throws Exception {
        for (String path : new String[] {
            "/",
            "/login",
            "/board",
            "/timeline",
            "/reports",
            "/reports/123",
            "/users",
            "/servers"
        }) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }

        mvc.perform(get("/api/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/assets/unknown.js")).andExpect(status().isNotFound());
        mvc.perform(get("/unknown.js")).andExpect(status().isNotFound());
    }
}
