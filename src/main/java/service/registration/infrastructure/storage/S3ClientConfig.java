package service.registration.infrastructure.storage;

import java.net.URI;
import java.util.Objects;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(BackblazeS3Properties.class)
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(BackblazeS3Properties props) {
        String endpoint = Objects.requireNonNull(props.endpoint(), "backblaze.endpoint must be set").trim();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(Objects.requireNonNull(props.region(), "backblaze.region must be set").trim()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                Objects.requireNonNull(props.accessKey(), "backblaze.access-key must be set").trim(),
                                Objects.requireNonNull(props.secretKey(), "backblaze.secret-key must be set").trim())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
