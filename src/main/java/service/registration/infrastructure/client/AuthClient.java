package service.registration.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import service.registration.infrastructure.client.dto.AuthMeResponse;

@Component
public class AuthClient {

    private final RestClient restClient;

    public AuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${auth.service.base-url}") String authBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(authBaseUrl).build();
    }

    public AuthMeResponse getAuthenticatedUser(String authorizationHeader) {
        return restClient.get()
                .uri("/api/v1/auth/me")
                .header("Authorization", authorizationHeader)
                .retrieve()
                .body(AuthMeResponse.class);
    }
}
