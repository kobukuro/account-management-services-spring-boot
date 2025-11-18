package com.peter.authnservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthnServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthnServiceApplication.class, args);
    }

}
