CREATE TYPE event_status AS ENUM (
    'upcoming', 'stage_1', 'stage_2', 'completed', 'cancelled'
);

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL REFERENCES clubs(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    location TEXT,
    event_datetime TIMESTAMP WITH TIME ZONE NOT NULL,
    participant_limit INTEGER NOT NULL,
    confirmed_count INTEGER NOT NULL DEFAULT 0,
    voting_opens_days_before INTEGER NOT NULL DEFAULT 3,
    status event_status NOT NULL DEFAULT 'upcoming',
    stage_2_triggered BOOLEAN NOT NULL DEFAULT false,
    attendance_finalized BOOLEAN NOT NULL DEFAULT false,
    attendance_finalized_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_club_datetime ON events(club_id, event_datetime);
CREATE INDEX idx_events_status ON events(status);
