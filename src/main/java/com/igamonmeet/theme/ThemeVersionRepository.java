package com.igamonmeet.theme;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemeVersionRepository extends JpaRepository<ThemeVersion, Long> {
    Optional<ThemeVersion> findFirstByThemeIdOrderByVersionNoDesc(Long themeId);
}
