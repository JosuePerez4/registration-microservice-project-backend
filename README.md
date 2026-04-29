# Registration Microservice

Spring Boot service for creating and querying conference registrations.

The service exposes a small REST API under `/registrations`, persists
registrations in PostgreSQL, and protects the registration endpoints with JWT
roles.

## Stack

- Java 21
- Spring Boot 4.0.5
- Spring Web MVC, Spring Data JPA, Bean Validation, Spring Security
- OAuth2 Resource Server with RSA JWT validation
- PostgreSQL
- Springdoc OpenAPI UI

## Architecture

The code follows a small layered structure:

- `presentation/controller`: HTTP endpoints and JWT principal extraction.
- `presentation/exception`: API-level validation error formatting.
- `application/service`: registration use cases and DTO mapping.
- `domain/model` and `domain/repository`: JPA entity and Spring Data access.
- `infrastructure/security`: stateless resource-server configuration and role
  mapping.
- `infrastructure/client`: `RestClient` integration point for the auth service.

Create requests flow from `RegistrationController` to `RegistrationService`,
which persists a `Registration` through `RegistrationRepository`. The controller
does not trust a user id from the request body; it derives the `userId` from the
validated JWT before calling the service.

## Local setup

### Prerequisites

- JDK 21
- A reachable PostgreSQL database
- A public RSA key that matches the JWTs issued by the authentication service

### Configuration

`src/main/resources/application.properties` imports `.env` from the project root
if present. Create one locally with the required values:

```properties
SERVER_PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/registration
auth.service.base-url=http://localhost:8081
jwt.public.key=-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqh...\n-----END PUBLIC KEY-----
```

| Property or variable | Required | Used by | Notes |
| --- | --- | --- | --- |
| `SERVER_PORT` | Yes | `server.port` | HTTP port for the service. |
| `DB_URL` | Yes | `spring.datasource.url` | PostgreSQL JDBC URL. Credentials can be included in the URL or supplied by the environment if configured externally. |
| `jwt.public.key` | Yes | `SecurityConfig` | RSA public key in PEM format. Escaped `\n` sequences are converted to real line breaks at startup. |
| `auth.service.base-url` | Yes | `AuthClient` | Base URL for the auth service. The current client calls `/api/v1/auth/me`; no controller currently invokes it, but the bean is created at startup. |

JPA currently runs with `spring.jpa.hibernate.ddl-auto=update` and SQL logging
enabled. Treat those defaults as development-oriented unless profiles override
them in the deployment environment.

### Run

```bash
./mvnw spring-boot:run
```

OpenAPI is available when the service is running:

- Swagger UI: `http://localhost:${SERVER_PORT}/swagger-ui.html`
- OpenAPI JSON: `http://localhost:${SERVER_PORT}/v3/api-docs`

## API

All registration endpoints require a bearer JWT.

| Method | Path | Roles | Description |
| --- | --- | --- | --- |
| `POST` | `/registrations/register` | `AUTHOR`, `ASSISTANT` | Creates a registration for the authenticated user. |
| `GET` | `/registrations/register/{id}` | `ADMIN`, `CHAIR`, `AUTHOR`, `ASSISTANT` | Fetches one registration by id. |
| `GET` | `/registrations/register-list?conferenceId={uuid}` | `ADMIN`, `CHAIR`, `AUTHOR`, `ASSISTANT` | Lists registrations for one conference. |

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
  "createdAt": "2026-04-28T22:24:24.319Z"
}
```

The `userId` is read from the JWT `userId` claim. If that claim is absent or
blank, the service falls back to the JWT subject (`sub`). New registrations are
created with `active=false`.

Validation and business responses:

- `400 Bad Request` when `conferenceId` is missing or invalid.
- `404 Not Found` when `GET /registrations/register/{id}` cannot find the id.
- `409 Conflict` when the same user is already registered for the same
  conference.

Validation errors handled by `GlobalExceptionHandler` use this shape:

```json
{
  "timestamp": "2026-04-28T22:24:24.319Z",
  "status": 400,
  "error": "Bad Request",
  "message": "conferenceId: conferenceId es obligatorio"
}
```

### Read registration

```http
GET /registrations/register/22222222-2222-2222-2222-222222222222
Authorization: Bearer <jwt>
```

Response `200 OK` uses the same JSON shape as create. If the id is not present
in the database, `RegistrationService` raises `404 Not Found`.

### List registrations for a conference

```http
GET /registrations/register-list?conferenceId=11111111-1111-1111-1111-111111111111
Authorization: Bearer <jwt>
```

Response `200 OK`:

```json
[
  {
    "id": "22222222-2222-2222-2222-222222222222",
    "conferenceId": "11111111-1111-1111-1111-111111111111",
    "userId": "33333333-3333-3333-3333-333333333333",
    "active": false,
    "createdAt": "2026-04-28T22:24:24.319Z"
  }
]
```

The list endpoint filters only by `conferenceId`; it does not filter by the
authenticated user. Access is controlled by role in `SecurityConfig`.

## Security contract

`SecurityConfig` validates JWTs using the configured RSA public key and maps a
single `role` claim to Spring authorities. Supported roles are:

- `ADMIN`
- `AUTHOR`
- `CHAIR`
- `ASSISTANT`

The role claim may be a string, an object with a `name` field, or a collection
where the first item is used. The legacy spelling `ASISTANT` is normalized to
`ASSISTANT`.

The create endpoint requires the authenticated user's id to be a valid UUID in
the `userId` claim or in the JWT subject.

Requests without a valid bearer token fail authentication before reaching the
controller. Tokens with a missing, unsupported, or malformed `role` claim do not
receive a Spring role authority and therefore cannot access the protected
registration endpoints.

### Auth service client

`AuthClient` is a reusable integration hook for the authentication service. It
builds a Spring `RestClient` with `auth.service.base-url` and calls:

```http
GET /api/v1/auth/me
Authorization: Bearer <jwt>
```

The expected response fields currently modeled by `AuthMeResponse` are `id` and
`role`; unknown fields are ignored. No current registration endpoint calls this
client, so JWT validation and claim extraction are the active authorization path.

## Data model

Registrations are stored in the `registrations` table with:

- `id`: generated UUID primary key
- `conferenceId`: conference UUID
- `userId`: authenticated user UUID
- `active`: registration state, initially `false`
- `createdAt`: creation timestamp

The database enforces one registration per `(conferenceId, userId)` pair through
the `uk_registration_conference_user` unique constraint.

## Troubleshooting

- **Startup fails with missing placeholder `SERVER_PORT`, `DB_URL`,
  `jwt.public.key`, or `auth.service.base-url`:** add the value to `.env` or the
  deployment environment.
- **Tests fail with `'url' must start with "jdbc"`:** the Spring context smoke
  test creates the datasource, so `DB_URL` must be set to a valid JDBC URL even
  when running `./mvnw test`.
- **Startup fails while loading the JWT key:** verify the key is an RSA public
  key in PEM format and that multiline values are escaped with `\n` when stored
  on one line.
- **Requests receive `403 Forbidden`:** confirm the JWT `role` claim maps to one
  of the roles allowed for the endpoint.
- **Create registration returns `409 Conflict`:** the user already has a
  registration for that conference.

## Tests

Run the current test suite with:

```bash
./mvnw test
```

The repository currently contains a Spring context smoke test. Because it loads
the application context, the same required configuration used for local startup
must be available to the test process. Endpoint and repository behavior should
be covered by additional tests when those contracts change.
