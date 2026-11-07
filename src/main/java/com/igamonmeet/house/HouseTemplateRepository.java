package com.igamonmeet.house;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HouseTemplateRepository extends JpaRepository<HouseTemplate, Long> {

    record Summary(int versionNo, String fileName, String uploadedBy, Instant uploadedAt) {
    }

    /** Without the file bytes. */
    @Query("select new com.igamonmeet.house.HouseTemplateRepository$Summary(h.versionNo, h.fileName, h.uploadedBy, h.uploadedAt) "
            + "from HouseTemplate h order by h.versionNo desc")
    List<Summary> summaries();

    @Query("select max(h.versionNo) from HouseTemplate h")
    Integer latestVersion();

    @Query("select h.themeJson from HouseTemplate h where h.versionNo = ?1")
    Optional<String> themeOf(int versionNo);

    Optional<HouseTemplate> findByVersionNo(int versionNo);
}
