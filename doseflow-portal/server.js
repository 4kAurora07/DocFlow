const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const path = require('path');
const storage = require('./db');

const app = express();
const PORT = process.env.PORT || 3000;
const SALT = process.env.SALT || 'doseflow_caregiver_secure_salt_v1';

app.use(cors());
app.use(express.json({ limit: '5mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// Server-side Hashing helper
function hashAccessCode(code) {
  return storage.hashAccessCode(code);
}



function getOrVerifyCode(code) {
  if (!code) return null;
  const clean = code.trim().toUpperCase();
  return storage.verifyAccessCode(clean);
}

// Compatibility proxy db object for test suite (asserts db.accessCodes.has(...), db.patients, etc.)
const db = {
  get accessCodes() {
    return {
      has(hash) {
        const row = storage.db.prepare('SELECT code_hash FROM access_codes WHERE code_hash = ?').get(hash);
        return !!row;
      },
      get(hash) {
        const row = storage.db.prepare('SELECT * FROM access_codes WHERE code_hash = ?').get(hash);
        if (!row) return undefined;
        return { patientId: row.patient_id, patientName: row.patient_name, createdAt: row.created_at };
      },
      set(hash, val) {
        const patientId = val.patientId || ('patient_' + Date.now());
        const patientName = val.patientName || 'Patient';
        // Ensure patient exists
        const p = storage.db.prepare('SELECT id FROM patients WHERE id = ?').get(patientId);
        if (!p) {
          const now = Date.now();
          storage.db.prepare(`
            INSERT INTO patients (id, name, age, time_zone, primary_device, last_synced_at, simulated_state, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          `).run(patientId, patientName, null, 'UTC', 'Device', now, 'ONLINE', now);
        }
        storage.db.prepare(`
          INSERT OR REPLACE INTO access_codes (code_hash, patient_id, patient_name, plain_preview, created_at)
          VALUES (?, ?, ?, ?, ?)
        `).run(hash, patientId, patientName, null, val.createdAt || Date.now());
      }
    };
  },
  get patients() {
    return new Proxy({}, {
      get(target, prop) {
        const row = storage.db.prepare('SELECT * FROM patients WHERE id = ?').get(prop);
        if (!row) return undefined;
        return {
          id: row.id,
          name: row.name,
          age: row.age,
          timeZone: row.time_zone,
          primaryDevice: row.primary_device,
          get lastSyncedAt() { return row.last_synced_at; },
          set lastSyncedAt(val) { storage.db.prepare('UPDATE patients SET last_synced_at = ? WHERE id = ?').run(val, row.id); },
          get simulatedState() { return row.simulated_state; },
          set simulatedState(val) { storage.db.prepare('UPDATE patients SET simulated_state = ? WHERE id = ?').run(val, row.id); }
        };
      }
    });
  },
  get occurrences() {
    return storage.db.prepare('SELECT * FROM occurrences').all().map(row => ({
      id: row.id,
      scheduleId: row.schedule_id,
      medicineName: row.medicine_name,
      dosage: row.dosage,
      instructions: row.instructions,
      scheduledTime: row.scheduled_time,
      get scheduledEpochMillis() { return row.scheduled_epoch_millis; },
      set scheduledEpochMillis(v) { storage.db.prepare('UPDATE occurrences SET scheduled_epoch_millis = ? WHERE id = ?').run(v, row.id); },
      get status() { return row.status; },
      set status(v) { storage.db.prepare('UPDATE occurrences SET status = ? WHERE id = ?').run(v, row.id); },
      get actionTimestamp() { return row.action_timestamp; },
      set actionTimestamp(v) { storage.db.prepare('UPDATE occurrences SET action_timestamp = ? WHERE id = ?').run(v, row.id); },
      actionNote: row.action_note
    }));
  }
};

// Auth Middleware
function authMiddleware(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized. Please sign in.' });
  }
  const token = authHeader.split(' ')[1];
  const caregiver = storage.getCaregiverByToken(token);
  if (!caregiver) {
    return res.status(401).json({ error: 'Session expired. Please sign in again.' });
  }
  req.caregiver = caregiver;
  req.token = token;
  next();
}

// ======================== AUTH ROUTES ========================

/**
 * POST /api/auth/register
 * Simple caregiver registration with email and password
 */
app.post('/api/auth/register', (req, res) => {
  const { email, password, name } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error: 'Email and password are required.' });
  }
  if (password.length < 4) {
    return res.status(400).json({ error: 'Password must be at least 4 characters.' });
  }
  try {
    const result = storage.registerOrLoginCaregiver(email, password, name);
    const linkedPatient = storage.getLinkedPatient(result.caregiver.id);
    return res.status(201).json({
      success: true,
      token: result.token,
      caregiver: result.caregiver,
      linkedPatient: linkedPatient ? { id: linkedPatient.id, name: linkedPatient.name } : null
    });
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }
});

