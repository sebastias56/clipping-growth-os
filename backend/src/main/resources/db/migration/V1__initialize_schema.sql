CREATE TABLE schema_verification (
    id SMALLINT PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE schema_verification IS
    'Infrastructure-only marker proving Flyway schema initialization';

INSERT INTO schema_verification (id) VALUES (1);
