package service.registration.application.dto;

import java.util.UUID;

import service.registration.domain.model.PaymentStatus;

public record ConferencePaymentStatusResponse(
        boolean paid,
        UUID registrationId,
        PaymentStatus paymentStatus
) {
}
