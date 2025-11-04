package com.peter.authnservice.config;

import com.peter.authnservice.service.impl.CustomUserDetailsService;
import com.peter.authnservice.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * JWT authentication filter for validating bearer tokens on protected endpoints.
 * <p>
 * This filter extends {@link OncePerRequestFilter} to ensure it executes exactly once per request.
 * It intercepts incoming HTTP requests, extracts and validates JWT tokens, and establishes the
 * security context for authenticated users. Public endpoints defined in {@link SecurityConfig#PUBLIC_PATHS}
 * bypass this filter entirely.
 * <p>
 * The filter performs the following workflow:
 * <ol>
 *   <li>Checks if the request path is public (via {@link #shouldNotFilter(HttpServletRequest)})</li>
 *   <li>Extracts JWT token from the Authorization header</li>
 *   <li>Validates token signature and expiration</li>
 *   <li>Loads user details from the database</li>
 *   <li>Sets up Spring Security authentication context</li>
 *   <li>Passes request to the next filter in the chain</li>
 * </ol>
 * <p>
 * Any authentication failure results in an immediate HTTP 401 response with a generic error message.
 * Detailed failure reasons are logged server-side for security monitoring and debugging purposes.
 * This approach prevents information disclosure while maintaining audit capabilities.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_ERROR_MESSAGE = "Unauthorized";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Constructs a new JwtFilter with required dependencies.
     *
     * @param userDetailsService service for loading user details by user ID
     * @param jwtUtils           utility for JWT token validation and parsing
     */
    public JwtFilter(CustomUserDetailsService userDetailsService, JwtUtils jwtUtils) {
        this.userDetailsService = userDetailsService;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Determines whether the JWT authentication filter should be bypassed for the current request.
     * <p>
     * This method is invoked by the Spring framework before {@link #doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)}
     * to decide if JWT validation should be skipped for certain paths. Public endpoints that don't require
     * authentication are excluded from filtering.
     *
     * @param request the HTTP servlet request to evaluate
     * @return {@code true} if the request URI matches a public path and should bypass JWT filtering;
     * {@code false} if JWT authentication is required
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return Arrays.stream(SecurityConfig.PUBLIC_PATHS)
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * Processes each HTTP request to validate JWT authentication for protected endpoints.
     * <p>
     * This method performs the following security checks in order:
     * <ol>
     *   <li>Extracts the JWT token from the Authorization header</li>
     *   <li>Validates the token signature and expiration</li>
     *   <li>Extracts the user ID from the token payload</li>
     *   <li>Loads user details from the database</li>
     *   <li>Sets the authentication context for the current request</li>
     * </ol>
     * <p>
     * If any step fails, the method returns an HTTP 401 Unauthorized response with a generic error message
     * and prevents further filter chain execution. Detailed failure reasons are logged server-side for
     * security monitoring and debugging. This approach follows OWASP security best practices by preventing
     * information disclosure to potential attackers while maintaining comprehensive audit logs.
     *
     * @param request     the HTTP servlet request being filtered
     * @param response    the HTTP servlet response to send back to the client
     * @param filterChain the filter chain to continue processing if authentication succeeds
     * @throws IOException if an I/O error occurs during request processing
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (jwt == null) {
                logAuthenticationFailure(request, "Missing JWT token in Authorization header");
                sendUnauthorizedResponse(response);
                return;
            }

            if (!jwtUtils.validateToken(jwt)) {
                logAuthenticationFailure(request, "Invalid or expired JWT token");
                sendUnauthorizedResponse(response);
                return;
            }

            UUID userId = jwtUtils.getUserIdFromToken(jwt);
            if (userId == null) {
                logAuthenticationFailure(request, "JWT token missing user ID in payload");
                sendUnauthorizedResponse(response);
                return;
            }

            UserDetails userDetails = userDetailsService.loadUserByUserId(userId);
            authenticateUser(request, userId, userDetails);

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            logger.error("Unexpected authentication error for request URI: {} from IP: {}",
                    request.getRequestURI(),
                    getClientIpAddress(request),
                    e);
            sendUnauthorizedResponse(response);
        }
    }

    /**
     * Sends an HTTP 401 Unauthorized response with a generic JSON error message.
     * <p>
     * This helper method standardizes all authentication failure responses to ensure
     * consistent error formatting across different failure scenarios. The response always
     * uses a generic error message to prevent information disclosure, following OWASP
     * security best practices. The response is returned as {@code application/json} with UTF-8 encoding.
     * <p>
     * Detailed error information is logged server-side via {@link #logAuthenticationFailure(HttpServletRequest, String)}
     * for security monitoring and debugging purposes.
     *
     * @param response the HTTP servlet response to write the error to
     * @throws IOException if an I/O error occurs while writing the response
     */
    private void sendUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"message\":\"%s\"}", AUTH_ERROR_MESSAGE));
    }

    /**
     * Logs authentication failure details for security monitoring and debugging.
     * <p>
     * This method records detailed information about authentication failures including
     * the request URI, client IP address, and specific failure reason. Logs are written
     * at DEBUG level to avoid cluttering production logs with routine authentication failures
     * while still maintaining an audit trail for security analysis.
     * <p>
     * This approach allows detailed server-side logging while returning generic error messages
     * to clients, following security best practices to prevent information disclosure.
     *
     * @param request the HTTP servlet request that failed authentication
     * @param reason  the specific reason for authentication failure
     */
    private void logAuthenticationFailure(HttpServletRequest request, String reason) {
        logger.debug("Authentication failed for URI: {} from IP: {} - Reason: {}",
                request.getRequestURI(),
                getClientIpAddress(request),
                reason);
    }

    /**
     * Extracts the client's IP address from the HTTP request.
     * <p>
     * This method checks common proxy headers (X-Forwarded-For, X-Real-IP) before falling back
     * to the direct remote address. This is important for accurate logging when the application
     * is behind a reverse proxy or load balancer.
     *
     * @param request the HTTP servlet request
     * @return the client's IP address, or "unknown" if it cannot be determined
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /**
     * Authenticates the user by setting up the Spring Security authentication context.
     * <p>
     * Creates a {@link UsernamePasswordAuthenticationToken} with the user's ID as the principal,
     * attaches the user's authorities (roles/permissions), and sets authentication details
     * from the current request. The authentication token is then stored in the
     * {@link SecurityContextHolder} for use by downstream security components.
     * <p>
     * Note: The principal is the user's UUID rather than username, as this service uses
     * UUID-based authentication. The credentials parameter is {@code null} since the user
     * is already authenticated via JWT.
     *
     * @param request     the HTTP servlet request to extract authentication details from
     * @param userId      the UUID of the authenticated user (used as the principal)
     * @param userDetails the user's details including authorities and account status
     */
    private void authenticateUser(HttpServletRequest request, UUID userId, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userId, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    /**
     * Extracts the JWT token from the Authorization header of the HTTP request.
     * <p>
     * This method looks for the standard Bearer token authentication scheme in the Authorization header.
     * The expected header format is: {@code Authorization: Bearer <token>}
     * <p>
     * If the header is present and properly formatted, the method strips the "Bearer " prefix
     * (7 characters including the space) and returns the raw JWT token string.
     *
     * @param request the HTTP servlet request containing the Authorization header
     * @return the JWT token string if the Authorization header is present and starts with "Bearer ";
     * {@code null} if the header is missing, malformed, or doesn't use the Bearer scheme
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
