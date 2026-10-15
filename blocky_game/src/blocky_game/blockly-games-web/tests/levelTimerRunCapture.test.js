const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert');
const { JSDOM } = require('jsdom');
const path = require('node:path');
const fs = require('node:fs');

describe('Level Timer and Activity Headers Test Suite', () => {
    let dom;
    let window;
    let capturedRequests = [];

    beforeEach(() => {
        capturedRequests = [];
        dom = new JSDOM('<!DOCTYPE html><html><body><button id="runButton">Run Program</button><button id="resetButton" style="display: none">Reset</button><div id="blockly"></div><svg id="svgMaze"></svg></body></html>', {
            url: 'http://localhost/?level=3',
            runScripts: 'dangerously'
        });
        window = dom.window;
        global.window = window;
        global.document = window.document;
        global.sessionStorage = window.sessionStorage;
        global.localStorage = window.localStorage;

        // Set session ID in sessionStorage
        window.sessionStorage.setItem('blocky_level_timer_session_id', 'test_user_sess_999');
        window.sessionStorage.setItem('blocky_level_timer_user_id', 'alice');
        window.sessionStorage.setItem('blocky_level_timer_current_level', '3');

        // Mock fetch
        window.fetch = async (url, options = {}) => {
            capturedRequests.push({ url, options });
            if (url.includes('/api/session/new')) {
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({ status: 'ok', sessionId: 'test-session-123' })
                };
            }
            return {
                ok: true,
                status: 200,
                json: async () => ({ status: 'ok', levels: {} })
            };
        };
        global.fetch = window.fetch;
    });

    afterEach(() => {
        if (window && window.__levelTimerCleanup) {
            try { window.__levelTimerCleanup(); } catch (e) {}
        }
        if (dom && dom.window) {
            dom.window.close();
        }
    });

    it('records program run when runButton is clicked via levelTimer hook', () => {
        window.BlocklyInterface = {
            getCode: () => '<xml><block type="maze_moveForward"></block></xml>'
        };

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        const runBtn = window.document.getElementById('runButton');
        assert.ok(runBtn, 'runButton should exist');

        runBtn.click();

        const simCalls = capturedRequests.filter(r => r.url.includes('/api/simulation/run'));
        assert.strictEqual(simCalls.length, 1, 'Should have triggered 1 simulation/run call');
        const req = simCalls[0];
        assert.strictEqual(req.options.headers['X-Timer-Session-ID'], 'test_user_sess_999');
        assert.strictEqual(req.options.headers['X-Level-ID'], '3');
        const body = JSON.parse(req.options.body);
        assert.strictEqual(body.timerSessionId, 'test_user_sess_999');
        assert.strictEqual(body.level, 3);
        assert.strictEqual(body.recordExecution, true);
        assert.ok(body.timestamp, 'timestamp should be present');
        assert.strictEqual(body.xml, '<xml><block type="maze_moveForward"></block></xml>');
    });

    it('falls back to localStorage workspace XML when BlocklyInterface is unavailable', () => {
        window.localStorage.setItem('maze3', '<xml><block type="maze_turn"><field name="DIR">turnLeft</field></block></xml>');

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        const runBtn = window.document.getElementById('runButton');
        runBtn.click();

        const simCalls = capturedRequests.filter(r => r.url.includes('/api/simulation/run'));
        assert.strictEqual(simCalls.length, 1);
        const body = JSON.parse(simCalls[0].options.body);
        assert.strictEqual(body.recordExecution, true);
        assert.strictEqual(body.xml, '<xml><block type="maze_turn"><field name="DIR">turnLeft</field></block></xml>');
    });

    it('does not count a Reset click or the Run click the browser fires right after it', () => {
        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        const runBtn = window.document.getElementById('runButton');
        const resetBtn = window.document.getElementById('resetButton');
        runBtn.click();
        runBtn.style.display = 'none';
        resetBtn.style.display = 'inline';
        resetBtn.click();
        resetBtn.style.display = 'none';
        runBtn.style.display = 'inline';
        runBtn.click();

        const simCalls = capturedRequests.filter(r => r.url.includes('/api/simulation/run'));
        assert.strictEqual(simCalls.length, 1, 'Reset and its follow-up Run click must not count');
    });

    it('attaches timer headers and payload to MOMoT actions in webBridge', async () => {
        const bridgeScript = fs.readFileSync(path.join(__dirname, '../common/webBridge.js'), 'utf8');
        window.eval(bridgeScript);

        assert.ok(window.javaBridge, 'javaBridge should exist');

        // Test runMomotWithParams
        window.javaBridge.runMomotWithParams(42, 20, 1000, 1, 10);
        await new Promise(r => setTimeout(r, 250));
        const momotCalls = capturedRequests.filter(r => r.url.includes('/api/momot/run'));
        assert.strictEqual(momotCalls.length, 1);
        assert.strictEqual(momotCalls[0].options.headers['X-Timer-Session-ID'], 'test_user_sess_999');
        assert.strictEqual(momotCalls[0].options.headers['X-Level-ID'], '3');

        // Test loadMomotSolution
        window.javaBridge.loadMomotSolution('models/sol1.xmi');
        await new Promise(r => setTimeout(r, 50));
        const loadCalls = capturedRequests.filter(r => r.url.includes('/api/momot/load'));
        assert.strictEqual(loadCalls.length, 1);
        assert.strictEqual(loadCalls[0].options.headers['X-Timer-Session-ID'], 'test_user_sess_999');
        assert.strictEqual(loadCalls[0].options.headers['X-Level-ID'], '3');

        // Test teleportPegman (Direct Manipulation)
        window.javaBridge.teleportPegman(1, 2, 0);
        await new Promise(r => setTimeout(r, 50));
        const dmCalls = capturedRequests.filter(r => r.url.includes('/api/dm/request'));
        assert.strictEqual(dmCalls.length, 1);
        assert.strictEqual(dmCalls[0].options.headers['X-Timer-Session-ID'], 'test_user_sess_999');
        assert.strictEqual(dmCalls[0].options.headers['X-Level-ID'], '3');
    });

    it('locks header level links by turning anchors into non-navigable spans', () => {
        const headerDiv = window.document.createElement('div');
        headerDiv.innerHTML = [
            '<span class="level_number level_done" id="level3">3</span>',
            '<a class="level_dot" id="level1" href="?level=1"></a>',
            '<a class="level_dot" id="level2" href="?level=2"></a>',
            '<a class="level_number" id="level10" href="?level=10">10</a>'
        ].join('');
        window.document.body.appendChild(headerDiv);

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        const remainingAnchors = window.document.querySelectorAll('a.level_dot, a.level_number');
        assert.strictEqual(remainingAnchors.length, 0, 'No clickable anchor level links should remain');

        const spanDots = window.document.querySelectorAll('span.level_dot');
        assert.strictEqual(spanDots.length, 2, 'Should convert dots to span elements');
        assert.strictEqual(spanDots[0].id, 'level1');
        assert.strictEqual(spanDots[1].id, 'level2');

        const span10 = window.document.getElementById('level10');
        assert.ok(span10, 'level10 element should exist');
        assert.strictEqual(span10.tagName.toLowerCase(), 'span', 'level10 should be a span, not anchor');
        assert.strictEqual(span10.textContent, '10');
        assert.strictEqual(span10.getAttribute('href'), null, 'level10 should have no href');
    });

    it('suppresses goal win dialog and skip dialog without marking solved or showing note', () => {
        window.BlocklyDialogs = {
            Xy: () => { throw new Error('Original win dialog should not be called'); },
            Zs: () => { throw new Error('Original skip dialog should not be called'); },
            kc: () => {}
        };
        window.BlocklyInterface = {
            Um: () => { throw new Error('Original Um should not be called'); }
        };

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        const goalNote = window.document.getElementById('timerGoalNote');
        const nextBtn = window.document.getElementById('timerNextTaskBtn');
        assert.ok(goalNote, 'Goal note element should exist in timer widget');
        assert.strictEqual(goalNote.style.display, 'none', 'Goal note should be hidden when unsolved');
        assert.strictEqual(nextBtn.disabled, true, 'Next button should be disabled when unsolved');

        // Simulate Xy trigger (animation finished)
        window.BlocklyDialogs.Xy();
        assert.strictEqual(goalNote.style.display, 'none', 'Xy call must NOT show goal note');
        assert.strictEqual(nextBtn.disabled, true, 'Xy call must NOT enable Next button');
        assert.strictEqual(window.__levelTimerIsLevelSolved(3), false, 'Xy call must NOT mark level solved');

        // Calling Um and Zs should be safe no-ops
        assert.doesNotThrow(() => {
            window.BlocklyInterface.Um();
            window.BlocklyDialogs.Zs();
        });

        // Maze assigns BlocklyInterface first, then overwrites Um with next-level navigation.
        const iface = {};
        window.BlocklyInterface = iface;
        iface.Um = () => { throw new Error('Later Um overwrite must not navigate'); };
        assert.doesNotThrow(() => iface.Um());

        const dialogs = {
            Xy: () => { throw new Error('Original win dialog should not be called'); },
            kc: () => {}
        };
        window.BlocklyDialogs = dialogs;
        dialogs.Xy = () => { throw new Error('Later Xy overwrite must not open the dialog'); };
        assert.doesNotThrow(() => dialogs.Xy());
        assert.strictEqual(goalNote.style.display, 'none');
    });

    it('gates Next task button on maze-original as well until level is solved', () => {
        const origDom = new JSDOM('<!DOCTYPE html><html><body><button id="runButton">Run Program</button></body></html>', {
            url: 'http://localhost/maze-original.html?level=3',
            runScripts: 'dangerously'
        });
        const origWin = origDom.window;
        global.window = origWin;
        global.document = origWin.document;
        global.sessionStorage = origWin.sessionStorage;
        global.localStorage = origWin.localStorage;

        origWin.sessionStorage.setItem('blocky_level_timer_session_id', 'test_sess_orig');
        origWin.sessionStorage.setItem('blocky_level_timer_user_id', 'alice');
        origWin.sessionStorage.setItem('blocky_level_timer_current_level', '3');

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        origWin.eval(timerScript);

        const nextBtn = origWin.document.getElementById('timerNextTaskBtn');
        assert.ok(nextBtn, 'Next task button should exist on maze-original');
        assert.strictEqual(nextBtn.disabled, true, 'Next task button should be disabled on maze-original before goal reach');

        if (origWin.__levelTimerCleanup) origWin.__levelTimerCleanup();
        origDom.window.close();
    });

    it('only marks level solved when run response has goalReached: true and matching levelId', async () => {
        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        const goalNote = window.document.getElementById('timerGoalNote');
        const nextBtn = window.document.getElementById('timerNextTaskBtn');
        assert.strictEqual(goalNote.style.display, 'none');
        assert.strictEqual(nextBtn.disabled, true);

        // 1. Run response with goalReached: true for wrong levelId (levelId: 2 when on level 3)
        window.fetch = async (url) => {
            if (url.includes('/api/simulation/run')) {
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({ status: 'ok', goalReached: true, levelId: 2 })
                };
            }
            return { ok: true, status: 200, json: async () => ({ status: 'ok' }) };
        };

        const runBtn = window.document.getElementById('runButton');
        runBtn.click();
        await new Promise(r => setTimeout(r, 50));

        assert.strictEqual(window.__levelTimerIsLevelSolved(3), false, 'Mismatched levelId in goalReached response must not solve level 3');
        assert.strictEqual(goalNote.style.display, 'none');
        assert.strictEqual(nextBtn.disabled, true);

        // 2. Run response with goalReached: true for matching levelId (levelId: 3)
        window.fetch = async (url) => {
            if (url.includes('/api/simulation/run')) {
                return {
                    ok: true,
                    status: 200,
                    json: async () => ({ status: 'ok', goalReached: true, levelId: 3 })
                };
            }
            return { ok: true, status: 200, json: async () => ({ status: 'ok' }) };
        };

        runBtn.click();
        await new Promise(r => setTimeout(r, 50));

        assert.strictEqual(window.__levelTimerIsLevelSolved(3), true, 'Matching levelId in goalReached response must solve level 3');
        assert.strictEqual(goalNote.style.display, 'inline-block');
        assert.strictEqual(nextBtn.disabled, false);
    });

    it('clears all solved flags, all levels completed flag, and scoreboard storage on New Session', () => {
        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        window.__levelTimerMarkLevelSolved(3);
        window.sessionStorage.setItem('blocky_all_levels_completed', 'true');
        for (let l = 1; l <= 10; l++) {
            window.sessionStorage.setItem('blocky_scoreboard_lvl_' + l, JSON.stringify([{ id: 1, memory: 2, power: 2 }]));
        }
        assert.strictEqual(window.__levelTimerIsLevelSolved(3), true);
        assert.ok(window.sessionStorage.getItem('blocky_scoreboard_lvl_1'));
        assert.ok(window.sessionStorage.getItem('blocky_scoreboard_lvl_10'));

        const newSessBtn = window.document.getElementById('timerNewSessionBtn');
        assert.ok(newSessBtn);
        newSessBtn.click();

        const submitBtn = window.document.getElementById('levelTimerModalSubmit');
        assert.ok(submitBtn);
        submitBtn.click();

        assert.strictEqual(window.sessionStorage.getItem('blocky_solved_level_3'), null, 'New session must clear level solved flag');
        assert.strictEqual(window.sessionStorage.getItem('blocky_all_levels_completed'), null, 'New session must clear all levels completed flag');
        for (let l = 1; l <= 10; l++) {
            assert.strictEqual(window.sessionStorage.getItem('blocky_scoreboard_lvl_' + l), null, 'New session must clear blocky_scoreboard_lvl_' + l);
        }
    });

    it('gates Next task button until level is solved, then confirms before advancing', () => {
        let navigatedUrl = null;
        window.__levelTimerNavigate = (url) => { navigatedUrl = url; };

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        const nextBtn = window.document.getElementById('timerNextTaskBtn');
        assert.ok(nextBtn, 'Next task button should exist on level 3');
        assert.strictEqual(nextBtn.disabled, true, 'Next task button should initially be disabled on level 3 before goal reach');

        // Click when disabled should show toast and not open confirm modal
        nextBtn.click();
        let confirmModal = window.document.getElementById('levelTimerConfirmModal');
        assert.strictEqual(confirmModal, null, 'Confirm modal should not open when level is unsolved');

        // Solve the level
        window.__levelTimerMarkLevelSolved(3);
        assert.strictEqual(nextBtn.disabled, false, 'Next task button should be enabled after level is solved');

        // Now clicking next task shows confirm modal
        nextBtn.click();

        confirmModal = window.document.getElementById('levelTimerConfirmModal');
        assert.ok(confirmModal, 'Confirm modal should exist');
        assert.strictEqual(confirmModal.style.display, 'flex', 'Confirm modal should be visible');
        assert.ok(confirmModal.textContent.includes('Go to the next task?'));

        let storedLevel = window.sessionStorage.getItem('blocky_level_timer_current_level');
        assert.strictEqual(storedLevel, '3', 'First click must not change stored level');
        assert.strictEqual(navigatedUrl, null, 'First click must not navigate');

        // Confirming must store level 4 and navigate to level=4
        const confirmNextBtn = window.document.getElementById('levelTimerConfirmNextBtn');
        assert.ok(confirmNextBtn, 'Confirm Next task button should exist');
        confirmNextBtn.click();

        assert.strictEqual(confirmModal.style.display, 'none', 'Confirm modal should close upon confirming');
        storedLevel = window.sessionStorage.getItem('blocky_level_timer_current_level');
        assert.strictEqual(storedLevel, '4', 'Confirming Next task must store level 4');
        assert.ok(navigatedUrl && navigatedUrl.includes('level=4'), 'Confirming Next task must navigate to level 4');
    });

    it('stops active MoMoT search when Next task button is clicked', () => {
        let stopCalls = 0;
        window.javaBridge = {
            stopMomotRun: () => {
                stopCalls++;
                window.__momotIsRunning = false;
                return Promise.resolve();
            }
        };
        window.__momotIsRunning = true;

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        // Mark level 3 solved so Next task is active
        window.__levelTimerMarkLevelSolved(3);

        const nextBtn = window.document.getElementById('timerNextTaskBtn');
        assert.ok(nextBtn);
        nextBtn.click();

        assert.ok(stopCalls >= 1, 'stopMomotRun should be called when Next task button is clicked');
        assert.strictEqual(window.__momotIsRunning, false, 'window.__momotIsRunning should be cleared to false');
    });

    it('closes dialog and does not navigate when Stay is clicked', () => {
        let navigatedUrl = null;
        window.__levelTimerNavigate = (url) => { navigatedUrl = url; };

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        window.eval(timerScript);

        // Mark level 3 solved
        window.__levelTimerMarkLevelSolved(3);

        const nextBtn = window.document.getElementById('timerNextTaskBtn');
        nextBtn.click();

        const confirmModal = window.document.getElementById('levelTimerConfirmModal');
        assert.ok(confirmModal);
        assert.strictEqual(confirmModal.style.display, 'flex');

        const stayBtn = window.document.getElementById('levelTimerConfirmStayBtn');
        assert.ok(stayBtn, 'Stay button should exist');
        stayBtn.click();

        assert.strictEqual(confirmModal.style.display, 'none', 'Stay must close the dialog');
        const storedLevel = window.sessionStorage.getItem('blocky_level_timer_current_level');
        assert.strictEqual(storedLevel, '3', 'Stored level must remain 3 after Stay');
        assert.strictEqual(navigatedUrl, null, 'Stay must not navigate');
    });

    it('provides Finish task button on level 10 that opens session report', () => {
        const level10Dom = new JSDOM('<!DOCTYPE html><html><body><div id="blockly"></div></body></html>', {
            url: 'http://localhost/?level=10',
            runScripts: 'dangerously'
        });
        const l10Win = level10Dom.window;
        global.window = l10Win;
        global.document = l10Win.document;
        global.sessionStorage = l10Win.sessionStorage;
        global.localStorage = l10Win.localStorage;

        l10Win.sessionStorage.setItem('blocky_level_timer_session_id', 'test_sess_10');
        l10Win.sessionStorage.setItem('blocky_level_timer_user_id', 'bob');
        l10Win.sessionStorage.setItem('blocky_level_timer_current_level', '10');
        l10Win.fetch = async () => ({ ok: true, status: 200, json: async () => ({}) });
        global.fetch = l10Win.fetch;

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        l10Win.eval(timerScript);

        const finBtn = l10Win.document.getElementById('timerFinishSessionBtn');
        assert.ok(finBtn, 'Finish task button should exist on level 10');
        assert.strictEqual(finBtn.textContent, 'Finish task');

        const nextBtn = l10Win.document.getElementById('timerNextTaskBtn');
        assert.strictEqual(nextBtn, null, 'Next task button should not exist on level 10');

        finBtn.click();

        const reportModal = l10Win.document.getElementById('levelTimerReportModal');
        assert.ok(reportModal, 'Session report modal should exist');
        assert.strictEqual(reportModal.style.display, 'flex');
        assert.ok(reportModal.textContent.includes('Session Timing Report'));

        if (l10Win.__levelTimerCleanup) l10Win.__levelTimerCleanup();
        level10Dom.window.close();
    });

    it('redirects URL to stored level if URL level does not match stored level', () => {
        let replacedUrl = null;
        const mismatchDom = new JSDOM('<!DOCTYPE html><html><body></body></html>', {
            url: 'http://localhost/?lang=en&level=7&skin=1',
            runScripts: 'dangerously'
        });
        const mmWin = mismatchDom.window;
        global.window = mmWin;
        global.document = mmWin.document;
        global.sessionStorage = mmWin.sessionStorage;
        global.localStorage = mmWin.localStorage;

        mmWin.sessionStorage.setItem('blocky_level_timer_session_id', 'test_sess');
        mmWin.sessionStorage.setItem('blocky_level_timer_current_level', '2');
        mmWin.__levelTimerNavigate = (url) => { replacedUrl = url; };

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        mmWin.eval(timerScript);

        assert.ok(replacedUrl, 'Should have called navigation redirect');
        assert.ok(replacedUrl.includes('level=2'), 'Should redirect to stored level 2');
        assert.ok(replacedUrl.includes('lang=en'), 'Should preserve lang=en');
        assert.ok(replacedUrl.includes('skin=1'), 'Should preserve skin=1');

        if (mmWin.__levelTimerCleanup) mmWin.__levelTimerCleanup();
        mismatchDom.window.close();
    });

    it('fresh session without stored level defaults to level 1 and redirects', () => {
        let replacedUrl = null;
        const freshDom = new JSDOM('<!DOCTYPE html><html><body></body></html>', {
            url: 'http://localhost/?level=8',
            runScripts: 'dangerously'
        });
        const freshWin = freshDom.window;
        global.window = freshWin;
        global.document = freshWin.document;
        global.sessionStorage = freshWin.sessionStorage;
        global.localStorage = freshWin.localStorage;

        freshWin.__levelTimerNavigate = (url) => { replacedUrl = url; };

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        freshWin.eval(timerScript);

        assert.strictEqual(freshWin.sessionStorage.getItem('blocky_level_timer_current_level'), '1', 'Should set stored level to 1');
        assert.ok(replacedUrl, 'Should have called navigation redirect');
        assert.ok(replacedUrl.includes('level=1'), 'Should redirect to level 1');

        if (freshWin.__levelTimerCleanup) freshWin.__levelTimerCleanup();
        freshDom.window.close();
    });

    it('marks all levels completed when level 10 is solved, displaying completion modal and unlocking free navigation', () => {
        const dom10 = new JSDOM('<!DOCTYPE html><html><body><div id="blockly"></div></body></html>', {
            url: 'http://localhost/?level=10',
            runScripts: 'dangerously'
        });
        const win10 = dom10.window;
        global.window = win10;
        global.document = win10.document;
        global.sessionStorage = win10.sessionStorage;
        global.localStorage = win10.localStorage;

        win10.sessionStorage.setItem('blocky_level_timer_session_id', 'test_sess_10_all');
        win10.sessionStorage.setItem('blocky_level_timer_user_id', 'charlie');
        win10.sessionStorage.setItem('blocky_level_timer_current_level', '10');

        const timerScript = fs.readFileSync(path.join(__dirname, '../common/levelTimer.js'), 'utf8');
        win10.eval(timerScript);

        // Solve level 10
        win10.__levelTimerMarkLevelSolved(10);

        assert.strictEqual(win10.sessionStorage.getItem('blocky_all_levels_completed'), 'true');
        assert.strictEqual(win10.__levelTimerIsAllLevelsCompleted(), true);

        const modal = win10.document.getElementById('allLevelsCompletedModal');
        assert.ok(modal, 'Completion modal should exist');
        assert.strictEqual(modal.style.display, 'flex');

        if (win10.__levelTimerCleanup) win10.__levelTimerCleanup();
        dom10.window.close();
    });
});
