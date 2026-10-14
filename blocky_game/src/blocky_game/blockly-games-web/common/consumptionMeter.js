/**
 * consumptionMeter.js
 * Live readout of the player's program, shown on maze.html and maze-original.html.
 *   Memory consumption (in kB) = number of blocks      (MoMoT objective "Blocks")
 *   Power consumption (in kW)  = executed actions      (MoMoT objective "Actions")
 * The numbers are computed by the server with the same Java functions as the MoMoT objectives and
 * arrive with every /api/simulation/run response (see webBridge.js, which calls __consumptionUpdate).
 *
 * Position: the maze page has an empty table cell left of the red "Run Program" button, below the maze.
 * The readout goes there and copies the button's font; without that cell it falls back to the bottom-left.
 */
(function() {
    // Full labels: "Memory consumption (in kB)" and "Power consumption (in kW)".
    var MEMORY_NAME = 'Memory consumption';
    var MEMORY_UNIT = '(in kB)';
    var POWER_NAME = 'Power consumption';
    var POWER_UNIT = '(in kW)';

    var box = null;
    var memoryEl = null;
    var powerEl = null;
    var lastMemory;
    var lastPower;

    function fmt(v) {
        return (typeof v === 'number' && !isNaN(v) && v >= 0) ? String(v) : '-';
    }

    // The empty <td> in front of the Run button's cell, or null when the page has no such cell yet.
    function findSlot(runBtn) {
        try {
            var cell = runBtn && runBtn.parentNode;
            var prev = cell && cell.previousElementSibling;
            if (prev && prev.tagName && prev.tagName.toLowerCase() === 'td') return prev;
        } catch (e) {}
        return null;
    }

    function build() {
        var b = document.createElement('div');
        b.id = '__consumptionMeter';
        b.style.padding = '6px 10px';
        b.style.margin = '5px';
        b.style.borderRadius = '8px';
        b.style.background = 'rgba(40,40,40,0.88)';
        b.style.border = '1px solid rgba(255,255,255,0.18)';
        b.style.color = '#fff';
        b.style.textAlign = 'left';
        b.style.boxSizing = 'border-box';
        b.style.pointerEvents = 'none';

        // Two fixed lines per metric: the name, then the unit and the value. Each line never wraps itself,
        // so the label is not split at an arbitrary place.
        function row(name, unit) {
            var r = document.createElement('div');
            r.style.marginTop = '4px';
            var l1 = document.createElement('div');
            l1.textContent = name;
            l1.style.whiteSpace = 'nowrap';
            var l2 = document.createElement('div');
            l2.style.whiteSpace = 'nowrap';
            var u = document.createElement('span');
            u.textContent = unit + ': ';
            var v = document.createElement('b');
            v.textContent = '-';
            l2.appendChild(u);
            l2.appendChild(v);
            r.appendChild(l1);
            r.appendChild(l2);
            b.appendChild(r);
            return v;
        }
        memoryEl = row(MEMORY_NAME, MEMORY_UNIT);
        powerEl = row(POWER_NAME, POWER_UNIT);
        return b;
    }

    // Same text size and font as the Run Program button, whatever the page or the overlay sets on it.
    function syncFont(runBtn) {
        try {
            if (!box || !runBtn || !window.getComputedStyle) return;
            var cs = window.getComputedStyle(runBtn);
            if (cs.fontSize) box.style.fontSize = cs.fontSize;
            if (cs.fontFamily) box.style.fontFamily = cs.fontFamily;
            box.style.lineHeight = '1.35';
        } catch (e) {}
    }

    function ensure() {
        var host = document.body || document.documentElement;
        if (!host) return false;
        var runBtn = document.getElementById('runButton');
        var slot = findSlot(runBtn);

        if (!box) box = build();

        if (slot) {
            if (box.parentNode !== slot) {
                box.style.position = '';
                box.style.left = '';
                box.style.bottom = '';
                box.style.zIndex = '';
                box.style.width = 'auto';
                slot.appendChild(box);
            }
        } else if (!box.parentNode) {
            // Run button not built yet (or an unexpected page layout): park it bottom-left for now.
            box.style.position = 'fixed';
            box.style.left = '10px';
            box.style.bottom = '10px';
            box.style.zIndex = '9000';
            host.appendChild(box);
        }
        syncFont(runBtn);
        return !!slot;
    }

    window.__consumptionUpdate = function(memory, power) {
        try {
            lastMemory = memory;
            lastPower = power;
            ensure();
            memoryEl.textContent = fmt(memory);
            powerEl.textContent = fmt(power);
        } catch (e) {}
    };

    // Move into the slot once the maze page has built its buttons, and keep the font in step with the
    // Run button (the overlay restyles it shortly after load). Cheap: a few DOM reads per second.
    var tries = 0;
    var timer = setInterval(function() {
        tries++;
        var inSlot = false;
        try { inSlot = ensure(); } catch (e) {}
        if (inSlot && tries > 30) clearInterval(timer);
        if (tries > 600) clearInterval(timer);
    }, 100);
})();
