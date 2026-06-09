package service.registration.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import service.registration.domain.model.PaymentStatus;
import service.registration.domain.model.Registration;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    List<Registration> findByConferenceId(UUID conferenceId);

    List<Registration> findByConferenceIdAndPaymentStatus(UUID conferenceId, PaymentStatus paymentStatus);

    List<Registration> findByUserId(UUID userId);

    Optional<Registration> findByConferenceIdAndUserId(UUID conferenceId, UUID userId);

    List<Registration> findByPaymentStatusAndProofObjectKeyIsNotNull(PaymentStatus paymentStatus);

    Optional<Registration> findByProofObjectKey(String proofObjectKey);
}
