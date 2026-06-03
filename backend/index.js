const express = require('express');
const { Pool } = require('pg');
const cors = require('cors');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const fetch = require('node-fetch');
const { execFileSync, spawn } = require('child_process');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '.env') });

const app = express();
// Default backend port set to 4000 to avoid colliding with doctor web on 3000
const port = process.env.PORT || 4000;

app.use(cors());
app.use(express.json());

app.use((req, res, next) => {
  console.log(`[${new Date().toLocaleString()}] ${req.method} ${req.url}`);
  next();
});

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

const JWT_SECRET = process.env.JWT_SECRET || 'change_this_secret';
const SERVER_URL = process.env.SERVER_URL || `http://localhost:${port}`;
const DOCTOR_URL = process.env.DOCTOR_URL || SERVER_URL;
const DOCTOR_SECRET = process.env.DOCTOR_SECRET || 'dev_doctor_secret';
const PROJECT_ROOT = path.join(__dirname, '..');
const MODEL_SERVICE_PORT = Number(process.env.MODEL_SERVICE_PORT || 8001);
const MODEL_SERVICE_HEALTH_URL = `http://127.0.0.1:${MODEL_SERVICE_PORT}/health`;
let modelServiceProcess = null;

function runLocalGrowthAssessment(payload) {
  const pythonExe = process.env.PYTHON_BIN || path.join(PROJECT_ROOT, '.venv', 'Scripts', 'python.exe');
  const assessorScript = path.join(PROJECT_ROOT, 'ml', 'src', 'assess_child.py');
  const modelPath = path.join(PROJECT_ROOT, 'ml', 'models', 'grow_model_final.joblib');

  const args = [
    assessorScript,
    '--model', modelPath,
    '--sex', String(payload.sex || 'male'),
    '--weight_kg', String(payload.weight_kg ?? 0),
    '--height_cm', String(payload.height_cm ?? 0),
    '--json'
  ];

  if (payload.age_months != null) {
    args.push('--age_months', String(payload.age_months));
  } else if (payload.age_days != null) {
    args.push('--age_days', String(payload.age_days));
  }

  if (payload.dob) {
    args.push('--dob', String(payload.dob));
  }

  if (payload.measurement_date) {
    args.push('--measurement_date', String(payload.measurement_date));
  }

  try {
    const stdout = execFileSync(pythonExe, args, {
      cwd: PROJECT_ROOT,
      encoding: 'utf8',
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe']
    });
    return JSON.parse(stdout);
  } catch (err) {
    const stderr = err && err.stderr ? String(err.stderr) : '';
    const stdout = err && err.stdout ? String(err.stdout) : '';
    throw new Error((stderr || stdout || err.message || 'Local growth assessment failed').trim());
  }
}

function describeModelServiceError(error) {
  if (!error) return 'unknown error';
  if (typeof error === 'string') return error;
  const parts = [];
  if (error.code) parts.push(String(error.code));
  if (error.errno && String(error.errno) !== String(error.code)) parts.push(String(error.errno));
  if (error.message) parts.push(String(error.message));
  if (!parts.length) parts.push(String(error));
  return parts.join(': ');
}

async function ensureModelServiceRunning() {
  if (String(process.env.START_MODEL_SERVICE || '1') === '0') {
    return;
  }

  try {
    const response = await fetch(MODEL_SERVICE_HEALTH_URL, { method: 'GET' });
    if (response.ok) {
      console.log(`Model service already running at ${MODEL_SERVICE_HEALTH_URL}`);
      return;
    }
  } catch (_) {
    // Will fall through to spawning the local service.
  }

  const pythonExe = process.env.PYTHON_BIN || path.join(PROJECT_ROOT, '.venv', 'Scripts', 'python.exe');
  const serviceDir = path.join(PROJECT_ROOT, 'ml', 'src');

  modelServiceProcess = spawn(
    pythonExe,
    ['-m', 'uvicorn', 'service_fastapi:app', '--host', '127.0.0.1', '--port', String(MODEL_SERVICE_PORT)],
    {
      cwd: serviceDir,
      env: {
        ...process.env,
        PYTHONPATH: serviceDir + path.delimiter + (process.env.PYTHONPATH || '')
      },
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe']
    }
  );

  modelServiceProcess.stdout.on('data', chunk => {
    process.stdout.write(`[model-service] ${chunk}`);
  });
  modelServiceProcess.stderr.on('data', chunk => {
    process.stderr.write(`[model-service] ${chunk}`);
  });
  modelServiceProcess.on('exit', code => {
    console.log(`Model service exited with code ${code}`);
    modelServiceProcess = null;
  });

  console.log(`Starting model service on ${MODEL_SERVICE_HEALTH_URL}...`);
}

// Ensure users table exists and create default tester account
async function ensureAuthTable() {
  await pool.query('CREATE EXTENSION IF NOT EXISTS pgcrypto');

  await pool.query(`CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(255) PRIMARY KEY,
    password_hash VARCHAR(255),
    id UUID DEFAULT gen_random_uuid(),
    full_name VARCHAR(255),
    phone VARCHAR(30),
    email VARCHAR(255),
    address TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
  )`);

  await pool.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid()');
  await pool.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(255)');
  await pool.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30)');
  await pool.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255)');
  await pool.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS address TEXT');
  await pool.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()');
  await pool.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()');
  await pool.query('UPDATE users SET id = gen_random_uuid() WHERE id IS NULL');
  await pool.query('ALTER TABLE users ALTER COLUMN id SET NOT NULL');
  await pool.query(`
    DO $$
    BEGIN
      IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'users_id_key'
          AND conrelid = 'public.users'::regclass
      ) THEN
        ALTER TABLE users ADD CONSTRAINT users_id_key UNIQUE (id);
      END IF;
    END $$
  `);

  const hash = await bcrypt.hash('1111', 10);
  await pool.query(
    `INSERT INTO users (username, password_hash)
     VALUES ($1, $2)
     ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash`,
    ['tester', hash]
  );
  console.log('Ensured default tester account (username: tester, password: 1111)');
}

// Call ensure on startup
async function ensureAllTables() {
  // create auth table + default tester
  await ensureAuthTable();

  // keep old table for compatibility with restored dumps
  await pool.query(`CREATE TABLE IF NOT EXISTS baby (
    name VARCHAR(255),
    dob VARCHAR(20),
    weight DOUBLE PRECISION,
    height DOUBLE PRECISION,
    gender VARCHAR(10)
  )`);

  await pool.query(`CREATE TABLE IF NOT EXISTS children (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    dob DATE NOT NULL,
    weight DOUBLE PRECISION CHECK (weight IS NULL OR weight >= 0),
    height DOUBLE PRECISION CHECK (height IS NULL OR height >= 0),
    gender VARCHAR(10),
    blood_type VARCHAR(10),
    note TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
  )`);
  await pool.query('CREATE INDEX IF NOT EXISTS idx_children_parent_id ON children(parent_id)');

  await pool.query(`CREATE TABLE IF NOT EXISTS vaccines (
    id INT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
  )`);

  await pool.query(`CREATE TABLE IF NOT EXISTS vaccine_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vaccine_id INT NOT NULL REFERENCES vaccines(id) ON DELETE CASCADE,
    recommended_month_age INT NOT NULL CHECK (recommended_month_age >= 0),
    dose_name VARCHAR(100),
    note TEXT,
    UNIQUE (vaccine_id, recommended_month_age, dose_name)
  )`);

  await pool.query(`
    INSERT INTO vaccines (id, name, description, is_mandatory) VALUES
      (1, 'Viêm gan B (Mũi sơ sinh)', 'Tiêm trong vòng 24 giờ đầu sau sinh', TRUE),
      (2, 'Lao (BCG)', 'Phòng bệnh lao, tiêm trong tháng đầu sau sinh', TRUE),
      (3, 'Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib, VGB (6 trong 1 - Mũi 1)', 'Phòng 6 bệnh truyền nhiễm nguy hiểm, tiêm khi trẻ 2 tháng tuổi', TRUE),
      (4, 'Phế cầu (Mũi 1)', 'Phòng viêm phổi, viêm màng não, viêm tai giữa do phế cầu khuẩn', TRUE),
      (5, 'Rota virus (Liều 1)', 'Vaccine uống phòng bệnh tiêu chảy cấp do Rota virus', TRUE),
      (6, 'Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib, VGB (6 trong 1 - Mũi 2)', 'Tiêm cách mũi 1 ít nhất 1 tháng', TRUE),
      (7, 'Phế cầu (Mũi 2)', 'Tiêm cách mũi 1 ít nhất 1 tháng', TRUE),
      (8, 'Rota virus (Liều 2)', 'Uống cách liều 1 ít nhất 1 tháng', TRUE),
      (9, 'Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib, VGB (6 trong 1 - Mũi 3)', 'Tiêm cách mũi 2 ít nhất 1 tháng', TRUE),
      (10, 'Phế cầu (Mũi 3)', 'Tiêm cách mũi 2 ít nhất 1 tháng', TRUE),
      (11, 'Bại liệt uống/tiêm (IPV - Mũi nhắc)', 'Tăng cường miễn dịch bại liệt', TRUE),
      (12, 'Cúm (Mũi 1)', 'Phòng cúm mùa, tiêm khi trẻ từ 6 tháng tuổi', TRUE),
      (13, 'Cúm (Mũi 2)', 'Tiêm cách mũi 1 ít nhất 1 tháng', TRUE),
      (14, 'Não mô cầu B+C (Mũi 1)', 'Phòng viêm màng não do não mô cầu khuẩn nhóm B, C', TRUE),
      (15, 'Não mô cầu B+C (Mũi 2)', 'Tiêm cách mũi 1 khoảng 6-8 tuần', TRUE),
      (16, 'Sởi đơn (Mũi 1)', 'Phòng bệnh sởi, tiêm khi trẻ đủ 9 tháng tuổi', TRUE),
      (17, 'Sởi - Quai bị - Rubella (MMR - Mũi 1)', 'Phòng 3 bệnh sởi, quai bị, rubella', TRUE),
      (18, 'Thủy đậu (Mũi 1)', 'Phòng bệnh thủy đậu (trái rạ)', TRUE),
      (19, 'Viêm não Nhật Bản (Mũi 1)', 'Phòng bệnh viêm não Nhật Bản', TRUE),
      (20, 'Viêm gan A (Mũi 1)', 'Phòng bệnh viêm gan A', TRUE),
      (21, 'Bạch hầu, Ho gà, Uốn ván, Bại liệt, Hib (Mũi 4 - Nhắc lại)', 'Tiêm nhắc lại lúc 18 tháng tuổi', TRUE),
      (22, 'Sởi - Quai bị - Rubella (MMR - Mũi 2)', 'Tiêm nhắc lại hoặc tiêm mũi 2', TRUE),
      (23, 'Viêm gan A (Mũi 2)', 'Tiêm cách mũi 1 từ 6 - 12 tháng', TRUE)
    ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description
  `);

  await pool.query(`
    INSERT INTO vaccine_schedule (vaccine_id, recommended_month_age, dose_name) VALUES
      (1, 0, 'Mũi sơ sinh'), (2, 0, 'Mũi sơ sinh'),
      (3, 2, 'Mũi 1'), (4, 2, 'Mũi 1'), (5, 2, 'Liều 1'),
      (6, 3, 'Mũi 2'), (7, 3, 'Mũi 2'), (8, 3, 'Liều 2'),
      (9, 4, 'Mũi 3'), (10, 4, 'Mũi 3'),
      (11, 5, 'Mũi nhắc'),
      (12, 6, 'Mũi 1'), (13, 7, 'Mũi 2'), (14, 6, 'Mũi 1'), (15, 8, 'Mũi 2'),
      (16, 9, 'Mũi 1'),
      (17, 12, 'Mũi 1'), (18, 12, 'Mũi 1'), (19, 12, 'Mũi 1'), (20, 12, 'Mũi 1'),
      (21, 18, 'Mũi 4'), (22, 18, 'Mũi 2'), (23, 18, 'Mũi 2')
    ON CONFLICT (vaccine_id, recommended_month_age, dose_name) DO NOTHING
  `);

  await pool.query(`CREATE TABLE IF NOT EXISTS appointments (
    id VARCHAR(255) PRIMARY KEY,
    service_type VARCHAR(255),
    hospital_name VARCHAR(255),
    date VARCHAR(20),
    time VARCHAR(20),
    status VARCHAR(50),
    parent_id UUID REFERENCES users(id) ON DELETE SET NULL,
    child_id UUID REFERENCES children(id) ON DELETE SET NULL,
    note TEXT,
    rejection_reason TEXT,
    parent_name TEXT,
    parent_phone TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
  )`);
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS parent_id UUID REFERENCES users(id) ON DELETE SET NULL');
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS child_id UUID REFERENCES children(id) ON DELETE SET NULL');
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS note TEXT');
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS rejection_reason TEXT');
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS parent_name TEXT');
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS parent_phone TEXT');
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()');
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()');
  await pool.query('CREATE INDEX IF NOT EXISTS idx_appointments_parent_id ON appointments(parent_id)');
  await pool.query('CREATE INDEX IF NOT EXISTS idx_appointments_child_id ON appointments(child_id)');

  // keep old table for compatibility with restored dumps
  await pool.query(`CREATE TABLE IF NOT EXISTS vaccinations (
    baby_name VARCHAR(255),
    vaccine_id INT,
    completed_date VARCHAR(20),
    PRIMARY KEY (baby_name, vaccine_id)
  )`);

  await pool.query(`CREATE TABLE IF NOT EXISTS vaccination_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    vaccine_id INT NOT NULL REFERENCES vaccines(id),
    scheduled_date DATE,
    completed_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'upcoming',
    place VARCHAR(255),
    note TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    UNIQUE (child_id, vaccine_id)
  )`);
  await pool.query('CREATE INDEX IF NOT EXISTS idx_vaccination_records_child_id ON vaccination_records(child_id)');

  await pool.query(`CREATE TABLE IF NOT EXISTS child_measurements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    measured_at DATE NOT NULL DEFAULT CURRENT_DATE,
    weight DOUBLE PRECISION CHECK (weight IS NULL OR weight >= 0),
    height DOUBLE PRECISION CHECK (height IS NULL OR height >= 0),
    head_circumference DOUBLE PRECISION CHECK (head_circumference IS NULL OR head_circumference >= 0),
    note TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
  )`);
  await pool.query('CREATE INDEX IF NOT EXISTS idx_child_measurements_child_id ON child_measurements(child_id)');

  await pool.query(`CREATE TABLE IF NOT EXISTS growth_assessments (
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
  )`);
  await pool.query('ALTER TABLE growth_assessments ADD COLUMN IF NOT EXISTS measurement_id UUID');
  await pool.query('ALTER TABLE growth_assessments ADD COLUMN IF NOT EXISTS weight_for_age_z DOUBLE PRECISION');
  await pool.query('ALTER TABLE growth_assessments ADD COLUMN IF NOT EXISTS height_for_age_z DOUBLE PRECISION');
  await pool.query('ALTER TABLE growth_assessments ADD COLUMN IF NOT EXISTS bmi_for_age_z DOUBLE PRECISION');
  await pool.query('ALTER TABLE growth_assessments ADD COLUMN IF NOT EXISTS risk_level VARCHAR(30)');
  await pool.query('ALTER TABLE growth_assessments ADD COLUMN IF NOT EXISTS summary TEXT');
  await pool.query('ALTER TABLE growth_assessments ADD COLUMN IF NOT EXISTS recommendations JSONB');
  await pool.query('ALTER TABLE growth_assessments ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()');
  await pool.query('CREATE INDEX IF NOT EXISTS idx_growth_assessments_child_id ON growth_assessments(child_id)');

  await pool.query(`CREATE TABLE IF NOT EXISTS vaccine_ai_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_id UUID NOT NULL REFERENCES children(id) ON DELETE CASCADE,
    vaccine_id INT REFERENCES vaccines(id),
    due_date DATE,
    alert_type VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
  )`);
  await pool.query('CREATE INDEX IF NOT EXISTS idx_vaccine_ai_alerts_child_id ON vaccine_ai_alerts(child_id)');

  await pool.query(`CREATE TABLE IF NOT EXISTS chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL DEFAULT 'Đoạn chat mới',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
  )`);
  await pool.query('CREATE INDEX IF NOT EXISTS idx_chat_sessions_parent_id ON chat_sessions(parent_id)');

  await pool.query(`CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    model VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
  )`);
  await pool.query('CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id ON chat_messages(session_id)');
}

