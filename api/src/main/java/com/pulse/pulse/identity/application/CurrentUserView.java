package com.pulse.pulse.identity.application;

import java.time.LocalDateTime;
import java.util.UUID;

public record CurrentUserView(
        Long id,
        String email,
        String firstName,
        String lastName,
        String profilePictureUrl,
        String role,
        UUID orgId,
        String timezone,
        LocalDateTime createdAt) {
}
