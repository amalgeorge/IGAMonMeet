package com.igamonmeet.meeting;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByMeetingIdOrderByCreatedAtAscIdAsc(Long meetingId);
}
