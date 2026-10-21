(function() {
  try { window.__blockyRunStarted = false; } catch(e) {}
  var lastXml = '';
  function log(msg) {
    try {
      if (window.javaBridge) window.javaBridge.logJS(msg);
      else if (window.parent && window.parent.javaBridge) window.parent.javaBridge.logJS(msg);
    } catch(e) {}
  }
  window.onerror = function(m, u, l) { log('JS Error: ' + m + ' at ' + u + ':' + l); };
  function getWS() {
    try {
      if (window.BlocklyInterface && window.BlocklyInterface.getWorkspace) return window.BlocklyInterface.getWorkspace();
      if (window.Blockly && window.Blockly.getMainWorkspace) return window.Blockly.getMainWorkspace();
    } catch(e) {}
    return null;
  }
  function getXml(ws) {
    try {
      if (window.h && window.h.K && typeof window.h.K.qn === 'function' && typeof window.h.K.Mc === 'function') {
        return window.h.K.Mc(window.h.K.qn());
      }
      var dom = null;
      if (window.Blockly && window.Blockly.Xml && typeof window.Blockly.Xml.workspaceToDom === 'function') {
        dom = window.Blockly.Xml.workspaceToDom(ws);
      } else if (window.h && window.h.K && typeof window.h.K.lf === 'function') {
        dom = window.h.K.lf(ws);
      } else if (window.h && window.h.K && typeof window.h.K.workspaceToDom === 'function') {
        dom = window.h.K.workspaceToDom(ws);
      }
      if (!dom) { log('Blockly.Xml not found'); return null; }
      return new XMLSerializer().serializeToString(dom);
    } catch(e) { log('XML error: ' + e); return null; }
  }
  function sync(ws) {
    var xml = getXml(ws);
    try { if (xml && xml.indexOf('<block') < 0) xml = null; } catch(e0) {}
    if (xml && xml !== lastXml) {
      lastXml = xml;
      log('Syncing XML: ' + xml.substring(0, 120));
      var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
      if (bridge) bridge.syncModel(xml);
    }
  }
  var iters = 0;
  var interval = setInterval(function() {
    var ws = getWS();
    if (ws) {
      log('Workspace active. Attaching listeners.');
      try { if (window.__lvlLoadingHide) window.__lvlLoadingHide(); } catch(eL) {}
      try { var __ov = document.getElementById('__lvlLoadingOverlay'); if (__ov) __ov.style.display = 'none'; } catch(eL2) {}
      clearInterval(interval);
      if (typeof ws.addChangeListener === 'function') ws.addChangeListener(function(e) { sync(ws); });
      else if (ws.zc && ws.zc.push) ws.zc.push(function(e) { sync(ws); });
      setInterval(function() { sync(ws); }, 1000);
      sync(ws);
      setTimeout(function() {
          var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
          if (!bridge) return;
          if (typeof window.X !== 'undefined') {
            bridge.syncMap(JSON.stringify(window.X));
          }
          try {
            var lvl  = (typeof window.K  !== 'undefined') ? window.K  : 1;
            var mxb  = (typeof window.Od !== 'undefined' && isFinite(window.Od)) ? window.Od : -1;
            var tb   = document.getElementById('toolbox');
            var tbHtml = tb ? tb.innerHTML : '';
            var hasLoops = tbHtml.indexOf('maze_forever') !== -1;
            var hasConds = tbHtml.indexOf('maze_if') !== -1;
            var hasIfElse = tbHtml.indexOf('maze_ifElse') !== -1;
            var sd = (typeof window.__stableStartT === 'number') ? window.__stableStartT : ((typeof window.T !== 'undefined') ? window.T : 1);
            var meta = JSON.stringify({ level: lvl, maxBlocks: mxb, startDirection: sd,
                                        allowLoops: hasLoops, allowConditionals: hasConds, allowIfElse: hasIfElse });
            bridge.syncLevelMeta(meta);
          } catch(e) { log('syncLevelMeta error: ' + e); }
      }, 500);

    } else if (iters % 5 == 0) {
      log('Waiting for workspace... (' + iters + 's)');
    }
    iters++;
  }, 1000);
  var runHooked = false;
  setInterval(function() {
    if (runHooked) return;
    var runBtn = document.getElementById('runButton');
    if (runBtn) {
      runHooked = true;
      log('Watching runButton via MutationObserver.');
      var observer = new MutationObserver(function(mutations) {
        for (var i = 0; i < mutations.length; i++) {
          if (mutations[i].attributeName === 'style' && runBtn.style.display === 'none') {
            if (window.__dbgActive || window.__dbgSessionStarted) {
              log('Run observer ignored while debug session is active.');
              continue;
            }
            try {
              // Blockly often resets the start direction to EAST (T=1) when Run starts.
              // If we loaded a model-specific start orientation, keep pegman facing it.
              if (typeof window.__modelStartT === 'number') {
                window.__stableStartT = window.__modelStartT;
                window.T = window.__modelStartT;
                if (typeof Z === 'function') Z(window.Q, window.S, 4 * window.T);
              }
            } catch(e) { /* ignore */ }
            try { window.__blockyRunStarted = true; } catch(e) {}
            log('Run button hidden — syncing state, saving XMI, then running simulation.');
            var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
            if (bridge) {
              var ws = getWS(); if (ws) sync(ws);
              if (typeof window.X !== 'undefined') bridge.syncMap(JSON.stringify(window.X));
              try {
                var lvl = (typeof window.K !== 'undefined') ? window.K : 1;
                var mxb = (typeof window.Od !== 'undefined' && isFinite(window.Od)) ? window.Od : -1;
                var tb = document.getElementById('toolbox'); var tbHtml = tb ? tb.innerHTML : '';
                var sd = (typeof window.__stableStartT === 'number') ? window.__stableStartT : ((typeof window.T !== 'undefined') ? window.T : 1);
                var meta = JSON.stringify({ level: lvl, maxBlocks: mxb, startDirection: sd, allowLoops: tbHtml.indexOf('maze_forever') !== -1, allowConditionals: tbHtml.indexOf('maze_if') !== -1, allowIfElse: tbHtml.indexOf('maze_ifElse') !== -1 });
                bridge.syncLevelMeta(meta);
              } catch(e) { log('syncLevelMeta: ' + e); }
              try {
                var gen = (typeof window.__javaPageGen === 'number') ? window.__javaPageGen : 0;
                if (bridge.runSimulationWithGen) bridge.runSimulationWithGen(gen);
                else bridge.runSimulation();
              } catch(e) { bridge.runSimulation(); }
            }
            break;
          }
        }
      });
      observer.observe(runBtn, { attributes: true, attributeFilter: ['style'] });
    }
  }, 500);
})();
