CREATE TYPE club_category AS ENUM (
    'sport', 'education', 'hobby', 'business', 'social', 'art', 'tech', 'other'
);

CREATE TYPE club_access_type AS ENUM (
    'open', 'closed', 'private'
);

CREATE TABLE clubs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(60) NOT NULL,
    description TEXT,
    city VARCHAR(255),
    category club_category NOT NULL,
    access_type club_access_type NOT NULL DEFAULT 'open',
    member_limit INTEGER NOT NULL CHECK (member_limit BETWEEN 10 AND 80),
    subscription_price INTEGER NOT NULL DEFAULT 0,
    avatar_url TEXT,
    cover_url TEXT,
    rules TEXT,
    application_question TEXT,
    telegram_group_id BIGINT,
    activity_rating DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    confirmed_count INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_clubs_city ON clubs(city);
CREATE INDEX idx_clubs_category ON clubs(category);
CREATE INDEX idx_clubs_access_type ON clubs(access_type);
CREATE INDEX idx_clubs_owner ON clubs(owner_id);
