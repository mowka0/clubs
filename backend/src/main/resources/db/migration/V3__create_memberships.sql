CREATE TYPE membership_role AS ENUM ('member', 'organizer');

CREATE TYPE membership_status AS ENUM (
    'active', 'cancelled', 'expired', 'grace_period'
);

CREATE TABLE memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    club_id UUID NOT NULL REFERENCES clubs(id),
    role membership_role NOT NULL DEFAULT 'member',
    status membership_status NOT NULL DEFAULT 'active',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    subscription_expires_at TIMESTAMP WITH TIME ZONE,
    locked_subscription_price INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (user_id, club_id)
);

CREATE INDEX idx_memberships_club_status ON memberships(club_id, status);
CREATE INDEX idx_memberships_user ON memberships(user_id);
CREATE INDEX idx_memberships_expires ON memberships(subscription_expires_at);
