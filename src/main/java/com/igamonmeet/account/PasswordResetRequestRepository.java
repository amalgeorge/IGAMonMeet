package com.igamonmeet.account;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    List<PasswordResetRequest> findByStatusOrderByRequestedAt(String status);

    List<PasswordResetRequest> findByUserIdAndStatus(Long userId, String status);
}