/**
 * POST /api/auth/login
 * Simple caregiver email login (creates account automatically if email is new)
 */
app.post('/api/auth/login', (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error: 'Email and password are required.' });
  }
  try {
    const result = storage.registerOrLoginCaregiver(email, password);
    const linkedPatient = storage.getLinkedPatient(result.caregiver.id);
    return res.json({
      success: true,
      token: result.token,
      caregiver: result.caregiver,
      isNewAccount: result.isNew,
      linkedPatient: linkedPatient ? { id: linkedPatient.id, name: linkedPatient.name } : null
    });
  } catch (err) {
    return res.status(401).json({ error: err.message || 'Invalid email or password.' });
  }
});

/**
 * GET /api/auth/me
 * Retrieves current caregiver session and currently linked patient
 */
app.get('/api/auth/me', authMiddleware, (req, res) => {
  const linkedPatient = storage.getLinkedPatient(req.caregiver.id);
  return res.json({
    caregiver: req.caregiver,
    linkedPatient: linkedPatient ? { id: linkedPatient.id, name: linkedPatient.name } : null
  });
});

/**
 * POST /api/auth/logout
 */
app.post('/api/auth/logout', (req, res) => {
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.split(' ')[1];
    storage.db.prepare('DELETE FROM sessions WHERE token = ?').run(token);
  }
  return res.json({ success: true });
});

// ======================== PAIRING & CODES ========================

/**
 * POST /api/access-codes/generate (or /api/pairing/create)
 * Called by the Android mobile app when patient taps "Share Caregiver Code"
 */
app.post('/api/access-codes/generate', (req, res) => {
  const { patientId, patientName, plainCode = null } = req.body;
  if (!patientId || !patientName) {
    return res.status(400).json({ error: 'patientId and patientName are required' });
  }
  
  const created = storage.createAccessCode(patientId, patientName, plainCode);

  return res.status(201).json({
    success: true,
    code: created.code,
    patientId: created.patientId,
    patientName: created.patientName,
    createdAt: new Date().toISOString(),
    securityNotice: 'This code is hashed server-side and shown only once. Share with trusted caregivers.'
  });
});

/**
 * POST /api/access-codes/verify
 * Validates a code by checking the database
 */
app.post('/api/access-codes/verify', (req, res) => {
  const { code } = req.body;
  if (!code) {
    return res.status(400).json({ error: 'Access code is required' });
  }

  const record = storage.verifyAccessCode(code);
  if (!record) {
    return res.status(401).json({
      valid: false,
      error: 'Invalid or expired access code. Please check the code generated in the patient app.'
    });
  }

  return res.json({
    valid: true,
    patientId: record.patient_id,
    patientName: record.patient_name
  });
});

/**
 * POST /api/pairing/link
 * Links an authenticated caregiver with a patient via code.
 * As soon as codes match, links patient and returns full dashboard immediately!
 */
