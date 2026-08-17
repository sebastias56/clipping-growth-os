CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    source_video_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT media_assets_source_video_fk
        FOREIGN KEY (source_video_id) REFERENCES source_videos (id) ON DELETE RESTRICT,
    CONSTRAINT media_assets_role_valid CHECK (role = 'ORIGINAL'),
    CONSTRAINT media_assets_storage_key_not_blank
        CHECK (storage_key !~ '^[[:space:]]*$'),
    CONSTRAINT media_assets_original_filename_not_blank
        CHECK (original_filename IS NULL OR original_filename !~ '^[[:space:]]*$'),
    CONSTRAINT media_assets_content_type_not_blank
        CHECK (content_type IS NULL OR content_type !~ '^[[:space:]]*$'),
    CONSTRAINT media_assets_size_bytes_positive CHECK (size_bytes > 0),
    CONSTRAINT media_assets_sha256_lowercase_hex
        CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT media_assets_storage_key_unique UNIQUE (storage_key),
    CONSTRAINT media_assets_source_video_role_unique UNIQUE (source_video_id, role)
);
