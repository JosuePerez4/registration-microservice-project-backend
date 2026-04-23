package service.registration.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import service.registration.application.dto.CreateRegistrationRequest;
import service.registration.application.dto.RegistrationResponse;
import service.registration.domain.model.Registration;
import service.registration.domain.repository.RegistrationRepository;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;

    public RegistrationService(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    public RegistrationResponse create(CreateRegistrationRequest request, UUID userId) {
        Registration registration = new Registration();
        registration.setConferenceId(request.conferenceId());
        registration.setUserId(userId);
        registration.setActive(false);
        registration.setCreatedAt(OffsetDateTime.now());

        try {
            return toResponse(registrationRepository.save(registration));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El usuario ya tiene un registro para esta conferencia",
                    ex
            );
        }
    }

    public RegistrationResponse getById(UUID id) {
        Registration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado"));
        return toResponse(registration);
    }

    public List<RegistrationResponse> getByConferenceId(UUID conferenceId) {
        return registrationRepository.findByConferenceId(conferenceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<RegistrationResponse> getByUserId(UUID userId) {
        return registrationRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private RegistrationResponse toResponse(Registration registration) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getConferenceId(),
                registration.getUserId(),
                registration.isActive(),
                registration.getCreatedAt()
        );
    }
}
