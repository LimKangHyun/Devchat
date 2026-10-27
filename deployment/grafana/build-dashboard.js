// Grafana 대시보드 JSON 생성 스크립트. 실행: node build-dashboard.js → provisioning/dashboards/json/devchat-ai-rag.json
// 패널을 고칠 때는 JSON을 직접 고치지 말고 이 파일을 고친 뒤 다시 생성한다.
const fs = require('fs');
const path = require('path');

const ds = { type: 'prometheus', uid: '${datasource}' };
const job = 'job="devchat-ai"';
const tgt = (expr, legend, refId = 'A', extra = {}) => ({ datasource: ds, expr, legendFormat: legend, refId, ...extra });

let nextId = 1;
const row = (title, y) => ({ id: nextId++, type: 'row', title, collapsed: false, gridPos: { h: 1, w: 24, x: 0, y }, panels: [] });
const panel = (type, title, description, gridPos, targets, fieldConfig = {}, options = {}) => ({
  id: nextId++, type, title, description, datasource: ds, gridPos, targets,
  fieldConfig: { defaults: {}, overrides: [], ...fieldConfig }, options,
});

const rename = (from, to, color) => ({
  matcher: { id: 'byName', options: from },
  properties: [{ id: 'displayName', value: to }, ...(color ? [{ id: 'color', value: { mode: 'fixed', fixedColor: color } }] : [])],
});
const stageNames = [rename('vector_search', '벡터 검색'), rename('structural_search', '구조 검색'), rename('method_query', '메서드별 쿼리'), rename('rerank', '리랭크')];

