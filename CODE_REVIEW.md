# 專案程式碼審查報告 (Code Review Report)

**審查日期**: 2026-02-17
**專案**: account-management-services-spring-boot
**審查範圍**: 全專案 (api-gateway, authn-service, notification-service)

---

## 📊 執行摘要

本報告針對 account-management-services-spring-boot 專案進行全面的程式碼審查，涵蓋三個微服務：
- **authn-service** (port 8080) - ✅ 已實作
- **api-gateway** (port 8000) - ⚠️ 部分實作
- **notification-service** (port 8081) - ⚠️ 部分實作

### 整體評分

| 類別 | 評分 | 說明 |
|------|------|------|
| **安全性** | 6/10 | 有基本安全措施，但缺少關鍵防護（CORS、速率限制） |
| **程式碼品質** | 7/10 | 結構良好，但存在重複和複雜度問題 |
| **測試覆蓋率** | 5/10 | authn-service 良好，其他服務嚴重不足 |
| **架構設計** | 8/10 | 優秀的微服務架構和事件驅動設計 |
| **整體** | **6.5/10** | 良好基礎，需改進關鍵安全問題 |

---

## 🔴 關鍵問題 (Critical Issues) - 需立即修復

### 1. 安全性缺陷

#### 1.1 ⚠️ CORS 設定缺失
**檔案**: `authn-service/src/main/java/com/peter/authnservice/config/SecurityConfig.java`

**問題描述**:
- SecurityConfig 中完全沒有 CORS 設定
- 這可能導致跨來源攻擊或阻擋合法的前端請求

**風險等級**: 🔴 高

**修復建議**:
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // 從環境變數讀取允許的來源
    String allowedOrigins = env.getProperty("cors.allowed-origins", "http://localhost:3000");
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}

// 在 SecurityFilterChain 中啟用 CORS
http.cors(withDefaults())
```

**環境變數設定** (`.env`):
```bash
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://yourdomain.com
```

---

#### 1.2 ⚠️ 認證端點沒有速率限制
**檔案**: `authn-service/src/main/java/com/peter/authnservice/controller/UserController.java`

**問題描述**:
- 登入、註冊、重設密碼等關鍵端點沒有速率限制
- 容易遭受暴力破解攻擊和 DoS 攻擊

**風險等級**: 🔴 高

**受影響的端點**:
- `POST /api/v1/users/login` - 登入端點
- `POST /api/v1/users/reset-password` - 密碼重設
- `POST /api/v1/users` - 使用者註冊
- `POST /api/v1/users/resend-activation` - 重新發送啟用信

**修復建議** (使用 Bucket4j):

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>
```

```java
// config/RateLimitConfig.java
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.create(10); // 每秒 10 個請求
    }
}

// controller/UserController.java
@PostMapping("/login")
public ResponseEntity<LoginResponse> login(
    @Valid @RequestBody LoginRequest request,
    HttpServletRequest httpRequest
) {
    String clientIp = getClientIp(httpRequest);
    if (!rateLimiter.tryAcquire(clientIp)) {
        throw new RateLimitExceededException("Too many attempts");
    }
    // ... 現有邏輯
}
```

**建議限制**:
- 登入端點: 5 req/min, 10 req/hour
- 註冊端點: 3 req/min, 10 req/hour
- 密碼重設: 3 req/hour
- 重新發送啟用: 3 req/hour

---

#### 1.3 ⚠️ getUsername() 方法回傳 null
**檔案**: `authn-service/src/main/java/com/peter/authnservice/domain/entity/AppUser.java:56-58`

**問題描述**:
```java
@Override
public String getUsername() {
    return null; // 永遠回傳 null
}
```

**風險等級**: 🔴 高

**影響**:
- 可能導致 Spring Security 授權機制異常
- UserDetails 實作不完整

**修復建議**:
```java
@Override
public String getUsername() {
    // 回傳使用者的主要 email 作為使用者名稱
    return this.authentications.stream()
        .filter(auth -> auth.getType() == AuthenticationType.LOCAL)
        .map(UserAuthentication::getEmail)
        .findFirst()
        .orElse(null);
}
```

---

### 2. 事務管理衝突

#### 2.1 ⚠️ 類別層級 @Transactional 與手動事務管理衝突
**檔案**: `authn-service/src/main/java/com/peter/authnservice/service/impl/UserServiceImpl.java`

**問題描述**:
- 類別層級有 `@Transactional` 註解 (line 51)
- 但 `uploadProfilePicture()` 方法 (line 493) 使用手動事務管理
- 這會導致事務行為不一致

