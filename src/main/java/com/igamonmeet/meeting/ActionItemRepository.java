package com.igamonmeet.meeting;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ActionItemRepository extends JpaRepository<ActionItem, Long> {

    List<ActionItem> findByMeetingIdOrderBySortOrder(Long meetingId);

    List<ActionItem> findByMeetingIdAndStatusOrderBySortOrder(Long meetingId, String status);

    long countByMeetingIdAndStatus(Long meetingId, String status);

    @Modifying
    @Query("delete from ActionItem a where a.meetingId = ?1")
    void deleteByMeeting(Long meetingId);
}
