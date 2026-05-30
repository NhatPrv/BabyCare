-- PostgreSQL migration for AI growth assessment, vaccine alerts and chat history.
-- Run:
--   psql -d babycare -f backend/sql/03_ai_growth_and_chat_schema.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS child_measurements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    measured_at DATE NOT NULL DEFAULT CURRENT_DATE,
    weight DOUBLE PRECISION CHECK (weight IS NULL OR weight >= 0),
    height DOUBLE PRECISION CHECK (height IS NULL OR height >= 0),
    head_circumference DOUBLE PRECISION CHECK (head_circumference IS NULL OR head_circumference >= 0),
    note TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_child_measurements_child_id ON child_measurements(child_id);

CREATE TABLE IF NOT EXISTS growth_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    measurement_id UUID REFERENCES child_measurements(id) ON DELETE SET NULL,
    model_version VARCHAR(100) NOT NULL,
    bmi DOUBLE PRECISION,
    weight_for_age_z DOUBLE PRECISION,
    height_for_age_z DOUBLE PRECISION,
    bmi_for_age_z DOUBLE PRECISION,
    classification VARCHAR(50),
    risk_level VARCHAR(30),
    summary TEXT,
    recommendations JSONB,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_growth_assessments_child_id ON growth_assessments(child_id);

CREATE TABLE IF NOT EXISTS vaccine_ai_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    vaccine_id INT REFERENCES vaccines(id),
    due_date DATE,
    alert_type VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vaccine_ai_alerts_child_id ON vaccine_ai_alerts(child_id);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL DEFAULT 'Đoạn chat mới',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_parent_id ON chat_sessions(parent_id);

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    model VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id ON chat_messages(session_id);
