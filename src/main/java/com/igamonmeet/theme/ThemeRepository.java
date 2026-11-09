package com.igamonmeet.theme;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemeRepository extends JpaRepository<Theme, Long> {
    List<Theme> findAllByOrderByNameAsc();
    Optional<Theme> findFirstByDefaultThemeTrue();
    boolean existsByNameIgnoreCase(String name);
}
