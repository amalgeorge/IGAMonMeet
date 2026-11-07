package com.igamonmeet.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryTokenRepository extends JpaRepository<RecoveryToken, Long> {

    Optional<RecoveryToken> findByTokenHash(String tokenHash);
}
