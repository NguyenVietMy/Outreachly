package com.pulse.pulse.personal.api;

import com.pulse.pulse.personal.application.InternalContextService;
import com.pulse.pulse.personal.application.InternalContextService.GithubContext;
import com.pulse.pulse.personal.application.InternalContextService.ItemsContext;
import com.pulse.pulse.personal.application.InternalContextService.ProfileContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service context API for the Python agent. Authenticated by the shared
 * {@code X-Internal-Token} secret only — see InternalApiSecurityConfig. Not reachable with a
 * session cookie, and not exposed through the public ALB listener.
 */
@RestController
@RequestMapping("/internal/context")
@RequiredArgsConstructor
public class InternalContextController {

    private final InternalContextService internalContextService;

    @GetMapping("/profile")
    public ProfileContext profile(@RequestParam Long userId) {
        return internalContextService.getProfile(userId);
    }

    @GetMapping("/items")
    public ItemsContext items(@RequestParam Long userId) {
        return internalContextService.getItems(userId);
    }

    @GetMapping("/github")
    public GithubContext github(@RequestParam Long userId) {
        return internalContextService.getGithub(userId);
    }
}
