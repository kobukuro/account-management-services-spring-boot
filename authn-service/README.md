# authn-service

## Run locally

```
docker compose -f docker-compose.yml up -d
```
Then run AuthnServiceApplication using IntelliJ IDEA.

Once started, you can access the API documentation at:
http://localhost:8080/authn/swagger-ui.html

## Security Configuration

### Trusted Proxy IP Addresses

The service validates proxy headers (X-Forwarded-For, X-Real-IP) to prevent IP spoofing attacks. Only requests from trusted proxy IP addresses will have their proxy headers honored.

Configure trusted proxy IPs in your `.env` file:

```properties
TRUSTED_PROXY_IPS=127.0.0.1,::1
```

**Important:** When deploying behind a reverse proxy or load balancer (such as the API Gateway), add the proxy's IP address to this configuration. For example:

- Local development: `127.0.0.1,::1` (localhost IPv4 and IPv6)
- Docker environment: Include Docker bridge network IPs (e.g., `172.17.0.1`)
- Production: Include your load balancer or API Gateway internal IP addresses

If a request comes from an untrusted source, proxy headers are ignored and the direct remote address is used instead. This prevents malicious clients from spoofing their IP address in security logs.

## Run tests locally in Windows

```
docker compose -f docker-compose-ci.yml up -d
.\mvnw test
```
