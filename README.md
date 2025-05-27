# account-management-services-spring-boot

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
2. Navigate to the `authentication-service` directory:

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