ensureAllTables()
  .then(() => ensureModelServiceRunning())
  .catch(err => console.error('Failed ensuring tables:', err));

function formatDateForApp(value) {
  if (!value) return '';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${day}/${month}/${date.getFullYear()}`;
}

function formatDateKey(value) {
  if (!value) return '';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function toBabyResponse(row) {
  if (!row) return { name: "", dob: "", weight: 0, height: 0, gender: "Nam" };
  const genderForApp = row.gender === 'Nu' ? 'Nữ' : (row.gender === 'Khac' ? 'Khác' : row.gender || 'Nam');
  return {
    id: row.id,
    name: row.name || "",
    dob: formatDateForApp(row.dob),
    weight: Number(row.weight || 0),
    height: Number(row.height || 0),
    gender: genderForApp
  };
}

function normalizeGender(gender) {
  if (gender === 'Nu' || gender === 'Nữ') return 'Nu';
  if (gender === 'Khac' || gender === 'Khác') return 'Khac';
  return 'Nam';
}

async function getCurrentParent(username) {
  const result = await pool.query('SELECT id, username FROM users WHERE username = $1', [username]);
  return result.rows[0] || null;
}

async function getFirstChild(parentId) {
  const result = await pool.query(
    'SELECT * FROM children WHERE parent_id = $1 ORDER BY created_at ASC LIMIT 1',
    [parentId]
  );
  return result.rows[0] || null;
}

function toChildResponse(row) {
  if (!row) return null;
  return {
    id: row.id,
    name: row.name || '',
    dob: formatDateForApp(row.dob),
    weight: Number(row.weight || 0),
    height: Number(row.height || 0),
    gender: row.gender === 'Nu' ? 'Nữ' : (row.gender === 'Khac' ? 'Khác' : row.gender || 'Nam'),
    bloodType: row.blood_type || null,
    note: row.note || null
  };
}

function toAssessmentResponse(row) {
  if (!row) return null;
  let recommendations = row.recommendations ?? null;
  if (typeof recommendations === 'string') {
    try {
      recommendations = JSON.parse(recommendations);
    } catch (_) {
      recommendations = { raw: recommendations };
    }
  }
  return {
    id: row.id,
    childId: row.child_id || null,
    measurementId: row.measurement_id || null,
    modelVersion: row.model_version || null,
    bmi: row.bmi != null ? Number(row.bmi) : null,
    weightForAgeZ: row.weight_for_age_z != null ? Number(row.weight_for_age_z) : null,
    heightForAgeZ: row.height_for_age_z != null ? Number(row.height_for_age_z) : null,
    bmiForAgeZ: row.bmi_for_age_z != null ? Number(row.bmi_for_age_z) : null,
    classification: row.classification || null,
    riskLevel: row.risk_level || null,
    summary: row.summary || null,
    recommendations,
    recommendation: recommendations && typeof recommendations === 'object'
      ? (recommendations.recommendation || null)
      : null,
    createdAt: formatDateForApp(row.created_at)
  };
}

function toMeasurementHistoryResponse(row) {
  if (!row) return null;
  const assessment = row.assessment_id ? toAssessmentResponse({
    id: row.assessment_id,
    child_id: row.child_id,
    measurement_id: row.id,
    model_version: row.assessment_model_version,
    bmi: row.assessment_bmi,
    weight_for_age_z: row.assessment_weight_for_age_z,
    height_for_age_z: row.assessment_height_for_age_z,
    bmi_for_age_z: row.assessment_bmi_for_age_z,
    classification: row.assessment_classification,
    risk_level: row.assessment_risk_level,
    summary: row.assessment_summary,
    recommendations: row.assessment_recommendations,
    created_at: row.assessment_created_at
  }) : null;

  return {
    id: row.id,
    childId: row.child_id,
    measuredAt: formatDateForApp(row.measured_at),
    measuredAtKey: formatDateKey(row.measured_at),
    weight: row.weight != null ? Number(row.weight) : null,
    height: row.height != null ? Number(row.height) : null,
    headCircumference: row.head_circumference != null ? Number(row.head_circumference) : null,
    note: row.note || null,
    createdAt: formatDateForApp(row.created_at),
    assessment
  };
}

async function getChildrenByParent(parentId) {
  const result = await pool.query(
    'SELECT * FROM children WHERE parent_id = $1 ORDER BY created_at ASC',
    [parentId]
  );
  return result.rows;
}

async function getChildProfileByParent(parentId, childId) {
  const childResult = await pool.query(
    'SELECT * FROM children WHERE id = $1 AND parent_id = $2 LIMIT 1',
    [childId, parentId]
  );
  const child = childResult.rows[0] || null;
  if (!child) return null;

  const latestAssessmentResult = await pool.query(
    `SELECT *
     FROM growth_assessments
     WHERE child_id = $1
     ORDER BY created_at DESC
     LIMIT 1`,
    [childId]
  );

  const measurementResult = await pool.query(
    `SELECT
        cm.id,
        cm.child_id,
        cm.measured_at,
        cm.weight,
        cm.height,
        cm.head_circumference,
        cm.note,
        cm.created_at,
        ga.id AS assessment_id,
        ga.model_version AS assessment_model_version,
        ga.bmi AS assessment_bmi,
        ga.weight_for_age_z AS assessment_weight_for_age_z,
        ga.height_for_age_z AS assessment_height_for_age_z,
        ga.bmi_for_age_z AS assessment_bmi_for_age_z,
        ga.classification AS assessment_classification,
        ga.risk_level AS assessment_risk_level,
        ga.summary AS assessment_summary,
        ga.recommendations AS assessment_recommendations,
        ga.created_at AS assessment_created_at
     FROM child_measurements cm
     LEFT JOIN growth_assessments ga ON ga.measurement_id = cm.id
     WHERE cm.child_id = $1
     ORDER BY cm.measured_at ASC, cm.created_at ASC`,
    [childId]
  );

  // If there is no stored assessment, attempt to compute a temporary assessment
  // based on child's age/sex by querying the model service (or running local assessor).
  let latestAssessment = toAssessmentResponse(latestAssessmentResult.rows[0] || null);
  if (!latestAssessment) {
    try {
      // compute age in months (approx)
      const dob = child.dob instanceof Date ? child.dob : new Date(child.dob);
      const now = new Date();
      let ageMonths = null;
      if (!Number.isNaN(dob.getTime())) {
        ageMonths = (now.getFullYear() - dob.getFullYear()) * 12 + (now.getMonth() - dob.getMonth());
        // adjust if day-of-month not reached
        if (now.getDate() < dob.getDate()) ageMonths -= 1;
        if (ageMonths < 0) ageMonths = 0;
      }

      const payload = {
        childId,
        sex: (child.gender === 'Nu') ? 'female' : 'male',
        age_months: ageMonths,
        dob: formatDateForApp(child.dob),
        measurement_date: formatDateKey(new Date())
      };

      const modelService = process.env.MODEL_SERVICE_URL || `http://localhost:${MODEL_SERVICE_PORT}/predict`;
      let result = null;
      try {
        const resp = await fetch(modelService, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        if (resp.ok) {
          result = await resp.json().catch(() => null);
        } else {
          // fallback to local assessor
          result = runLocalGrowthAssessment(payload);
        }
      } catch (e) {
        result = runLocalGrowthAssessment(payload);
      }

      if (result) {
        const modelVersion = process.env.MODEL_VERSION || 'lgbm_optuna';
        latestAssessment = {
          id: null,
          childId: childId,
          measurementId: null,
          modelVersion: modelVersion,
          bmi: result.bmi ?? null,
          weightForAgeZ: result.weight_for_age_z ?? null,
          heightForAgeZ: result.height_for_age_z ?? null,
          bmiForAgeZ: result.bmi_for_age_z ?? null,
          classification: result.prediction || result.predicted || null,
          riskLevel: result.risk_level || (result.prediction || result.predicted) || null,
          summary: null,
          recommendations: result.recommendations || null,
          recommendation: result.recommendation || null,
          createdAt: formatDateForApp(new Date())
        };
      }
    } catch (err) {
      // ignore model errors here; return profile without assessment
      console.error('Failed to compute ephemeral assessment for profile:', err && err.message ? err.message : err);
    }
  }

  return {
    child: toChildResponse(child),
    latestAssessment: latestAssessment,
    measurementHistory: measurementResult.rows.map(toMeasurementHistoryResponse)
  };
}

