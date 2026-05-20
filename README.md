# Registration Microservice

Spring Boot service for creating conference registrations, approving simulated
payments, and querying registration state.

The service exposes a REST API under `/registrations`, persists registrations
in PostgreSQL, stores payment proof images in Backblaze B2 through the
S3-compatible API, and protects registration workflows with JWT authentication
and role checks.

## Stack

- Java 21
- Spring Boot 4.0.5
- Spring Web MVC, Spring Data JPA, Bean Validation, Spring Security
- OAuth2 Resource Server with RSA JWT validation
- PostgreSQL
- AWS SDK S3 client for Backblaze B2 object storage
- Springdoc OpenAPI UI

## Local setup

### Prerequisites

- JDK 21
- A reachable PostgreSQL database
- A public RSA key that matches the JWTs issued by the authentication service
- A Backblaze B2 bucket and S3-compatible application key for payment proofs

### Configuration

`src/main/resources/application.properties` imports `.env` from the project root
if present. Create one locally with the required values:

```properties
SERVER_PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/registration
auth.service.base-url=http://localhost:8081
JWT_PUBLIC_KEY=MIIBIjANBgkqh...
BACKBLAZE_ENDPOINT=https://s3.us-west-004.backblazeb2.com
BACKBLAZE_REGION=us-west-004
BACKBLAZE_BUCKET_NAME=registration-payment-proofs-dev
BACKBLAZE_ACCESS_KEY=<application-key-id>
BACKBLAZE_SECRET_KEY=<application-key>
```

| Property or variable | Required | Used by | Notes |
| --- | --- | --- | --- |
| `SERVER_PORT` | Yes | `server.port` | HTTP port for the service. |
| `DB_URL` | Yes | `spring.datasource.url` | PostgreSQL JDBC URL. Credentials can be included in the URL or supplied by the environment if configured externally. |
| `JWT_PUBLIC_KEY` | Yes | `jwt.public.key` / `SecurityConfig` | Base64 body of the RSA public key in SPKI format. `application.properties` wraps it with PEM headers before startup. |
| `auth.service.base-url` | Yes | `AuthClient` | Base URL for the auth service. The current client calls `/api/v1/auth/me`; no controller currently invokes it, but the bean is created at startup. |
| `BACKBLAZE_ENDPOINT` | Yes | `S3ClientConfig` | Backblaze S3-compatible endpoint. `https://` is added automatically when the value has no scheme. |
| `BACKBLAZE_REGION` | Yes | `S3ClientConfig` | Region passed to the AWS SDK S3 client. |
| `BACKBLAZE_BUCKET_NAME` | Yes | `BackblazePaymentProofObjectStorage` | Bucket where payment proof images are uploaded. |
| `BACKBLAZE_ACCESS_KEY` | Yes | `S3ClientConfig` | Backblaze application key id. |
| `BACKBLAZE_SECRET_KEY` | Yes | `S3ClientConfig` | Backblaze application key secret. |

JPA currently runs with `spring.jpa.hibernate.ddl-auto=update` and SQL logging
enabled. Treat those defaults as development-oriented unless profiles override
them in the deployment environment.

Multipart uploads are limited to a 5 MB file and a 6 MB total request by
`spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size`.

### Run

```bash
./mvnw spring-boot:run
```

### Docker image

The Dockerfile builds the Maven project in a builder image and runs the packaged
JAR on Eclipse Temurin 21. `EXPOSE 8084` is image metadata only; the application
still listens on `SERVER_PORT`.

```bash
docker build -t registration-service .
docker run --env-file .env -p 8084:8084 registration-service
```

OpenAPI is available when the service is running:

- Swagger UI: `http://localhost:${SERVER_PORT}/swagger-ui.html`
- OpenAPI JSON: `http://localhost:${SERVER_PORT}/v3/api-docs`

## API

All `/registrations/**` endpoints require a bearer JWT. Creating records,
fetching a registration by id, and listing by conference also check roles;
payment-status and "my registrations" only require an authenticated JWT.

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| `POST` | `/registrations/register` | `AUTHOR`, `ASISTANT` | Creates a pending registration for the authenticated user. |
| `POST` | `/registrations/pay` | `ASISTANT` | Uploads a JPEG/PNG payment proof and approves the authenticated user's registration for a conference. |
| `GET` | `/registrations/payment-status?conferenceId={uuid}` | Authenticated JWT | Returns whether the authenticated user has an approved active registration for one conference. |
| `GET` | `/registrations/register/{id}` | `ADMIN`, `CHAIR`, `AUTHOR`, `ASISTANT` | Fetches one registration by id. |
| `GET` | `/registrations/register-list?conferenceId={uuid}` | `ADMIN`, `CHAIR`, `AUTHOR`, `ASISTANT` | Lists registrations for one conference. |
| `GET` | `/registrations/my` | Authenticated JWT | Lists registrations for the authenticated user. |

### Create registration

Request:

```http
POST /registrations/register
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "conferenceId": "11111111-1111-1111-1111-111111111111"
}
```

Response `201 Created`:

```json
{
  "id": "22222222-2222-2222-2222-222222222222",
  "conferenceId": "11111111-1111-1111-1111-111111111111",
  "userId": "33333333-3333-3333-3333-333333333333",
  "active": false,
  "paymentStatus": "PENDING",
  "proofObjectKey": null,
  "createdAt": "2026-04-28T22:24:24.319Z"
}
```

