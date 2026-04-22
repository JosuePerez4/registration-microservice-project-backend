package service.registration.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthMeResponse(
        UUID id,
        JsonNode role
) {
}
