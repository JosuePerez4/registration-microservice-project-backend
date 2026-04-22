package service.registration.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RegistrationResponse(
        UUID id,
        UUID conferenceId,
        UUID userId,
        boolean active,
        OffsetDateTime createdAt
) {
}
