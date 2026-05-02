package com.pulse.pulse.identity.api;

import com.pulse.pulse.identity.application.CurrentUserView;
import com.pulse.pulse.identity.application.TimeService;
import com.pulse.pulse.identity.application.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final UserService userService;
    private final TimeService timeService;

    @GetMapping("/timezones")
    public ResponseEntity<List<String>> getAvailableTimezones() {
        return ResponseEntity.ok(timeService.getCommonTimezones());
    }

    @GetMapping("/timezone")
    public ResponseEntity<Map<String, Object>> getTimezone(Authentication authentication) {
        CurrentUserView user = getUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        String timezone = user.timezone() != null ? user.timezone() : "UTCÂ±0";
        return ResponseEntity.ok(Map.of(
                "timezone", timezone,
                "timezoneOffset", timeService.getTimezoneOffset(timezone)));
    }

    @PutMapping("/timezone")
    public ResponseEntity<Map<String, Object>> updateTimezone(
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        String timezone = request.get("timezone");
        if (timezone == null || timezone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Timezone is required"));
        }

        if (!timeService.isValidTimezone(timezone)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid timezone: " + timezone));
        }

        CurrentUserView user = getUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        CurrentUserView updated = userService.updateTimezone(user.id(), timezone);

        log.info("Updated timezone for user {}: {}", updated.email(), timezone);
        return ResponseEntity.ok(Map.of(
                "timezone", timezone,
                "timezoneOffset", timeService.getTimezoneOffset(timezone)));
    }

    private CurrentUserView getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userService.getCurrentUser(authentication.getName());
    }
}
