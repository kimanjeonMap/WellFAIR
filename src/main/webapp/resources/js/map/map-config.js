/*
 * 분석 지도 설정 (살피구로 config.js 를 네이버 지도용으로 옮김)
 * 카카오 level L ≈ 네이버 zoom (20 − L). 네이버는 숫자가 클수록 확대.
 */
window.MAP_CONFIG = {
  /* 3구(구로·양천·영등포) 가운데 */
  CENTER: { lat: 37.513, lng: 126.876 },

  /* 줌 3단계 (살피구로 modeOf) : gu ≤ 14 / cluster 15~17 / building ≥ 18 */
  ZOOM: {
    INIT: 13,          // 시작 (카카오 7) : 구 단위 (서울시청 = 3개 구 전체)
    GU: 14,            // 구청 계정 시작 : 소속 구 하나가 화면에 꽉 차는 줌 (구 단위)
    MIN: 11,
    MAX: 19,
    CLUSTER_MIN: 15,   // 15 이상 : 격자 도넛 묶음
    BUILDING_MIN: 18,  // 18 이상 : 건물 핀
    DONG: 16,          // 동을 고르면
    BUILDING: 18       // 결과 리스트에서 건물을 고르면 / 묶음을 누르면
  },

  /* 묶음 칸 크기 : 화면에서 이 픽셀 크기 칸 안의 건물을 도넛 하나로 */
  CLUSTER_PX: 120,
  /* 건물 단위에서 한 화면에 그리는 핀 최대 수 (점수 높은 순) */
  BUILDING_LIMIT: 1500,

  COLORS: { red: '#D93A3A', orange: '#EC8B2F', yellow: '#E2B52A', green: '#3E9E5A', blue: '#4a8fe0' },

  /* 취약 점수 → 색 구간 (도넛 조각 · 핀 색). hist 순서와 같다 */
  LEVELS: [
    { min: 80, key: 'red',    label: '80점 이상', rank: 4 },
    { min: 60, key: 'orange', label: '60~79점',  rank: 3 },
    { min: 40, key: 'yellow', label: '40~59점',  rank: 2 },
    { min: 0,  key: 'green',  label: '40점 미만', rank: 1 }
  ],

  /* 구 단위 "위험 N곳" → 구 색 (임시값) */
  GU_LEVELS: [
    { min: 1000, key: 'red' },
    { min: 400,  key: 'orange' },
    { min: 100,  key: 'yellow' },
    { min: 0,    key: 'green' }
  ],

  /* 구 선택 (sgg_cd) */
  GU: [
    { code: '11530', name: '구로구',   lat: 37.4954, lng: 126.8580 },
    { code: '11470', name: '양천구',   lat: 37.5270, lng: 126.8560 },
    { code: '11560', name: '영등포구', lat: 37.5200, lng: 126.9050 }
  ],

  /* 핀 머리 그림 : building.use_nm → 용도 묶음 */
  USE_GROUP: { '단독주택': '단독주택', '공동주택': '공동주택', '업무시설': '업무시설', '제1종근린생활시설': '업무시설', '제2종근린생활시설': '업무시설' },

  /* 가중치 조정 팝업 (Figma) : 묶음 하나가 요인 둘에 같이 적용 */
  WEIGHT_GROUPS: [
    { key: 'a', label: '지하층 · 고시원',   def: 30, max: 50, factors: ['ugrnd', 'gosiwon'] },
    { key: 'b', label: '옥탑 · 위반건축물', def: 20, max: 50, factors: ['rooftop', 'viol'] },
    { key: 'c', label: '노후 · 침수',       def: 15, max: 50, factors: ['old', 'flood'] },
    { key: 'd', label: '저지대 · 저가',     def: 5,  max: 30, factors: ['low', 'cheap'] }
  ],

  /* 왼쪽 패널 요인. param 이 있으면 슬라이더 기준값이 서버 파라미터로 간다 */
  FACTORS: [
    { key: 'ugrnd',   on: true },
    { key: 'rooftop', on: true },
    { key: 'gosiwon', on: true },
    { key: 'viol',    on: true },
    { key: 'old',     on: true,  param: 'oldYears', def: 30, min: 10, max: 50, step: 1,   fmt: function (v) { return v + '년 이상'; } },
    { key: 'cheap',   on: false, param: 'cheapPct', def: 20, min: 5,  max: 50, step: 5,   fmt: function (v) { return v + '% 이하'; } },
    { key: 'flood',   on: true,  param: 'floodCnt', def: 1,  min: 1,  max: 5,  step: 1,   fmt: function (v) { return v + '회 이상'; } },
    { key: 'low',     on: true,  param: 'lowElev',  def: 15, min: 5,  max: 40, step: 1,   fmt: function (v) { return v + 'm 이하'; } }
  ],

  SCORE_MIN: 70,
  /* 지도 위 체크박스 "70점 이상만 보기" 기본값 (살피구로 riskOnly) */
  RISK_ONLY: true
};
