package service.registration.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Entity
@Data
@Table(
        name = "registrations",
        uniqueConstraints = @UniqueConstraint(name = "uk_registration_conference_user", columnNames = {"conferenceId", "userId"})
)
public class Registration {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private UUID conferenceId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @ColumnDefault("'PENDING'")
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(length = 1024)
    private String proofObjectKey;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