**風險等級**: 🔴 高

**修復建議**:
```java
// 移除類別層級的 @Transactional
// 在需要的方法上單獨標註

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    // ... 依賴注入

    @Transactional
    public User registerUser(UserRegistrationRequest request) {
        // 需要事務的方法
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void uploadProfilePicture(UUID userId, MultipartFile file) {
        // 特殊事務設定
    }

    // 不需要事務的方法不標註
    public LoginResponse loginUser(LoginRequest request) {
        // ...
    }
}
```

---

### 3. 敏感資訊暴露

#### 3.1 ⚠️ 硬編碼的資料庫憑證
**檔案**: `authn-service/src/main/resources/application-dev.yml:4-5`

**問題描述**:
```yaml
spring:
  datasource:
    username: postgres
    password: postgres
```

**風險等級**: 🔴 高

**修復建議**:
```yaml
spring:
  datasource:
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
```

**環境變數** (`.env`):
```bash
DB_USERNAME=your_secure_username
DB_PASSWORD=your_secure_password
```

---

## 🟡 高優先級問題 (High Priority Issues)

### 4. 程式碼品質問題

#### 4.1 程式碼重複
**檔案**: `authn-service/src/main/java/com/peter/authnservice/service/impl/UserServiceImpl.java`

**問題位置**:
- Lines 184-186, 206-208, 274-276, 319-321, 456-458, 574-577

**重複的驗證邏輯**:
```java
// 在多個方法中重複出現
if (!user.isEnabled()) {
    throw new UserDisabledException("User account is disabled");
}
if (!user.isEmailVerified()) {
    throw new EmailNotVerifiedException("Email not verified");
}
```

**修復建議**:
```java
// 提取為私有方法
private void validateUserStatus(AppUser user) {
    if (!user.isEnabled()) {
        throw new UserDisabledException("User account is disabled");
    }
    if (!user.isEmailVerified()) {
        throw new EmailNotVerifiedException("Email not verified");
    }
}

// 在各方法中使用
public User registerUser(UserRegistrationRequest request) {
    // ...
    validateUserStatus(user);
    // ...
}
```

---

#### 4.2 方法複雜度過高
**檔案**: `authn-service/src/main/java/com/peter/authnservice/service/impl/UserServiceImpl.java`

**問題方法**:
1. `uploadProfilePicture()` (line 493) - 70+ 行
2. `exchangeCodeForAccessToken()` (line 369) - 45 行

**修復建議**:

```java
// 重構 uploadProfilePicture
private void validateProfilePicture(MultipartFile file) {
    if (file.isEmpty()) {
        throw new InvalidFileException("File is empty");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
        throw new FileSizeExceededException("File size exceeds limit");
    }
    String contentType = file.getContentType();
    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
        throw new InvalidFileException("Invalid file type");
    }
}

private String generateS3Key(UUID userId, String fileName) {
    String sanitizedFileName = sanitizeFileName(fileName);
    return String.format("profile-pictures/%s/%s", userId, sanitizedFileName);
}

private String sanitizeFileName(String fileName) {
    return fileName.replaceAll("[^a-zA-Z0-9.\\-]", "_");
}

private String uploadFileToS3(InputStream inputStream, String s3Key,
                              long contentLength, String contentType) {
    // S3 上傳邏輯
}

public void uploadProfilePicture(UUID userId, MultipartFile file) {
    validateProfilePicture(file);
    String s3Key = generateS3Key(userId, file.getOriginalFilename());

    try (InputStream inputStream = file.getInputStream()) {
        String fileUrl = uploadFileToS3(inputStream, s3Key,
                                        file.getSize(), file.getContentType());
        updateUserProfilePicture(userId, fileUrl);
    } catch (IOException e) {
        throw new FileUploadException("Failed to process file", e);
    }
}
```

---

#### 4.3 資源管理問題
**檔案**: `authn-service/src/main/java/com/peter/authnservice/service/impl/UserServiceImpl.java:502, 519`

**問題**: InputStream 沒有使用 try-with-resources

**修復建議**:
```java
// 修復前
InputStream inputStream = file.getInputStream();
// ... 使用 inputStream

// 修復後
try (InputStream inputStream = file.getInputStream()) {
    // ... 使用 inputStream
} catch (IOException e) {
    throw new FileUploadException("Failed to process file", e);
}
```

---

### 5. 安全性強化

#### 5.1 目錄遍歷漏洞
**檔案**: `authn-service/src/main/java/com/peter/authnservice/service/impl/UserServiceImpl.java:514`

