package service.registration.application.dto;

import java.util.UUID;

import service.registration.domain.model.PaymentStatus;

public record PendingPaymentResponse(
    UUID registrationId,
    UUID conferenceId,
    UUID userId,
    boolean active,
    PaymentStatus paymentStatus,
    String proofObjectKey
) {
}