async function assessAndPersistForChild(child, sourcePayload = {}) {
  if (!child || !child.id) return null;
  try {
    const childId = child.id;
    // Determine sex for model
    const sex = (child.gender === 'Nu') ? 'female' : (child.gender === 'Khac' ? 'other' : 'male');

    // compute age in months
    const dob = child.dob instanceof Date ? child.dob : new Date(child.dob);
    const now = new Date();
    let ageMonths = null;
    if (!Number.isNaN(dob.getTime())) {
      ageMonths = (now.getFullYear() - dob.getFullYear()) * 12 + (now.getMonth() - dob.getMonth());
      if (now.getDate() < dob.getDate()) ageMonths -= 1;
      if (ageMonths < 0) ageMonths = 0;
    }

    const payload = {
      childId,
      sex,
      age_months: ageMonths,
      dob: formatDateForApp(child.dob),
      measurement_date: formatDateKey(new Date()),
      weight_kg: sourcePayload.weight_kg ?? sourcePayload.weight ?? child.weight ?? null,
      height_cm: sourcePayload.height_cm ?? sourcePayload.height ?? child.height ?? null,
      head_circumference_cm: sourcePayload.head_circumference_cm ?? null
    };

    // Only run assessment if we have at least one of weight/height
    if (payload.weight_kg == null && payload.height_cm == null) return null;

    const modelService = process.env.MODEL_SERVICE_URL || `http://localhost:${MODEL_SERVICE_PORT}/predict`;
    let result = null;
    try {
      const resp = await fetch(modelService, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (resp.ok) {
        result = await resp.json().catch(() => null);
      } else {
        result = runLocalGrowthAssessment(payload);
      }
    } catch (e) {
      result = runLocalGrowthAssessment(payload);
    }

    if (!result) return null;

    const modelVersion = process.env.MODEL_VERSION || 'lgbm_optuna';

    const measurementResult = await pool.query(
      `INSERT INTO child_measurements (child_id, measured_at, weight, height, head_circumference, note)
       VALUES ($1, CURRENT_DATE, $2, $3, $4, $5)
       RETURNING *`,
      [
        childId,
        payload.weight_kg ?? null,
        payload.height_cm ?? null,
        payload.head_circumference_cm ?? null,
        JSON.stringify({ source: 'profile_autosave', request: payload })
      ]
    );
    const measurement = measurementResult.rows[0];

    const insertResult = await pool.query(
      `INSERT INTO growth_assessments (
         child_id, measurement_id, model_version, bmi,
         weight_for_age_z, height_for_age_z, bmi_for_age_z,
         classification, risk_level, summary, recommendations
       ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING *`,
      [
        childId,
        measurement.id,
        modelVersion,
        result.bmi ?? null,
        result.weight_for_age_z ?? null,
        result.height_for_age_z ?? null,
        result.bmi_for_age_z ?? null,
        result.prediction || result.predicted || null,
        result.risk_level || result.prediction || result.predicted || null,
        JSON.stringify({ request: payload, result }),
        JSON.stringify({ recommendation: result.recommendation || null, probabilities: result.probabilities || {} })
      ]
    );

    return { measurement: measurementResult.rows[0], assessment: insertResult.rows[0], model_result: result };
  } catch (err) {
    console.error('Error in assessAndPersistForChild:', err && err.message ? err.message : err);
    return null;
  }
}

function normalizeChildGender(gender) {
  const value = String(gender || '').trim().toLowerCase();
  if (['nu', 'nữ', 'female', 'girl', 'f'].includes(value)) return 'Nu';
  if (['khac', 'khác'].includes(value)) return 'Khac';
  return 'Nam';
}

function extractOpenAiText(data) {
  if (data && typeof data.output_text === 'string') return data.output_text;
  const output = Array.isArray(data?.output) ? data.output : [];
  const texts = [];
  for (const item of output) {
    const content = Array.isArray(item.content) ? item.content : [];
    for (const part of content) {
      if (typeof part.text === 'string') texts.push(part.text);
    }
  }
  return texts.join('\n').trim();
}

function extractGeminiText(data) {
  const parts = data?.candidates?.[0]?.content?.parts;
  if (!Array.isArray(parts)) return '';
  return parts
    .map(part => part.text)
    .filter(text => typeof text === 'string' && text.trim())
    .join('\n')
    .trim();
}

async function generateBabyCareReply(parent, userMessage, historyRows) {
  const apiKey = process.env.GEMINI_API_KEY;
  const model = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
  if (!apiKey) {
    return {
      model: 'local-fallback',
      text: 'Mình đã lưu câu hỏi của bạn. Để AI trả lời thật, hãy thêm GEMINI_API_KEY vào backend/.env rồi khởi động lại server.'
    };
  }

  // Lấy danh sách tất cả các bé của phụ huynh hiện tại
  const childrenResult = await pool.query(
    'SELECT * FROM children WHERE parent_id = $1 ORDER BY created_at ASC',
    [parent.id]
  );
  const children = childrenResult.rows;

  let childrenContext = '';
  if (children.length > 0) {
    childrenContext = 'Dưới đây là thông tin chi tiết về các bé của phụ huynh này:\n';
    for (let i = 0; i < children.length; i++) {
      const child = children[i];
      const dob = child.dob instanceof Date ? child.dob : new Date(child.dob);
      const now = new Date();
      let ageMonths = 0;
      if (!isNaN(dob.getTime())) {
        ageMonths = (now.getFullYear() - dob.getFullYear()) * 12 + (now.getMonth() - dob.getMonth());
        if (now.getDate() < dob.getDate()) ageMonths -= 1;
        if (ageMonths < 0) ageMonths = 0;
      }
      
      const latestAssessmentResult = await pool.query(
        `SELECT classification FROM growth_assessments WHERE child_id = $1 ORDER BY created_at DESC LIMIT 1`,
        [child.id]
      );
      const assessment = latestAssessmentResult.rows[0];
      const classification = assessment ? assessment.classification : 'Chưa được đo và đánh giá thể trạng';

      const vacResult = await pool.query(
        `SELECT v.name FROM vaccination_records vr 
         JOIN vaccines v ON vr.vaccine_id = v.id 
         WHERE vr.child_id = $1 AND vr.status = 'completed'`,
        [child.id]
      );
      const completedVaccines = vacResult.rows.map(r => r.name).join(', ') || 'Chưa tiêm mũi nào (hoặc chưa được đánh dấu đã tiêm)';

      const genderName = child.gender === 'Nu' ? 'Nữ' : (child.gender === 'Khac' ? 'Khác' : 'Nam');
      childrenContext += `- Bé thứ ${i + 1}: Tên "${child.name}", Giới tính: ${genderName}, Ngày sinh: ${formatDateForApp(child.dob)} (${ageMonths} tháng tuổi), Cân nặng: ${child.weight || 'chưa rõ'}kg, Chiều cao: ${child.height || 'chưa rõ'}cm. Thể trạng hiện tại: ${classification}. Các mũi vắc xin đã tiêm thành công: ${completedVaccines}.\n`;
    }
  } else {
    childrenContext = 'Phụ huynh hiện tại chưa khai báo thông tin của bất kỳ bé nào trong hồ sơ.';
  }

  const previousMessages = historyRows.slice(-12).map(row => ({
    role: row.role === 'assistant' ? 'model' : 'user',
    parts: [{ text: row.content }]
  }));

  const systemInstruction = [
    'Bạn là trợ lý ảo chăm sóc trẻ em thông minh (BabyCare Agent) chuyên nghiệp cho phụ huynh Việt Nam.',
    'Nhiệm vụ của bạn là tư vấn các vấn đề chăm sóc trẻ, dinh dưỡng, tiêm chủng, và thể trạng của các bé dựa vào thông tin thực tế được cung cấp.',
    'Dưới đây là thông tin chi tiết về các bé của phụ huynh hiện tại để bạn làm căn cứ tư vấn:',
    childrenContext,
    'Quy tắc phản hồi bắt buộc dành cho bạn (Hãy luôn tuân thủ nghiêm ngặt):',
    '1. Kiểm tra xem phụ huynh đang hỏi về bé nào:',
    '   - Nếu phụ huynh hỏi chung chung (ví dụ: "bé nhà tôi tiêm phòng gì?", "nên cho ăn dặm như thế nào?"): Hãy liệt kê tên các bé của phụ huynh này, và hỏi rõ xem họ muốn tư vấn cho bé nào cụ thể.',
    '   - Nếu phụ huynh chỉ đích danh tên bé (hoặc nếu phụ huynh chỉ có duy nhất 1 bé trong danh sách): Hãy tập trung trả lời chi tiết và cá nhân hóa cho bé đó.',
    '2. Khi tư vấn cụ thể cho một bé, hãy kết hợp thông tin:',
    '   - Số tháng tuổi (tính từ ngày sinh của bé) để đưa ra lịch tiêm chủng phù hợp (ví dụ: nhắc các mũi cần tiêm ở tuổi này) hoặc chế độ dinh dưỡng tương ứng.',
    '   - Thể trạng hiện tại (ví dụ: nếu bé bị "Suy dinh dưỡng" hoặc "Thừa cân", hãy đưa ra lời khuyên dinh dưỡng an toàn và phù hợp riêng biệt cho thể trạng đó).',
    '   - Các mũi vắc xin đã tiêm (tránh tư vấn tiêm lại những mũi bé đã tiêm xong, chỉ gợi ý những mũi chưa tiêm dựa theo tháng tuổi).',
    '3. Nguyên tắc an toàn y tế:',
    '   - Tuyệt đối không chẩn đoán bệnh lý hoặc kê đơn thuốc.',
    '   - Luôn khuyên phụ huynh đưa bé đến cơ sở y tế/bác sĩ nhi khoa ngay nếu có các dấu hiệu nguy hiểm như sốt cao liên tục, co giật, bỏ bú/bỏ ăn, khó thở, li bì, hoặc khi phụ huynh quá lo lắng.',
    '   - Nhắc nhở phụ huynh rằng các lời khuyên của bạn chỉ mang tính tham khảo khoa học, luôn đối chiếu với lịch tiêm chủng và hồ sơ y khoa chính thức.',
    '4. Văn phong của bạn:',
    '   - Trả lời bằng tiếng Việt, giọng điệu ấm áp, ngắn gọn, súc tích, dễ hiểu và khoa học.'
  ].join('\n');

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
    {
      method: 'POST',
      headers: {
        'x-goog-api-key': apiKey,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        systemInstruction: {
          parts: [{ text: systemInstruction }]
        },
        contents: [
          ...previousMessages,
          { role: 'user', parts: [{ text: userMessage }] }
        ],
        generationConfig: {
          temperature: 0.4,
          maxOutputTokens: 700
        }
      })
    }
  );

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error?.message || `Gemini API error ${response.status}`);
  }

  return {
    model,
    text: extractGeminiText(data) || 'Mình chưa tạo được câu trả lời. Bạn thử hỏi lại ngắn hơn nhé.'
  };
}

