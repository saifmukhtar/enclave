-- =================================================================
-- 05-drawings-gallery.sql
-- Enclave Droplet Migration: Persistent Drawing Board Gallery
-- =================================================================

-- ─────────────────────────────────────────────
-- 1. Shared Lounge Drawings Table
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lounge_drawings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       TEXT NOT NULL,
    url         TEXT NOT NULL,
    uploaded_by UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.lounge_drawings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can read lounge drawings"
    ON public.lounge_drawings FOR SELECT
    USING (auth.role() = 'authenticated');

CREATE POLICY "Authenticated users can insert lounge drawings"
    ON public.lounge_drawings FOR INSERT
    WITH CHECK (auth.uid() = uploaded_by);

CREATE POLICY "Uploader can delete their own drawings"
    ON public.lounge_drawings FOR DELETE
    USING (auth.uid() = uploaded_by);

-- ─────────────────────────────────────────────
-- 2. Drawings Storage Bucket (Public — streams via URL)
-- ─────────────────────────────────────────────
INSERT INTO storage.buckets (id, name, public)
VALUES ('drawings', 'drawings', true)
ON CONFLICT (id) DO NOTHING;

-- Allow anyone authenticated to read drawing files
CREATE POLICY "Public drawings read"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'drawings');

-- Allow authenticated users to upload drawings
CREATE POLICY "Authenticated drawings upload"
    ON storage.objects FOR INSERT
    WITH CHECK (bucket_id = 'drawings' AND auth.role() = 'authenticated');

-- Allow uploader to delete their own drawing files (first path segment = user id)
CREATE POLICY "Uploader can delete own drawings"
    ON storage.objects FOR DELETE
    USING (bucket_id = 'drawings' AND auth.uid()::text = (storage.foldername(name))[1]);
