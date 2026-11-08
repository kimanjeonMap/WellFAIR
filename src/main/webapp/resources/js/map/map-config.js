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
    LOCK_MIN: 12,      // 구청 계정 최소 줌 (관할 밖으로 너무 멀리 못 나가게, 11단계)
    MIN: 11,
    MAX: 19,
    CLUSTER_MIN: 15,   // 15 이상 : 격자 도넛 묶음
    BUILDING_MIN: 18,  // 18 이상 : 건물 핀
    DONG: 16,          // 동을 고르면
    /* 지역 선택 줌 : 고른 지역 테두리 가운데로 이 줌만큼 확대 (숫자가 클수록 더 확대) */
    SEL_GU: 13,        // 구를 고르면
    SEL_BJD: 14,       // 법정동을 고르면
    SEL_ADM: 15,       // 행정동을 고르면
    BUILDING: 18       // 결과 리스트에서 건물을 고르면 / 묶음을 누르면
  },

  /* 묶음 칸 크기 : 화면에서 이 픽셀 크기 칸 안의 건물을 도넛 하나로 */
  CLUSTER_PX: 120,
  /* 그린 뒤 화면에서 이 픽셀 거리 안에 있는 도넛끼리는 하나로 합친다 (바둑판 모양 방지) */
  MERGE_PX: 70,
  /* 건물 단위에서 한 화면에 받는 건물 최대 수 (점수 높은 순) */
  BUILDING_LIMIT: 1500,
  /* 그중 아이콘 핀으로 그리는 수 (점수 순, 동점은 취약 가구 수 순). 나머지는 지름 8px 색 점 */
  PIN_ICON_MAX: 200,

  COLORS: { red: '#D93A3A', orange: '#EC8B2F', yellow: '#E2B52A', green: '#3E9E5A', blue: '#4a8fe0' },

  /* 취약 점수 → 색 구간 (도넛 조각 · 핀 색). hist 순서와 같다 */
  LEVELS: [
    { min: 80, key: 'red',    label: '80점 이상', rank: 4 },
    { min: 60, key: 'orange', label: '60~79점',  rank: 3 },
    { min: 40, key: 'yellow', label: '40~59점',  rank: 2 },
    { min: 0,  key: 'green',  label: '40점 미만', rank: 1 }
  ],

  /* 구 단위 "위험 N곳" → 구 색. 기준점 80 · 기본 가중치(10단계 규칙)로 세면 구마다 약 760~1,750동 (2026-11-08) */
  GU_LEVELS: [
    { min: 1500, key: 'red' },
    { min: 1000, key: 'orange' },
    { min: 500,  key: 'yellow' },
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

  /* 가중치 = 위험 유형 4묶음, 합계 100 (5-1). 서버 파라미터 = key + 'W' (spaceW bldW floodW econW) */
  WEIGHT_GROUPS: [
    { key: 'space', label: '주거 공간', def: 45, factors: ['ugrnd', 'rooftop', 'gosiwon'], names: '지하층 · 옥탑 · 고시원' },
    { key: 'bld',   label: '건물 상태', def: 20, factors: ['old', 'viol'],                names: '노후 · 위반건축물' },
    { key: 'flood', label: '침수 위험', def: 25, factors: ['flood', 'low'],               names: '침수 이력 · 저지대' },
    { key: 'econ',  label: '경제 여건', def: 10, factors: ['cheap'],                      names: '저가 주택' }
  ],
  WEIGHT_STEP: 5,

  /* 왼쪽 패널 요인. param 이 있으면 슬라이더 기준값이 서버 파라미터로 간다 */
  FACTORS: [
    { key: 'ugrnd',   on: true },
    { key: 'rooftop', on: true },
    { key: 'gosiwon', on: true },
    { key: 'viol',    on: true },
    { key: 'old',     on: true,  param: 'oldYears', def: 30, min: 10, max: 50, step: 1,   fmt: function (v) { return v + '년 이상'; } },
    { key: 'cheap',   on: true,  param: 'cheapPct', def: 20, min: 5,  max: 50, step: 5,   fmt: function (v) { return v + '% 이하'; } },
    { key: 'flood',   on: true,  param: 'floodCnt', def: 1,  min: 1,  max: 5,  step: 1,   fmt: function (v) { return v + '회 이상'; } },
    { key: 'low',     on: true,  param: 'lowElev',  def: 15, min: 5,  max: 40, step: 1,   fmt: function (v) { return v + 'm 이하'; } }
  ],

  /* 기준점 : app.properties app.analysis.score-min (map.jsp 가 APP_SCORE_MIN 으로 넘김). 기본 80 */
  SCORE_MIN: window.APP_SCORE_MIN || 80,
  /* 현장 확인됨 표시 기간 기본값 (개월). 12 = 1년, 0 = 전체, -1 = 끄기. 범례의 "현장 확인됨" 에서 바꾼다 */
  CHECKED_MONTHS: 3,
  /* 지도 위 체크박스 "80점 이상만 보기" 기본값 (살피구로 riskOnly) */
  RISK_ONLY: true
};
