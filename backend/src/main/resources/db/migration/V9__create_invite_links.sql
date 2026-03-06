CREATE TABLE invite_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id UUID NOT NULL REFERENCES clubs(id),
    code VARCHAR(32) NOT NULL UNIQUE,
    is_single_use BOOLEAN NOT NULL DEFAULT false,
    is_used BOOLEAN NOT NULL DEFAULT false,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    used_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_invite_links_code ON invite_links(code);
CREATE INDEX idx_invite_links_club ON invite_links(club_id);
