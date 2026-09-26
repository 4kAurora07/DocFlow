/**
 * DoseFlow Caregiver Portal — Client Application
 * Strict Freshness Guard & Read-Only Adherence Monitor
 */

(function () {
  'use strict';

  // State
  let currentAccessCode = localStorage.getItem('doseflow_access_code') || 'DF-8492';
  let cachedDashboardData = null;
  let autoRefreshTimer = null;

  // DOM Elements
  const authModal = document.getElementById('authModal');
  const authForm = document.getElementById('authForm');
  const inputAccessCode = document.getElementById('inputAccessCode');
  const authError = document.getElementById('authError');
  const btnQuickDemoCode = document.getElementById('btnQuickDemoCode');
  const btnDisconnect = document.getElementById('btnDisconnect');
  const btnManualRefresh = document.getElementById('btnManualRefresh');

  // Simulation Elements
  const btnSimLive = document.getElementById('btnSimLive');
  const btnSimStale = document.getElementById('btnSimStale');
  const btnSimDeadLetter = document.getElementById('btnSimDeadLetter');
  const btnToggleSimCode = document.getElementById('btnToggleSimCode');
  const codeGeneratorModal = document.getElementById('codeGeneratorModal');
  const btnCloseGenModal = document.getElementById('btnCloseGenModal');
  const btnGenerateNewCode = document.getElementById('btnGenerateNewCode');
  const plainTextCodeValue = document.getElementById('plainTextCodeValue');
  const btnCopyCode = document.getElementById('btnCopyCode');

  // Dashboard Elements
  const freshnessBannerContainer = document.getElementById('freshnessBannerContainer');
  const patientNameDisplay = document.getElementById('patientNameDisplay');
  const patientDeviceDisplay = document.getElementById('patientDeviceDisplay');
  const timelineList = document.getElementById('timelineList');
  const historyTableBody = document.getElementById('historyTableBody');
  const footerSyncStatus = document.getElementById('footerSyncStatus');
  const btnExportCsv = document.getElementById('btnExportCsv');

  // Adherence Gauge Elements
  const sevenDayValue = document.getElementById('sevenDayValue');
  const sevenDayGauge = document.getElementById('sevenDayGauge');
  const sevenDayTaken = document.getElementById('sevenDayTaken');
  const sevenDaySkipped = document.getElementById('sevenDaySkipped');
  const sevenDayMissed = document.getElementById('sevenDayMissed');

  const thirtyDayValue = document.getElementById('thirtyDayValue');
  const thirtyDayGauge = document.getElementById('thirtyDayGauge');
  const thirtyDayTaken = document.getElementById('thirtyDayTaken');
  const thirtyDaySkipped = document.getElementById('thirtyDaySkipped');
  const thirtyDayMissed = document.getElementById('thirtyDayMissed');

  // ======================== CORE API CALLS ========================

  async function loadDashboard(code) {
    try {
      const response = await fetch(`/api/dashboard/${encodeURIComponent(code)}`);
      if (response.status === 401) {
        showAuthModal('Please enter a valid Caregiver Access Code.');
        return;
      }
      if (!response.ok) {
        throw new Error(`Server returned HTTP ${response.status}`);
      }

      const data = await response.json();
      cachedDashboardData = data;
      hideAuthModal();
      localStorage.setItem('doseflow_access_code', code);
      currentAccessCode = code;

      renderDashboard(data);
    } catch (err) {
      console.error('Error loading dashboard:', err);
      showAuthModal(`Failed to connect to backend: ${err.message}`);
    }
  }

  async function setSimulationMode(mode) {
    try {
      const response = await fetch('/api/simulate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode })
      });
      if (response.ok) {
        updateSimToolbarState(mode);
        await loadDashboard(currentAccessCode);
      }
    } catch (err) {
      console.error('Failed to set simulation mode:', err);
    }
  }

  async function generateNewAccessCode() {
    try {
      const res = await fetch('/api/access-codes/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          patientId: 'patient_eleanor_001',
          patientName: 'Eleanor Vance'
        })
      });
      const data = await res.json();
      if (data.code) {
        plainTextCodeValue.textContent = data.code;
      }
    } catch (err) {
      console.error('Failed to generate code:', err);
    }
  }

  // ======================== RENDERING ========================

  function renderDashboard(data) {
    // 1. Patient Header Info
    patientNameDisplay.textContent = data.patient.name;
    patientDeviceDisplay.textContent = `${data.patient.primaryDevice.split('(')[0].trim()} • ${data.patient.timeZone}`;

    // 2. Strict Freshness Guard Banner
    renderFreshnessBanner(data.deviceStatus);

    // 3. Today's Timeline Schedule
    renderTimeline(data.todaySchedule, data.deviceStatus.isStale);

    // 4. Adherence Metrics
    renderAdherence(data.adherence);

    // 5. Recent History Log
    renderHistory(data.historyLogs);

    // 6. Footer sync status
    const formattedSyncTime = new Date(data.deviceStatus.lastSyncedAtEpochMillis).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    footerSyncStatus.textContent = `Last device check: ${formattedSyncTime} (${data.deviceStatus.stalenessMinutes}m ago)`;
  }

  /**
   * CRITICAL: Freshness Guard Banner
   * Guarantees that the dashboard never silently shows stale data as current.
   */
  function renderFreshnessBanner(deviceStatus) {
    freshnessBannerContainer.innerHTML = '';

    const lastSyncDate = new Date(deviceStatus.lastSyncedAtEpochMillis);
    const timeString = lastSyncDate.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
    const dateString = lastSyncDate.toLocaleDateString([], { month: 'short', day: 'numeric' });

    if (deviceStatus.isStale) {
      // HIGH-VISIBILITY STALE / OFFLINE WARNING BANNER
      const banner = document.createElement('div');
      banner.className = 'freshness-banner stale';
      banner.id = 'freshnessAlertBanner';
      banner.setAttribute('role', 'alert');
      banner.innerHTML = `
        <div class="banner-left">
          <div class="warning-icon-box" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <div>
            <strong class="banner-title">⚠️ Device Offline — Last known state as of ${dateString} at ${timeString}</strong>
            <span class="banner-sub">
              Eleanor's phone has not synced in ${deviceStatus.stalenessMinutes} minutes. Doses taken while offline will update upon reconnection.
            </span>
          </div>
        </div>
        <div class="banner-right">
          <button id="btnRetryBanner" class="btn-retry-sync" title="Check for pending sync packets">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            <span>Ping Device</span>
          </button>
        </div>
      `;
      freshnessBannerContainer.appendChild(banner);

      document.getElementById('btnRetryBanner').onclick = () => {
        loadDashboard(currentAccessCode);
      };
    } else {
      // HEALTHY LIVE-SYNCED STATE BANNER
      const banner = document.createElement('div');
      banner.className = 'freshness-banner live';
      banner.id = 'freshnessAlertBanner';
      banner.innerHTML = `
        <div class="banner-left">
          <div class="live-pulse-container" aria-hidden="true">
            <div class="live-pulse-dot"></div>
            <div class="live-pulse-ring"></div>
          </div>
          <div>
            <strong class="banner-title">● Device Online — Live sync active</strong>
            <span class="banner-sub">Last synced ${deviceStatus.stalenessMinutes === 0 ? 'just now' : `${deviceStatus.stalenessMinutes}m ago`} (All reminder states verified)</span>
          </div>
        </div>
      `;
      freshnessBannerContainer.appendChild(banner);
    }
  }

  function renderTimeline(occurrences, isDeviceStale) {
    timelineList.innerHTML = '';

    occurrences.forEach(occ => {
      const item = document.createElement('div');
      item.className = 'timeline-item';

      let markerClass = 'pending';
      let statusChipHtml = '';
      let actionDetails = '';

      switch (occ.displayStatus) {
        case 'TAKEN':
          markerClass = 'taken';
          statusChipHtml = `
            <span class="status-chip chip-taken">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              TAKEN
            </span>
          `;
          actionDetails = `<div class="dose-action-note">✓ Taken at ${new Date(occ.actionTimestamp).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })} • ${occ.actionNote || 'Confirmed'}</div>`;
          break;

        case 'SKIPPED':
          markerClass = 'skipped';
          statusChipHtml = `
            <span class="status-chip chip-skipped">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              SKIPPED
            </span>
          `;
          actionDetails = `<div class="dose-action-note">Skipped by patient</div>`;
          break;

        case 'MISSED':
          markerClass = 'missed';
          statusChipHtml = `
            <span class="status-chip chip-missed">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
              MISSED
            </span>
          `;
          actionDetails = `<div class="dose-action-note">Auto-transitioned to missed at T+45m window</div>`;
          break;

        case 'AWAITING_SYNC':
          // CRITICAL FRESHNESS GUARD CASE
          markerClass = 'awaiting';
          statusChipHtml = `
            <span class="status-chip chip-awaiting" title="Device was offline when dose was scheduled. Status will update upon reconnection.">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 14 14"/>
              </svg>
              AWAITING DEVICE SYNC
            </span>
          `;
          actionDetails = `<div class="dose-action-note" style="color: #9A3412; font-weight: 500;">⚠️ Device offline during dose window; awaiting verification</div>`;
          break;

        case 'PENDING':
        default:
          markerClass = 'pending';
          statusChipHtml = `
            <span class="status-chip chip-pending">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
              UPCOMING
            </span>
          `;
          actionDetails = `<div class="dose-action-note">Scheduled for this evening</div>`;
          break;
      }

      item.innerHTML = `
        <div class="timeline-marker ${markerClass}"></div>
        <div class="dose-card">
          <div class="dose-info">
            <div class="dose-time">
              <span>${occ.scheduledTime}</span>
            </div>
            <div class="dose-name">${occ.medicineName} ${occ.dosage}</div>
            <div class="dose-instructions">${occ.instructions}</div>
            ${actionDetails}
          </div>
          <div class="dose-status-badge">
            ${statusChipHtml}
          </div>
        </div>
      `;
      timelineList.appendChild(item);
    });
  }

  function renderAdherence(adherence) {
    // 7-day
    sevenDayValue.textContent = `${adherence.sevenDay.adherenceRate}%`;
    sevenDayTaken.textContent = adherence.sevenDay.taken;
    sevenDaySkipped.textContent = adherence.sevenDay.skipped;
    sevenDayMissed.textContent = adherence.sevenDay.missed;
    sevenDayGauge.style.background = `conic-gradient(#10B981 0% ${adherence.sevenDay.adherenceRate}%, #E2E8F0 ${adherence.sevenDay.adherenceRate}% 100%)`;

    // 30-day
    thirtyDayValue.textContent = `${adherence.thirtyDay.adherenceRate}%`;
    thirtyDayTaken.textContent = adherence.thirtyDay.taken;
    thirtyDaySkipped.textContent = adherence.thirtyDay.skipped;
    thirtyDayMissed.textContent = adherence.thirtyDay.missed;
    thirtyDayGauge.style.background = `conic-gradient(#0284C7 0% ${adherence.thirtyDay.adherenceRate}%, #E2E8F0 ${adherence.thirtyDay.adherenceRate}% 100%)`;
  }

  function renderHistory(logs) {
    historyTableBody.innerHTML = '';
    logs.forEach(log => {
      const row = document.createElement('tr');
      let badgeStyle = 'background: #D1FAE5; color: #065F46;';
      if (log.status === 'SKIPPED') badgeStyle = 'background: #F1F5F9; color: #334155;';
      if (log.status === 'MISSED') badgeStyle = 'background: #FEE2E2; color: #991B1B;';

      row.innerHTML = `
        <td>${log.date}</td>
        <td><strong>${log.medicine}</strong></td>
        <td>${log.scheduled}</td>
        <td>${log.recorded}</td>
        <td><span class="badge-pill-sm" style="${badgeStyle}">${log.status}</span></td>
      `;
      historyTableBody.appendChild(row);
    });
  }

  // ======================== AUTH MODAL ========================

  function showAuthModal(errMsg = '') {
    authModal.style.display = 'flex';
    if (errMsg) {
      authError.textContent = errMsg;
      authError.style.display = 'block';
    } else {
      authError.style.display = 'none';
    }
  }

  function hideAuthModal() {
    authModal.style.display = 'none';
  }

  authForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const code = inputAccessCode.value.trim().toUpperCase();
    if (code) {
      loadDashboard(code);
    }
  });

  btnQuickDemoCode.addEventListener('click', () => {
    inputAccessCode.value = 'DF-8492';
    loadDashboard('DF-8492');
  });

  btnDisconnect.addEventListener('click', () => {
    localStorage.removeItem('doseflow_access_code');
    showAuthModal();
  });

  btnManualRefresh.addEventListener('click', () => {
    loadDashboard(currentAccessCode);
  });

  // ======================== SIMULATION QA CONTROLS ========================

  function updateSimToolbarState(activeMode) {
    btnSimLive.classList.remove('active');
    btnSimStale.classList.remove('active');
    btnSimDeadLetter.classList.remove('active');

    if (activeMode === 'live') btnSimLive.classList.add('active');
    if (activeMode === 'stale') btnSimStale.classList.add('active');
    if (activeMode === 'dead_letter') btnSimDeadLetter.classList.add('active');
  }

  btnSimLive.addEventListener('click', () => setSimulationMode('live'));
  btnSimStale.addEventListener('click', () => setSimulationMode('stale'));
  btnSimDeadLetter.addEventListener('click', () => setSimulationMode('dead_letter'));

  btnToggleSimCode.addEventListener('click', () => {
    codeGeneratorModal.style.display = 'flex';
  });

  btnCloseGenModal.addEventListener('click', () => {
    codeGeneratorModal.style.display = 'none';
  });

  btnGenerateNewCode.addEventListener('click', () => {
    generateNewAccessCode();
  });

  btnCopyCode.addEventListener('click', () => {
    const text = plainTextCodeValue.textContent;
    navigator.clipboard?.writeText(text);
    btnCopyCode.textContent = 'Copied!';
    setTimeout(() => { btnCopyCode.textContent = 'Copy'; }, 2000);
  });

  // ======================== CSV EXPORT ========================

  btnExportCsv.addEventListener('click', () => {
    if (!cachedDashboardData) return;
    const header = ['Date', 'Medication', 'Scheduled Time', 'Recorded Time', 'Status', 'Notes'];
    const rows = cachedDashboardData.historyLogs.map(log => [
      `"${log.date}"`,
      `"${log.medicine}"`,
      `"${log.scheduled}"`,
      `"${log.recorded}"`,
      `"${log.status}"`,
      `"${log.note || ''}"`
    ]);

    const csvContent = [header.join(','), ...rows.map(r => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `DoseFlow_Adherence_Report_${cachedDashboardData.patient.name.replace(/\s+/g, '_')}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  });

  // ======================== INITIALIZATION ========================

  if (currentAccessCode) {
    loadDashboard(currentAccessCode);
  } else {
    showAuthModal();
  }

  // Auto-refresh every 30 seconds
  autoRefreshTimer = setInterval(() => {
    if (currentAccessCode && authModal.style.display === 'none') {
      loadDashboard(currentAccessCode);
    }
  }, 30000);

})();
