-- =================================================================
-- 04-lounge-extensions.sql
-- Enclave Droplet Migration: Shared Lounge Music + Call History
-- =================================================================

-- ─────────────────────────────────────────────
-- 1. Shared Lounge Songs Index Table
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lounge_songs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       TEXT NOT NULL,
    url         TEXT NOT NULL,
    uploaded_by UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.lounge_songs ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read lounge songs"
    ON public.lounge_songs FOR SELECT
    USING (auth.role() = 'authenticated');

CREATE POLICY "Authenticated users can insert lounge songs"
    ON public.lounge_songs FOR INSERT
    WITH CHECK (auth.uid() = uploaded_by);

CREATE POLICY "Uploader can delete their own songs"
    ON public.lounge_songs FOR DELETE
    USING (auth.uid() = uploaded_by);

-- ─────────────────────────────────────────────
-- 2. Music Storage Bucket (Public — streams via URL, no expiry)
-- ─────────────────────────────────────────────
INSERT INTO storage.buckets (id, name, public)
VALUES ('music', 'music', true)
ON CONFLICT (id) DO NOTHING;

-- Allow anyone authenticated to read music files (public bucket, stream freely)
CREATE POLICY "Public music read"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'music');

-- Allow authenticated users to upload music files
CREATE POLICY "Authenticated music upload"
    ON storage.objects FOR INSERT
    WITH CHECK (bucket_id = 'music' AND auth.role() = 'authenticated');

-- Allow uploader to delete their own music files (first path segment = user id)
CREATE POLICY "Uploader can delete own music"
    ON storage.objects FOR DELETE
    USING (bucket_id = 'music' AND auth.uid()::text = (storage.foldername(name))[1]);
