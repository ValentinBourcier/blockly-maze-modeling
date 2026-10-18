/**
 * blockyUIOverlay.js
 * Comprehensive UI overlay for standalone browser environment.
 * Replicates 100% of the UI injection logic from BlockyUI.java:
 * - Level Loading Overlay
 * - Execution Log Panel (draggable & resizable)
 * - MOMoT Search Panel (draggable & resizable, with interactive solution table & controls)
 * - Immediate Feedback & DM Comparison SVG Path Overlays
 * - Debugger Control Bar (Pause/Resume, Stop, Step, Skip End, Direct Manipulation)
 * - Direct Manipulation Maze Grid Cell Click Handlers
 */
(function() {
    console.log('[blockyUIOverlay] Initializing Web Application UI Overlays...');

    // maze-original.html sets window.BLOCKY_MOMOT_ENABLED = false before loading this file: the page is the
    // same game, but without the MoMoT panel, search and direct manipulation (which starts a search).
    function __momotOn() {
        return window.BLOCKY_MOMOT_ENABLED !== false;
    }

    // ==========================================
    // 1. Level Loading Overlay
    // ==========================================
    function __lvlLoadingEnsure() {
        try {
            if (window.__lvlLoadingReady) return true;
            var ov = document.getElementById('__lvlLoadingOverlay');
            if (!ov) {
                ov = document.createElement('div');
                ov.id = '__lvlLoadingOverlay';
                ov.style.position = 'fixed';
                ov.style.left = '0'; ov.style.top = '0'; ov.style.right = '0'; ov.style.bottom = '0';
                ov.style.background = 'rgba(0,0,0,0.35)';
                ov.style.zIndex = '999999';
                ov.style.display = 'none';
                ov.style.alignItems = 'center';
                ov.style.justifyContent = 'center';
                ov.style.pointerEvents = 'auto';

                var box = document.createElement('div');
                box.style.width = '360px';
                box.style.maxWidth = '90vw';
                box.style.padding = '16px 16px 14px 16px';
                box.style.borderRadius = '10px';
                box.style.background = 'rgba(40,40,40,0.92)';
                box.style.border = '1px solid rgba(255,255,255,0.18)';
                box.style.boxShadow = '0 8px 30px rgba(0,0,0,0.35)';

                var title = document.createElement('div');
                title.id = '__lvlLoadingTitle';
                title.textContent = 'Loading level…';
                title.style.color = '#fff';
                title.style.fontSize = '15px';
                title.style.fontWeight = '600';
                title.style.marginBottom = '10px';

                var bar = document.createElement('div');
                bar.style.height = '10px';
                bar.style.borderRadius = '8px';
                bar.style.overflow = 'hidden';
                bar.style.background = 'rgba(255,255,255,0.14)';

                var fill = document.createElement('div');
                fill.id = '__lvlLoadingFill';
                fill.style.height = '100%';
                fill.style.width = '40%';
                fill.style.borderRadius = '8px';
                fill.style.background = 'linear-gradient(90deg,#4d90fe,#3bd46a,#ffcc00)';
                fill.style.backgroundSize = '200% 100%';
                fill.style.animation = '__lvlLoadAnim 1.8s linear infinite';
                bar.appendChild(fill);

                var sub = document.createElement('div');
                sub.textContent = 'Syncing toolbox & metadata…';
                sub.style.color = 'rgba(255,255,255,0.82)';
                sub.style.fontSize = '12px';
                sub.style.marginTop = '10px';

                box.appendChild(title);
                box.appendChild(bar);
                box.appendChild(sub);
                ov.appendChild(box);
                document.body.appendChild(ov);

                var st = document.getElementById('__lvlLoadingStyle');
                if (!st) {
                    st = document.createElement('style'); st.id = '__lvlLoadingStyle';
                    st.textContent = '@keyframes __lvlLoadAnim { 0%{transform:translateX(-30%);background-position:0% 50%;} 100%{transform:translateX(220%);background-position:100% 50%;} }';
                    document.head.appendChild(st);
                }
            }
            window.__lvlLoadingReady = true;
            window.__lvlLoadingShow = function(txt) {
                try {
                    __lvlLoadingEnsure();
                    var t = document.getElementById('__lvlLoadingTitle');
                    if (t && txt) t.textContent = txt;
                    var o = document.getElementById('__lvlLoadingOverlay');
                    if (o) o.style.display = 'flex';
                    window.__lvlLoadingStart = Date.now();
                } catch(e) {}
            };
            window.__lvlLoadingHide = function() {
                try {
                    var o2 = document.getElementById('__lvlLoadingOverlay');
                    if (!o2) return;
                    var min = 500;
                    var elapsed = Date.now() - (window.__lvlLoadingStart || 0);
                    if (elapsed < min) {
                        setTimeout(function() { o2.style.display = 'none'; }, min - elapsed);
                    } else {
                        o2.style.display = 'none';
                    }
                } catch(e) {}
            };
            return true;
        } catch(e) { return false; }
    }

    __lvlLoadingEnsure();

    try {
        if (!window.__lvlLoadingClickBound) {
            window.__lvlLoadingClickBound = true;
            document.addEventListener('click', function(ev) {
                try {
                    var el = ev && ev.target ? ev.target : null;
                    if (!el) return;
                    if (el.id === 'levelModel' || (el.classList && el.classList.contains('level_number'))) {
                        var label = (el.textContent && el.textContent.trim()) ? el.textContent.trim() : 'level';
                        if (label.toLowerCase() === 'save' || label.toLowerCase() === 'load') return;
                        window.__lvlLoadingShow('Loading ' + label + '…');
                    }
                } catch(e2) {}
            }, true);
        }
    } catch(e) {}


    // ==========================================
    // Reusable Panel Dragging & Resizing Helper
    // ==========================================
    function __makePanelDraggableAndResizable(panelId, headerId, handleId, minW, minH, maxW, maxH) {
        try {
            var panel = document.getElementById(panelId);
            var header = document.getElementById(headerId);
            var handle = handleId ? document.getElementById(handleId) : null;
            if (!panel || !header) return;
            if (panel.__dragBound) return;
            panel.__dragBound = true;

            var dragging = false, resizing = false;
            var startX = 0, startY = 0, startLeft = 0, startTop = 0, startW = 0, startH = 0;

            function clamp(v, min, max) { return Math.max(min, Math.min(max, v)); }

            function getViewportSize() {
                var w = Math.max(
                    window.innerWidth || 0,
                    document.documentElement ? document.documentElement.clientWidth : 0,
                    document.body ? document.body.clientWidth : 0,
                    800
                );
                var h = Math.max(
                    window.innerHeight || 0,
                    document.documentElement ? document.documentElement.clientHeight : 0,
                    document.body ? document.body.clientHeight : 0,
                    600
                );
                return { w: w, h: h };
            }

            function onMove(ev) {
                try {
                    var vp = getViewportSize();
                    var clientX = ev.clientX || (ev.touches && ev.touches[0] ? ev.touches[0].clientX : 0);
                    var clientY = ev.clientY || (ev.touches && ev.touches[0] ? ev.touches[0].clientY : 0);

                    if (resizing) {
                        var dx = clientX - startX;
                        var dy = clientY - startY;
                        var maxAllowW = Math.max(minW || 200, vp.w - 20);
                        var maxAllowH = Math.max(minH || 100, vp.h - 20);
                        var newW = clamp(startW + dx, minW || 200, maxW ? Math.min(maxW, maxAllowW) : maxAllowW);
                        var newH = clamp(startH + dy, minH || 100, maxH ? Math.min(maxH, maxAllowH) : maxAllowH);
                        panel.style.width = Math.round(newW) + 'px';
                        panel.style.height = Math.round(newH) + 'px';
                        return;
                    }
                    if (dragging) {
                        var dx2 = clientX - startX;
                        var dy2 = clientY - startY;

                        var maxL = Math.max(0, vp.w - 60);
                        var maxT = Math.max(0, vp.h - 30);

                        var newLeft = clamp(startLeft + dx2, 0, maxL);
                        var newTop  = clamp(startTop + dy2, 0, maxT);

                        panel.style.left = Math.round(newLeft) + 'px';
                        panel.style.top = Math.round(newTop) + 'px';
                        panel.style.right = 'auto';
                        panel.style.bottom = 'auto';
                    }
                } catch(e) {}
            }

            function onUp() {
                dragging = false;
                resizing = false;
                try {
                    document.removeEventListener('mousemove', onMove, true);
                    document.removeEventListener('mouseup', onUp, true);
                    document.removeEventListener('touchmove', onMove, true);
                    document.removeEventListener('touchend', onUp, true);
                } catch(e) {}
            }

            function onStartDrag(ev) {
                try {
                    var target = ev.target || ev.srcElement;
                    var tag = target ? (target.tagName || '').toUpperCase() : '';
                    if (tag === 'BUTTON' || tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA' || (target && typeof target.closest === 'function' && (target.closest('button') || target.closest('input')))) return;

                    var clientX = ev.clientX || (ev.touches && ev.touches[0] ? ev.touches[0].clientX : 0);
                    var clientY = ev.clientY || (ev.touches && ev.touches[0] ? ev.touches[0].clientY : 0);
                    if (ev.type === 'mousedown' && ev.button !== 0) return;

                    var pb = panel.getBoundingClientRect();
                    dragging = true;
                    panel.__userDragged = true;
                    startX = clientX;
                    startY = clientY;
                    startLeft = pb.left;
                    startTop = pb.top;

                    panel.style.left = Math.round(startLeft) + 'px';
                    panel.style.top = Math.round(startTop) + 'px';
                    panel.style.right = 'auto';
                    panel.style.bottom = 'auto';

                    document.addEventListener('mousemove', onMove, true);
                    document.addEventListener('mouseup', onUp, true);
                    document.addEventListener('touchmove', onMove, true);
                    document.addEventListener('touchend', onUp, true);

                    if (ev && ev.preventDefault) ev.preventDefault();
                } catch(e) {}
            }

            function onStartResize(ev) {
                try {
                    var clientX = ev.clientX || (ev.touches && ev.touches[0] ? ev.touches[0].clientX : 0);
                    var clientY = ev.clientY || (ev.touches && ev.touches[0] ? ev.touches[0].clientY : 0);
                    if (ev.type === 'mousedown' && ev.button !== 0) return;

                    var pb = panel.getBoundingClientRect();
                    resizing = true;
                    panel.__userResized = true;
                    startX = clientX;
                    startY = clientY;
                    startW = pb.width;
                    startH = pb.height;

                    panel.style.left = Math.round(pb.left) + 'px';
                    panel.style.top = Math.round(pb.top) + 'px';
                    panel.style.right = 'auto';
                    panel.style.bottom = 'auto';

                    document.addEventListener('mousemove', onMove, true);
                    document.addEventListener('mouseup', onUp, true);
                    document.addEventListener('touchmove', onMove, true);
                    document.addEventListener('touchend', onUp, true);

                    if (ev && ev.preventDefault) ev.preventDefault();
                } catch(e) {}
            }

            header.addEventListener('mousedown', onStartDrag, true);
            header.addEventListener('touchstart', onStartDrag, true);

            if (handle) {
                handle.addEventListener('mousedown', onStartResize, true);
                handle.addEventListener('touchstart', onStartResize, true);
            }
        } catch(e) {}
    }


    // ==========================================
    // 2. Execution Log Panel (Draggable & Resizable)
    // ==========================================
    function __execLogEnsure() {
        try {
            var existing = document.getElementById('__execLogPanel');
            if (!existing) {
                var host = document.body || document.documentElement;
                if (!host) return false;

                var panel = document.createElement('div'); panel.id = '__execLogPanel';
                panel.style.position = 'fixed';
                panel.style.right = '20px';
                panel.style.bottom = '20px';
                panel.style.left = 'auto';
                panel.style.top = 'auto';
                panel.style.width = '380px';
                panel.style.height = '220px';
                panel.style.minWidth = '260px';
                panel.style.minHeight = '120px';
                panel.style.maxWidth = '600px';
                panel.style.maxHeight = '600px';
                if (window.innerWidth < 768) {
                    panel.style.left = '10px';
                    panel.style.right = '10px';
                    panel.style.bottom = '10px';
                    panel.style.width = 'calc(100vw - 20px)';
                    panel.style.maxHeight = '50vh';
                }
                panel.style.overflow = 'hidden';
                panel.style.background = 'rgba(64,64,64,0.88)';
                panel.style.borderRadius = '8px';
                panel.style.border = '1px solid rgba(255,255,255,0.15)';
                panel.style.boxShadow = '2px 2px 8px rgba(0,0,0,0.4)';
                panel.style.zIndex = '99999';
                panel.style.display = 'none'; // hidden by default; the "Execution Log" button toggles it

                var header = document.createElement('div'); header.id = '__execLogHeader';
                header.style.display = 'flex'; header.style.alignItems = 'center'; header.style.justifyContent = 'space-between';
                header.style.padding = '6px 8px'; header.style.color = '#fff'; header.style.fontSize = '14px';
                header.style.cursor = 'move'; header.style.userSelect = 'none';

                var title = document.createElement('div'); title.textContent = 'Execution log'; title.style.fontWeight = 'bold';
                
                var rightHeader = document.createElement('div');
                rightHeader.style.display = 'flex'; rightHeader.style.gap = '4px';

                var btn = document.createElement('button'); btn.id = '__execLogClearBtn'; btn.textContent = 'Clear';
                btn.style.margin = '0'; btn.style.padding = '6px 12px'; btn.style.fontSize = '13px'; btn.style.minHeight = '36px';
                btn.style.borderRadius = '4px'; btn.style.border = '1px solid rgba(255,255,255,0.25)';
                btn.style.background = 'rgba(255,255,255,0.10)'; btn.style.color = '#fff'; btn.style.cursor = 'pointer';
                btn.style.touchAction = 'manipulation';
                btn.addEventListener('click', function(){ try { if (window.__execLogClear) window.__execLogClear(); } catch(e) {} });

                var closeBtn = document.createElement('button'); closeBtn.id = '__execLogCloseBtn'; closeBtn.textContent = '✕';
                closeBtn.title = 'Close / Hide Execution Log';
                closeBtn.style.margin = '0'; closeBtn.style.padding = '6px 12px'; closeBtn.style.fontSize = '14px'; closeBtn.style.minHeight = '36px'; closeBtn.style.minWidth = '36px';
                closeBtn.style.borderRadius = '4px'; closeBtn.style.border = '1px solid rgba(255,255,255,0.25)';
                closeBtn.style.background = 'rgba(255,255,255,0.10)'; closeBtn.style.color = '#fff'; closeBtn.style.cursor = 'pointer';
                closeBtn.style.touchAction = 'manipulation';
                closeBtn.addEventListener('click', function(){
                    try {
                        var p = document.getElementById('__execLogPanel');
                        if (p) p.style.display = 'none';
                    } catch(e) {}
                });

                rightHeader.appendChild(btn); rightHeader.appendChild(closeBtn);
                header.appendChild(title); header.appendChild(rightHeader);

                var body = document.createElement('pre'); body.id = '__execLogBody';
                body.style.margin = '0'; body.style.padding = '6px 8px';
                body.style.height = 'calc(100% - 38px)'; body.style.overflow = 'auto';
                body.style.color = '#fff'; body.style.fontFamily = 'Consolas, Menlo, Monaco, monospace'; body.style.fontSize = '12px';
                body.style.whiteSpace = 'pre-wrap'; body.style.wordBreak = 'break-word';

                panel.appendChild(header); panel.appendChild(body);

                var rh = document.createElement('div'); rh.id = '__execLogResizeHandle';
                rh.style.position = 'absolute'; rh.style.right = '0'; rh.style.bottom = '0';
                rh.style.width = '28px'; rh.style.height = '28px';
                rh.style.cursor = 'se-resize'; rh.style.opacity = '0.85'; rh.style.touchAction = 'none';
                rh.style.background = 'linear-gradient(135deg, rgba(255,255,255,0.0) 0%, rgba(255,255,255,0.0) 45%, rgba(255,255,255,0.35) 46%, rgba(255,255,255,0.35) 55%, rgba(255,255,255,0.0) 56%, rgba(255,255,255,0.0) 100%)';
                panel.appendChild(rh);

                host.appendChild(panel);
                existing = panel;
            } else if (!existing.parentNode) {
                var host2 = document.body || document.documentElement;
                if (host2) host2.appendChild(existing);
            }

            window.__execLogReady = true;
            if (!window.__execLogMaxLines) window.__execLogMaxLines = 500;

            __makePanelDraggableAndResizable('__execLogPanel', '__execLogHeader', '__execLogResizeHandle', 260, 120, 600, 600);

            window.__execLogClear = function() {
                try { var b = document.getElementById('__execLogBody'); if (b) b.textContent = ''; window.__execLogLineCount = 0; } catch(e) {}
            };
            window.__execLogAppend = function(lines) {
                try {
                    var b = document.getElementById('__execLogBody');
                    if (!b) {
                        __execLogEnsure();
                        b = document.getElementById('__execLogBody');
                        if (!b) return;
                    }
                    if (lines === undefined || lines === null) return;
                    if (typeof lines === 'string') lines = [lines];
                    if (!lines.length) return;
                    var txt = b.textContent || '';
                    for (var i=0; i<lines.length; i++) {
                        var line = (lines[i] === undefined || lines[i] === null) ? '' : String(lines[i]);
                        txt += (txt.length ? '\n' : '') + line;
                    }
                    var parts = txt.split(/\n/);
                    var max = window.__execLogMaxLines || 500;
                    if (parts.length > max) parts = parts.slice(parts.length - max);
                    b.textContent = parts.join('\n');
                    b.scrollTop = b.scrollHeight + 1000;
                } catch(e) {}
            };
            return true;
        } catch(e) { return false; }
    }


    // ==========================================
    // 3. MOMoT Search Panel (Draggable & Resizable)
    // ==========================================
    function __momotEnsure() {
        try {
            var existing = document.getElementById('__momotPanel');
            if (!existing) {
                var host = document.body || document.documentElement;
                if (!host) return false;

                var panel = document.createElement('div'); panel.id = '__momotPanel';
                panel.style.position = 'fixed';
                panel.style.right = '10px';
                panel.style.top = '80px';
                panel.style.bottom = 'auto';
                panel.style.width = '540px';
                panel.style.height = '380px';
                panel.style.minWidth = '300px';
                panel.style.minHeight = '140px';
                panel.style.maxWidth = 'calc(100vw - 20px)';
                panel.style.maxHeight = 'calc(100vh - 20px)';
                if (window.innerWidth < 768) {
                    panel.style.left = '10px';
                    panel.style.right = '10px';
                    panel.style.bottom = '10px';
                    panel.style.top = 'auto';
                    panel.style.width = 'calc(100vw - 20px)';
                    panel.style.maxHeight = '60vh';
                }
                panel.style.overflow = 'hidden';
                panel.style.background = 'rgba(32,32,32,0.88)';
                panel.style.borderRadius = '8px';
                panel.style.border = '1px solid rgba(255,255,255,0.15)';
                panel.style.boxShadow = '2px 2px 5px rgba(0,0,0,0.35)';
                panel.style.zIndex = '99998';
                panel.style.display = 'none';
                panel.style.flexDirection = 'column';

                window.addEventListener('resize', function() {
                    try {
                        if (!panel.__userResized && !panel.__userDragged) {
                            if (window.innerWidth < 768) {
                                panel.style.left = '10px';
                                panel.style.right = '10px';
                                panel.style.bottom = '10px';
                                panel.style.top = 'auto';
                                panel.style.width = 'calc(100vw - 20px)';
                                panel.style.maxHeight = '60vh';
                            } else {
                                var targetH = Math.min(Math.max(300, window.innerHeight - 100), 750);
                                var targetW = Math.min(Math.max(500, window.innerWidth - 40), 900);
                                panel.style.width = targetW + 'px';
                                panel.style.height = targetH + 'px';
                            }
                        }
                    } catch(e) {}
                });

                var header = document.createElement('div'); header.id = '__momotHeader';
                header.style.display = 'flex'; header.style.alignItems = 'center'; header.style.justifyContent = 'space-between';
                header.style.padding = '6px 8px'; header.style.color = '#fff'; header.style.fontSize = '14px'; header.style.userSelect = 'none';
                header.style.cursor = 'move';

                var title = document.createElement('div'); title.textContent = 'MoMoT solutions'; title.style.fontWeight = 'bold';
                var right = document.createElement('div'); right.style.display = 'flex'; right.style.gap = '6px';

                function mkBtn(id, label, tip) {
                    var b = document.createElement('button'); b.id = id; b.textContent = label; b.title = tip;
                    b.style.margin = '0'; b.style.padding = '6px 12px'; b.style.fontSize = '13px'; b.style.minHeight = '36px';
                    b.style.borderRadius = '4px'; b.style.border = '1px solid rgba(255,255,255,0.25)';
                    b.style.background = 'rgba(255,255,255,0.10)'; b.style.color = '#fff'; b.style.cursor = 'pointer';
                    b.style.touchAction = 'manipulation';
                    return b;
                }
                function mkInput(id, val, tip, width) {
                    var i = document.createElement('input'); i.id = id; i.value = val; i.title = tip;
                    i.style.width = width || '40px'; i.style.fontSize = '11px'; i.style.padding = '2px 4px';
                    i.style.background = 'rgba(0,0,0,0.3)'; i.style.color = '#fff'; i.style.border = '1px solid rgba(255,255,255,0.2)';
                    i.style.borderRadius = '3px';
                    return i;
                }

                var settings = document.createElement('div'); settings.id = '__momotSettings';
                settings.style.display = 'none'; settings.style.flexWrap = 'wrap'; settings.style.gap = '6px';
                settings.style.padding = '6px 8px'; settings.style.borderBottom = '1px solid rgba(255,255,255,0.1)';
                settings.style.alignItems = 'center'; settings.style.fontSize = '11px'; settings.style.color = '#fff';

                var labSeed = document.createElement('span'); labSeed.textContent = 'Seed:';
                var inpSeed = mkInput('__momotInpSeed', '0', 'Random seed (0 for auto)', '35px');
                var labPop = document.createElement('span'); labPop.textContent = 'Pop:';
                var inpPop = mkInput('__momotInpPop', '50', 'Population size', '35px');
                var labIter = document.createElement('span'); labIter.textContent = 'Iter:';
                var inpIter = mkInput('__momotInpIter', '40', 'Number of iterations (generations)', '30px');
                var labRuns = document.createElement('span'); labRuns.textContent = 'Runs:';
                var inpRuns = mkInput('__momotInpRuns', '10', 'Number of algorithm runs', '25px');
                var labSolLen = document.createElement('span'); labSolLen.textContent = 'SolLen:';
                var inpSolLen = mkInput('__momotInpSolLen', '10', 'Solution length (number of transformation steps)', '25px');

                var mRunBtn = mkBtn('__momotRunBtn', 'Run', 'Execute MOMoT search');
                mRunBtn.style.background = 'rgba(70, 150, 70, 0.6)';
                // Hidden: the search starts when the Direct Manipulation marker is placed (window.__momotStartRun).
                // The button stays in the DOM because that function is registered on it.
                mRunBtn.style.display = 'none';
                var mStopBtn = mkBtn('__momotStopBtn', 'Stop', 'Stop current MOMoT search');
                mStopBtn.style.background = 'rgba(180, 50, 50, 0.6)';

                settings.appendChild(labSeed); settings.appendChild(inpSeed);
                settings.appendChild(labPop); settings.appendChild(inpPop);
                settings.appendChild(labIter); settings.appendChild(inpIter);
                settings.appendChild(labRuns); settings.appendChild(inpRuns);
                settings.appendChild(labSolLen); settings.appendChild(inpSolLen);

                var clearOverlayBtn = mkBtn('__momotClearOverlayBtn', 'Clear path', 'Clear the comparison path and the marker from the maze');
                var chartToggleBtn = mkBtn('__momotChartToggleBtn', 'Chart', 'Toggle 2D Pareto scatter plot');
                chartToggleBtn.addEventListener('click', function() {
                    var show = (chartContainer.style.display === 'none' || !chartContainer.style.display);
                    chartContainer.style.display = show ? 'flex' : 'none';
                    chartToggleBtn.textContent = show ? 'Hide Chart' : 'Chart';
                    if (show && typeof renderParetoChart === 'function') {
                        renderParetoChart(window.__momotLastFiltered || window.__momotLastProcessed || []);
                    }
                });
                var logToggleBtn = mkBtn('__momotLogToggleBtn', 'Log', 'Show or hide the MoMoT log');
                logToggleBtn.addEventListener('click', function() {
                    var show = (log.style.display === 'none');
                    log.style.display = show ? 'block' : 'none';
                    logToggleBtn.textContent = show ? 'Hide Log' : 'Log';
                    if (show) { log.scrollTop = log.scrollHeight + 1000; }
                });
                var gearBtn = mkBtn('__momotGearBtn', '⚙', 'Advanced settings');
                gearBtn.addEventListener('click', function() {
                    var cur = settings.style.display;
                    settings.style.display = (cur === 'none' || !cur) ? 'flex' : 'none';
                });
                var mCloseBtn = mkBtn('__momotCloseBtn', '✕', 'Close / Hide MoMoT Panel (available when the search has finished or was stopped)');
                function momotSearchActive() {
                    return window.__momotIsRunning === true || (typeof progTimer !== 'undefined' && !!progTimer);
                }
                window.__momotSearchActive = momotSearchActive;
                function updateCloseBtnState() {
                    try {
                        var active = momotSearchActive();
                        mCloseBtn.disabled = active;
                        mCloseBtn.style.opacity = active ? '0.35' : '1';
                        mCloseBtn.style.cursor = active ? 'not-allowed' : 'pointer';
                    } catch(e) {}
                }
                mCloseBtn.addEventListener('click', function() {
                    try {
                        if (momotSearchActive()) return;
                        var p = document.getElementById('__momotPanel');
                        if (p) p.style.display = 'none';
                    } catch(e) {}
                });
                updateCloseBtnState();
                setInterval(updateCloseBtnState, 500);
                right.appendChild(mRunBtn);
                right.appendChild(mStopBtn);
                right.appendChild(chartToggleBtn);
                right.appendChild(logToggleBtn);
                right.appendChild(clearOverlayBtn);
                right.appendChild(gearBtn);
                right.appendChild(mCloseBtn);
                header.appendChild(title); header.appendChild(right);

                var body = document.createElement('div'); body.id = '__momotBody';
                body.style.padding = '6px 8px';
                body.style.display = 'flex';
                body.style.flexDirection = 'column';
                body.style.flex = '1 1 auto';
                body.style.minHeight = '0';
                body.style.overflow = 'hidden';
                body.style.color = '#fff'; body.style.fontFamily = 'Consolas, Menlo, Monaco, monospace'; body.style.fontSize = '12px';

                // Filter toolbar
                var filterBar = document.createElement('div'); filterBar.id = '__momotFilterBar';
                filterBar.style.display = 'flex'; filterBar.style.flexWrap = 'wrap'; filterBar.style.gap = '8px';
                filterBar.style.alignItems = 'center'; filterBar.style.padding = '2px 0 6px 0';
                filterBar.style.fontSize = '11px'; filterBar.style.color = '#ccc'; filterBar.style.flexShrink = '0';

                var chkGoalLabel = document.createElement('label');
                chkGoalLabel.style.display = 'inline-flex'; chkGoalLabel.style.alignItems = 'center'; chkGoalLabel.style.gap = '3px'; chkGoalLabel.style.cursor = 'pointer';
                var chkGoal = document.createElement('input'); chkGoal.type = 'checkbox'; chkGoal.id = '__momotFilterGoal';
                chkGoalLabel.appendChild(chkGoal);
                chkGoalLabel.appendChild(document.createTextNode('Hide misses'));
                chkGoalLabel.title = 'Hide programs that miss the goal';
                chkGoal.addEventListener('change', function() { renderSolutions(); });
                filterBar.appendChild(chkGoalLabel);

                function mkFilterInput(id, placeholder, tip) {
                    var inp = mkInput(id, '', tip, '38px');
                    inp.placeholder = placeholder;
                    inp.addEventListener('input', function() { renderSolutions(); });
                    return inp;
                }

                var labFEdits = document.createElement('span'); labFEdits.textContent = '≤Edits:';
                var inpFEdits = mkFilterInput('__momotFilterMaxEdits', 'max', 'Maximum Edits');
                var labFPower = document.createElement('span'); labFPower.textContent = '≤Power:'; labFPower.style.color = '#f59e0b';
                var inpFPower = mkFilterInput('__momotFilterMaxPower', 'max', 'Maximum Power (kW)');
                var labFClosest = document.createElement('span'); labFClosest.textContent = '≤Closest:';
                var inpFClosest = mkFilterInput('__momotFilterMaxClosest', 'max', 'Maximum Closest to Goal');
                var labFMem = document.createElement('span'); labFMem.textContent = '≤Mem:'; labFMem.style.color = '#38bdf8';
                var inpFMem = mkFilterInput('__momotFilterMaxMemory', 'max', 'Maximum Memory (kB)');

                var filterResetBtn = mkBtn('__momotFilterResetBtn', 'Reset', 'Clear filters');
                filterResetBtn.style.padding = '2px 6px'; filterResetBtn.style.fontSize = '10px'; filterResetBtn.style.minHeight = '20px';
                filterResetBtn.addEventListener('click', function() {
                    chkGoal.checked = false;
                    inpFEdits.value = '';
                    inpFPower.value = '';
                    inpFClosest.value = '';
                    inpFMem.value = '';
                    renderSolutions();
                });

                filterBar.appendChild(labFEdits); filterBar.appendChild(inpFEdits);
                filterBar.appendChild(labFPower); filterBar.appendChild(inpFPower);
                filterBar.appendChild(labFClosest); filterBar.appendChild(inpFClosest);
                filterBar.appendChild(labFMem); filterBar.appendChild(inpFMem);
                filterBar.appendChild(filterResetBtn);

                // Interactive 2D Pareto Chart
                var chartContainer = document.createElement('div'); chartContainer.id = '__momotChartContainer';
                chartContainer.style.display = 'none';
                chartContainer.style.flexDirection = 'column';
                chartContainer.style.gap = '4px';
                chartContainer.style.padding = '6px';
                chartContainer.style.marginBottom = '6px';
                chartContainer.style.background = 'rgba(0,0,0,0.3)';
                chartContainer.style.borderRadius = '4px';
                chartContainer.style.border = '1px solid rgba(255,255,255,0.1)';
                chartContainer.style.flexShrink = '0';
                chartContainer.style.position = 'relative';

                var chartControls = document.createElement('div');
                chartControls.style.display = 'flex';
                chartControls.style.alignItems = 'center';
                chartControls.style.gap = '8px';
                chartControls.style.fontSize = '11px';

                var axisXLabel = document.createElement('span'); axisXLabel.textContent = 'X:';
                var axisXSelect = document.createElement('select'); axisXSelect.id = '__momotChartAxisX';
                axisXSelect.style.background = 'rgba(0,0,0,0.5)'; axisXSelect.style.color = '#fff'; axisXSelect.style.fontSize = '11px'; axisXSelect.style.border = '1px solid rgba(255,255,255,0.2)'; axisXSelect.style.borderRadius = '3px';

                var axisYLabel = document.createElement('span'); axisYLabel.textContent = 'Y:';
                var axisYSelect = document.createElement('select'); axisYSelect.id = '__momotChartAxisY';
                axisYSelect.style.background = 'rgba(0,0,0,0.5)'; axisYSelect.style.color = '#fff'; axisYSelect.style.fontSize = '11px'; axisYSelect.style.border = '1px solid rgba(255,255,255,0.2)'; axisYSelect.style.borderRadius = '3px';

                var axisOptions = [
                    { idx: 1, label: 'Edits' },
                    { idx: 2, label: 'Power (kW)' },
                    { idx: 3, label: 'Closest to Goal' },
                    { idx: 4, label: 'Memory (kB)' }
                ];
                axisOptions.forEach(function(opt) {
                    var oX = document.createElement('option'); oX.value = opt.idx; oX.textContent = opt.label;
                    if (opt.idx === 2) oX.selected = true;
                    axisXSelect.appendChild(oX);

                    var oY = document.createElement('option'); oY.value = opt.idx; oY.textContent = opt.label;
                    if (opt.idx === 4) oY.selected = true;
                    axisYSelect.appendChild(oY);
                });

                axisXSelect.addEventListener('change', function() { renderParetoChart(window.__momotLastFiltered || window.__momotLastProcessed || []); });
                axisYSelect.addEventListener('change', function() { renderParetoChart(window.__momotLastFiltered || window.__momotLastProcessed || []); });

                var chartLegend = document.createElement('span');
                chartLegend.style.marginLeft = 'auto'; chartLegend.style.fontSize = '10px'; chartLegend.style.color = '#aaa';
                chartLegend.innerHTML = '<span style="color:#38bdf8;">●</span> 2D Pareto Front &nbsp; <span style="color:#22c55e;">●</span> Goal &nbsp; <span style="color:#ec4899;">●</span> Selected';

                chartControls.appendChild(axisXLabel); chartControls.appendChild(axisXSelect);
                chartControls.appendChild(axisYLabel); chartControls.appendChild(axisYSelect);
                chartControls.appendChild(chartLegend);

                var chartSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                chartSvg.id = '__momotChartSvg';
                chartSvg.setAttribute('viewBox', '0 0 440 140');
                chartSvg.style.width = '100%';
                chartSvg.style.height = '140px';
                chartSvg.style.display = 'block';

                var chartTooltip = document.createElement('div');
                chartTooltip.id = '__momotChartTooltip';
                chartTooltip.style.position = 'absolute';
                chartTooltip.style.display = 'none';
                chartTooltip.style.padding = '4px 8px';
                chartTooltip.style.background = 'rgba(0,0,0,0.9)';
                chartTooltip.style.border = '1px solid rgba(255,255,255,0.4)';
                chartTooltip.style.borderRadius = '4px';
                chartTooltip.style.fontSize = '11px';
                chartTooltip.style.color = '#fff';
                chartTooltip.style.pointerEvents = 'none';
                chartTooltip.style.zIndex = '100000';
                chartTooltip.style.whiteSpace = 'pre';

                chartContainer.appendChild(chartControls);
                chartContainer.appendChild(chartSvg);
                chartContainer.appendChild(chartTooltip);

                var list = document.createElement('div'); list.id = '__momotList';
                list.style.flex = '1 1 auto';
                list.style.minHeight = '60px';
                list.style.overflow = 'auto';
                list.style.border = '1px solid rgba(255,255,255,0.1)'; list.style.borderRadius = '4px';
                list.style.background = 'rgba(0,0,0,0.2)'; list.style.display = 'none';

                var status = document.createElement('div'); status.id = '__momotStatus'; status.style.marginTop = '6px';
                status.style.color = '#0f0'; status.style.fontWeight = 'bold'; status.style.flexShrink = '0';
                status.textContent = 'Place the pegman to start search.';

                var log = document.createElement('pre'); log.id = '__momotLog';
                log.style.margin = '8px 0 0 0'; log.style.padding = '6px 8px';
                log.style.height = '80px'; log.style.overflow = 'auto';
                log.style.background = 'rgba(0,0,0,0.4)'; log.style.color = '#ddd';
                log.style.border = '1px solid rgba(255,255,255,0.15)'; log.style.borderRadius = '6px';
                log.style.whiteSpace = 'pre-wrap'; log.style.wordBreak = 'break-word'; log.textContent = '';
                log.style.display = 'none'; log.style.flexShrink = '0';

                var prog = document.createElement('div'); prog.id = '__momotProgress';
                prog.style.marginTop = '6px'; prog.style.display = 'none'; prog.style.flexShrink = '0';
                var progBar = document.createElement('div');
                progBar.style.height = '8px'; progBar.style.background = 'rgba(255,255,255,0.15)';
                progBar.style.borderRadius = '4px'; progBar.style.overflow = 'hidden';
                var progFill = document.createElement('div'); progFill.id = '__momotProgressFill';
                progFill.style.height = '100%'; progFill.style.width = '0%'; progFill.style.background = '#4caf50';
                progFill.style.transition = 'width 0.3s';
                progBar.appendChild(progFill);
                var progText = document.createElement('div'); progText.id = '__momotProgressText';
                progText.style.marginTop = '3px'; progText.style.fontSize = '11px'; progText.style.color = '#ddd';
                prog.appendChild(progBar); prog.appendChild(progText);

                body.appendChild(filterBar);
                body.appendChild(chartContainer);
                body.appendChild(list);
                body.appendChild(status);
                body.appendChild(prog);
                body.appendChild(log);
                panel.appendChild(header); panel.appendChild(settings); panel.appendChild(body);

                var rh = document.createElement('div'); rh.id = '__momotResizeHandle';
                rh.style.position = 'absolute'; rh.style.right = '0'; rh.style.bottom = '0';
                rh.style.width = '28px'; rh.style.height = '28px';
                rh.style.cursor = 'se-resize'; rh.style.opacity = '0.85'; rh.style.touchAction = 'none';
                rh.style.background = 'linear-gradient(135deg, rgba(255,255,255,0.0) 0%, rgba(255,255,255,0.0) 45%, rgba(255,255,255,0.35) 46%, rgba(255,255,255,0.35) 55%, rgba(255,255,255,0.0) 56%, rgba(255,255,255,0.0) 100%)';
                panel.appendChild(rh);

                host.appendChild(panel);

                window.__momotSelectedPath = null;
                window.__momotSortKeys = [{ col: 0, dir: 1 }];
                window.__momotSortCol = 0;
                window.__momotSortDir = 1;
                window.__momotLastData = [];
                window.__momotLastProcessed = [];
                window.__momotLastFiltered = [];

                function setStatus(msg) { try { status.textContent = msg || ''; } catch(e) {} }
                function logClear() { try { log.textContent = ''; } catch(e) {} }
                function logAppend(txt) {
                    try {
                        if (txt === undefined || txt === null) return;
                        var s = String(txt); if (!s.length) return;
                        var cur = log.textContent || '';
                        cur += (cur.length ? '\n' : '') + s;
                        var parts = cur.split(/\n/);
                        if (parts.length > 400) parts = parts.slice(parts.length - 400);
                        log.textContent = parts.join('\n');
                        log.scrollTop = log.scrollHeight + 1000;
                    } catch(e) {}
                }

                var progStart = 0, progTimer = null, progEnded = false;
                var progLast = { run: 1, runs: 1, gen: 0, gens: 1, pct: 0 };
                function fmtElapsed(ms) {
                    var s = Math.floor(ms / 1000), m = Math.floor(s / 60); s = s % 60;
                    return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
                }
                function renderProgress() {
                    try {
                        var el = progStart ? (Date.now() - progStart) : 0;
                        progFill.style.width = progLast.pct + '%';
                        progText.textContent = 'Run ' + progLast.run + '/' + progLast.runs + ' | generation ' + progLast.gen + '/' + progLast.gens
                            + ' | ' + Math.round(progLast.pct) + '% | ' + fmtElapsed(el) + (progEnded ? ' | ended' : '');
                    } catch(e) {}
                }
                window.__momotProgressStart = function(runs, gens) {
                    try {
                        if (progTimer) clearInterval(progTimer);
                        progStart = Date.now(); progEnded = false;
                        progLast = { run: 1, runs: runs || 1, gen: 0, gens: gens || 1, pct: 0 };
                        prog.style.display = 'block'; renderProgress();
                        progTimer = setInterval(renderProgress, 1000);
                    } catch(e) {}
                };
                window.__momotSetProgress = function(run, runs, gen, gens, pct) {
                    try {
                        progLast = { run: run, runs: runs, gen: gen, gens: gens, pct: pct };
                        prog.style.display = 'block';
                        renderProgress();
                    } catch(e) {}
                };
                window.__momotProgressDone = function(cancelled) {
                    try {
                        if (progTimer) { clearInterval(progTimer); progTimer = null; }
                        progEnded = true;
                        if (!cancelled && progLast) {
                            progLast.pct = 100;
                            if (progLast.runs) progLast.run = progLast.runs;
                            if (progLast.gens) progLast.gen = progLast.gens;
                        }
                        renderProgress();
                    } catch(e) {}
                };

                function clearSolutions() {
                    try {
                        if (progTimer) { clearInterval(progTimer); progTimer = null; }
                        prog.style.display = 'none';
                        window.__momotLastData = [];
                        window.__momotLastJson = null;
                        window.__momotSelectedPath = null;
                        window.__momotLastProcessed = [];
                        window.__momotLastFiltered = [];
                        list.innerHTML = '';
                        list.style.display = 'none';
                        logClear();
                        setStatus('No solutions for this level yet. Place the pegman to start search.');
                        if (window.__dbgDrawComparisonPath) window.__dbgDrawComparisonPath([]);
                        if (typeof renderParetoChart === 'function') renderParetoChart([]);
                    } catch(e) {}
                }

                window.__momotSetStatus = setStatus;
                window.__momotLogClear = logClear;
                window.__momotLogAppend = logAppend;
                window.__momotRenderSolutions = renderSolutions;
                window.__momotClearSolutions = clearSolutions;

                function __dbgDrawComparisonPath(path) {
                    try {
                        window.__injectDmEnabled = !!(path && path.length >= 2);
                        window.__injectDmSolutionPath = path || [];
                        if (!window.__injectDmBaselinePath) window.__injectDmBaselinePath = [];
                        if (typeof window.__ifRender === 'function') window.__ifRender();
                    } catch(e) {}
                }
                window.__dbgDrawComparisonPath = __dbgDrawComparisonPath;

                function renderParetoChart(processed) {
                    try {
                        if (!chartSvg) return;
                        while (chartSvg.firstChild) chartSvg.removeChild(chartSvg.firstChild);
                        if (chartContainer.style.display === 'none') return;
                        if (!processed || !processed.length) return;

                        var axisX = parseInt(axisXSelect ? axisXSelect.value : '2') || 2;
                        var axisY = parseInt(axisYSelect ? axisYSelect.value : '4') || 4;

                        var pts = [];
                        for (var i = 0; i < processed.length; i++) {
                            var item = processed[i];
                            var xv = item.objs[axisX];
                            var yv = item.objs[axisY];
                            if (xv !== undefined && !isNaN(xv) && xv < 100000 && yv !== undefined && !isNaN(yv) && yv < 100000) {
                                pts.push({
                                    p: item,
                                    x: xv,
                                    y: yv,
                                    isGoal: item.isGoal,
                                    modelName: item.modelName,
                                    isSelected: (window.__momotSelectedPath && item.it.modelPath === window.__momotSelectedPath)
                                });
                            }
                        }
                        if (!pts.length) return;

                        for (var pi = 0; pi < pts.length; pi++) {
                            var pA = pts[pi];
                            if (!pA.isGoal) { pA.isPareto2D = false; continue; }
                            var dominated = false;
                            for (var pj = 0; pj < pts.length; pj++) {
                                if (pi === pj) continue;
                                var pB = pts[pj];
                                if (!pB.isGoal) continue;
                                if (pB.x <= pA.x && pB.y <= pA.y && (pB.x < pA.x || pB.y < pA.y)) {
                                    dominated = true;
                                    break;
                                }
                            }
                            pA.isPareto2D = !dominated;
                        }

                        var minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
                        for (var k = 0; k < pts.length; k++) {
                            if (pts[k].x < minX) minX = pts[k].x;
                            if (pts[k].x > maxX) maxX = pts[k].x;
                            if (pts[k].y < minY) minY = pts[k].y;
                            if (pts[k].y > maxY) maxY = pts[k].y;
                        }
                        if (minX === maxX) { minX -= 1; maxX += 1; }
                        if (minY === maxY) { minY -= 1; maxY += 1; }

                        var padL = 40, padR = 20, padT = 15, padB = 25;
                        var plotW = 440 - padL - padR;
                        var plotH = 140 - padT - padB;

                        function toPx(x) { return padL + ((x - minX) / (maxX - minX)) * plotW; }
                        function toPy(y) { return padT + ((maxY - y) / (maxY - minY)) * plotH; }

                        var ns = 'http://www.w3.org/2000/svg';

                        var axisLines = document.createElementNS(ns, 'path');
                        var d = 'M ' + padL + ' ' + padT + ' L ' + padL + ' ' + (padT + plotH) + ' L ' + (padL + plotW) + ' ' + (padT + plotH);
                        axisLines.setAttribute('d', d);
                        axisLines.setAttribute('stroke', 'rgba(255,255,255,0.3)');
                        axisLines.setAttribute('stroke-width', '1');
                        axisLines.setAttribute('fill', 'none');
                        chartSvg.appendChild(axisLines);

                        var xLabelText = (axisXSelect && axisXSelect.options[axisXSelect.selectedIndex]) ? axisXSelect.options[axisXSelect.selectedIndex].text : 'X';
                        var yLabelText = (axisYSelect && axisYSelect.options[axisYSelect.selectedIndex]) ? axisYSelect.options[axisYSelect.selectedIndex].text : 'Y';

                        var xLbl = document.createElementNS(ns, 'text');
                        xLbl.setAttribute('x', padL + plotW / 2);
                        xLbl.setAttribute('y', 140 - 5);
                        xLbl.setAttribute('fill', '#aaa');
                        xLbl.setAttribute('font-size', '10');
                        xLbl.setAttribute('text-anchor', 'middle');
                        xLbl.textContent = xLabelText + ' (' + minX + '..' + maxX + ')';
                        chartSvg.appendChild(xLbl);

                        var yLbl = document.createElementNS(ns, 'text');
                        yLbl.setAttribute('x', 5);
                        yLbl.setAttribute('y', padT + 8);
                        yLbl.setAttribute('fill', '#aaa');
                        yLbl.setAttribute('font-size', '10');
                        yLbl.setAttribute('text-anchor', 'start');
                        yLbl.textContent = yLabelText;
                        chartSvg.appendChild(yLbl);

                        pts.forEach(function(pt) {
                            var cx = toPx(pt.x);
                            var cy = toPy(pt.y);
                            var circle = document.createElementNS(ns, 'circle');
                            circle.setAttribute('cx', cx);
                            circle.setAttribute('cy', cy);
                            circle.style.cursor = 'pointer';

                            if (pt.isSelected) {
                                circle.setAttribute('r', '6');
                                circle.setAttribute('fill', '#ec4899');
                                circle.setAttribute('stroke', '#ffffff');
                                circle.setAttribute('stroke-width', '2');
                            } else if (pt.isPareto2D) {
                                circle.setAttribute('r', '5');
                                circle.setAttribute('fill', '#38bdf8');
                                circle.setAttribute('stroke', '#ffffff');
                                circle.setAttribute('stroke-width', '1.5');
                            } else if (pt.isGoal) {
                                circle.setAttribute('r', '3.5');
                                circle.setAttribute('fill', '#22c55e');
                                circle.setAttribute('stroke', 'rgba(255,255,255,0.4)');
                                circle.setAttribute('stroke-width', '0.5');
                            } else {
                                circle.setAttribute('r', '2.5');
                                circle.setAttribute('fill', '#9ca3af');
                                circle.setAttribute('opacity', '0.5');
                            }

                            circle.addEventListener('click', function(e) {
                                if (e && e.stopPropagation) e.stopPropagation();
                                selectAndLoad(pt.p);
                            });

                            circle.addEventListener('mouseenter', function(e) {
                                if (!chartTooltip) return;
                                chartTooltip.style.left = (cx + 10) + 'px';
                                chartTooltip.style.top = (cy - 10) + 'px';
                                chartTooltip.textContent = pt.modelName + '\n' +
                                    xLabelText + ': ' + pt.x + '\n' +
                                    yLabelText + ': ' + pt.y + '\n' +
                                    'Edits: ' + (pt.p.objs[1] !== undefined ? pt.p.objs[1] : '-') + '\n' +
                                    (pt.isPareto2D ? '[2D Pareto Front]' : (pt.isGoal ? '[Goal Reaching]' : '[Misses Goal]'));
                                chartTooltip.style.display = 'block';
                            });

                            circle.addEventListener('mouseleave', function() {
                                if (chartTooltip) chartTooltip.style.display = 'none';
                            });

                            chartSvg.appendChild(circle);
                        });
                    } catch(e) {}
                }

                function selectAndLoad(p) {
                    window.__momotSelectedPath = p.it.modelPath;
                    setStatus('Selected: ' + p.modelName);
                    renderSolutions();
                    try {
                        var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                        if (bridge && bridge.getSolutionPath) {
                            var pathJson = bridge.getSolutionPath(p.it.modelPath);
                            var path = (typeof pathJson === 'string') ? JSON.parse(pathJson) : pathJson;
                            if (window.__dbgDrawComparisonPath) window.__dbgDrawComparisonPath(path);
                        }
                    } catch(eP) {}
                    try {
                        var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                        if (bridge && bridge.loadMomotSolution) {
                            setStatus('Loading ' + p.modelName + '...');
                            bridge.loadMomotSolution(p.it.modelPath);
                        }
                    } catch(eL) {}
                }

                function renderSolutions(data) {
                    try {
                        var arr = [];
                        if (Array.isArray(data)) {
                            arr = data;
                        } else if (data && typeof data === 'object' && Array.isArray(data.solutions)) {
                            arr = data.solutions;
                        } else if (data === undefined || data === null) {
                            var cached = window.__momotLastData;
                            if (Array.isArray(cached)) {
                                arr = cached;
                            } else if (cached && typeof cached === 'object' && Array.isArray(cached.solutions)) {
                                arr = cached.solutions;
                            } else {
                                arr = [];
                            }
                        } else if (data && typeof data === 'object') {
                            arr = data.solutions || [];
                        }
                        var hasData = (Array.isArray(data) && data.length > 0) ||
                                      (data && typeof data === 'object' && Array.isArray(data.solutions) && data.solutions.length > 0);
                        if (hasData) {
                            window.__momotLastData = data;
                        }
                        var keepPath = window.__momotSelectedPath;
                        var keepScroll = list.scrollTop;
                        list.innerHTML = '';
                        if (!arr || !arr.length) {
                            var searchActive = (typeof window.__momotSearchActive === 'function') ? window.__momotSearchActive() : false;
                            var cachedArr = Array.isArray(window.__momotLastData) ? window.__momotLastData :
                                            (window.__momotLastData && Array.isArray(window.__momotLastData.solutions) ? window.__momotLastData.solutions : []);
                            if (searchActive && cachedArr && cachedArr.length > 0) {
                                arr = cachedArr;
                            } else {
                                window.__momotLastData = [];
                                window.__momotLastJson = null;
                                window.__momotSelectedPath = null;
                                setStatus('No solutions found.');
                                list.style.display = 'none';
                                if (typeof renderParetoChart === 'function') renderParetoChart([]);
                                return;
                            }
                        }
                        list.style.display = 'block';
                        var maxObj = 0;
                        var processed = arr.map(function(it) {
                            var ln = it.objectiveLine || "";
                            if (ln === "(unknown)") ln = "";
                            var objs = ln.trim().split(/\s+/).filter(Boolean).map(Number);
                            if (objs.length > maxObj) maxObj = objs.length;
                            var name = (it.modelPath || "").split(/[\/\\]/).pop();
                            var isGoal = objs[0] === -1;
                            return { it: it, objs: objs, modelName: name, isGoal: isGoal };
                        });

                        window.__momotLastProcessed = processed;

                        var hideMisses = chkGoal && chkGoal.checked;
                        var maxEdits = parseFloat(inpFEdits ? inpFEdits.value : '');
                        var maxPower = parseFloat(inpFPower ? inpFPower.value : '');
                        var maxClosest = parseFloat(inpFClosest ? inpFClosest.value : '');
                        var maxMemory = parseFloat(inpFMem ? inpFMem.value : '');

                        var filtered = processed.filter(function(p) {
                            if (hideMisses && !p.isGoal) return false;
                            if (!isNaN(maxEdits) && p.objs[1] !== undefined && p.objs[1] > maxEdits) return false;
                            if (!isNaN(maxPower) && p.objs[2] !== undefined && p.objs[2] > maxPower) return false;
                            if (!isNaN(maxClosest) && p.objs[3] !== undefined && p.objs[3] > maxClosest) return false;
                            if (!isNaN(maxMemory) && p.objs[4] !== undefined && p.objs[4] > maxMemory) return false;
                            return true;
                        });

                        window.__momotLastFiltered = filtered;

                        if (!window.__momotSortKeys || !window.__momotSortKeys.length) {
                            window.__momotSortKeys = [{ col: 0, dir: 1 }];
                        }
                        filtered.sort(function(a, b) {
                            for (var k = 0; k < window.__momotSortKeys.length; k++) {
                                var sKey = window.__momotSortKeys[k];
                                var col = sKey.col;
                                var dir = sKey.dir;
                                var vA, vB;
                                if (col === 0) {
                                    vA = a.isGoal ? 0 : 1;
                                    vB = b.isGoal ? 0 : 1;
                                } else if (col < maxObj) {
                                    vA = (a.objs[col] !== undefined && !isNaN(a.objs[col])) ? a.objs[col] : Infinity;
                                    vB = (b.objs[col] !== undefined && !isNaN(b.objs[col])) ? b.objs[col] : Infinity;
                                } else {
                                    vA = a.modelName; vB = b.modelName;
                                }
                                if (vA < vB) return -1 * dir;
                                if (vA > vB) return 1 * dir;
                            }
                            return 0;
                        });

                        var baseline = null;
                        if (keepPath) {
                            for (var bi = 0; bi < filtered.length; bi++) {
                                if (filtered[bi].it.modelPath === keepPath) {
                                    baseline = filtered[bi];
                                    break;
                                }
                            }
                        }

                        var table = document.createElement('table');
                        table.style.width = '100%'; table.style.borderCollapse = 'collapse'; table.style.fontSize = '11px';
                        table.style.border = '1px solid rgba(255,255,255,0.25)';

                        var thead = document.createElement('thead');
                        var hRow = document.createElement('tr');
                        hRow.style.position = 'sticky'; hRow.style.top = '0'; hRow.style.background = '#444'; hRow.style.zIndex = '10';

                        var objNames = ['Goal Reached', 'Edits', 'Power (kW)', 'Closest to Goal', 'Memory (kB)'];
                        var displayCols = Math.max(maxObj, objNames.length);

                        function mkTh(label, colIdx) {
                            var th = document.createElement('th');
                            th.textContent = label;
                            th.style.padding = '6px 8px';
                            th.style.border = '1px solid rgba(255,255,255,0.25)';
                            th.style.cursor = 'pointer';
                            th.style.textAlign = (colIdx === 0 ? 'center' : 'right');
                            if (colIdx === 2) { th.style.color = '#f59e0b'; th.title = 'Power consumption (in kW)'; }
                            if (colIdx === 4) { th.style.color = '#38bdf8'; th.title = 'Memory consumption (in kB)'; }

                            var sortIdx = -1;
                            for (var k = 0; k < window.__momotSortKeys.length; k++) {
                                if (window.__momotSortKeys[k].col === colIdx) { sortIdx = k; break; }
                            }
                            if (sortIdx !== -1) {
                                var dir = window.__momotSortKeys[sortIdx].dir;
                                var arrow = (dir === 1 ? ' ▲' : ' ▼');
                                if (window.__momotSortKeys.length > 1) {
                                    th.textContent += ' ' + (sortIdx + 1) + (dir === 1 ? '▲' : '▼');
                                } else {
                                    th.textContent += arrow;
                                }
                            }

                            th.addEventListener('click', function(ev) {
                                if (ev.shiftKey) {
                                    var found = false;
                                    for (var k = 0; k < window.__momotSortKeys.length; k++) {
                                        if (window.__momotSortKeys[k].col === colIdx) {
                                            window.__momotSortKeys[k].dir *= -1;
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        window.__momotSortKeys.push({ col: colIdx, dir: 1 });
                                    }
                                } else {
                                    if (window.__momotSortKeys.length === 1 && window.__momotSortKeys[0].col === colIdx) {
                                        window.__momotSortKeys[0].dir *= -1;
                                    } else {
                                        window.__momotSortKeys = [{ col: colIdx, dir: 1 }];
                                    }
                                }
                                window.__momotSortCol = window.__momotSortKeys[0].col;
                                window.__momotSortDir = window.__momotSortKeys[0].dir;
                                renderSolutions();
                            });
                            return th;
                        }

                        for (var i = 0; i < displayCols; i++) {
                            hRow.appendChild(mkTh(objNames[i] || ('Obj ' + (i+1)), i));
                        }
                        thead.appendChild(hRow); table.appendChild(thead);

                        var tbody = document.createElement('tbody');
                        var selectedName = null;
                        filtered.forEach(function(p) {
                            var tr = document.createElement('tr');
                            tr.style.cursor = 'pointer';
                            if (!p.isGoal) tr.style.opacity = '0.65';
                            var isSelected = (keepPath && p.it.modelPath === keepPath);
                            if (isSelected) {
                                tr.style.background = 'rgba(56,189,248,0.2)';
                                tr.style.outline = '1px solid #38bdf8';
                                selectedName = p.modelName;
                            }

                            for (var i = 0; i < displayCols; i++) {
                                var td = document.createElement('td');
                                var val = p.objs[i];
                                if (i === 2) td.style.color = '#f59e0b';
                                if (i === 4) td.style.color = '#38bdf8';

                                if (i === 0) {
                                    td.style.textAlign = 'center';
                                    if (val === -1) td.textContent = 'TRUE';
                                    else if (val === 0) td.textContent = 'FALSE';
                                    else td.textContent = (val !== undefined && !isNaN(val)) ? val : '-';
                                } else {
                                    td.style.textAlign = 'right';
                                    var valValid = (val !== undefined && !isNaN(val) && val < 100000);
                                    var valStr = valValid ? String(val) : '-';

                                    if (baseline && !isSelected && valValid && baseline.objs[i] !== undefined && !isNaN(baseline.objs[i]) && baseline.objs[i] < 100000) {
                                        var baseVal = baseline.objs[i];
                                        var diff = val - baseVal;
                                        var deltaSpan = '';
                                        if (diff > 0) {
                                            deltaSpan = ' <span style="font-size:10px; color:#ef4444;">(+' + diff + ')</span>';
                                        } else if (diff < 0) {
                                            deltaSpan = ' <span style="font-size:10px; color:#22c55e;">(' + diff + ')</span>';
                                        } else {
                                            deltaSpan = ' <span style="font-size:10px; color:#9ca3af;">(0)</span>';
                                        }
                                        td.innerHTML = valStr + deltaSpan;
                                    } else {
                                        td.textContent = valStr;
                                    }
                                }
                                td.style.padding = '6px 8px';
                                td.style.border = '1px solid rgba(255,255,255,0.15)';
                                tr.appendChild(td);
                            }

                            tr.addEventListener('mouseenter', function() {
                                if (window.__momotSelectedPath !== p.it.modelPath) tr.style.background = 'rgba(255,255,255,0.05)';
                            });
                            tr.addEventListener('mouseleave', function() {
                                if (window.__momotSelectedPath !== p.it.modelPath) tr.style.background = 'transparent';
                            });

                            tr.addEventListener('click', function() {
                                selectAndLoad(p);
                            });
                            tbody.appendChild(tr);
                        });
                        table.appendChild(tbody); list.appendChild(table);
                        var goalCount = processed.filter(function(p) { return p.isGoal; }).length;
                        if (selectedName) {
                            window.__momotSelectedPath = keepPath;
                            setStatus('Selected: ' + selectedName + ' (' + filtered.length + ' shown / ' + processed.length + ' total)');
                        } else {
                            window.__momotSelectedPath = null;
                            setStatus(processed.length + ' candidate(s), ' + goalCount + ' reaching the goal' + (filtered.length !== processed.length ? ' (' + filtered.length + ' shown)' : '') + '.');
                        }
                        list.scrollTop = keepScroll;

                        if (typeof renderParetoChart === 'function') {
                            renderParetoChart(filtered);
                        }
                    } catch(e) { setStatus('Failed to render: ' + e); }
                }

                function refresh() {
                    try {
                        var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                        if (!bridge || !bridge.listMomotSolutions) { setStatus('Java bridge listMomotSolutions not available'); return; }
                        setStatus('Loading solutions...');
                        var txt = bridge.listMomotSolutions();
                        var data = [];
                        try { data = (typeof txt === 'string') ? JSON.parse(txt) : txt; } catch(e2) { data = []; }
                        renderSolutions(data);
                    } catch(e) { setStatus('Refresh failed'); }
                }

                window.__momotShowAndRefresh = function(){
                    try { panel.style.display = 'flex'; } catch(e) {}
                    try { refresh(); } catch(e2) {}
                };

                clearOverlayBtn.addEventListener('click', function(){
                    try { if (window.__dbgDrawComparisonPath) window.__dbgDrawComparisonPath([]); } catch(eC) {}
                    try {
                        var oldMarker = document.getElementById('dmgMarker');
                        if (oldMarker && oldMarker.parentNode) oldMarker.parentNode.removeChild(oldMarker);
                    } catch(eM) {}
                    try {
                        var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                        if (bridge && typeof bridge.clearDirectManipulation === 'function') bridge.clearDirectManipulation();
                    } catch(eB) {}
                });
                mStopBtn.addEventListener('click', function(){
                    try {
                        var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                        if (bridge && bridge.stopMomotRun) {
                            bridge.stopMomotRun();
                            setStatus('MoMoT stopping...');
                        }
                    } catch(e) { setStatus('Stop failed'); }
                });
                window.__momotStartRun = function(){
                    try {
                        var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                        if (!bridge || !bridge.runMomotWithParams) { setStatus('Java bridge runMomotWithParams not available'); return; }
                        try {
                            if (window.__momotFirstGoalReset) window.__momotFirstGoalReset();
                        } catch(eR) {}
                        try {
                            if (window.Z && typeof window.__preDmQ === 'number') {
                                window.Q = window.__preDmQ;
                                window.S = window.__preDmS;
                                var t = (typeof window.__stableStartT === 'number') ? window.__stableStartT : window.T;
                                window.T = t;
                                Z(window.Q, window.S, 4 * t);
                            }
                        } catch(eT) {}
                        var s = parseInt(document.getElementById('__momotInpSeed') ? document.getElementById('__momotInpSeed').value : '0') || 0;
                        var p = parseInt(document.getElementById('__momotInpPop') ? document.getElementById('__momotInpPop').value : '50') || 50;
                        var it = parseInt(document.getElementById('__momotInpIter') ? document.getElementById('__momotInpIter').value : '40') || 40;
                        var e = p * it;
                        var r = parseInt(document.getElementById('__momotInpRuns') ? document.getElementById('__momotInpRuns').value : '10') || 10;
                        var sl = parseInt(document.getElementById('__momotInpSolLen') ? document.getElementById('__momotInpSolLen').value : '10') || 10;
                        if (window.__momotProgressStart) {
                            window.__momotProgressStart(r, it);
                        }
                        setStatus('Starting MoMoT (seed=' + s + ', pop=' + p + ', iter=' + it + ' (eval=' + e + '), runs=' + r + ', solLen=' + sl + ')...');
                        try { if (window.__dbgDrawComparisonPath) window.__dbgDrawComparisonPath([]); } catch(eC) {}
                        bridge.runMomotWithParams(s, p, e, r, sl);
                    } catch(e) { setStatus('Run failed: ' + e); }
                };
                mRunBtn.addEventListener('click', window.__momotStartRun);
            } else if (!existing.parentNode) {
                var host2 = document.body || document.documentElement;
                if (host2) host2.appendChild(existing);
            }
            __makePanelDraggableAndResizable('__momotPanel', '__momotHeader', '__momotResizeHandle', 300, 140, null, null);
            window.__momotReady = true;
            return true;
        } catch(e) { return false; }
    }


    // ==========================================
    // 4. Immediate Feedback SVG Overlay Renderer
    // ==========================================
    function setupImmediateFeedbackOverlay() {
        try {
            var ns = 'http://www.w3.org/2000/svg';
            function drawOverlay(id, path, color, offset, dash) {
                var svg = document.getElementById('svgMaze');
                var old = document.getElementById(id);
                if (!path || !path.length || !svg) {
                    if (old && old.parentNode) old.parentNode.removeChild(old);
                    return;
                }
                var off = (typeof offset === 'number') ? offset : 25;
                var pts = [];
                for (var i=0; i<path.length; i++) {
                    pts.push((50*path[i][0]+off) + ',' + (50*path[i][1]+off));
                }
                var ptsString = (path.length >= 2) ? pts.join(' ') : '';

                if (old) {
                    var existingKey = old.getAttribute('data-path-points');
                    var existingPoly = old.querySelector('polyline');
                    var polyMatches = (path.length >= 2) ? (existingPoly && existingPoly.getAttribute('points') === ptsString) : !existingPoly;
                    if (existingKey === ptsString && polyMatches) {
                        return;
                    }
                    if (old.parentNode) old.parentNode.removeChild(old);
                }

                var g = document.createElementNS(ns, 'g');
                g.setAttribute('id', id);
                g.setAttribute('data-immediate-feedback', 'true');
                g.setAttribute('data-path-points', ptsString);
                if (path.length >= 2) {
                    var poly = document.createElementNS(ns, 'polyline');
                    poly.setAttribute('points', ptsString);
                    poly.setAttribute('fill', 'none');
                    poly.setAttribute('stroke', color);
                    poly.setAttribute('stroke-width', '7');
                    poly.setAttribute('stroke-linecap', 'round');
                    poly.setAttribute('stroke-linejoin', 'round');
                    poly.setAttribute('stroke-opacity', '0.9');
                    if (dash) poly.setAttribute('stroke-dasharray', dash);
                    g.appendChild(poly);
                }
                for (var j=0; j<path.length; j++) {
                    var cx = (50*path[j][0]+off);
                    var cy = (50*path[j][1]+off);
                    var c = document.createElementNS(ns, 'circle');
                    c.setAttribute('cx', cx);
                    c.setAttribute('cy', cy);
                    c.setAttribute('r', '4');
                    c.setAttribute('fill', color);
                    c.setAttribute('fill-opacity', '0.75');
                    g.appendChild(c);
                }
                var peg = document.getElementById('pegman');
                if (peg && peg.parentNode) svg.insertBefore(g, peg); else svg.appendChild(g);
            }
            window.__ifRender = function() {
                try {
                    if (window.__injectDmEnabled && window.__injectDmSolutionPath) {
                        var sol = window.__injectDmSolutionPath || [];
                        drawOverlay('dmSol', sol, '#a020f0', 30, '12 6 2 6');
                        var ob = document.getElementById('dmBase'); if (ob && ob.parentNode) ob.parentNode.removeChild(ob);
                        var oc = document.getElementById('dmCommon'); if (oc && oc.parentNode) oc.parentNode.removeChild(oc);
                    } else {
                        var next = window.__injectNewPath || [];
                        // BLOCKY_LIVE_PATH === false (maze-original.html): draw only after Run Program
                        if (window.BLOCKY_LIVE_PATH === false && !window.__runPathShown) next = [];
                        drawOverlay('ifNew', next, '#ff8c1a', 25, '10 6');
                        var op = document.getElementById('ifPast'); if (op && op.parentNode) op.parentNode.removeChild(op);
                        var oc2 = document.getElementById('ifCommon'); if (oc2 && oc2.parentNode) oc2.parentNode.removeChild(oc2);
                    }
                } catch(e) { console.log('__ifRender error: ' + e); }
            };
            window.__dbgRenderOverlay = function(prefix, pastPrefix, newPreview, common) {
                try {
                    if (newPreview && newPreview.length) {
                        window.__injectNewPath = newPreview;
                    } else if (prefix && prefix.length) {
                        window.__injectNewPath = prefix;
                    }
                    if (typeof window.__ifRender === 'function') {
                        window.__ifRender();
                    }
                } catch(e) {}
            };
            window.__ifRender();
        } catch(e) { console.error('ImmediateFeedback definition error:', e); }
    }


    // ==========================================
    // 5. Debugger Control Bar & Direct Manipulation
    // ==========================================
    function setupDebuggerAndControls() {
        var attempts = 0, maxAttempts = 60, interval = 100;
        var id = setInterval(function() {
            try {
                __execLogEnsure();
                if (__momotOn()) __momotEnsure();
                setupImmediateFeedbackOverlay();

                var runBtn = document.getElementById('runButton');
                if (!runBtn) { attempts++; return; }
                var container = runBtn.parentNode;
                if (container) {
                    container.style.display = 'flex';
                    container.style.flexWrap = 'wrap';
                    container.style.gap = '6px';
                    container.style.justifyContent = 'center';
                    container.style.alignItems = 'center';
                }
                if (runBtn) {
                    runBtn.style.minHeight = '44px';
                    runBtn.style.padding = '8px 12px';
                    runBtn.style.fontSize = '14px';
                    runBtn.style.touchAction = 'manipulation';
                }
                if (container && !window.__dbgButtonsBound) {
                    window.__dbgButtonsBound = true;
                    if (id) clearInterval(id);
                    window.__dbgTimer = null;
                    window.__dbgTurnTimers = [];
                    window.__dbgSessionStarted = false;
                    window.__dbgActive = false;
                    window.__dbgStepInFlight = false;
                    window.__dbgLastHighlightedId = null;

                    if (!window.__uiWatchdog) {
                        window.__uiWatchdog = setInterval(function() {
                            try {
                                __execLogEnsure();
                                if (__momotOn()) __momotEnsure();
                            } catch(e) {}
                        }, 1000);
                        if (window.__uiWatchdog && typeof window.__uiWatchdog.unref === 'function') window.__uiWatchdog.unref();
                    }

                    try {
                        var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                        if (bridge) {
                            var startX = (typeof bridge.getStartX === 'function') ? bridge.getStartX() : undefined;
                            var startY = (typeof bridge.getStartY === 'function') ? bridge.getStartY() : undefined;
                            var startT = (typeof bridge.getStartT === 'function') ? bridge.getStartT() : undefined;

                            if (typeof startX === 'number' && !isNaN(startX)) window.Q = startX;
                            if (typeof startY === 'number' && !isNaN(startY)) window.S = startY;
                            if (typeof startT === 'number' && !isNaN(startT)) window.T = startT;

                            if (typeof window.Q === 'number' && typeof window.S === 'number') {
                                window.nd = { x: window.Q, y: window.S };
                            }
                        }
                        if (typeof window.T === 'number') window.__stableStartT = window.T;

                        if (typeof $d === 'function' && document.getElementById('finish')) {
                            $d(false);
                        }
                    } catch(e) {}

                    window.__dbgLastT = undefined;
                    if (!window.__dbgTWatchdog) {
                        window.__dbgTWatchdog = setInterval(function() {
                            try {
                                if (!window.__dbgSessionStarted) return;
                                if (typeof window.T !== 'number') return;
                                if (typeof window.__dbgLastT !== 'number') { window.__dbgLastT = window.T; return; }
                                if (window.__dbgLastT !== window.T) {
                                    window.__dbgLastT = window.T;
                                    if (window.javaBridge) window.javaBridge.logJS('__dbgWatch T changed to ' + window.T);
                                }
                            } catch(e) {}
                        }, 50);
                        if (window.__dbgTWatchdog && typeof window.__dbgTWatchdog.unref === 'function') window.__dbgTWatchdog.unref();
                    }

                    function __dbgSync() {
                        try {
                            var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                            if (!bridge) return;
                            if (bridge.debugTick) {
                                var fr0 = JSON.parse(bridge.debugTick());
                                __dbgRenderFrame(fr0);
                            }
                        } catch(e) {}
                    }

                    function __dbgSetPegman(q, s, t, immediate) {
                        try {
                            if (window.__dbgTurnTimers && window.__dbgTurnTimers.length) {
                                for (var k=0; k<window.__dbgTurnTimers.length; k++) { clearTimeout(window.__dbgTurnTimers[k]); }
                                window.__dbgTurnTimers = [];
                            }
                            var startObj = (typeof window.__getActiveGrid === 'function') ? (function(){
                                try {
                                    var grid = window.__getActiveGrid();
                                    if (grid) {
                                        for (var y=0; y<grid.length; y++) {
                                            for (var x=0; x<grid[y].length; x++) {
                                                if (grid[y][x] === 2) return {x:x, y:y};
                                            }
                                        }
                                    }
                                } catch(e) {}
                                return null;
                            })() : null;

                            var defQ = startObj ? startObj.x : (typeof window.Q === 'number' ? window.Q : 0);
                            var defS = startObj ? startObj.y : (typeof window.S === 'number' ? window.S : 0);

                            var validQ = (typeof q === 'number' && !isNaN(q)) ? q : defQ;
                            var validS = (typeof s === 'number' && !isNaN(s)) ? s : defS;

                            var validT = (typeof t === 'number' && !isNaN(t)) ? t : (typeof window.T === 'number' ? window.T : 0);

                            var oldQ = window.Q, oldS = window.S, oldT = window.T;
                            if (typeof oldT !== 'number' || isNaN(oldT)) oldT = validT;
                            var oldD = 4 * oldT, newD = 4 * validT;
                            window.Q = validQ; window.S = validS; window.T = validT;

                            if (typeof Z === 'function') {
                                if (!immediate && oldQ === validQ && oldS === validS && oldT !== validT) {
                                    var frames = 4, frameDelay = 50;
                                    for (var i=1; i<=frames; i++) {
                                        (function(step){
                                            var tid = setTimeout(function() {
                                                var d = Math.round(oldD + (newD - oldD) * (step/frames));
                                                if (typeof Z === 'function') Z(validQ, validS, d);
                                            }, step * frameDelay);
                                            window.__dbgTurnTimers.push(tid);
                                        })(i);
                                    }
                                } else {
                                    Z(validQ, validS, newD);
                                }
                            }
                        } catch(e) {}
                    }

                    function __dbgRenderFrame(fr, immediate) {
                        try {
                            if (!fr) return;
                            window.__dbgSessionStarted = true;
                            if (typeof __dbgRenderOverlay === 'function') __dbgRenderOverlay(fr.prefix, fr.pastPrefix, fr.newPreview, fr.common);
                            try {
                                var ws = (window.BlocklyInterface && window.BlocklyInterface.getWorkspace && window.BlocklyInterface.getWorkspace()) ||
                                         (window.Blockly && window.Blockly.getMainWorkspace && window.Blockly.getMainWorkspace()) || null;
                                var bid = (fr.blockId && fr.blockId.length) ? fr.blockId : null;
                                if (window.__dbgLastHighlightedId !== bid) {
                                    window.__dbgLastHighlightedId = bid;
                                    var ok = false;
                                    try {
                                        if (ws && typeof ws.highlightBlock === 'function') { ws.highlightBlock(bid); ok = true; }
                                    } catch(eh) {}
                                    if (!ok && window.Blockly) {
                                        try { if (Blockly.selected && Blockly.selected.unselect) Blockly.selected.unselect(); } catch(e2) {}
                                        var b = null;
                                        try { if (ws && bid && typeof ws.getBlockById === 'function') b = ws.getBlockById(bid); } catch(e3) {}
                                        try {
                                            if (b && typeof b.select === 'function') { b.select(); ok = true; }
                                        } catch(e6) {}
                                    }
                                    if (!ok) {
                                        try {
                                            if (window.__dbgLastHighlightedEl) {
                                                window.__dbgLastHighlightedEl.classList.remove('blocklySelected');
                                                window.__dbgLastHighlightedEl = null;
                                            }
                                            if (bid) {
                                                var nodes = document.querySelectorAll('[data-id]');
                                                for (var ni=0; ni<nodes.length; ni++) {
                                                    var cand = nodes[ni];
                                                    if (cand && cand.getAttribute && cand.getAttribute('data-id') === bid) {
                                                        cand.classList.add('blocklySelected');
                                                        window.__dbgLastHighlightedEl = cand;
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch(ed) {}
                                    }
                                }
                            } catch(e) {}

                            __dbgSetPegman(fr.q, fr.s, fr.t, immediate);
                            var pauseBtn = document.getElementById('debugPauseResumeButton');
                            var stepBtn = document.getElementById('debugStepButton');
                            var skipBtn = document.getElementById('debugSkipEndButton');
                            var terminal = !!fr.result && fr.result !== 'RUNNING';
                            var terminalEditable = terminal && !!fr.dirty;

                            if (pauseBtn) pauseBtn.textContent = fr.paused ? 'Resume' : 'Pause';
                            if (pauseBtn && terminal) pauseBtn.textContent = 'Resume';
                            if (stepBtn) {
                                stepBtn.disabled = terminal && !terminalEditable;
                                stepBtn.title = (terminal && terminalEditable) ? ('Program changed after ' + fr.result + ' — realign & step') : (terminal ? ('Debugger finished: ' + fr.result) : 'Execute one step');
                            }
                            if (skipBtn) {
                                skipBtn.disabled = terminal && !terminalEditable;
                                skipBtn.title = (terminal && terminalEditable) ? ('Program changed after ' + fr.result + ' — realign & jump') : (terminal ? ('Debugger finished: ' + fr.result) : 'Jump to final outcome');
                            }
                            if (fr.paused && window.__dbgTimer) { clearInterval(window.__dbgTimer); window.__dbgTimer = null; }
                            if (terminal && !terminalEditable) {
                                window.__dbgActive = false;
                                if (window.__dbgTimer) { clearInterval(window.__dbgTimer); window.__dbgTimer = null; }
                            }
                        } catch(e) {}
                    }

                    function __dbgStart() {
                        try {
                            var stepBtn = document.getElementById('debugStepButton');
                            var skipBtn = document.getElementById('debugSkipEndButton');
                            if (stepBtn) { stepBtn.disabled = false; stepBtn.title = 'Execute one step'; }
                            if (skipBtn) { skipBtn.disabled = false; skipBtn.title = 'Jump to final outcome'; }
                            window.__dbgStepInFlight = false;
                            window.__dbgLastLoggedIndex = -1;
                            var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
                            var startX = bridge && bridge.getStartX ? bridge.getStartX() : undefined;
                            var startY = bridge && bridge.getStartY ? bridge.getStartY() : undefined;
                            var startT = bridge && bridge.getStartT ? bridge.getStartT() : undefined;
                            window.__dbgActive = true;
                            if (!bridge || !bridge.debugStart) return null;
                            var frStr = bridge.debugStart(startX, startY, startT);
                            var fr = JSON.parse(frStr);
                            __dbgRenderFrame(fr);
                            return fr;
                        } catch(e) { window.__dbgActive = false; return null; }
                    }

                    function __dbgTogglePause() {
                        try {
                            if (!window.__dbgSessionStarted) { __dbgStart(); }
                            var frStr = window.javaBridge.debugTogglePause();
                            var fr = JSON.parse(frStr);
                            __dbgRenderFrame(fr);
                            if (!fr.paused && !window.__dbgTimer) {
                                window.__dbgTimer = setInterval(function() {
                                    try {
                                        var fr2Str = window.javaBridge.debugTick();
                                        var fr2 = JSON.parse(fr2Str);
                                        __dbgRenderFrame(fr2);
                                        if (fr2.paused && window.__dbgTimer) { clearInterval(window.__dbgTimer); window.__dbgTimer = null; }
                                    } catch(e) {}
                                }, 150);
                            }
                        } catch(e) {}
                    }

                    function __dbgStep() {
                        try {
                            if (window.__dbgStepInFlight) return;
                            if (!window.__dbgSessionStarted) { __dbgStart(); }
                            var stepBtn = document.getElementById('debugStepButton');
                            if (stepBtn) { stepBtn.disabled = true; stepBtn.title = 'Stepping...'; }
                            window.__dbgStepInFlight = true;
                            var frStr = window.javaBridge.debugStep();
                            var fr = JSON.parse(frStr);
                            __dbgRenderFrame(fr);
                            if (window.__dbgTimer) { clearInterval(window.__dbgTimer); window.__dbgTimer = null; }
                        } catch(e) {} finally {
                            window.__dbgStepInFlight = false;
                            var stepBtn2 = document.getElementById('debugStepButton');
                            if (stepBtn2 && !stepBtn2.disabled) stepBtn2.title = 'Execute one step';
                        }
                    }

                    function __dbgStop() {
                        try {
                            if (!window.javaBridge || !window.javaBridge.debugStop) return;
                            var frStr = window.javaBridge.debugStop();
                            var fr = JSON.parse(frStr);
                            __dbgRenderFrame(fr);
                            try { if (window.__execLogClear) window.__execLogClear(); } catch(e) {}
                            window.__dbgSessionStarted = false;
                            window.__dbgActive = false;
                            window.__dbgStepInFlight = false;
                            if (window.__dbgTimer) { clearInterval(window.__dbgTimer); window.__dbgTimer = null; }
                        } catch(e) {}
                    }

                    function __dbgMkBtn(id, label, title) {
                        var b = document.getElementById(id);
                        if (!b) {
                            b = document.createElement('button');
                            b.id = id;
                            b.className = 'primary';
                            b.textContent = label;
                            b.title = title;
                            b.style.minHeight = '44px';
                            b.style.padding = '8px 12px';
                            b.style.fontSize = '14px';
                            b.style.touchAction = 'manipulation';
                            container.appendChild(b);
                        }
                        return b;
                    }

                    var debugPauseResumeBtn = __dbgMkBtn('debugPauseResumeButton', 'Resume', 'Pause/Resume debugging');
                    var debugStopBtn = __dbgMkBtn('debugStopButton', 'Stop', 'Stop debugging and reset');
                    var debugStepBtn = __dbgMkBtn('debugStepButton', 'Step', 'Execute one step');
                    var debugSkipBtn = __dbgMkBtn('debugSkipEndButton', 'Skip End', 'Jump to final outcome');
                    var directManipBtn = __dbgMkBtn('directManipulationButton', 'Direct Manipulation', 'Teleport pegman to an empty/goal cell (paused or before run)');
                    directManipBtn.style.marginLeft = '8px';
                    if (!__momotOn()) directManipBtn.style.display = 'none';
                    var execLogToggleBtn = __dbgMkBtn('execLogTogglePanelButton', 'Execution Log', 'Show or hide Execution Log panel');
                    execLogToggleBtn.style.marginLeft = '8px';

                    execLogToggleBtn.addEventListener('click', function() {
                        try {
                            __execLogEnsure();
                            var p = document.getElementById('__execLogPanel');
                            if (p) {
                                p.style.display = (p.style.display === 'none') ? 'block' : 'none';
                            }
                        } catch(e) {}
                    });

                    function __dbgPerformSkip() {
                        try {
                            if (!window.__dbgSessionStarted) __dbgStart();
                            if (window.Y && Array.isArray(window.Y)) {
                                for (var yi = 0; yi < window.Y.length; yi++) {
                                    clearTimeout(window.Y[yi]);
                                }
                                window.Y = [];
                            }
                            if (window.__dbgTurnTimers && window.__dbgTurnTimers.length) {
                                for (var k = 0; k < window.__dbgTurnTimers.length; k++) {
                                    clearTimeout(window.__dbgTurnTimers[k]);
                                }
                                window.__dbgTurnTimers = [];
                            }
                            if (window.__dbgTimer) {
                                clearInterval(window.__dbgTimer);
                                window.__dbgTimer = null;
                            }
                            var frStr = window.javaBridge.debugSkipToEnd();
                            var fr = JSON.parse(frStr);
                            __dbgRenderFrame(fr, true);
                            if (window.__dbgTurnTimers && window.__dbgTurnTimers.length) {
                                for (var k2 = 0; k2 < window.__dbgTurnTimers.length; k2++) {
                                    clearTimeout(window.__dbgTurnTimers[k2]);
                                }
                                window.__dbgTurnTimers = [];
                            }
                        } catch(e) {}
                    }

                    debugPauseResumeBtn.addEventListener('click', function() { __dbgTogglePause(); });
                    debugStopBtn.addEventListener('click', function() { __dbgStop(); });
                    debugStepBtn.addEventListener('click', function() { __dbgStep(); });

                    if (!window.__dbgDelegationBound) {
                        window.__dbgDelegationBound = true;
                        document.addEventListener('click', function(ev) {
                            try {
                                var tgt = ev && ev.target ? ev.target : null;
                                if (!tgt || !tgt.id) return;
                                if (tgt.id === 'debugPauseResumeButton') { __dbgTogglePause(); ev.preventDefault(); ev.stopPropagation(); }
                                else if (tgt.id === 'debugStopButton') { __dbgStop(); ev.preventDefault(); ev.stopPropagation(); }
                                else if (tgt.id === 'debugStepButton') { __dbgStep(); ev.preventDefault(); ev.stopPropagation(); }
                                else if (tgt.id === 'debugSkipEndButton') { __dbgPerformSkip(); ev.preventDefault(); ev.stopPropagation(); }
                            } catch(e) {}
                        }, true);
                    }

                    if (!window.__dbgSkipBound && !debugSkipBtn.__skipBound) {
                        debugSkipBtn.__skipBound = true;
                        debugSkipBtn.addEventListener('click', function() {
                            __dbgPerformSkip();
                        });
                    }

                    // Direct Manipulation Click Logic
                    window.__dmActive = false;
                    function __dmCanEnable() {
                        try {
                            if (typeof window.__momotSearchActive === 'function' && window.__momotSearchActive()) return false;
                            var paused = (!window.__dbgTimer);
                            var inDebug = !!window.__dbgSessionStarted;
                            var beforeRun = !window.__blockyRunStarted && !inDebug;
                            return beforeRun || (inDebug && paused);
                        } catch(e) { return false; }
                    }
                    function __dmUpdateButton() {
                        try {
                            if (!directManipBtn) return;
                            var en = __dmCanEnable();
                            directManipBtn.disabled = !en;
                            directManipBtn.style.opacity = en ? '1.0' : '0.55';
                            directManipBtn.textContent = window.__dmActive ? 'Direct Manipulation (click a cell)' : 'Direct Manipulation';
                        } catch(e) {}
                    }
                    function __dmStop() {
                        try { window.__dmActive = false; __dmUpdateButton(); } catch(e) {}
                        try {
                            if (window.__dmClickHandler && window.__dmClickTarget) {
                                window.__dmClickTarget.removeEventListener('click', window.__dmClickHandler, true);
                                window.__dmClickTarget.removeEventListener('touchend', window.__dmClickHandler, true);
                            }
                        } catch(e) {}
                        try { window.__dmClickHandler = null; window.__dmClickTarget = null; } catch(e) {}
                        try { document.body.style.cursor = ''; } catch(e) {}
                    }
                    function __dmStart() {
                        try {
                            if (!__dmCanEnable()) {
                                __dmStop();
                                try {
                                    if (typeof window.__momotSearchActive === 'function' && window.__momotSearchActive() && window.__momotSetStatus) {
                                        window.__momotSetStatus('A search is running. Press Stop before placing a new marker.');
                                    }
                                } catch(eM) {}
                                return;
                            }
                            var svg = document.getElementById('svgMaze');
                            if (!svg) return;
                            window.__dmActive = true; __dmUpdateButton();
                            try { document.body.style.cursor = 'crosshair'; } catch(e) {}
                            window.__dmClickTarget = svg;
                            window.__dmClickHandler = function(ev) {
                                try {
                                    if (!window.__dmActive || !ev) return;
                                    var rect = svg.getBoundingClientRect();
                                    var clientX = ev.clientX;
                                    var clientY = ev.clientY;
                                    if (ev.changedTouches && ev.changedTouches.length > 0) {
                                        clientX = ev.changedTouches[0].clientX;
                                        clientY = ev.changedTouches[0].clientY;
                                    } else if (ev.touches && ev.touches.length > 0) {
                                        clientX = ev.touches[0].clientX;
                                        clientY = ev.touches[0].clientY;
                                    }
                                    if (typeof clientX !== 'number' || isNaN(clientX)) return;
                                    var cx = clientX - rect.left;
                                    var cy = clientY - rect.top;
                                    var grid = (typeof window.__getActiveGrid === 'function') ? window.__getActiveGrid() : window.X;
                                    if (!grid || !grid.length || !grid[0] || !grid[0].length) return;
                                    var height = grid.length;
                                    var width  = grid[0].length;
                                    var col = Math.floor((cx / rect.width)  * width);
                                    var row = Math.floor((cy / rect.height) * height);
                                    if (col < 0 || row < 0 || col >= width || row >= height) return;
                                    var v = grid[row][col];
                                    if (!(v === 1 || v === 3)) return; // 0 = WALL, 2 = START
                                    var t = (typeof window.T === 'number') ? window.T : ((typeof window.__stableStartT === 'number') ? window.__stableStartT : 0);
                                    try { if (typeof __dbgSetPegman === 'function') __dbgSetPegman(col, row, t); } catch(e3) {}

                                    var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);

                                    function doPlacementAndStart() {
                                        if (!__momotOn()) return;
                                        var pPlacement = null;
                                        try {
                                            if (bridge && bridge.teleportPegman) {
                                                pPlacement = bridge.teleportPegman(col, row, t);
                                            }
                                        } catch(e4) {}

                                        function onPlacementDone() {
                                            try {
                                                var goalObj = (typeof window.od === 'object' && window.od) ? window.od : null;
                                                var isGoal = goalObj && goalObj.x === col && goalObj.y === row;
                                                var oldMarker = document.getElementById('dmgMarker');
                                                if (oldMarker && oldMarker.parentNode) oldMarker.parentNode.removeChild(oldMarker);

                                                if (!isGoal) {
                                                    var svg = document.getElementById('svgMaze');
                                                    if (svg) {
                                                        var ns = 'http://www.w3.org/2000/svg';
                                                        var g = document.createElementNS(ns, 'g');
                                                        g.setAttribute('id', 'dmgMarker');
                                                        g.setAttribute('transform', 'translate(' + (50 * col + 25) + ',' + (50 * row + 25) + ')');
                                                        var star = document.createElementNS(ns, 'path');
                                                        star.setAttribute('d', 'M 0,-15 L 4.5,-4.5 L 15,-4.5 L 6.5,3 L 10,14 L 0,7 L -10,14 L -6.5,3 L -15,-4.5 L -4.5,-4.5 Z');
                                                        star.setAttribute('fill', '#ff0000');
                                                        star.setAttribute('stroke', '#ffffff');
                                                        star.setAttribute('stroke-width', '2');
                                                        g.appendChild(star);
                                                        var peg = document.getElementById('pegman');
                                                        if (peg && peg.parentNode) svg.insertBefore(g, peg);
                                                        else svg.appendChild(g);
                                                    }
                                                }
                                            } catch(eG) {}
                                            try { if (window.__momotShowAndRefresh) window.__momotShowAndRefresh(); } catch(e4b) {}
                                            try { if (window.__momotStartRun) window.__momotStartRun(); } catch(e4c) {}
                                        }

                                        if (pPlacement && typeof pPlacement.then === 'function') {
                                            pPlacement.then(onPlacementDone).catch(function(err) {
                                                console.error('[DM] teleportPegman failed:', err);
                                                onPlacementDone();
                                            });
                                        } else {
                                            onPlacementDone();
                                        }
                                    }

                                    var isRunning = false;
                                    try {
                                        if (bridge && typeof bridge.isMomotRunning === 'function') {
                                            isRunning = bridge.isMomotRunning();
                                        } else if (typeof window.__momotIsRunning === 'boolean') {
                                            isRunning = window.__momotIsRunning;
                                        }
                                    } catch(eR) {}

                                    if (isRunning && bridge && bridge.stopMomotRun) {
                                        var pStop = null;
                                        try {
                                            pStop = bridge.stopMomotRun();
                                        } catch(eS) {}
                                        if (pStop && typeof pStop.then === 'function') {
                                            pStop.then(doPlacementAndStart).catch(doPlacementAndStart);
                                        } else {
                                            doPlacementAndStart();
                                        }
                                    } else {
                                        doPlacementAndStart();
                                    }

                                    if (ev.type === 'touchend' && ev.preventDefault) ev.preventDefault();
                                    __dmStop();
                                } catch(e5) { __dmStop(); }
                            };
                            svg.addEventListener('click', window.__dmClickHandler, true);
                            svg.addEventListener('touchend', window.__dmClickHandler, true);
                        } catch(e) {}
                    }

                    directManipBtn.addEventListener('click', function(){
                        try {
                            if (window.__dmActive) __dmStop();
                            else {
                                window.__preDmQ = window.Q;
                                window.__preDmS = window.S;
                                __dmStart();
                            }
                        } catch(e) {}
                    });

                    try {
                        var dmTimer = setInterval(__dmUpdateButton, 200);
                        if (dmTimer && typeof dmTimer.unref === 'function') dmTimer.unref();
                    } catch(e) {}
                    __dmUpdateButton();
                }
                if (window.__dbgButtonsBound) clearInterval(id);
                attempts++;
                if (attempts >= maxAttempts) clearInterval(id);
            } catch(e) {}
        }, interval);
    }

    setupDebuggerAndControls();

})();
