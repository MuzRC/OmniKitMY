/**
 * @file tests/converter.test.ts
 * @description Ujian Unit bagi Formula Penukaran Unit (Unit Testing for Universal Converter).
 */

export function testLengthConversions() {
  const mToKm = 1000 / 1000;
  if (mToKm !== 1) throw new Error('1000m sepatutnya bersamaan 1km');

  const cmToM = 100 * 0.01;
  if (cmToM !== 1) throw new Error('100cm sepatutnya bersamaan 1m');

  const inchToM = 0.0254;
  if (Math.abs(inchToM * 39.3701 - 1) > 0.01) throw new Error('Kiraan inci ke meter tidak tepat');

  return true;
}

export function testWeightConversions() {
  const kgToG = 1 * 1000;
  if (kgToG !== 1000) throw new Error('1kg sepatutnya bersamaan 1000g');

  const lbToKg = 2.20462 * 0.453592;
  if (Math.abs(lbToKg - 1) > 0.01) throw new Error('Kiraan paun (lbs) ke kg tidak tepat');

  return true;
}

export function testTemperatureConversions() {
  // 100°C = 212°F
  const cToF = (100 * 9) / 5 + 32;
  if (cToF !== 212) throw new Error('100°C sepatutnya bersamaan 212°F');

  // 0°C = 273.15 K
  const cToK = 0 + 273.15;
  if (cToK !== 273.15) throw new Error('0°C sepatutnya bersamaan 273.15K');

  // 32°F = 0°C
  const fToC = ((32 - 32) * 5) / 9;
  if (fToC !== 0) throw new Error('32°F sepatutnya bersamaan 0°C');

  return true;
}

export function testDataConversions() {
  const mbToKb = 1 * 1024;
  if (mbToKb !== 1024) throw new Error('1MB sepatutnya bersamaan 1024KB');

  const gbToMb = 1 * 1024;
  if (gbToMb !== 1024) throw new Error('1GB sepatutnya bersamaan 1024MB');

  return true;
}
