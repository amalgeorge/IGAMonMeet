-- Who is editing a Section right now. Expires after 15 minutes without activity.
CREATE TABLE section_lock (
    section_id   BIGINT       NOT NULL PRIMARY KEY,
    username     VARCHAR(100) NOT NULL,
    acquired_at  DATETIME2    NOT NULL,
    last_seen_at DATETIME2    NOT NULL,
    CONSTRAINT fk_section_lock_section FOREIGN KEY (section_id) REFERENCES meeting_section (id) ON DELETE CASCADE
);
