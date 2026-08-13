CREATE TABLE source_videos (
    id UUID PRIMARY KEY,
    creator_id UUID NOT NULL,
    title VARCHAR(300) NOT NULL,
    origin_url VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT source_videos_creator_fk
        FOREIGN KEY (creator_id) REFERENCES creators (id) ON DELETE RESTRICT,
    CONSTRAINT source_videos_title_not_blank CHECK (title !~ '^[[:space:]]*$'),
    CONSTRAINT source_videos_origin_url_not_blank
        CHECK (origin_url IS NULL OR origin_url !~ '^[[:space:]]*$')
);

CREATE INDEX source_videos_creator_created_id_idx
    ON source_videos (creator_id, created_at DESC, id DESC);
