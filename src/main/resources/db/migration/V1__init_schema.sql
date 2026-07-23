-- 기존 db/schema.sql을 Flyway 기준선으로 옮긴 것이다.
-- 이미 이 테이블들을 가진 DB는 baseline-on-migrate 설정으로 이 스크립트를 건너뛴다.
-- 적용된 마이그레이션은 절대 수정하지 않는다. 변경은 항상 새 V 파일로 추가한다.

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
