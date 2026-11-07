package com.igamonmeet.version;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MeetingVersionRepository extends JpaRepository<MeetingVersion, Long> {

    List<MeetingVersion> findByMeetingIdOrderByVersionNoDesc(Long meetingId);

    Optional<MeetingVersion> findByMeetingIdAndVersionNo(Long meetingId, int versionNo);

    boolean existsByMeetingIdAndReason(Long meetingId, String reason);

    @Query("select coalesce(max(v.versionNo), 0) from MeetingVersion v where v.meetingId = ?1")
    int latest(Long meetingId);
}
