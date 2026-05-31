-- =================================================================
-- 07-playlist-queue.sql
-- Enclave Droplet Migration: Playlist Queue + Profile Enhancements
-- =================================================================

-- ─────────────────────────────────────────────
-- 1. Shared Lounge Music Queue Table
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lounge_music_queue (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    song_id     UUID REFERENCES public.lounge_songs(id) ON DELETE CASCADE,
    queued_by   UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.lounge_music_queue ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read lounge music queue"
    ON public.lounge_music_queue FOR SELECT
    USING (auth.role() = 'authenticated');

CREATE POLICY "Authenticated users can insert lounge music queue"
    ON public.lounge_music_queue FOR INSERT
    WITH CHECK (auth.uid() = queued_by);

CREATE POLICY "Authenticated users can delete from lounge music queue"
    ON public.lounge_music_queue FOR DELETE
    USING (auth.role() = 'authenticated');

-- ─────────────────────────────────────────────
-- 2. Extend profiles table with Love Language and Location City
-- ─────────────────────────────────────────────
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS love_language TEXT,
    ADD COLUMN IF NOT EXISTS location_city  TEXT;
