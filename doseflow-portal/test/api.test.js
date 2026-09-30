/**
 * Backend API & Cryptographic Security Test Suite
 * DoseFlow Caregiver Portal
 */

const assert = require('assert');
const http = require('http');
const { app, hashAccessCode, db } = require('../server');

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

    await test('Plaintext access codes are NEVER stored in database registry', () => {
      const defaultHash = hashAccessCode('DF-8492');
      assert.ok(db.accessCodes.has(defaultHash), 'Database must store hashed code');
      assert.ok(!db.accessCodes.has('DF-8492'), 'Database must NOT store plaintext code as key');
    });

    // 2. Generate Access Code
    await test('POST /api/access-codes/generate creates new code and stores hash', async () => {
      const res = await makeRequest('POST', '/api/access-codes/generate', {
        patientId: 'patient_eleanor_001',
        patientName: 'Eleanor Vance'
      });

      assert.strictEqual(res.status, 201);
      assert.ok(res.body.code.startsWith('DF-'), 'Code must start with DF- prefix');
      const generatedHash = hashAccessCode(res.body.code);
      assert.ok(db.accessCodes.has(generatedHash), 'Database must contain hash of generated code');
    });

    // 3. Verify Access Code
    await test('POST /api/access-codes/verify validates correct code', async () => {
      const res = await makeRequest('POST', '/api/access-codes/verify', { code: 'DF-8492' });
      assert.strictEqual(res.status, 200);
      assert.strictEqual(res.body.valid, true);
      assert.strictEqual(res.body.patientName, 'Eleanor Vance');
    });

    await test('POST /api/access-codes/verify rejects invalid code with 401', async () => {
      const res = await makeRequest('POST', '/api/access-codes/verify', { code: 'INVALID-CODE' });
      assert.strictEqual(res.status, 401);
      assert.strictEqual(res.body.valid, false);
    });

    // 4. Freshness Guard Contract
    await test('GET /api/dashboard/:code enforces Read-Only flag', async () => {
      const res = await makeRequest('GET', '/api/dashboard/DF-8492');
      assert.strictEqual(res.status, 200);
      assert.strictEqual(res.body.isReadOnly, true, 'Dashboard must be strictly read-only');
    });

    await test('Freshness Guard: Online state reflects LIVE_SYNCED under 15m window', async () => {
      await makeRequest('POST', '/api/simulate', { mode: 'live' });
      const res = await makeRequest('GET', '/api/dashboard/DF-8492');

      assert.strictEqual(res.status, 200);
      assert.strictEqual(res.body.deviceStatus.isStale, false);
      assert.strictEqual(res.body.deviceStatus.freshnessContract.statusText, 'LIVE_SYNCED');
    });

    await test('Freshness Guard: Stale state (>15m lag) surfaces OFFLINE_STALE and AWAITING_SYNC chip', async () => {
      await makeRequest('POST', '/api/simulate', { mode: 'stale' });
      const res = await makeRequest('GET', '/api/dashboard/DF-8492');

      assert.strictEqual(res.status, 200);
      assert.strictEqual(res.body.deviceStatus.isStale, true, 'Device must be flagged stale when > 15m lag');
      assert.strictEqual(res.body.deviceStatus.freshnessContract.statusText, 'OFFLINE_STALE');
      assert.strictEqual(res.body.deviceStatus.freshnessContract.staleBannerRequired, true);

      // Verify that past unconfirmed dose is converted to AWAITING_SYNC, not silently pending or missed!
      const unconfirmedDose = res.body.todaySchedule.find(d => d.id === 'occ_today_03');
      assert.ok(unconfirmedDose, 'Dose 03 must exist');
      assert.strictEqual(unconfirmedDose.displayStatus, 'AWAITING_SYNC', 'Unconfirmed dose under stale device must be AWAITING_SYNC');
      assert.ok(unconfirmedDose.freshnessWarning.includes('Device offline'), 'Warning text must explain offline state');
    });

    // 5. Mobile Outbox Event Sync Ingestion
    await test('POST /api/sync/events ingests mobile batch and resets online heartbeat', async () => {
      const syncPayload = {
        patientId: 'patient_eleanor_001',
        events: [
          {
            id: 'evt_sync_001',
            eventType: 'DOSE_TAKEN',
            aggregateId: 'occ_today_04',
            payloadJson: JSON.stringify({
              occurrenceId: 'occ_today_04',
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

      // Re-query dashboard: device must be live again and dose 04 marked TAKEN
      const dash = await makeRequest('GET', '/api/dashboard/DF-8492');
      assert.strictEqual(dash.body.deviceStatus.isStale, false);
      const dose04 = dash.body.todaySchedule.find(d => d.id === 'occ_today_04');
      assert.strictEqual(dose04.status, 'TAKEN');
    });

    // 6. Security & Scaffolding: Dynamic generation produces unique credentials, invalid codes rejected
    await test('Security & Scaffolding: Dynamic generator produces unique credentials and unseeded codes return 401', async () => {
      const gen1 = await makeRequest('POST', '/api/access-codes/generate', { patientId: 'patient_eleanor_001' });
      const gen2 = await makeRequest('POST', '/api/access-codes/generate', { patientId: 'patient_eleanor_001' });
      assert.notStrictEqual(gen1.body.code, gen2.body.code, 'Dynamically generated codes must be distinct');

      const dash1 = await makeRequest('GET', `/api/dashboard/${gen1.body.code}`);
      const dash2 = await makeRequest('GET', `/api/dashboard/${gen2.body.code}`);
      assert.strictEqual(dash1.status, 200);
      assert.strictEqual(dash2.status, 200);

      const invalid = await makeRequest('GET', '/api/dashboard/DF-0000');
      assert.strictEqual(invalid.status, 401);
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
