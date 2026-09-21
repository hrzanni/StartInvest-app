CREATE TABLE news (
    id  BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(120),
    category VARCHAR(20) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL
);
