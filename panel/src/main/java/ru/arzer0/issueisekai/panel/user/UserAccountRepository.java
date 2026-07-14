package ru.arzer0.issueisekai.panel.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    long countByRoleAndEnabledTrue(UserAccount.Role role);

    boolean existsByUsername(String username);

    Optional<UserAccount> findByUsername(String username);
}
