package com.zeromail.core.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Authentication-path lookup. Native SQL bypasses Hibernate's {@code @TenantId} filter
     * because at this point in the request lifecycle the tenant is not yet bound — the
     * caller (TenantBindingFilter) discovers the tenant *from* the resolved user. Using
     * a derived JPA query here would always return empty, since the bootstrap-sentinel
     * tenant filter excludes every real user row. {@code google_subject} is globally unique
     * (Google's user-id is the same string in every Zero Mail tenant), so dropping the
     * tenant filter is safe for this specific lookup.
     */
    @Query(value = "SELECT * FROM users WHERE google_subject = :googleSubject",
           nativeQuery = true)
    Optional<UserEntity> findByGoogleSubject(@Param("googleSubject") String googleSubject);

    /** Canonical tenant-scoped user accessor. Controllers never fall back to findAll().filter(). */
    Optional<UserEntity> findFirstByTenantId(UUID tenantId);
}
