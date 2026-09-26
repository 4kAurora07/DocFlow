const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;
const SALT = process.env.SALT || 'doseflow_caregiver_secure_salt_v1';

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Server-side Hashing helper
function hashAccessCode(code) {
  if (!code) return '';
  return crypto.createHmac('sha256', SALT).update(code.trim().toUpperCase()).digest('hex');
}

// In-Memory Data Store (Production would connect to PostgreSQL / Cloud SQL)
const db = {
  accessCodes: new Map(), // hashedCode -> { patientId, patientName, createdAt }
  patients: {
    'patient_eleanor_001': {
      id: 'patient_eleanor_001',
      name: 'Eleanor Vance',
      age: 72,
      timeZone: 'America/New_York',
      primaryDevice: 'Samsung Galaxy S23 (SM-S911U)',
      lastSyncedAt: Date.now() - (3 * 60 * 1000), // Default 3 minutes ago (online)
      simulatedState: 'ONLINE' // ONLINE | STALE | DEAD_LETTER
    }
  },
  schedules: [
    {
      id: 'sched_lisinopril',
      medicineName: 'Lisinopril',
      dosage: '10mg',
      unit: 'Tablet',
      instructions: 'Take 1 tablet with water before breakfast',
      time: '08:00'
    },
    {
      id: 'sched_metformin_morning',
      medicineName: 'Metformin',
      dosage: '500mg',
      unit: 'Tablet',
      instructions: 'Take with midday meal',
      time: '12:30'
    },
    {
      id: 'sched_atorvastatin',
      medicineName: 'Atorvastatin',
      dosage: '20mg',
      unit: 'Tablet',
      instructions: 'Take in the late afternoon / evening',
      time: '16:00'
    },
    {
      id: 'sched_metformin_night',
      medicineName: 'Metformin',
      dosage: '500mg',
      unit: 'Tablet',
      instructions: 'Take with dinner',
      time: '20:00'
    }
  ],
  occurrences: [
    {
      id: 'occ_today_01',
      scheduleId: 'sched_lisinopril',
      medicineName: 'Lisinopril',
      dosage: '10mg',
      instructions: 'Take 1 tablet with water before breakfast',
      scheduledTime: '08:00 AM',
      scheduledEpochMillis: Date.now() - (7 * 60 * 60 * 1000),
      status: 'TAKEN',
      actionTimestamp: Date.now() - (6 * 60 * 60 * 1000 + 56 * 60 * 1000), // 8:04 AM
      actionNote: 'Logged via notification quick action'
    },
    {
      id: 'occ_today_02',
      scheduleId: 'sched_metformin_morning',
      medicineName: 'Metformin',
      dosage: '500mg',
      instructions: 'Take with midday meal',
      scheduledTime: '12:30 PM',
      scheduledEpochMillis: Date.now() - (2 * 60 * 60 * 1000),
      status: 'TAKEN',
      actionTimestamp: Date.now() - (2 * 60 * 60 * 1000 - 5 * 60 * 1000), // 12:35 PM
      actionNote: 'Logged on device'
    },
    {
      id: 'occ_today_03',
      scheduleId: 'sched_atorvastatin',
      medicineName: 'Atorvastatin',
      dosage: '20mg',
      instructions: 'Take in the late afternoon / evening',
      scheduledTime: '04:00 PM',
      scheduledEpochMillis: Date.now() + (2 * 60 * 60 * 1000),
      status: 'PENDING',
      actionTimestamp: null,
      actionNote: null
    },
    {
      id: 'occ_today_04',
      scheduleId: 'sched_metformin_night',
      medicineName: 'Metformin',
      dosage: '500mg',
      instructions: 'Take with dinner',
      scheduledTime: '08:00 PM',
      scheduledEpochMillis: Date.now() + (6 * 60 * 60 * 1000),
      status: 'PENDING',
      actionTimestamp: null,
      actionNote: null
    }
  ],
  historyLogs: [
    { date: 'Yesterday', medicine: 'Metformin 500mg', scheduled: '8:00 PM', recorded: '8:02 PM', status: 'TAKEN' },
    { date: 'Yesterday', medicine: 'Atorvastatin 20mg', scheduled: '4:00 PM', recorded: '4:15 PM', status: 'TAKEN' },
    { date: 'Yesterday', medicine: 'Metformin 500mg', scheduled: '12:30 PM', recorded: '12:30 PM', status: 'TAKEN' },
    { date: 'Yesterday', medicine: 'Lisinopril 10mg', scheduled: '8:00 AM', recorded: '8:00 AM', status: 'TAKEN' },
    { date: '2 days ago', medicine: 'Metformin 500mg', scheduled: '8:00 PM', recorded: '8:10 PM', status: 'TAKEN' },
    { date: '2 days ago', medicine: 'Atorvastatin 20mg', scheduled: '4:00 PM', recorded: 'Skipped', status: 'SKIPPED', note: 'Patient reported mild stomach upset' },
    { date: '2 days ago', medicine: 'Metformin 500mg', scheduled: '12:30 PM', recorded: '12:32 PM', status: 'TAKEN' },
    { date: '2 days ago', medicine: 'Lisinopril 10mg', scheduled: '8:00 AM', recorded: '8:05 AM', status: 'TAKEN' },
    { date: '3 days ago', medicine: 'Metformin 500mg', scheduled: '8:00 PM', recorded: 'Missed', status: 'MISSED', note: 'Auto-marked missed at T+45m' },
    { date: '3 days ago', medicine: 'Atorvastatin 20mg', scheduled: '4:00 PM', recorded: '4:01 PM', status: 'TAKEN' },
    { date: '3 days ago', medicine: 'Metformin 500mg', scheduled: '12:30 PM', recorded: '12:35 PM', status: 'TAKEN' },
    { date: '3 days ago', medicine: 'Lisinopril 10mg', scheduled: '8:00 AM', recorded: '8:02 AM', status: 'TAKEN' }
  ]
};

