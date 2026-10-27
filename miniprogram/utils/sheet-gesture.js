function sheetDelta(startY, currentY) {
  return Math.max(0, currentY - startY);
}

function sheetShouldClose(delta, elapsedMs, panelHeight) {
  const limit = Math.max(72, (panelHeight > 0 ? panelHeight : 280) * 0.3);
  const velocity = delta / Math.max(elapsedMs, 1);
  return delta >= limit || velocity >= 0.65;
}

module.exports = { sheetDelta, sheetShouldClose };
