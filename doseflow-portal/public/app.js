/**
 * DoseFlow Caregiver Portal — Client Application
 * Real Caregiver Auth, App Pairing Code Matching, and Live Freshness Guard
 */

(function () {
  'use strict';

  // State
  let caregiverToken = localStorage.getItem('doseflow_token') || null;
  let currentCaregiver = null;
  let linkedPatient = null;
  let cachedDashboardData = null;
  let autoRefreshTimer = null;

  // DOM Elements - Modals & Forms
  const authModal = document.getElementById('authModal');
  const authForm = document.getElementById('authForm');
  const inputEmail = document.getElementById('inputEmail');
  const inputPassword = document.getElementById('inputPassword');
  const authError = document.getElementById('authError');

  const pairingModal = document.getElementById('pairingModal');
  const pairingForm = document.getElementById('pairingForm');
  const inputPairingCode = document.getElementById('inputPairingCode');
  const pairingError = document.getElementById('pairingError');
  const btnCancelPairing = document.getElementById('btnCancelPairing');

  // Dashboard Header Elements
  const patientAvatarDisplay = document.getElementById('patientAvatarDisplay');
  const patientNameDisplay = document.getElementById('patientNameDisplay');
  const patientDeviceDisplay = document.getElementById('patientDeviceDisplay');
  const caregiverEmailDisplay = document.getElementById('caregiverEmailDisplay');
  const btnSwitchPatient = document.getElementById('btnSwitchPatient');
  const btnSignOut = document.getElementById('btnSignOut');
  const btnManualRefresh = document.getElementById('btnManualRefresh');

  // Dashboard Body Elements
  const freshnessBannerContainer = document.getElementById('freshnessBannerContainer');
  const currentDateSubtitle = document.getElementById('currentDateSubtitle');
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

  // ======================== API HELPERS ========================

  async function apiRequest(endpoint, method = 'GET', body = null) {
    const headers = { 'Content-Type': 'application/json' };
    if (caregiverToken) {
      headers['Authorization'] = `Bearer ${caregiverToken}`;
    }
    const config = { method, headers };
    if (body) {
      config.body = JSON.stringify(body);
    }
    const response = await fetch(endpoint, config);
    const data = await response.json().catch(() => ({}));
    return { ok: response.ok, status: response.status, data };
  }

  // ======================== INITIALIZATION ========================

  async function init() {
    updateDateSubtitle();
    bindEvents();

    if (!caregiverToken) {
      showAuthModal();
      return;
    }

    // Verify token with backend
    try {
      const { ok, data } = await apiRequest('/api/auth/me');
      if (ok && data.caregiver) {
        currentCaregiver = data.caregiver;
        linkedPatient = data.linkedPatient;
        caregiverEmailDisplay.textContent = currentCaregiver.email;

        if (linkedPatient) {
          hideAuthModal();
          hidePairingModal();
          await loadDashboard();
        } else {
          hideAuthModal();
          showPairingModal(false);
        }
      } else {
        // Token expired or invalid
        signOut();
      }
    } catch (err) {
      console.error('Initialization error:', err);
      showAuthModal('Could not connect to DoseFlow server. Please check your network.');
    }
  }

  function updateDateSubtitle() {
    const now = new Date();
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
    if (currentDateSubtitle) {
      currentDateSubtitle.textContent = now.toLocaleDateString(undefined, options);
    }
  }

  // ======================== AUTH & PAIRING FLOW ========================

  function showAuthModal(errMsg = null) {
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

  function showPairingModal(canCancel = false) {
    pairingModal.style.display = 'flex';
    pairingError.style.display = 'none';
    inputPairingCode.value = '';
    btnCancelPairing.style.display = canCancel ? 'inline-block' : 'none';
  }

  function hidePairingModal() {
    pairingModal.style.display = 'none';
  }

  async function handleLogin(email, password) {
    authError.style.display = 'none';
    const { ok, status, data } = await apiRequest('/api/auth/login', 'POST', { email, password });
    if (!ok) {
      authError.textContent = data.error || 'Authentication failed. Please verify credentials.';
      authError.style.display = 'block';
      return;
    }

    caregiverToken = data.token;
    localStorage.setItem('doseflow_token', caregiverToken);
    currentCaregiver = data.caregiver;
    linkedPatient = data.linkedPatient;
    caregiverEmailDisplay.textContent = currentCaregiver.email;

    hideAuthModal();

    if (linkedPatient) {
      await loadDashboard();
    } else {
      showPairingModal(false);
    }
  }

  async function handlePairingCode(code) {
    pairingError.style.display = 'none';
    const cleanCode = code.trim().toUpperCase();
    if (!cleanCode) {
      pairingError.textContent = 'Please enter a valid pairing code.';
      pairingError.style.display = 'block';
      return;
    }

    const { ok, data } = await apiRequest('/api/pairing/link', 'POST', { code: cleanCode });
    if (!ok) {
      pairingError.textContent = data.error || 'Invalid or expired code. Please verify the code in the patient mobile app.';
      pairingError.style.display = 'block';
      return;
    }

    // Code matched! Patient linked immediately
    linkedPatient = data.patient;
    hidePairingModal();

    if (data.dashboard) {
      cachedDashboardData = data.dashboard;
      renderDashboard(data.dashboard);
    } else {
      await loadDashboard();
    }

    startAutoRefresh();
  }

  function signOut() {
    if (caregiverToken) {
      apiRequest('/api/auth/logout', 'POST').catch(() => {});
    }
    caregiverToken = null;
    currentCaregiver = null;
    linkedPatient = null;
    cachedDashboardData = null;
    localStorage.removeItem('doseflow_token');
    stopAutoRefresh();
    hidePairingModal();
    showAuthModal();
  }

  // ======================== DASHBOARD LOADING & RENDERING ========================

  async function loadDashboard() {
    try {
      const { ok, status, data } = await apiRequest('/api/dashboard');
      if (status === 401) {
        signOut();
        return;
      }
      if (!ok) {
        throw new Error(data.error || `HTTP ${status}`);
      }

      if (data.hasPatient === false) {
        showPairingModal(false);
        return;
      }

      cachedDashboardData = data;
      renderDashboard(data);
      startAutoRefresh();
    } catch (err) {
      console.error('Failed to load dashboard:', err);
    }
  }

  function renderDashboard(data) {
    if (!data || !data.patient) return;

    // 1. Patient Header Info
    const patientName = data.patient.name || 'Patient';
    patientNameDisplay.textContent = patientName;
    const initials = patientName.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() || 'PT';
    if (patientAvatarDisplay) {
      patientAvatarDisplay.textContent = initials;
    }
    patientDeviceDisplay.textContent = `${data.patient.primaryDevice || 'Android Device'} • ${data.patient.timeZone || 'Local Time'}`;

    // 2. Strict Freshness Guard Banner
    renderFreshnessBanner(data.deviceStatus, data.patient.name);

    // 3. Today's Timeline Schedule
    renderTimeline(data.todaySchedule || []);

    // 4. Adherence Analytics Gauges
    renderAdherence(data.adherence || {});

    // 5. Recent Dose Logs
    renderHistoryTable(data.historyLogs || []);

    // 6. Footer sync status
    if (footerSyncStatus) {
      footerSyncStatus.textContent = `Last synchronized: ${new Date(data.serverTimestamp || Date.now()).toLocaleTimeString()}`;
    }
  }

  function renderFreshnessBanner(status, patientName) {
    if (!freshnessBannerContainer) return;
    if (!status) {
      freshnessBannerContainer.innerHTML = '';
      return;
    }

    if (status.isStale) {
      freshnessBannerContainer.innerHTML = `
        <div class="freshness-banner banner-stale" role="alert">
          <div class="banner-icon-col">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <div class="banner-content">
            <strong class="banner-title">STALE DATA WARNING: ${patientName}'s device is offline (${status.stalenessMinutes}m lag)</strong>
            <p class="banner-desc">
              No sync heartbeat received in over 15 minutes. Unconfirmed doses are protected under the 
              <strong>Strict Freshness Contract</strong> and flagged as <em>Awaiting Sync</em> rather than assumed taken or missed.
            </p>
          </div>
        </div>
      `;
    } else {
      freshnessBannerContainer.innerHTML = `
        <div class="freshness-banner banner-live" role="status">
          <div class="banner-icon-col">
            <span class="live-pulse"></span>
          </div>
          <div class="banner-content">
            <strong class="banner-title">LIVE CONNECTION: Sync Heartbeat Fresh (&lt;15m)</strong>
            <p class="banner-desc">
              Connected directly to ${patientName}'s mobile Room SQLite outbox. Timestamps reflect local device confirmation.
            </p>
          </div>
        </div>
      `;
    }
  }

  function renderTimeline(occurrences) {
    if (!timelineList) return;

    if (!occurrences || occurrences.length === 0) {
      timelineList.innerHTML = `
        <div class="empty-state-container">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
            <line x1="16" y1="2" x2="16" y2="6"/>
            <line x1="8" y1="2" x2="8" y2="6"/>
            <line x1="3" y1="10" x2="21" y2="10"/>
          </svg>
          <div class="empty-state-title">No Medications Scheduled Today</div>
          <p class="empty-state-desc">When you add medications in the DoseFlow mobile app, they will sync and appear here immediately.</p>
        </div>
      `;
      return;
    }

    let html = '';
    occurrences.forEach((occ) => {
      const isAwaitingSync = occ.displayStatus === 'AWAITING_SYNC';
      const statusClass = getStatusClass(occ.displayStatus);
      const statusBadge = getStatusBadge(occ.displayStatus, isAwaitingSync);

      const actionTimeText = occ.actionTimestamp 
        ? `<span class="dose-logged-at">Logged at ${new Date(occ.actionTimestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>`
        : '';

      html += `
        <div class="dose-card-item ${statusClass}" data-occ-id="${occ.id}">
          <div class="dose-card-header">
            <div>
              <div class="dose-med-title">${escapeHtml(occ.medicineName)}</div>
              ${occ.dosage ? `<div class="dose-dosage-sub">${escapeHtml(occ.dosage)}</div>` : ''}
            </div>
            <div>
              ${statusBadge}
            </div>
          </div>

          <div class="dose-meta-row">
            <div class="dose-scheduled-time">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
              <span>Scheduled: ${escapeHtml(occ.scheduledTime || 'Scheduled')}</span>
            </div>
            ${actionTimeText}
          </div>

          ${occ.instructions ? `<div class="dose-instructions">${escapeHtml(occ.instructions)}</div>` : ''}

          ${isAwaitingSync ? `
            <div class="status-badge badge-awaiting" style="margin-top: 6px;">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
              <span>${escapeHtml(occ.freshnessWarning || 'Awaiting Device Sync')}</span>
            </div>
          ` : ''}
        </div>
      `;
    });

    timelineList.innerHTML = html;
  }

  function getStatusClass(status) {
    switch (status) {
      case 'TAKEN': return 'status-taken';
      case 'SKIPPED': return 'status-skipped';
      case 'MISSED': return 'status-missed';
      case 'AWAITING_SYNC': return 'status-awaiting';
      default: return 'status-pending';
    }
  }

  function getStatusBadge(status, isAwaiting) {
    if (isAwaiting) {
      return `<span class="status-badge badge-awaiting">Awaiting Sync</span>`;
    }
    switch (status) {
      case 'TAKEN':
        return `<span class="status-badge badge-taken"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg> Taken</span>`;
      case 'SKIPPED':
        return `<span class="status-badge badge-skipped">Skipped</span>`;
      case 'MISSED':
        return `<span class="status-badge badge-missed">Missed</span>`;
      case 'SNOOZED':
        return `<span class="status-badge badge-snoozed">Snoozed</span>`;
      default:
        return `<span class="status-badge badge-pending">Due</span>`;
    }
  }

  function renderAdherence(adherence) {
    const seven = adherence.sevenDay || { adherenceRate: 100, taken: 0, skipped: 0, missed: 0 };
    const thirty = adherence.thirtyDay || { adherenceRate: 100, taken: 0, skipped: 0, missed: 0 };

    if (sevenDayValue) sevenDayValue.textContent = `${seven.adherenceRate}%`;
    if (sevenDayTaken) sevenDayTaken.textContent = seven.taken;
    if (sevenDaySkipped) sevenDaySkipped.textContent = seven.skipped;
    if (sevenDayMissed) sevenDayMissed.textContent = seven.missed;
    if (sevenDayGauge) {
      sevenDayGauge.style.background = `conic-gradient(#10B981 ${seven.adherenceRate * 3.6}deg, #E2E8F0 0deg)`;
    }

    if (thirtyDayValue) thirtyDayValue.textContent = `${thirty.adherenceRate}%`;
    if (thirtyDayTaken) thirtyDayTaken.textContent = thirty.taken;
    if (thirtyDaySkipped) thirtyDaySkipped.textContent = thirty.skipped;
    if (thirtyDayMissed) thirtyDayMissed.textContent = thirty.missed;
    if (thirtyDayGauge) {
      thirtyDayGauge.style.background = `conic-gradient(#0284C7 ${thirty.adherenceRate * 3.6}deg, #E2E8F0 0deg)`;
    }
  }

  function renderHistoryTable(logs) {
    if (!historyTableBody) return;
    if (!logs || logs.length === 0) {
      historyTableBody.innerHTML = `
        <tr>
          <td colspan="5" style="text-align: center; color: var(--text-muted); padding: 24px;">
            No historical doses logged yet.
          </td>
        </tr>
      `;
      return;
    }

    let html = '';
    logs.forEach(log => {
      const badge = log.status === 'TAKEN'
        ? `<span class="table-badge badge-taken">Taken</span>`
        : (log.status === 'SKIPPED'
          ? `<span class="table-badge badge-skipped">Skipped</span>`
          : `<span class="table-badge badge-missed">Missed</span>`);

      html += `
        <tr>
          <td><strong>${escapeHtml(log.date)}</strong></td>
          <td>${escapeHtml(log.medicine)}</td>
          <td>${escapeHtml(log.scheduled)}</td>
          <td>${escapeHtml(log.recorded)}</td>
          <td>${badge}</td>
        </tr>
      `;
    });

    historyTableBody.innerHTML = html;
  }

  // ======================== AUTO REFRESH & SIMULATION ========================

  function startAutoRefresh() {
    stopAutoRefresh();
    // Poll every 10 seconds for real-time app sync updates
    autoRefreshTimer = setInterval(() => {
      if (caregiverToken && linkedPatient) {
        loadDashboard();
      }
    }, 10000);
  }

  function stopAutoRefresh() {
    if (autoRefreshTimer) {
      clearInterval(autoRefreshTimer);
      autoRefreshTimer = null;
    }
  }

  function exportCsv() {
    if (!cachedDashboardData || !cachedDashboardData.historyLogs) return;
    const logs = cachedDashboardData.historyLogs;
    let csv = 'Date,Medication,Scheduled Time,Recorded Time,Status\n';
    logs.forEach(l => {
      csv += `"${l.date}","${l.medicine}","${l.scheduled}","${l.recorded}","${l.status}"\n`;
    });
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `doseflow_adherence_report_${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  // ======================== EVENT BINDINGS ========================

  function bindEvents() {
    // Auth Form
    authForm.addEventListener('submit', (e) => {
      e.preventDefault();
      handleLogin(inputEmail.value, inputPassword.value);
    });

    // Pairing Form
    pairingForm.addEventListener('submit', (e) => {
      e.preventDefault();
      handlePairingCode(inputPairingCode.value);
    });

    btnCancelPairing.addEventListener('click', () => {
      if (linkedPatient) {
        hidePairingModal();
      }
    });

    // Switch Patient & Sign Out
    btnSwitchPatient.addEventListener('click', () => {
      showPairingModal(true);
    });

    btnSignOut.addEventListener('click', () => {
      signOut();
    });

    btnManualRefresh.addEventListener('click', async () => {
      btnManualRefresh.classList.add('spinning');
      await loadDashboard();
      setTimeout(() => btnManualRefresh.classList.remove('spinning'), 500);
    });

    if (btnExportCsv) {
      btnExportCsv.addEventListener('click', exportCsv);
    }
  }

  function escapeHtml(text) {
    if (!text) return '';
    return String(text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // Start application on DOM ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
