CREATE TYPE vote_status AS ENUM ('going', 'maybe', 'not_going');

CREATE TYPE final_status AS ENUM ('confirmed', 'waitlisted', 'declined', 'absent', 'attended');

CREATE TABLE event_responses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id),
    user_id UUID NOT NULL REFERENCES users(id),
    stage_1_status vote_status,
    final_status final_status,
    waitlist_position INTEGER,
    attended BOOLEAN,
    responded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_responses_event ON event_responses(event_id);
CREATE INDEX idx_event_responses_user ON event_responses(user_id);