// --- API THÔNG TIN BÉ ---
app.get('/baby', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const child = await getFirstChild(parent.id);
    res.json(toBabyResponse(child));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.post('/baby', authenticateToken, async (req, res) => {
  const { name, dob, weight, height, gender } = req.body;
  try {
    // Validate input
    if (!name || !dob) {
      return res.status(400).json({ error: 'Name and date of birth are required' });
    }
    if (typeof weight !== 'number' || weight < 0 || typeof height !== 'number' || height < 0) {
      return res.status(400).json({ error: 'Weight and height must be positive numbers' });
    }

    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const existingChild = await getFirstChild(parent.id);
    const normalizedGender = normalizeGender(gender);

    if (existingChild) {
      const result = await pool.query(
        `UPDATE children
         SET name = $1, dob = to_date($2, 'DD/MM/YYYY'), weight = $3, height = $4,
             gender = $5, updated_at = NOW()
         WHERE id = $6 AND parent_id = $7
         RETURNING *`,
        [name, dob, weight, height, normalizedGender, existingChild.id, parent.id]
      );
      const updated = result.rows[0];
      try {
        await assessAndPersistForChild(updated, { weight_kg: weight ?? null, height_cm: height ?? null });
      } catch (e) {
        console.error('Failed to auto-assess after /baby update:', e && e.message ? e.message : e);
      }
      return res.json(toBabyResponse(updated));
    }

    const result = await pool.query(
      `INSERT INTO children (parent_id, name, dob, weight, height, gender)
       VALUES ($1, $2, to_date($3, 'DD/MM/YYYY'), $4, $5, $6)
       RETURNING *`,
      [parent.id, name, dob, weight, height, normalizedGender]
    );
    const created = result.rows[0];
    try {
      await assessAndPersistForChild(created, { weight_kg: weight ?? null, height_cm: height ?? null });
    } catch (e) {
      console.error('Failed to auto-assess after /baby create:', e && e.message ? e.message : e);
    }
    res.json(toBabyResponse(created));
  } catch (err) {
    console.error('Error updating baby info:', err);
    res.status(500).json({ error: 'Failed to update baby information' });
  }
});

// --- API THÔNG TIN TÀI KHOẢN PHỤ HUYNH ---
app.get('/parent/profile', authenticateToken, async (req, res) => {
  try {
    const result = await pool.query('SELECT id, username, full_name, phone, email, address FROM users WHERE username = $1', [req.user.username]);
    if (result.rowCount === 0) return res.status(404).json({ error: 'User not found' });
    const user = result.rows[0];
    res.json({
      id: user.id,
      username: user.username,
      fullName: user.full_name || '',
      phone: user.phone || '',
      email: user.email || '',
      address: user.address || ''
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.put('/parent/profile', authenticateToken, async (req, res) => {
  const { fullName, phone } = req.body;
  try {
    const result = await pool.query(
      `UPDATE users
       SET full_name = $1, phone = $2, updated_at = NOW()
       WHERE username = $3
       RETURNING id, username, full_name, phone`,
      [fullName || null, phone || null, req.user.username]
    );
    if (result.rowCount === 0) return res.status(404).json({ error: 'User not found' });
    const user = result.rows[0];
    res.json({
      id: user.id,
      username: user.username,
      fullName: user.full_name || '',
      phone: user.phone || ''
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// --- API THỐNG KÊ PHỤ HUYNH ---
app.get('/parent/stats', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    // Count children
    const childrenCountRes = await pool.query('SELECT COUNT(*)::int AS count FROM children WHERE parent_id = $1', [parent.id]);
    const childrenCount = childrenCountRes.rows[0].count;

    // Count appointments
    const appointmentsCountRes = await pool.query('SELECT COUNT(*)::int AS count FROM appointments WHERE parent_id = $1', [parent.id]);
    const appointmentsCount = appointmentsCountRes.rows[0].count;

    // Count completed vaccinations across all children
    const completedVaccinationsRes = await pool.query(
      `SELECT COUNT(*)::int AS count 
       FROM vaccination_records vr
       JOIN children c ON vr.child_id = c.id
       WHERE c.parent_id = $1 AND vr.status = 'completed'`,
      [parent.id]
    );
    const completedCount = completedVaccinationsRes.rows[0].count;

    res.json({
      childrenCount,
      appointmentsCount,
      completedVaccinationsCount: completedCount
    });
  } catch (err) {
    console.error('Error fetching parent stats:', err);
    res.status(500).json({ error: err.message || 'Failed to fetch parent stats' });
  }
});

// --- API DANH SACH/CHINH SUA BE ---
app.get('/children', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });
    const rows = await getChildrenByParent(parent.id);
    res.json(rows.map(toChildResponse));
  } catch (err) {
    console.error('Error fetching children:', err);
    res.status(500).json({ error: 'Failed to fetch children' });
  }
});

app.get('/children/:childId', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });
    const result = await pool.query(
      'SELECT * FROM children WHERE id = $1 AND parent_id = $2 LIMIT 1',
      [req.params.childId, parent.id]
    );
    if (result.rowCount === 0) return res.status(404).json({ error: 'Child not found' });
    res.json(toChildResponse(result.rows[0]));
  } catch (err) {
    console.error('Error fetching child:', err);
    res.status(500).json({ error: 'Failed to fetch child' });
  }
});

app.get('/children/:childId/profile', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const profile = await getChildProfileByParent(parent.id, req.params.childId);
    if (!profile) return res.status(404).json({ error: 'Child not found' });

    res.json(profile);
  } catch (err) {
    console.error('Error fetching child profile:', err);
    res.status(500).json({ error: 'Failed to fetch child profile' });
  }
});

app.post('/children', authenticateToken, async (req, res) => {
  const { name, dob, weight, height, gender, blood_type, note } = req.body || {};
  try {
    if (!name || !dob) {
      return res.status(400).json({ error: 'Name and date of birth are required' });
    }

    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const result = await pool.query(
      `INSERT INTO children (parent_id, name, dob, weight, height, gender, blood_type, note)
       VALUES ($1, $2, to_date($3, 'DD/MM/YYYY'), $4, $5, $6, $7, $8)
       RETURNING *`,
      [parent.id, name, dob, weight ?? null, height ?? null, normalizeChildGender(gender), blood_type ?? null, note ?? null]
    );

    const created = result.rows[0];
    // If weight/height provided, compute and persist an initial assessment
    try {
      await assessAndPersistForChild(created, { weight_kg: weight ?? null, height_cm: height ?? null });
    } catch (e) {
      console.error('Failed to auto-assess after creating child:', e && e.message ? e.message : e);
    }

    res.json(toChildResponse(created));
  } catch (err) {
    console.error('Error creating child:', err);
    res.status(500).json({ error: 'Failed to create child' });
  }
});

app.put('/children/:childId', authenticateToken, async (req, res) => {
  const { name, dob, weight, height, gender, blood_type, note } = req.body || {};
  try {
    if (!name || !dob) {
      return res.status(400).json({ error: 'Name and date of birth are required' });
    }

    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const result = await pool.query(
      `UPDATE children
       SET name = $1, dob = to_date($2, 'DD/MM/YYYY'), weight = $3, height = $4,
           gender = $5, blood_type = $6, note = $7, updated_at = NOW()
       WHERE id = $8 AND parent_id = $9
       RETURNING *`,
      [name, dob, weight ?? null, height ?? null, normalizeChildGender(gender), blood_type ?? null, note ?? null, req.params.childId, parent.id]
    );

    if (result.rowCount === 0) return res.status(404).json({ error: 'Child not found' });
    const updated = result.rows[0];
    try {
      await assessAndPersistForChild(updated, { weight_kg: weight ?? null, height_cm: height ?? null });
    } catch (e) {
      console.error('Failed to auto-assess after updating child:', e && e.message ? e.message : e);
    }
    res.json(toChildResponse(updated));
  } catch (err) {
    console.error('Error updating child:', err);
    res.status(500).json({ error: 'Failed to update child' });
  }
});

// --- API LỊCH HẸN ---
app.get('/appointments', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });
    const result = await pool.query(
      'SELECT * FROM appointments WHERE parent_id = $1 ORDER BY date DESC, time DESC',
      [parent.id]
    );
    res.json(result.rows.map(row => ({
      id: row.id,
      serviceType: row.service_type,
      hospitalName: row.hospital_name,
      date: row.date,
      time: row.time,
      status: row.status,
      childId: row.child_id || null,
      note: row.note || null,
      rejectionReason: row.rejection_reason || null
    })));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.post('/appointments', authenticateToken, async (req, res) => {
  const { id, serviceType, hospitalName, date, time, status, childId, note, parentName, parentPhone } = req.body;
  try {
    // Validate input
    if (!id || !serviceType || !hospitalName || !date || !time) {
      return res.status(400).json({ error: 'All appointment fields are required' });
    }

    const finalStatus = status || 'Chờ xác nhận';

    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });
    
    let resolvedChildId = childId;
    if (!resolvedChildId) {
      const child = await getFirstChild(parent.id);
      resolvedChildId = child ? child.id : null;
    }

    // Use provided parentName/parentPhone or fall back to user profile data
    const resolvedParentName = parentName || parent.full_name || null;
    const resolvedParentPhone = parentPhone || parent.phone || null;

    const result = await pool.query(
      `INSERT INTO appointments
       (id, service_type, hospital_name, date, time, status, parent_id, child_id, note, parent_name, parent_phone)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
       RETURNING *`,
      [id, serviceType, hospitalName, date, time, finalStatus, parent.id, resolvedChildId, note || null, resolvedParentName, resolvedParentPhone]
    );

    const created = result.rows[0];

    // Notify doctor's web so they can accept/reject. The doctor's web is expected to be
    // listening on DOCTOR_URL and have an endpoint to receive notifications.
    (async () => {
      try {
        const notifyUrl = `${DOCTOR_URL.replace(/\/$/, '')}/appointments/notify`;
        await fetch(notifyUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            id: created.id,
            serviceType: created.service_type,
            hospitalName: created.hospital_name,
            date: created.date,
            time: created.time,
            callbackUrl: `${SERVER_URL}/doctor/confirm`,
            callbackSecret: DOCTOR_SECRET
          })
        });
        console.log('Notified doctor web at', notifyUrl);
      } catch (notifyErr) {
        console.error('Failed to notify doctor web:', notifyErr.message || notifyErr);
      }
    })();

    res.json(created);
  } catch (err) {
    console.error('Error creating appointment:', err);
    res.status(500).json({ error: 'Failed to create appointment' });
  }
});

// POST: Cancel an appointment (from client/user side)
app.post('/appointments/:id/cancel', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    // Check if the appointment exists and belongs to this parent
    const appointmentResult = await pool.query(
      'SELECT id, status FROM appointments WHERE id = $1 AND parent_id = $2',
      [req.params.id, parent.id]
    );

    if (appointmentResult.rowCount === 0) {
      return res.status(404).json({ error: 'Appointment not found or unauthorized' });
    }

    const currentStatus = appointmentResult.rows[0].status;
    if (currentStatus === 'Đã tiêm' || currentStatus === 'Đã hủy' || currentStatus === 'Từ chối') {
      return res.status(400).json({ error: `Không thể hủy lịch hẹn đang có trạng thái: ${currentStatus}` });
    }

    await pool.query(
      "UPDATE appointments SET status = 'Đã hủy' WHERE id = $1",
      [req.params.id]
    );

    res.json({ success: true, message: 'Lịch hẹn đã được hủy thành công' });
  } catch (err) {
    console.error('Error cancelling appointment:', err);
    res.status(500).json({ error: err.message || 'Failed to cancel appointment' });
  }
});


// --- API TIÊM CHỦNG ---
app.get('/vaccinations/:babyName', authenticateToken, async (req, res) => {
  try {
    // Validate input
    if (!req.params.babyName || req.params.babyName.trim() === '') {
      return res.status(400).json({ error: 'Baby name is required' });
    }
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const childResult = await pool.query(
      'SELECT id FROM children WHERE parent_id = $1 AND name = $2 ORDER BY created_at ASC LIMIT 1',
      [parent.id, req.params.babyName]
    );
    const child = childResult.rows[0] || await getFirstChild(parent.id);
    if (!child) return res.json([]);

    const result = await pool.query(
      `SELECT vaccine_id
       FROM vaccination_records
       WHERE child_id = $1 AND status = 'completed'`,
      [child.id]
    );
    res.json(result.rows.map(row => row.vaccine_id));
  } catch (err) {
    console.error('Error fetching vaccinations:', err);
    res.status(500).json({ error: 'Failed to fetch vaccinations' });
  }
});

app.post('/vaccinations', authenticateToken, async (req, res) => {
  const { babyName, vaccineId, status } = req.body;
  try {
    // Validate input
    if (!babyName || !vaccineId) {
      return res.status(400).json({ error: 'Baby name and vaccine ID are required' });
    }
    if (typeof vaccineId !== 'number' || vaccineId < 0) {
      return res.status(400).json({ error: 'Invalid vaccine ID' });
    }

    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const childResult = await pool.query(
      'SELECT id FROM children WHERE parent_id = $1 AND name = $2 ORDER BY created_at ASC LIMIT 1',
      [parent.id, babyName]
    );
    const child = childResult.rows[0] || await getFirstChild(parent.id);
    if (!child) return res.status(400).json({ error: 'Baby information is required before marking vaccinations' });

    const finalStatus = status === 'upcoming' ? 'upcoming' : 'completed';
    const completedDate = finalStatus === 'completed' ? new Date() : null;

    await pool.query(
      `INSERT INTO vaccination_records (child_id, vaccine_id, completed_date, status)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT (child_id, vaccine_id)
       DO UPDATE SET completed_date = EXCLUDED.completed_date,
                     status = EXCLUDED.status,
                     updated_at = NOW()`,
      [child.id, vaccineId, completedDate, finalStatus]
    );
    res.json({ success: true });
  } catch (err) {
    console.error('Error marking vaccination:', err);
    res.status(500).json({ error: 'Failed to mark vaccination' });
  }
});

