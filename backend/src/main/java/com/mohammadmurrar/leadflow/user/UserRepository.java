package com.mohammadmurrar.leadflow.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByWorkspaceId(UUID workspaceId);
    Optional<User> findByNormalizedEmail(String normalizedEmail);

    List<User> findAllByWorkspaceIdAndRoleAndEnabledTrueOrderByNormalizedEmailAsc(
            UUID workspaceId, UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    Optional<User> findByIdForUpdate(UUID id);
}
