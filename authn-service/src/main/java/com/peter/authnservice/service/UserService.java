package com.peter.authnservice.service;

import com.peter.authnservice.domain.entity.AppUser;

public interface UserService {
    AppUser register(String firstName, String lastName, String email, String password);
    void activateAccount(String token);
}
