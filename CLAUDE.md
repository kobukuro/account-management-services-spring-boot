# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a microservices-based account management system built with Spring Boot 3.x and Java 21. The system consists of three services:

1. **api-gateway** (port 8000): Spring Cloud Gateway with Redis-based rate limiting
2. **authn-service** (port 8080): Authentication service handling user registration, login, OAuth2, and password management
3. **notification-service** (port 8081): Email notification service that consumes Kafka events

The authn-service publishes events to Kafka which are consumed by notification-service for asynchronous email processing. Services are designed to be run independently.

## Development Commands

### Running the Full Stack Locally

Each service must be started independently in the following order:

1. **API Gateway**:
```bash
cd api-gateway
# Create .env file based on .env.sample
docker compose up -d  # Starts Redis
# Run ApiGatewayApplication from IDE or: ./mvnw spring-boot:run
```

2. **Authentication Service**:
```bash
cd authn-service
# Create .env file based on .env.sample
docker compose up -d  # Starts PostgreSQL + Kafka
# Run AuthnServiceApplication from IDE or: ./mvnw spring-boot:run
```

3. **Notification Service**:
```bash
cd notification-service
# Create .env file based on .env.sample
# DO NOT run docker compose (use Kafka from authn-service)
# Run NotificationServiceApplication from IDE or: ./mvnw spring-boot:run
```

Access point:
- http://localhost:8000/swagger-ui.html

