import { diffLines } from 'diff';

export const UI_FONT = '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif';
export const CODE_FONT = '"JetBrains Mono", "Fira Code", "SF Mono", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace';

export const INACTIVE_REASONS = [
  { value: 'DUPLICATE', label: '중복' },
  { value: 'UNNECESSARY', label: '불필요' },
  { value: 'INCORRECT', label: '잘못됨' },
  { value: 'OTHER', label: '기타' },
];

// ── SHA-256 해시 캐시 ──────────────────────────────────────────────────────────
const hashCache = new Map();

export const getFileHash = async (filePath) => {
  if (hashCache.has(filePath)) return hashCache.get(filePath);
  const hashBuffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(filePath));
  const hashHex = Array.from(new Uint8Array(hashBuffer))
    .map(b => b.toString(16).padStart(2, '0')).join('');
  hashCache.set(filePath, hashHex);
  return hashHex;
};

export const getGitHubFileUrl = async (repositoryUrl, prNumber, filePath) => {
  const hashHex = await getFileHash(filePath);
  return `${repositoryUrl}/pull/${prNumber}/files#diff-${hashHex}`;
};

// ── diff 행 생성 ───────────────────────────────────────────────────────────────
export const buildDiffRows = (baseContent, headContent) => {
  const changes = diffLines(baseContent || '', headContent || '');
  const rows = [];
  let baseNum = 1;
  let headNum = 1;

  changes.forEach(change => {
    const lines = change.value.split('\n');
    if (lines[lines.length - 1] === '') lines.pop();

    if (change.removed) {
      lines.forEach(line =>
        rows.push({ type: 'removed', baseLine: line, headLine: null, baseNum: baseNum++, headNum: null })
      );
    } else if (change.added) {
      lines.forEach(line =>
        rows.push({ type: 'added', baseLine: null, headLine: line, baseNum: null, headNum: headNum++ })
      );
    } else {
      lines.forEach(line =>
        rows.push({ type: 'normal', baseLine: line, headLine: line, baseNum: baseNum++, headNum: headNum++ })
      );
    }
  });

  return rows;
};