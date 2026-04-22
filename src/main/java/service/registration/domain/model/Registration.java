package service.registration.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
