-- Backend-specific tables required by the Java code.
-- The 53coxroad SQL dump contains the application data tables (singular names such as artwork, auction, exhibition, etc.).
-- These tables are for the backend's own authentication and notification features and are kept separate.

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    full_name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    role TEXT NOT NULL,
    created_date TIMESTAMPTZ,
    updated_date TIMESTAMPTZ,
    reset_token TEXT,
    reset_token_expiry TIMESTAMPTZ,
    email_verified BOOLEAN DEFAULT FALSE,
    verification_token TEXT,
    verification_token_expiry TIMESTAMPTZ
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS reset_token TEXT,
    ADD COLUMN IF NOT EXISTS reset_token_expiry TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS verification_token TEXT,
    ADD COLUMN IF NOT EXISTS verification_token_expiry TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS notifications (
    id TEXT PRIMARY KEY,
    user_email TEXT,
    type TEXT,
    title TEXT,
    message TEXT,
    link TEXT,
    is_read BOOLEAN DEFAULT FALSE,
    created_date TIMESTAMPTZ DEFAULT NOW(),
    updated_date TIMESTAMPTZ DEFAULT NOW(),
    created_by TEXT
);

-- Application table schema migrations (Base44 tables the Java backend touches)
ALTER TABLE auction
    ADD COLUMN IF NOT EXISTS reserve_price numeric,
    ADD COLUMN IF NOT EXISTS image_url text;

ALTER TABLE auction_watchlist
    ADD COLUMN IF NOT EXISTS user_id text,
    ADD COLUMN IF NOT EXISTS auction_id text,
    ADD COLUMN IF NOT EXISTS artwork_id text,
    ADD COLUMN IF NOT EXISTS notify_on_outbid boolean DEFAULT true,
    ADD COLUMN IF NOT EXISTS notify_on_ending_soon boolean DEFAULT true;

-- Older rows were written before these columns existed; the registering user
-- was only recorded in created_by_id, so carry it forward.
UPDATE auction_watchlist SET user_id = created_by_id WHERE user_id IS NULL;

ALTER TABLE auction_winner
    ADD COLUMN IF NOT EXISTS auction_id text,
    ADD COLUMN IF NOT EXISTS artwork_id text,
    ADD COLUMN IF NOT EXISTS winner_id text,
    ADD COLUMN IF NOT EXISTS winning_bid_amount numeric,
    ADD COLUMN IF NOT EXISTS payment_status text DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS payment_intent_id text,
    ADD COLUMN IF NOT EXISTS created_by text,
    ADD COLUMN IF NOT EXISTS shipping_name text,
    ADD COLUMN IF NOT EXISTS shipping_email text,
    ADD COLUMN IF NOT EXISTS shipping_phone text,
    ADD COLUMN IF NOT EXISTS shipping_address text,
    ADD COLUMN IF NOT EXISTS shipping_city text,
    ADD COLUMN IF NOT EXISTS shipping_state text,
    ADD COLUMN IF NOT EXISTS shipping_postcode text,
    ADD COLUMN IF NOT EXISTS shipping_country text,
    ADD COLUMN IF NOT EXISTS shipping_status text,
    ADD COLUMN IF NOT EXISTS tracking_number text;

ALTER TABLE artwork
    ADD COLUMN IF NOT EXISTS status text DEFAULT 'approved',
    ADD COLUMN IF NOT EXISTS rejection_reason text,
    ADD COLUMN IF NOT EXISTS reviewed_date timestamptz;

ALTER TABLE auction_bid
    ADD COLUMN IF NOT EXISTS max_proxy_bid numeric;

-- Xperience API tables
CREATE TABLE IF NOT EXISTS gallery_commission (
    id TEXT PRIMARY KEY,
    commission_id TEXT,
    gallery_commission_id TEXT,
    artist_id TEXT,
    amount numeric,
    status text DEFAULT 'pending',
    progress_stage TEXT,
    progress_percentage INTEGER,
    deposit_status TEXT,
    balance_status TEXT,
    balance_amount numeric,
    approval_status TEXT,
    final_image_url TEXT,
    tracking_number TEXT,
    created_by TEXT,
    created_date TIMESTAMPTZ DEFAULT NOW(),
    updated_date TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS gallery_portrait_delivery (
    id TEXT PRIMARY KEY,
    commission_id TEXT,
    delivery_url TEXT,
    status text DEFAULT 'delivered',
    created_by TEXT,
    created_date TIMESTAMPTZ DEFAULT NOW(),
    updated_date TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS gallery_approval (
    id TEXT PRIMARY KEY,
    commission_id TEXT,
    approved_by TEXT,
    approved BOOLEAN DEFAULT true,
    notes TEXT,
    approved_at TIMESTAMPTZ,
    revision_note TEXT,
    revision_count INTEGER DEFAULT 0,
    max_revisions INTEGER DEFAULT 3,
    created_by TEXT,
    created_date TIMESTAMPTZ DEFAULT NOW(),
    updated_date TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS gallery_refund_request (
    id TEXT PRIMARY KEY,
    commission_id TEXT,
    amount numeric,
    reason TEXT,
    status text DEFAULT 'requested',
    requested_by TEXT,
    note TEXT,
    transfer_status TEXT,
    created_by TEXT,
    created_date TIMESTAMPTZ DEFAULT NOW(),
    updated_date TIMESTAMPTZ DEFAULT NOW()
);

-- Add missing columns to existing tables
ALTER TABLE gallery_commission
    ADD COLUMN IF NOT EXISTS gallery_commission_id TEXT,
    ADD COLUMN IF NOT EXISTS progress_stage TEXT,
    ADD COLUMN IF NOT EXISTS progress_percentage INTEGER,
    ADD COLUMN IF NOT EXISTS deposit_status TEXT,
    ADD COLUMN IF NOT EXISTS balance_status TEXT,
    ADD COLUMN IF NOT EXISTS balance_amount numeric,
    ADD COLUMN IF NOT EXISTS approval_status TEXT,
    ADD COLUMN IF NOT EXISTS final_image_url TEXT,
    ADD COLUMN IF NOT EXISTS tracking_number TEXT;

ALTER TABLE gallery_approval
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revision_note TEXT,
    ADD COLUMN IF NOT EXISTS revision_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_revisions INTEGER DEFAULT 3;

ALTER TABLE gallery_refund_request
    ADD COLUMN IF NOT EXISTS requested_by TEXT,
    ADD COLUMN IF NOT EXISTS note TEXT,
    ADD COLUMN IF NOT EXISTS transfer_status TEXT;
