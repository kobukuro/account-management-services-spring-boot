package com.peter.authnservice.repository;


import com.peter.authnservice.domain.entity.AppUser;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepository extends CrudRepository<AppUser, UUID> {
}
