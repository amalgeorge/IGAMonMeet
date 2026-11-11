package com.igamonmeet.settings;

import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

    /** Reads the row with a write lock held until the transaction ends; a second caller waits for the first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppSetting s where s.key = :key")
    Optional<AppSetting> lockByKey(@Param("key") String key);
}