const panels = [
  row('요약 (선택한 기간)', 0),
  panel('stat', 'AI 서버 수집 상태', 'Prometheus가 devchat-ai를 수집하고 있는지. DOWN이면 아래 지표도 멈춘다.',
    { h: 4, w: 4, x: 0, y: 1 },
    [tgt(`max(up{${job}})`, '')],
    { defaults: {
      mappings: [{ type: 'value', options: { 0: { text: 'DOWN', color: 'red' }, 1: { text: 'UP', color: 'green' } } }],
      thresholds: { mode: 'absolute', steps: [{ color: 'red', value: null }, { color: 'green', value: 1 }] } } },
    { colorMode: 'background', reduceOptions: { calcs: ['lastNotNull'] }, textMode: 'value' }),

  panel('stat', '맥락 없이 나간 리뷰 비율', '검색 결과가 없거나(empty) 실패해서(embedding_failed, error) RAG 맥락 없이 LLM에 넘어간 리뷰 파일의 비율.',
    { h: 4, w: 5, x: 4, y: 1 },
    [tgt(`sum(increase(rag_context_total{${job},result!="ok"}[$__range])) / clamp_min(sum(increase(rag_context_total{${job}}[$__range])), 1)`, '', 'A', { instant: true })],
    { defaults: { unit: 'percentunit', decimals: 1, min: 0, max: 1,
      thresholds: { mode: 'absolute', steps: [{ color: 'green', value: null }, { color: 'orange', value: 0.05 }, { color: 'red', value: 0.2 }] } } },
    { colorMode: 'background', reduceOptions: { calcs: ['lastNotNull'] } }),

  panel('stat', '검색한 리뷰 파일 수', '선택한 기간에 RAG 검색을 거친 리뷰 파일 수.',
    { h: 4, w: 5, x: 9, y: 1 },
    [tgt(`sum(increase(rag_context_total{${job}}[$__range]))`, '', 'A', { instant: true })],
    { defaults: { unit: 'short', decimals: 0, thresholds: { mode: 'absolute', steps: [{ color: 'text', value: null }] } } },
    { colorMode: 'value', reduceOptions: { calcs: ['lastNotNull'] } }),

  panel('stat', '단계별 대체 처리 횟수', '검색 단계가 실패해 낮은 품질로 대체한 횟수. 0이 아니면 해당 외부 서비스를 확인한다.',
    { h: 4, w: 6, x: 14, y: 1 },
    [tgt(`sum by (stage) (increase(rag_fallback_total{${job}}[$__range]))`, '{{stage}}', 'A', { instant: true })],
    { defaults: { unit: 'short', decimals: 0, thresholds: { mode: 'absolute', steps: [{ color: 'green', value: null }, { color: 'orange', value: 1 }] } },
      overrides: stageNames },
    { colorMode: 'background', reduceOptions: { calcs: ['lastNotNull'] }, textMode: 'value_and_name', orientation: 'horizontal' }),

  panel('stat', '검색 시간 p90', '리뷰 파일 1개의 RAG 검색 시간 90번째 백분위 (최근 값).',
    { h: 4, w: 4, x: 20, y: 1 },
    [tgt(`max(rag_search_seconds{${job},quantile="0.9"})`, '')],
    { defaults: { unit: 's', decimals: 2,
      thresholds: { mode: 'absolute', steps: [{ color: 'green', value: null }, { color: 'orange', value: 5 }, { color: 'red', value: 10 }] } } },
    { colorMode: 'value', reduceOptions: { calcs: ['lastNotNull'] } }),

  row('추이', 5),
  panel('timeseries', '단계별 대체 처리 (분당)', '벡터 검색, 구조 검색, 메서드별 쿼리, 리랭크가 실패해 대체 처리된 횟수의 추이.',
    { h: 8, w: 12, x: 0, y: 6 },
    [tgt(`sum by (stage) (rate(rag_fallback_total{${job}}[$__rate_interval])) * 60`, '{{stage}}')],
    { defaults: { unit: 'short', decimals: 2, min: 0, custom: { drawStyle: 'line', lineWidth: 2, fillOpacity: 10, showPoints: 'never' } },
      overrides: stageNames },
    { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'multi' } }),

  panel('timeseries', '검색 결과 종류 (분당)', '리뷰 파일별 검색 결과. 정상 외의 값이 늘면 리뷰가 맥락 없이 나가고 있다는 뜻.',
    { h: 8, w: 12, x: 12, y: 6 },
    [tgt(`sum by (result) (rate(rag_context_total{${job}}[$__rate_interval])) * 60`, '{{result}}')],
    { defaults: { unit: 'short', decimals: 2, min: 0, custom: { drawStyle: 'bars', fillOpacity: 70, lineWidth: 1, stacking: { mode: 'normal', group: 'A' } } },
      overrides: [rename('ok', '정상', 'green'), rename('empty', '결과 없음', 'yellow'),
        rename('embedding_failed', '임베딩 실패', 'orange'), rename('error', '오류', 'red')] },
    { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'multi' } }),

  panel('timeseries', '검색 시간 백분위 (리뷰 파일 1개)', '앱에서 계산한 백분위. 서버가 재시작되면 초기화된다.',
    { h: 8, w: 12, x: 0, y: 14 },
    [tgt(`max(rag_search_seconds{${job},quantile="0.5"})`, 'p50', 'A'),
     tgt(`max(rag_search_seconds{${job},quantile="0.9"})`, 'p90', 'B'),
     tgt(`max(rag_search_seconds{${job},quantile="0.99"})`, 'p99', 'C')],
    { defaults: { unit: 's', decimals: 2, min: 0, custom: { drawStyle: 'line', lineWidth: 2, fillOpacity: 0, showPoints: 'never' } } },
    { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'multi' } }),

  panel('timeseries', '평균 검색 시간', '구간별 평균 = 검색 시간 합계 / 검색 횟수.',
    { h: 8, w: 12, x: 12, y: 14 },
    [tgt(`sum(rate(rag_search_seconds_sum{${job}}[$__rate_interval])) / clamp_min(sum(rate(rag_search_seconds_count{${job}}[$__rate_interval])), 1e-9)`, '평균')],
    { defaults: { unit: 's', decimals: 2, min: 0, custom: { drawStyle: 'line', lineWidth: 2, fillOpacity: 10, showPoints: 'never' } } },
    { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'single' } }),
];

