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

const dashboard = {
  title: 'DevChat AI · RAG 검색',
  uid: 'devchat-ai-rag',
  description: 'AI 코드 리뷰 RAG 검색의 대체 처리, 결과 종류, 검색 시간 (RagMetrics)',
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
