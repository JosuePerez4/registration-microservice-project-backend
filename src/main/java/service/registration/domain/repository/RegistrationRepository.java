package service.registration.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import service.registration.domain.model.Registration;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    List<Registration> findByConferenceId(UUID conferenceId);
}