// 검색 점수: 운영에는 정답이 없어 Recall을 잴 수 없으므로, 점수 분포로 검색 품질 변화를 본다.
// 하이브리드 검색 점수는 순위로만 계산한 RRF 점수라 품질을 나타내지 않아 넣지 않는다.
const scorePanel = (title, description, metric, x) => panel('timeseries', title, description,
  { h: 8, w: 8, x, y: 23 },
  [tgt(`max(${metric}{${job},quantile="0.1"})`, '하위 10%', 'A'),
   tgt(`max(${metric}{${job},quantile="0.5"})`, '중앙값', 'B'),
   tgt(`max(${metric}{${job},quantile="0.9"})`, '상위 10%', 'C')],
  { defaults: { unit: 'none', decimals: 3, min: 0, max: 1,
    custom: { drawStyle: 'line', lineWidth: 2, fillOpacity: 0, showPoints: 'never' } } },
  { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'multi' } });

panels.push(
  row('검색 점수 (튜닝용, 앱에서 계산한 분포라 서버 재시작 시 초기화)', 22),
  scorePanel('구조 검색 1위 유사도',
    '구조 검색 후보 중 diff와 가장 비슷한 코드의 코사인 유사도. 낮아지면 구조 검색이 관련 없는 코드를 잡고 있다는 신호.',
    'rag_score_structural_top', 0),
  scorePanel('리랭커 1위 점수',
    'Cohere 리랭커가 매긴 가장 관련 높은 후보의 점수. 검색 품질 전반의 지표(하이브리드 1위 점수 대신 사용).',
    'rag_score_rerank_top', 8),
  scorePanel('리랭커 5위 점수',
    'K=5 경계 후보의 점수. 1위와 차이가 크거나 아주 낮으면 5개를 다 넘길 필요가 없다는 뜻(동적 K 기준 참고).',
    'rag_score_rerank_fifth', 16),
);

// ── 리뷰 품질: MySQL에서 직접 집계 ──
// 사용자가 AI 코멘트를 끄면 ai_comment_mod에 이력이 쌓인다(코멘트 생성 시 "켜짐" 1줄, 끄고 켤 때마다 1줄).
// 코멘트의 현재 상태는 코멘트별 가장 마지막 이력이다. "채택" = 꺼지지 않은 코멘트(아무도 손대지 않은 코멘트 포함).
const mysql = { type: 'mysql', uid: 'mysql-devchat' };
const sqlTgt = (rawSql, format = 'table', refId = 'A') =>
  ({ datasource: mysql, refId, rawSql, format, rawQuery: true, editorMode: 'code' });
const sqlPanel = (...args) => ({ ...panel(...args), datasource: mysql });

const LATEST = `WITH latest AS (
  SELECT m.comment_id, m.active, m.reason, m.other_reason, m.created_at AS changed_at
  FROM ai_comment_mod m
  JOIN (SELECT comment_id, MAX(id) AS max_id FROM ai_comment_mod GROUP BY comment_id) x ON x.max_id = m.id
)`;
const OFF = 'CASE WHEN l.active = 0 THEN 1 ELSE 0 END';
const FROM_COMMENTS = 'FROM ai_review_comment c LEFT JOIN latest l ON l.comment_id = c.id';
const REASON_LABEL = `CASE l.reason WHEN 'DUPLICATE' THEN '중복' WHEN 'UNNECESSARY' THEN '불필요'
  WHEN 'INCORRECT' THEN '잘못됨' WHEN 'OTHER' THEN '기타' ELSE '(이유 없음)' END`;
const percent = { unit: 'percentunit', decimals: 1, min: 0, max: 1 };
const rateThresholds = { mode: 'absolute', steps: [{ color: 'red', value: null }, { color: 'orange', value: 0.6 }, { color: 'green', value: 0.8 }] };