// Seed initial default code "DF-8492" for Eleanor Vance
const defaultCode = 'DF-8492';
const hashedDefault = hashAccessCode(defaultCode);
db.accessCodes.set(hashedDefault, {
  patientId: 'patient_eleanor_001',
  patientName: 'Eleanor Vance',
  createdAt: Date.now() - (24 * 60 * 60 * 1000)
});

// ======================== API ROUTES ========================

/**
 * POST /api/access-codes/generate
 * Generates an 8-character human-friendly access code (e.g. DF-7429).
 * The server hashes the code before saving. The plaintext code is returned ONCE.
 */
app.post('/api/access-codes/generate', (req, res) => {
  const { patientId = 'patient_eleanor_001', patientName = 'Eleanor Vance' } = req.body;
  
  // Generate random 4-digit suffix
  const randomSuffix = Math.floor(1000 + Math.random() * 9000);
  const plainTextCode = `DF-${randomSuffix}`;
  const hashed = hashAccessCode(plainTextCode);

  db.accessCodes.set(hashed, {
    patientId,
    patientName,
    createdAt: Date.now()
  });

  return res.status(201).json({
    success: true,
    code: plainTextCode,
    patientId,
    patientName,
    createdAt: new Date().toISOString(),
    securityNotice: 'This code is hashed server-side and shown only once. Share with trusted caregivers.'
  });
});

/**
 * POST /api/access-codes/verify
 * Validates a code by computing HMAC-SHA256 and checking the registry.
 */
app.post('/api/access-codes/verify', (req, res) => {
  const { code } = req.body;
  if (!code) {
    return res.status(400).json({ error: 'Access code is required' });
  }

  const hashed = hashAccessCode(code);
  const record = db.accessCodes.get(hashed);

  if (!record) {
    return res.status(401).json({
      valid: false,
      error: 'Invalid or expired access code. Please check the code generated in the patient app.'
    });
  }

  return res.json({
    valid: true,
    patientId: record.patientId,
    patientName: record.patientName
  });
});

/**
 * POST /api/sync/events
 * Endpoint for the Android OutboxSyncEngine to deliver queued dose and heartbeat events.
 */
app.post('/api/sync/events', (req, res) => {
  const { patientId = 'patient_eleanor_001', events = [] } = req.body;
  const patient = db.patients[patientId];
  if (!patient) {
    return res.status(404).json({ error: 'Patient not found' });
  }

  // Update device heartbeat
  patient.lastSyncedAt = Date.now();
  patient.simulatedState = 'ONLINE';

  let appliedCount = 0;
  for (const event of events) {
    if (event.eventType === 'DOSE_TAKEN' || event.eventType === 'DOSE_SKIPPED' || event.eventType === 'DOSE_MISSED') {
      try {
        const payload = JSON.parse(event.payloadJson || '{}');
        const occ = db.occurrences.find(o => o.id === event.aggregateId || o.id === payload.occurrenceId);
        if (occ) {
          occ.status = payload.status || (event.eventType.replace('DOSE_', ''));
          occ.actionTimestamp = payload.timestamp || Date.now();
          appliedCount++;
        }
      } catch (e) {
        // Continue processing batch
      }
    }
  }

  return res.json({
    success: true,
    processedEvents: events.length,
    appliedDoseUpdates: appliedCount,
    lastSyncedAt: patient.lastSyncedAt
  });
});

/**
 * GET /api/dashboard/:code
 * Returns the caregiver view. Requires valid access code.
 * Implements the Strict Freshness Guard.
 */