**問題**:
```java
String s3Key = String.format("profile-pictures/%s/%s", userId, fileName);
```

如果 `userId` 或 `fileName` 包含路徑字符（如 `../`），可能導致目錄遍歷攻擊。

**風險等級**: 🟡 中高

**修復建議**:
```java
private String sanitizeUserId(UUID userId) {
    // UUID 本身是安全的，但仍需驗證格式
    if (userId == null) {
        throw new IllegalArgumentException("User ID cannot be null");
    }
    return userId.toString();
}

private String sanitizeFileName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
        throw new IllegalArgumentException("File name cannot be null or empty");
    }

    // 移除路徑分隔符和特殊字符
    String sanitized = fileName.replaceAll("[^a-zA-Z0-9.\\-]", "_");

    // 防止路徑遍歷
    if (sanitized.contains("..") || sanitized.contains("/") || sanitized.contains("\\")) {
        throw new SecurityException("Invalid file name");
    }

    return sanitized;
}

private String generateS3Key(UUID userId, String fileName) {
    String sanitizedUserId = sanitizeUserId(userId);
    String sanitizedFileName = sanitizeFileName(fileName);
    return String.format("profile-pictures/%s/%s", sanitizedUserId, sanitizedFileName);
}
```

---

#### 5.2 JWT 缺少 nbf 聲明
**檔案**: `authn-service/src/main/java/com/peter/authnservice/util/JwtUtils.java`

**問題**: JWT token 沒有包含 "nbf" (not before) 聲明，可能導致 token 被提前使用。

**風險等級**: 🟡 中

**修復建議**:
```java
public String generateAccessToken(UUID userId) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + accessTokenExpirationMilliseconds);

    return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .notBefore(now)  // 新增 nbf 聲明
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact();
}
```

---

#### 5.3 缺少內容安全政策標頭
**檔案**: `authn-service/src/main/java/com/peter/authnservice/config/SecurityConfig.java`

**問題**: 沒有設定 CSP 標頭來緩解 XSS 風險

**風險等級**: 🟡 中

**修復建議**:
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // ... 現有設定

        // 新增安全標頭
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self';"))
            .xssProtection(xss -> xss
                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            .frameOptions(frame -> frame.deny())
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true)
                .maxAgeInSeconds(31536000))
            .contentTypeOptions(content -> content.disable())
        );

    return http.build();
}
```

---

### 6. 測試覆蓋率問題

#### 6.1 ❌ Notification Service 完全沒有測試
**目錄**: `notification-service/src/test/`

**問題**:
- 零測試覆蓋
- 沒有 Kafka consumer 測試
- 沒有 Email 發送測試
- 沒有錯誤處理測試

**風險等級**: 🟡 高

**修復建議**: 新增以下測試類別

```java
// 測試 Kafka Consumer
@EmbeddedKafka
class NotificationServiceConsumerTest {

    @Autowired
    private KafkaTemplate<String, Event> kafkaTemplate;

    @Captor
    private ArgumentCaptor<Event> eventCaptor;

    @Test
    void shouldConsumeUserRegistrationEvent() {
        // Arrange
        Event event = createTestEvent();

        // Act
        kafkaTemplate.send("user_registration", event);

        // Assert
        // 驗證 EmailService 被呼叫
        verify(emailService).sendEmail(any(), any(), any());
    }
}

// 測試 Email Service
class EmailServiceTest {