**Note**: For standalone notification-service development (without other services), you can run `docker compose up -d` to start its own Kafka instance. See [Kafka Usage Patterns](#kafka-usage-patterns) for details.

### Testing

**Authentication Service** (has comprehensive test suite):
```bash
cd authn-service
docker compose -f docker-compose-ci.yml up -d
.\mvnw test                        # Windows
./mvnw test                        # Unix
.\mvnw test -Dtest=ClassName      # Run specific test class
```

**Other Services**: No test infrastructure currently configured.

### Building

Each service has independent Maven build:
```bash
cd <service-name>
.\mvnw clean package              # Build with tests
.\mvnw clean package -DskipTests  # Skip tests
```

## System Architecture

### Service Communication Flow

```
Client → API Gateway (8000) → Authn Service (8080)
                                      ↓
                                  Kafka Events
                                      ↓
                              Notification Service (8081) → Email (SMTP)
```

### API Gateway (Spring Cloud Gateway)

The gateway acts as the single entry point, providing:
- **Route forwarding**: Proxies requests to backend services based on path predicates
- **Rate limiting**: IP-based rate limiting using Redis
  - Registration: 2 req/s, burst of 4
  - Activation: 1 req/s, burst of 2
- **API documentation aggregation**: Combines Swagger docs from all services

**Key Configuration** (`api-gateway/src/main/resources/application.yml`):
- Routes are defined with predicates (Path, Method) and filters (RequestRateLimiter)
- Rate limiter uses `ipKeyResolver` bean (defined in `GatewayConfig.java`) to limit by IP address
- Requires Redis for rate limiting state storage

### Authentication Service (Spring Boot + PostgreSQL + Kafka)

Comprehensive authentication service (Spring Boot 3.4.4, Java 21) providing:
- Local email/password authentication with email verification
- Google OAuth2 authentication
- JWT-based access/refresh tokens
- Password reset flows
- Multi-authentication support (users can have both local and OAuth methods)

#### Authentication Model

The system uses a flexible multi-authentication approach:
- **AppUser**: Core user entity containing basic user information (id, firstName, lastName, enabled status)
- **UserAuthentication**: Separate authentication method entity supporting multiple auth types per user
  - `AuthenticationType.LOCAL`: Email/password authentication (requires verification)
  - `AuthenticationType.GOOGLE`: Google OAuth2 authentication (auto-verified)
  - Unique constraint on `(type, email)` to prevent duplicate auth methods
  - For OAuth: stores `providerId` (provider-specific user ID) instead of password

This design allows users to have both local and OAuth authentication methods simultaneously.

#### Security Architecture

- **JwtFilter** (`config/JwtFilter.java`): Validates JWT tokens on protected endpoints, extracts userId and loads UserDetails
- **SecurityConfig** (`config/SecurityConfig.java`): Configures public/protected paths, CORS, and stateless session management
- **Public endpoints** (no auth required):
  - User registration, login, activation
  - Password reset flows
  - Token refresh
  - Google OAuth login
  - API docs/Swagger UI
- **Protected endpoints**: All others require valid JWT in `Authorization: Bearer <token>` header

#### JWT Token Types

The service uses different JWT tokens for different purposes:
- **Access Token**: Short-lived token for API authentication (configured via `ACCESS_TOKEN_EXPIRATION_MILLISECONDS`)
- **Refresh Token**: Long-lived token to obtain new access tokens (configured via `REFRESH_TOKEN_EXPIRATION_MILLISECONDS`)
- **Verification Token**: Single-use token for email verification (configured via `VERIFICATION_TOKEN_EXPIRATION_MILLISECONDS`)
- **Reset Password Token**: Single-use token for password reset (configured via `RESET_PASSWORD_TOKEN_EXPIRATION_MILLISECONDS`)

All tokens are signed with `SECRET_KEY` and contain userId as the subject. JWT logic is in `util/JwtUtils.java`.

#### Event-Driven Architecture

The service publishes events to Kafka topics for asynchronous processing (handled by notification-service):
- **Topics**: `user_registration`, `resend_activation`, `password_reset`, `password_reset_confirm`, `password_change`
- **Event Structure**: Contains `UserDetails` (name, email) and `Email` (subject, template, template variables)
- Events are published via `KafkaTemplate<String, Event>` with JSON serialization

#### Google OAuth2 Flow

1. Frontend redirects user to Google OAuth consent screen
2. Google redirects back with authorization code
3. Service exchanges code for access token at `https://oauth2.googleapis.com/token`
4. Service retrieves user info from `https://www.googleapis.com/oauth2/v2/userinfo`
5. Creates new user or links to existing OAuth authentication by `providerId`
6. Returns JWT token pair

**OAuth Error Handling** (in `service/impl/UserServiceImpl.java`):
- `invalid_grant`: Authorization code is invalid/expired/already used → throws `InvalidAuthorizationCodeException`
- `redirect_uri_mismatch`: Redirect URI doesn't match registered URI → throws `MismatchRedirectUriException`

#### Database Migrations

Flyway migrations are located in `authn-service/src/main/resources/db/migration/`:
- `V1__create_app_user_table.sql`: Initial user table
- `V2__delete_duplicate_index.sql`: Index cleanup
- `V3__alter_app_user_id_to_uuid.sql`: Migration to UUID primary key
- `V4__remove_email_password_from_app_user.sql`: Moved email/password to separate table
- `V5__create_user_authentication_table.sql`: Created multi-auth support table
- `V6__add_provider_id_to_user_authentication.sql`: Added OAuth provider ID support

**Always create new migrations as `V{next_number}__description.sql`. Never modify existing migrations.**

#### Maven Profiles

- `dev` (default): Development profile with Spring DevTools
- `ci`: Continuous Integration profile for testing

**Key Files**:
- Security: `config/SecurityConfig.java`, `config/JwtFilter.java`, `util/JwtUtils.java`
- OAuth: `service/impl/UserServiceImpl.java` (Google OAuth flow)
- Database: `domain/entity/AppUser.java`, `domain/entity/UserAuthentication.java`
- Controllers: `controller/UserController.java`
- Exception handling: `exception/GlobalExceptionHandler.java`

### Notification Service (Spring Boot + Kafka Consumer + JavaMail)

Event-driven email notification service:
- **Kafka Consumer**: Listens to topics published by authn-service
  - `user_registration`, `resend_activation`, `password_reset`, `password_reset_confirm`, `password_change`
- **Email Sending**: Uses Spring Mail (JavaMail) with Thymeleaf templates
- **Template-based**: HTML email templates in `src/main/resources/templates/email/`
  - `verification-email.html`: User registration/activation
  - `reset-password-email.html`: Password reset request
  - `reset-password-confirm-email.html`: Password reset confirmation
  - `change-password-email.html`: Password change notification

**Event Structure** (`domain/event/Event.java`):
```java
record Event(UserDetails userDetails, Email email)
record UserDetails(String firstName, String lastName, String email)
record Email(String subject, String template, Map<String, String> variables)
```

**Strategy Pattern**: Uses `NotificationStrategy` interface with `EmailNotificationStrategy` implementation for extensibility.

## Technology Stack

### Common Technologies
- Java 21
- Spring Boot 3.4.x
- Maven (wrapper included: `mvnw`/`mvnw.cmd`)
- Lombok
- Docker Compose for infrastructure

### Service-Specific Technologies

**API Gateway**:
- Spring Cloud Gateway 2024.0.0 (WebFlux/Reactive)
- Spring Data Redis Reactive
- SpringDoc OpenAPI (WebFlux)

**Authn Service**:
- Spring Data JPA + PostgreSQL
- Spring Security
- Spring Kafka (Producer)
- Flyway (Database migrations)
- Auth0 java-jwt 3.18.1
- BCrypt password hashing

**Notification Service**:
- Spring Kafka (Consumer)
- Spring Mail (JavaMail)
- Thymeleaf (Email templates)

## Configuration Notes

### Environment Variables

Each service requires a `.env` file (see `.env.sample` in each service directory):

**api-gateway**:
- `AUTHN_URI`: Authentication service URL (e.g., `http://localhost:8080`)
- `REDIS_PASSWORD`: Redis container password

**authn-service** (extensive, see `authn-service/.env.sample`):
- JWT configuration: `SECRET_KEY`, token expiration times
- Database: PostgreSQL connection
- Kafka: Bootstrap servers
- OAuth: `GOOGLE_OAUTH2_CLIENT_ID`, `GOOGLE_OAUTH2_CLIENT_SECRET`
- CORS: `ALLOWED_ORIGINS`
- Frontend: `FRONTEND_URL` (for email links)

**notification-service**:
- SMTP configuration: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`

### Docker Compose Files

Each service has its own `docker-compose.yml` for local development, following microservices independence principles:
- **api-gateway**: Redis only
- **authn-service**: PostgreSQL + Kafka
- **notification-service**: Kafka only (for standalone development)

**authn-service** also has `docker-compose-ci.yml` for CI/test environment with separate test database.

#### Kafka Usage Patterns

Following microservices best practices, each service can be developed independently. However, when running the full stack, **only one Kafka instance should be used**:

**Scenario 1: Standalone Service Development**
```bash
# Develop notification-service independently
cd notification-service
docker compose up -d        # Starts its own Kafka
./mvnw spring-boot:run      # Connects to localhost:9092
```

**Scenario 2: Full Stack Development** (Recommended)
```bash
# 1. Start authn-service with infrastructure
cd authn-service
docker compose up -d        # Starts PostgreSQL + Kafka
./mvnw spring-boot:run

# 2. Start notification-service (no containers)
cd notification-service
./mvnw spring-boot:run      # Connects to authn-service's Kafka (localhost:9092)
```

**Important**: Both `authn-service/docker-compose.yml` and `notification-service/docker-compose.yml` define Kafka with the same `CLUSTER_ID` (`tJjunm5nTDOkCqLR5JO6dw`). This ensures consistency, but **do not start both Kafka containers simultaneously** as they use the same ports (9092, 9093) and container name.

## Project Structure

```
account-management-services-spring-boot/
├── api-gateway/               # Spring Cloud Gateway (port 8000)
│   ├── src/main/java/com/peter/apigateway/
│   │   ├── config/           # GatewayConfig (rate limiting), RedisConfig
│   │   └── ApiGatewayApplication.java
│   └── src/main/resources/
│       └── application.yml   # Routes, rate limiting, Redis config
├── authn-service/            # Authentication service (port 8080)
│   ├── src/main/java/com/peter/authnservice/
│   │   ├── config/          # Security, CORS, JWT filter
│   │   ├── controller/      # REST controllers
│   │   ├── domain/
│   │   │   ├── dto/        # Request/response DTOs
│   │   │   ├── entity/     # JPA entities
│   │   │   └── event/      # Kafka event records
│   │   ├── exception/      # Custom exceptions, global handler
│   │   ├── repository/     # Spring Data JPA repositories
│   │   ├── service/        # Business logic
│   │   └── util/           # JwtUtils
│   ├── src/main/resources/
│   │   ├── db/migration/   # Flyway SQL migrations
│   │   ├── application.yml # Base configuration
│   │   ├── application-dev.yml
│   │   └── application-ci.yml
└── notification-service/    # Email notification service (port 8081)
    ├── src/main/java/com/peter/notificationservice/
    │   ├── domain/event/   # Event records (mirrors authn-service)
    │   └── service/
    │       ├── notification/ # Strategy pattern for notifications
    │       ├── EmailService.java
    │       └── NotificationService.java
    └── src/main/resources/
        ├── templates/email/ # Thymeleaf email templates
        └── application.yml  # Kafka consumer, SMTP config

```

## Development Workflow

### Adding New Features

1. **New API Endpoint (authn-service)**:
   - Add controller method in `controller/`
   - Implement business logic in `service/impl/`
   - Create DTOs in `domain/dto/`
   - Add integration tests
   - Update gateway routes if needed

2. **New Notification Type**:
   - Add Kafka topic in authn-service publisher
   - Create HTML template in `notification-service/src/main/resources/templates/email/`
   - Add consumer method in `NotificationServiceImpl`

3. **Database Schema Changes**:
   - Create new Flyway migration in `authn-service/src/main/resources/db/migration/`
   - Format: `V{next_number}__description.sql`
   - NEVER modify existing migrations

4. **Gateway Route Changes**:
   - Update `api-gateway/src/main/resources/application.yml`
   - Add route with predicates and optional rate limiting

### Common Pitfalls

- **Service Startup Order**: Always start authn-service before notification-service (Kafka dependency)
- **Environment Files**: Each service needs its own `.env` file configured
- **Port Conflicts**: Ensure ports 8000, 8080, 8081, 5432, 6379, 9092 are available
- **JWT Secret**: Use a strong `SECRET_KEY` in production, never commit .env files
- **Flyway**: Never modify existing migrations; always create new ones for schema changes
- **Rate Limiting**: Requires Redis to be running; gateway will fail to start without it

## Maven Wrapper Usage

All commands use Maven wrapper (no need to install Maven globally):
- Windows: `.\mvnw <command>`
- Unix/Mac: `./mvnw <command>`

Common commands:
- `.\mvnw clean install`: Build and install
- `.\mvnw spring-boot:run`: Run Spring Boot app
- `.\mvnw dependency:tree`: View dependency tree
- `.\mvnw flyway:info`: View Flyway migration status (in authn-service)
