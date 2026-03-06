CREATE TABLE user_club_reputation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    club_id UUID NOT NULL REFERENCES clubs(id),
    reliability_index INTEGER NOT NULL DEFAULT 0,
    promise_fulfillment_pct DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    spontaneity_count INTEGER NOT NULL DEFAULT 0,
    total_confirmed INTEGER NOT NULL DEFAULT 0,
    total_attended INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (user_id, club_id)
);

CREATE INDEX idx_reputation_club ON user_club_reputation(club_id);
CREATE INDEX idx_reputation_user ON user_club_reputation(user_id);
