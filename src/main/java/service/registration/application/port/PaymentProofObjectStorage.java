package service.registration.application.port;

import java.time.Duration;

public interface PaymentProofObjectStorage {

    void putObject(String objectKey, byte[] body, String contentType);

    /**
     * Returns a temporary, signed URL that can be used by clients to download the object.
     */
    String getSignedObjectUrl(String objectKey, Duration expiresIn);
}
