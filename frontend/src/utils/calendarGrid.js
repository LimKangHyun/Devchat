const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

// 한국 시간 기준 연/월/일/요일(0=일)
export const kstParts = (date) => {
  const d = new Date(date.getTime() + KST_OFFSET_MS);
  return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate(), dow: d.getUTCDay() };
};

// 한국 시간 y-m-d h:mi 를 Date로 (날짜가 넘치면 알아서 이월)
export const kstDate = (y, m, d, h = 0, mi = 0) =>
  new Date(Date.UTC(y, m - 1, d, h, mi) - KST_OFFSET_MS);

const pad = (n) => String(n).padStart(2, '0');

// 'YYYY-MM-DD' (KST)
export const dayKey = (date) => {
  const t = kstParts(date);
  return `${t.y}-${pad(t.m)}-${pad(t.d)}`;
};

// 그 주 일~토 (각 칸은 KST 0시)
export const weekDays = (date) => {
  const t = kstParts(date);
  return Array.from({ length: 7 }, (_, i) => kstDate(t.y, t.m, t.d - t.dow + i));
};

// 6주 x 7일, 일요일 시작
export const monthGrid = (year, month) => {
  const firstDow = kstParts(kstDate(year, month, 1)).dow;
  return Array.from({ length: 6 }, (_, w) =>
    Array.from({ length: 7 }, (_, i) => kstDate(year, month, 1 - firstDow + w * 7 + i))
  );
};

