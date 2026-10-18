(function(){
  var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
  function logJS(m) { if (bridge && bridge.logJS) bridge.logJS('ApplyLevel: ' + m); console.log('ApplyLevel: ' + m); }
  function finish() { if (bridge && bridge.injectComplete) bridge.injectComplete(); }
  logJS('Injection script started for Level ' + (window.__injectK !== undefined ? window.__injectK : ''));
  window.__dbgDisableAutoRun = true;
  var attempts = 0, maxAttempts = 60, interval = 100;
  var id = setInterval(function() {
    var ws = (window.BlocklyInterface && window.BlocklyInterface.getWorkspace && window.BlocklyInterface.getWorkspace()) ||
             (window.Blockly && window.Blockly.getMainWorkspace && window.Blockly.getMainWorkspace());
    var bK = (window.h && window.h.K) || (window.Blockly && window.Blockly.Xml);
    if (!document.getElementById('svgMaze') || !ws || !bK) {
      attempts++; if (attempts >= maxAttempts) {
         clearInterval(id);
         var m=[]; if(!document.getElementById('svgMaze'))m.push('svgMaze'); if(!ws)m.push('ws'); if(!bK)m.push('bK');
         logJS('Timed out waiting for workspace. Missing: ' + m.join(','));
         finish();
      }
      return;
    }
    clearInterval(id);
    logJS('Workspace found. Overwriting maze state...');
    if (window.__injectGridJson !== undefined) {
      window.X = window.__injectGridJson;
      if (window.__injectQd !== undefined) window.Qd = window.__injectQd;
      if (window.__injectRd !== undefined) window.Rd = window.__injectRd;
      if (window.__injectSd !== undefined) window.Sd = window.__injectSd;
      if (window.__injectTd !== undefined) window.Td = window.__injectTd;
      if (window.__injectNd !== undefined) window.nd = {x: window.__injectNd.x, y: window.__injectNd.y};
      if (window.__injectOd !== undefined) window.od = {x: window.__injectOd.x, y: window.__injectOd.y};
      if (window.__injectK !== undefined) window.K = window.__injectK;
      if (window.__injectOdVal !== undefined) window.Od = window.__injectOdVal;
      if (window.__modelStartT !== undefined) window.modelStartT = window.__modelStartT;
      if (window.redrawMap) { window.redrawMap(); logJS('redrawMap called'); }
      if (typeof Z === 'function' && window.__injectQ !== undefined && window.__injectS !== undefined) {
        var dir = (typeof window.modelStartT === 'number') ? window.modelStartT : (typeof window.T === 'number' ? window.T : 1);
        window.Q = window.__injectQ; window.S = window.__injectS; window.T = dir;
        Z(window.Q, window.S, 4 * dir);
        logJS('Z called: pegman moved to (' + window.Q + ',' + window.S + ') dir=' + dir);
      }
    }
    if (window.localStorage) { for(var i=1; i<=10; i++) window.localStorage.removeItem('maze'+i); }
    if (window.sessionStorage) window.sessionStorage.removeItem('Vp');
    var ok = false;
    if (window.BlocklyInterface && window.BlocklyInterface.Kv) {
      try { window.BlocklyInterface.Kv(window.__loadXml); ok = true; logJS('Kv loaded'); } catch(eK) { ok = false; }
    }
    if (!ok) {
      try {
        var dom = (bK.textToDom ? bK.textToDom(window.__loadXml) : (bK.$f ? bK.$f(window.__loadXml) : null));
        if (!dom) dom = (new DOMParser()).parseFromString(window.__loadXml, 'text/xml').documentElement;
        if (dom) {
          if (dom.nodeName && dom.nodeName.toLowerCase() !== 'xml') { var w = document.createElement('xml'); w.appendChild(dom); dom = w; }
          ws.clear();
          if (bK.domToWorkspace) bK.domToWorkspace(dom, ws); else if (bK.Eg) bK.Eg(dom, ws);
          ok = true; logJS('Fallback domToWorkspace loaded');
        }
      } catch(e2) { logJS('Error: ' + e2); }
    }
    if (typeof Z === 'function' && window.__injectQ !== undefined && window.__injectS !== undefined) {
      var dir = (typeof window.modelStartT === 'number') ? window.modelStartT : (typeof window.T === 'number' ? window.T : 1);
      window.Q = window.__injectQ; window.S = window.__injectS; window.T = dir;
      Z(window.Q, window.S, 4 * dir);
    }
    logJS('Injection complete.');
    finish();
  }, interval);
})();
