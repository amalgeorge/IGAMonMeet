package com.igamonmeet.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndStatus(Role role, UserStatus status);

    List<AppUser> findByStatusOrderByCreatedAt(UserStatus status);

    List<AppUser> findAllByOrderByUsername();
}
