const DEFAULT_IMAGES = {
  MEAL: '/assets/photos/services/meal.jpg',
  CLEANING: '/assets/photos/services/cleaning.jpg',
  ERRAND: '/assets/photos/services/errand.jpg',
  ESCORT: '/assets/photos/services/escort.jpg'
};

function serviceImage(icon, category) {
  const fallback = DEFAULT_IMAGES[category] || DEFAULT_IMAGES.MEAL;
  if (typeof icon !== 'string' || !icon) return fallback;
  if (/^\/media\/service-images\/[a-zA-Z0-9-]+\.(jpg|png|jpeg)$/.test(icon)) {
    let base = '';
    try { base = getApp()?.globalData?.baseUrl || ''; } catch (_) {}
    return base ? base.replace(/\/$/, '') + icon : fallback;
  }
  return fallback;
}

function categoryImages(rows) {
  const images = { ...DEFAULT_IMAGES };
  const seen = new Set();
  for (const row of rows) {
    if (DEFAULT_IMAGES[row.category] && !seen.has(row.category)) {
      images[row.category] = row.imageUrl || DEFAULT_IMAGES[row.category];
      seen.add(row.category);
    }
  }
  return images;
}
module.exports = { DEFAULT_IMAGES, serviceImage, categoryImages };
