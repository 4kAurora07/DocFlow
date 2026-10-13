process.env.NODE_ENV = 'test';
/**
 * Backend API & Cryptographic Security Test Suite
 * DoseFlow Caregiver Portal
 */

const assert = require('assert');
const http = require('http');
const { app, hashAccessCode, storage } = require('../server');

let server;
const PORT = 3987;
const BASE_URL = `http://127.0.0.1:${PORT}`;

function makeRequest(method, path, body = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    const options = {
      method,
      headers: {
        'Content-Type': 'application/json'
      }
    };

    const req = http.request(url, options, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        try {
          const parsed = data ? JSON.parse(data) : {};
          resolve({ status: res.statusCode, body: parsed, raw: data });
        } catch (e) {
          resolve({ status: res.statusCode, body: data, raw: data });
        }
      });
    });

    req.on('error', reject);
    if (body) {
      req.write(JSON.stringify(body));
    }
    req.end();
  });
}

async function runTests() {
  console.log('--- Starting DoseFlow Portal Backend Tests ---');
  let passed = 0;
  let failed = 0;

  function test(name, fn) {
    return (async () => {
      try {
        await fn();
        console.log(`  ✓ ${name}`);
        passed++;
      } catch (err) {
        console.error(`  ✗ ${name}:`, err.message);
        failed++;
      }
    })();
  }

  // Start temporary server
  await new Promise(resolve => {
    server = app.listen(PORT, resolve);
  });

  try {
    // 1. Cryptographic Hashing
    await test('Access Code Hashing is deterministic and case-insensitive', () => {
      const hash1 = hashAccessCode('DF-8492');
      const hash2 = hashAccessCode('df-8492');
      const hash3 = hashAccessCode('  DF-8492  ');
      const hashDifferent = hashAccessCode('DF-9999');

      assert.strictEqual(hash1, hash2, 'Hash must be case-insensitive');
      assert.strictEqual(hash1, hash3, 'Hash must trim surrounding whitespace');
      assert.notStrictEqual(hash1, hashDifferent, 'Different codes must produce distinct hashes');
      assert.strictEqual(typeof hash1, 'string');
      assert.strictEqual(hash1.length, 64, 'SHA-256 HMAC must be 64 hex characters');
    });

    // 2. Generate Access Code & Verify Storage
    let generatedCode = '';
    await test('POST /api/access-codes/generate creates new code and stores hash', async () => {
      const res = await makeRequest('POST', '/api/access-codes/generate', {
        patientId: 'patient_test_001',
        patientName: 'Test Patient'
      });

      assert.strictEqual(res.status, 201);
      assert.ok(res.body.code.startsWith('DF-'), 'Code must start with DF- prefix');
      generatedCode = res.body.code;
      const generatedHash = hashAccessCode(generatedCode);
      const row = storage.db.prepare('SELECT * FROM access_codes WHERE code_hash = ?').get(generatedHash);
      assert.ok(row, 'Database must contain hash of generated code');
    });

    await test('Plaintext access codes are NEVER stored as primary key', () => {
      const rawRow = storage.db.prepare('SELECT * FROM access_codes WHERE code_hash = ?').get(generatedCode);
      assert.strictEqual(rawRow, undefined, 'Database must NOT store plaintext code as key');
    });

    // 3. Verify Access Code
    await test('POST /api/access-codes/verify validates correct code', async () => {
      const res = await makeRequest('POST', '/api/access-codes/verify', { code: generatedCode });
      assert.strictEqual(res.status, 200);
      assert.strictEqual(res.body.valid, true);
      assert.strictEqual(res.body.patientName, 'Test Patient');
    });

    await test('POST /api/access-codes/verify rejects invalid code with 401', async () => {
      const res = await makeRequest('POST', '/api/access-codes/verify', { code: 'INVALID-CODE' });
      assert.strictEqual(res.status, 401);
      assert.strictEqual(res.body.valid, false);
    });

    // 4. Sync Patient State & Dashboard Read-Only
    await test('Sync patient state and verify GET /api/dashboard/:code enforces Read-Only flag', async () => {
      const syncRes = await makeRequest('POST', '/api/sync/patient-state', {
        patientId: 'patient_test_001',
        patientName: 'Test Patient',
        deviceName: 'Pixel 8',
        timeZone: 'America/New_York',
        pairingCode: generatedCode,
        schedules: [
          { id: 'sched_test_1', medicineName: 'Metformin', dosage: '500mg', time: '08:00 AM' }
        ],
        occurrences: [
          {
            id: 'occ_test_1',
            scheduleId: 'sched_test_1',
            medicineName: 'Metformin',
            dosage: '500mg',
            scheduledTime: '08:00 AM',
            scheduledEpochMillis: Date.now() - 60000,
            status: 'TAKEN',
            actionTimestamp: Date.now() - 30000
          }
        ]
      });
      assert.strictEqual(syncRes.status, 200);

      const res = await makeRequest('GET', `/api/dashboard/${generatedCode}`);
      assert.strictEqual(res.status, 200);
      assert.strictEqual(res.body.isReadOnly, true, 'Dashboard must be strictly read-only');
      assert.strictEqual(res.body.patient.name, 'Test Patient');
      assert.strictEqual(res.body.todaySchedule.length, 1);
      assert.strictEqual(res.body.todaySchedule[0].medicineName, 'Metformin');
    });

    // 5. Mobile Outbox Event Sync Ingestion
    await test('POST /api/sync/events ingests mobile batch and updates occurrences', async () => {
      const syncPayload = {
        patientId: 'patient_test_001',
        events: [
          {
            id: 'evt_sync_001',
            eventType: 'DOSE_TAKEN',
            aggregateId: 'occ_test_1',
            payloadJson: JSON.stringify({
              occurrenceId: 'occ_test_1',
              status: 'TAKEN',
              timestamp: Date.now()
            })
          }
        ]
      };

      const res = await makeRequest('POST', '/api/sync/events', syncPayload);
      assert.strictEqual(res.status, 200);
      assert.strictEqual(res.body.success, true);
      assert.strictEqual(res.body.processedEvents, 1);

      const dash = await makeRequest('GET', `/api/dashboard/${generatedCode}`);
      assert.strictEqual(dash.body.deviceStatus.isStale, false);
      const dose = dash.body.todaySchedule.find(d => d.id === 'occ_test_1');
      assert.strictEqual(dose.status, 'TAKEN');
    });

    // 6. Security & Scaffolding: Dynamic generation produces unique credentials, invalid codes rejected
    await test('Security & Scaffolding: Dynamic generator produces unique credentials and unseeded codes return 401', async () => {
      const gen1 = await makeRequest('POST', '/api/access-codes/generate', { patientId: 'patient_test_001', patientName: 'Test Patient' });
      const gen2 = await makeRequest('POST', '/api/access-codes/generate', { patientId: 'patient_test_001', patientName: 'Test Patient' });
      assert.notStrictEqual(gen1.body.code, gen2.body.code, 'Dynamically generated codes must be distinct');

      const dash1 = await makeRequest('GET', `/api/dashboard/${gen1.body.code}`);
      const dash2 = await makeRequest('GET', `/api/dashboard/${gen2.body.code}`);
      assert.strictEqual(dash1.status, 200);
      assert.strictEqual(dash2.status, 200);

      const invalid = await makeRequest('GET', '/api/dashboard/DF-0000');
      assert.strictEqual(invalid.status, 401);
    });

    // 7. Caregiver Email Login & Pairing Flow
    await test('Caregiver Email Login, Pairing Code Matching & Immediate Data Delivery', async () => {
      const authRes = await makeRequest('POST', '/api/auth/login', {
        email: 'caregiver.test@doseflow.org',
        password: 'securepassword123'
      });
      assert.strictEqual(authRes.status, 200);
      assert.ok(authRes.body.token, 'Must return authentication session token');
      const token = authRes.body.token;

      function makeAuthRequest(method, path, body = null) {
        return new Promise((resolve, reject) => {
          const url = new URL(path, BASE_URL);
          const req = http.request(url, {
            method,
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${token}`
            }
          }, (res) => {
            let data = '';
            res.on('data', chunk => { data += chunk; });
            res.on('end', () => {
              try { resolve({ status: res.statusCode, body: JSON.parse(data) }); }
              catch (e) { resolve({ status: res.statusCode, body: data }); }
            });
          });
          req.on('error', reject);
          if (body) req.write(JSON.stringify(body));
          req.end();
        });
      }

      // Check me - initially no patient linked
      const meRes = await makeAuthRequest('GET', '/api/auth/me');
      assert.strictEqual(meRes.status, 200);
      assert.strictEqual(meRes.body.caregiver.email, 'caregiver.test@doseflow.org');

      // Sync custom real patient data from app
      const customPatientId = 'patient_real_user_99';
      const syncRes = await makeRequest('POST', '/api/sync/patient-state', {
        patientId: customPatientId,
        patientName: 'Jane Doe',
        deviceName: 'Google Pixel 8',
        timeZone: 'America/Chicago',
        pairingCode: 'DF-9999',
        schedules: [
          { id: 'sched_custom_1', medicineName: 'Amlodipine', dosage: '5mg', time: '09:00 AM' }
        ],
        occurrences: [
          {
            id: 'occ_custom_1',
            scheduleId: 'sched_custom_1',
            medicineName: 'Amlodipine',
            dosage: '5mg',
            scheduledTime: '09:00 AM',
            scheduledEpochMillis: Date.now(),
            status: 'TAKEN',
            actionTimestamp: Date.now()
          }
        ]
      });
      assert.strictEqual(syncRes.status, 200);

      // Now caregiver links code DF-9999 from website
      const linkRes = await makeAuthRequest('POST', '/api/pairing/link', { code: 'DF-9999' });
      assert.strictEqual(linkRes.status, 200);
      assert.strictEqual(linkRes.body.success, true);
      assert.strictEqual(linkRes.body.patient.name, 'Jane Doe');
      // Verify data is shown immediately in response!
      assert.strictEqual(linkRes.body.dashboard.patient.name, 'Jane Doe');
      assert.strictEqual(linkRes.body.dashboard.todaySchedule[0].medicineName, 'Amlodipine');
      assert.strictEqual(linkRes.body.dashboard.todaySchedule[0].status, 'TAKEN');

      // Now GET /api/dashboard returns Jane Doe's real data immediately
      const dashRes = await makeAuthRequest('GET', '/api/dashboard');
      assert.strictEqual(dashRes.status, 200);
      assert.strictEqual(dashRes.body.hasPatient, true);
      assert.strictEqual(dashRes.body.patient.name, 'Jane Doe');
      assert.strictEqual(dashRes.body.todaySchedule[0].medicineName, 'Amlodipine');
    });

  } finally {
    server.close();
  }

  console.log(`\nTest Summary: ${passed} passed, ${failed} failed.`);
  if (failed > 0) {
    process.exit(1);
  }
}

runTests().catch(err => {
  console.error('Fatal test runner error:', err);
  process.exit(1);
});
