# authn-service

## Run locally

```
docker compose -f docker-compose.yml up -d
```
Then run AuthnServiceApplication using IntelliJ IDEA.

Once started, you can access the API documentation at:
http://localhost:8080/authn/swagger-ui.html

## Run tests locally in Windows

```
docker compose -f docker-compose-ci.yml up -d
.\mvnw test
```
