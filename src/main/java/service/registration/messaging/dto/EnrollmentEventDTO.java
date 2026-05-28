package service.registration.messaging.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class EnrollmentEventDTO {
    private UUID conferenceId;
    private UUID userId;
    private String tipo;
}