panels.push(
  row('리뷰 품질 (사용자가 끈 AI 코멘트 기준, MySQL 집계)', 31),

  sqlPanel('stat', '채택률', '선택한 기간에 만든 AI 코멘트 중 사용자가 끄지 않은 비율. 아무도 손대지 않은 코멘트도 채택으로 센다.',
    { h: 4, w: 6, x: 0, y: 32 },
    [sqlTgt(`${LATEST}
SELECT 1 - AVG(${OFF}) AS "채택률" ${FROM_COMMENTS} WHERE $__timeFilter(c.created_at)`)],
    { defaults: { ...percent, thresholds: rateThresholds } },
    { colorMode: 'background', reduceOptions: { calcs: ['lastNotNull'] } }),

  sqlPanel('stat', 'AI 코멘트 수', '선택한 기간에 만든 AI 코멘트 수와 그중 사용자가 끈 수.',
    { h: 4, w: 6, x: 6, y: 32 },
    [sqlTgt(`${LATEST}
SELECT COUNT(*) AS "생성", SUM(${OFF}) AS "꺼짐" ${FROM_COMMENTS} WHERE $__timeFilter(c.created_at)`)],
    { defaults: { unit: 'short', decimals: 0, thresholds: { mode: 'absolute', steps: [{ color: 'text', value: null }] } } },
    { colorMode: 'value', reduceOptions: { calcs: ['lastNotNull'] }, textMode: 'value_and_name', orientation: 'horizontal' }),

  sqlPanel('barchart', '꺼진 이유', '사용자가 코멘트를 끌 때 고른 이유. "잘못됨"이 많으면 사실 오류, "불필요"가 많으면 지적 기준을 손볼 신호.',
    { h: 8, w: 12, x: 12, y: 32 },
    [sqlTgt(`${LATEST}
SELECT ${REASON_LABEL} AS "이유", COUNT(*) AS "코멘트 수"
${FROM_COMMENTS} WHERE $__timeFilter(c.created_at) AND l.active = 0
GROUP BY 1 ORDER BY 2 DESC`)],
    { defaults: { unit: 'short', decimals: 0, color: { mode: 'fixed', fixedColor: 'orange' } } },
    { orientation: 'horizontal', showValue: 'always', legend: { showLegend: false }, xField: '이유' }),

  sqlPanel('timeseries', '일별 채택률', '하루 단위 채택률 추이. 프롬프트나 검색 방식을 바꾼 날 전후를 비교한다.',
    { h: 8, w: 12, x: 0, y: 36 },
    [sqlTgt(`${LATEST}
SELECT $__timeGroupAlias(c.created_at, '1d'), 1 - AVG(${OFF}) AS "채택률"
${FROM_COMMENTS} WHERE $__timeFilter(c.created_at)
GROUP BY 1 ORDER BY 1`, 'time_series')],
    { defaults: { ...percent, custom: { drawStyle: 'line', lineWidth: 2, fillOpacity: 10, showPoints: 'always', pointSize: 5 } } },
    { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'single' } }),

  sqlPanel('table', '프롬프트 버전별 채택률', '프롬프트(또는 모델)를 바꾸면 버전이 바뀐다. 같은 기간의 다른 버전과 채택률을 비교한다.',
    { h: 8, w: 12, x: 12, y: 40 },
    [sqlTgt(`${LATEST}
SELECT COALESCE(c.prompt_version, '(기록 전)') AS "버전",
       MIN(c.created_at) AS "처음 사용", MAX(c.created_at) AS "마지막 사용",
       COUNT(*) AS "코멘트", SUM(${OFF}) AS "꺼짐", 1 - AVG(${OFF}) AS "채택률"
${FROM_COMMENTS} WHERE $__timeFilter(c.created_at)
GROUP BY 1 ORDER BY 2 DESC`)],
    { defaults: {}, overrides: [{ matcher: { id: 'byName', options: '채택률' },
      properties: [{ id: 'unit', value: 'percentunit' }, { id: 'decimals', value: 1 },
        { id: 'custom.cellOptions', value: { type: 'color-background' } }, { id: 'thresholds', value: rateThresholds }] }] },
    { showHeader: true }),

  sqlPanel('table', '팀 규칙 코멘트 vs 일반 코멘트', '"[팀 규칙]"으로 시작하는 코멘트(팀 리뷰 규칙 위반 지적)와 일반 코멘트의 채택률 비교.',
    { h: 6, w: 12, x: 0, y: 44 },
    [sqlTgt(`${LATEST}
SELECT CASE WHEN c.comment LIKE '[팀 규칙]%' THEN '팀 규칙' ELSE '일반' END AS "종류",
       COUNT(*) AS "코멘트", SUM(${OFF}) AS "꺼짐", 1 - AVG(${OFF}) AS "채택률"
${FROM_COMMENTS} WHERE $__timeFilter(c.created_at)
GROUP BY 1 ORDER BY 1`)],
    { defaults: {}, overrides: [{ matcher: { id: 'byName', options: '채택률' },
      properties: [{ id: 'unit', value: 'percentunit' }, { id: 'decimals', value: 1 },
        { id: 'custom.cellOptions', value: { type: 'color-background' } }, { id: 'thresholds', value: rateThresholds }] }] },
    { showHeader: true }),

  sqlPanel('table', '최근 꺼진 코멘트', '사용자가 끈 코멘트 원문과 이유. 프롬프트를 고칠 때 실제 사례로 쓴다.',
    { h: 10, w: 24, x: 0, y: 50 },
    [sqlTgt(`${LATEST}
SELECT l.changed_at AS "꺼진 시각", ${REASON_LABEL} AS "이유", l.other_reason AS "기타 사유",
       c.file_path AS "파일", LEFT(c.comment, 200) AS "코멘트", COALESCE(c.prompt_version, '-') AS "버전"
${FROM_COMMENTS} WHERE l.active = 0 AND $__timeFilter(l.changed_at)
ORDER BY l.changed_at DESC LIMIT 50`)],
    { defaults: {}, overrides: [{ matcher: { id: 'byName', options: '코멘트' },
      properties: [{ id: 'custom.cellOptions', value: { type: 'auto', wrapText: true } }, { id: 'custom.width', value: 520 }] }] },
    { showHeader: true, cellHeight: 'md' }),
);

