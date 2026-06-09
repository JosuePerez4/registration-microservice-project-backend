package service.registration.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import service.registration.application.port.PaymentProofObjectStorage;
import service.registration.domain.model.PaymentStatus;
import service.registration.domain.model.Registration;
import service.registration.domain.repository.RegistrationRepository;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private PaymentProofObjectStorage paymentProofObjectStorage;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    void testGetByConferenceIdAndStatus() {
        UUID conferenceId = UUID.randomUUID();
        PaymentStatus status = PaymentStatus.APPROVED;

        Registration reg = new Registration();
        reg.setId(UUID.randomUUID());
        reg.setConferenceId(conferenceId);
        reg.setUserId(UUID.randomUUID());
        reg.setActive(true);
        reg.setPaymentStatus(status);
        reg.setProofObjectKey("some-key");
        reg.setCreatedAt(OffsetDateTime.now());

        when(registrationRepository.findByConferenceIdAndPaymentStatus(conferenceId, status))
                .thenReturn(List.of(reg));

        List<RegistrationResponse> responses = registrationService.getByConferenceIdAndStatus(conferenceId, status);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(conferenceId, responses.get(0).conferenceId());
        assertEquals(status, responses.get(0).paymentStatus());
    }
}
