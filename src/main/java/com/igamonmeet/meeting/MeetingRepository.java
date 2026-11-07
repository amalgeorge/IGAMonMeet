package com.igamonmeet.meeting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByDeletedAtIsNullOrderByMeetingDateDesc();

    List<Meeting> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

    boolean existsByMonthAndDeletedAtIsNull(String month);

    Optional<Meeting> findFirstByDeletedAtIsNullAndMeetingDateLessThanOrderByMeetingDateDesc(LocalDate date);

    List<Meeting> findByDeletedAtBefore(Instant cutoff);

    /** This Meeting and the ones before it, newest first (KPI trend charts). */
    List<Meeting> findTop12ByDeletedAtIsNullAndMeetingDateLessThanEqualOrderByMeetingDateDesc(LocalDate date);
}