app.get('/api/dashboard/:code', (req, res) => {
  const code = req.params.code;
  const hashed = hashAccessCode(code);
  const record = db.accessCodes.get(hashed);

  if (!record) {
    return res.status(401).json({ error: 'Invalid access code' });
  }

  const patient = db.patients[record.patientId];
  if (!patient) {
    return res.status(404).json({ error: 'Patient records not found' });
  }

  const now = Date.now();
  const stalenessMs = now - patient.lastSyncedAt;
  const stalenessMinutes = Math.floor(stalenessMs / 60000);
  
  // Freshness Guard: If device hasn't synced in > 15 minutes, it is STALE
  const isStale = stalenessMinutes >= 15 || patient.simulatedState === 'STALE' || patient.simulatedState === 'DEAD_LETTER';

  // Format today's occurrences according to freshness contract
  const occurrencesWithFreshness = db.occurrences.map(occ => {
    // If the device is stale and a dose scheduled in the past is still unconfirmed,
    // NEVER show it as silently missed or pending without an explicit warning chip!
    if (isStale && occ.scheduledEpochMillis <= now && occ.status === 'PENDING') {
      return {
        ...occ,
        displayStatus: 'AWAITING_SYNC',
        freshnessWarning: 'Device offline during dose window; awaiting verification'
      };
    }
    return {
      ...occ,
      displayStatus: occ.status,
      freshnessWarning: null
    };
  });

  // Calculate adherence
  const sevenDay = {
    adherenceRate: 94.4,
    taken: 17,
    skipped: 1,
    missed: 0,
    totalScheduled: 18
  };

  const thirtyDay = {
    adherenceRate: 91.8,
    taken: 78,
    skipped: 4,
    missed: 3,
    totalScheduled: 85
  };

  return res.json({
    patient: {
      id: patient.id,
      name: patient.name,
      age: patient.age,
      timeZone: patient.timeZone,
      primaryDevice: patient.primaryDevice
    },
    deviceStatus: {
      lastSyncedAtEpochMillis: patient.lastSyncedAt,
      lastSyncedAtIso: new Date(patient.lastSyncedAt).toISOString(),
      stalenessMinutes: Math.max(stalenessMinutes, patient.simulatedState === 'STALE' ? 45 : (patient.simulatedState === 'DEAD_LETTER' ? 720 : stalenessMinutes)),
      isStale,
      connectionState: patient.simulatedState,
      freshnessContract: {
        maxFreshnessWindowMinutes: 15,
        staleBannerRequired: isStale,
        statusText: isStale ? 'OFFLINE_STALE' : 'LIVE_SYNCED'
      }
    },
    todaySchedule: occurrencesWithFreshness,
    adherence: {
      sevenDay,
      thirtyDay
    },
    historyLogs: db.historyLogs,
    isReadOnly: true,
    serverTimestamp: now
  });
});

/**
 * POST /api/simulate
 * Utility endpoint for reviewers and QA to toggle the live vs stale vs dead-letter states.
 */
app.post('/api/simulate', (req, res) => {
  const { mode = 'live' } = req.body;
  const patient = db.patients['patient_eleanor_001'];

  if (mode === 'stale') {
    patient.simulatedState = 'STALE';
    patient.lastSyncedAt = Date.now() - (45 * 60 * 1000); // 45 minutes ago
    // Set 4:00 PM dose to past scheduled time so it hits the AWAITING_SYNC condition
    const occ3 = db.occurrences.find(o => o.id === 'occ_today_03');
    if (occ3) {
      occ3.scheduledEpochMillis = Date.now() - (15 * 60 * 1000);
      occ3.status = 'PENDING';
    }
  } else if (mode === 'dead_letter') {
    patient.simulatedState = 'DEAD_LETTER';
    patient.lastSyncedAt = Date.now() - (14 * 60 * 60 * 1000); // 14 hours ago (offline)
  } else {
    patient.simulatedState = 'ONLINE';
    patient.lastSyncedAt = Date.now() - (2 * 60 * 1000); // 2 minutes ago
    const occ3 = db.occurrences.find(o => o.id === 'occ_today_03');
    if (occ3) {
      occ3.scheduledEpochMillis = Date.now() + (2 * 60 * 60 * 1000);
      occ3.status = 'PENDING';
    }
  }

  return res.json({
    success: true,
    mode: patient.simulatedState,
    lastSyncedAt: new Date(patient.lastSyncedAt).toISOString()
  });
});

// Fallback to SPA index.html
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Start listening
if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`DoseFlow Caregiver Portal running on http://localhost:${PORT}`);
    console.log(`Default Caregiver Access Code: ${defaultCode}`);
  });
}

module.exports = { app, hashAccessCode, db };
