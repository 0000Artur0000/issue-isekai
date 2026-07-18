package ru.arzer0.issueisekai.panel.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<UserAccount> findByRoleCodeAndEnabledTrue(String roleCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM UserAccount user WHERE user.id = :id")
    Optional<UserAccount> findLockedById(@Param("id") UUID id);

    boolean existsByUsername(String username);

    boolean existsByRoleId(UUID roleId);

    Optional<UserAccount> findByUsername(String username);

    @Query(
            "SELECT user.authVersion FROM UserAccount user "
                    + "WHERE user.username = :username AND user.enabled = true")
    Optional<Long> findEnabledAuthVersion(@Param("username") String username);
}
