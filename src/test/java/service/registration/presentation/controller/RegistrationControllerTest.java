package service.registration.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import service.registration.application.dto.RegistrationResponse;
import service.registration.application.service.RegistrationService;
import service.registration.domain.model.PaymentStatus;

@ExtendWith(MockitoExtension.class)
class RegistrationControllerTest {

    @Mock
    private RegistrationService registrationService;

    @InjectMocks
    private RegistrationController registrationController;

    @Test
    void testGetByConferenceWithStatus() {
        UUID conferenceId = UUID.randomUUID();
        PaymentStatus status = PaymentStatus.PENDING;

        RegistrationResponse res = new RegistrationResponse(
                UUID.randomUUID(),
                conferenceId,
                UUID.randomUUID(),
                false,
                status,
                "proof-key",
                OffsetDateTime.now()
        );

        when(registrationService.getByConferenceIdAndStatus(conferenceId, status))
                .thenReturn(List.of(res));

        List<RegistrationResponse> responses = registrationController.getByConference(conferenceId, status);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(status, responses.get(0).paymentStatus());
        verify(registrationService).getByConferenceIdAndStatus(conferenceId, status);
    }

    @Test
    void testGetByConferenceWithoutStatus() {
        UUID conferenceId = UUID.randomUUID();

        RegistrationResponse res = new RegistrationResponse(
                UUID.randomUUID(),
                conferenceId,
                UUID.randomUUID(),
                false,
                PaymentStatus.PENDING,
                "proof-key",
                OffsetDateTime.now()
        );

        when(registrationService.getByConferenceId(conferenceId))
                .thenReturn(List.of(res));

        List<RegistrationResponse> responses = registrationController.getByConference(conferenceId, null);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        verify(registrationService).getByConferenceId(conferenceId);
    }
}
