/**
 * DoseFlow Caregiver Portal — Client Application v2.0
 * Direct Code Pairing, Cookie & LocalStorage Persistence, Instant Zero-Delay Cache,
 * Automatic Real-Time Live Sync, and Elderly/Caregiver Usability.
 */

(function () {
  'use strict';

  // API Base Resolution:
  // When hosted on GitHub Pages (*.github.io), Vercel, or Netlify, route API requests to the cloud server.
  // When running locally, use relative path.
  const API_BASE = (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')
    ? ''
    : 'https://adhivio-med.onrender.com';

  // State
  let pairingCode = getCookie('doseflow_code') || localStorage.getItem('doseflow_code') || null;
  let cachedDashboardData = null;
  let autoRefreshTimer = null;
  let isFetching = false;
  let lastKnownStatuses = {};

  // DOM Elements - Modals & Forms
  const pairingModal = document.getElementById('pairingModal');
  const pairingForm = document.getElementById('pairingForm');
  const inputPairingCode = document.getElementById('inputPairingCode');
  const pairingError = document.getElementById('pairingError');
  const btnCancelPairing = document.getElementById('btnCancelPairing');

  // Dashboard Header Elements
  const liveSyncPill = document.getElementById('liveSyncPill');
  const liveSyncStatusText = document.getElementById('liveSyncStatusText');
  const patientAvatarDisplay = document.getElementById('patientAvatarDisplay');
  const patientNameDisplay = document.getElementById('patientNameDisplay');
  const codePillDisplay = document.getElementById('codePillDisplay');
  const patientDeviceDisplay = document.getElementById('patientDeviceDisplay');
  const btnSwitchPatient = document.getElementById('btnSwitchPatient');
  const btnManualRefresh = document.getElementById('btnManualRefresh');

  // Dashboard Body Elements
  const freshnessBannerContainer = document.getElementById('freshnessBannerContainer');
  const currentDateSubtitle = document.getElementById('currentDateSubtitle');
  const timelineList = document.getElementById('timelineList');
  const historyTableBody = document.getElementById('historyTableBody');
  const footerSyncStatus = document.getElementById('footerSyncStatus');
  const btnExportCsv = document.getElementById('btnExportCsv');
  const toastContainer = document.getElementById('toastContainer');

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

  // ======================== COOKIE HELPERS ========================

  function setCookie(name, value, days = 365) {
    const expires = new Date(Date.now() + days * 864e5).toUTCString();
    document.cookie = `${encodeURIComponent(name)}=${encodeURIComponent(value)}; expires=${expires}; path=/; SameSite=Lax`;
  }

  function getCookie(name) {
    const nameEQ = encodeURIComponent(name) + '=';
    const cookies = document.cookie.split(';');
    for (let c of cookies) {
      c = c.trim();
      if (c.indexOf(nameEQ) === 0) {
        return decodeURIComponent(c.substring(nameEQ.length));
      }
    }
    return null;
  }

  function deleteCookie(name) {
    document.cookie = `${encodeURIComponent(name)}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; SameSite=Lax`;
  }

  // ======================== TOAST NOTIFICATIONS ========================

  function showToast(title, message, type = 'success') {
    if (!toastContainer) return;
    const toast = document.createElement('div');
    toast.className = `toast-item toast-${type}`;
    toast.innerHTML = `
      <div class="toast-icon">
        ${type === 'success' ? '✓' : 'ℹ'}
      </div>
      <div class="toast-body">
        <div class="toast-title">${escapeHtml(title)}</div>
        <div class="toast-desc">${escapeHtml(message)}</div>
      </div>
    `;
    toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.classList.add('toast-fade-out');
      setTimeout(() => toast.remove(), 400);
    }, 4500);
  }

  // ======================== INITIALIZATION ========================

  async function init() {
    updateDateSubtitle();
    bindEvents();

    // Check if code was passed in URL query param, e.g. ?code=DF-7821
    const urlParams = new URLSearchParams(window.location.search);
    const queryCode = urlParams.get('code');
    if (queryCode && queryCode.trim()) {
      pairingCode = queryCode.trim().toUpperCase();
      savePairingCode(pairingCode);
      // Clean query parameter from URL without reload
      window.history.replaceState({}, document.title, window.location.pathname);
    }

    if (!pairingCode) {
      showPairingModal(false);
      return;
    }

    // 1. Instant Rendering from cache if available (0 seconds delay!)
    const cached = localStorage.getItem(`doseflow_cache_${pairingCode}`);
    if (cached) {
      try {
        const parsed = JSON.parse(cached);
        cachedDashboardData = parsed;
        renderDashboard(parsed, true);
        setSyncStatus('Showing saved data • Updating live...', 'syncing');
      } catch (_e) {}
    }

    hidePairingModal();

    // 2. Load fresh data from server
    await loadDashboard(true);

    // 3. Start auto-refresh polling every 5 seconds for live device updates
    startAutoRefresh();
  }

  function updateDateSubtitle() {
    const now = new Date();
    const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
    if (currentDateSubtitle) {
      currentDateSubtitle.textContent = now.toLocaleDateString(undefined, options);
    }
  }

  function savePairingCode(code) {
    const clean = code.trim().toUpperCase();
    pairingCode = clean;
    setCookie('doseflow_code', clean, 365);
    localStorage.setItem('doseflow_code', clean);
  }

  function clearPairingCode() {
    if (pairingCode) {
      localStorage.removeItem(`doseflow_cache_${pairingCode}`);
    }
    pairingCode = null;
    deleteCookie('doseflow_code');
    localStorage.removeItem('doseflow_code');
    stopAutoRefresh();
  }

  // ======================== PAIRING MODAL FLOW ========================

  function showPairingModal(canCancel = false) {
    pairingModal.style.display = 'flex';
    pairingError.style.display = 'none';
    inputPairingCode.value = '';
    btnCancelPairing.style.display = canCancel && pairingCode ? 'inline-block' : 'none';
    setTimeout(() => inputPairingCode.focus(), 150);
  }

  function hidePairingModal() {
    pairingModal.style.display = 'none';
  }

  async function handlePairingCodeSubmit(code) {
    pairingError.style.display = 'none';
    const cleanCode = (code || '').trim().toUpperCase();
    if (!cleanCode) {
      pairingError.textContent = 'Please enter a valid pairing code from the app.';
      pairingError.style.display = 'block';
      return;
    }

    const btnSubmit = document.getElementById('btnSubmitPairing');
    const originalText = btnSubmit.innerHTML;
    btnSubmit.disabled = true;
    btnSubmit.innerHTML = `<span>Connecting to device...</span>`;

    try {
      const response = await fetch(`${API_BASE}/api/dashboard/${encodeURIComponent(cleanCode)}`);
      const data = await response.json().catch(() => ({}));

      if (!response.ok) {
        throw new Error(data.error || 'Code not recognized yet. Please confirm the code shown in the DoseFlow app.');
      }

      // Code validated! Save permanently
      savePairingCode(cleanCode);
      hidePairingModal();

      // Cache and render immediately
      cachedDashboardData = data;
      localStorage.setItem(`doseflow_cache_${cleanCode}`, JSON.stringify(data));
      renderDashboard(data);
      showToast('Connected!', `Connected live to ${data.patient?.name || 'patient'}'s DoseFlow app.`);

      startAutoRefresh();
    } catch (err) {
      pairingError.textContent = err.message || 'Could not connect. Please check network and retry.';
      pairingError.style.display = 'block';
    } finally {
      btnSubmit.disabled = false;
      btnSubmit.innerHTML = originalText;
    }
  }

  // ======================== DASHBOARD LOADING & RENDERING ========================

  async function loadDashboard(isInitial = false) {
    if (!pairingCode || isFetching) return;
    isFetching = true;

    try {
      const response = await fetch(`${API_BASE}/api/dashboard/${encodeURIComponent(pairingCode)}`);
      const data = await response.json().catch(() => ({}));

      if (response.status === 401 || response.status === 404) {
        // Code is invalid or expired
        if (isInitial) {
          clearPairingCode();
          showPairingModal(false);
          pairingError.textContent = 'Saved code is invalid or has expired. Please enter the active code from your app.';
          pairingError.style.display = 'block';
        }
        return;
      }

      if (!response.ok) {
        throw new Error(data.error || `HTTP ${response.status}`);
      }

      // Detect real-time dose status transitions to notify caregiver
      detectDoseTransitions(data.todaySchedule || []);

      // Cache fresh data in localStorage
      localStorage.setItem(`doseflow_cache_${pairingCode}`, JSON.stringify(data));
      cachedDashboardData = data;

      renderDashboard(data);
      setSyncStatus('Auto-Sync Active (Live)', 'live');

      if (footerSyncStatus) {
        footerSyncStatus.textContent = `Last synchronized: ${new Date().toLocaleTimeString()}`;
      }
    } catch (err) {
      console.warn('Dashboard sync polling notice:', err.message);
      setSyncStatus('Connecting to device...', 'syncing');
    } finally {
      isFetching = false;
    }
  }

  function detectDoseTransitions(newSchedule) {
    newSchedule.forEach((occ) => {
      const prevStatus = lastKnownStatuses[occ.id];
      if (prevStatus && prevStatus !== 'TAKEN' && occ.displayStatus === 'TAKEN') {
        // Dose was just taken on the mobile phone!
        showToast(
          'Medicine Taken!',
          `✓ ${occ.medicineName} was just confirmed as taken!`,
          'success'
        );
      }
      lastKnownStatuses[occ.id] = occ.displayStatus;
    });
  }

  function setSyncStatus(text, state) {
    if (liveSyncStatusText) {
      liveSyncStatusText.textContent = text;
    }
    if (liveSyncPill) {
      liveSyncPill.className = `live-sync-pill status-${state}`;
    }
  }

  function renderDashboard(data, isFromCache = false) {
    if (!data || !data.patient) return;

    // 1. Patient Header Info
    const patientName = data.patient.name || 'Patient';
    if (patientNameDisplay) {
      patientNameDisplay.textContent = patientName;
    }

    const initials = patientName
      .split(' ')
      .filter(Boolean)
      .map((n) => n[0])
      .join('')
      .substring(0, 2)
      .toUpperCase() || 'PT';

    if (patientAvatarDisplay) {
      patientAvatarDisplay.textContent = initials;
    }

    if (codePillDisplay) {
      codePillDisplay.textContent = `Code: ${pairingCode || '--'}`;
    }

    if (patientDeviceDisplay) {
      const device = data.patient.primaryDevice || 'Android Device';
      const tz = data.patient.timeZone || 'Local';
      patientDeviceDisplay.textContent = `${device} • ${tz}`;
    }

    // 2. Freshness Guard Banner
    renderFreshnessBanner(data.deviceStatus, patientName, isFromCache);

    // 3. Today's Timeline Schedule
    renderTimeline(data.todaySchedule || []);

    // 4. Adherence Analytics Gauges
    renderAdherence(data.adherence || {});

    // 5. Recent Dose Logs
    renderHistoryTable(data.historyLogs || []);
  }

  function renderFreshnessBanner(status, patientName, isFromCache) {
    if (!freshnessBannerContainer) return;
    if (!status && !isFromCache) {
      freshnessBannerContainer.innerHTML = '';
      return;
    }

    if (status && status.isStale) {
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
            <strong class="banner-title">DEVICE OFFLINE: ${patientName}'s phone has not synced recently (${status.stalenessMinutes}m lag)</strong>
            <p class="banner-desc">
              Unconfirmed doses are protected and marked as <em>Awaiting Sync</em> until the phone reconnects to prevent false alerts.
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
            <strong class="banner-title">LIVE CONNECTION: Patient Device Connected</strong>
            <p class="banner-desc">
              Connected directly to ${patientName}'s DoseFlow app. Dose actions taken by your loved one update automatically.
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
          <p class="empty-state-desc">When medications are added in the DoseFlow mobile app, they will automatically appear here.</p>
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
        ? `<span class="dose-logged-at">✓ Confirmed at ${new Date(occ.actionTimestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>`
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
        return `<span class="status-badge badge-snoozed">⏰ Snoozed</span>`;
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
    logs.forEach((log) => {
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

  // ======================== AUTO REFRESH ========================

  function startAutoRefresh() {
    stopAutoRefresh();
    // Poll every 5 seconds for real-time mobile updates
    autoRefreshTimer = setInterval(() => {
      if (pairingCode && !isFetching) {
        loadDashboard(false);
      }
    }, 5000);
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
    logs.forEach((l) => {
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
    // Pairing Form Submission
    pairingForm.addEventListener('submit', (e) => {
      e.preventDefault();
      handlePairingCodeSubmit(inputPairingCode.value);
    });

    btnCancelPairing.addEventListener('click', () => {
      if (pairingCode) {
        hidePairingModal();
      }
    });

    // Switch Patient / Change Code
    btnSwitchPatient.addEventListener('click', () => {
      showPairingModal(true);
    });

    // Manual Refresh Button
    btnManualRefresh.addEventListener('click', async () => {
      btnManualRefresh.classList.add('spinning');
      await loadDashboard(false);
      setTimeout(() => btnManualRefresh.classList.remove('spinning'), 600);
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
