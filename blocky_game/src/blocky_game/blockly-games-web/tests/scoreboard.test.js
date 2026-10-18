const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert');
const { JSDOM } = require('jsdom');
const path = require('node:path');
const fs = require('node:fs');

describe('Scoreboard Test Suite', () => {
    let dom;
    let window;

    beforeEach(() => {
        dom = new JSDOM('<!DOCTYPE html><html><body><button id="runButton">Run Program</button><div id="blockly"></div><svg id="svgMaze"></svg></body></html>', {
            url: 'http://localhost/?level=1',
            runScripts: 'dangerously'
        });
        window = dom.window;
        global.window = window;
        global.document = window.document;
        global.sessionStorage = window.sessionStorage;
        global.localStorage = window.localStorage;

        const scoreboardScript = fs.readFileSync(path.join(__dirname, '../common/scoreboard.js'), 'utf8');
        window.eval(scoreboardScript);
    });

    afterEach(() => {
        if (dom && dom.window) {
            dom.window.close();
        }
    });

    it('exports BlockyScoreboard API to window and initializes toggle button', () => {
        assert.ok(window.BlockyScoreboard, 'BlockyScoreboard should be defined');
        assert.ok(typeof window.__scoreboardAddRun === 'function', '__scoreboardAddRun should exist');
        assert.ok(typeof window.__scoreboardGetRuns === 'function', '__scoreboardGetRuns should exist');

        const btn = window.document.getElementById('scoreboardToggleBtn');
        assert.ok(btn, 'Scoreboard toggle button should be created in DOM');
        assert.strictEqual(btn.textContent, 'Scoreboard (0)');
    });

    it('adds run records with the 3 objectives and updates sessionStorage and badge count', () => {
        window.__scoreboardAddRun({
            goalReached: true,
            memory: 6,
            power: 14,
            xml: '<xml><block type="maze_moveForward"></block></xml>'
        });

        const runs = window.__scoreboardGetRuns(1);
        assert.strictEqual(runs.length, 1);
        assert.strictEqual(runs[0].id, 1);
        assert.strictEqual(runs[0].goalReached, true);
        assert.strictEqual(runs[0].memory, 6);
        assert.strictEqual(runs[0].power, 14);
        assert.strictEqual(runs[0].xml, '<xml><block type="maze_moveForward"></block></xml>');

        const btn = window.document.getElementById('scoreboardToggleBtn');
        assert.strictEqual(btn.textContent, 'Scoreboard (1)');

        window.__scoreboardAddRun({
            goalReached: false,
            memory: 8,
            power: 20,
            xml: '<xml><block type="maze_turn"></block></xml>'
        });

        const updatedRuns = window.__scoreboardGetRuns(1);
        assert.strictEqual(updatedRuns.length, 2);
        assert.strictEqual(updatedRuns[1].id, 2);
        assert.strictEqual(updatedRuns[1].goalReached, false);
        assert.strictEqual(btn.textContent, 'Scoreboard (2)');
    });

    it('rejects a run payload without valid non-negative memory and power fields', () => {
        const added1 = window.__scoreboardAddRun({
            goalReached: true
        });
        assert.strictEqual(added1, null, 'Payload without memory/power should return null');

        const added2 = window.__scoreboardAddRun({
            goalReached: true,
            memory: -1,
            power: 10
        });
        assert.strictEqual(added2, null, 'Payload with negative memory should return null');

        const runs = window.__scoreboardGetRuns(1);
        assert.strictEqual(runs.length, 0, 'No run should be stored when memory/power are invalid');

        const btn = window.document.getElementById('scoreboardToggleBtn');
        assert.strictEqual(btn.textContent, 'Scoreboard (0)');
    });

    it('loads a previous run restoring XML workspace and syncing with backend model', () => {
        let injectedXml = null;
        let syncedXml = null;
        let consumptionUpdated = null;

        window.applyXmlToWorkspace = (xml) => { injectedXml = xml; };
        window.javaBridge = {
            syncModel: (xml, allowEmpty) => { syncedXml = xml; }
        };
        window.__consumptionUpdate = (mem, pow) => { consumptionUpdated = { mem, pow }; };

        window.__scoreboardAddRun({
            goalReached: true,
            memory: 5,
            power: 12,
            xml: '<xml><block type="maze_moveForward" id="1"></block></xml>'
        });

        const loaded = window.__scoreboardLoadRun(1, 1);
        assert.strictEqual(loaded, true);
        assert.strictEqual(injectedXml, '<xml><block type="maze_moveForward" id="1"></block></xml>');
        assert.strictEqual(syncedXml, '<xml><block type="maze_moveForward" id="1"></block></xml>');
        assert.deepStrictEqual(consumptionUpdated, { mem: 5, pow: 12 });
    });

    it('omits stored Yes / -1 / -1 dash rows, collapses duplicate rows for the same program, renumbers them, and writes cleaned list back', () => {
        const programXml = '<xml><block type="maze_moveForward"></block></xml>';
        const storedRows = [
            { id: 1, goalReached: true, memory: -1, power: -1, xml: programXml },
            { id: 2, goalReached: true, memory: -1, power: -1, xml: programXml },
            { id: 3, goalReached: true, memory: 2, power: 2, xml: programXml },
            { id: 4, goalReached: false, memory: 4, power: 8, xml: '<xml><block type="maze_turn"></block></xml>' },
            { id: 5, goalReached: true, memory: 2, power: 2, xml: programXml }
        ];
        window.sessionStorage.setItem('blocky_scoreboard_lvl_1', JSON.stringify(storedRows));

        const runs = window.__scoreboardGetRuns(1);
        assert.strictEqual(runs.length, 2, 'Dash rows dropped, and duplicate row for programXml collapsed into one');

        // Check that remaining runs are renumbered starting from 1
        assert.strictEqual(runs[0].id, 1);
        assert.strictEqual(runs[0].goalReached, true);
        assert.strictEqual(runs[0].memory, 2);
        assert.strictEqual(runs[0].power, 2);
        assert.strictEqual(runs[0].xml, programXml);

        assert.strictEqual(runs[1].id, 2);
        assert.strictEqual(runs[1].goalReached, false);
        assert.strictEqual(runs[1].memory, 4);
        assert.strictEqual(runs[1].power, 8);

        // Check that cleaned list was written back to sessionStorage
        const saved = JSON.parse(window.sessionStorage.getItem('blocky_scoreboard_lvl_1'));
        assert.strictEqual(saved.length, 2);
        assert.strictEqual(saved[0].id, 1);
        assert.strictEqual(saved[0].memory, 2);
        assert.strictEqual(saved[1].id, 2);

        // Check that the toggle button badge is updated to 2
        const btn = window.document.getElementById('scoreboardToggleBtn');
        btn.click();
        assert.strictEqual(btn.textContent, 'Scoreboard (2)');

        // Check rendered rows in table body: no dash rows displayed
        const tableBody = window.document.getElementById('scoreboardTableBody');
        assert.ok(tableBody);
        const rows = tableBody.querySelectorAll('tr');
        assert.strictEqual(rows.length, 2);
        assert.ok(!tableBody.textContent.includes('- kB'));
        assert.ok(!tableBody.textContent.includes('- kW'));
        assert.ok(tableBody.textContent.includes('2 kB'));
        assert.ok(tableBody.textContent.includes('2 kW'));
    });

    it('leaves runs length at 1 on duplicate add of the same xml and surfaces the already recorded message with run id', () => {
        const runXml = '<xml><block type="maze_moveForward"></block></xml>';
        const first = window.__scoreboardAddRun({
            goalReached: false,
            memory: 6,
            power: 14,
            xml: runXml
        });
        assert.strictEqual(first.id, 1);
        assert.strictEqual(window.__scoreboardGetRuns(1).length, 1);

        const btn = window.document.getElementById('scoreboardToggleBtn');
        assert.strictEqual(btn.textContent, 'Scoreboard (1)');

        // Open panel to verify status message and highlight
        btn.click();
        const panel = window.document.getElementById('__scoreboardPanel');
        assert.strictEqual(panel.style.display, 'flex');

        const second = window.__scoreboardAddRun({
            goalReached: false,
            memory: 6,
            power: 14,
            xml: runXml
        });
        assert.strictEqual(second.id, 1, 'Should return existing matched run');
        assert.strictEqual(window.__scoreboardGetRuns(1).length, 1, 'Length should remain 1');
        assert.strictEqual(btn.textContent, 'Scoreboard (1)');

        // Check toast element
        const toast = window.document.getElementById('scoreboardToast');
        assert.ok(toast, 'Scoreboard toast should be created');
        assert.strictEqual(toast.textContent, 'This program is already recorded as Run #1.');

        // Check panel status message
        const statusMsg = window.document.getElementById('scoreboardStatusMsg');
        assert.ok(statusMsg);
        assert.strictEqual(statusMsg.textContent, 'This program is already recorded as Run #1.');

        // Check that row 1 is highlighted
        const row1 = window.document.querySelector('#scoreboardTableBody tr[data-run-id="1"]');
        assert.ok(row1);
        assert.strictEqual(row1.style.background, 'rgba(9, 105, 218, 0.3)');
    });

    it('treats blocks with different id and x/y as the same model, and a different block type as a new row', () => {
        const run1 = window.__scoreboardAddRun({
            goalReached: false,
            memory: 5,
            power: 10,
            xml: '<xml><block type="maze_moveForward" id="blockA" x="20" y="40"></block></xml>'
        });
        assert.strictEqual(run1.id, 1);
        assert.strictEqual(window.__scoreboardGetRuns(1).length, 1);

        // Add run with different id and coordinates, but same block structure
        const runDuplicate = window.__scoreboardAddRun({
            goalReached: false,
            memory: 7,
            power: 15,
            xml: '<xml><block type="maze_moveForward" id="blockB" x="120" y="240"></block></xml>'
        });
        assert.strictEqual(runDuplicate.id, 1);
        const runsAfterDup = window.__scoreboardGetRuns(1);
        assert.strictEqual(runsAfterDup.length, 1, 'Should still be 1 row');
        assert.strictEqual(runsAfterDup[0].memory, 5, 'Original memory should be kept');
        assert.strictEqual(runsAfterDup[0].power, 10, 'Original power should be kept');

        // Add run with a different block type
        const runDifferent = window.__scoreboardAddRun({
            goalReached: false,
            memory: 4,
            power: 8,
            xml: '<xml><block type="maze_turn"><field name="DIR">turnLeft</field></block></xml>'
        });
        assert.strictEqual(runDifferent.id, 2);
        const runsAfterDiff = window.__scoreboardGetRuns(1);
        assert.strictEqual(runsAfterDiff.length, 2, 'Different block type must create a new row');
        assert.strictEqual(runsAfterDiff[1].id, 2);
    });

    it('promotes goalReached to true on duplicate add that newly reaches the goal without adding a row', () => {
        const progA = '<xml><block type="maze_moveForward"></block></xml>';
        const progB = '<xml><block type="maze_turn"><field name="DIR">turnRight</field></block></xml>';

        // Add run 1 (progA, non-goal)
        window.__scoreboardAddRun({
            goalReached: false,
            memory: 5,
            power: 10,
            xml: progA
        });

        // Add run 2 (progB, non-goal)
        window.__scoreboardAddRun({
            goalReached: false,
            memory: 6,
            power: 12,
            xml: progB
        });

        const initialRuns = window.__scoreboardGetRuns(1);
        assert.strictEqual(initialRuns.length, 2);
        assert.strictEqual(initialRuns[0].goalReached, false);
        assert.strictEqual(initialRuns[1].goalReached, false);

        // Run progA again, this time reaching goal
        const dup = window.__scoreboardAddRun({
            goalReached: true,
            memory: 5,
            power: 10,
            xml: progA
        });
        assert.strictEqual(dup.id, 1);

        const runsAfterGoal = window.__scoreboardGetRuns(1);
        assert.strictEqual(runsAfterGoal.length, 2, 'Duplicate add must not increase runs count');
        assert.strictEqual(runsAfterGoal[0].goalReached, true, 'Run #1 must be promoted to goalReached: true');
        assert.strictEqual(runsAfterGoal[1].goalReached, false, 'Run #2 must remain goalReached: false');

        // Calling markLastRunGoalReached updates the run just touched (Run #1), not Run #2
        window.__scoreboardMarkLastRunGoalReached(1);
        const runsAfterMark = window.__scoreboardGetRuns(1);
        assert.strictEqual(runsAfterMark[0].goalReached, true);
        assert.strictEqual(runsAfterMark[1].goalReached, false, 'Marking goal reached must not target run #2 when run #1 was touched');
    });

    it('falls back to marking the last row when markLastRunGoalReached is called with no preceding add', () => {
        window.__scoreboardAddRun({
            goalReached: false,
            memory: 5,
            power: 10,
            xml: '<xml><block type="maze_moveForward"></block></xml>'
        });
        window.__scoreboardAddRun({
            goalReached: false,
            memory: 6,
            power: 12,
            xml: '<xml><block type="maze_turn"></block></xml>'
        });

        // Level 2 where runs exist from direct storage
        const level2Runs = [
            { id: 1, goalReached: false, memory: 4, power: 8, xml: '<xml><block type="maze_turn"></block></xml>' },
            { id: 2, goalReached: false, memory: 5, power: 10, xml: '<xml><block type="maze_moveForward"></block></xml>' }
        ];
        window.sessionStorage.setItem('blocky_scoreboard_lvl_2', JSON.stringify(level2Runs));

        window.__scoreboardMarkLastRunGoalReached(2);
        const loadedRuns = window.__scoreboardGetRuns(2);
        assert.strictEqual(loadedRuns[0].goalReached, false);
        assert.strictEqual(loadedRuns[1].goalReached, true, 'Fallback should mark the last run goalReached');
    });
});
