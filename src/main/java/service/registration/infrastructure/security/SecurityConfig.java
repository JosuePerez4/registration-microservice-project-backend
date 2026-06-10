package service.registration.infrastructure.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/registrations/register").hasAnyRole("AUTHOR", "ASISTANT")
                        .requestMatchers(HttpMethod.POST, "/registrations/pay").hasRole("ASISTANT")
                        .requestMatchers(HttpMethod.POST, "/registrations/approve-payment").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/registrations/reject-payment").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/registrations/pending-payments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/registrations/payment-proof").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/registrations/register/**", "/registrations/register-list")
                        .hasAnyRole("ADMIN", "CHAIR", "AUTHOR", "ASISTANT")
                        .requestMatchers("/registrations/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.public.key}") String publicKeyPem) {
        try {
            String normalizedKey = publicKeyPem
                    .replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(normalizedKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) publicKey).build();
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException ex) {
            throw new IllegalStateException("No se pudo cargar la llave publica JWT", ex);
        }
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object roleClaim = jwt.getClaims().get("role");
        if (roleClaim == null) {
            return Collections.emptyList();
        }

        String role = extractRole(roleClaim);
        if (role == null || role.isBlank()) {
            return Collections.emptyList();
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + normalized));
    }

    private String extractRole(Object roleClaim) {
        if (roleClaim instanceof String role) {
            return role;
        }
        if (roleClaim instanceof Map<?, ?> roleMap) {
            Object roleName = roleMap.get("name");
            return roleName == null ? null : String.valueOf(roleName);
        }
        if (roleClaim instanceof Collection<?> roles && !roles.isEmpty()) {
            Object firstRole = roles.iterator().next();
            return firstRole == null ? null : String.valueOf(firstRole);
        }
        return String.valueOf(roleClaim);
    }
}
