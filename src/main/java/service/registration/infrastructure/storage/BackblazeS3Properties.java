package service.registration.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backblaze")
public record BackblazeS3Properties(
        String endpoint,
        String region,
        String bucketName,
        String accessKey,
        String secretKey
) {
}