// --- GROWTH ASSESSMENT PROXY ---
app.post('/api/growth/assess', authenticateToken, async (req, res) => {
  try {
    const payload = req.body || {};
    const modelService = process.env.MODEL_SERVICE_URL || 'http://localhost:8001/predict';
    let result;
    try {
      const response = await fetch(modelService, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const text = await response.text().catch(() => '');
        console.error('Model service error:', response.status, text);
        throw new Error(text || `Model service returned ${response.status}`);
      }

      result = await response.json().catch(() => ({}));
    } catch (modelErr) {
      console.info(`Falling back to local growth assessor because model service is unavailable: ${describeModelServiceError(modelErr)}`);
      result = runLocalGrowthAssessment(payload);
    }

    const parent = await getCurrentParent(req.user.username);
    const child = parent && payload.childId
      ? await pool.query('SELECT * FROM children WHERE id = $1 AND parent_id = $2 LIMIT 1', [payload.childId, parent.id]).then(r => r.rows[0] || null)
      : null;

    const modelVersion = process.env.MODEL_VERSION || 'lgbm_optuna';
    const classification = result.prediction || result.predicted || null;
    const bmi = result.bmi || null;

    // If we do not have a linked parent/child yet, still return the model result.
    // This prevents the UI from surfacing a 404 for a valid assessment request.
    if (!parent || !child) {
      return res.json({
        success: true,
        assessment: null,
        model_result: result,
        prediction: result.prediction || result.predicted || null,
        top_probability: result.top_probability || null,
        recommendation: result.recommendation || null,
        who_class: result.who_class || null,
        who_zscore: result.who_zscore || null,
        bmi: result.bmi || null,
        probabilities: result.probabilities || {},
        persisted: false,
        warning: !parent ? 'Parent not found; assessment not persisted' : 'Child not found; assessment not persisted'
      });
    }

    const measurementResult = await pool.query(
      `INSERT INTO child_measurements (child_id, measured_at, weight, height, head_circumference, note)
       VALUES ($1, CURRENT_DATE, $2, $3, $4, $5)
       RETURNING *`,
      [
        child.id,
        payload.weight_kg ?? null,
        payload.height_cm ?? null,
        payload.head_circumference_cm ?? null,
        JSON.stringify({ source: 'growth_assessment', request: payload })
      ]
    );
    const measurement = measurementResult.rows[0];

    const insertResult = await pool.query(
      `INSERT INTO growth_assessments (
         child_id,
         measurement_id,
         model_version,
         bmi,
         weight_for_age_z,
         height_for_age_z,
         bmi_for_age_z,
         classification,
         risk_level,
         summary,
         recommendations
       )
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
       RETURNING *`,
      [
        child.id,
        measurement.id,
        modelVersion,
        bmi,
        result.weight_for_age_z ?? null,
        result.height_for_age_z ?? null,
        result.bmi_for_age_z ?? null,
        classification,
        result.risk_level || classification,
        JSON.stringify({ request: payload, result }),
        JSON.stringify({ recommendation: result.recommendation || null, probabilities: result.probabilities || {} })
      ]
    );

    res.json({
      success: true,
      assessment: insertResult.rows[0],
      measurement,
      model_result: result,
      prediction: result.prediction || result.predicted || null,
      top_probability: result.top_probability || null,
      recommendation: result.recommendation || null,
      who_class: result.who_class || null,
      who_zscore: result.who_zscore || null,
      bmi: result.bmi || null,
      probabilities: result.probabilities || {},
      persisted: true
    });
  } catch (err) {
    console.error('Error in /api/growth/assess:', err);
    res.status(500).json({ error: 'Failed to perform assessment', message: err.message });
  }
});

// --- AI CHAT HISTORY ---
app.get('/chat/sessions', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const result = await pool.query(
      `SELECT id, title, created_at, updated_at
       FROM chat_sessions
       WHERE parent_id = $1
       ORDER BY updated_at DESC`,
      [parent.id]
    );
    res.json(result.rows);
  } catch (err) {
    console.error('Error fetching chat sessions:', err);
    res.status(500).json({ error: 'Failed to fetch chat sessions' });
  }
});

app.post('/chat/sessions', authenticateToken, async (req, res) => {
  const { title } = req.body || {};
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const result = await pool.query(
      `INSERT INTO chat_sessions (parent_id, title)
       VALUES ($1, $2)
       RETURNING id, title, created_at, updated_at`,
      [parent.id, title || 'Đoạn chat mới']
    );
    res.json(result.rows[0]);
  } catch (err) {
    console.error('Error creating chat session:', err);
    res.status(500).json({ error: 'Failed to create chat session' });
  }
});

app.get('/chat/sessions/:sessionId/messages', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const session = await pool.query(
      'SELECT id FROM chat_sessions WHERE id = $1 AND parent_id = $2',
      [req.params.sessionId, parent.id]
    );
    if (session.rowCount === 0) return res.status(404).json({ error: 'Chat session not found' });

    const result = await pool.query(
      `SELECT id, role, content, model, metadata, created_at
       FROM chat_messages
       WHERE session_id = $1
       ORDER BY created_at ASC`,
      [req.params.sessionId]
    );
    res.json(result.rows);
  } catch (err) {
    console.error('Error fetching chat messages:', err);
    res.status(500).json({ error: 'Failed to fetch chat messages' });
  }
});

app.post('/chat/sessions/:sessionId/messages', authenticateToken, async (req, res) => {
  const { message } = req.body || {};
  if (!message || !message.trim()) return res.status(400).json({ error: 'Message is required' });

  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const session = await pool.query(
      'SELECT id, title FROM chat_sessions WHERE id = $1 AND parent_id = $2',
      [req.params.sessionId, parent.id]
    );
    if (session.rowCount === 0) return res.status(404).json({ error: 'Chat session not found' });

    const history = await pool.query(
      `SELECT role, content
       FROM chat_messages
       WHERE session_id = $1
       ORDER BY created_at ASC`,
      [req.params.sessionId]
    );

    const userInsert = await pool.query(
      `INSERT INTO chat_messages (session_id, role, content)
       VALUES ($1, 'user', $2)
       RETURNING id, role, content, model, metadata, created_at`,
      [req.params.sessionId, message.trim()]
    );

    let reply;
    try {
      reply = await generateBabyCareReply(parent, message.trim(), history.rows);
    } catch (aiErr) {
      console.error('AI provider failed:', aiErr.message || aiErr);
      reply = {
        model: 'gemini-error-fallback',
        text: 'Mình đã lưu câu hỏi của bạn, nhưng Gemini API hiện đang lỗi quota, billing hoặc key. Bạn có thể kiểm tra Google AI Studio/API key rồi thử lại sau.'
      };
    }

    const assistantInsert = await pool.query(
      `INSERT INTO chat_messages (session_id, role, content, model)
       VALUES ($1, 'assistant', $2, $3)
       RETURNING id, role, content, model, metadata, created_at`,
      [req.params.sessionId, reply.text, reply.model]
    );

    const newTitle = session.rows[0].title === 'Đoạn chat mới'
      ? message.trim().slice(0, 80)
      : session.rows[0].title;
    await pool.query(
      'UPDATE chat_sessions SET title = $1, updated_at = NOW() WHERE id = $2',
      [newTitle, req.params.sessionId]
    );

    res.json({
      userMessage: userInsert.rows[0],
      assistantMessage: assistantInsert.rows[0]
    });
  } catch (err) {
    console.error('Error sending chat message:', err);
    res.status(500).json({ error: err.message || 'Failed to send chat message' });
  }
});

// PUT: Update chat session title
app.put('/chat/sessions/:sessionId', authenticateToken, async (req, res) => {
  const { title } = req.body || {};
  if (!title || !title.trim()) return res.status(400).json({ error: 'Title is required' });
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const session = await pool.query(
      'SELECT id FROM chat_sessions WHERE id = $1 AND parent_id = $2',
      [req.params.sessionId, parent.id]
    );
    if (session.rowCount === 0) return res.status(404).json({ error: 'Chat session not found' });

    await pool.query(
      'UPDATE chat_sessions SET title = $1, updated_at = NOW() WHERE id = $2',
      [title.trim(), req.params.sessionId]
    );
    res.json({ success: true });
  } catch (err) {
    console.error('Error updating chat session:', err);
    res.status(500).json({ error: 'Failed to update chat session' });
  }
});

// DELETE single chat session and its messages
app.delete('/chat/sessions/:sessionId', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    const session = await pool.query(
      'SELECT id FROM chat_sessions WHERE id = $1 AND parent_id = $2',
      [req.params.sessionId, parent.id]
    );
    if (session.rowCount === 0) return res.status(404).json({ error: 'Chat session not found' });

    await pool.query(
      'DELETE FROM chat_messages WHERE session_id = $1',
      [req.params.sessionId]
    );
    await pool.query('DELETE FROM chat_sessions WHERE id = $1', [req.params.sessionId]);

    res.json({ success: true });
  } catch (err) {
    console.error('Error deleting chat session:', err);
    res.status(500).json({ error: 'Failed to delete chat session' });
  }
});

// DELETE all chat sessions and messages for current parent (dev helper)
app.delete('/chat/sessions-all', authenticateToken, async (req, res) => {
  try {
    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });

    await pool.query(
      `DELETE FROM chat_messages WHERE session_id IN (
         SELECT id FROM chat_sessions WHERE parent_id = $1
       )`,
      [parent.id]
    );
    await pool.query('DELETE FROM chat_sessions WHERE parent_id = $1', [parent.id]);

    res.json({ success: true, message: 'All chat sessions deleted' });
  } catch (err) {
    console.error('Error deleting chat sessions:', err);
    res.status(500).json({ error: 'Failed to delete chat sessions' });
  }
});

// --- AUTHENTICATION ---
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  if (!token) return res.status(401).json({ error: 'Missing token' });
  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) return res.status(403).json({ error: 'Invalid token' });
    req.user = user;
    next();
  });
}

app.post('/auth/register', async (req, res) => {
  const { username, password, fullName } = req.body;
  if (!username || !password) return res.status(400).json({ error: 'Username and password required' });
  try {
    const exists = await pool.query('SELECT username FROM users WHERE username = $1', [username]);
    if (exists.rowCount > 0) return res.status(400).json({ error: 'User already exists' });
    const hash = await bcrypt.hash(password, 10);
    const result = await pool.query(
      'INSERT INTO users (username, password_hash, full_name) VALUES ($1, $2, $3) RETURNING id, username',
      [username, hash, fullName || null]
    );
    const user = result.rows[0];
    const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '12h' });
    res.json({ success: true, token });
  } catch (err) {
    console.error('Error registering user:', err);
    res.status(500).json({ error: 'Registration failed' });
  }
});

app.post('/auth/login', async (req, res) => {
  const { username, password } = req.body;
  console.log(`[auth] Login request received for user: ${username}`);
  if (!username || !password) {
    console.log('[auth] Login failed: missing username or password');
    return res.status(400).json({ error: 'Username and password required' });
  }
  try {
    const result = await pool.query('SELECT id, username, password_hash FROM users WHERE username = $1', [username]);
    if (result.rowCount === 0) {
      console.log(`[auth] Login failed: user ${username} not found`);
      return res.status(400).json({ error: 'Invalid credentials' });
    }
    const user = result.rows[0];
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) {
      console.log(`[auth] Login failed: incorrect password for user ${username}`);
      return res.status(400).json({ error: 'Invalid credentials' });
    }
    const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '12h' });
    console.log(`[auth] Login successful for user: ${username}`);
    res.json({ token });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ error: 'Login failed' });
  }
});

// Endpoint for doctor's web to confirm/reject an appointment
app.post('/doctor/confirm', async (req, res) => {
  const secret = req.headers['x-doctor-secret'];
  if (secret !== DOCTOR_SECRET) return res.status(403).json({ error: 'Invalid doctor secret' });
  const { id, status, rejection_reason } = req.body;
  if (!id || !status) return res.status(400).json({ error: 'Appointment id and status required' });
  try {
    const validStatuses = ['Đã xác nhận', 'Từ chối', 'Đã hủy'];
    if (!validStatuses.includes(status)) return res.status(400).json({ error: 'Invalid status' });
    await pool.query('UPDATE appointments SET status = $1, rejection_reason = $2 WHERE id = $3', [status, rejection_reason || null, id]);
    res.json({ success: true });
  } catch (err) {
    console.error('Error in doctor confirm:', err);
    res.status(500).json({ error: 'Failed to update appointment' });
  }
});

