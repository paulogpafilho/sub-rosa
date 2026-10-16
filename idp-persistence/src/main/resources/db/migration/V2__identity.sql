CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(200) NOT NULL UNIQUE,
    email VARCHAR(320),
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
