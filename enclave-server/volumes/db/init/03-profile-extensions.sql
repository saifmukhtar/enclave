-- Migration 03: Extend profiles table with Signal-style identity fields
-- Username, display name, bio, avatar, online status, last seen

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS username       TEXT,
    ADD COLUMN IF NOT EXISTS display_name   TEXT,
    ADD COLUMN IF NOT EXISTS bio            TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS avatar_url     TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS last_seen      TIMESTAMPTZ DEFAULT now(),
    ADD COLUMN IF NOT EXISTS is_online      BOOLEAN DEFAULT false;

-- Unique constraint on username (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_username_unique
    ON public.profiles (LOWER(username))
    WHERE username IS NOT NULL;

-- Index for fast online queries
CREATE INDEX IF NOT EXISTS idx_profiles_is_online ON public.profiles (is_online);
CREATE INDEX IF NOT EXISTS idx_profiles_last_seen  ON public.profiles (last_seen DESC);

-- Status stories table for 24h ephemeral stories
CREATE TABLE IF NOT EXISTS public.status_stories (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_type  TEXT NOT NULL DEFAULT 'TEXT',   -- TEXT, IMAGE, EMOJI
    content       TEXT NOT NULL DEFAULT '',        -- text or base64 thumbnail (not E2EE — metadata only)
    bg_color      TEXT NOT NULL DEFAULT '#FCE2E6', -- hex color for text stories
    expires_at    TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT now(),
    viewed_by     UUID[]      DEFAULT ARRAY[]::UUID[]
);

-- RLS for status_stories
ALTER TABLE public.status_stories ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can create their own stories"
    ON public.status_stories FOR INSERT
    WITH CHECK (auth.uid() = author_id);

CREATE POLICY "Authenticated users can read all stories"
    ON public.status_stories FOR SELECT
    USING (auth.role() = 'authenticated' AND expires_at > now());

CREATE POLICY "Authors can delete their own stories"
    ON public.status_stories FOR DELETE
    USING (auth.uid() = author_id);

CREATE POLICY "Authors can update their own stories"
    ON public.status_stories FOR UPDATE
    USING (auth.uid() = author_id);

-- Auto-cleanup expired stories (optional trigger)
CREATE OR REPLACE FUNCTION delete_expired_stories() RETURNS void AS $$
BEGIN
    DELETE FROM public.status_stories WHERE expires_at < now();
END;
$$ LANGUAGE plpgsql;