    @Test
    void shouldSendVerificationEmail() throws MessagingException {
        // Arrange
        UserDetails userDetails = new UserDetails("John", "Doe", "john@example.com");
        Email email = new Email("Verify Email", "verification-email", Map.of());

        // Act
        emailService.sendEmail(userDetails, email);

        // Assert
        // 驗證 JavaMailSender 被正確呼叫
    }
}
```

---

#### 6.2 ⚠️ API Gateway 測試不足
**目錄**: `api-gateway/src/test/`

**問題**:
- 沒有速率限制測試（關鍵功能）
- 沒有錯誤處理測試
- 沒有安全測試

**修復建議**:

```java
// 測試速率限制
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRateLimitTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldRateLimitRequests() {
        // 發送多個請求，驗證速率限制
        for (int i = 0; i < 10; i++) {
            webTestClient.post()
                .uri("/api/v1/users/login")
                .bodyValue(LoginRequest)
                .exchange()
                .expectStatus()
                .isIn(HttpStatus.OK, HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
```

---

## 🟢 中優先級問題 (Medium Priority Issues)

### 7. 架構與效能

#### 7.1 快取機制缺失

**問題**:
- 沒有 Redis 快取使用者資料
- 頻繁查詢資料庫

**修復建議**:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```java
// service/impl/UserServiceImpl.java
@Cacheable(value = "users", key = "#userId")
public User getUserById(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
}

@CacheEvict(value = "users", key = "#userId")
public void updateUser(UUID userId, UpdateUserRequest request) {
    // 更新邏輯
}

@CacheEvict(value = "users", key = "#userId")
public void deleteUser(UUID userId) {
    // 刪除邏輯
}
```

```yaml
# application.yml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 600000 # 10 分鐘
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

---

#### 7.2 連線池設定

**問題**: 沒有 HikariCP 連線池設定，使用 Spring Boot 預設值

**修復建議**:

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000      # 5 分鐘
      max-lifetime: 1800000      # 30 分鐘
      connection-timeout: 30000  # 30 秒
      pool-name: AuthnServiceHikariPool
```

---

#### 7.3 非同步處理

**問題**: 關鍵路徑上的同步處理影響效能

**修復建議**:

```java
// config/AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}

// service/impl/UserServiceImpl.java
@Async("taskExecutor")
public void processProfilePictureAsync(UUID userId, MultipartFile file) {
    // 非同步處理圖片
}

@Async("taskExecutor")
public void publishEventAsync(Event event) {
    // 非同步發布 Kafka 事件
}
```

---

### 8. 錯誤處理

#### 8.1 Notification Service 錯誤處理不足

**問題**:
- 沒有全域例外處理器
- 沒有 Kafka consumer 失敗處理
- 沒有 Email 癵送錯誤處理

**修復建議**:

```java
// exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TemplateValidationException.class)
    public ResponseEntity<ErrorResponse> handleTemplateValidation(
        TemplateValidationException ex
    ) {
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .message("Invalid email template")
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<ErrorResponse> handleMailException(MailException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .message("Failed to send email")
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
}

// service/impl/NotificationServiceImpl.java
@KafkaListener(
    topics = "${kafka.topics.user-registration}",
    errorHandler = "kafkaErrorHandler"
)
public void handleUserRegistration(Event event) {
    try {
        notificationStrategy.notify(event);
    } catch (Exception e) {
        // 記錄錯誤並重試
        log.error("Failed to process event: {}", event, e);
        throw e; // 觸發 Kafka 重試機制
    }
}
```

---

#### 8.2 API Gateway 錯誤處理

**修復建議**:

```java
// exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .message("An unexpected error occurred")
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

### 9. 版本不一致

**問題**:
- authn-service: Spring Boot 3.4.11
- api-gateway: Spring Boot 3.4.13
- notification-service: Spring Boot 3.5.7
- SpringDoc 版本不一致（2.8.14 vs 2.6.0）

**風險**: 可能導致相容性問題

**修復建議**: 統一使用 Spring Boot 3.4.13

```xml
<!-- 所有服務的 pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.13</version> <!-- 統一版本 -->
    <relativePath/>
</parent>
```

---

## 🔵 低優先級問題 (Low Priority Issues)

### 10. 文檔改進

#### 10.1 JavaDoc 缺失

**修復建議**: 為所有公開 API 新增 JavaDoc

```java
/**
 * Service interface for user-related operations.
 *
 * <p>This service provides methods for user registration, authentication,
 * profile management, and password operations.</p>
 *
 * @author Peter Chen
 * @version 1.0
 * @since 2024-01-01
 */
public interface UserService {

    /**
     * Registers a new user with email authentication.
     *
     * @param request the user registration request containing email and password
     * @return the registered user entity
     * @throws DuplicateEmailException if an account with the email already exists
     * @throws InvalidEmailException if the email format is invalid
     */
    User registerUser(UserRegistrationRequest request);
}
```

---

#### 10.2 程式碼註解

**修復建議**: 在關鍵邏輯處新增註解說明

```java
// 檢查使用者是否已有本地認證
// 一個使用者只能有一個本地認證（email/password）
if (user.hasLocalAuthentication()) {
    throw new DuplicateEmailException("Local authentication already exists");
}

// 建立新的本地認證
// 密碼會使用 BCrypt 加密後存儲
UserAuthentication auth = UserAuthentication.createLocalAuth(
    request.getEmail(),
    passwordEncoder.encode(request.getPassword())
);
```

---

### 11. 日誌與監控

#### 11.1 日誌標準化

**修復建議**: 使用結構化日誌（JSON 格式）

```xml
<!-- pom.xml -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

```xml
<!-- logback-spring.xml -->
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

---

#### 11.2 監控與指標

**修復建議**: 整合 Micrometer 和 Prometheus

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## 📊 修復優先級順序

### 第一階段（立即修復 - 1-2 週）

- [ ] **修復 CORS 設定** - `SecurityConfig.java`
- [ ] **實作認證端點速率限制** - `UserController.java`
- [ ] **修復 getUsername() 方法** - `AppUser.java`
- [ ] **解決事務管理衝突** - `UserServiceImpl.java`
- [ ] **移除硬編碼憑證** - `application-dev.yml`

**預期工作量**: 8-12 小時

---

### 第二階段（高優先級 - 2-4 週）

- [ ] **提取重複程式碼** - `UserServiceImpl.java`
- [ ] **分解複雜方法** - `uploadProfilePicture()`, `exchangeCodeForAccessToken()`
- [ ] **修復目錄遍歷漏洞** - 檔案上傳功能
- [ ] **新增 JWT nbf 聲明** - `JwtUtils.java`
- [ ] **實作 CSP 標頭** - `SecurityConfig.java`
- [ ] **新增 Notification Service 測試** - 完整測試套件
- [ ] **新增 API Gateway 速率限制測試** - 測試覆蓋

**預期工作量**: 16-24 小時

---

### 第三階段（中優先級 - 1-2 個月）

- [ ] **實作 Redis 快取** - 使用者資料快取
- [ ] **設定連線池** - HikariCP 優化
- [ ] **實作非同步處理** - `@Async` 註解
- [ ] **新增錯誤處理器** - Gateway 和 Notification Service
- [ ] **統一版本** - Spring Boot 3.4.13

**預期工作量**: 12-20 小時

---

### 第四階段（低優先級 - 持續改進）

- [ ] **新增 JavaDoc** - 所有公開 API
- [ ] **標準化日誌** - JSON 格式日誌
- [ ] **整合監控指標** - Prometheus + Grafana
- [ ] **效能測試** - JMeter/Gatling

**預期工作量**: 8-16 小時

**總預期工作量**: 44-72 小時（約 1-2 個月，視資源而定）

---

## 🎯 優勢與良好實踐

### ✅ 做得好的地方

1. **資料庫遷移管理**
   - ✅ 使用 Flyway 進行版本控制
   - ✅ 清晰的遷移歷史（V1-V7）
   - ✅ 正確的 UUID 遷移策略
   - ✅ 多認證支援的 schema 設計

2. **多認證架構**
   - ✅ 優秀的設計模式，支援本地和 OAuth 認證
   - ✅ 使用者可以同時擁有多種認證方式
   - ✅ 清晰的 `AuthenticationType` 枚舉

3. **事件驅動架構**
   - ✅ Kafka 事件設計良好（`Event` record）
   - ✅ 清晰的事件類型分離
   - ✅ JSON 序列化配置正確

4. **分層架構**
   - ✅ 清晰的 Controller → Service → Repository 分層
   - ✅ DTO 模式實作正確
   - ✅ 依賴注入使用得當

5. **安全實踐**
   - ✅ BCrypt 密碼雜湊
   - ✅ JWT token 實作
   - ✅ 適當的存取控制（`@PreAuthorize`）

6. **CVE 修復**
   - ✅ 積極更新依賴修復安全漏洞
   - ✅ 近期已修復多個 CVE（kafka-clients, netty, commons-lang3 等）

7. **測試實作**（authn-service）
   - ✅ 良好的整合測試覆蓋
   - ✅ MockMvc 整合測試
   - ✅ Kafka 測試配置

8. **專案文檔**
   - ✅ `CLAUDE.md` 完整詳細
   - ✅ `README.md` 清晰易懂
   - ✅ 開發流程說明完整

---

## 📁 關鍵檔案清單

### 需要立即修改的檔案

| 檔案路徑 | 修改項目 | 優先級 |
|---------|---------|--------|
| `authn-service/src/main/java/com/peter/authnservice/config/SecurityConfig.java` | 新增 CORS 設定 | 🔴 關鍵 |
| `authn-service/src/main/java/com/peter/authnservice/controller/UserController.java` | 新增速率限制 | 🔴 關鍵 |
| `authn-service/src/main/java/com/peter/authnservice/domain/entity/AppUser.java` | 修復 getUsername() | 🔴 關鍵 |
| `authn-service/src/main/java/com/peter/authnservice/service/impl/UserServiceImpl.java` | 事務管理、重構 | 🔴 關鍵 |
| `authn-service/src/main/resources/application-dev.yml` | 移除硬編碼憑證 | 🔴 關鍵 |
| `authn-service/src/main/java/com/peter/authnservice/util/JwtUtils.java` | 新增 nbf 聲明 | 🟡 高 |
| `authn-service/src/main/java/com/peter/authnservice/config/SecurityConfig.java` | 新增安全標頭 | 🟡 高 |

### 需要新增測試的目錄

| 目錄 | 測試類型 | 優先級 |
|-----|---------|--------|
| `notification-service/src/test/java/com/peter/notificationservice/` | Kafka consumer 測試 | 🟡 高 |
| `notification-service/src/test/java/com/peter/notificationservice/` | Email 發送測試 | 🟡 高 |
| `notification-service/src/test/java/com/peter/notificationservice/` | 模板驗證測試 | 🟡 高 |
| `api-gateway/src/test/java/com/peter/apigateway/` | 速率限制測試 | 🟡 高 |
| `api-gateway/src/test/java/com/peter/apigateway/` | 錯誤處理測試 | 🟡 高 |

### 需要新增錯誤處理的檔案

| 檔案路徑 | 用途 | 優先級 |
|---------|------|--------|
| `notification-service/src/main/java/com/peter/notificationservice/exception/GlobalExceptionHandler.java` | 全域例外處理 | 🟢 中 |
| `api-gateway/src/main/java/com/peter/apigateway/exception/GlobalExceptionHandler.java` | 全域例外處理 | 🟢 中 |

---

## 🧪 驗證計畫

### 安全性測試

```bash
# 測試 CORS 設定
curl -H "Origin: http://malicious-site.com" \
     -H "Access-Control-Request-Method: POST" \
     -X OPTIONS http://localhost:8080/api/v1/users/login

# 預期: Access-Control-Allow-Origin 不應包含 malicious-site.com

# 測試速率限制
for i in {1..100}; do
  curl -X POST http://localhost:8080/api/v1/users/login \
       -H "Content-Type: application/json" \
       -d '{"email":"test@test.com","password":"wrong"}'
done

# 預期: 第 6 次請求後應收到 429 Too Many Requests

# 測試目錄遍歷
curl -F "file=@test.txt;filename=../../etc/passwd" \
     http://localhost:8080/api/v1/users/123/profile-picture

# 預期: 應拒絕包含路徑字符的檔名
```

### 測試覆蓋率

```bash
# authn-service
cd authn-service
./mvnw clean test jacoco:report
# 開啟 target/site/jacoco/index.html 查看覆蓋率報告

# notification-service (待實作)
cd notification-service
./mvnw clean test
```

### 效能測試

使用 JMeter 或 Gatling 進行負載測試：

```bash
# 安裝 Gatling
brew install gatling  # macOS

# 執行負載測試
cd authn-service
./mvnw gatling:test
```

### 整合測試

```bash
# 啟動完整服務堆疊
cd api-gateway && docker compose up -d
cd authn-service && docker compose up -d
cd notification-service && ./mvnw spring-boot:run

# 執行整合測試
cd authn-service
./mvnw verify -P integration-test
```

---

## 📝 總結

這個專案展現了良好的架構設計和 Spring Boot 最佳實踐，特別是在以下方面：

**優勢**:
- ✅ 優秀的資料庫遷移策略（Flyway）
- ✅ 靈活的多認證支援設計
- ✅ 清晰的事件驅動架構
- ✅ 良好的分層架構和 DTO 模式
- ✅ 基本安全措施（BCrypt、JWT）
- ✅ 積極的 CVE 修復
- ✅ 完善的專案文檔

**關鍵改進領域**:
- 🔴 CORS 設定和速率限制（安全性）
- 🔴 事務管理和程式碼重構（程式碼品質）
- 🟡 測試覆蓋率（特別是 notification-service）
- 🟡 效能優化（快取、連線池、非同步處理）
- 🟡 錯誤處理和監控

**建議**:
按照上述優先級順序逐步改進，首先處理關鍵的安全性和程式碼品質問題（第一、二階段），然後再處理效能和監控相關的改進（第三、四階段）。

經過系統性的改進後，這個專案可以達到生產環境的標準，並具備良好的可維護性和可擴展性。

---

**審查者**: Claude Code
**審查日期**: 2026-02-17
**下次審查建議**: 完成第一階段修復後進行追蹤審查
