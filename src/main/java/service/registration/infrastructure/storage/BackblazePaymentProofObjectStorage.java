package service.registration.infrastructure.storage;

import java.net.URI;
import java.util.Objects;
import java.time.Duration;

import org.springframework.stereotype.Component;

import service.registration.application.port.PaymentProofObjectStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.regions.Region;

@Component
public class BackblazePaymentProofObjectStorage implements PaymentProofObjectStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public BackblazePaymentProofObjectStorage(S3Client s3Client, BackblazeS3Properties props) {
        this.s3Client = s3Client;
        this.bucket = Objects.requireNonNull(props.bucketName(), "backblaze.bucket-name must be set").trim();

        String endpoint = Objects.requireNonNull(props.endpoint(), "backblaze.endpoint must be set").trim();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }

        this.s3Presigner = S3Presigner.builder()
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

    @Override
    public void putObject(String objectKey, byte[] body, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(body));
    }

    @Override
    public String getSignedObjectUrl(String objectKey, Duration expiresIn) {
        String safeKey = objectKey == null ? "" : objectKey.trim();
        if (safeKey.isBlank()) {
            throw new IllegalArgumentException("objectKey requerido");
        }
        if (expiresIn == null || expiresIn.isZero() || expiresIn.isNegative()) {
            throw new IllegalArgumentException("expiresIn invalido");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(safeKey)
                .build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .getObjectRequest(getObjectRequest)
                .build();

        var presigned = s3Presigner.presignGetObject(getObjectPresignRequest);
        return presigned.url().toString();
    }
}
