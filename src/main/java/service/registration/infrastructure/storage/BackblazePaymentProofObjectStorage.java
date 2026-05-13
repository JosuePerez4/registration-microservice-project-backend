package service.registration.infrastructure.storage;

import java.util.Objects;

import org.springframework.stereotype.Component;

import service.registration.application.port.PaymentProofObjectStorage;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class BackblazePaymentProofObjectStorage implements PaymentProofObjectStorage {

    private final S3Client s3Client;
    private final String bucket;

    public BackblazePaymentProofObjectStorage(S3Client s3Client, BackblazeS3Properties props) {
        this.s3Client = s3Client;
        this.bucket = Objects.requireNonNull(props.bucketName(), "backblaze.bucket-name must be set").trim();
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
}
