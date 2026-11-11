package com.igamonmeet.settings;

import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

    /** Reads the row with a write lock held until the transaction ends; a second caller waits for the first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppSetting> findOneByKey(String key); // derived query: "key" is a reserved word in JPQL text
}
