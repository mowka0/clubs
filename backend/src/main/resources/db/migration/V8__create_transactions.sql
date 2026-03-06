CREATE TYPE transaction_type AS ENUM ('subscription', 'renewal');

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    club_id UUID NOT NULL REFERENCES clubs(id),
    membership_id UUID REFERENCES memberships(id),
    amount_stars INTEGER NOT NULL,
    platform_fee INTEGER NOT NULL,
    organizer_revenue INTEGER NOT NULL,
    telegram_payment_id VARCHAR(255),
    transaction_type transaction_type NOT NULL DEFAULT 'subscription',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_user_club ON transactions(user_id, club_id);
CREATE INDEX idx_transactions_club ON transactions(club_id);
