const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert');
const { JSDOM } = require('jsdom');
const path = require('node:path');
const fs = require('node:fs');

describe('immediateFeedbackOverlay.test.js Test Suite', () => {
    let dom;
    let window;

    beforeEach(() => {
        dom = new JSDOM(`<!DOCTYPE html>
<html>
<body>
  <div id="blockly">
    <button id="runButton">Run</button>
  </div>
  <svg id="svgMaze">
    <image id="pegman"></image>
  </svg>
</body>
</html>`, {
            url: 'http://localhost/',
            runScripts: 'dangerously'
        });
        window = dom.window;
        global.window = window;
        global.document = window.document;

        const overlayScript = fs.readFileSync(path.join(__dirname, '../common/blockyUIOverlay.js'), 'utf8');
        window.eval(overlayScript);
    });

    afterEach(() => {
        if (dom && dom.window) {
            dom.window.close();
        }
    });

    it('renders predicted path overlay <g id="ifNew"> on svgMaze', async () => {
        await new Promise(r => setTimeout(r, 150));
        window.__injectNewPath = [[0, 0], [1, 0], [1, 1]];
        assert.ok(typeof window.__ifRender === 'function');

        window.__ifRender();

        const svg = window.document.getElementById('svgMaze');
        const gIfNew = window.document.getElementById('ifNew');

        assert.ok(gIfNew, '<g id="ifNew"> should exist in SVG');
        const polyline = gIfNew.querySelector('polyline');
        assert.ok(polyline, '<polyline> element should be created');
        assert.strictEqual(polyline.getAttribute('stroke'), '#ff8c1a');

        const circles = gIfNew.querySelectorAll('circle');
        assert.strictEqual(circles.length, 3, 'Should render 3 circles for 3 path points');
        assert.strictEqual(polyline.getAttribute('points'), '25,25 75,25 75,75', 'Should draw on cell centers (50*coord + 25)');
    });

    it('rendering the same path twice keeps the existing polyline node in place', async () => {
        await new Promise(r => setTimeout(r, 150));
        window.__injectNewPath = [[0, 0], [1, 0], [1, 1]];
        window.__ifRender();

        const gIfNew1 = window.document.getElementById('ifNew');
        const poly1 = gIfNew1.querySelector('polyline');
        assert.ok(poly1, 'Initial polyline must exist');

        // Re-render with identical path points
        window.__ifRender();

        const gIfNew2 = window.document.getElementById('ifNew');
        const poly2 = gIfNew2.querySelector('polyline');
        assert.strictEqual(gIfNew1, gIfNew2, '<g id="ifNew"> element should remain unchanged');
        assert.strictEqual(poly1, poly2, 'Polyline node reference should remain identical without being recreated');

        // Change path points - should rebuild polyline
        window.__injectNewPath = [[0, 0], [0, 1]];
        window.__ifRender();

        const gIfNew3 = window.document.getElementById('ifNew');
        const poly3 = gIfNew3.querySelector('polyline');
        assert.notStrictEqual(poly1, poly3, 'Polyline should be recreated when path points change');
        assert.strictEqual(poly3.getAttribute('points'), '25,25 25,75');
    });

    it('renders DM comparison path overlay <g id="dmSol"> when DM mode is enabled', async () => {
        await new Promise(r => setTimeout(r, 150));
        window.__injectDmEnabled = true;
        window.__injectDmSolutionPath = [[0, 0], [0, 1], [0, 2]];

        window.__ifRender();

        const gDmSol = window.document.getElementById('dmSol');
        assert.ok(gDmSol, '<g id="dmSol"> should exist in SVG');

        const polyline = gDmSol.querySelector('polyline');
        assert.ok(polyline);
        assert.strictEqual(polyline.getAttribute('stroke'), '#a020f0');
    });
});
