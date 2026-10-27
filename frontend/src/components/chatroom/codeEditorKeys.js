// textarea 를 코드 에디터처럼 다루기 위한 키 처리.
// 값 변경은 insertText 로 넣어서 브라우저 기본 실행 취소(Ctrl+Z)가 그대로 동작하게 한다.

const PAIRS = { '(': ')', '[': ']', '{': '}', '"': '"', "'": "'", '`': '`' };
const CLOSERS = new Set([')', ']', '}', '"', "'", '`']);

export const indentUnitFor = (language) =>
  language === 'java' || language === 'python' ? '    ' : '  ';

// 선택 영역 [start, end) 를 text 로 바꾸고 커서/선택을 다시 잡는다.
const replaceRange = (el, start, end, text, selStart, selEnd = selStart) => {
  el.focus();
  el.setSelectionRange(start, end);
  const ok = typeof document.execCommand === 'function' && document.execCommand('insertText', false, text);
  if (!ok) {
    el.setRangeText(text, start, end, 'end');
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }
  el.setSelectionRange(selStart, selEnd);
};

const lineStartOf = (value, pos) => value.lastIndexOf('\n', pos - 1) + 1;

// 여러 줄 선택 시 줄 단위로 들여쓰기/내어쓰기
const shiftLines = (el, unit, outdent) => {
  const { value, selectionStart, selectionEnd } = el;
  const blockStart = lineStartOf(value, selectionStart);
  // 선택 끝이 줄 맨 앞이면 그 줄은 제외
  const effEnd = selectionEnd > selectionStart && value[selectionEnd - 1] === '\n' ? selectionEnd - 1 : selectionEnd;
  let blockEnd = value.indexOf('\n', effEnd);
  if (blockEnd === -1) blockEnd = value.length;

  const lines = value.slice(blockStart, blockEnd).split('\n');
  let firstDelta = 0;
  let totalDelta = 0;
  const next = lines.map((line, i) => {
    if (outdent) {
      const m = line.match(new RegExp(`^( {1,${unit.length}}|\\t)`));
      const removed = m ? m[0].length : 0;
      if (i === 0) firstDelta = -Math.min(removed, selectionStart - blockStart);
      totalDelta -= removed;
      return line.slice(removed);
    }
    if (i === 0) firstDelta = unit.length;
    totalDelta += unit.length;
    return unit + line;
  });

  if (totalDelta === 0) return;
  const singleCaret = selectionStart === selectionEnd;
  replaceRange(
    el,
    blockStart,
    blockEnd,
    next.join('\n'),
    singleCaret ? selectionStart + firstDelta : Math.max(blockStart, selectionStart + firstDelta),
    singleCaret ? selectionStart + firstDelta : selectionEnd + totalDelta,
  );
};

/**
 * 코드 모드 keydown 처리. 처리했으면 true 를 반환(호출부는 기본 동작을 막지 않아도 됨).
 */
export const handleCodeKeyDown = (e, language) => {
  const el = e.currentTarget;
  if (e.nativeEvent?.isComposing || e.altKey) return false;

  const unit = indentUnitFor(language);
  const { value, selectionStart: start, selectionEnd: end } = el;
  const hasSelection = start !== end;

  // Tab / Shift+Tab
  if (e.key === 'Tab' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault();
    if (e.shiftKey || (hasSelection && value.slice(start, end).includes('\n'))) {
      shiftLines(el, unit, e.shiftKey);
    } else {
      replaceRange(el, start, end, unit, start + unit.length);
    }
    return true;
  }

  // Enter: 현재 줄 들여쓰기 유지, 여는 괄호 뒤면 한 단계 더
  if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.metaKey) {
    e.preventDefault();
    const lineStart = lineStartOf(value, start);
    const currentIndent = value.slice(lineStart, start).match(/^[ \t]*/)[0];
    const prev = value[start - 1];
    const after = value[end];
    const opensBlock = prev === '{' || prev === '[' || prev === '(' || (language === 'python' && prev === ':');

    if (opensBlock && after && PAIRS[prev] === after) {
      // {|} → 가운데 줄로 펼치기
      const inner = `\n${currentIndent}${unit}`;
      replaceRange(el, start, end, `${inner}\n${currentIndent}`, start + inner.length);
    } else {
      const text = `\n${currentIndent}${opensBlock ? unit : ''}`;
      replaceRange(el, start, end, text, start + text.length);
    }
    return true;
  }

  if (e.ctrlKey || e.metaKey) return false;

  // Backspace: 빈 괄호쌍은 같이 지우고, 들여쓰기 공백은 한 단계씩 지움
  if (e.key === 'Backspace' && !hasSelection && start > 0) {
    const prev = value[start - 1];
    if (PAIRS[prev] && value[start] === PAIRS[prev]) {
      e.preventDefault();
      replaceRange(el, start - 1, start + 1, '', start - 1);
      return true;
    }
    const lineStart = lineStartOf(value, start);
    const before = value.slice(lineStart, start);
    if (before.length > 0 && /^ +$/.test(before)) {
      e.preventDefault();
      const remove = before.length % unit.length || unit.length;
      replaceRange(el, start - remove, start, '', start - remove);
      return true;
    }
    return false;
  }

  // 닫는 문자가 이미 커서 뒤에 있으면 건너뛰기
  if (CLOSERS.has(e.key) && !hasSelection && value[start] === e.key) {
    e.preventDefault();
    el.setSelectionRange(start + 1, start + 1);
    return true;
  }

  // 괄호·따옴표 자동 닫기 (선택 영역이 있으면 감싸기)
  if (PAIRS[e.key]) {
    const isQuote = e.key === PAIRS[e.key];
    const next = value[end];
    const prevChar = value[start - 1];
    // 단어 바로 뒤의 따옴표(it's 등)나 단어 바로 앞에서는 자동 닫지 않음
    if (!hasSelection && isQuote && prevChar && /\w/.test(prevChar)) return false;
    if (!hasSelection && next && /\w/.test(next)) return false;

    e.preventDefault();
    const selected = value.slice(start, end);
    replaceRange(
      el,
      start,
      end,
      `${e.key}${selected}${PAIRS[e.key]}`,
      start + 1,
      start + 1 + selected.length,
    );
    return true;
  }

  return false;
};
