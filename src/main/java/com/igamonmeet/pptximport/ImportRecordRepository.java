package com.igamonmeet.pptximport;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRecordRepository extends JpaRepository<ImportRecord, Long> {

    List<ImportRecord> findByMeetingIdOrderByUploadedAtDesc(Long meetingId);
}
