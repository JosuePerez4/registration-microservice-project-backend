package service.registration.presentation.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import service.registration.application.dto.ConferencePaymentStatusResponse;
import service.registration.application.dto.CreateRegistrationRequest;
import service.registration.application.dto.PendingPaymentResponse;
import service.registration.application.dto.PaymentProofUrlResponse;
import service.registration.application.dto.RegistrationResponse;
import service.registration.application.service.RegistrationService;
import service.registration.domain.model.PaymentStatus;

@RestController
@RequestMapping("/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse create(
            @Valid @RequestBody CreateRegistrationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return registrationService.create(request, resolveUserId(jwt));
    }

    @PostMapping(value = "/pay", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RegistrationResponse submitSimulatedPayment(
            @RequestParam UUID conferenceId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return registrationService.submitSimulatedPayment(conferenceId, resolveUserId(jwt), file);
    }

    @GetMapping("/pending-payments")
    public List<PendingPaymentResponse> getPendingPayments() {
        return registrationService.getPendingPayments();
    }

    @GetMapping("/payment-proof")
    public PaymentProofUrlResponse getPaymentProofUrl(@RequestParam String proofObjectKey) {
        return registrationService.getPaymentProofUrl(proofObjectKey);
    }

    @PostMapping("/approve-payment")
    public RegistrationResponse approvePayment(@RequestParam UUID registrationId) {
        return registrationService.approvePendingPayment(registrationId);
    }

    @PostMapping("/reject-payment")
    public RegistrationResponse rejectPayment(@RequestParam UUID registrationId) {
        return registrationService.rejectPendingPayment(registrationId);
    }


    @GetMapping("/payment-status")
    public ConferencePaymentStatusResponse getPaymentStatus(
            @RequestParam UUID conferenceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return registrationService.getPaymentStatusForConference(conferenceId, resolveUserId(jwt));
    }

    @GetMapping("/register/{id}")
    public RegistrationResponse getById(@PathVariable UUID id) {
        return registrationService.getById(id);
    }

    @GetMapping("/register-list")
    public List<RegistrationResponse> getByConference(
            @RequestParam UUID conferenceId,
            @RequestParam(required = false) PaymentStatus status
    ) {
        if (status != null) {
            return registrationService.getByConferenceIdAndStatus(conferenceId, status);
        }
        return registrationService.getByConferenceId(conferenceId);
    }

    @GetMapping("/my")
    public List<RegistrationResponse> getMyRegistrations(@AuthenticationPrincipal Jwt jwt) {
        return registrationService.getByUserId(resolveUserId(jwt));
    }

    private static UUID resolveUserId(Jwt jwt) {
        String userIdClaim = jwt.getClaimAsString("userId");
        if (userIdClaim == null || userIdClaim.isBlank()) {
            userIdClaim = jwt.getSubject();
        }
        return UUID.fromString(userIdClaim);
    }
}
