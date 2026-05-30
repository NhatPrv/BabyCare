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
  const modelPath = path.join(PROJECT_ROOT, 'ml', 'models', 'growth_model_lgbm_optuna.joblib');

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
    ON CONFLICT (id) DO NOTHING
  `);

  await pool.query(`
    INSERT INTO vaccine_schedule (vaccine_id, recommended_month_age, dose_name) VALUES
      (1, 0, 'Mui so sinh'), (2, 0, 'Mui so sinh'),
      (3, 2, 'Mui 1'), (4, 2, 'Mui 1'), (5, 2, 'Lieu 1'),
      (6, 3, 'Mui 2'), (7, 3, 'Mui 2'), (8, 3, 'Lieu 2'),
      (9, 4, 'Mui 3'), (10, 4, 'Mui 3'),
      (11, 9, 'Mui 1'), (12, 12, 'Mui 1')
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
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
  )`);
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS parent_id UUID REFERENCES users(id) ON DELETE SET NULL');
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS child_id UUID REFERENCES children(id) ON DELETE SET NULL');
  await pool.query('ALTER TABLE appointments ADD COLUMN IF NOT EXISTS note TEXT');
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

  return {
    child: toChildResponse(child),
    latestAssessment: toAssessmentResponse(latestAssessmentResult.rows[0] || null),
    measurementHistory: measurementResult.rows.map(toMeasurementHistoryResponse)
  };
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

  const child = await getFirstChild(parent.id);
  const context = child
    ? `Thông tin bé: tên ${child.name}, ngày sinh ${formatDateForApp(child.dob)}, giới tính ${child.gender || 'chưa rõ'}, cân nặng ${child.weight || 'chưa rõ'}kg, chiều cao ${child.height || 'chưa rõ'}cm.`
    : 'Chưa có thông tin bé trong hồ sơ.';

  const previousMessages = historyRows.slice(-12).map(row => ({
    role: row.role === 'assistant' ? 'model' : 'user',
    parts: [{ text: row.content }]
  }));

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
        parts: [{
          text: [
        'Bạn là trợ lý BabyCare cho phụ huynh Việt Nam.',
        'Trả lời ngắn gọn, dễ hiểu, ưu tiên an toàn.',
        'Không chẩn đoán bệnh. Luôn khuyên gặp bác sĩ nhi khi có dấu hiệu nguy hiểm, sốt cao, bỏ bú, co giật, khó thở, mất nước, hoặc phụ huynh lo lắng.',
              'Khi nói về tăng trưởng/tiêm chủng, nhắc rằng kết quả chỉ hỗ trợ tham khảo và cần đối chiếu lịch tiêm/hồ sơ y tế chính thức.',
              context
            ].join(' ')
          }]
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
      return res.json(toBabyResponse(result.rows[0]));
    }

    const result = await pool.query(
      `INSERT INTO children (parent_id, name, dob, weight, height, gender)
       VALUES ($1, $2, to_date($3, 'DD/MM/YYYY'), $4, $5, $6)
       RETURNING *`,
      [parent.id, name, dob, weight, height, normalizedGender]
    );
    res.json(toBabyResponse(result.rows[0]));
  } catch (err) {
    console.error('Error updating baby info:', err);
    res.status(500).json({ error: 'Failed to update baby information' });
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

    res.json(toChildResponse(result.rows[0]));
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
    res.json(toChildResponse(result.rows[0]));
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
      status: row.status
    })));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.post('/appointments', authenticateToken, async (req, res) => {
  const { id, serviceType, hospitalName, date, time, status } = req.body;
  try {
    // Validate input
    if (!id || !serviceType || !hospitalName || !date || !time) {
      return res.status(400).json({ error: 'All appointment fields are required' });
    }

    const finalStatus = status || 'Chờ xác nhận';

    const parent = await getCurrentParent(req.user.username);
    if (!parent) return res.status(404).json({ error: 'Parent not found' });
    const child = await getFirstChild(parent.id);

    const result = await pool.query(
      `INSERT INTO appointments
       (id, service_type, hospital_name, date, time, status, parent_id, child_id)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
       RETURNING *`,
      [id, serviceType, hospitalName, date, time, finalStatus, parent.id, child ? child.id : null]
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
  const { babyName, vaccineId } = req.body;
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

    await pool.query(
      `INSERT INTO vaccination_records (child_id, vaccine_id, completed_date, status)
       VALUES ($1, $2, CURRENT_DATE, 'completed')
       ON CONFLICT (child_id, vaccine_id)
       DO UPDATE SET completed_date = EXCLUDED.completed_date,
                     status = 'completed',
                     updated_at = NOW()`,
      [child.id, vaccineId]
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
  if (!username || !password) return res.status(400).json({ error: 'Username and password required' });
  try {
    const result = await pool.query('SELECT id, username, password_hash FROM users WHERE username = $1', [username]);
    if (result.rowCount === 0) return res.status(400).json({ error: 'Invalid credentials' });
    const user = result.rows[0];
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) return res.status(400).json({ error: 'Invalid credentials' });
    const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '12h' });
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
  const { id, status } = req.body;
  if (!id || !status) return res.status(400).json({ error: 'Appointment id and status required' });
  try {
    const validStatuses = ['Đã xác nhận', 'Từ chối'];
    if (!validStatuses.includes(status)) return res.status(400).json({ error: 'Invalid status' });
    await pool.query('UPDATE appointments SET status = $1 WHERE id = $2', [status, id]);
    res.json({ success: true });
  } catch (err) {
    console.error('Error in doctor confirm:', err);
    res.status(500).json({ error: 'Failed to update appointment' });
  }
});

// --- QUẢN LÝ ADMIN ---
app.post('/admin/confirm-appointment', authenticateToken, async (req, res) => {
    const { id, status } = req.body;
    try {
        // Validate input
        if (!id || !status) {
            return res.status(400).json({ error: 'Appointment ID and status are required' });
        }
        const validStatuses = ['Đã xác nhận', 'Từ chối', 'Chờ xác nhận'];
        if (!validStatuses.includes(status)) {
            return res.status(400).json({ error: 'Invalid status' });
        }

        await pool.query('UPDATE appointments SET status = $1 WHERE id = $2', [status, id]);
        res.json({ success: true });
    } catch (err) {
        console.error('Error updating appointment:', err);
        res.status(500).json({ error: 'Failed to update appointment' });
    }
});

// Admin API: return appointments JSON (admin UI removed for security)
app.get('/admin', authenticateToken, async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM appointments ORDER BY date DESC');
    res.json(result.rows.map(row => ({
      id: row.id,
      serviceType: row.service_type,
      hospitalName: row.hospital_name,
      date: row.date,
      time: row.time,
      status: row.status
    })));
  } catch (err) {
    console.error('Error fetching admin data:', err);
    res.status(500).json({ error: 'Failed to load admin data' });
  }
});

// Simple doctor web UI for reviewing and confirming/rejecting appointments.
app.get('/doctor', (req, res) => {
  res.type('html').send(`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Doctor Portal</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 0; background: #f3f4f6; color: #111827; }
    .wrap { max-width: 980px; margin: 24px auto; padding: 0 16px; }
    .card { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 4px 20px rgba(0,0,0,.06); margin-bottom: 16px; }
    h1 { margin: 0 0 12px; font-size: 24px; }
    .row { display: flex; gap: 8px; flex-wrap: wrap; }
    input { padding: 10px; border: 1px solid #d1d5db; border-radius: 8px; min-width: 200px; }
    button { padding: 10px 12px; border: 0; border-radius: 8px; cursor: pointer; }
    .btn-primary { background: #2563eb; color: #fff; }
    .btn-ok { background: #059669; color: #fff; }
    .btn-no { background: #dc2626; color: #fff; }
    .btn-muted { background: #e5e7eb; color: #111827; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; border-bottom: 1px solid #e5e7eb; padding: 10px 8px; font-size: 14px; }
    .muted { color: #6b7280; font-size: 13px; }
    .status { display: inline-block; padding: 4px 8px; border-radius: 999px; font-size: 12px; }
    .pending { background: #fef3c7; color: #92400e; }
    .approved { background: #d1fae5; color: #065f46; }
    .rejected { background: #fee2e2; color: #991b1b; }
    .hidden { display: none; }
    .msg { margin-top: 8px; font-size: 13px; }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="card">
      <h1>Doctor Portal</h1>
      <div class="muted">Login to review appointments and confirm/reject quickly.</div>
      <div class="row" style="margin-top:12px;">
        <input id="username" value="tester" placeholder="Username" />
        <input id="password" value="1111" placeholder="Password" type="password" />
        <button id="loginBtn" class="btn-primary">Login</button>
        <button id="refreshBtn" class="btn-muted hidden">Refresh</button>
      </div>
      <div id="loginMsg" class="msg muted"></div>
    </div>

    <div id="tableCard" class="card hidden">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Service</th>
            <th>Hospital</th>
            <th>Date</th>
            <th>Time</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody id="rows"></tbody>
      </table>
    </div>
  </div>

  <script>
    let token = '';
    const loginMsg = document.getElementById('loginMsg');
    const tableCard = document.getElementById('tableCard');
    const rows = document.getElementById('rows');
    const refreshBtn = document.getElementById('refreshBtn');

    function badgeClass(status) {
      if (status === 'Đã xác nhận') return 'status approved';
      if (status === 'Từ chối') return 'status rejected';
      return 'status pending';
    }

    function setMsg(text, isError) {
      loginMsg.textContent = text || '';
      loginMsg.style.color = isError ? '#b91c1c' : '#065f46';
    }

    async function api(url, options) {
      const res = await fetch(url, options);
      let data = {};
      try { data = await res.json(); } catch (_) { /* ignore */ }
      if (!res.ok) throw new Error(data.error || ('HTTP ' + res.status));
      return data;
    }

    async function loadAppointments() {
      const data = await api('/admin', {
        headers: { Authorization: 'Bearer ' + token }
      });

      rows.innerHTML = '';
      if (!Array.isArray(data) || data.length === 0) {
        rows.innerHTML = '<tr><td colspan="7" class="muted">No appointments yet.</td></tr>';
        return;
      }

      data.forEach((a) => {
        const tr = document.createElement('tr');
        tr.innerHTML =
          '<td>' + a.id + '</td>' +
          '<td>' + a.serviceType + '</td>' +
          '<td>' + a.hospitalName + '</td>' +
          '<td>' + a.date + '</td>' +
          '<td>' + a.time + '</td>' +
          '<td><span class="' + badgeClass(a.status) + '">' + a.status + '</span></td>' +
          '<td>' +
            '<button class="btn-ok" data-id="' + a.id + '" data-status="Đã xác nhận">Confirm</button> ' +
            '<button class="btn-no" data-id="' + a.id + '" data-status="Từ chối">Reject</button>' +
          '</td>';
        rows.appendChild(tr);
      });
    }

    document.getElementById('loginBtn').addEventListener('click', async () => {
      try {
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;
        const login = await api('/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password })
        });
        token = login.token;
        setMsg('Login successful. Loading appointments...', false);
        tableCard.classList.remove('hidden');
        refreshBtn.classList.remove('hidden');
        await loadAppointments();
      } catch (e) {
        setMsg(e.message, true);
      }
    });

    refreshBtn.addEventListener('click', async () => {
      try {
        await loadAppointments();
        setMsg('Refreshed', false);
      } catch (e) {
        setMsg(e.message, true);
      }
    });

    rows.addEventListener('click', async (e) => {
      const btn = e.target.closest('button[data-id]');
      if (!btn || !token) return;
      try {
        btn.disabled = true;
        await api('/admin/confirm-appointment', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer ' + token
          },
          body: JSON.stringify({ id: btn.dataset.id, status: btn.dataset.status })
        });
        await loadAppointments();
        setMsg('Appointment updated', false);
      } catch (err) {
        setMsg(err.message, true);
      } finally {
        btn.disabled = false;
      }
    });
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
