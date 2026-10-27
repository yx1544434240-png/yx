// Null/empty values are unknown coordinates; zero is a valid coordinate.
function hasCoordinates(latitude, longitude) {
  if ([latitude, longitude].some(value => value === null || value === undefined || value === '')) return false;
  const lat = Number(latitude), lon = Number(longitude);
  return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
}
module.exports = { hasCoordinates };
