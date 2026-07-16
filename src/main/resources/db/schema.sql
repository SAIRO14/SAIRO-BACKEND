CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS photos (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    image_url   TEXT NOT NULL,
    location    TEXT,
    keywords    TEXT,
    embedding   vector(512) NOT NULL
);

CREATE TABLE IF NOT EXISTS spots (
    spot_id         TEXT PRIMARY KEY,
    name            TEXT,
    region_name     TEXT,
    lat             FLOAT,
    lng             FLOAT,
    image_url       TEXT,
    operating_hours TEXT,
    closed_days     TEXT,
    parking         TEXT,
    contact         TEXT,
    cat1            TEXT,
    cat2            TEXT,
    cat3            TEXT
);

CREATE TABLE IF NOT EXISTS shared_courses (
    share_id    TEXT PRIMARY KEY,
    course_data JSONB,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS photos_embedding_idx
    ON photos USING ivfflat (embedding vector_cosine_ops) WITH (lists = 50);

CREATE INDEX IF NOT EXISTS spots_location_idx
    ON spots (lat, lng);
