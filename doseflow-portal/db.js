const { DatabaseSync } = require('node:sqlite');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

const dataDir = path.join(__dirname, 'data');
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}

const dbPath = path.join(dataDir, 'doseflow.db');
const db = new DatabaseSync(dbPath);

// Enable WAL mode & foreign keys for robustness
db.exec('PRAGMA journal_mode = WAL;');
db.exec('PRAGMA foreign_keys = ON;');

// Initialize Schema
db.exec(`
  CREATE TABLE IF NOT EXISTS caregivers (
    id TEXT PRIMARY KEY,
    email TEXT UNIQUE COLLATE NOCASE NOT NULL,
    password_hash TEXT NOT NULL,
    name TEXT,
    created_at INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS sessions (
    token TEXT PRIMARY KEY,
    caregiver_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    FOREIGN KEY(caregiver_id) REFERENCES caregivers(id) ON DELETE CASCADE
  );

  CREATE TABLE IF NOT EXISTS patients (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    age INTEGER DEFAULT 0,
    time_zone TEXT DEFAULT 'America/New_York',
    primary_device TEXT DEFAULT 'Android Device',
    last_synced_at INTEGER NOT NULL,
    simulated_state TEXT DEFAULT 'ONLINE',
    created_at INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS access_codes (
    code_hash TEXT PRIMARY KEY,
    patient_id TEXT NOT NULL,
    patient_name TEXT NOT NULL,
    plain_preview TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(patient_id) REFERENCES patients(id) ON DELETE CASCADE
  );

  CREATE TABLE IF NOT EXISTS caregiver_patients (
    caregiver_id TEXT NOT NULL,
    patient_id TEXT NOT NULL,
    linked_at INTEGER NOT NULL,
    PRIMARY KEY(caregiver_id, patient_id),
    FOREIGN KEY(caregiver_id) REFERENCES caregivers(id) ON DELETE CASCADE,
    FOREIGN KEY(patient_id) REFERENCES patients(id) ON DELETE CASCADE
  );

  CREATE TABLE IF NOT EXISTS schedules (
    id TEXT PRIMARY KEY,
    patient_id TEXT NOT NULL,
    medicine_name TEXT NOT NULL,
    dosage TEXT DEFAULT '',
    unit TEXT DEFAULT '',
    instructions TEXT DEFAULT '',
    time TEXT DEFAULT '',
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(patient_id) REFERENCES patients(id) ON DELETE CASCADE
  );

  CREATE TABLE IF NOT EXISTS occurrences (
    id TEXT PRIMARY KEY,
    patient_id TEXT NOT NULL,
    schedule_id TEXT,
    medicine_name TEXT NOT NULL,
    dosage TEXT DEFAULT '',
    instructions TEXT DEFAULT '',
    scheduled_time TEXT,
    scheduled_epoch_millis INTEGER NOT NULL,
    status TEXT NOT NULL,
    action_timestamp INTEGER,
    action_note TEXT,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(patient_id) REFERENCES patients(id) ON DELETE CASCADE
  );

  CREATE INDEX IF NOT EXISTS idx_occurrences_patient ON occurrences(patient_id, scheduled_epoch_millis);
`);

// Password helper
function hashPassword(password) {
  return crypto.pbkdf2Sync(password, 'doseflow_auth_salt_v1', 10000, 32, 'sha256').toString('hex');
}

// Access Code helper
const SALT = process.env.SALT || 'doseflow_caregiver_secure_salt_v1';
function hashAccessCode(code) {
  if (!code) return '';
  return crypto.createHmac('sha256', SALT).update(code.trim().toUpperCase()).digest('hex');
}

// Caregiver helpers
function registerOrLoginCaregiver(email, password, name = '') {
  const normalizedEmail = email.trim().toLowerCase();
  const pwdHash = hashPassword(password);
  
  const existing = db.prepare('SELECT * FROM caregivers WHERE email = ?').get(normalizedEmail);
  if (existing) {
    if (existing.password_hash !== pwdHash) {
      throw new Error('Invalid credentials');
    }
    const token = crypto.randomBytes(24).toString('hex');
    const now = Date.now();
    db.prepare('INSERT INTO sessions (token, caregiver_id, created_at, expires_at) VALUES (?, ?, ?, ?)').run(
      token, existing.id, now, now + (30 * 24 * 60 * 60 * 1000)
    );
    return { caregiver: { id: existing.id, email: existing.email, name: existing.name }, token, isNew: false };
  }

  // Register new caregiver
  const id = 'cg_' + crypto.randomUUID();
  const now = Date.now();
  const displayName = name.trim() || normalizedEmail.split('@')[0];
  db.prepare('INSERT INTO caregivers (id, email, password_hash, name, created_at) VALUES (?, ?, ?, ?, ?)').run(
    id, normalizedEmail, pwdHash, displayName, now
  );

  const token = crypto.randomBytes(24).toString('hex');
  db.prepare('INSERT INTO sessions (token, caregiver_id, created_at, expires_at) VALUES (?, ?, ?, ?)').run(
    token, id, now, now + (30 * 24 * 60 * 60 * 1000)
  );

  return { caregiver: { id, email: normalizedEmail, name: displayName }, token, isNew: true };
}

