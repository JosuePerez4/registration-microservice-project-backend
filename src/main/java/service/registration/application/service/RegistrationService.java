package service.registration.application.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import service.registration.application.dto.ConferencePaymentStatusResponse;
import service.registration.application.dto.CreateRegistrationRequest;
import service.registration.application.dto.RegistrationResponse;
import service.registration.application.port.PaymentProofObjectStorage;
import service.registration.domain.model.PaymentStatus;
import service.registration.domain.model.Registration;
import service.registration.domain.repository.RegistrationRepository;
import service.registration.messaging.EnrollmentEventPublisher;
import software.amazon.awssdk.core.exception.SdkException;

@Service
public class RegistrationService {

    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final String INSCRIPTION_TYPE_ASISTENTE = "ASISTENTE";

    private final RegistrationRepository registrationRepository;
    private final PaymentProofObjectStorage paymentProofObjectStorage;
    private final EnrollmentEventPublisher enrollmentEventPublisher;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            PaymentProofObjectStorage paymentProofObjectStorage,
            EnrollmentEventPublisher enrollmentEventPublisher
    ) {
        this.registrationRepository = registrationRepository;
        this.paymentProofObjectStorage = paymentProofObjectStorage;
        this.enrollmentEventPublisher = enrollmentEventPublisher;
    }

    public RegistrationResponse create(CreateRegistrationRequest request, UUID userId) {
        try {
            return toResponse(newPendingRegistration(request.conferenceId(), userId));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El usuario ya tiene un registro para esta conferencia",
                    ex
            );
        }
    }

    @Transactional
    public RegistrationResponse submitSimulatedPayment(UUID conferenceId, UUID userId, MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo", ex);
        }

        ImageProofFormat format = validatePaymentProofImage(file, bytes);

        Registration registration = registrationRepository
                .findByConferenceIdAndUserId(conferenceId, userId)
                .orElse(null);

        boolean alreadyApproved = registration != null
                && registration.getPaymentStatus() == PaymentStatus.APPROVED
                && registration.isActive();
        if (alreadyApproved) {
            return toResponse(registration);
        }

        boolean isNew = registration == null;

        String objectKey = "registration-payment-proofs/%s/%s/%s.%s".formatted(
                conferenceId,
                userId,
                UUID.randomUUID(),
                format.extension()
        );

        try {
            paymentProofObjectStorage.putObject(objectKey, bytes, format.mediaType());
        } catch (SdkException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo subir el comprobante al almacenamiento",
                    ex
            );
        }

        if (isNew) {
            registration = new Registration();
            registration.setConferenceId(conferenceId);
            registration.setUserId(userId);
            registration.setCreatedAt(OffsetDateTime.now());
        }

        registration.setActive(true);
        registration.setPaymentStatus(PaymentStatus.APPROVED);
        registration.setProofObjectKey(objectKey);

        try {
            RegistrationResponse response = toResponse(registrationRepository.save(registration));
            publishEnrollmentCreated(conferenceId, userId);
            return response;
        } catch (DataIntegrityViolationException ex) {
            if (!isNew) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El usuario ya tiene un registro para esta conferencia",
                        ex
                );
            }
            Registration existing = registrationRepository.findByConferenceIdAndUserId(conferenceId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "El usuario ya tiene un registro para esta conferencia",
                            ex
                    ));
            if (existing.getPaymentStatus() == PaymentStatus.APPROVED && existing.isActive()) {
                return toResponse(existing);
            }
            String retryKey = "registration-payment-proofs/%s/%s/%s.%s".formatted(
                    conferenceId,
                    userId,
                    UUID.randomUUID(),
                    format.extension()
            );
            try {
                paymentProofObjectStorage.putObject(retryKey, bytes, format.mediaType());
            } catch (SdkException sdkEx) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "No se pudo subir el comprobante al almacenamiento",
                        sdkEx
                );
            }
            existing.setActive(true);
            existing.setPaymentStatus(PaymentStatus.APPROVED);
            existing.setProofObjectKey(retryKey);
            RegistrationResponse response = toResponse(registrationRepository.save(existing));
            publishEnrollmentCreated(conferenceId, userId);
            return response;
        }
    }

    private void publishEnrollmentCreated(UUID conferenceId, UUID userId) {
        enrollmentEventPublisher.publishCreated(conferenceId, userId, INSCRIPTION_TYPE_ASISTENTE);
    }

    private Registration newPendingRegistration(UUID conferenceId, UUID userId) {
        Registration registration = new Registration();
        registration.setConferenceId(conferenceId);
        registration.setUserId(userId);
        registration.setActive(false);
        registration.setPaymentStatus(PaymentStatus.PENDING);
        registration.setCreatedAt(OffsetDateTime.now());
        return registrationRepository.save(registration);
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

    public ConferencePaymentStatusResponse getPaymentStatusForConference(UUID conferenceId, UUID userId) {
        return registrationRepository.findByConferenceIdAndUserId(conferenceId, userId)
                .map(reg -> new ConferencePaymentStatusResponse(
                        reg.getPaymentStatus() == PaymentStatus.APPROVED && reg.isActive(),
                        reg.getId(),
                        reg.getPaymentStatus()
                ))
                .orElse(new ConferencePaymentStatusResponse(false, null, null));
    }

    private ImageProofFormat validatePaymentProofImage(MultipartFile file, byte[] bytes) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo es obligatorio");
        }
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
        }

        String declared = file.getContentType();
        String contentType = declared == null ? "" : declared.trim().toLowerCase(Locale.ROOT);
        String extFromName = extensionFromFilename(file.getOriginalFilename());

        if (startsWith(bytes, PNG_MAGIC)) {
            assertImageConstraints(contentType, extFromName, Set.of("image/png"), Set.of("png"));
            return new ImageProofFormat("image/png", "png");
        }
        if (isJpegStart(bytes)) {
            assertImageConstraints(contentType, extFromName, Set.of("image/jpeg", "image/jpg"), Set.of("jpg", "jpeg"));
            return new ImageProofFormat("image/jpeg", "jpg");
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten imágenes JPEG o PNG");
    }

    private static void assertImageConstraints(
            String contentType,
            String extFromName,
            Set<String> allowedMime,
            Set<String> allowedExt
    ) {
        if (!contentType.isEmpty() && allowedMime.stream().noneMatch(contentType::equals)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content-Type de imagen no permitido");
        }
        if (extFromName != null && !extFromName.isEmpty() && allowedExt.stream().noneMatch(extFromName::equals)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Extensión de archivo no permitida");
        }
    }

    private static String extensionFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isJpegStart(byte[] b) {
        return b.length >= 3
                && (b[0] & 0xFF) == 0xFF
                && (b[1] & 0xFF) == 0xD8
                && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private RegistrationResponse toResponse(Registration registration) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getConferenceId(),
                registration.getUserId(),
                registration.isActive(),
                registration.getPaymentStatus(),
                registration.getProofObjectKey(),
                registration.getCreatedAt()
        );
    }

    private record ImageProofFormat(String mediaType, String extension) {
    }
}
