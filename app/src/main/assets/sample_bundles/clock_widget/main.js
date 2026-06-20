/**
 * WidgetForge Sample: Animated Clock Widget
 *
 * Draws an analog + digital clock with a tap-ripple effect that
 * demonstrates the onClick(x, y) hook (manifest: captureClickPosition=true).
 *
 * API available:
 *   ctx          — HTML5 Canvas 2D context
 *   WIDTH        — canvas width in pixels
 *   HEIGHT       — canvas height in pixels
 *   AndroidBridge.publish(channel, jsonString)
 *   AndroidBridge.getChannelState(channel)
 *   AndroidBridge.log(message)
 */

// ── Tap ripple state ────────────────────────────────────────────
var ripples = []; // {x, y, age} in pixels / frames

// Called automatically when the user taps the widget, IF
// manifest.json sets captureClickPosition: true. x and y are
// normalized [0,1] across the widget's current WIDTH/HEIGHT.
function onClick(x, y) {
    ripples.push({ x: x * WIDTH, y: y * HEIGHT, age: 0 });
    if (ripples.length > 5) ripples.shift(); // cap concurrent ripples
}

function draw(ctx, WIDTH, HEIGHT) {
    var cx = WIDTH / 2;
    var cy = HEIGHT / 2;
    var r = Math.min(WIDTH, HEIGHT) * 0.38;

    // ── Background ──────────────────────────────────────────────
    var bg = ctx.createRadialGradient(cx, cy, 0, cx, cy, Math.max(WIDTH, HEIGHT));
    bg.addColorStop(0, '#1a1a2e');
    bg.addColorStop(1, '#0d0d1a');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, WIDTH, HEIGHT);

    var now = new Date();
    var h = now.getHours();
    var m = now.getMinutes();
    var s = now.getSeconds();

    // ── Clock face ──────────────────────────────────────────────
    ctx.beginPath();
    ctx.arc(cx, cy * 0.85, r, 0, Math.PI * 2);
    ctx.strokeStyle = 'rgba(108,158,255,0.4)';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Hour markers
    for (var i = 0; i < 12; i++) {
        var angle = (i / 12) * Math.PI * 2 - Math.PI / 2;
        var x1 = cx + Math.cos(angle) * (r - 4);
        var y1 = cy * 0.85 + Math.sin(angle) * (r - 4);
        var x2 = cx + Math.cos(angle) * (r - 12);
        var y2 = cy * 0.85 + Math.sin(angle) * (r - 12);
        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.lineTo(x2, y2);
        ctx.strokeStyle = i % 3 === 0 ? 'rgba(108,158,255,0.8)' : 'rgba(108,158,255,0.3)';
        ctx.lineWidth = i % 3 === 0 ? 2 : 1;
        ctx.stroke();
    }

    // Hour hand
    drawHand(ctx, cx, cy * 0.85, (h % 12 + m / 60) / 12 * Math.PI * 2, r * 0.5, 3, '#6C9EFF');
    // Minute hand
    drawHand(ctx, cx, cy * 0.85, (m + s / 60) / 60 * Math.PI * 2, r * 0.72, 2, '#B8C8FF');
    // Second hand
    drawHand(ctx, cx, cy * 0.85, s / 60 * Math.PI * 2, r * 0.78, 1, '#FF6B9D');

    // Center dot
    ctx.beginPath();
    ctx.arc(cx, cy * 0.85, 4, 0, Math.PI * 2);
    ctx.fillStyle = '#FF6B9D';
    ctx.fill();

    // ── Digital time ─────────────────────────────────────────────
    var timeStr = pad(h) + ':' + pad(m) + ':' + pad(s);
    ctx.textAlign = 'center';
    ctx.font = 'bold ' + (HEIGHT * 0.1) + 'px monospace';
    ctx.fillStyle = 'rgba(255,255,255,0.9)';
    ctx.fillText(timeStr, cx, HEIGHT * 0.88);

    // Date
    var days = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
    var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    var dateStr = days[now.getDay()] + ', ' + months[now.getMonth()] + ' ' + now.getDate();
    ctx.font = (HEIGHT * 0.07) + 'px sans-serif';
    ctx.fillStyle = 'rgba(108,158,255,0.8)';
    ctx.fillText(dateStr, cx, HEIGHT * 0.97);

    // ── Tap ripples (drawn on top, advanced + pruned each frame) ───
    for (var i = ripples.length - 1; i >= 0; i--) {
        var rp = ripples[i];
        var progress = rp.age / 12; // ~1s lifespan at fps:12
        if (progress >= 1) { ripples.splice(i, 1); continue; }
        var radius = progress * Math.max(WIDTH, HEIGHT) * 0.4;
        ctx.beginPath();
        ctx.arc(rp.x, rp.y, radius, 0, Math.PI * 2);
        ctx.strokeStyle = 'rgba(255,107,157,' + (1 - progress) + ')';
        ctx.lineWidth = 2;
        ctx.stroke();
        rp.age++;
    }
}

function drawHand(ctx, cx, cy, angle, length, width, color) {
    var a = angle - Math.PI / 2;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + Math.cos(a) * length, cy + Math.sin(a) * length);
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineCap = 'round';
    ctx.stroke();
}

function pad(n) { return n < 10 ? '0' + n : '' + n; }
