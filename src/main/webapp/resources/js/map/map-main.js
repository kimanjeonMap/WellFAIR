/*
 * 분석 지도 시작점 (살피구로 main.js 의 modeOf · loadPoints · scheduleLoad · onLevelChanged 담당)
 *  - 줌 3단계 : gu(≤14) 구 경계·라벨만 / cluster(15~17) 격자 도넛 / building(≥18) 건물 핀
 *  - zoom_changed → 모드·범례 갱신, idle → 200ms 디바운스 후 loadPoints()
 *  - 요청 bbox = 화면 범위 + 여유, 요청 순번(pointSeq)으로 늦게 온 옛 응답은 버린다
 *  - 분석 전 : 현장 확인됨 핀 + 구 경계(기본 기준). "분석 실행" 후 : 기준값으로 다시 조회 + 결과 리스트
 */
$(function () {
  var C = window.MAP_CONFIG;
  var $map = $('#map');

  /* 지도 SDK 가 없거나 인증에 실패해도(키 · Web 서비스 URL 미등록) 결과 리스트 · 상세 패널은 동작하게 한다 */
  var hasMap = !!(window.naver && naver.maps && naver.maps.Map);
  if (!hasMap) {
    $map.html('<div class="map-error"><b>네이버 지도를 불러오지 못했습니다</b>'
      + '<p>네이버 클라우드 콘솔 &gt; Maps &gt; Application 에서 <b>Dynamic Map</b> 이 켜져 있고, Web 서비스 URL 에 <code>' + Common.esc(location.origin) + '</code> 이 등록돼 있는지, '
      + 'app.properties 의 app.naver.client-id 가 맞는지 확인하세요.<br>지도 없이도 "분석 실행" 으로 결과 리스트와 건물 상세는 볼 수 있습니다.</p></div>');
  }

  var state = {
    analyzed: false,     // "분석 실행" 을 눌렀는지
    criteria: null,      // 마지막으로 실행한 조건 (없으면 패널 기본값)
    gu: window.LOCK_GU || '', dong: '', q: '',   // 구청 계정이면 소속 구로 고정 (selGu 는 disabled)
    selectedId: null,
    riskOnly: C.RISK_ONLY,   // 기준점 이상 건물이 있는 묶음 · 기준점 이상 핀만 (지도 위 칩으로 끄고 켬)
    lastRaw: null,           // 마지막 응답 { mode, list } : 칩을 눌렀을 때 다시 요청하지 않고 다시 그린다
    pointSeq: 0,
    cellByLevel: {}      // zoom → 묶음 칸 크기 (같은 zoom 에서는 같은 값 → 지도를 옮겨도 묶음이 흔들리지 않음)
  };

  /* ---------- 지도 ---------- */
  /* 첫 화면 : 서울시청 = 3개 구 가운데(zoom 13), 구청 계정 = 소속 구 중심(zoom 14) */
  var lockGu = C.GU.filter(function (x) { return x.code === state.gu; })[0];
  var map = hasMap ? new naver.maps.Map('map', {
    center: new naver.maps.LatLng(lockGu ? lockGu.lat : C.CENTER.lat, lockGu ? lockGu.lng : C.CENTER.lng),
    zoom: lockGu ? C.ZOOM.GU : C.ZOOM.INIT, minZoom: C.ZOOM.MIN, maxZoom: C.ZOOM.MAX,
    zoomControl: false, mapDataControl: false, scaleControl: false,
    logoControlOptions: { position: naver.maps.Position.BOTTOM_RIGHT }
  }) : null;
  /* 인증 실패는 SDK 로드 뒤에 비동기로 오고 그때 naver.maps 가 null 이 된다 → 쓸 때마다 확인 */
  var live = function () { return !!(map && window.naver && naver.maps); };
  var prevFail = window.navermap_authFailure;
  window.navermap_authFailure = function () { map = null; if (prevFail) prevFail(); };
  var LL = function (lat, lng) { return new naver.maps.LatLng(lat, lng); };
  var zoomNow = function () { return live() ? map.getZoom() : C.ZOOM.INIT; };
  /* 현재 zoom 의 표시 방식 (네이버는 숫자가 클수록 확대) */
  var modeOf = function (z) { return z >= C.ZOOM.BUILDING_MIN ? 'building' : z >= C.ZOOM.CLUSTER_MIN ? 'cluster' : 'gu'; };
  var flyTo = function (lat, lng, zoom) {
    if (!live()) return;
    if (zoom == null || zoom === map.getZoom()) map.panTo(LL(lat, lng));
    else map.morph(LL(lat, lng), zoom);
  };
  var criteria = function () { return state.criteria || MapLayer.getCriteria(); };

  /* 결과 리스트 · 상세 공용 파라미터 */
  function listParams() {
    return $.extend({}, criteria(), { gu: state.gu, dong: state.dong, q: state.q });
  }

  /* ---------- 서버 조회 (살피구로 loadPoints) ---------- */
  function loadPoints() {
    if (!live()) return;
    var z = zoomNow(), mode = modeOf(z);
    var seq = ++state.pointSeq;
    if (mode === 'gu') { state.lastRaw = null; MapMarker.clear(); return; }     // 구 단위에서는 건물·묶음 없음
    var b = map.getBounds(), sw = b.getSW(), ne = b.getNE();
    var pad = mode === 'cluster' ? 0.002 : 0.0005;        // 화면 바깥 조금 (건물 단위는 약 50m)
    var q = $.extend({}, criteria());
    q.bbox = [sw.lng() - pad, sw.lat() - pad, ne.lng() + pad, ne.lat() + pad].map(function (v) { return v.toFixed(5); }).join(',');
    var req;
    if (mode === 'cluster') {
      /* 칸 크기(경도, 도) = 화면 CLUSTER_PX 픽셀 */
      if (!state.cellByLevel[z]) state.cellByLevel[z] = C.CLUSTER_PX * (ne.lng() - sw.lng()) / $map.width();
      q.cell = state.cellByLevel[z].toFixed(8);
      req = MapApi.clusters(q);
    } else {
      q.limit = C.BUILDING_LIMIT;
      req = MapApi.buildings(q);
    }
    req.done(function (list) {
      if (seq !== state.pointSeq) return;                 // 늦게 온 옛 응답
      state.lastRaw = { mode: mode, list: list };
      renderMarks();
    });
  }
  /* 마지막 응답(lastRaw)을 지도에. riskOnly 면 기준점 이상 건물이 있는 묶음(빨간 숫자)과 기준점 이상 핀만. 선택한 건물은 항상 */
  var passRisk = function (b) { return !state.riskOnly || b.score >= C.SCORE_MIN || b.bldId === state.selectedId; };
  function renderMarks() {
    if (!state.lastRaw) { MapMarker.clear(); return; }
    var r = state.lastRaw, list;
    if (r.mode === 'cluster') list = r.list.filter(function (c) { return c.point ? passRisk(c.point) : !state.riskOnly || c.riskCount > 0; });
    else list = r.list.filter(passRisk);
    MapMarker.render(r.mode, list);
    if (state.selectedId) MapMarker.select(state.selectedId);
  }
  /* 지도 위 칩 : 기준점 이상만 보기 켜고 끄기 */
  function applyRiskOnly() {
    $('#riskOnly').toggleClass('on', state.riskOnly).attr('aria-pressed', String(state.riskOnly))
      .attr('title', state.riskOnly ? '누르면 모든 건물을 표시합니다' : '누르면 ' + C.SCORE_MIN + '점 이상인 건물만 표시합니다');
  }
  $('#riskOnly').on('click', function () {
    state.riskOnly = !state.riskOnly;
    applyRiskOnly();
    renderMarks();
    legendKey = null; renderLegend(modeOf(zoomNow()));
  });
  applyRiskOnly();
  var moveTimer = null;
  function scheduleLoad() { clearTimeout(moveTimer); moveTimer = setTimeout(loadPoints, 200); }

  function loadGus() {
    MapApi.gus(criteria()).done(function (list) { MapMarker.renderGu(list); });
  }
  function loadChecked() {
    MapApi.checked({ gu: state.gu, dong: state.dong }).done(function (list) { MapMarker.renderChecked(list); });
  }

  /* 줌이 바뀌면 표시 방식 · 범례 (살피구로 onLevelChanged) */
  var legendKey = null;
  function onLevelChanged() {
    var z = zoomNow(), mode = modeOf(z);
    $map.closest('.map-wrap').toggleClass('show-labels', z >= C.ZOOM.MAX);
    if (mode === 'gu') { state.pointSeq++; state.lastRaw = null; MapMarker.clear(); }
    MapMarker.showGu(mode === 'gu');
    renderLegend(mode);
  }
  function renderLegend(mode) {
    var key = mode + '|' + state.analyzed + '|' + state.riskOnly;
    var only = state.riskOnly ? C.SCORE_MIN + '점 이상만 표시 중 · ' : '';
    if (key === legendKey) return;
    legendKey = key;
    var lv = C.LEVELS.map(function (l) { return '<span class="lg"><i class="sw" style="background:' + C.COLORS[l.key] + '"></i>' + l.label + '</span>'; }).join('');
    var html = '<b>범례</b><span class="lg"><i class="lg-check">✓</i> 현장 확인됨</span>';
    if (mode === 'gu') {
      html += '<span class="lg lg-mode">구 단위 · 구 라벨의 숫자 = ' + C.SCORE_MIN + '점 이상 건물 수 · 확대하면 묶음</span>';
    } else if (mode === 'cluster') {
      var sample = MapMarker.svgUri(MapMarker.clusterSvg({ size: 10, hist: [2, 3, 2, 3] }));
      html += lv + '<span class="lg"><img src="' + sample + '" width="22" height="22" alt=""> 숫자 = 건물 수 · 테두리 = 점수 구간 비율</span>'
        + '<span class="lg"><span class="lg-badge">N</span>' + C.SCORE_MIN + '점 이상</span>'
        + '<span class="lg lg-mode">' + only + '묶음을 누르면 확대</span>';
    } else {
      html += lv + '<span class="lg lg-mode">' + only + '건물 단위 · 점수 높은 순 최대 ' + Common.num(C.BUILDING_LIMIT) + '동</span>';
    }
    $('#legend').html(html);
  }

  if (live()) {
    naver.maps.Event.addListener(map, 'zoom_changed', onLevelChanged);
    naver.maps.Event.addListener(map, 'idle', scheduleLoad);
  }

  /* "분석 실행" : 기준값 확정 → 구 요약 · 지도 · 결과 리스트 */
  function runAnalysis(c) {
    state.analyzed = true;
    state.criteria = c;
    state.cellByLevel = {};
    $('#panelRight').addClass('open');
    $('#detailView').hide(); $('#resultView').show();
    resize();
    loadGus();
    loadPoints();
    legendKey = null; renderLegend(modeOf(zoomNow()));
    MapResult.load(true);
  }

  /* ---------- 선택 ---------- */
  function selectBuilding(b, fly) {
    state.selectedId = b.bldId;
    MapMarker.select(b.bldId);
    MapResult.highlight(b.bldId);
    MapPanel.show(b.bldId);
    if (fly) flyTo(b.lat, b.lng, zoomNow() < C.ZOOM.BUILDING ? C.ZOOM.BUILDING : null);
  }

  /* ---------- 모듈 연결 ---------- */
  MapMarker.init(map, {
    mode: function () { return modeOf(zoomNow()); },
    onMarkClick: function (m) {
      if (m.kind === 'cluster') flyTo(m.data.lat, m.data.lng, Math.max(C.ZOOM.BUILDING, zoomNow() + 1));   // 묶음 → 확대
      else selectBuilding(m.data, false);
    },
    onGuClick: function (g) { flyTo(g.lat, g.lng, C.ZOOM.CLUSTER_MIN); }
  });
  MapLayer.init({ onRun: runAnalysis, onToggle: resize });
  MapResult.init({ getParams: listParams, onSelect: function (b) { selectBuilding(b, true); } });
  MapPanel.init({ getCriteria: criteria, onArchived: setChips, onClose: resize });

  function resize() { if (live()) naver.maps.Event.trigger(map, 'resize'); }

  /* ---------- 상단 : 구 · 동 · 검색 · 칩 ---------- */
  var DONGS = window.DONG_LIST || [];   // map.jsp 가 서버 목록을 넣어 준다
  function fillDongs() {
    var $d = $('#selDong').empty().append('<option value="">전체 동</option>');
    DONGS.filter(function (x) { return !state.gu || x.sggCd === state.gu; })
      .forEach(function (x) { $d.append('<option value="' + Common.esc(x.emdNm) + '">' + Common.esc(x.emdNm) + '</option>'); });
  }
  $('#selGu').on('change', function () {
    state.gu = this.value; state.dong = '';
    fillDongs();
    var g = C.GU.filter(function (x) { return x.code === state.gu; })[0];
    flyTo(g ? g.lat : C.CENTER.lat, g ? g.lng : C.CENTER.lng, g ? C.ZOOM.CLUSTER_MIN : C.ZOOM.INIT);
    loadChecked();
    if (state.analyzed) MapResult.load(true);
  });
  $('#selDong').on('change', function () {
    state.dong = this.value;
    var d = DONGS.filter(function (x) { return x.emdNm === state.dong && (!state.gu || x.sggCd === state.gu); })[0];
    if (d) flyTo(d.lat, d.lng, C.ZOOM.DONG);
    loadChecked();
    if (state.analyzed) MapResult.load(true);
  });
  function search() {
    state.q = $.trim($('#searchQ').val());
    if (!state.analyzed) { runAnalysis(MapLayer.getCriteria()); return; }
    MapResult.load(true);
  }
  $('#btnSearch').on('click', search);
  $('#searchQ').on('keydown', function (e) { if (e.key === 'Enter') search(); });

  function setChips() { /* 상단 칩은 없앴다 (대시보드로) */ }

  /* 확대 · 축소 */
  $('#zoomIn').on('click', function () { if (live()) map.setZoom(Math.min(C.ZOOM.MAX, map.getZoom() + 1), true); });
  $('#zoomOut').on('click', function () { if (live()) map.setZoom(Math.max(C.ZOOM.MIN, map.getZoom() - 1), true); });

  /* ---------- 시작 : 구 경계(기본 기준) + 현장 확인됨 핀 ---------- */
  fillDongs();
  renderLegend(modeOf(zoomNow()));
  loadGus();
  loadChecked();
});
