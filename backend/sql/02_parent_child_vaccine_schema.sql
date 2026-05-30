-- PostgreSQL migration for parent, children, vaccines and vaccination schedules.
-- Run after restoring babycare.sql:
--   psql -d babycare -f backend/sql/02_parent_child_vaccine_schema.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Keep the existing username primary key so the current backend login code still works,
-- but add a stable id for new foreign keys.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW();

UPDATE users SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE users ALTER COLUMN id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'users_id_key'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE users ADD CONSTRAINT users_id_key UNIQUE (id);
    END IF;
END $$;

-- New canonical table for babies/children.
-- This replaces the old single-row baby table design.
CREATE TABLE IF NOT EXISTS children (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    dob DATE NOT NULL,
    weight DOUBLE PRECISION CHECK (weight IS NULL OR weight >= 0),
    height DOUBLE PRECISION CHECK (height IS NULL OR height >= 0),
    gender VARCHAR(10) CHECK (gender IN ('Nam', 'Nu', 'Khac')),
    blood_type VARCHAR(10),
    note TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_children_parent_id ON children(parent_id);

-- Optional: migrate the old one-row baby table to tester if it exists.
INSERT INTO children (parent_id, name, dob, weight, height, gender)
SELECT u.id,
       b.name,
       to_date(b.dob, 'DD/MM/YYYY'),
       b.weight,
       b.height,
       CASE
           WHEN b.gender IN ('Nam', 'Nu', 'Khac') THEN b.gender
           WHEN b.gender = 'Nữ' THEN 'Nu'
           ELSE 'Nam'
       END
FROM baby b
JOIN users u ON u.username = 'tester'
WHERE b.name IS NOT NULL
  AND b.name <> ''
  AND b.dob ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$'
  AND NOT EXISTS (
      SELECT 1
      FROM children c
      WHERE c.parent_id = u.id
        AND c.name = b.name
        AND c.dob = to_date(b.dob, 'DD/MM/YYYY')
  );

-- Vaccine catalog. IDs match the Android app's current hard-coded vaccine IDs.
CREATE TABLE IF NOT EXISTS vaccines (
    id INT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS vaccine_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vaccine_id INT NOT NULL REFERENCES vaccines(id) ON DELETE CASCADE,
    recommended_month_age INT NOT NULL CHECK (recommended_month_age >= 0),
    dose_name VARCHAR(100),
    note TEXT,
    UNIQUE (vaccine_id, recommended_month_age, dose_name)
);

INSERT INTO vaccines (id, name, description, is_mandatory) VALUES
    (1, 'Viem gan B (Mui so sinh)', 'Tiem trong vong 24h sau sinh', TRUE),
    (2, 'Lao (BCG)', 'Tiem mot lan cho tre so sinh', TRUE),
    (3, 'Bach hau, Ho ga, Uon van, Bai liet, Hib (Mui 1)', 'Vaccine 5 trong 1 hoac 6 trong 1', TRUE),
    (4, 'Phe cau (Mui 1)', 'Phong viem phoi, viem mang nao do phe cau', TRUE),
    (5, 'Rota virus (Lieu 1)', 'Vaccine uong phong tieu chay', TRUE),
    (6, 'Bach hau, Ho ga, Uon van, Bai liet, Hib (Mui 2)', 'Tiem cach mui 1 it nhat 1 thang', TRUE),
    (7, 'Phe cau (Mui 2)', 'Tiem cach mui 1 it nhat 1 thang', TRUE),
    (8, 'Rota virus (Lieu 2)', 'Uong cach lieu 1 it nhat 1 thang', TRUE),
    (9, 'Bach hau, Ho ga, Uon van, Bai liet, Hib (Mui 3)', 'Tiem cach mui 2 it nhat 1 thang', TRUE),
    (10, 'Phe cau (Mui 3)', 'Tiem cach mui 2 it nhat 1 thang', TRUE),
    (11, 'Soi (Mui 1)', 'Tiem khi tre du 9 thang tuoi', TRUE),
    (12, 'Viem nao Nhat Ban', 'Tiem khi tre du 12 thang tuoi', TRUE)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_mandatory = EXCLUDED.is_mandatory;

INSERT INTO vaccine_schedule (vaccine_id, recommended_month_age, dose_name) VALUES
    (1, 0, 'Mui so sinh'),
    (2, 0, 'Mui so sinh'),
    (3, 2, 'Mui 1'),
    (4, 2, 'Mui 1'),
    (5, 2, 'Lieu 1'),
    (6, 3, 'Mui 2'),
    (7, 3, 'Mui 2'),
    (8, 3, 'Lieu 2'),
    (9, 4, 'Mui 3'),
    (10, 4, 'Mui 3'),
    (11, 9, 'Mui 1'),
    (12, 12, 'Mui 1')
ON CONFLICT (vaccine_id, recommended_month_age, dose_name) DO NOTHING;

-- Actual vaccination records per child.
CREATE TABLE IF NOT EXISTS vaccination_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    vaccine_id INT NOT NULL REFERENCES vaccines(id),
    scheduled_date DATE,
    completed_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'upcoming'
        CHECK (status IN ('upcoming', 'completed', 'overdue', 'skipped')),
    place VARCHAR(255),
    note TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    UNIQUE (child_id, vaccine_id)
);

CREATE INDEX IF NOT EXISTS idx_vaccination_records_child_id ON vaccination_records(child_id);
CREATE INDEX IF NOT EXISTS idx_vaccination_records_status ON vaccination_records(status);

-- Migrate old vaccinations from baby_name to child_id when names match.
INSERT INTO vaccination_records (child_id, vaccine_id, completed_date, status)
SELECT c.id,
       v.vaccine_id,
       CASE
           WHEN v.completed_date ~ '^[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}$'
           THEN to_date(v.completed_date, 'MM/DD/YYYY')
           ELSE CURRENT_DATE
       END,
       'completed'
FROM vaccinations v
JOIN children c ON c.name = v.baby_name
WHERE NOT EXISTS (
    SELECT 1
    FROM vaccination_records vr
    WHERE vr.child_id = c.id
      AND vr.vaccine_id = v.vaccine_id
);

-- Link appointments to parent/child. Columns are nullable first so old data still survives.
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS parent_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS child_id UUID REFERENCES children(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS note TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_appointments_parent_id ON appointments(parent_id);
CREATE INDEX IF NOT EXISTS idx_appointments_child_id ON appointments(child_id);
