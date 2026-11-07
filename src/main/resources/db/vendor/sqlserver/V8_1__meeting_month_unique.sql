-- One live Meeting per month; soft-deleted Meetings do not count.
CREATE UNIQUE INDEX uq_meeting_month_live ON meeting (meeting_month) WHERE deleted_at IS NULL;
