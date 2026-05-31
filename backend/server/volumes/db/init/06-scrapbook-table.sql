-- =================================================================
-- 06-scrapbook-table.sql
-- Enclave Droplet Migration: Collaborative Photo Scrapbook
-- =================================================================

-- ─────────────────────────────────────────────
-- 1. Shared Lounge Scrapbook Table
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lounge_scrapbook (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caption     TEXT NOT NULL,
    photo_url   TEXT NOT NULL,
    event_date  TEXT NOT NULL,
    uploaded_by UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.lounge_scrapbook ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read lounge scrapbook"
    ON public.lounge_scrapbook FOR SELECT
    USING (auth.role() = 'authenticated');

CREATE POLICY "Authenticated users can insert lounge scrapbook"
    ON public.lounge_scrapbook FOR INSERT
    WITH CHECK (auth.uid() = uploaded_by);

CREATE POLICY "Uploader can delete their own scrapbook entries"
    ON public.lounge_scrapbook FOR DELETE
    USING (auth.uid() = uploaded_by);

-- ─────────────────────────────────────────────
-- 2. Scrapbook Storage Bucket (Public — streams via URL)
-- ─────────────────────────────────────────────
INSERT INTO storage.buckets (id, name, public)
VALUES ('scrapbook', 'scrapbook', true)
ON CONFLICT (id) DO NOTHING;

-- Allow anyone authenticated to read scrapbook files
CREATE POLICY "Public scrapbook read"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'scrapbook');

-- Allow authenticated users to upload scrapbook photos
CREATE POLICY "Authenticated scrapbook upload"
    ON storage.objects FOR INSERT
    WITH CHECK (bucket_id = 'scrapbook' AND auth.role() = 'authenticated');

-- Allow uploader to delete their own scrapbook files (first path segment = user id)
CREATE POLICY "Uploader can delete own scrapbook files"
    ON storage.objects FOR DELETE
    USING (bucket_id = 'scrapbook' AND auth.uid()::text = (storage.foldername(name))[1]);
