package service.registration.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import service.registration.domain.model.PaymentStatus;

public record RegistrationResponse(
        UUID id,
        UUID conferenceId,
        UUID userId,
        boolean active,
        PaymentStatus paymentStatus,
        String proofObjectKey,
        OffsetDateTime createdAt
) {
}
