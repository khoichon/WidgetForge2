/**
 * WidgetForge Sample: Weather Widget
 *
 * Subscribes to the 'weather' channel for real-time data updates
 * from other widgets or external data sources.
 *
 * Publish weather data from another widget:
 *   AndroidBridge.publish('weather', JSON.stringify({
 *     temp: 22, condition: 'Cloudy', humidity: 70
 *   }));
 */

var weatherData = { temp: 22, condition: 'Sunny', humidity: 65 };

// Called by the engine when a channel message arrives
function onChannelMessage(channel, payload) {
    if (channel === 'weather') {
        weatherData = payload;
    }
}

function draw(ctx, WIDTH, HEIGHT) {
    // Background
    var grad = ctx.createLinearGradient(0, 0, WIDTH, HEIGHT);
    grad.addColorStop(0, '#1a3a5c');
    grad.addColorStop(1, '#0d1f33');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, WIDTH, HEIGHT);

    var data = weatherData;

    // Condition icon (emoji shorthand)
    var icon = conditionIcon(data.condition || 'Sunny');
    ctx.textAlign = 'left';
    ctx.font = (HEIGHT * 0.45) + 'px sans-serif';
    ctx.fillText(icon, HEIGHT * 0.1, HEIGHT * 0.72);

    // Temperature
    ctx.textAlign = 'right';
    ctx.font = 'bold ' + (HEIGHT * 0.5) + 'px sans-serif';
    ctx.fillStyle = '#FFFFFF';
    ctx.fillText((data.temp || '--') + '°', WIDTH - HEIGHT * 0.1, HEIGHT * 0.72);

    // Condition label
    ctx.textAlign = 'right';
    ctx.font = (HEIGHT * 0.18) + 'px sans-serif';
    ctx.fillStyle = 'rgba(108,158,255,0.85)';
    ctx.fillText((data.condition || 'Unknown'), WIDTH - HEIGHT * 0.1, HEIGHT * 0.92);

    // Humidity
    ctx.textAlign = 'left';
    ctx.font = (HEIGHT * 0.16) + 'px sans-serif';
    ctx.fillStyle = 'rgba(255,255,255,0.5)';
    ctx.fillText('💧 ' + (data.humidity || '--') + '%', HEIGHT * 0.15, HEIGHT * 0.92);
}

function conditionIcon(condition) {
    var c = condition.toLowerCase();
    if (c.includes('sun') || c.includes('clear')) return '☀️';
    if (c.includes('cloud')) return '☁️';
    if (c.includes('rain')) return '🌧️';
    if (c.includes('snow')) return '❄️';
    if (c.includes('storm') || c.includes('thunder')) return '⛈️';
    if (c.includes('wind')) return '💨';
    return '🌤️';
}
