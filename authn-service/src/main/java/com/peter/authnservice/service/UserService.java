package com.peter.authnservice.service;

import com.peter.authnservice.domain.dto.UserRegistrationRequest;
import com.peter.authnservice.domain.entity.AppUser;

public interface UserService {
    AppUser register(UserRegistrationRequest request);
    void activateAccount(String token);
}