// ── 증분 리뷰: API 서버(job=spring-boot)의 ai_review_files_total ──
const apiJob = 'job="spring-boot"';
const files = (result) => `sum(increase(ai_review_files_total{${apiJob},result="${result}"}[$__range]))`;
panels.push(
  row('증분 리뷰 (새 커밋에서 바뀐 파일만 다시 리뷰)', 60),

  panel('stat', '재사용한 파일 비율', '리뷰 대상 파일 중 이전 커밋의 리뷰 결과를 그대로 쓴 비율. 재사용한 파일 하나가 LLM 호출 하나를 아낀 것이다.',
    { h: 4, w: 6, x: 0, y: 61 },
    [tgt(`${files('reused')} / clamp_min(${files('reused')} + ${files('reviewed')}, 1)`, '', 'A', { instant: true })],
    { defaults: { unit: 'percentunit', decimals: 1, min: 0, max: 1, noValue: '0',
      thresholds: { mode: 'absolute', steps: [{ color: 'text', value: null }] } } },
    { colorMode: 'value', reduceOptions: { calcs: ['lastNotNull'] } }),

  panel('stat', '아낀 리뷰 호출', '증분 리뷰로 다시 리뷰하지 않은 파일 수 (= 아낀 LLM 리뷰 호출 수).',
    { h: 4, w: 6, x: 6, y: 61 },
    [tgt(files('reused'), '', 'A', { instant: true })],
    { defaults: { unit: 'short', decimals: 0, noValue: '0', thresholds: { mode: 'absolute', steps: [{ color: 'text', value: null }] } } },
    { colorMode: 'value', reduceOptions: { calcs: ['lastNotNull'] } }),

  panel('timeseries', '리뷰 요청 vs 재사용 (1시간 단위)', '새로 리뷰를 요청한 파일과 이전 결과를 재사용한 파일 수. mode=full은 첫 리뷰나 비교가 불가능해 전체를 다시 본 경우.',
    { h: 8, w: 12, x: 12, y: 61 },
    [tgt(`sum by (result, mode) (increase(ai_review_files_total{${apiJob}}[1h]))`, '{{mode}} · {{result}}')],
    { defaults: { unit: 'short', decimals: 0, custom: { drawStyle: 'bars', fillOpacity: 60, stacking: { mode: 'normal' } } } },
    { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'multi' } }),
);