function getCaregiverByToken(token) {
  if (!token) return null;
  const now = Date.now();
  const session = db.prepare('SELECT * FROM sessions WHERE token = ? AND expires_at > ?').get(token, now);
  if (!session) return null;
  const caregiver = db.prepare('SELECT id, email, name, created_at FROM caregivers WHERE id = ?').get(session.caregiver_id);
  return caregiver;
}

function getLinkedPatient(caregiverId) {
  const link = db.prepare(`
    SELECT p.* FROM patients p
    JOIN caregiver_patients cp ON cp.patient_id = p.id
    WHERE cp.caregiver_id = ?
    ORDER BY cp.linked_at DESC LIMIT 1
  `).get(caregiverId);
  return link || null;
}

function linkCaregiverToPatient(caregiverId, patientId) {
  const now = Date.now();
  db.prepare(`
    INSERT OR REPLACE INTO caregiver_patients (caregiver_id, patient_id, linked_at)
    VALUES (?, ?, ?)
  `).run(caregiverId, patientId, now);
}

function unlinkCaregiver(caregiverId, patientId) {
  if (patientId) {
    db.prepare('DELETE FROM caregiver_patients WHERE caregiver_id = ? AND patient_id = ?').run(caregiverId, patientId);
  } else {
    db.prepare('DELETE FROM caregiver_patients WHERE caregiver_id = ?').run(caregiverId);
  }
}