// --- QUẢN LÝ ADMIN ---
app.post('/admin/confirm-appointment', authenticateToken, async (req, res) => {
    const { id, status, rejection_reason } = req.body;
    try {
        // Validate input
        if (!id || !status) {
            return res.status(400).json({ error: 'Appointment ID and status are required' });
        }
        const validStatuses = ['Đã xác nhận', 'Từ chối', 'Chờ xác nhận', 'Đã tiêm', 'Đã hủy'];
        if (!validStatuses.includes(status)) {
            return res.status(400).json({ error: 'Invalid status' });
        }

        await pool.query('UPDATE appointments SET status = $1, rejection_reason = $2 WHERE id = $3', [status, rejection_reason || null, id]);

        // If the appointment status is updated to 'Đã tiêm', automatically mark it as completed in vaccination_records
        if (status === 'Đã tiêm') {
          const apptResult = await pool.query('SELECT * FROM appointments WHERE id = $1', [id]);
          if (apptResult.rowCount > 0) {
            const appt = apptResult.rows[0];
            const serviceType = appt.service_type; // e.g. "Tiêm chủng (Lao (BCG))"
            let vaccineName = '';
            const prefix = "Tiêm chủng (";
            if (serviceType.startsWith(prefix) && serviceType.endsWith(")")) {
              vaccineName = serviceType.substring(prefix.length, serviceType.length - 1);
            } else {
              vaccineName = serviceType.replace("Tiêm chủng", "").replace("(", "").replace(")", "").trim();
            }

            if (vaccineName && appt.child_id) {
              const vacResult = await pool.query('SELECT id FROM vaccines WHERE name = $1', [vaccineName]);
              if (vacResult.rowCount > 0) {
                const vaccineId = vacResult.rows[0].id;
                await pool.query(
                  `INSERT INTO vaccination_records (child_id, vaccine_id, completed_date, status)
                   VALUES ($1, $2, NOW(), 'completed')
                   ON CONFLICT (child_id, vaccine_id)
                   DO UPDATE SET completed_date = NOW(), status = 'completed', updated_at = NOW()`,
                  [appt.child_id, vaccineId]
                );
              }
            }
          }
        }

        res.json({ success: true });
    } catch (err) {
        console.error('Error updating appointment:', err);
        res.status(500).json({ error: 'Failed to update appointment' });
    }
});

