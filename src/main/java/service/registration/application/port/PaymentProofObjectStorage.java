package service.registration.application.port;

public interface PaymentProofObjectStorage {

    void putObject(String objectKey, byte[] body, String contentType);
}