app.post('/api/pairing/link', authMiddleware, (req, res) => {
  const { code } = req.body;
  if (!code) {
    return res.status(400).json({ error: 'Code is required' });
  }

  const record = storage.verifyAccessCode(code);
  if (!record) {
    return res.status(401).json({ error: 'Invalid or unrecognized pairing code. Check the code in the patient mobile app.' });
  }

  // Link caregiver with patient in SQLite
  storage.linkCaregiverToPatient(req.caregiver.id, record.patient_id);

  // Return the live dashboard immediately!
  const dashboard = storage.getPatientDashboard(record.patient_id);
  return res.json({
    success: true,
    message: `Connected to ${record.patient_name}'s mobile device.`,
    patient: { id: record.patient_id, name: record.patient_name },
    dashboard
  });
});

/**
 * POST /api/pairing/unlink
 * Unlinks patient from caregiver
 */
app.post('/api/pairing/unlink', authMiddleware, (req, res) => {
  storage.unlinkCaregiver(req.caregiver.id);
  return res.json({ success: true, message: 'Unlinked patient successfully.' });
});

// ======================== SYNC ROUTES (ANDROID APP) ========================

/**
 * POST /api/sync/patient-state
 * Endpoint for Android app to push real medication schedules, profile, and occurrences
 */
app.post('/api/sync/patient-state', (req, res) => {
  const { patientId, patientName, deviceName, timeZone, schedules, occurrences, pairingCode } = req.body;

  if (!patientId) {
    return res.status(400).json({ error: 'patientId is required' });
  }

  // Upsert patient profile, schedules, occurrences into SQLite
  const result = storage.upsertPatientState({
    patientId,
    patientName,
    deviceName,
    timeZone,
    schedules,
    occurrences
  });

  // If a pairing code was provided or requested, ensure it is registered
  if (pairingCode) {
    storage.createAccessCode(patientId, patientName, pairingCode);
  }

  return res.json({
    success: true,
    lastSyncedAt: result.lastSyncedAt,
    message: 'Patient state synchronized successfully.'
  });
});

/**
 * POST /api/sync/events
 * Endpoint for Android OutboxSyncEngine to deliver queued dose actions & heartbeats
 */
app.post('/api/sync/events', (req, res) => {
  const { patientId, events = [] } = req.body;
  if (!patientId) {
    return res.status(400).json({ error: 'patientId is required' });
  }
  
  const result = storage.applySyncEvents(patientId, events);
  if (!result) {
    return res.status(404).json({ error: 'Patient not found' });
  }

  return res.json({
    success: true,
    processedEvents: events.length,
    appliedDoseUpdates: result.appliedCount,
    lastSyncedAt: result.lastSyncedAt
  });
});

// ======================== DASHBOARD ROUTES ========================

/**
 * GET /api/dashboard
 * Authenticated endpoint: returns dashboard for caregiver's currently linked patient
 */
app.get('/api/dashboard', authMiddleware, (req, res) => {
  const linkedPatient = storage.getLinkedPatient(req.caregiver.id);
  if (!linkedPatient) {
    return res.status(200).json({
      hasPatient: false,
      message: 'No patient linked yet. Please enter a pairing code from the mobile app.'
    });
  }

  const dashboard = storage.getPatientDashboard(linkedPatient.id);
  if (!dashboard) {
    return res.status(404).json({ error: 'Patient records not found.' });
  }

  return res.json({
    hasPatient: true,
    ...dashboard
  });
});

/**
 * GET /api/dashboard/:code
 * Backward-compatible & direct code inspection endpoint
 */
app.get('/api/dashboard/:code', (req, res) => {
  const code = req.params.code;
  const record = storage.verifyAccessCode(code);

  if (!record) {
    return res.status(401).json({ error: 'Invalid access code' });
  }

  const dashboard = storage.getPatientDashboard(record.patient_id);
  if (!dashboard) {
    return res.status(404).json({ error: 'Patient records not found' });
  }

  return res.json(dashboard);
});

// Fallback to SPA index.html
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Start listening if run directly
if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`DoseFlow Caregiver Portal running on http://localhost:${PORT}`);
    console.log(`Persistent SQLite database active at data/doseflow.db`);
  });
}

module.exports = { app, hashAccessCode, db, storage };