// Admin API: return appointments JSON (admin UI removed for security)
app.get('/admin', authenticateToken, async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT a.*, c.name AS child_name, c.dob AS child_dob,
             COALESCE(a.parent_name, u.full_name) AS resolved_parent_name,
             COALESCE(a.parent_phone, u.phone) AS resolved_parent_phone
      FROM appointments a
      LEFT JOIN children c ON a.child_id = c.id
      LEFT JOIN users u ON a.parent_id = u.id
      ORDER BY a.date DESC, a.time DESC
    `);
    res.json(result.rows.map(row => ({
      id: row.id,
      serviceType: row.service_type,
      hospitalName: row.hospital_name,
      date: row.date,
      time: row.time,
      status: row.status,
      childId: row.child_id || null,
      childName: row.child_name || 'N/A',
      childDob: row.child_dob ? formatDateForApp(row.child_dob) : 'N/A',
      note: row.note || null,
      rejectionReason: row.rejection_reason || null,
      parentName: row.resolved_parent_name || 'N/A',
      parentPhone: row.resolved_parent_phone || 'N/A'
    })));
  } catch (err) {
    console.error('Error fetching admin data:', err);
    res.status(500).json({ error: 'Failed to load admin data' });
  }
});

// Simple doctor web UI for reviewing and confirming/rejecting appointments.
app.get('/doctor', (req, res) => {
  res.type('html').send(`<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Bác sĩ Portal - BabyCare</title>
  <style>
    body { font-family: 'Segoe UI', Arial, sans-serif; margin: 0; background: #f8fafc; color: #1e293b; display: flex; height: 100vh; overflow: hidden; }
    .hidden { display: none !important; }
    
    /* Login Centered Style */
    .login-container { display: flex; align-items: center; justify-content: center; height: 100vh; width: 100vw; background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%); }
    .login-card { background: #fff; border-radius: 16px; padding: 32px; box-shadow: 0 10px 25px rgba(0,0,0,0.05); width: 100%; max-width: 400px; border: 1px solid #e2e8f0; text-align: center; }
    .login-card h2 { margin: 0 0 8px; font-size: 24px; color: #1e3a8a; }
    .login-card p { margin: 0 0 24px; color: #64748b; font-size: 14px; }
    .login-card input { width: 100%; padding: 12px 16px; border: 1px solid #cbd5e1; border-radius: 10px; margin-bottom: 16px; box-sizing: border-box; font-size: 14px; outline: none; transition: border 0.2s; }
    .login-card input:focus { border-color: #2563eb; }
    
    /* Dashboard Sidebar Layout */
    .dashboard-container { display: flex; width: 100vw; height: 100vh; }
    .sidebar { width: 260px; background: #1e293b; color: #fff; display: flex; flex-direction: column; flex-shrink: 0; }
    .sidebar-header { padding: 24px; font-size: 20px; font-weight: bold; border-bottom: 1px solid #334155; display: flex; align-items: center; gap: 10px; color: #3b82f6; }
    .sidebar-menu { list-style: none; padding: 0; margin: 20px 0; flex: 1; }
    .sidebar-menu li { padding: 14px 24px; cursor: pointer; display: flex; align-items: center; gap: 12px; font-weight: 600; transition: all 0.2s; color: #94a3b8; }
    .sidebar-menu li:hover { background: #334155; color: #fff; }
    .sidebar-menu li.active { background: #2563eb; color: #fff; border-left: 4px solid #3b82f6; }
    
    /* Main Content Area */
    .main-content { flex: 1; display: flex; flex-direction: column; background: #f8fafc; overflow-y: auto; min-width: 0; }
    .header { background: #fff; padding: 16px 32px; border-bottom: 1px solid #e2e8f0; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 1px 2px rgba(0,0,0,0.02); }
    .header-title { font-size: 18px; font-weight: bold; color: #0f172a; }
    .user-info { display: flex; align-items: center; gap: 12px; font-size: 14px; font-weight: 500; color: #475569; }
    .wrap { padding: 24px 32px; width: 100%; box-sizing: border-box; }
    
    /* UI Elements */
    .card { background: #fff; border-radius: 16px; padding: 24px; box-shadow: 0 4px 20px rgba(0,0,0,0.02); border: 1px solid #e2e8f0; margin-bottom: 24px; }
    .card-title { font-size: 20px; font-weight: bold; margin: 0 0 16px; color: #0f172a; }
    button { padding: 10px 18px; border: 0; border-radius: 10px; cursor: pointer; font-weight: 600; font-size: 13px; transition: all 0.2s; }
    .btn-primary { background: #2563eb; color: #fff; width: 100%; }
    .btn-primary:hover { background: #1d4ed8; }
    .btn-ok { background: #10b981; color: #fff; }
    .btn-ok:hover { background: #059669; }
    .btn-no { background: #ef4444; color: #fff; }
    .btn-no:hover { background: #dc2626; }
    .btn-muted { background: #f1f5f9; color: #334155; border: 1px solid #e2e8f0; }
    .btn-muted:hover { background: #e2e8f0; }
    
    /* Table Styling */
    table { width: 100%; border-collapse: collapse; margin-top: 8px; table-layout: auto; }
    th, td { text-align: left; border-bottom: 1px solid #f1f5f9; padding: 12px 14px; font-size: 13px; white-space: nowrap; }
    th { background: #f8fafc; color: #475569; font-weight: 700; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px; cursor: pointer; user-select: none; position: relative; }
    th:hover { background: #e2e8f0; color: #1e293b; }
    th .sort-icon { margin-left: 4px; opacity: 0.4; font-size: 10px; }
    th.sort-asc .sort-icon, th.sort-desc .sort-icon { opacity: 1; color: #2563eb; }
    td { color: #1e293b; }
    tr:hover td { background: #f8fafc; }
    .muted { color: #64748b; font-size: 13px; text-align: center; padding: 32px 0; }
    
    /* Pagination */
    .pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; flex-wrap: wrap; gap: 8px; }
    .pagination-info { font-size: 13px; color: #64748b; }
    .pagination-btns { display: flex; align-items: center; gap: 4px; }
    .page-btn { padding: 6px 12px; border-radius: 8px; border: 1px solid #e2e8f0; background: #fff; color: #475569; font-size: 13px; font-weight: 600; cursor: pointer; min-width: 36px; text-align: center; transition: all 0.15s; }
    .page-btn:hover { background: #eff6ff; border-color: #bfdbfe; color: #2563eb; }
    .page-btn.active { background: #2563eb; color: #fff; border-color: #2563eb; }
    .page-btn:disabled { opacity: 0.4; cursor: default; }
    .page-btn.nav { font-size: 15px; }
    
    /* Badges */
    .status { display: inline-block; padding: 4px 10px; border-radius: 8px; font-size: 12px; font-weight: bold; text-align: center; white-space: nowrap; }
    .pending { background: #fef3c7; color: #d97706; }
    .approved { background: #d1fae5; color: #059669; }
    .rejected { background: #fee2e2; color: #dc2626; }
    .vaccinated { background: #e0f2fe; color: #0284c7; }
    .cancelled { background: #f1f5f9; color: #64748b; }
  </style>
</head>
<body>

  <!-- Login Area -->
  <div id="loginArea" class="login-container">
    <div class="login-card">
      <h2>Bác sĩ Portal</h2>
      <p>Đăng nhập để duyệt lịch hẹn và cập nhật lịch tiêm của các bé.</p>
      <input id="username" value="tester" placeholder="Tên đăng nhập" />
      <input id="password" value="1111" placeholder="Mật khẩu" type="password" />
      <button id="loginBtn" class="btn-primary">Đăng nhập</button>
      <div id="loginMsg" class="msg"></div>
    </div>
  </div>

  <!-- Dashboard Area -->
  <div id="dashboardArea" class="dashboard-container hidden">
    <!-- Sidebar Left Navigation -->
    <div class="sidebar">
      <div class="sidebar-header">
        <span>🩺 BabyCare Doctor</span>
      </div>
      <ul class="sidebar-menu">
        <li id="menu-confirm" class="active" onclick="switchTab('confirm')">
          <span>📋 Xác nhận Lịch hẹn</span>
        </li>
        <li id="menu-vaccine" onclick="switchTab('vaccine')">
          <span>💉 Lịch tiêm</span>
        </li>
        <li id="menu-history" onclick="switchTab('history')">
          <span>🗂️ Lịch sử</span>
        </li>
      </ul>
      <div style="padding: 24px; border-top: 1px solid #334155;">
        <button onclick="logout()" class="btn-muted" style="width:100%;">Đăng xuất</button>
      </div>
    </div>

    <!-- Main Content Right -->
    <div class="main-content">
      <div class="header">
        <div id="headerTitle" class="header-title">Xác nhận Lịch hẹn</div>
        <div class="user-info">
          <span>Bác sĩ trực: <b>tester</b></span>
          <button id="refreshBtn" class="btn-muted" style="padding: 6px 12px; border-radius: 8px;">Tải lại</button>
        </div>
      </div>

      <div class="wrap">
        <!-- Tab 1: Pending Appointments (chờ xác nhận) -->
        <div id="tab-confirm" class="tab-content">
          <div class="card">
            <div class="card-title">📋 Lịch hẹn chờ xác nhận</div>
            <div style="overflow-x: auto; width: 100%;">
              <table id="confirm-table">
                <thead>
                  <tr>
                    <th onclick="sortTable('confirm', 'childName')">Bé <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('confirm', 'serviceType')">Dịch vụ <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('confirm', 'hospitalName')">Cơ sở / Bác sĩ <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('confirm', 'date')">Ngày hẹn <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('confirm', 'time')">Giờ hẹn <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('confirm', 'parentName')">Phụ huynh <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('confirm', 'parentPhone')">SĐT <span class="sort-icon">&#8597;</span></th>
                    <th>Ghi chú</th>
                    <th>Hành động</th>
                  </tr>
                </thead>
                <tbody id="confirm-rows"></tbody>
              </table>
            </div>
            <div class="pagination">
              <div class="pagination-info" id="confirm-page-info"></div>
              <div class="pagination-btns" id="confirm-page-btns"></div>
            </div>
          </div>
        </div>

        <!-- Tab 2: Vaccine Appointments (đã xác nhận, chờ tiêm) -->
        <div id="tab-vaccine" class="tab-content hidden">
          <div class="card">
            <div class="card-title">💉 Lịch tiêm đã xác nhận – chờ tiêm</div>
            <div style="overflow-x: auto; width: 100%;">
              <table id="vaccine-table">
                <thead>
                  <tr>
                    <th onclick="sortTable('vaccine', 'childName')">Bé <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('vaccine', 'childDob')">Ngày sinh <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('vaccine', 'serviceType')">Tên vắc-xin <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('vaccine', 'date')">Thời gian tiêm <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('vaccine', 'parentName')">Phụ huynh <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('vaccine', 'parentPhone')">SĐT <span class="sort-icon">&#8597;</span></th>
                    <th>Ghi chú</th>
                    <th>Hành động</th>
                  </tr>
                </thead>
                <tbody id="vaccine-rows"></tbody>
              </table>
            </div>
            <div class="pagination">
              <div class="pagination-info" id="vaccine-page-info"></div>
              <div class="pagination-btns" id="vaccine-page-btns"></div>
            </div>
          </div>
        </div>

        <!-- Tab 3: History (hoàn thành / hủy / từ chối) -->
        <div id="tab-history" class="tab-content hidden">
          <div class="card">
            <div class="card-title">🗂️ Lịch sử lịch hẹn</div>
            <div style="overflow-x: auto; width: 100%;">
              <table id="history-table">
                <thead>
                  <tr>
                    <th onclick="sortTable('history', 'childName')">Bé <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('history', 'serviceType')">Dịch vụ <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('history', 'hospitalName')">Cơ sở / Bác sĩ <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('history', 'date')">Ngày <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('history', 'time')">Giờ <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('history', 'parentName')">Phụ huynh <span class="sort-icon">&#8597;</span></th>
                    <th onclick="sortTable('history', 'parentPhone')">SĐT <span class="sort-icon">&#8597;</span></th>
                    <th>Ghi chú</th>
                    <th onclick="sortTable('history', 'status')">Kết quả <span class="sort-icon">&#8597;</span></th>
                  </tr>
                </thead>
                <tbody id="history-rows"></tbody>
              </table>
            </div>
            <div class="pagination">
              <div class="pagination-info" id="history-page-info"></div>
              <div class="pagination-btns" id="history-page-btns"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Modal Lý do Từ chối / Hủy lịch -->
  <div id="rejectModal" class="hidden" style="position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 9999;">
    <div style="background: white; border-radius: 16px; padding: 24px; width: 100%; max-width: 450px; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1); border: 1px solid #e2e8f0; color: #1e293b;">
      <h3 style="margin-top: 0; margin-bottom: 12px; color: #1e3a8a; font-size: 18px; text-align: left;">Lý do từ chối / hủy lịch</h3>
      <p style="margin: 0 0 16px; color: #64748b; font-size: 14px; text-align: left;">Vui lòng chọn hoặc nhập lý do bác sĩ từ chối hoặc hủy lịch hẹn này:</p>
      
      <div style="display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; text-align: left;">
        <label style="display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; color: #1e293b;">
          <input type="radio" name="rejectReasonOpt" value="Trùng lịch khám của bác sĩ" checked onclick="toggleCustomReason(false)">
          <span>Trùng lịch khám của bác sĩ</span>
        </label>
        <label style="display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; color: #1e293b;">
          <input type="radio" name="rejectReasonOpt" value="Bệnh viện/Phòng khám hết thuốc vắc-xin" onclick="toggleCustomReason(false)">
          <span>Bệnh viện/Phòng khám hết thuốc vắc-xin</span>
        </label>
        <label style="display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; color: #1e293b;">
          <input type="radio" name="rejectReasonOpt" value="Lịch hẹn ngoài giờ làm việc của bác sĩ" onclick="toggleCustomReason(false)">
          <span>Lịch hẹn ngoài giờ làm việc của bác sĩ</span>
        </label>
        <label style="display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; color: #1e293b;">
          <input type="radio" name="rejectReasonOpt" value="Phụ huynh yêu cầu hủy lịch" onclick="toggleCustomReason(false)">
          <span>Phụ huynh yêu cầu hủy lịch</span>
        </label>
        <label style="display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; color: #1e293b;">
          <input type="radio" name="rejectReasonOpt" value="Khác" onclick="toggleCustomReason(true)">
          <span>Lý do khác...</span>
        </label>
      </div>
      
      <textarea id="customReasonInput" class="hidden" placeholder="Nhập lý do khác của bác sĩ..." style="width: 100%; height: 80px; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 8px; box-sizing: border-box; font-size: 14px; outline: none; margin-bottom: 16px; resize: none; font-family: inherit;"></textarea>
      
      <div style="display: flex; justify-content: flex-end; gap: 12px;">
        <button onclick="closeRejectModal()" class="btn-muted" style="width: auto;">Hủy bỏ</button>
        <button id="submitRejectBtn" class="btn-no" style="width: auto;" onclick="submitRejectReason()">Xác nhận từ chối</button>
      </div>
    </div>
  </div>

  <script>
    const PAGE_SIZE = 10;

    // Sort state per tab
    const sortState = {
      confirm: { key: 'date', dir: 'asc' },
      vaccine: { key: 'date', dir: 'asc' },
      history: { key: 'date', dir: 'desc' }
    };
    // Current page per tab
    const pageState = { confirm: 1, vaccine: 1, history: 1 };

    function escapeHtml(text) {
      if (!text) return '';
      const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
      return text.toString().replace(/[&<>"']/g, m => map[m]);
    }
    let token = '';
    const loginArea = document.getElementById('loginArea');
    const dashboardArea = document.getElementById('dashboardArea');
    const loginMsg = document.getElementById('loginMsg');
    const confirmRows = document.getElementById('confirm-rows');
    const vaccineRows = document.getElementById('vaccine-rows');
    const refreshBtn = document.getElementById('refreshBtn');
    const headerTitle = document.getElementById('headerTitle');
    
    let currentTab = 'confirm';
    let appointmentsData = [];
    
    let pendingRejectId = null;
    let pendingRejectStatus = null;

    function sortTable(tab, key) {
      const s = sortState[tab];
      if (s.key === key) {
        s.dir = s.dir === 'asc' ? 'desc' : 'asc';
      } else {
        s.key = key;
        s.dir = 'asc';
      }
      pageState[tab] = 1; // reset to page 1 on sort
      renderData();
    }

    function getSortedData(data, tab) {
      const { key, dir } = sortState[tab];
      return [...data].sort((a, b) => {
        let va = (a[key] || '').toString().toLowerCase();
        let vb = (b[key] || '').toString().toLowerCase();
        // try numeric/date comparison
        const na = Date.parse(va) || parseFloat(va);
        const nb = Date.parse(vb) || parseFloat(vb);
        if (!isNaN(na) && !isNaN(nb)) {
          return dir === 'asc' ? na - nb : nb - na;
        }
        if (va < vb) return dir === 'asc' ? -1 : 1;
        if (va > vb) return dir === 'asc' ? 1 : -1;
        return 0;
      });
    }

    function updateSortIcons(tab, tbodyId) {
      const table = document.getElementById(tab === 'confirm' ? 'confirm-table' : 'vaccine-table');
      if (!table) return;
      const { key, dir } = sortState[tab];
      table.querySelectorAll('th').forEach(th => {
        const icon = th.querySelector('.sort-icon');
        if (!icon) return;
        th.classList.remove('sort-asc', 'sort-desc');
        icon.innerHTML = '&#8597;';
        if (th.getAttribute('onclick') && th.getAttribute('onclick').includes("'" + key + "'")) {
          th.classList.add(dir === 'asc' ? 'sort-asc' : 'sort-desc');
          icon.innerHTML = dir === 'asc' ? '&#8593;' : '&#8595;';
        }
      });
    }

    function renderPagination(tab, total, current) {
      const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
      const infoEl = document.getElementById(tab + '-page-info');
      const btnsEl = document.getElementById(tab + '-page-btns');
      const from = total === 0 ? 0 : (current - 1) * PAGE_SIZE + 1;
      const to = Math.min(current * PAGE_SIZE, total);
      infoEl.textContent = total === 0 ? 'Không có dữ liệu' : 'Hiển thị ' + from + '–' + to + ' / ' + total + ' bản ghi';

      btnsEl.innerHTML = '';
      // Prev
      const prev = document.createElement('button');
      prev.className = 'page-btn nav';
      prev.innerHTML = '&#8249;';
      prev.title = 'Trang trước';
      prev.disabled = current <= 1;
      prev.onclick = () => { if (current > 1) { pageState[tab] = current - 1; renderData(); } };
      btnsEl.appendChild(prev);

      // Page buttons with ellipsis
      const pages = getPageRange(current, totalPages);
      pages.forEach(p => {
        if (p === '...') {
          const span = document.createElement('span');
          span.textContent = '…';
          span.style.cssText = 'padding: 0 6px; color:#94a3b8; font-size:14px; line-height:36px;';
          btnsEl.appendChild(span);
        } else {
          const btn = document.createElement('button');
          btn.className = 'page-btn' + (p === current ? ' active' : '');
          btn.textContent = p;
          btn.onclick = () => { pageState[tab] = p; renderData(); };
          btnsEl.appendChild(btn);
        }
      });

      // Next
      const next = document.createElement('button');
      next.className = 'page-btn nav';
      next.innerHTML = '&#8250;';
      next.title = 'Trang sau';
      next.disabled = current >= totalPages;
      next.onclick = () => { if (current < totalPages) { pageState[tab] = current + 1; renderData(); } };
      btnsEl.appendChild(next);
    }

    function getPageRange(current, total) {
      if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
      const pages = [];
      pages.push(1);
      if (current > 3) pages.push('...');
      for (let i = Math.max(2, current - 1); i <= Math.min(total - 1, current + 1); i++) pages.push(i);
      if (current < total - 2) pages.push('...');
      pages.push(total);
      return pages;
    }

    function openRejectModal(id, status) {
      pendingRejectId = id;
      pendingRejectStatus = status;
      document.getElementsByName('rejectReasonOpt')[0].checked = true;
      toggleCustomReason(false);
      document.getElementById('customReasonInput').value = '';
      const submitBtn = document.getElementById('submitRejectBtn');
      submitBtn.textContent = status === 'Từ chối' ? 'Xác nhận từ chối' : 'Xác nhận hủy';
      document.getElementById('rejectModal').classList.remove('hidden');
    }

    function closeRejectModal() {
      document.getElementById('rejectModal').classList.add('hidden');
      pendingRejectId = null;
      pendingRejectStatus = null;
    }

    function toggleCustomReason(show) {
      const input = document.getElementById('customReasonInput');
      if (show) {
        input.classList.remove('hidden');
        input.focus();
      } else {
        input.classList.add('hidden');
      }
    }

    async function submitRejectReason() {
      if (!pendingRejectId || !pendingRejectStatus) return;
      
      const selectedOpt = document.querySelector('input[name="rejectReasonOpt"]:checked').value;
      let reason = selectedOpt;
      if (selectedOpt === 'Khác') {
        reason = document.getElementById('customReasonInput').value.trim();
        if (!reason) {
          alert('Vui lòng nhập lý do khác của bác sĩ!');
          return;
        }
      }
      
      const idToSend = pendingRejectId;
      const statusToSend = pendingRejectStatus;
      
      closeRejectModal();
      
      try {
        await api('/admin/confirm-appointment', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer ' + token
          },
          body: JSON.stringify({ id: idToSend, status: statusToSend, rejection_reason: reason })
        });
        await fetchAppointments();
      } catch (err) {
        alert('Lỗi: ' + err.message);
      }
    }

    // Check for saved session on page load
    window.addEventListener('load', async () => {
      const savedToken = localStorage.getItem('doctor_token');
      if (savedToken) {
        token = savedToken;
        loginArea.classList.add('hidden');
        dashboardArea.classList.remove('hidden');
        await fetchAppointments();
      }
    });

    function badgeClass(status) {
      const clean = status.trim().toLowerCase();
      if (clean === 'đã xác nhận' || clean === 'approved' || clean === 'confirmed') return 'status approved';
      if (clean === 'từ chối' || clean === 'rejected') return 'status rejected';
      if (clean === 'đã tiêm' || clean === 'vaccinated') return 'status vaccinated';
      if (clean === 'đã hủy' || clean === 'cancelled') return 'status cancelled';
      return 'status pending';
    }

    function switchTab(tab) {
      currentTab = tab;
      document.querySelectorAll('.sidebar-menu li').forEach(li => li.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(tc => tc.classList.add('hidden'));

      const tabMap = {
        confirm: { menu: 'menu-confirm', tab: 'tab-confirm', title: 'Xác nhận Lịch hẹn' },
        vaccine: { menu: 'menu-vaccine', tab: 'tab-vaccine', title: 'Lịch tiêm chủng' },
        history: { menu: 'menu-history', tab: 'tab-history', title: 'Lịch sử' }
      };
      const info = tabMap[tab];
      if (info) {
        document.getElementById(info.menu).classList.add('active');
        document.getElementById(info.tab).classList.remove('hidden');
        headerTitle.textContent = info.title;
      }
      renderData();
    }

    function logout() {
      token = '';
      localStorage.removeItem('doctor_token');
      loginArea.classList.remove('hidden');
      dashboardArea.classList.add('hidden');
      loginMsg.textContent = '';
    }

    function extractVaccineName(serviceType) {
      const prefix = "Tiêm chủng (";
      if (serviceType.startsWith(prefix) && serviceType.endsWith(")")) {
        return serviceType.substring(prefix.length, serviceType.length - 1);
      }
      return serviceType.replace("Tiêm chủng", "").replace("(", "").replace(")", "").trim() || "Vắc-xin";
    }

    function formatApptDate(dateStr) {
      if (!dateStr) return '';
      const viMatch = dateStr.match(/([0-9]{1,2})[ \t]+thg[ \t]+([0-9]{1,2})[ \t]+([0-9]{4})/i);
      if (viMatch) {
        return viMatch[1].padStart(2, '0') + '/' + viMatch[2].padStart(2, '0') + '/' + viMatch[3];
      }
      const enMatch = dateStr.match(/([0-9]{1,2})[ \t]+([a-zA-Z]{3})[ \t]+([0-9]{4})/i);
      if (enMatch) {
        const months = { jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06', jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12' };
        const month = months[enMatch[2].toLowerCase()] || '01';
        return enMatch[1].padStart(2, '0') + '/' + month + '/' + enMatch[3];
      }
      const slashMatch = dateStr.match(new RegExp("([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})"));
      if (slashMatch) {
        return slashMatch[1].padStart(2, '0') + '/' + slashMatch[2].padStart(2, '0') + '/' + slashMatch[3];
      }
      const dashMatch = dateStr.match(/([0-9]{4})-([0-9]{2})-([0-9]{2})/);
      if (dashMatch) {
        return dashMatch[3] + '/' + dashMatch[2] + '/' + dashMatch[1];
      }
      return dateStr;
    }

    async function api(url, options) {
      const res = await fetch(url, options);
      let data = {};
      try { data = await res.json(); } catch (_) {}
      if (!res.ok) throw new Error(data.error || ('Lỗi HTTP ' + res.status));
      return data;
    }

    async function updateAppointmentStatus(id, newStatus, confirmMsg) {
      if (confirmMsg && !confirm(confirmMsg)) {
        return;
      }
      try {
        await api('/admin/confirm-appointment', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer ' + token
          },
          body: JSON.stringify({ id, status: newStatus })
        });
        await fetchAppointments();
      } catch (err) {
        alert('Lỗi: ' + err.message);
      }
    }

    async function fetchAppointments() {
      try {
        const data = await api('/admin', {
          headers: { Authorization: 'Bearer ' + token }
        });
        appointmentsData = Array.isArray(data) ? data : [];
        renderData();
      } catch (err) {
        alert('Không thể tải lịch hẹn: ' + err.message);
      }
    }

    function renderData() {
      // ============================================================
      // TAB 1: Chờ xác nhận (≠ tiêm chủng đã xác nhận)
      // Chỉ hiện các lịch đang "Chờ xác nhận"
      // ============================================================
      const pendingAppts = appointmentsData.filter(a => a.status === 'Chờ xác nhận');
      const sortedConfirm = getSortedData(pendingAppts, 'confirm');
      const confirmPage = pageState.confirm;
      const confirmTotal = sortedConfirm.length;
      const confirmPageData = sortedConfirm.slice((confirmPage - 1) * PAGE_SIZE, confirmPage * PAGE_SIZE);

      confirmRows.innerHTML = '';
      if (confirmTotal === 0) {
        confirmRows.innerHTML = '<tr><td colspan="9" class="muted">✅ Không có lịch hẹn nào đang chờ xác nhận.</td></tr>';
      } else {
        confirmPageData.forEach(a => {
          const tr = document.createElement('tr');
          tr.innerHTML =
            '<td><b>' + escapeHtml(a.childName || 'N/A') + '</b></td>' +
            '<td>' + escapeHtml(a.serviceType) + '</td>' +
            '<td style="max-width:180px;white-space:normal;">' + escapeHtml(a.hospitalName) + '</td>' +
            '<td>' + formatApptDate(a.date) + '</td>' +
            '<td>' + escapeHtml(a.time) + '</td>' +
            '<td><b>' + escapeHtml(a.parentName || 'N/A') + '</b></td>' +
            '<td>' + escapeHtml(a.parentPhone || 'N/A') + '</td>' +
            '<td style="max-width:160px;white-space:normal;color:#475569;">' + escapeHtml(a.note || '—') + '</td>' +
            '<td style="white-space:nowrap;">' +
              '<button class="btn-ok" style="padding:6px 12px;font-size:12px;" onclick="updateAppointmentStatus(\\\'' + a.id + '\\\', \\\'Đã xác nhận\\\')">Duyệt</button> ' +
              '<button class="btn-no" style="padding:6px 12px;font-size:12px;" onclick="openRejectModal(\\\'' + a.id + '\\\', \\\'Từ chối\\\')">Từ chối</button>' +
            '</td>';
          confirmRows.appendChild(tr);
        });
      }
      updateSortIcons('confirm', 'confirm-rows');
      renderPagination('confirm', confirmTotal, confirmPage);

      // ============================================================
      // TAB 2: Lịch tiêm – Tiêm chủng đã xác nhận, chưa tiêm xong
      // ============================================================
      const vaccineAppts = appointmentsData.filter(a =>
        a.serviceType && a.serviceType.includes('Tiêm chủng') &&
        a.status === 'Đã xác nhận'
      );
      const sortedVaccine = getSortedData(vaccineAppts, 'vaccine');
      const vaccinePage = pageState.vaccine;
      const vaccineTotal = sortedVaccine.length;
      const vaccinePageData = sortedVaccine.slice((vaccinePage - 1) * PAGE_SIZE, vaccinePage * PAGE_SIZE);

      vaccineRows.innerHTML = '';
      if (vaccineTotal === 0) {
        vaccineRows.innerHTML = '<tr><td colspan="8" class="muted">✅ Không có lịch tiêm nào đang chờ tiêm.</td></tr>';
      } else {
        vaccinePageData.forEach(a => {
          const tr = document.createElement('tr');
          tr.innerHTML =
            '<td><b>' + escapeHtml(a.childName) + '</b></td>' +
            '<td>' + escapeHtml(a.childDob) + '</td>' +
            '<td><span style="color:#2563eb;font-weight:600;">' + extractVaccineName(a.serviceType) + '</span></td>' +
            '<td>' + formatApptDate(a.date) + ' – ' + escapeHtml(a.time) + '</td>' +
            '<td><b>' + escapeHtml(a.parentName || 'N/A') + '</b></td>' +
            '<td>' + escapeHtml(a.parentPhone || 'N/A') + '</td>' +
            '<td style="max-width:160px;white-space:normal;color:#475569;">' + escapeHtml(a.note || '—') + '</td>' +
            '<td style="white-space:nowrap;">' +
              '<button class="btn-ok" style="padding:6px 12px;font-size:12px;" onclick="updateAppointmentStatus(\\\'' + a.id + '\\\', \\\'Đã tiêm\\\', \\\'Xác nhận ĐÃ TIÊM cho lịch này?\\\')">Đã tiêm</button> ' +
              '<button class="btn-no" style="padding:6px 12px;font-size:12px;" onclick="openRejectModal(\\\'' + a.id + '\\\', \\\'Đã hủy\\\')">Hủy</button>' +
            '</td>';
          vaccineRows.appendChild(tr);
        });
      }
      updateSortIcons('vaccine', 'vaccine-rows');
      renderPagination('vaccine', vaccineTotal, vaccinePage);

      // ============================================================
      // TAB 3: Lịch sử – Đã tiêm, Từ chối, Đã hủy
      // ============================================================
      const historyRows = document.getElementById('history-rows');
      const historyAppts = appointmentsData.filter(a =>
        a.status === 'Đã tiêm' || a.status === 'Từ chối' || a.status === 'Đã hủy'
      );
      const sortedHistory = getSortedData(historyAppts, 'history');
      const historyPage = pageState.history;
      const historyTotal = sortedHistory.length;
      const historyPageData = sortedHistory.slice((historyPage - 1) * PAGE_SIZE, historyPage * PAGE_SIZE);

      historyRows.innerHTML = '';
      if (historyTotal === 0) {
        historyRows.innerHTML = '<tr><td colspan="9" class="muted">Chưa có lịch hẹn nào trong lịch sử.</td></tr>';
      } else {
        historyPageData.forEach(a => {
          const rejectionText = (a.rejectionReason && (a.status === 'Từ chối' || a.status === 'Đã hủy'))
            ? '<br><span style="font-size:11px;color:#ef4444;font-weight:600;">Lý do: ' + escapeHtml(a.rejectionReason) + '</span>'
            : '';
          const tr = document.createElement('tr');
          tr.innerHTML =
            '<td><b>' + escapeHtml(a.childName || 'N/A') + '</b></td>' +
            '<td>' + escapeHtml(a.serviceType) + '</td>' +
            '<td style="max-width:180px;white-space:normal;">' + escapeHtml(a.hospitalName) + '</td>' +
            '<td>' + formatApptDate(a.date) + '</td>' +
            '<td>' + escapeHtml(a.time) + '</td>' +
            '<td><b>' + escapeHtml(a.parentName || 'N/A') + '</b></td>' +
            '<td>' + escapeHtml(a.parentPhone || 'N/A') + '</td>' +
            '<td style="max-width:160px;white-space:normal;color:#475569;">' + escapeHtml(a.note || '—') + '</td>' +
            '<td><span class="' + badgeClass(a.status) + '">' + a.status + '</span>' + rejectionText + '</td>';
          historyRows.appendChild(tr);
        });
      }
      updateSortIcons('history', 'history-rows');
      renderPagination('history', historyTotal, historyPage);
    }

    document.getElementById('loginBtn').addEventListener('click', async () => {
      console.log('Login button clicked');
      try {
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;
        console.log('Attempting login for username:', username);
        loginMsg.style.color = '#475569';
        loginMsg.textContent = 'Đang đăng nhập...';

        const login = await api('/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password })
        });
        console.log('Login successful, token received.');
        token = login.token;
        localStorage.setItem('doctor_token', token);
        loginArea.classList.add('hidden');
        dashboardArea.classList.remove('hidden');
        console.log('Loading appointments dashboard...');
        await fetchAppointments();
        console.log('Appointments loaded.');
      } catch (e) {
        console.error('Login error:', e);
        loginMsg.style.color = '#ef4444';
        loginMsg.textContent = e.message;
      }
    });

    // Press Enter to login shortcut
    const triggerLoginOnEnter = (e) => {
      if (e.key === 'Enter') {
        document.getElementById('loginBtn').click();
      }
    };
    document.getElementById('username').addEventListener('keydown', triggerLoginOnEnter);
    document.getElementById('password').addEventListener('keydown', triggerLoginOnEnter);

    refreshBtn.addEventListener('click', async () => {
      await fetchAppointments();
    });

    // Auto-refresh every 5 seconds when tab is active and logged in
    setInterval(async () => {
      if (token && !document.hidden) {
        try {
          const data = await api('/admin', {
            headers: { Authorization: 'Bearer ' + token }
          });
          appointmentsData = Array.isArray(data) ? data : [];
          renderData();
        } catch (e) {
          console.error('Auto-refresh error:', e);
        }
      }
    }, 5000);
  </script>
</body>
</html>`);
});

// Utility function to escape HTML
function escapeHtml(text) {
  if (!text) return '';
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  };
  return text.toString().replace(/[&<>"']/g, m => map[m]);
}

// Start server and fallback if port is already in use
function startListening(p) {
  const server = app.listen(p, () => console.log(`Server running on port ${p}`));
  server.on('error', (err) => {
    if (err && err.code === 'EADDRINUSE') {
      const fallback = 4000;
      if (p === fallback) {
        console.error(`Port ${p} already in use. Exiting.`);
        process.exit(1);
      } else {
        console.warn(`Port ${p} in use, trying fallback ${fallback}...`);
        startListening(fallback);
      }
    } else {
      console.error('Server error', err);
      process.exit(1);
    }
  });
}

startListening(port);
