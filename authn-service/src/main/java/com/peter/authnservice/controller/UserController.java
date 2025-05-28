package com.peter.authnservice.controller;

import com.peter.authnservice.domain.dto.UserActivationRequest;
import com.peter.authnservice.domain.dto.UserRegistrationRequest;
import com.peter.authnservice.domain.dto.UserRegistrationResponse;
import com.peter.authnservice.domain.entity.AppUser;
import com.peter.authnservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Management", description = "APIs for managing users")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    @Operation(
            summary = "Register new user",
            description = "Register a new user account"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "User registered successfully. Activation required via email",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserRegistrationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "This email has been registered",
                    content = @Content
            )
    })
    @PostMapping
    // @Valid annotation is used to validate the request body
    public ResponseEntity<UserRegistrationResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        AppUser registeredUser = userService.register(request.firstName(), request.lastName(), request.email(), request.password());
        UserRegistrationResponse response = new UserRegistrationResponse(
                registeredUser.getId(),
                registeredUser.getEmail(),
                registeredUser.getCreatedAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Activate user account",
            description = "Activate a user account using the verification token sent via email"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Account activated successfully",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or expired verification token",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Email not found",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email has already been verified",
                    content = @Content
            )
    })
    @PostMapping("/activation")
    public ResponseEntity<Void> activateAccount(@Valid @RequestBody UserActivationRequest request) {
        userService.activateAccount(request.token());
        return ResponseEntity.noContent().build();
    }
}
