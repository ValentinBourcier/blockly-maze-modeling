(function(){
  var bridge = window.javaBridge || (window.parent && window.parent.javaBridge);
  function logJS(m) {
    console.log('MoMoT: ' + m);
    if (bridge && bridge.logJS) bridge.logJS('MoMoT: ' + m);
    else alert('MoMoT: ' + m);
  }
  var thisLoadId = window.__momotLoadId;
  function finish() {
    if (thisLoadId && window.__momotLoadId && thisLoadId !== window.__momotLoadId) {
      logJS('Dropping finish() for superseded load request ' + thisLoadId + ' (current=' + window.__momotLoadId + ')');
      return;
    }
    if (bridge) {
      if (typeof bridge.injectCompleteWithGen === 'function' && typeof thisLoadId === 'number') {
        bridge.injectCompleteWithGen(thisLoadId);
      } else if (bridge.injectComplete) {
        bridge.injectComplete();
      }
    }
  }
  try {
    logJS('In-place script started');
    var modelName = window.__momotLastLoadedModelName || 'solution';
    if (window.__momotSetStatus) window.__momotSetStatus('Applying solution…');
    
    if (window.localStorage) {
      logJS('Clearing maze cache');
      for(var i=1; i<=10; i++) window.localStorage.removeItem('maze'+i);
    }
    if (window.sessionStorage) window.sessionStorage.removeItem('Vp');

    var attempts = 0, maxAttempts = 50;
    var interval = setInterval(function() {
      if (thisLoadId && window.__momotLoadId && thisLoadId !== window.__momotLoadId) {
        clearInterval(interval);
        logJS('Superseded by newer load request ' + window.__momotLoadId + ' (dropping attempt ' + thisLoadId + ')');
        // Do NOT call finish(): the newer request is in flight and still owns suppressSync.
        return;
      }
      attempts++;
      var ws = (window.BlocklyInterface && window.BlocklyInterface.getWorkspace && window.BlocklyInterface.getWorkspace()) ||
               (window.Blockly && (window.Blockly.getMainWorkspace && window.Blockly.getMainWorkspace() || window.Blockly.mainWorkspace));
      var bK = (window.h && window.h.K) || (window.Blockly && window.Blockly.Xml);
      
      if (ws && bK) {
        clearInterval(interval);
        logJS('Workspace found. Applying blocks...');
        try {
          var xmlText = window.__momotXml;
          var curXml = '';
          try {
            if (window.h && window.h.K && typeof window.h.K.qn === 'function' && typeof window.h.K.Mc === 'function') {
              curXml = window.h.K.Mc(window.h.K.qn(ws));
            } else {
              var dom = null;
              if (bK && typeof bK.workspaceToDom === 'function') dom = bK.workspaceToDom(ws);
              else if (window.Blockly && window.Blockly.Xml && typeof window.Blockly.Xml.workspaceToDom === 'function') dom = window.Blockly.Xml.workspaceToDom(ws);
              else if (window.h && window.h.K && typeof window.h.K.lf === 'function') dom = window.h.K.lf(ws);
              if (dom) {
                if (bK && typeof bK.domToText === 'function') curXml = bK.domToText(dom);
                else curXml = new XMLSerializer().serializeToString(dom);
              }
            }
          } catch(eC) {}
          function norm(s) {
            if (!s || typeof s !== 'string') return '';
            return s.replace(/<[^>]+>/g, function(tag) {
              return tag.replace(/\s+(?:x|y|id)\s*=\s*(?:"[^"]*"|'[^']*')/gi, '');
            }).replace(/>\s+</g, '><').replace(/\s+/g, ' ').trim();
          }
          var unchanged = !!(curXml && xmlText && norm(curXml) === norm(xmlText));

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

          if (window.__momotSetStatus) {
            window.__momotSetStatus('Loaded ' + modelName + (unchanged ? ' (blocks unchanged)' : ''));
          }
        } catch(e) {
          logJS('Error applying blocks: ' + e);
        }
        if (thisLoadId && window.__momotLoadId && thisLoadId !== window.__momotLoadId) {
          logJS('Dropping finish() for superseded load attempt ' + thisLoadId);
          return;
        }
        finish();
      } else if (attempts > maxAttempts) {
        clearInterval(interval);
        logJS('Timed out waiting for workspace (ws=' + !!ws + ', bK=' + !!bK + ')');
        if (!thisLoadId || !window.__momotLoadId || thisLoadId === window.__momotLoadId) {
          finish();
        }
      }
    }, 200);
  } catch(e) {
    alert('MoMoT TOP ERROR: ' + e);
    if (!thisLoadId || !window.__momotLoadId || thisLoadId === window.__momotLoadId) {
      finish();
    }
  }
})();