The `userId` is read from the JWT `userId` claim. If that claim is absent or
blank, the service falls back to the JWT subject (`sub`). New registrations are
created with `active=false`, `paymentStatus=PENDING`, and no proof object key.

### Submit simulated payment

Request:

```http
POST /registrations/pay?conferenceId=11111111-1111-1111-1111-111111111111
Authorization: Bearer <jwt>
Content-Type: multipart/form-data

file=<payment-proof.jpg>
```

The `file` part is required. The service accepts only JPEG and PNG images,
verified from the file bytes. If a content type or filename extension is
provided, it must match the detected image type.

Response `200 OK`:

```json
{
  "id": "22222222-2222-2222-2222-222222222222",
  "conferenceId": "11111111-1111-1111-1111-111111111111",
  "userId": "33333333-3333-3333-3333-333333333333",
  "active": true,
  "paymentStatus": "APPROVED",
  "proofObjectKey": "registration-payment-proofs/11111111-1111-1111-1111-111111111111/33333333-3333-3333-3333-333333333333/44444444-4444-4444-4444-444444444444.jpg",
  "createdAt": "2026-04-28T22:24:24.319Z"
}
```

Payment submission looks up the registration by `(conferenceId, userId)`. If
none exists, it creates one. If an active approved registration already exists,
the endpoint returns it without uploading another proof. Otherwise, the proof is
stored under `registration-payment-proofs/{conferenceId}/{userId}/{randomUuid}.{ext}`
and the registration is marked active and approved.

### Check payment status

Request:

```http
GET /registrations/payment-status?conferenceId=11111111-1111-1111-1111-111111111111
Authorization: Bearer <jwt>
```

Response when approved:

```json
{
  "paid": true,
  "registrationId": "22222222-2222-2222-2222-222222222222",
  "paymentStatus": "APPROVED"
}
```

Response when the user has no registration for the conference:

```json
{
  "paid": false,
  "registrationId": null,
  "paymentStatus": null
}
```

Validation and business responses:

- `400 Bad Request` when `conferenceId` is missing or invalid.
- `400 Bad Request` when a payment proof file is missing, empty, not JPEG/PNG,
  or has a mismatched content type or extension.
- `404 Not Found` when `GET /registrations/register/{id}` cannot find the id.
- `409 Conflict` when the same user is already registered for the same
  conference.
- `502 Bad Gateway` when the payment proof cannot be uploaded to object storage.

## Security contract

`SecurityConfig` validates JWTs using the configured RSA public key and maps a
single `role` claim to Spring authorities. The role claim may be a string, an
object with a `name` field, or a collection where the first item is used.

Endpoint role checks currently expect these exact role names:

- `ADMIN`
- `AUTHOR`
- `CHAIR`
- `ASISTANT`

Use the `ASISTANT` spelling for assistant tokens; the service uppercases role
values but does not accept the English `ASSISTANT` spelling for role-restricted
endpoints.

The create endpoint requires the authenticated user's id to be a valid UUID in
the `userId` claim or in the JWT subject. Payment status and user-specific
queries use the same user-id resolution.

## Data model

Registrations are stored in the `registrations` table with:

- `id`: generated UUID primary key
- `conferenceId`: conference UUID
- `userId`: authenticated user UUID
- `active`: registration state, initially `false`
- `paymentStatus`: `PENDING` for created registrations, `APPROVED` after a
  successful payment proof submission
- `proofObjectKey`: Backblaze object key for the latest accepted payment proof
- `createdAt`: creation timestamp

The database enforces one registration per `(conferenceId, userId)` pair through
the `uk_registration_conference_user` unique constraint.

## Payment proof storage

Payment proof uploads use the AWS SDK `S3Client` configured for Backblaze B2:

- path-style access is enabled;
- the configured endpoint may include or omit `http://` or `https://`;
- objects are uploaded to the configured bucket with the detected image content
  type;
- object keys are persisted on the registration record, but the service does not
  expose signed download URLs.

If storage upload succeeds and a later database write fails because another
request created the same registration, the service retries against the existing
registration with a new object key.

## Troubleshooting

- **Startup fails with a missing placeholder:** add the required `SERVER_PORT`,
  `DB_URL`, `JWT_PUBLIC_KEY`, `auth.service.base-url`, and `BACKBLAZE_*` values
  to `.env` or the deployment environment.
- **Tests fail with `'url' must start with "jdbc"`:** the Spring context smoke
  test creates the datasource, so `DB_URL` must be set to a valid JDBC URL even
  when running `./mvnw test`.
- **Startup fails while loading the JWT key:** verify the key is an RSA public
  key in SPKI format. For the default properties, `JWT_PUBLIC_KEY` should contain
  the base64 body only, without PEM headers.
- **Requests receive `403 Forbidden`:** confirm the JWT `role` claim maps to one
  of the roles allowed for the endpoint. Assistant endpoints currently require
  the `ASISTANT` spelling.
- **Create registration returns `409 Conflict`:** the user already has a
  registration for that conference.
- **Payment upload returns `400 Bad Request`:** send a non-empty JPEG or PNG
  file. When provided, the multipart content type and filename extension must
  agree with the file bytes.
- **Payment upload returns `502 Bad Gateway`:** verify the Backblaze endpoint,
  region, bucket name, application key id, and application key secret.

## Tests

Run the current test suite with:

```bash
./mvnw test
```

The repository currently contains a Spring context smoke test. Because it loads
the application context, the same required configuration used for local startup
must be available to the test process. Endpoint, storage, and repository behavior
should be covered by additional tests when those contracts change.
