/**
 * levelTimer.js
 * Tracks and displays per-level elapsed time for Blockly Games Maze.
 * Supports named sessions (userId + timestamp), resumes cumulative level time,
 * enforces sequential task navigation, and reports progress to /api/level-time.
 * On Level 10, provides a "Finish task" report modal with HTML and JSON exports.
 */
(function(window, document) {
    'use strict';
    if (!window || !document) return;

    var STORAGE_SESSION_ID = 'blocky_level_timer_session_id';
    var STORAGE_USER_ID = 'blocky_level_timer_user_id';
    var STORAGE_CURRENT_LEVEL = 'blocky_level_timer_current_level';
    var STORAGE_PREFIX = 'blocky_level_timer_lvl_';
    var STORAGE_VARIANT_PREFIX = 'blocky_level_timer_var_';

    function getStoredLevel() {
        try {
            if (window.sessionStorage) {
                var val = window.sessionStorage.getItem(STORAGE_CURRENT_LEVEL);
                if (val) {
                    var parsed = parseInt(val, 10);
                    if (!isNaN(parsed) && parsed >= 1 && parsed <= 10) {
                        return parsed;
                    }
                }
            }
        } catch (e) {}
        return 1;
    }

    function setStoredLevel(lvl) {
        var validLvl = Math.max(1, Math.min(10, parseInt(lvl, 10) || 1));
        try {
            if (window.sessionStorage) {
                window.sessionStorage.setItem(STORAGE_CURRENT_LEVEL, String(validLvl));
            }
        } catch (e) {}
        return validLvl;
    }

    // Parse current level from URL query parameters (1-10, default 1)
    function getLevel() {
        var match = window.location && window.location.search ? window.location.search.match(/[?&]level=([^&]+)/) : null;
        var lvl = match ? parseInt(decodeURIComponent(match[1].replace(/\+/g, '%20')), 10) : 1;
        return isNaN(lvl) || lvl < 1 ? 1 : (lvl > 10 ? 10 : lvl);
    }

    function buildLevelUrl(targetLevel) {
        var search = (window.location && window.location.search) || '';
        var newSearch;
        if (/[?&]level=/.test(search)) {
            newSearch = search.replace(/([?&])level=[^&]*/, '$1level=' + targetLevel);
        } else if (search.length > 1) {
            newSearch = search + '&level=' + targetLevel;
        } else {
            newSearch = '?level=' + targetLevel;
        }
        return ((window.location && window.location.pathname) || '') + newSearch + ((window.location && window.location.hash) || '');
    }

    function navigateTo(targetUrl) {
        if (typeof window.__levelTimerNavigate === 'function') {
            window.__levelTimerNavigate(targetUrl);
            return;
        }
        try {
            if (window.location && typeof window.location.replace === 'function') {
                window.location.replace(targetUrl);
            } else if (window.location) {
                window.location.href = targetUrl;
            }
        } catch (e) {
            try {
                if (window.location) window.location.href = targetUrl;
            } catch (e2) {}
        }
    }

    function isAllLevelsCompleted() {
        try {
            if (window.sessionStorage) {
                return window.sessionStorage.getItem('blocky_all_levels_completed') === 'true';
            }
        } catch (e) {}
        return false;
    }

    function isLevelSolved(lvl) {
        var targetLvl = (typeof lvl === 'number') ? lvl : (typeof level === 'number' ? level : 1);
        try {
            if (window.sessionStorage) {
                return window.sessionStorage.getItem('blocky_solved_level_' + targetLvl) === 'true';
            }
        } catch (e) {}
        return false;
    }

    function markLevelSolved(lvl) {
        var targetLvl = (typeof lvl === 'number') ? lvl : (typeof level === 'number' ? level : 1);
        try {
            if (window.sessionStorage) {
                window.sessionStorage.setItem('blocky_solved_level_' + targetLvl, 'true');
            }
        } catch (e) {}
        if (typeof window.__scoreboardMarkLastRunGoalReached === 'function') {
            window.__scoreboardMarkLastRunGoalReached(targetLvl);
        }
        if (targetLvl === 10) {
            try {
                if (window.sessionStorage) {
                    window.sessionStorage.setItem('blocky_all_levels_completed', 'true');
                }
            } catch (e) {}
            showAllLevelsCompletedModal();
        }
        updateNextTaskButtonState();
    }

    // Lock sequence: ensure URL matches active task level stored in sessionStorage.
    // A fresh session starts at level 1, even if URL has ?level=7.
    var storedLevel = getStoredLevel();
    try {
        if (window.sessionStorage && !window.sessionStorage.getItem(STORAGE_CURRENT_LEVEL)) {
            setStoredLevel(storedLevel);
        }
    } catch (e) {}

    var urlLevel = getLevel();
    if (urlLevel !== storedLevel && !isAllLevelsCompleted()) {
        var targetUrl = buildLevelUrl(storedLevel);
        navigateTo(targetUrl);
        return;
    }

    var level = isAllLevelsCompleted() ? urlLevel : storedLevel;

    // Determine variant (original vs momot)
    function getVariant() {
        var path = ((window.location && window.location.pathname) || '').toLowerCase();
        if (path.indexOf('maze-original') !== -1 || path.indexOf('maze-classic') !== -1 || path.indexOf('maze_original') !== -1) {
            return 'original';
        }
        return 'momot';
    }

    function formatTimestamp(d) {
        var pad = function(n) { return n < 10 ? '0' + n : '' + n; };
        return '' + d.getUTCFullYear() +
            pad(d.getUTCMonth() + 1) +
            pad(d.getUTCDate()) + 'T' +
            pad(d.getUTCHours()) +
            pad(d.getUTCMinutes()) +
            pad(d.getUTCSeconds()) + 'Z';
    }

    function formatDisplayTime(ms) {
        if (!ms || ms < 0) ms = 0;
        var totalSec = Math.floor(ms / 1000);
        var hrs = Math.floor(totalSec / 3600);
        var mins = Math.floor((totalSec % 3600) / 60);
        var secs = totalSec % 60;
        var pad = function(n) { return n < 10 ? '0' + n : '' + n; };

        if (hrs > 0) {
            return pad(hrs) + ':' + pad(mins) + ':' + pad(secs);
        }
        return pad(mins) + ':' + pad(secs);
    }

    var level = storedLevel;
    var variant = getVariant();
    var sessionId = null;
    var userId = null;
    var currentElapsedMs = 0;
    var lastTick = null;
    var isModalOpen = false;
    var timerInterval = null;
    var syncInterval = null;

    function generateSessionId(uid) {
        var safeUid = (uid || 'anonymous').trim().replace(/[^a-zA-Z0-9_-]/g, '_');
        if (!safeUid) safeUid = 'anonymous';
        return safeUid + '_' + formatTimestamp(new Date());
    }

    function loadSessionData() {
        try {
            if (window.sessionStorage) {
                sessionId = window.sessionStorage.getItem(STORAGE_SESSION_ID);
                userId = window.sessionStorage.getItem(STORAGE_USER_ID);
                if (sessionId) {
                    var storedMs = window.sessionStorage.getItem(STORAGE_PREFIX + level);
                    currentElapsedMs = storedMs ? parseInt(storedMs, 10) : 0;
                    if (isNaN(currentElapsedMs) || currentElapsedMs < 0) currentElapsedMs = 0;
                }
            }
        } catch (e) {
            console.error('[levelTimer] Error reading sessionStorage:', e);
        }
    }

    function saveSessionData() {
        try {
            if (sessionId && window.sessionStorage) {
                window.sessionStorage.setItem(STORAGE_SESSION_ID, sessionId);
                window.sessionStorage.setItem(STORAGE_USER_ID, userId || 'anonymous');
                window.sessionStorage.setItem(STORAGE_PREFIX + level, String(Math.floor(currentElapsedMs)));
                window.sessionStorage.setItem(STORAGE_VARIANT_PREFIX + level, variant);
            }
        } catch (e) {
            console.error('[levelTimer] Error saving sessionStorage:', e);
        }
    }

    function clearCachedLevelSolutions() {
        try {
            if (window.localStorage) {
                for (var l = 1; l <= 10; l++) {
                    window.localStorage.removeItem('maze' + l);
                }
                window.localStorage.removeItem('blocky_session_id');
            }
        } catch (e) {
            console.error('[levelTimer] Error clearing cached level solutions:', e);
        }
        try {
            if (window.sessionStorage) {
                window.sessionStorage.removeItem('Vp');
            }
        } catch (e2) {
            console.error('[levelTimer] Error clearing temporary program cache:', e2);
        }
        try {
            if (typeof window.__blockyResetMomotSession === 'function') {
                window.__blockyResetMomotSession();
            }
        } catch (e3) {
            console.error('[levelTimer] Error resetting MoMoT session cache:', e3);
        }
    }

    function clearSessionElapsed() {
        try {
            if (window.sessionStorage) {
                for (var l = 1; l <= 10; l++) {
                    window.sessionStorage.removeItem(STORAGE_PREFIX + l);
                    window.sessionStorage.removeItem(STORAGE_VARIANT_PREFIX + l);
                    window.sessionStorage.removeItem('blocky_solved_level_' + l);
                    window.sessionStorage.removeItem('blocky_scoreboard_lvl_' + l);
                }
                window.sessionStorage.removeItem('blocky_all_levels_completed');
            }
        } catch (e) {}
    }

    function getAllLevelTimes() {
        var result = {};
        var totalMs = 0;
        for (var l = 1; l <= 10; l++) {
            var ms = 0;
            var v = 'none';
            try {
                if (window.sessionStorage) {
                    var val = window.sessionStorage.getItem(STORAGE_PREFIX + l);
                    if (val) ms = parseInt(val, 10) || 0;
                    var storedV = window.sessionStorage.getItem(STORAGE_VARIANT_PREFIX + l);
                    if (storedV) v = storedV;
                }
            } catch (e) {}
            if (l === level) {
                ms = Math.floor(currentElapsedMs);
                v = variant;
            }
            result[l] = { elapsedMs: ms, variant: v };
            totalMs += ms;
        }
        return { levels: result, totalMs: totalMs };
    }

    function sendLevelTime(isBeacon) {
        if (!sessionId) return;
        var payload = JSON.stringify({
            sessionId: sessionId,
            userId: userId || 'anonymous',
            variant: variant,
            level: level,
            elapsedMs: Math.floor(currentElapsedMs),
            timestamp: new Date().toISOString()
        });

        if (isBeacon && window.navigator && window.navigator.sendBeacon) {
            try {
                var blob = new Blob([payload], { type: 'application/json' });
                window.navigator.sendBeacon('/api/level-time', blob);
                return;
            } catch (e) {}
        }

        try {
            if (typeof fetch === 'function') {
                fetch('/api/level-time', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: payload,
                    keepalive: true
                }).then(function(resp) {
                    if (resp.ok) return resp.json();
                }).then(function(data) {
                    if (data && data.levels && window.sessionStorage) {
                        for (var lvlKey in data.levels) {
                            var lvlObj = data.levels[lvlKey];
                            if (lvlObj && typeof lvlObj.elapsedMs === 'number') {
                                var lNum = parseInt(lvlKey, 10);
                                if (lNum !== level) {
                                    window.sessionStorage.setItem(STORAGE_PREFIX + lNum, String(lvlObj.elapsedMs));
                                    if (lvlObj.variant) window.sessionStorage.setItem(STORAGE_VARIANT_PREFIX + lNum, lvlObj.variant);
                                }
                            }
                        }
                    }
                }).catch(function(err) {
                    console.warn('[levelTimer] Sync failed:', err);
                });
            }
        } catch (e) {}
    }

    function lockLevelHeaderLinks() {
        if (!document || !document.querySelectorAll) return;
        if (isAllLevelsCompleted()) {
            try {
                var items = document.querySelectorAll('.level_dot, .level_number, span.level_dot, span.level_number');
                for (var i = 0; i < items.length; i++) {
                    var el = items[i];
                    var lNum = i + 1;
                    if (el.tagName && el.tagName.toLowerCase() === 'span') {
                        var a = document.createElement('a');
                        a.className = el.className;
                        if (el.id) a.id = el.id;
                        a.textContent = el.textContent || String(lNum);
                        a.href = buildLevelUrl(lNum);
                        if (el.parentNode) el.parentNode.replaceChild(a, el);
                    } else if (el.tagName && el.tagName.toLowerCase() === 'a') {
                        el.href = buildLevelUrl(lNum);
                    }
                }
            } catch (e) {}
            return;
        }
        try {
            var anchors = document.querySelectorAll('a.level_dot, a.level_number');
            for (var i = 0; i < anchors.length; i++) {
                var a = anchors[i];
                var span = document.createElement('span');
                span.className = a.className;
                if (a.id) span.id = a.id;
                span.textContent = a.textContent || '';
                if (a.parentNode) {
                    a.parentNode.replaceChild(span, a);
                }
            }
        } catch (e) {}
    }

    function showToast(msg) {
        if (!document || !document.body) return;
        var toast = document.getElementById('levelTimerToast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'levelTimerToast';
            toast.style.position = 'fixed';
            toast.style.bottom = '20px';
            toast.style.left = '50%';
            toast.style.transform = 'translateX(-50%)';
            toast.style.background = 'rgba(215, 58, 73, 0.95)';
            toast.style.color = '#ffffff';
            toast.style.padding = '8px 16px';
            toast.style.borderRadius = '6px';
            toast.style.fontSize = '13px';
            toast.style.fontWeight = '600';
            toast.style.zIndex = '1000002';
            toast.style.boxShadow = '0 4px 12px rgba(0,0,0,0.3)';
            toast.style.transition = 'opacity 0.3s ease';
            document.body.appendChild(toast);
        }
        toast.textContent = msg;
        toast.style.opacity = '1';
        toast.style.display = 'block';
        setTimeout(function() {
            toast.style.opacity = '0';
            setTimeout(function() { toast.style.display = 'none'; }, 300);
        }, 3000);
    }

    function updateNextTaskButtonState() {
        if (!document) return;
        var nextBtn = document.getElementById('timerNextTaskBtn');
        if (!nextBtn) return;

        var isSolved = isLevelSolved(level);
        var isCompleted = isAllLevelsCompleted();
        var isGated = !isSolved && !isCompleted;

        if (isGated) {
            nextBtn.disabled = true;
            nextBtn.style.opacity = '0.5';
            nextBtn.style.cursor = 'not-allowed';
            nextBtn.title = 'Run a program that reaches the goal to unlock the next task';
        } else {
            nextBtn.disabled = false;
            nextBtn.style.opacity = '1';
            nextBtn.style.cursor = 'pointer';
            nextBtn.title = 'Next task';
        }

        var note = document.getElementById('timerGoalNote');
        if (note) {
            note.style.display = (isSolved || isCompleted) ? 'inline-block' : 'none';
        }
    }

    function showAllLevelsCompletedModal() {
        injectStyles();
        var modal = document.getElementById('allLevelsCompletedModal');
        if (!modal && document.body) {
            modal = document.createElement('div');
            modal.id = 'allLevelsCompletedModal';
            modal.style.position = 'fixed';
            modal.style.top = '0'; modal.style.left = '0'; modal.style.right = '0'; modal.style.bottom = '0';
            modal.style.background = 'rgba(0,0,0,0.6)';
            modal.style.zIndex = '1000001';
            modal.style.display = 'flex';
            modal.style.alignItems = 'center';
            modal.style.justifyContent = 'center';
            modal.style.fontFamily = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';

            modal.innerHTML = [
                '<div class="level-timer-modal-box" style="width: 440px;">',
                '  <h3>All 10 Levels Completed!</h3>',
                '  <p>Congratulations! You have completed all tasks in the study.</p>',
                '  <p style="font-size: 12px; color: #57606a;">You can conclude your session to view your final report or stay to freely revisit and improve earlier levels.</p>',
                '  <div class="level-timer-actions" style="margin-top: 16px;">',
                '    <button type="button" class="timer-btn-secondary" id="allLevelsImproveBtn">Improve Previous Solutions</button>',
                '    <button type="button" class="timer-btn-primary" id="allLevelsConcludeBtn">Conclude Session</button>',
                '  </div>',
                '</div>'
            ].join('');
            document.body.appendChild(modal);

            var improveBtn = document.getElementById('allLevelsImproveBtn');
            if (improveBtn) {
                improveBtn.onclick = function() {
                    modal.style.display = 'none';
                    lockLevelHeaderLinks();
                };
            }
            var concludeBtn = document.getElementById('allLevelsConcludeBtn');
            if (concludeBtn) {
                concludeBtn.onclick = function() {
                    modal.style.display = 'none';
                    showSessionReport();
                };
            }
        } else if (modal) {
            modal.style.display = 'flex';
        }
    }

    var headerObserver = null;
    var headerObserverTimeout = null;

    function observeHeaderLinks() {
        lockLevelHeaderLinks();
        if (typeof MutationObserver !== 'undefined' && document && document.body) {
            headerObserver = new MutationObserver(function() {
                lockLevelHeaderLinks();
            });
            try {
                headerObserver.observe(document.body, { childList: true, subtree: true });
            } catch (e) {}
        }
    }

    function showGoalReachedNote() {
        var note = document.getElementById('timerGoalNote');
        if (note) {
            note.style.display = 'inline-block';
        }
    }

    function lockMethod(obj, name, fn) {
        try {
            Object.defineProperty(obj, name, {
                configurable: true,
                enumerable: true,
                get: function() { return fn; },
                set: function() {}
            });
        } catch (e) {
            obj[name] = fn;
        }
    }

    function patchBlocklyDialogs(dialogs) {
        if (!dialogs || dialogs.__blockyPatched) return;
        dialogs.__blockyPatched = true;

        // Maze assigns these methods, then keeps using the same object.
        // Lock them so a later assignment cannot restore the next-level dialog.
        lockMethod(dialogs, 'Xy', function() {});
        lockMethod(dialogs, 'Zs', function() {});
        lockMethod(dialogs, 'vt', function(e) {
            if (e && (e.keyCode === 13 || e.keyCode === 27 || e.keyCode === 32)) {
                if (typeof dialogs.kc === 'function') dialogs.kc(true);
                if (e.stopPropagation) e.stopPropagation();
                if (e.preventDefault) e.preventDefault();
            }
        });
        lockMethod(dialogs, 'Ys', function(e) {
            if (e && (e.keyCode === 13 || e.keyCode === 27 || e.keyCode === 32)) {
                if (typeof dialogs.kc === 'function') dialogs.kc(true);
                if (e.stopPropagation) e.stopPropagation();
                if (e.preventDefault) e.preventDefault();
            }
        });
    }

    function patchBlocklyInterface(iface) {
        if (!iface || iface.__blockyPatched) return;
        iface.__blockyPatched = true;

        // compressed.js assigns window.BlocklyInterface, then overwrites Um
        // with the function that navigates to the next level. Ignore that write.
        lockMethod(iface, 'Um', function() {});
    }

    function hookBlocklyGlobals() {
        var rawDialogs = window.BlocklyDialogs;
        if (rawDialogs) {
            patchBlocklyDialogs(rawDialogs);
        }
        try {
            Object.defineProperty(window, 'BlocklyDialogs', {
                configurable: true,
                enumerable: true,
                get: function() { return rawDialogs; },
                set: function(val) {
                    rawDialogs = val;
                    if (val) patchBlocklyDialogs(val);
                }
            });
        } catch (e) {}

        var rawInterface = window.BlocklyInterface;
        if (rawInterface) {
            patchBlocklyInterface(rawInterface);
        }
        try {
            Object.defineProperty(window, 'BlocklyInterface', {
                configurable: true,
                enumerable: true,
                get: function() { return rawInterface; },
                set: function(val) {
                    rawInterface = val;
                    if (val) patchBlocklyInterface(val);
                }
            });
        } catch (e) {}
    }

    function stopActiveMomotSearch() {
        try {
            window.__momotIsRunning = false;
            var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
            if (bridge && typeof bridge.stopMomotRun === 'function') {
                return bridge.stopMomotRun();
            }
        } catch (e) {
            console.error('[levelTimer] Failed to stop MoMoT search:', e);
        }
        return null;
    }

    function confirmNextTask() {
        if (!isLevelSolved(level) && !isAllLevelsCompleted()) {
            showToast('Run a program that reaches the goal to unlock the next task.');
            return;
        }
        stopActiveMomotSearch();
        injectStyles();
        var modal = document.getElementById('levelTimerConfirmModal');
        if (!modal && document.body) {
            modal = document.createElement('div');
            modal.id = 'levelTimerConfirmModal';
            modal.style.display = 'flex';
            modal.innerHTML = [
                '<div class="level-timer-modal-box">',
                '  <h3>Go to the next task?</h3>',
                '  <p>Time on this task will be saved and the page will move to the next level.</p>',
                '  <div class="level-timer-actions">',
                '    <button type="button" class="timer-btn-secondary" id="levelTimerConfirmStayBtn">Stay</button>',
                '    <button type="button" class="timer-btn-primary" id="levelTimerConfirmNextBtn">Next task</button>',
                '  </div>',
                '</div>'
            ].join('');
            document.body.appendChild(modal);

            var stayBtn = document.getElementById('levelTimerConfirmStayBtn');
            if (stayBtn) {
                stayBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    modal.style.display = 'none';
                });
            }

            var nextBtn = document.getElementById('levelTimerConfirmNextBtn');
            if (nextBtn) {
                nextBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    modal.style.display = 'none';
                    goToNextTask();
                });
            }
        } else if (modal) {
            modal.style.display = 'flex';
        }
    }

    function goToNextTask() {
        if (!isLevelSolved(level) && !isAllLevelsCompleted()) {
            showToast('Run a program that reaches the goal to unlock the next task.');
            return;
        }
        saveSessionData();
        sendLevelTime(true);
        var pStop = stopActiveMomotSearch();
        var nextLevel = level + 1;
        setStoredLevel(nextLevel);
        var targetUrl = buildLevelUrl(nextLevel);

        var navigated = false;
        var doNav = function() {
            if (!navigated) {
                navigated = true;
                navigateTo(targetUrl);
            }
        };

        if (pStop && typeof pStop.then === 'function') {
            var navTimer = setTimeout(doNav, 300);
            pStop.then(function() {
                clearTimeout(navTimer);
                doNav();
            }).catch(function() {
                clearTimeout(navTimer);
                doNav();
            });
        } else {
            doNav();
        }
    }

    function injectStyles() {
        if (document.getElementById('level-timer-style')) return;
        var style = document.createElement('style');
        style.id = 'level-timer-style';
        style.textContent = [
            '#levelTimerWidget {',
            '  position: fixed;',
            '  top: 8px;',
            '  right: 12px;',
            '  z-index: 99999;',
            '  display: flex;',
            '  align-items: center;',
            '  gap: 8px;',
            '  background: rgba(255, 255, 255, 0.95);',
            '  padding: 4px 10px;',
            '  border-radius: 16px;',
            '  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.16);',
            '  border: 1px solid #c0c6cc;',
            '  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;',
            '  font-size: 13px;',
            '  color: #24292f;',
            '  user-select: none;',
            '}',
            '#levelTimerWidget .timer-session {',
            '  font-weight: 600;',
            '  color: #0969da;',
            '  max-width: 140px;',
            '  overflow: hidden;',
            '  text-overflow: ellipsis;',
            '  white-space: nowrap;',
            '}',
            '#levelTimerWidget .timer-level {',
            '  font-weight: 500;',
            '  color: #57606a;',
            '}',
            '#levelTimerWidget .timer-clock {',
            '  display: none;',
            '  font-variant-numeric: tabular-nums;',
            '  font-weight: 700;',
            '  color: #1a7f37;',
            '  background: #dafbe1;',
            '  padding: 2px 6px;',
            '  border-radius: 6px;',
            '  border: 1px solid rgba(27, 31, 36, 0.15);',
            '}',
            '#levelTimerWidget .timer-goal-note {',
            '  display: none;',
            '  font-weight: 600;',
            '  font-size: 11px;',
            '  color: #1a7f37;',
            '  background: #dafbe1;',
            '  padding: 2px 8px;',
            '  border-radius: 6px;',
            '  border: 1px solid rgba(27, 31, 36, 0.15);',
            '}',
            '#levelTimerWidget .timer-btn {',
            '  background: #f6f8fa;',
            '  border: 1px solid #d0d7de;',
            '  border-radius: 6px;',
            '  padding: 2px 8px;',
            '  font-size: 11px;',
            '  font-weight: 600;',
            '  color: #24292f;',
            '  cursor: pointer;',
            '}',
            '#levelTimerWidget .timer-btn:hover {',
            '  background: #eef1f4;',
            '  border-color: rgba(27, 31, 36, 0.15);',
            '}',
            '#levelTimerWidget .timer-next-btn {',
            '  background: #0969da !important;',
            '  color: #ffffff !important;',
            '  border-color: #044289 !important;',
            '}',
            '#levelTimerWidget .timer-next-btn:hover {',
            '  background: #044289 !important;',
            '}',
            '#levelTimerWidget .timer-finish-btn {',
            '  background: #8250df !important;',
            '  color: #ffffff !important;',
            '  border-color: #6e40c9 !important;',
            '}',
            '#levelTimerWidget .timer-finish-btn:hover {',
            '  background: #6e40c9 !important;',
            '}',
            '#levelTimerModal, #levelTimerReportModal, #levelTimerConfirmModal {',
            '  position: fixed;',
            '  top: 0; left: 0; right: 0; bottom: 0;',
            '  background: rgba(0, 0, 0, 0.55);',
            '  z-index: 1000000;',
            '  display: flex;',
            '  align-items: center;',
            '  justify-content: center;',
            '  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;',
            '}',
            '.level-timer-modal-box {',
            '  background: #ffffff;',
            '  padding: 22px 26px;',
            '  border-radius: 12px;',
            '  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.35);',
            '  width: 360px;',
            '  max-width: 92vw;',
            '  box-sizing: border-box;',
            '}',
            '.level-timer-report-box {',
            '  background: #ffffff;',
            '  padding: 24px 28px;',
            '  border-radius: 12px;',
            '  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.35);',
            '  width: 520px;',
            '  max-width: 94vw;',
            '  max-height: 90vh;',
            '  overflow-y: auto;',
            '  box-sizing: border-box;',
            '}',
            '.level-timer-modal-box h3, .level-timer-report-box h3 {',
            '  margin: 0 0 8px 0;',
            '  font-size: 18px;',
            '  color: #24292f;',
            '}',
            '.level-timer-modal-box p, .level-timer-report-box p {',
            '  margin: 0 0 14px 0;',
            '  font-size: 13px;',
            '  color: #57606a;',
            '}',
            '.level-timer-modal-box input {',
            '  width: 100%;',
            '  box-sizing: border-box;',
            '  padding: 8px 12px;',
            '  font-size: 14px;',
            '  border: 1px solid #d0d7de;',
            '  border-radius: 6px;',
            '  margin-bottom: 16px;',
            '  outline: none;',
            '}',
            '.level-timer-modal-box input:focus {',
            '  border-color: #0969da;',
            '  box-shadow: 0 0 0 3px rgba(9, 105, 218, 0.2);',
            '}',
            '.level-timer-actions {',
            '  display: flex;',
            '  justify-content: flex-end;',
            '  gap: 8px;',
            '  flex-wrap: wrap;',
            '}',
            '.timer-btn-primary {',
            '  background: #2da44e;',
            '  color: #ffffff;',
            '  border: 1px solid rgba(27, 31, 36, 0.15);',
            '  border-radius: 6px;',
            '  padding: 6px 14px;',
            '  font-size: 13px;',
            '  font-weight: 600;',
            '  cursor: pointer;',
            '}',
            '.timer-btn-primary:hover { background: #2c974b; }',
            '.timer-btn-secondary {',
            '  background: #f6f8fa;',
            '  color: #24292f;',
            '  border: 1px solid #d0d7de;',
            '  border-radius: 6px;',
            '  padding: 6px 12px;',
            '  font-size: 13px;',
            '  font-weight: 600;',
            '  cursor: pointer;',
            '}',
            '.timer-btn-secondary:hover { background: #eef1f4; }',
            '.report-table {',
            '  width: 100%;',
            '  border-collapse: collapse;',
            '  margin: 12px 0 16px 0;',
            '  font-size: 13px;',
            '}',
            '.report-table th, .report-table td {',
            '  padding: 6px 10px;',
            '  border-bottom: 1px solid #e1e4e8;',
            '  text-align: left;',
            '}',
            '.report-table th {',
            '  background: #f6f8fa;',
            '  font-weight: 600;',
            '  color: #24292f;',
            '}',
            '.report-total-row {',
            '  font-weight: 700;',
            '  background: #f1f8ff;',
            '}'
        ].join('\n');
        document.head.appendChild(style);
    }

    function ensureWidget() {
        injectStyles();
        var widget = document.getElementById('levelTimerWidget');
        var finishBtnText = (isAllLevelsCompleted() && level < 10) ? 'Conclude Session' : 'Finish task';
        if (!widget && document.body) {
            widget = document.createElement('div');
            widget.id = 'levelTimerWidget';
            var actionBtnHtml = (level === 10 || isAllLevelsCompleted()) ?
                '<button type="button" class="timer-btn timer-finish-btn" id="timerFinishSessionBtn" title="Finish task and view report">' + finishBtnText + '</button>' :
                '<button type="button" class="timer-btn timer-next-btn" id="timerNextTaskBtn" title="Next task">Next task</button>';
            widget.innerHTML = [
                '<span class="timer-session" id="timerSessionDisplay" title="' + (sessionId || '') + '">' + (userId || 'No Session') + '</span>',
                '<span class="timer-level">Lvl ' + level + '</span>',
                '<span class="timer-clock" id="timerClockDisplay" style="display: none;">' + formatDisplayTime(currentElapsedMs) + '</span>',
                '<span class="timer-goal-note" id="timerGoalNote"' + ((isLevelSolved(level) || isAllLevelsCompleted()) ? ' style="display: inline-block;"' : ' style="display: none;"') + '>Goal reached. You can keep improving this solution.</span>',
                actionBtnHtml,
                '<button type="button" class="timer-btn" id="timerNewSessionBtn" title="Start a new session">New Session</button>'
            ].join('');
            document.body.appendChild(widget);

            var nextBtn = document.getElementById('timerNextTaskBtn');
            if (nextBtn) {
                nextBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    confirmNextTask();
                });
            }

            var finBtn = document.getElementById('timerFinishSessionBtn');
            if (finBtn) {
                finBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    showSessionReport();
                });
            }

            var newBtn = document.getElementById('timerNewSessionBtn');
            if (newBtn) {
                newBtn.addEventListener('click', function(e) {
                    e.stopPropagation();
                    promptUserForSession();
                });
            }
            updateNextTaskButtonState();
        } else if (widget) {
            var sessEl = document.getElementById('timerSessionDisplay');
            if (sessEl) {
                sessEl.textContent = userId || 'No Session';
                sessEl.title = sessionId || '';
            }
            var clockEl = document.getElementById('timerClockDisplay');
            if (clockEl) {
                clockEl.textContent = formatDisplayTime(currentElapsedMs);
            }
            var noteEl = document.getElementById('timerGoalNote');
            if (noteEl) {
                noteEl.style.display = (isLevelSolved(level) || isAllLevelsCompleted()) ? 'inline-block' : 'none';
            }
            var newBtnRef = document.getElementById('timerNewSessionBtn');
            var existingNextBtn = document.getElementById('timerNextTaskBtn');
            var existingFinBtn = document.getElementById('timerFinishSessionBtn');
            if (level === 10 || isAllLevelsCompleted()) {
                if (level === 10 && existingNextBtn) existingNextBtn.remove();
                if (!existingFinBtn && newBtnRef) {
                    var btn = document.createElement('button');
                    btn.type = 'button';
                    btn.className = 'timer-btn timer-finish-btn';
                    btn.id = 'timerFinishSessionBtn';
                    btn.title = 'Finish task and view report';
                    btn.textContent = 'Conclude Session';
                    btn.addEventListener('click', function(e) {
                        e.stopPropagation();
                        showSessionReport();
                    });
                    widget.insertBefore(btn, newBtnRef);
                }
            } else {
                if (existingFinBtn) existingFinBtn.remove();
                if (!existingNextBtn && newBtnRef) {
                    var nextB = document.createElement('button');
                    nextB.type = 'button';
                    nextB.className = 'timer-btn timer-next-btn';
                    nextB.id = 'timerNextTaskBtn';
                    nextB.title = 'Next task';
                    nextB.textContent = 'Next task';
                    nextB.addEventListener('click', function(e) {
                        e.stopPropagation();
                        confirmNextTask();
                    });
                    widget.insertBefore(nextB, newBtnRef);
                }
            }
            updateNextTaskButtonState();
        }
    }

    function promptUserForSession() {
        stopActiveMomotSearch();
        injectStyles();
        var modal = document.getElementById('levelTimerModal');
        if (!modal && document.body) {
            modal = document.createElement('div');
            modal.id = 'levelTimerModal';
            modal.innerHTML = [
                '<div class="level-timer-modal-box">',
                '  <h3>Start New Session</h3>',
                '  <p>Enter your User ID to track level progress:</p>',
                '  <input type="text" id="levelTimerUserIdInput" placeholder="User ID (e.g. player1)" maxlength="32" />',
                '  <div class="level-timer-actions">',
                '    <button type="button" class="timer-btn-primary" id="levelTimerModalSubmit">Start Session</button>',
                '  </div>',
                '</div>'
            ].join('');
            document.body.appendChild(modal);

            var input = document.getElementById('levelTimerUserIdInput');
            if (input) {
                input.value = userId || '';
                setTimeout(function() { input.focus(); }, 50);
            }

            function submit() {
                var val = (input ? input.value : '').trim();
                if (!val) val = 'player_' + Math.floor(Math.random() * 1000);

                // Send current session final state before starting new one
                if (sessionId) {
                    sendLevelTime(false);
                }

                userId = val;
                sessionId = generateSessionId(userId);
                clearSessionElapsed();
                currentElapsedMs = 0;
                setStoredLevel(1);
                saveSessionData();
                clearCachedLevelSolutions();
                getLevelArrivedAt(); // a new session starts at level 1 now

                var m = document.getElementById('levelTimerModal');
                if (m) m.style.display = 'none';
                isModalOpen = false;
                lastTick = Date.now();
                ensureWidget();
                sendLevelTime(false);
                try {
                    if (level !== 1) {
                        navigateTo(buildLevelUrl(1));
                    } else {
                        window.location.reload();
                    }
                } catch (e4) {}
            }

            var btn = document.getElementById('levelTimerModalSubmit');
            if (btn) {
                btn.onclick = submit;
            }
            if (input) {
                input.onkeydown = function(e) {
                    if (e.key === 'Enter') submit();
                };
            }
        } else if (modal) {
            modal.style.display = 'flex';
        }
        isModalOpen = true;
    }

    function showSessionReport() {
        stopActiveMomotSearch();
        sendLevelTime(false);
        var data = getAllLevelTimes();
        var levelsData = data.levels;
        var totalMs = data.totalMs;

        injectStyles();
        var reportModal = document.getElementById('levelTimerReportModal');
        if (!reportModal) {
            reportModal = document.createElement('div');
            reportModal.id = 'levelTimerReportModal';
            document.body.appendChild(reportModal);
        }

        var rowsHtml = '';
        for (var l = 1; l <= 10; l++) {
            var item = levelsData[l];
            var displayTime = item.elapsedMs > 0 ? formatDisplayTime(item.elapsedMs) : '-';
            var variantDisplay = item.elapsedMs > 0 ? item.variant : '-';
            rowsHtml += [
                '<tr>',
                '  <td>Level ' + l + '</td>',
                '  <td>' + displayTime + '</td>',
                '  <td>' + variantDisplay + '</td>',
                '</tr>'
            ].join('');
        }

        reportModal.innerHTML = [
            '<div class="level-timer-report-box">',
            '  <h3>Session Timing Report</h3>',
            '  <p><strong>Session ID:</strong> ' + (sessionId || 'None') + ' &nbsp;|&nbsp; <strong>User ID:</strong> ' + (userId || 'None') + '</p>',
            '  <table class="report-table">',
            '    <thead><tr><th>Level</th><th>Time Elapsed</th><th>Variant</th></tr></thead>',
            '    <tbody>',
            rowsHtml,
            '      <tr class="report-total-row"><td>Total Duration</td><td colspan="2">' + formatDisplayTime(totalMs) + ' (' + (Math.round(totalMs / 1000)) + 's)</td></tr>',
            '    </tbody>',
            '  </table>',
            '  <div class="level-timer-actions">',
            '    <button type="button" class="timer-btn-secondary" id="reportDownloadHtmlBtn">Download HTML</button>',
            '    <button type="button" class="timer-btn-secondary" id="reportDownloadJsonBtn">Download JSON</button>',
            '    <button type="button" class="timer-btn-primary" id="reportNewSessionBtn">New Session</button>',
            '    <button type="button" class="timer-btn-secondary" id="reportCloseBtn">Close</button>',
            '  </div>',
            '</div>'
        ].join('');
        reportModal.style.display = 'flex';

        // Close button handler
        var closeBtn = document.getElementById('reportCloseBtn');
        if (closeBtn) {
            closeBtn.onclick = function() {
                reportModal.style.display = 'none';
            };
        }

        // New session handler
        var newSessBtn = document.getElementById('reportNewSessionBtn');
        if (newSessBtn) {
            newSessBtn.onclick = function() {
                reportModal.style.display = 'none';
                promptUserForSession();
            };
        }

        // Download HTML handler
        var dlHtmlBtn = document.getElementById('reportDownloadHtmlBtn');
        if (dlHtmlBtn) {
            dlHtmlBtn.onclick = function() {
                var htmlDoc = generateHtmlReport(sessionId, userId, levelsData, totalMs);
                downloadBlob(htmlDoc, 'session_' + (sessionId || 'report') + '.html', 'text/html');
            };
        }

        // Download JSON handler
        var dlJsonBtn = document.getElementById('reportDownloadJsonBtn');
        if (dlJsonBtn) {
            dlJsonBtn.onclick = function() {
                var jsonDoc = JSON.stringify({
                    sessionId: sessionId,
                    userId: userId,
                    totalDurationMs: totalMs,
                    exportedAt: new Date().toISOString(),
                    levels: levelsData
                }, null, 2);
                downloadBlob(jsonDoc, 'session_' + (sessionId || 'data') + '.json', 'application/json');
            };
        }
    }

    function generateHtmlReport(sId, uId, lvls, totMs) {
        var rows = '';
        for (var l = 1; l <= 10; l++) {
            var item = lvls[l];
            var displayTime = item.elapsedMs > 0 ? formatDisplayTime(item.elapsedMs) : '-';
            var variantDisplay = item.elapsedMs > 0 ? item.variant : '-';
            rows += '<tr><td>Level ' + l + '</td><td>' + displayTime + '</td><td>' + variantDisplay + '</td></tr>\n';
        }

        return [
            '<!DOCTYPE html>',
            '<html>',
            '<head>',
            '  <meta charset="utf-8">',
            '  <title>Blockly Maze Session Report - ' + (sId || '') + '</title>',
            '  <style>',
            '    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif; padding: 30px; background: #f6f8fa; color: #24292f; }',
            '    .container { max-width: 600px; margin: 0 auto; background: #ffffff; padding: 24px 30px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }',
            '    h1 { font-size: 20px; margin-top: 0; color: #0969da; }',
            '    .meta { font-size: 14px; margin-bottom: 20px; color: #57606a; }',
            '    table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }',
            '    th, td { padding: 8px 12px; border: 1px solid #d0d7de; text-align: left; font-size: 14px; }',
            '    th { background: #f6f8fa; font-weight: 600; }',
            '    tr.total { font-weight: bold; background: #dafbe1; }',
            '    footer { font-size: 12px; color: #8c959f; text-align: center; }',
            '  </style>',
            '</head>',
            '<body>',
            '  <div class="container">',
            '    <h1>Blockly Maze Session Report</h1>',
            '    <div class="meta">',
            '      <strong>Session ID:</strong> ' + (sId || 'None') + '<br>',
            '      <strong>User ID:</strong> ' + (uId || 'None') + '<br>',
            '      <strong>Generated:</strong> ' + new Date().toUTCString(),
            '    </div>',
            '    <table>',
            '      <thead><tr><th>Level</th><th>Elapsed Time</th><th>Variant</th></tr></thead>',
            '      <tbody>',
            rows,
            '        <tr class="total"><td>Total Duration</td><td colspan="2">' + formatDisplayTime(totMs) + ' (' + Math.round(totMs / 1000) + 's)</td></tr>',
            '      </tbody>',
            '    </table>',
            '    <footer>Blockly Maze Modeling Research Framework</footer>',
            '  </div>',
            '</body>',
            '</html>'
        ].join('\n');
    }

    function downloadBlob(content, filename, mimeType) {
        var blob = new Blob([content], { type: mimeType });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    function tick() {
        ensureWidget();
        lockLevelHeaderLinks();
        if (window.BlocklyDialogs) patchBlocklyDialogs(window.BlocklyDialogs);
        if (window.BlocklyInterface) patchBlocklyInterface(window.BlocklyInterface);

        if (!sessionId) {
            var modal = document.getElementById('levelTimerModal');
            if (!modal || modal.style.display === 'none') {
                promptUserForSession();
            }
        }
        if (isModalOpen || !sessionId) {
            lastTick = Date.now();
            return;
        }

        var now = Date.now();
        if (lastTick === null) lastTick = now;
        var delta = now - lastTick;
        lastTick = now;

        if (!document.hidden && delta > 0 && delta < 5000) {
            currentElapsedMs += delta;
            saveSessionData();
            var clockEl = document.getElementById('timerClockDisplay');
            if (clockEl) {
                clockEl.textContent = formatDisplayTime(currentElapsedMs);
            }
        }
    }

    function getCurrentWorkspaceXml() {
        try {
            if (window.BlocklyInterface && typeof window.BlocklyInterface.getCode === 'function') {
                var code = window.BlocklyInterface.getCode();
                if (code && typeof code === 'string' && code.trim().length > 0) {
                    return code;
                }
            }
        } catch (e) {}
        try {
            if (window.localStorage) {
                var stored = window.localStorage.getItem('maze' + level);
                if (stored && typeof stored === 'string' && stored.trim().length > 0) {
                    return stored;
                }
            }
        } catch (e2) {}
        return '';
    }

    // Wall-clock time of the first arrival at this level in the current timer session (kept per session and level).
    function getLevelArrivedAt() {
        var key = 'blocky_level_arrived_' + sessionId + '_' + level;
        try {
            if (window.sessionStorage) {
                var stored = window.sessionStorage.getItem(key);
                if (stored) return stored;
                var now = new Date().toISOString();
                window.sessionStorage.setItem(key, now);
                return now;
            }
        } catch (e) {}
        return null;
    }

    function recordProgramRun() {
        if (!sessionId) return;
        try {
            var doRun = function() {
                var sid = null;
                try { if (window.localStorage) sid = window.localStorage.getItem('blocky_session_id'); } catch (e) {}
                var headers = {
                    'Content-Type': 'application/json',
                    'X-Timer-Session-ID': sessionId,
                    'X-Level-ID': String(level)
                };
                if (sid) {
                    headers['X-Session-ID'] = sid;
                }
                if (typeof fetch === 'function') {
                    var nowIso = new Date().toISOString();
                    var currentXml = getCurrentWorkspaceXml();
                    var pathToken = (typeof window.__nextPathToken === 'function') ? window.__nextPathToken() : null;
                    fetch('/api/simulation/run', {
                        method: 'POST',
                        headers: headers,
                        body: JSON.stringify({
                            timerSessionId: sessionId,
                            level: level,
                            recordExecution: true,
                            variant: getVariant(),
                            timestamp: nowIso,
                            levelElapsedMs: Math.floor(currentElapsedMs),
                            levelArrivedAt: getLevelArrivedAt(),
                            xml: currentXml
                        })
                    }).then(function(r) {
                        if (r.ok) return r.json();
                    }).then(function(data) {
                        var isLatest = (pathToken === null || typeof window.__isLatestPathToken !== 'function' || window.__isLatestPathToken(pathToken));
                        if (isLatest && data && data.newPath && typeof window.__ifRender === 'function') {
                            window.__runPathShown = true; // see BLOCKY_LIVE_PATH in blockyUIOverlay.js
                            window.__injectNewPath = data.newPath;
                            window.__ifRender();
                        }
                        // Run Program: keep the memory / power readout in step with the program that just ran.
                        if (data && (data.levelId === undefined || data.levelId === level)) {
                            if (typeof window.__lastObservedXml !== 'undefined') {
                                window.__lastObservedXml = currentXml;
                            }
                            if (typeof data.memory === 'number' && data.memory >= 0 &&
                                typeof data.power === 'number' && data.power >= 0) {
                                if (typeof window.__consumptionUpdate === 'function') {
                                    window.__consumptionUpdate(data.memory, data.power);
                                }
                                if (typeof window.__scoreboardAddRun === 'function') {
                                    window.__scoreboardAddRun({
                                        goalReached: !!data.goalReached,
                                        memory: data.memory,
                                        power: data.power,
                                        xml: currentXml
                                    });
                                }
                            }
                        }
                        if (data && data.goalReached && (data.levelId === undefined || data.levelId === level)) {
                            markLevelSolved(level);
                        }
                    }).catch(function(err) {});
                }
            };

            if (typeof window.__syncCurrentLevelStateToBackend === 'function') {
                window.__syncCurrentLevelStateToBackend(doRun);
            } else {
                doRun();
            }
        } catch (e2) {}
    }

    function buttonFromClick(target, id) {
        var node = target;
        while (node && node !== document.body && node !== document) {
            if (node.id === id) return node;
            node = node.parentNode;
        }
        return null;
    }

    function hookRunButton() {
        if (window.__runButtonHookBound) return;
        window.__runButtonHookBound = true;
        // Clicking Reset hides that button and shows Run Program in the same place.
        // The browser then delivers a second click to Run Program. Ignore that follow-up.
        var suppressRunClickUntil = 0;
        document.addEventListener('click', function(e) {
            if (buttonFromClick(e.target, 'resetButton')) {
                suppressRunClickUntil = Date.now() + 400;
                return;
            }
            var runBtn = buttonFromClick(e.target, 'runButton');
            if (!runBtn) return;
            if (runBtn.style && runBtn.style.display === 'none') return;
            var label = (runBtn.textContent || '').toLowerCase();
            if (label.indexOf('reset') !== -1 && label.indexOf('run') === -1) return;
            if (Date.now() < suppressRunClickUntil) return;
            recordProgramRun();
        }, true);
    }

    function init() {
        loadSessionData();
        ensureWidget();
        hookRunButton();
        hookBlocklyGlobals();
        observeHeaderLinks();

        if (!sessionId) {
            promptUserForSession();
        } else {
            lastTick = Date.now();
            getLevelArrivedAt(); // stamp the arrival at this level
            sendLevelTime(false);
        }

        if (timerInterval) clearInterval(timerInterval);
        timerInterval = setInterval(tick, 500);
        if (timerInterval && typeof timerInterval.unref === 'function') {
            timerInterval.unref();
        }

        if (syncInterval) clearInterval(syncInterval);
        syncInterval = setInterval(function() {
            sendLevelTime(false);
        }, 10000);
        if (syncInterval && typeof syncInterval.unref === 'function') {
            syncInterval.unref();
        }

        window.__levelTimerCleanup = function() {
            if (timerInterval) {
                clearInterval(timerInterval);
                timerInterval = null;
            }
            if (syncInterval) {
                clearInterval(syncInterval);
                syncInterval = null;
            }
            if (headerObserver) {
                try { headerObserver.disconnect(); } catch (e) {}
                headerObserver = null;
            }
            if (headerObserverTimeout) {
                clearTimeout(headerObserverTimeout);
                headerObserverTimeout = null;
            }
        };

        window.__levelTimerIsLevelSolved = isLevelSolved;
        window.__levelTimerMarkLevelSolved = markLevelSolved;
        window.__levelTimerIsAllLevelsCompleted = isAllLevelsCompleted;
        window.__levelTimerShowAllLevelsCompletedModal = showAllLevelsCompletedModal;

        document.addEventListener('visibilitychange', function() {
            if (document.hidden) {
                sendLevelTime(false);
            } else {
                lastTick = Date.now();
            }
        });

        window.addEventListener('pagehide', function() {
            sendLevelTime(true);
        });
        window.addEventListener('beforeunload', function() {
            sendLevelTime(true);
        });
    }

    if (document.body) {
        init();
    } else if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})(typeof window !== 'undefined' ? window : this, typeof document !== 'undefined' ? document : (typeof window !== 'undefined' ? window.document : undefined));