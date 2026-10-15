/**
 * scoreboard.js
 * Model Run Scoreboard tracking Goal Reached, Memory Consumption, and Power Consumption
 * per level for maze.html and maze-original.html, with reload capability.
 */
(function(root, factory) {
    if (typeof define === 'function' && define.amd) {
        define([], factory);
    } else if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.BlockyScoreboard = factory();
    }
}(typeof self !== 'undefined' ? self : this, function() {
    'use strict';

    var STORAGE_PREFIX = 'blocky_scoreboard_lvl_';
    var memoryStore = {};

    function getStorage() {
        try {
            if (typeof window !== 'undefined' && window.sessionStorage) {
                return window.sessionStorage;
            }
        } catch (e) {}
        return null;
    }

    function getLevel() {
        if (typeof window === 'undefined') return 1;
        try {
            if (typeof window.K === 'number' && window.K >= 1 && window.K <= 10) {
                return window.K;
            }
            var match = window.location && window.location.search ? window.location.search.match(/[?&]level=([^&]+)/) : null;
            var parsed = match ? parseInt(decodeURIComponent(match[1].replace(/\+/g, '%20')), 10) : 1;
            if (!isNaN(parsed) && parsed >= 1 && parsed <= 10) return parsed;
        } catch (e) {}
        return 1;
    }

    function formatVal(v) {
        return (typeof v === 'number' && !isNaN(v) && v >= 0) ? v : -1;
    }

    function formatDisplayVal(v, unit) {
        return (typeof v === 'number' && !isNaN(v) && v >= 0) ? (v + ' ' + unit) : '-';
    }

    function sanitizeRuns(rawList) {
        if (!Array.isArray(rawList)) return { runs: [], modified: true };
        var cleaned = [];
        var modified = false;
        for (var i = 0; i < rawList.length; i++) {
            var r = rawList[i];
            if (r && typeof r === 'object' &&
                typeof r.memory === 'number' && !isNaN(r.memory) && r.memory >= 0 &&
                typeof r.power === 'number' && !isNaN(r.power) && r.power >= 0) {
                var nextId = cleaned.length + 1;
                if (r.id !== nextId) {
                    r.id = nextId;
                    modified = true;
                }
                cleaned.push(r);
            } else {
                modified = true;
            }
        }
        return { runs: cleaned, modified: modified };
    }

    function getRuns(lvl) {
        var level = (typeof lvl === 'number') ? lvl : getLevel();
        var storage = getStorage();
        if (storage) {
            try {
                var raw = storage.getItem(STORAGE_PREFIX + level);
                if (raw) {
                    var parsed = JSON.parse(raw);
                    if (Array.isArray(parsed)) {
                        var result = sanitizeRuns(parsed);
                        if (result.modified) {
                            saveRuns(level, result.runs);
                        }
                        return result.runs;
                    }
                }
            } catch (e) {}
            return [];
        }
        var memList = memoryStore[level] || [];
        var memResult = sanitizeRuns(memList);
        if (memResult.modified) {
            memoryStore[level] = memResult.runs;
        }
        return memResult.runs;
    }

    function saveRuns(lvl, runs) {
        var level = (typeof lvl === 'number') ? lvl : getLevel();
        var storage = getStorage();
        if (storage) {
            try {
                storage.setItem(STORAGE_PREFIX + level, JSON.stringify(runs));
            } catch (e) {}
        } else {
            memoryStore[level] = runs;
        }
    }

    function addRun(runData) {
        var level = getLevel();
        var runs = getRuns(level);
        var memVal = formatVal(runData ? runData.memory : -1);
        var powVal = formatVal(runData ? runData.power : -1);
        if (memVal < 0 || powVal < 0) {
            return null;
        }
        var newRun = {
            id: runs.length + 1,
            timestamp: Date.now(),
            goalReached: !!(runData && runData.goalReached),
            memory: memVal,
            power: powVal,
            xml: (runData && typeof runData.xml === 'string') ? runData.xml : ''
        };
        runs.push(newRun);
        saveRuns(level, runs);
        if (typeof window !== 'undefined') {
            updateUI();
        }
        return newRun;
    }

    function markLastRunGoalReached(lvl) {
        var level = (typeof lvl === 'number') ? lvl : getLevel();
        var runs = getRuns(level);
        if (runs.length > 0) {
            runs[runs.length - 1].goalReached = true;
            saveRuns(level, runs);
            if (typeof window !== 'undefined') {
                updateUI();
            }
        }
    }

    function clearRuns(lvl) {
        var level = (typeof lvl === 'number') ? lvl : getLevel();
        saveRuns(level, []);
        if (typeof window !== 'undefined') {
            updateUI();
        }
    }

    function loadRun(runId, lvl) {
        var level = (typeof lvl === 'number') ? lvl : getLevel();
        var runs = getRuns(level);
        var target = null;
        for (var i = 0; i < runs.length; i++) {
            if (runs[i].id === runId) {
                target = runs[i];
                break;
            }
        }
        if (!target) return false;

        if (typeof window !== 'undefined') {
            if (target.xml) {
                if (typeof window.applyXmlToWorkspace === 'function') {
                    window.applyXmlToWorkspace(target.xml);
                } else if (window.BlocklyInterface && typeof window.BlocklyInterface.setCode === 'function') {
                    window.BlocklyInterface.setCode(target.xml);
                } else {
                    var ws = (window.BlocklyInterface && window.BlocklyInterface.getWorkspace && window.BlocklyInterface.getWorkspace()) ||
                             (window.Blockly && window.Blockly.getMainWorkspace && window.Blockly.getMainWorkspace());
                    if (ws && window.Blockly && window.Blockly.Xml) {
                        ws.clear();
                        var dom = window.Blockly.Xml.textToDom(target.xml);
                        window.Blockly.Xml.domToWorkspace(dom, ws);
                    }
                }
                var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                if (bridge && typeof bridge.syncModel === 'function') {
                    bridge.syncModel(target.xml, false);
                }
            }

            if (typeof window.__consumptionUpdate === 'function') {
                window.__consumptionUpdate(target.memory, target.power);
            }

            highlightLoadedRow(runId);
        }
        return true;
    }

    function highlightLoadedRow(runId) {
        if (typeof document === 'undefined') return;
        var statusEl = document.getElementById('scoreboardStatusMsg');
        if (statusEl) {
            statusEl.textContent = 'Loaded Run #' + runId;
        }
        var rows = document.querySelectorAll('#scoreboardTableBody tr');
        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            if (row.getAttribute('data-run-id') === String(runId)) {
                row.style.background = 'rgba(9, 105, 218, 0.3)';
            } else {
                row.style.background = 'transparent';
            }
        }
    }

    // DOM UI Rendering
    var isPanelVisible = false;

    function buildPanel() {
        if (typeof document === 'undefined') return null;
        var panel = document.createElement('div');
        panel.id = '__scoreboardPanel';
        panel.style.position = 'fixed';
        panel.style.top = '90px';
        panel.style.right = '20px';
        panel.style.width = '420px';
        panel.style.maxWidth = '92vw';
        panel.style.maxHeight = '75vh';
        panel.style.zIndex = '99990';
        panel.style.background = 'rgba(40,40,40,0.92)';
        panel.style.border = '1px solid rgba(255,255,255,0.2)';
        panel.style.borderRadius = '8px';
        panel.style.boxShadow = '0 8px 24px rgba(0,0,0,0.4)';
        panel.style.color = '#fff';
        panel.style.fontFamily = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
        panel.style.fontSize = '12px';
        panel.style.display = 'none';
        panel.style.flexDirection = 'column';
        panel.style.overflow = 'hidden';

        var header = document.createElement('div');
        header.style.padding = '8px 12px';
        header.style.background = 'rgba(20,20,20,0.6)';
        header.style.borderBottom = '1px solid rgba(255,255,255,0.15)';
        header.style.display = 'flex';
        header.style.alignItems = 'center';
        header.style.justifyContent = 'space-between';
        header.style.userSelect = 'none';

        var title = document.createElement('span');
        title.id = 'scoreboardPanelTitle';
        title.style.fontWeight = '700';
        title.style.fontSize = '13px';
        title.textContent = 'Run History';

        var statusMsg = document.createElement('span');
        statusMsg.id = 'scoreboardStatusMsg';
        statusMsg.style.fontSize = '11px';
        statusMsg.style.color = '#58a6ff';
        statusMsg.style.marginLeft = 'auto';
        statusMsg.style.marginRight = '10px';

        var closeBtn = document.createElement('button');
        closeBtn.textContent = '✕';
        closeBtn.style.background = 'transparent';
        closeBtn.style.border = 'none';
        closeBtn.style.color = '#ccc';
        closeBtn.style.fontSize = '14px';
        closeBtn.style.cursor = 'pointer';
        closeBtn.onclick = function() {
            togglePanel(false);
        };

        header.appendChild(title);
        header.appendChild(statusMsg);
        header.appendChild(closeBtn);
        panel.appendChild(header);

        var body = document.createElement('div');
        body.style.padding = '8px';
        body.style.overflowY = 'auto';
        body.style.flex = '1';

        var table = document.createElement('table');
        table.style.width = '100%';
        table.style.borderCollapse = 'collapse';
        table.style.textAlign = 'left';

        var thead = document.createElement('thead');
        thead.innerHTML = '<tr style="border-bottom: 1px solid rgba(255,255,255,0.2); color: #aaa;">' +
            '<th style="padding: 4px 6px;">#</th>' +
            '<th style="padding: 4px 6px;">Goal Reached</th>' +
            '<th style="padding: 4px 6px;">Memory</th>' +
            '<th style="padding: 4px 6px;">Power</th>' +
            '<th style="padding: 4px 6px; text-align: center;">Action</th>' +
            '</tr>';
        table.appendChild(thead);

        var tbody = document.createElement('tbody');
        tbody.id = 'scoreboardTableBody';
        table.appendChild(tbody);
        body.appendChild(table);
        panel.appendChild(body);

        document.body.appendChild(panel);
        return panel;
    }

    function togglePanel(show) {
        if (typeof document === 'undefined') return;
        var panel = document.getElementById('__scoreboardPanel') || buildPanel();
        if (!panel) return;
        if (typeof show === 'boolean') {
            isPanelVisible = show;
        } else {
            isPanelVisible = !isPanelVisible;
        }
        panel.style.display = isPanelVisible ? 'flex' : 'none';
        if (isPanelVisible) {
            updateUI();
        }
    }

    function ensureToggleButton() {
        if (typeof document === 'undefined' || !document.body) return null;
        var btn = document.getElementById('scoreboardToggleBtn');
        if (!btn) {
            btn = document.createElement('button');
            btn.id = 'scoreboardToggleBtn';
            btn.type = 'button';
            btn.className = 'primary';
            btn.style.padding = '4px 10px';
            btn.style.margin = '4px';
            btn.style.borderRadius = '6px';
            btn.style.background = 'rgba(40,40,40,0.88)';
            btn.style.border = '1px solid rgba(255,255,255,0.2)';
            btn.style.color = '#fff';
            btn.style.fontWeight = '600';
            btn.style.fontSize = '12px';
            btn.style.cursor = 'pointer';
            btn.onclick = function() {
                togglePanel();
            };

            var runBtn = document.getElementById('runButton');
            var slot = null;
            if (runBtn && runBtn.parentNode) {
                var prev = runBtn.parentNode.previousElementSibling;
                if (prev && prev.tagName && prev.tagName.toLowerCase() === 'td') {
                    slot = prev;
                } else {
                    slot = runBtn.parentNode;
                }
            }
            if (slot) {
                slot.appendChild(btn);
            } else {
                btn.style.position = 'fixed';
                btn.style.bottom = '10px';
                btn.style.right = '10px';
                btn.style.zIndex = '9000';
                document.body.appendChild(btn);
            }
            var level = getLevel();
            var runs = getRuns(level);
            btn.textContent = 'Scoreboard (' + runs.length + ')';
        }
        return btn;
    }

    function updateUI() {
        if (typeof document === 'undefined') return;
        var level = getLevel();
        var runs = getRuns(level);

        var toggleBtn = ensureToggleButton();
        if (toggleBtn) {
            toggleBtn.textContent = 'Scoreboard (' + runs.length + ')';
        }

        var title = document.getElementById('scoreboardPanelTitle');
        if (title) {
            title.textContent = 'Run History (Level ' + level + ')';
        }

        var tbody = document.getElementById('scoreboardTableBody');
        if (!tbody) return;

        tbody.innerHTML = '';
        if (runs.length === 0) {
            var emptyTr = document.createElement('tr');
            emptyTr.innerHTML = '<td colspan="5" style="padding: 12px; text-align: center; color: #888;">No runs recorded for this level yet.</td>';
            tbody.appendChild(emptyTr);
            return;
        }

        for (var i = 0; i < runs.length; i++) {
            var run = runs[i];
            var tr = document.createElement('tr');
            tr.setAttribute('data-run-id', String(run.id));
            tr.style.borderBottom = '1px solid rgba(255,255,255,0.08)';

            var goalBadge = run.goalReached ?
                '<span style="background: #238636; color: #fff; padding: 2px 6px; border-radius: 4px; font-weight: 600; font-size: 11px;">Yes</span>' :
                '<span style="background: #484f58; color: #b1bac4; padding: 2px 6px; border-radius: 4px; font-weight: 500; font-size: 11px;">No</span>';

            tr.innerHTML =
                '<td style="padding: 6px;">#' + run.id + '</td>' +
                '<td style="padding: 6px;">' + goalBadge + '</td>' +
                '<td style="padding: 6px;">' + formatDisplayVal(run.memory, 'kB') + '</td>' +
                '<td style="padding: 6px;">' + formatDisplayVal(run.power, 'kW') + '</td>' +
                '<td style="padding: 6px; text-align: center;">' +
                '  <button type="button" class="scoreboard-load-btn" data-id="' + run.id + '" style="background: #1f6feb; border: none; color: #fff; padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 11px; font-weight: 600;">Load</button>' +
                '</td>';

            tbody.appendChild(tr);
        }

        var loadBtns = tbody.querySelectorAll('.scoreboard-load-btn');
        for (var j = 0; j < loadBtns.length; j++) {
            loadBtns[j].addEventListener('click', function(e) {
                var id = parseInt(this.getAttribute('data-id'), 10);
                loadRun(id, level);
            });
        }
    }

    // Attach window global functions
    if (typeof window !== 'undefined') {
        window.__scoreboardAddRun = function(runData) {
            return addRun(runData);
        };
        window.__scoreboardMarkLastRunGoalReached = function(lvl) {
            markLastRunGoalReached(lvl);
        };
        window.__scoreboardGetRuns = function(lvl) {
            return getRuns(lvl);
        };
        window.__scoreboardClearRuns = function(lvl) {
            clearRuns(lvl);
        };
        window.__scoreboardLoadRun = function(id, lvl) {
            return loadRun(id, lvl);
        };

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', function() {
                ensureToggleButton();
            });
        } else {
            ensureToggleButton();
        }
    }

    return {
        getRuns: getRuns,
        addRun: addRun,
        markLastRunGoalReached: markLastRunGoalReached,
        clearRuns: clearRuns,
        loadRun: loadRun
    };
}));
