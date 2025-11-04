package com.peter.authnservice.config;

import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.service.impl.CustomUserDetailsService;
import com.peter.authnservice.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtFilter
 * Tests JWT token validation and security context setup
 */
@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtFilter jwtFilter;

    private UUID testUserId;
    private AppUser testUser;
    private String validToken;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new AppUser(testUserId, "John", "Doe", true);
        validToken = "valid.jwt.token";
        SecurityContextHolder.clearContext();
    }

    // ==================== SHOULD NOT FILTER TESTS ====================

    /**
     * Test that public paths bypass the filter
     */
    @Test
    void whenRequestToPublicPath_thenShouldNotFilter() {
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        boolean shouldNotFilter = jwtFilter.shouldNotFilter(request);
        assertTrue(shouldNotFilter);
    }

    /**
     * Test that login path bypasses the filter
     */
    @Test
    void whenRequestToLoginPath_thenShouldNotFilter() {
        when(request.getRequestURI()).thenReturn("/api/v1/users/login");
        boolean shouldNotFilter = jwtFilter.shouldNotFilter(request);
        assertTrue(shouldNotFilter);
    }

    /**
     * Test that activation path bypasses the filter
     */
    @Test
    void whenRequestToActivationPath_thenShouldNotFilter() {
        when(request.getRequestURI()).thenReturn("/api/v1/users/activation");
        boolean shouldNotFilter = jwtFilter.shouldNotFilter(request);
        assertTrue(shouldNotFilter);
    }

    /**
     * Test that swagger paths bypass the filter
     */
    @Test
    void whenRequestToSwaggerPath_thenShouldNotFilter() {
        when(request.getRequestURI()).thenReturn("/authn/swagger-ui/index.html");
        boolean shouldNotFilter = jwtFilter.shouldNotFilter(request);
        assertTrue(shouldNotFilter);
    }

    /**
     * Test that protected paths do not bypass the filter
     */
    @Test
    void whenRequestToProtectedPath_thenShouldFilter() {
        when(request.getRequestURI()).thenReturn("/api/v1/users/change-password");
        boolean shouldNotFilter = jwtFilter.shouldNotFilter(request);
        assertFalse(shouldNotFilter);
    }

    // ==================== VALID TOKEN TESTS ====================

    /**
     * Test successful authentication with valid JWT token
     */
    @Test
    void whenValidToken_thenAuthenticationSetInContext() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtUtils.validateToken(validToken)).thenReturn(true);
        when(jwtUtils.getUserIdFromToken(validToken)).thenReturn(testUserId);
        when(userDetailsService.loadUserByUserId(testUserId)).thenReturn(testUser);

        jwtFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(testUserId, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtUtils, times(1)).validateToken(validToken);
        verify(jwtUtils, times(1)).getUserIdFromToken(validToken);
        verify(userDetailsService, times(1)).loadUserByUserId(testUserId);
    }

    // ==================== MISSING TOKEN TESTS ====================

    /**
     * Test request with no Authorization header
     */
    @Test
    void whenNoAuthorizationHeader_thenReturns401() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(null);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    /**
     * Test request with empty Authorization header
     */
    @Test
    void whenEmptyAuthorizationHeader_thenReturns401() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("");

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    /**
     * Test request with Authorization header without Bearer prefix
     */
    @Test
    void whenAuthorizationHeaderWithoutBearer_thenReturns401() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn(validToken);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    // ==================== INVALID TOKEN TESTS ====================

    /**
     * Test request with invalid JWT token
     */
    @Test
    void whenInvalidToken_thenReturns401() throws ServletException, IOException {
        String invalidToken = "invalid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);
        when(jwtUtils.validateToken(invalidToken)).thenReturn(false);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }


    // ==================== USER NOT FOUND TESTS ====================

    /**
     * Test request with valid token but user not found
     */
    @Test
    void whenValidTokenButUserNotFound_thenReturns401() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtUtils.validateToken(validToken)).thenReturn(true);
        when(jwtUtils.getUserIdFromToken(validToken)).thenReturn(testUserId);
        when(userDetailsService.loadUserByUserId(testUserId))
                .thenThrow(new RuntimeException("User not found"));

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    // ==================== EDGE CASE TESTS ====================

    /**
     * Test that security context is cleared before each request
     */
    @Test
    void whenMultipleRequests_thenSecurityContextUpdatedCorrectly() throws IOException {
        // First request
        UUID userId1 = UUID.randomUUID();
        String token1 = "token1";
        AppUser user1 = new AppUser(userId1, "User", "One", true);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token1);
        when(jwtUtils.validateToken(token1)).thenReturn(true);
        when(jwtUtils.getUserIdFromToken(token1)).thenReturn(userId1);
        when(userDetailsService.loadUserByUserId(userId1)).thenReturn(user1);

        jwtFilter.doFilterInternal(request, response, filterChain);
        assertEquals(userId1, SecurityContextHolder.getContext().getAuthentication().getPrincipal());

        // Clear context for second request
        SecurityContextHolder.clearContext();

        // Second request
        UUID userId2 = UUID.randomUUID();
        String token2 = "token2";
        AppUser user2 = new AppUser(userId2, "User", "Two", true);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token2);
        when(jwtUtils.validateToken(token2)).thenReturn(true);
        when(jwtUtils.getUserIdFromToken(token2)).thenReturn(userId2);
        when(userDetailsService.loadUserByUserId(userId2)).thenReturn(user2);

        jwtFilter.doFilterInternal(request, response, filterChain);
        assertEquals(userId2, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    /**
     * Test that filter is case-sensitive for Bearer prefix
     */
    @Test
    void whenLowercaseBearerPrefix_thenReturns401() throws ServletException, IOException {
        when(request.getHeader("Authorization")).thenReturn("bearer " + validToken);

        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        jwtFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }
}
