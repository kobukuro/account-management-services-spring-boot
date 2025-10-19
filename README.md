# account-management-services-spring-boot

A microservices-based account management system built with Spring Boot 3.x and Java 21, featuring authentication, OAuth2 integration, and email notifications.

## Architecture

This project consists of three independent microservices:

- **api-gateway** (port 8000): Spring Cloud Gateway with Redis-based rate limiting
- **authn-service** (port 8080): Authentication service handling user registration, login, OAuth2, and password management
- **notification-service** (port 8081): Email notification service that consumes Kafka events

The authn-service publishes events to Kafka which are consumed by notification-service for asynchronous email processing.

```
Client → API Gateway (8000) → Authn Service (8080)
                                      ↓
                                  Kafka Events
                                      ↓
                              Notification Service (8081) → Email (SMTP)
```

## Features

### API Gateway
- Request routing to backend services
- IP-based rate limiting (Redis)
- Aggregated API documentation (Swagger UI)

### Authentication Service
- Local email/password authentication with email verification
- Google OAuth2 authentication
- JWT-based access and refresh tokens
- Password reset flows
- Multi-authentication support (users can link both local and OAuth methods)
- PostgreSQL database with Flyway migrations

### Notification Service
- Kafka consumer for email events
- Thymeleaf HTML email templates
- SMTP email delivery
- Strategy pattern for extensible notification types

## Prerequisites

- Java 21
- Docker & Docker Compose
- Maven (or use included wrapper: `mvnw`/`mvnw.cmd`)

## Run locally

### 1. Run the API gateway service

1. Navigate to the `api-gateway` directory:

    ```
    cd api-gateway
    ```
2. Create a .env file and configure environment variables by referring to the .env.sample file.
3. Run the required containers:

    ```
    docker compose up -d
    ```
4. Execute the main method of ApiGatewayApplication class located in the following path:

   ```
   account-management-services-spring-boot\api-gateway\src\main\java\com\peter\apigateway\ApiGatewayApplication.java
   ```

### 2. Run the authentication service

1. Navigate back to the root directory:

    ```
    cd ..
    ```
2. Navigate to the `authn-service` directory:

    ```
    cd authn-service
    ```
3. Create a .env file and configure environment variables by referring to the .env.sample file.
4. Run the required containers:

    ```
    docker compose up -d
    ```
5. Execute the main method of AuthnServiceApplication class located in the following path:

   ```
   account-management-services-spring-boot\authn-service\src\main\java\com\peter\authnservice\AuthnServiceApplication.java
   ```

### 3. Run the notification service

1. Navigate back to the root directory:

    ```
    cd ..
    ```
2. Navigate to the `notification-service` directory:

    ```
    cd notification-service
    ```
3. Create a .env file and configure environment variables by referring to the .env.sample file.
4. Execute the main method of NotificationServiceApplication class located in the following path:

   ```
   account-management-services-spring-boot\notification-service\src\main\java\com\peter\notificationservice\NotificationServiceApplication.java
   ```

Then go to http://localhost:8000/swagger-ui.html to view the API documentation.

## Development

### Running Tests

**Authentication Service** (has comprehensive test suite):

```bash
cd authn-service
docker compose -f docker-compose-ci.yml up -d
./mvnw test                        # All tests
./mvnw test -Dtest=ClassName      # Specific test class
```

### Building

Each service has independent Maven build:

```bash
cd <service-name>
./mvnw clean package              # Build with tests
./mvnw clean package -DskipTests  # Skip tests
```

### Database Migrations

The authentication service uses Flyway for database migrations. Migrations are located in `authn-service/src/main/resources/db/migration/`.

**View migration status:**
```bash
cd authn-service
./mvnw flyway:info
```

**Important:** Never modify existing migrations. Always create new migrations as `V{next_number}__description.sql`.

## Technology Stack

### Common
- Java 21
- Spring Boot 3.4.x
- Maven
- Docker & Docker Compose
- Lombok

### API Gateway
- Spring Cloud Gateway 2024.0.0
- Spring Data Redis Reactive
- SpringDoc OpenAPI

### Authentication Service
- Spring Data JPA
- PostgreSQL
- Spring Security
- Spring Kafka (Producer)
- Flyway
- Auth0 java-jwt
- BCrypt

### Notification Service
- Spring Kafka (Consumer)
- Spring Mail (JavaMail)
- Thymeleaf
