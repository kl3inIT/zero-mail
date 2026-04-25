package com.zeromail.core.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByGoogleSubject(String googleSubject);

    /** Canonical tenant-scoped user accessor. Controllers never fall back to findAll().filter(). */
    Optional<UserEntity> findFirstByTenantId(UUID tenantId);
}
