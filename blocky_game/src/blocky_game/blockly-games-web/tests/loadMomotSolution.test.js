const { describe, it } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const { JSDOM } = require('jsdom');

describe('loadMomotSolution.js Test Suite', () => {
    const scriptPath = path.join(__dirname, '../../resources/scripts/loadMomotSolution.js');
    const loadScriptContent = fs.readFileSync(scriptPath, 'utf8');

    function createEnvironment() {
        const dom = new JSDOM('<!DOCTYPE html><html><body><div id="svgMaze"></div></body></html>', {
            runScripts: 'dangerously'
        });
        const window = dom.window;
        global.window = window;
        global.document = window.document;
        global.DOMParser = window.DOMParser;
        global.XMLSerializer = window.XMLSerializer;
        return window;
    }

    it('does not call injectComplete when superseded by a newer load request', async () => {
        const win = createEnvironment();

        const injectCompleteCalls = [];
        const injectCompleteWithGenCalls = [];

        win.javaBridge = {
            logJS: () => {},
            injectComplete: () => { injectCompleteCalls.push(true); },
            injectCompleteWithGen: (gen) => { injectCompleteWithGenCalls.push(gen); }
        };

        // First load request: loadId = 1
        win.__momotLoadId = 1;
        win.__momotXml = '<xml><block type="maze_moveForward"></block></xml>';
        win.__momotLastLoadedModelName = 'sol1.xmi';

        // Do NOT define workspace yet so it polls
        win.eval(loadScriptContent);

        // Before workspace appears, a second load request arrives: loadId = 2
        win.__momotLoadId = 2;
        win.__momotLastLoadedModelName = 'sol2.xmi';

        // Provide workspace now
        let cleared = false;
        win.Blockly = {
            getMainWorkspace: () => ({
                clear: () => { cleared = true; }
            }),
            Xml: {
                workspaceToDom: () => ({}),
                domToText: () => '',
                domToWorkspace: () => {}
            }
        };

        // Wait for polling cycle
        await new Promise(r => setTimeout(r, 450));

        // Attempt 1 should have been superseded and dropped WITHOUT calling injectComplete or injectCompleteWithGen(1)
        assert.strictEqual(injectCompleteCalls.length, 0, 'Superseded load 1 must NOT call injectComplete');
        assert.ok(!injectCompleteWithGenCalls.includes(1), 'Superseded load 1 must NOT complete with gen 1');
    });

    it('detects unchanged blocks on real maze using h.K.qn and h.K.Mc', async () => {
        const win = createEnvironment();

        let statusMsg = '';
        win.__momotSetStatus = (msg) => { statusMsg = msg; };

        win.javaBridge = {
            logJS: () => {},
            injectComplete: () => {},
            injectCompleteWithGen: () => {}
        };

        const existingXml = '<xml><block type="maze_moveForward" id="block1" x="20" y="30"></block></xml>';
        const solutionXml = '<xml><block type="maze_moveForward" id="block99" x="100" y="200"></block></xml>';

        win.__momotLoadId = 10;
        win.__momotXml = solutionXml;
        win.__momotLastLoadedModelName = 'level9_sol.xmi';

        // Mock compressed.js environment: h.K.qn / h.K.Mc instead of Blockly.Xml.workspaceToDom
        win.h = {
            K: {
                qn: () => ({}),
                Mc: () => existingXml,
                domToWorkspace: () => {}
            }
        };

        win.BlocklyInterface = {
            getWorkspace: () => ({
                clear: () => {}
            })
        };

        win.eval(loadScriptContent);

        await new Promise(r => setTimeout(r, 250));

        assert.strictEqual(statusMsg, 'Loaded level9_sol.xmi (blocks unchanged)',
            'Status must report (blocks unchanged) when h.K.qn / h.K.Mc returns structurally identical XML');
    });

    it('reports loaded without blocks unchanged when blocks differ', async () => {
        const win = createEnvironment();

        let statusMsg = '';
        win.__momotSetStatus = (msg) => { statusMsg = msg; };

        win.javaBridge = {
            logJS: () => {},
            injectComplete: () => {},
            injectCompleteWithGen: () => {}
        };

        const existingXml = '<xml><block type="maze_moveForward"></block></xml>';
        const solutionXml = '<xml><block type="maze_turn"><field name="DIR">turnLeft</field></block></xml>';

        win.__momotLoadId = 11;
        win.__momotXml = solutionXml;
        win.__momotLastLoadedModelName = 'diff_sol.xmi';

        win.h = {
            K: {
                qn: () => ({}),
                Mc: () => existingXml,
                domToWorkspace: () => {}
            }
        };

        win.BlocklyInterface = {
            getWorkspace: () => ({
                clear: () => {}
            })
        };

        win.eval(loadScriptContent);

        await new Promise(r => setTimeout(r, 250));

        assert.strictEqual(statusMsg, 'Loaded diff_sol.xmi',
            'Status must not say (blocks unchanged) when program blocks differ');
    });
});
