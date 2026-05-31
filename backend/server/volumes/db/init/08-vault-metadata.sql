-- =================================================================
-- 08-vault-metadata.sql
-- Enclave Droplet Migration: Collaborative E2EE Vault & Backup Buckets
-- =================================================================

-- ─────────────────────────────────────────────
-- 1. Shared Lounge Vault Metadata Index Table
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.lounge_vault_metadata (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id                TEXT NOT NULL,
    local_encrypted_path    TEXT NOT NULL UNIQUE,
    mime_type               TEXT NOT NULL,
    size_bytes              BIGINT NOT NULL,
    is_ephemeral            BOOLEAN DEFAULT false,
    folder_name             TEXT NOT NULL DEFAULT 'General',
    thumbnail_path          TEXT DEFAULT '',
    uploaded_by             UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at              TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.lounge_vault_metadata ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can select vault metadata"
    ON public.lounge_vault_metadata FOR SELECT
    USING (auth.role() = 'authenticated');

CREATE POLICY "Authenticated users can insert vault metadata"
    ON public.lounge_vault_metadata FOR INSERT
    WITH CHECK (auth.uid() = uploaded_by);

CREATE POLICY "Authenticated users can delete vault metadata"
    ON public.lounge_vault_metadata FOR DELETE
    USING (auth.role() = 'authenticated');

-- ─────────────────────────────────────────────
-- 2. Shared Vault Storage Bucket (Public - Streams encrypted E2EE payload directly)
-- ─────────────────────────────────────────────
INSERT INTO storage.buckets (id, name, public)
VALUES ('vault', 'vault', true)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Public read of encrypted vault files"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'vault');

CREATE POLICY "Authenticated users can upload encrypted vault files"
    ON storage.objects FOR INSERT
    WITH CHECK (bucket_id = 'vault' AND auth.role() = 'authenticated');

CREATE POLICY "Authenticated users can delete vault files"
    ON storage.objects FOR DELETE
    USING (bucket_id = 'vault' AND auth.role() = 'authenticated');

-- ─────────────────────────────────────────────
-- 3. Private Backups Storage Bucket
-- ─────────────────────────────────────────────
INSERT INTO storage.buckets (id, name, public)
VALUES ('backups', 'backups', false)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Authenticated users can read backups"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'backups' AND auth.role() = 'authenticated');

CREATE POLICY "Authenticated users can upload backups"
    ON storage.objects FOR INSERT
    WITH CHECK (bucket_id = 'backups' AND auth.role() = 'authenticated');

CREATE POLICY "Authenticated users can delete own backups"
    ON storage.objects FOR DELETE
    USING (bucket_id = 'backups' AND auth.role() = 'authenticated');