// ── LLM 사용량: AI 서버(job=devchat-ai)의 llm_* (LlmBudgetService) ──
const purposeNames = [rename('review', '리뷰'), rename('summary', 'Git 요약')];
panels.push(
  row('LLM 사용량 (Gemini 토큰, 채팅방별 하루 예산)', 69),

  panel('stat', '사용한 토큰', '선택한 기간에 Gemini 리뷰·요약 호출에 쓴 토큰 (입력+출력, thinking 포함).',
    { h: 4, w: 6, x: 0, y: 70 },
    [tgt(`sum(increase(llm_tokens_total{${job}}[$__range]))`, '', 'A', { instant: true })],
    { defaults: { unit: 'short', decimals: 0, thresholds: { mode: 'absolute', steps: [{ color: 'text', value: null }] } } },
    { colorMode: 'value', reduceOptions: { calcs: ['lastNotNull'] } }),

  panel('stat', 'LLM 호출 수', '성공한 Gemini 호출 수 (재시도 실패는 제외).',
    { h: 4, w: 6, x: 6, y: 70 },
    [tgt(`sum by (purpose) (increase(llm_calls_total{${job}}[$__range]))`, '{{purpose}}', 'A', { instant: true })],
    { defaults: { unit: 'short', decimals: 0, thresholds: { mode: 'absolute', steps: [{ color: 'text', value: null }] } },
      overrides: purposeNames },
    { colorMode: 'value', reduceOptions: { calcs: ['lastNotNull'] }, textMode: 'value_and_name' }),

  panel('stat', '예산 때문에 줄이거나 건너뛴 횟수', 'reduced: 한도 80%를 넘어 RAG 맥락 없이 리뷰. blocked: 한도를 넘어 리뷰·요약을 건너뜀. blocked가 자주 보이면 한도(LLM_BUDGET_DAILY_TOKENS_PER_ROOM)를 확인한다.',
    { h: 4, w: 12, x: 12, y: 70 },
    [tgt(`sum by (action) (increase(llm_budget_action_total{${job}}[$__range]))`, '{{action}}', 'A', { instant: true })],
    { defaults: { unit: 'short', decimals: 0, thresholds: { mode: 'absolute', steps: [{ color: 'green', value: null }, { color: 'orange', value: 1 }] } },
      overrides: [rename('reduced', '줄임 (맥락 제외)'), rename('blocked', '건너뜀')] },
    { colorMode: 'background', reduceOptions: { calcs: ['lastNotNull'] }, textMode: 'value_and_name', orientation: 'horizontal' }),

  panel('timeseries', '시간당 토큰', '용도(리뷰, Git 요약)와 종류(입력, 출력)별 시간당 토큰.',
    { h: 8, w: 24, x: 0, y: 74 },
    [tgt(`sum by (purpose, type) (increase(llm_tokens_total{${job}}[1h]))`, '{{purpose}} · {{type}}')],
    { defaults: { unit: 'short', decimals: 0, custom: { drawStyle: 'bars', fillOpacity: 60, stacking: { mode: 'normal' } } } },
    { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'multi' } }),
);

