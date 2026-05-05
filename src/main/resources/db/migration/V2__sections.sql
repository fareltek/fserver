CREATE TABLE IF NOT EXISTS sections (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    host        VARCHAR(45)  NOT NULL,
    port        INTEGER      NOT NULL DEFAULT 4001,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sections_host    ON sections(host);
CREATE INDEX IF NOT EXISTS idx_sections_enabled ON sections(enabled);

COMMENT ON TABLE  sections            IS 'Tramway line sections with CPU signal controllers';
COMMENT ON COLUMN sections.host       IS 'Waveshare RS485/ETH module IP address';
COMMENT ON COLUMN sections.port       IS 'Waveshare TCP Server port (default 4001)';
COMMENT ON COLUMN sections.enabled    IS 'Active connection flag';
