CREATE TYPE application_status AS ENUM (
    'pending', 'approved', 'rejected', 'auto_rejected'
);

CREATE TABLE applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    club_id UUID NOT NULL REFERENCES clubs(id),
    answer_text TEXT,
    status application_status NOT NULL DEFAULT 'pending',
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_applications_club_status ON applications(club_id, status);
CREATE INDEX idx_applications_user ON applications(user_id);