// Access code generator & verifier
function createAccessCode(patientId, patientName, plainCode = null) {
  const code = plainCode || ('DF-' + Math.floor(1000 + Math.random() * 9000));
  const hashed = hashAccessCode(code);
  const now = Date.now();

  // Ensure patient exists in patients table
  const existingPatient = db.prepare('SELECT id FROM patients WHERE id = ?').get(patientId);
  if (!existingPatient) {
    db.prepare(`
      INSERT INTO patients (id, name, age, time_zone, primary_device, last_synced_at, simulated_state, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(patientId, patientName || 'Patient', 0, 'America/New_York', 'Android Device', now, 'ONLINE', now);
  } else if (patientName) {
    db.prepare('UPDATE patients SET name = ? WHERE id = ?').run(patientName, patientId);
  }

  db.prepare(`
    INSERT OR REPLACE INTO access_codes (code_hash, patient_id, patient_name, plain_preview, created_at)
    VALUES (?, ?, ?, ?, ?)
  `).run(hashed, patientId, patientName || 'Patient', code, now);

  return { code, hashed, patientId, patientName: patientName || 'Patient' };
}

function verifyAccessCode(code) {
  if (!code) return null;
  const hashed = hashAccessCode(code);
  const record = db.prepare('SELECT * FROM access_codes WHERE code_hash = ?').get(hashed);
  if (!record) return null;
  const patient = db.prepare('SELECT * FROM patients WHERE id = ?').get(record.patient_id);
  return { ...record, patient };
}

// Upsert patient state from Android app
function upsertPatientState(data) {
  const {
    patientId = ('patient_' + Date.now()),
    patientName = 'Patient',
    deviceName = 'Android Device',
    timeZone = 'UTC',
    schedules = [],
    occurrences = []
  } = data;

  const now = Date.now();
  const existing = db.prepare('SELECT id FROM patients WHERE id = ?').get(patientId);

  if (!existing) {
    db.prepare(`
      INSERT INTO patients (id, name, age, time_zone, primary_device, last_synced_at, simulated_state, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(patientId, patientName, null, timeZone, deviceName, now, 'ONLINE', now);
  } else {
    db.prepare(`
      UPDATE patients
      SET name = ?, primary_device = ?, time_zone = ?, last_synced_at = ?, simulated_state = 'ONLINE'
      WHERE id = ?
    `).run(patientName, deviceName, timeZone, now, patientId);
  }

  if (data.pairingCode) {
    createAccessCode(patientId, patientName, data.pairingCode);
  }

  // Upsert schedules
  if (Array.isArray(schedules) && schedules.length > 0) {
    const upsertSched = db.prepare(`
      INSERT INTO schedules (id, patient_id, medicine_name, dosage, unit, instructions, time, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        medicine_name=excluded.medicine_name,
        dosage=excluded.dosage,
        unit=excluded.unit,
        instructions=excluded.instructions,
        time=excluded.time,
        updated_at=excluded.updated_at
    `);
    for (const s of schedules) {
      upsertSched.run(
        s.id, patientId, s.medicineName || s.name, s.dosage || '', s.unit || '',
        s.instructions || '', s.time || '', now
      );
    }
  }

  // Upsert occurrences
  if (Array.isArray(occurrences) && occurrences.length > 0) {
    const upsertOcc = db.prepare(`
      INSERT INTO occurrences (
        id, patient_id, schedule_id, medicine_name, dosage, instructions,
        scheduled_time, scheduled_epoch_millis, status, action_timestamp, action_note, updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        patient_id=excluded.patient_id,
        schedule_id=excluded.schedule_id,
        medicine_name=excluded.medicine_name,
        dosage=excluded.dosage,
        instructions=excluded.instructions,
        scheduled_time=excluded.scheduled_time,
        scheduled_epoch_millis=excluded.scheduled_epoch_millis,
        status=excluded.status,
        action_timestamp=excluded.action_timestamp,
        action_note=excluded.action_note,
        updated_at=excluded.updated_at
    `);
    for (const o of occurrences) {
      upsertOcc.run(
        o.id, patientId, o.scheduleId || null, o.medicineName || '', o.dosage || '',
        o.instructions || '', o.scheduledTime || '', o.scheduledEpochMillis || now,
        o.status || 'PENDING', o.actionTimestamp || null, o.actionNote || null, now
      );
    }
  }

  return { success: true, lastSyncedAt: now };
}

// Ingest Sync Events from Android Outbox Engine
function applySyncEvents(patientId, events) {
  const patient = db.prepare('SELECT * FROM patients WHERE id = ?').get(patientId);
  if (!patient) return null;

  const now = Date.now();
  db.prepare("UPDATE patients SET last_synced_at = ?, simulated_state = 'ONLINE' WHERE id = ?").run(now, patientId);

  let appliedCount = 0;
  for (const event of events) {
    if (event.eventType === 'DOSE_TAKEN' || event.eventType === 'DOSE_SKIPPED' || event.eventType === 'DOSE_MISSED') {
      try {
        const payload = JSON.parse(event.payloadJson || '{}');
        const occId = event.aggregateId || payload.occurrenceId;
        const status = payload.status || event.eventType.replace('DOSE_', '');
        const actionTimestamp = payload.timestamp || now;
        const note = payload.note || null;

        const res = db.prepare(`
          UPDATE occurrences
          SET status = ?, action_timestamp = ?, action_note = COALESCE(?, action_note), updated_at = ?
          WHERE id = ? AND patient_id = ?
        `).run(status, actionTimestamp, note, now, occId, patientId);

        if (res.changes > 0) {
          appliedCount++;
        }
      } catch (e) {
        // Continue
      }
    }
  }

  return { success: true, appliedCount, lastSyncedAt: now };
}

// Compute dynamic dashboard for a patient
function getPatientDashboard(patientId) {
  const patient = db.prepare('SELECT * FROM patients WHERE id = ?').get(patientId);
  if (!patient) return null;

  const now = Date.now();
  const stalenessMs = now - patient.last_synced_at;
  const stalenessMinutes = Math.floor(stalenessMs / 60000);
  const isStale = stalenessMinutes >= 15 || patient.simulated_state === 'STALE' || patient.simulated_state === 'DEAD_LETTER';

  // Occurrences
  const rawOccurrences = db.prepare(`
    SELECT * FROM occurrences
    WHERE patient_id = ?
    ORDER BY scheduled_epoch_millis ASC
  `).all(patientId);

  const tz = patient.time_zone || 'UTC';

  // Format today's date string in patient's timezone (YYYY-MM-DD)
  let todayDateStr;
  try {
    todayDateStr = new Intl.DateTimeFormat('en-CA', { timeZone: tz }).format(new Date(now));
  } catch (e) {
    todayDateStr = new Date(now).toISOString().split('T')[0];
  }

  // Filter ONLY occurrences scheduled for today
  const todayRawOccurrences = rawOccurrences.filter(occ => {
    try {
      const occDateStr = new Intl.DateTimeFormat('en-CA', { timeZone: tz }).format(new Date(occ.scheduled_epoch_millis));
      return occDateStr === todayDateStr;
    } catch (e) {
      return Math.abs(occ.scheduled_epoch_millis - now) < (12 * 60 * 60 * 1000);
    }
  });

  const todaySchedule = todayRawOccurrences.map(occ => {
    let displayStatus = occ.status;
    let freshnessWarning = null;

    if (isStale && occ.scheduled_epoch_millis <= now && occ.status === 'PENDING') {
      displayStatus = 'AWAITING_SYNC';
      freshnessWarning = 'Device offline during dose window; awaiting verification';
    }

    return {
      id: occ.id,
      scheduleId: occ.schedule_id,
      medicineName: occ.medicine_name,
      dosage: occ.dosage,
      instructions: occ.instructions,
      scheduledTime: occ.scheduled_time,
      scheduledEpochMillis: occ.scheduled_epoch_millis,
      status: occ.status,
      displayStatus,
      freshnessWarning,
      actionTimestamp: occ.action_timestamp,
      actionNote: occ.action_note
    };
  });

  // Calculate adherence from occurrences
  const sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000);
  const thirtyDaysAgo = now - (30 * 24 * 60 * 60 * 1000);

  function calcAdherence(sinceEpoch) {
    const list = rawOccurrences.filter(o => o.scheduled_epoch_millis >= sinceEpoch && o.scheduled_epoch_millis <= now);
    const taken = list.filter(o => o.status === 'TAKEN').length;
    const skipped = list.filter(o => o.status === 'SKIPPED').length;
    const missed = list.filter(o => o.status === 'MISSED').length;
    const totalSched = list.length;
    const finished = taken + skipped + missed;
    const rate = finished > 0 ? Number(((taken / finished) * 100).toFixed(1)) : 100.0;
    return {
      adherenceRate: rate,
      taken,
      skipped,
      missed,
      totalScheduled: totalSched
    };
  }

  const sevenDay = calcAdherence(sevenDaysAgo);
  const thirtyDay = calcAdherence(thirtyDaysAgo);

  // History logs
  const historyLogs = rawOccurrences
    .filter(o => o.status !== 'PENDING')
    .sort((a, b) => (b.action_timestamp || b.scheduled_epoch_millis) - (a.action_timestamp || a.scheduled_epoch_millis))
    .slice(0, 20)
    .map(o => {
      const actTime = o.action_timestamp ? new Date(o.action_timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : (o.status === 'MISSED' ? 'Missed' : 'Skipped');
      const dateStr = new Date(o.scheduled_epoch_millis).toLocaleDateString([], { month: 'short', day: 'numeric' });
      return {
        date: dateStr,
        medicine: `${o.medicine_name} ${o.dosage}`.trim(),
        scheduled: o.scheduled_time || 'Scheduled',
        recorded: actTime,
        status: o.status,
        note: o.action_note
      };
    });

  return {
    patient: {
      id: patient.id,
      name: patient.name,
      age: patient.age,
      timeZone: patient.time_zone,
      primaryDevice: patient.primary_device
    },
    deviceStatus: {
      lastSyncedAtEpochMillis: patient.last_synced_at,
      lastSyncedAtIso: new Date(patient.last_synced_at).toISOString(),
      stalenessMinutes: Math.max(stalenessMinutes, patient.simulated_state === 'STALE' ? 45 : (patient.simulated_state === 'DEAD_LETTER' ? 720 : stalenessMinutes)),
      isStale,
      connectionState: patient.simulated_state,
      freshnessContract: {
        maxFreshnessWindowMinutes: 15,
        staleBannerRequired: isStale,
        statusText: isStale ? 'OFFLINE_STALE' : 'LIVE_SYNCED'
      }
    },
    todaySchedule,
    adherence: {
      sevenDay,
      thirtyDay
    },
    historyLogs,
    isReadOnly: true,
    serverTimestamp: now
  };
}

module.exports = {
  db,
  hashPassword,
  hashAccessCode,
  registerOrLoginCaregiver,
  getCaregiverByToken,
  getLinkedPatient,
  linkCaregiverToPatient,
  unlinkCaregiver,
  createAccessCode,
  verifyAccessCode,
  upsertPatientState,
  applySyncEvents,
  getPatientDashboard
};