// ── 리뷰 요청 Outbox: API 서버(job=spring-boot)의 OutboxRelay ──
// 게이지는 DB 값이라 블루/그린 두 서버가 같은 값을 낸다 → max로 본다
const eventTypeNames = [rename('AI_REVIEW_PREPARE', '리뷰 준비'), rename('AI_REVIEW_REQUEST', '파일 리뷰 요청')];
panels.push(
  row('리뷰 요청 Outbox (DB → Redis 전송)', 82),

  panel('stat', '보내지 못한 요청', '아직 보내지 못하고 기다리는 outbox 이벤트 수 (재시도 대기 포함). 평소에는 0~몇 개.',
    { h: 4, w: 6, x: 0, y: 83 },
    [tgt(`max(outbox_pending{${apiJob}})`, '')],
    { defaults: { unit: 'short', decimals: 0, noValue: '0',
      thresholds: { mode: 'absolute', steps: [{ color: 'green', value: null }, { color: 'orange', value: 20 }] } } },
    { colorMode: 'background', reduceOptions: { calcs: ['lastNotNull'] } }),

  panel('stat', '가장 오래 기다린 요청', '보내지 못한 이벤트 중 가장 오래된 것의 대기 시간. 10분을 넘으면 Discord로 알린다.',
    { h: 4, w: 6, x: 6, y: 83 },
    [tgt(`max(outbox_oldest_pending_seconds{${apiJob}})`, '')],
    { defaults: { unit: 's', decimals: 0, noValue: '0',
      thresholds: { mode: 'absolute', steps: [{ color: 'green', value: null }, { color: 'orange', value: 60 }, { color: 'red', value: 600 }] } } },
    { colorMode: 'background', reduceOptions: { calcs: ['lastNotNull'] } }),

  panel('stat', '재시도 · 포기', '선택한 기간에 실패 후 다시 시도한 횟수(retry)와 최대 재시도 뒤 포기한 횟수(failed). 포기한 리뷰는 실패로 끝난다.',
    { h: 4, w: 12, x: 12, y: 83 },
    [tgt(`sum by (result) (increase(outbox_events_total{${apiJob},result!="sent"}[$__range]))`, '{{result}}', 'A', { instant: true })],
    { defaults: { unit: 'short', decimals: 0, thresholds: { mode: 'absolute', steps: [{ color: 'green', value: null }, { color: 'orange', value: 1 }] } },
      overrides: [rename('retry', '재시도'), rename('failed', '포기', 'red')] },
    { colorMode: 'background', reduceOptions: { calcs: ['lastNotNull'] }, textMode: 'value_and_name', orientation: 'horizontal' }),

  panel('timeseries', '보낸 이벤트 (10분 단위)', '종류별로 보낸 outbox 이벤트 수.',
    { h: 8, w: 24, x: 0, y: 87 },
    [tgt(`sum by (type) (increase(outbox_events_total{${apiJob},result="sent"}[10m]))`, '{{type}}')],
    { defaults: { unit: 'short', decimals: 0, custom: { drawStyle: 'bars', fillOpacity: 60, stacking: { mode: 'normal' } } },
      overrides: eventTypeNames },
    { legend: { displayMode: 'list', placement: 'bottom' }, tooltip: { mode: 'multi' } }),
);

const dashboard = {
  title: 'DevChat AI · RAG 검색',
  uid: 'devchat-ai-rag',
  description: 'AI 코드 리뷰 RAG 검색의 대체 처리, 결과 종류, 검색 시간 (RagMetrics), 리뷰 품질(MySQL)',
  tags: ['devchat', 'rag'],
  timezone: 'browser',
  editable: true,
  schemaVersion: 39,
  version: 1,
  refresh: '1m',
  time: { from: 'now-24h', to: 'now' },
  templating: { list: [{ name: 'datasource', label: 'Prometheus', type: 'datasource', query: 'prometheus', current: {}, hide: 0, refresh: 1 }] },
  annotations: { list: [] },
  panels,
};

fs.writeFileSync(path.join(__dirname, 'provisioning', 'dashboards', 'json', 'devchat-ai-rag.json'), JSON.stringify(dashboard, null, 2) + '\n');
console.log(`panels=${panels.length}`);
