package com.example.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.system.config.ApplicationShutdownService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminShutdownControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationShutdownService applicationShutdownService;

    @Test
    void postShutdown_withoutCsrf_returnsForbidden() throws Exception {
        mockMvc.perform(post("/admin/shutdown").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verify(applicationShutdownService, times(0)).shutdownGracefully();
    }

    @Test
    void postShutdown_withCsrf_returnsAckAndInvokesServiceOnce() throws Exception {
        mockMvc.perform(post("/admin/shutdown").with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Shutdown requested")));

        verify(applicationShutdownService, times(1)).shutdownGracefully();
    }

    /**
     * With {@code @MockitoBean}, each POST hits the mock; in production {@link ApplicationShutdownService}
     * ignores duplicate {@link ApplicationShutdownService#shutdownGracefully()} calls.
     */
    @Test
    void postShutdown_twice_controllerInvokesServiceEachTime() throws Exception {
        mockMvc.perform(post("/admin/shutdown").with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/shutdown").with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(applicationShutdownService, times(2)).shutdownGracefully();
    }

    /**
     * Opening the floor dashboard ({@code GET /}) invalidates the HTTP session; a stale admin tab
     * cannot complete a POST with the CSRF token that was tied to the invalidated session.
     */
    @Test
    void postShutdown_afterSessionInvalidated_usingStaleCsrfFromPage_returnsForbidden() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult page =
                mockMvc.perform(get("/admin/shutdown").session(session).with(user("admin").roles("ADMIN")))
                        .andExpect(status().isOk())
                        .andReturn();
        String html = page.getResponse().getContentAsString();
        Matcher matcher =
                Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "expected rendered CSRF hidden field name=_csrf");
        String staleToken = matcher.group(1);

        mockMvc.perform(get("/").session(session)).andExpect(status().isOk());

        mockMvc.perform(
                        post("/admin/shutdown")
                                .session(session)
                                .with(user("admin").roles("ADMIN"))
                                .param("_csrf", staleToken))
                .andExpect(status().isForbidden());

        verify(applicationShutdownService, times(0)).shutdownGracefully();
    }

    @Test
    void getShutdownPage_containsCsrfHiddenInputWhenTokenPresent() throws Exception {
        mockMvc.perform(get("/admin/shutdown").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    /** {@code GET /} serves the gate HTML; client-side sessionStorage may redirect to the matrix in the same tab. */
    @Test
    void getRoot_servesGatePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Production Calling System — Welcome")));
    }
}
