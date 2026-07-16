package ru.arzer0.issueisekai.panel.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    Optional<UserRole> findByCode(String code);

    @Query(
            value = "SELECT permission_code FROM role_permissions WHERE role_id = :roleId",
            nativeQuery = true)
    List<String> findPermissionCodes(@Param("roleId") UUID roleId);

    @Query(value = "SELECT code FROM permissions", nativeQuery = true)
    List<String> findAllPermissionCodes();
}
