(function(){
  var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
  function logJS(m) {
    console.log('MoMoT: ' + m);
    if (bridge && bridge.logJS) bridge.logJS('MoMoT: ' + m);
    else alert('MoMoT: ' + m);
  }
  function finish() { if (bridge && bridge.injectComplete) bridge.injectComplete(); }
  try {
    logJS('In-place script started');
    if (window.__momotSetStatus) window.__momotSetStatus('Applying solution…');
    
    if (window.localStorage) {
      logJS('Clearing maze cache');
      for(var i=1; i<=10; i++) window.localStorage.removeItem('maze'+i);
    }
    if (window.sessionStorage) window.sessionStorage.removeItem('Vp');

    var attempts = 0, maxAttempts = 50;
    var interval = setInterval(function() {
      attempts++;
      var ws = (window.BlocklyInterface && window.BlocklyInterface.getWorkspace && window.BlocklyInterface.getWorkspace()) ||
               (window.Blockly && (window.Blockly.getMainWorkspace && window.Blockly.getMainWorkspace() || window.Blockly.mainWorkspace));
      var bK = (window.h && window.h.K) || (window.Blockly && window.Blockly.Xml);
      
      if (ws && bK) {
        clearInterval(interval);
        logJS('Workspace found. Applying blocks...');
        try {
          var xmlText = window.__momotXml;
          var ok = false;
          if (window.BlocklyInterface && window.BlocklyInterface.Kv) {
             try { window.BlocklyInterface.Kv(xmlText); ok = true; logJS('Applied via Kv'); } catch(eK) { ok = false; }
          }
          if (!ok) {
            var dom = (bK.textToDom ? bK.textToDom(xmlText) : (bK.$f ? bK.$f(xmlText) : null));
            if (!dom) dom = (new DOMParser()).parseFromString(xmlText, 'text/xml').documentElement;
            if (dom) {
              if (dom.nodeName && dom.nodeName.toLowerCase() !== 'xml') {
                var wrap = document.createElement('xml'); wrap.appendChild(dom); dom = wrap;
              }
              ws.clear();
              if (bK.domToWorkspace) bK.domToWorkspace(dom, ws);
              else if (bK.Eg) bK.Eg(dom, ws);
              ok = true; logJS('Applied via fallback');
            }
          }
          if (!ok) logJS('CRITICAL: No injection method worked');

          if (window.__injectQ !== undefined && window.__injectS !== undefined) {
            logJS('Teleporting pegman to aligned cell (' + window.__injectQ + ',' + window.__injectS + ')');
            window.Q = window.__injectQ;
            window.S = window.__injectS;
            if (typeof window.__modelStartT === 'number') window.T = window.__modelStartT;
            if (typeof Z === 'function') {
              Z(window.Q, window.S, 4 * window.T);
            }
          }
        } catch(e) {
          logJS('Error applying blocks: ' + e);
        }
        finish();
      } else if (attempts > maxAttempts) {
        clearInterval(interval);
        logJS('Timed out waiting for workspace (ws=' + !!ws + ', bK=' + !!bK + ')');
        finish();
      }
    }, 200);
  } catch(e) {
    alert('MoMoT TOP ERROR: ' + e);
    finish();
  }
})();
