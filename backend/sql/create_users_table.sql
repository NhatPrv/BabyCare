-- PostgreSQL: users table for register/login logic
-- Run with: psql -d babycare -f backend/sql/create_users_table.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(255) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

-- Dev seed account: tester / 1111
-- Uses bcrypt via pgcrypto crypt()
INSERT INTO users (username, password_hash)
VALUES ('tester', crypt('1111', gen_salt('bf')))
ON CONFLICT (username) DO NOTHING;

