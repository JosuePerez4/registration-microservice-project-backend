package service.registration.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateRegistrationRequest(
        @NotNull(message = "conferenceId es obligatorio")
        UUID conferenceId
) {
}
