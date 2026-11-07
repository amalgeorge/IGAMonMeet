package com.igamonmeet.meeting;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Meeting dates are Singapore calendar days; this turns them into instants (CONTEXT.md: Edit window). */
@Component
public class MeetingTime {

    public static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    public static final int EDIT_WINDOW_DAYS = 2;

    private final Clock clock;

    public MeetingTime(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(SGT));
    }

    /** End users may edit until 23:59:59 SGT, two days after the Meeting date. */
    public Instant editWindowCloses(LocalDate meetingDate) {
        return meetingDate.plusDays(EDIT_WINDOW_DAYS).atTime(LocalTime.MAX).atZone(SGT).toInstant();
    }

    public boolean editWindowOpen(LocalDate meetingDate) {
        return !now().isAfter(editWindowCloses(meetingDate));
    }

    /** Preparing before the Meeting date, Held from that day on. Information only. */
    public String label(LocalDate meetingDate) {
        return today().isBefore(meetingDate) ? "PREPARING" : "HELD";
    }
}
