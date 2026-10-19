const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert');
const { JSDOM } = require('jsdom');
const path = require('node:path');
const fs = require('node:fs');

describe('blockyUIOverlay.js Test Suite', () => {
    let dom;
    let window;

    beforeEach(() => {
        dom = new JSDOM(`<!DOCTYPE html>
<html>
<body>
  <div id="blockly">
    <button id="runButton">Run Program</button>
  </div>
  <svg id="svgMaze"></svg>
</body>
</html>`, {
            url: 'http://localhost/',
            runScripts: 'dangerously'
        });
        window = dom.window;
        global.window = window;
        global.document = window.document;

        // Mock window.X grid
        window.X = [[2, 1, 3]];
        window.Q = 0; window.S = 0; window.T = 1;

        const overlayScript = fs.readFileSync(path.join(__dirname, '../common/blockyUIOverlay.js'), 'utf8');
        window.eval(overlayScript);
    });

    afterEach(() => {
        if (dom && dom.window) {
            dom.window.close();
        }
    });

    it('injects overlays and debugger buttons into DOM', async () => {
        await new Promise(r => setTimeout(r, 150));
        const loadingOverlay = window.document.getElementById('__lvlLoadingOverlay');
        assert.ok(loadingOverlay, '#__lvlLoadingOverlay should exist');

        const execLogPanel = window.document.getElementById('__execLogPanel');
        assert.ok(execLogPanel, '#__execLogPanel should exist');

        const momotPanel = window.document.getElementById('__momotPanel');
        assert.ok(momotPanel, '#__momotPanel should exist');

        const pauseBtn = window.document.getElementById('debugPauseResumeButton');
        const stepBtn = window.document.getElementById('debugStepButton');
        const stopBtn = window.document.getElementById('debugStopButton');
        const skipBtn = window.document.getElementById('debugSkipEndButton');
        const dmBtn = window.document.getElementById('directManipulationButton');

        assert.ok(pauseBtn, '#debugPauseResumeButton should exist');
        assert.ok(stepBtn, '#debugStepButton should exist');
        assert.ok(stopBtn, '#debugStopButton should exist');
        assert.ok(skipBtn, '#debugSkipEndButton should exist');
        assert.ok(dmBtn, '#directManipulationButton should exist');
    });

    it('execution log append and clear works properly', async () => {
        await new Promise(r => setTimeout(r, 150));
        assert.ok(typeof window.__execLogAppend === 'function');
        window.__execLogAppend(['Line 1', 'Line 2']);

        const body = window.document.getElementById('__execLogBody');
        assert.ok(body.textContent.includes('Line 1'));
        assert.ok(body.textContent.includes('Line 2'));

        window.__execLogClear();
        assert.strictEqual(body.textContent, '');
    });

    it('handles panel drag and resize mousedown events', async () => {
        await new Promise(r => setTimeout(r, 150));
        const header = window.document.getElementById('__execLogHeader');
        assert.ok(header);

        const mouseDownEv = new window.MouseEvent('mousedown', { clientX: 100, clientY: 100, button: 0 });
        header.dispatchEvent(mouseDownEv);

        const mouseMoveEv = new window.MouseEvent('mousemove', { clientX: 150, clientY: 150 });
        window.document.dispatchEvent(mouseMoveEv);

        const mouseUpEv = new window.MouseEvent('mouseup', {});
        window.document.dispatchEvent(mouseUpEv);
    });

    it('loads solution directly on single click and does not require double click', async () => {
        await new Promise(r => setTimeout(r, 150));
        let loadedPath = null;
        window.javaBridge = {
            loadMomotSolution: (path) => {
                loadedPath = path;
            },
            getSolutionPath: () => '[[0,0],[0,1]]'
        };

        assert.ok(typeof window.__momotRenderSolutions === 'function');
        window.__momotRenderSolutions([
            { modelPath: 'models/sol_test.xmi', objectiveLine: '-1.0 4.0' }
        ]);

        const list = window.document.getElementById('__momotList');
        assert.ok(list);
        const tr = list.querySelector('tbody tr');
        assert.ok(tr, 'Solution row should be rendered');

        tr.click();
        assert.strictEqual(loadedPath, 'models/sol_test.xmi', 'Single click must immediately call loadMomotSolution');
    });

    it('collapses MoMoT parameters behind gear button on header', async () => {
        await new Promise(r => setTimeout(r, 150));
        const settings = window.document.getElementById('__momotSettings');
        assert.ok(settings, '#__momotSettings should exist');
        assert.strictEqual(settings.style.display, 'none', 'Parameters row should be hidden on load');

        const runBtn = window.document.getElementById('__momotRunBtn');
        const stopBtn = window.document.getElementById('__momotStopBtn');
        const refreshBtn = window.document.getElementById('__momotRefreshBtn');
        const clearBtn = window.document.getElementById('__momotClearOverlayBtn');
        const gearBtn = window.document.getElementById('__momotGearBtn');
        const closeBtn = window.document.getElementById('__momotCloseBtn');

        assert.ok(runBtn, '#__momotRunBtn should exist');
        assert.ok(stopBtn, '#__momotStopBtn should exist');
        assert.strictEqual(refreshBtn, null, 'Refresh button was removed (the table refreshes itself)');
        assert.ok(clearBtn, '#__momotClearOverlayBtn (Clear path) should exist');
        assert.ok(gearBtn, '#__momotGearBtn should exist');
        assert.ok(closeBtn, '#__momotCloseBtn should exist');

        assert.strictEqual(runBtn.style.display, 'none', 'Run button should be hidden (search starts from the Direct Manipulation marker)');
        assert.notStrictEqual(stopBtn.style.display, 'none', 'Stop button should be visible');

        // Inputs should be in the DOM with defaults
        const inpSeed = window.document.getElementById('__momotInpSeed');
        const inpPop = window.document.getElementById('__momotInpPop');
        const inpIter = window.document.getElementById('__momotInpIter');
        const inpRuns = window.document.getElementById('__momotInpRuns');
        const inpSolLen = window.document.getElementById('__momotInpSolLen');

        assert.ok(inpSeed, 'inpSeed should exist in DOM');
        assert.strictEqual(inpSeed.value, '0');
        assert.ok(inpPop, 'inpPop should exist in DOM');
        assert.strictEqual(inpPop.value, '50');
        assert.ok(inpIter, 'inpIter should exist in DOM');
        assert.strictEqual(inpIter.value, '40');
        assert.ok(inpRuns, 'inpRuns should exist in DOM');
        assert.strictEqual(inpRuns.value, '10');
        assert.ok(inpSolLen, 'inpSolLen should exist in DOM');
        assert.strictEqual(inpSolLen.value, '10');

        // Clicking gear shows inputs
        gearBtn.click();
        assert.strictEqual(settings.style.display, 'flex', 'Gear click should show parameters row');

        // Clicking gear again collapses inputs
        gearBtn.click();
        assert.strictEqual(settings.style.display, 'none', 'Second gear click should collapse parameters row');
    });

    it('hides log by default, toggles it via header Log button, and omits Load button and Model column', async () => {
        await new Promise(r => setTimeout(r, 150));
        const loadBtn = window.document.getElementById('__momotLoadBtn');
        const actions = window.document.getElementById('__momotActions');
        assert.strictEqual(loadBtn, null, '#__momotLoadBtn should be removed');
        assert.strictEqual(actions, null, '#__momotActions should be removed');

        const log = window.document.getElementById('__momotLog');
        assert.ok(log, '#__momotLog should exist');
        assert.strictEqual(log.style.display, 'none', 'Log should start hidden');

        const logToggleBtn = window.document.getElementById('__momotLogToggleBtn');
        assert.ok(logToggleBtn, '#__momotLogToggleBtn should exist in header');
        assert.strictEqual(logToggleBtn.textContent, 'Log');

        logToggleBtn.click();
        assert.strictEqual(log.style.display, 'block', 'Clicking Log button should show log');
        assert.strictEqual(logToggleBtn.textContent, 'Hide Log');

        logToggleBtn.click();
        assert.strictEqual(log.style.display, 'none', 'Clicking again should hide log');
        assert.strictEqual(logToggleBtn.textContent, 'Log');

        const status = window.document.getElementById('__momotStatus');
        assert.strictEqual(status.textContent, 'Place the pegman to start search.');

        // Render solution and verify Model column does not exist
        window.__momotRenderSolutions([
            { modelPath: 'models/sol_test.xmi', objectiveLine: '-1.0 4.0 5.0 0.0 3.0' }
        ]);
        const list = window.document.getElementById('__momotList');
        const ths = Array.from(list.querySelectorAll('thead th')).map(th => th.textContent.trim());
        assert.ok(!ths.some(t => t.startsWith('Model')), 'Table headers should not contain Model column');

        const tds = list.querySelectorAll('tbody tr td');
        assert.strictEqual(tds.length, 5, 'Should have exactly 5 objective columns and no Model column');
    });

    it('renders solutions from object contract { solutions: [...] } and updates progress properly on done', async () => {
        await new Promise(r => setTimeout(r, 150));
        const list = window.document.getElementById('__momotList');
        const status = window.document.getElementById('__momotStatus');

        // Test object wrapper format (returned by MomotUiCoordinator.listMomotSolutions)
        window.__momotRenderSolutions({
            firstGoalTimeMs: 1500,
            solutions: [
                { modelPath: 'models/sol_obj.xmi', objectiveLine: '-1.0 2.0 3.0 0.0 2.0' },
                { modelPath: 'models/sol_obj2.xmi', objectiveLine: '0.0 1.0 5.0 1.0 4.0' }
            ]
        });

        assert.strictEqual(list.style.display, 'block', 'List should be displayed for object format');
        const rows = list.querySelectorAll('tbody tr');
        assert.strictEqual(rows.length, 2, 'Should render 2 solution rows');
        assert.ok(status.textContent.includes('2 candidate(s)'), 'Status should report 2 candidates');

        // Test progress lifecycle and progress done
        window.__momotProgressStart(10, 40);
        window.__momotSetProgress(2, 10, 5, 40, 15.0);
        const progText = window.document.getElementById('__momotProgressText');
        assert.ok(progText.textContent.includes('Run 2/10'), 'Should reflect in-flight progress');

        window.__momotProgressDone();
        assert.ok(progText.textContent.includes('Run 10/10'), 'Progress done should update to final run 10/10');
        assert.ok(progText.textContent.includes('generation 40/40'), 'Progress done should update to final generation 40/40');
        assert.ok(progText.textContent.includes('100%'), 'Progress done should update to 100%');
        assert.ok(progText.textContent.includes('| ended'), 'Progress done should append | ended');
    });

    it('supports table flex layout and responsive sizing', async () => {
        await new Promise(r => setTimeout(r, 150));
        const panel = window.document.getElementById('__momotPanel');
        const list = window.document.getElementById('__momotList');
        const body = window.document.getElementById('__momotBody');

        assert.strictEqual(panel.style.flexDirection, 'column');
        assert.strictEqual(body.style.display, 'flex');
        assert.strictEqual(body.style.flexDirection, 'column');
        assert.strictEqual(list.style.flex, '1 1 auto');
    });

    it('applies Power and Memory styling and correct column ordering in table headers and cells', async () => {
        await new Promise(r => setTimeout(r, 150));
        window.__momotRenderSolutions([
            { modelPath: 'models/sol_1.xmi', objectiveLine: '-1.0 3.0 12.0 0.0 45.0' }
        ]);

        const list = window.document.getElementById('__momotList');
        const ths = list.querySelectorAll('thead th');
        assert.strictEqual(ths.length, 5);

        // Header 2: Power (kW) with amber color
        assert.ok(ths[2].textContent.includes('Power (kW)'));
        assert.strictEqual(ths[2].style.color, 'rgb(245, 158, 11)'); // #f59e0b

        // Header 4: Memory (kB) with blue color
        assert.ok(ths[4].textContent.includes('Memory (kB)'));
        assert.strictEqual(ths[4].style.color, 'rgb(56, 189, 248)'); // #38bdf8

        // Cell 2 and 4 styling
        const tds = list.querySelectorAll('tbody tr td');
        assert.strictEqual(tds[2].style.color, 'rgb(245, 158, 11)');
        assert.strictEqual(tds[4].style.color, 'rgb(56, 189, 248)');
    });

    it('supports multi-column sort with shift-click', async () => {
        await new Promise(r => setTimeout(r, 150));
        window.__momotRenderSolutions([
            { modelPath: 'models/sol_a.xmi', objectiveLine: '-1.0 5.0 10.0 0.0 20.0' },
            { modelPath: 'models/sol_b.xmi', objectiveLine: '-1.0 2.0 15.0 0.0 10.0' },
            { modelPath: 'models/sol_c.xmi', objectiveLine: '0.0 1.0 5.0 1.0 5.0' }
        ]);

        const list = window.document.getElementById('__momotList');
        const ths = list.querySelectorAll('thead th');

        // Click Col 1 (Edits) normally to set primary sort
        ths[1].dispatchEvent(new window.MouseEvent('click', { bubbles: true, shiftKey: false }));

        // Shift-click Col 2 (Power) to add secondary sort
        const thsAfterFirstClick = list.querySelectorAll('thead th');
        thsAfterFirstClick[2].dispatchEvent(new window.MouseEvent('click', { bubbles: true, shiftKey: true }));

        assert.strictEqual(window.__momotSortKeys.length, 2);
        assert.strictEqual(window.__momotSortKeys[0].col, 1);
        assert.strictEqual(window.__momotSortKeys[1].col, 2);

        // Header should show multi-sort indicators (e.g. 1▲ and 2▲)
        const updatedThs = list.querySelectorAll('thead th');
        assert.ok(updatedThs[1].textContent.includes('1▲') || updatedThs[1].textContent.includes('1'));
        assert.ok(updatedThs[2].textContent.includes('2▲') || updatedThs[2].textContent.includes('2'));

        // Row order: sol_c (Edits 1), then sol_b (Edits 2), then sol_a (Edits 5)
        const rows = list.querySelectorAll('tbody tr');
        assert.ok(rows[0].textContent.includes('1'));
        assert.ok(rows[1].textContent.includes('2'));
        assert.ok(rows[2].textContent.includes('5'));
    });

    it('filters solutions by goal status and numeric maximums combined', async () => {
        await new Promise(r => setTimeout(r, 150));
        window.__momotRenderSolutions([
            { modelPath: 'models/sol_1.xmi', objectiveLine: '-1.0 2.0 10.0 0.0 20.0' },
            { modelPath: 'models/sol_2.xmi', objectiveLine: '-1.0 4.0 30.0 0.0 25.0' },
            { modelPath: 'models/sol_3.xmi', objectiveLine: '0.0 1.0 5.0 1.0 10.0' }
        ]);

        const list = window.document.getElementById('__momotList');
        assert.strictEqual(list.querySelectorAll('tbody tr').length, 3);

        const chkGoal = window.document.getElementById('__momotFilterGoal');
        const inpPower = window.document.getElementById('__momotFilterMaxPower');

        // Filter: Hide misses
        chkGoal.checked = true;
        chkGoal.dispatchEvent(new window.Event('change'));
        assert.strictEqual(list.querySelectorAll('tbody tr').length, 2);

        // Also filter: Max Power <= 15 (should only leave sol_1)
        inpPower.value = '15';
        inpPower.dispatchEvent(new window.Event('input'));
        assert.strictEqual(list.querySelectorAll('tbody tr').length, 1);
        assert.ok(list.querySelector('tbody tr').textContent.includes('10'));

        // Reset filters
        const resetBtn = window.document.getElementById('__momotFilterResetBtn');
        resetBtn.click();
        assert.strictEqual(list.querySelectorAll('tbody tr').length, 3);
    });

    it('calculates and displays red/green deltas relative to selected baseline row', async () => {
        await new Promise(r => setTimeout(r, 150));
        window.javaBridge = {
            loadMomotSolution: () => {},
            getSolutionPath: () => '[]'
        };

        window.__momotRenderSolutions([
            { modelPath: 'models/sol_base.xmi', objectiveLine: '-1.0 5.0 20.0 0.0 30.0' },
            { modelPath: 'models/sol_other.xmi', objectiveLine: '-1.0 7.0 15.0 0.0 30.0' }
        ]);

        const list = window.document.getElementById('__momotList');
        const rows = list.querySelectorAll('tbody tr');

        // Select the first row as baseline
        rows[0].click();

        // The second row should show relative deltas:
        // Edits: 7 vs 5 -> (+2) worse in red
        // Power: 15 vs 20 -> (-5) better in green
        // Memory: 30 vs 30 -> (0) neutral in gray
        const updatedRows = list.querySelectorAll('tbody tr');
        const otherRowTds = updatedRows[1].querySelectorAll('td');

        assert.ok(otherRowTds[1].innerHTML.includes('(+2)'), 'Edits should show (+2) delta');
        assert.ok(otherRowTds[2].innerHTML.includes('(-5)'), 'Power should show (-5) delta');
        assert.ok(otherRowTds[4].innerHTML.includes('(0)'), 'Memory should show (0) delta');
    });

    it('renders interactive 2D Pareto chart and loads candidate on point click', async () => {
        await new Promise(r => setTimeout(r, 150));
        let loaded = null;
        window.javaBridge = {
            loadMomotSolution: (p) => { loaded = p; },
            getSolutionPath: () => '[]'
        };

        const chartToggleBtn = window.document.getElementById('__momotChartToggleBtn');
        assert.ok(chartToggleBtn);
        const chartContainer = window.document.getElementById('__momotChartContainer');
        assert.strictEqual(chartContainer.style.display, 'none');

        // Toggle chart on
        chartToggleBtn.click();
        assert.strictEqual(chartContainer.style.display, 'flex');

        window.__momotRenderSolutions([
            { modelPath: 'models/sol_p1.xmi', objectiveLine: '-1.0 2.0 10.0 0.0 20.0' },
            { modelPath: 'models/sol_p2.xmi', objectiveLine: '-1.0 4.0 20.0 0.0 10.0' }
        ]);

        const svg = window.document.getElementById('__momotChartSvg');
        const circles = svg.querySelectorAll('circle');
        assert.strictEqual(circles.length, 2, 'Should render 2 circles for candidates');

        // Click first circle
        circles[0].dispatchEvent(new window.MouseEvent('click', { bubbles: true }));
        assert.strictEqual(loaded, 'models/sol_p1.xmi', 'Clicking chart circle should load the solution');
    });

    it('Skip End positions pegman on (0,0) without reset and clears animation timeouts', async () => {
        await new Promise(r => setTimeout(r, 150));
        let cleared = false;
        const fakeTimer = setTimeout(() => { cleared = false; }, 10000);
        window.Y = [fakeTimer];

        // Position starts at (0, 0) with heading 1
        window.Q = 0;
        window.S = 0;
        window.T = 1;

        window.javaBridge = {
            debugSkipToEnd: () => JSON.stringify({
                q: 0,
                s: 0,
                t: 2,
                result: 'GOAL'
            })
        };

        const skipBtn = window.document.getElementById('debugSkipEndButton');
        skipBtn.click();

        // Coordinates should be (0, 0)
        assert.strictEqual(window.Q, 0);
        assert.strictEqual(window.S, 0);
        assert.strictEqual(window.T, 2);

        // window.Y timeouts should be cleared
        assert.strictEqual(window.Y.length, 0);

        // When cell stays the same and only heading changes, turn animation timers must not be scheduled
        assert.strictEqual((window.__dbgTurnTimers || []).length, 0);
    });

    it('preserves existing solutions list when search is active', async () => {
        await new Promise(r => setTimeout(r, 150));
        window.__momotRenderSolutions([
            { modelPath: 'models/sol_keep.xmi', objectiveLine: '-1.0 2.0 10.0 0.0 20.0' }
        ]);

        const list = window.document.getElementById('__momotList');
        assert.strictEqual(list.querySelectorAll('tbody tr').length, 1);

        // Mark search as active
        window.__momotIsRunning = true;

        // Render empty solutions
        window.__momotRenderSolutions([]);

        // Previous solution should be kept
        assert.strictEqual(list.querySelectorAll('tbody tr').length, 1);
        assert.ok(list.querySelector('tbody tr').textContent.includes('10'));

        window.__momotIsRunning = false;
    });
});
