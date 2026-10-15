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

    it('omits stored Yes / -1 / -1 dash rows, keeps later Yes / 2 / 2 rows for the same program, renumbers them, and writes cleaned list back', () => {
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
        assert.strictEqual(runs.length, 3, 'Dash rows with sentinel -1 memory/power must be dropped');

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

        assert.strictEqual(runs[2].id, 3);
        assert.strictEqual(runs[2].goalReached, true);
        assert.strictEqual(runs[2].memory, 2);
        assert.strictEqual(runs[2].power, 2);
        assert.strictEqual(runs[2].xml, programXml);

        // Check that cleaned list was written back to sessionStorage
        const saved = JSON.parse(window.sessionStorage.getItem('blocky_scoreboard_lvl_1'));
        assert.strictEqual(saved.length, 3);
        assert.strictEqual(saved[0].id, 1);
        assert.strictEqual(saved[0].memory, 2);
        assert.strictEqual(saved[1].id, 2);
        assert.strictEqual(saved[2].id, 3);

        // Check that the toggle button badge is updated to 3
        const btn = window.document.getElementById('scoreboardToggleBtn');
        btn.click();
        assert.strictEqual(btn.textContent, 'Scoreboard (3)');

        // Check rendered rows in table body: no dash rows displayed
        const tableBody = window.document.getElementById('scoreboardTableBody');
        assert.ok(tableBody);
        const rows = tableBody.querySelectorAll('tr');
        assert.strictEqual(rows.length, 3);
        assert.ok(!tableBody.textContent.includes('- kB'));
        assert.ok(!tableBody.textContent.includes('- kW'));
        assert.ok(tableBody.textContent.includes('2 kB'));
        assert.ok(tableBody.textContent.includes('2 kW'));
    });
});
