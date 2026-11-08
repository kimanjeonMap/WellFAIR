/*
 * 분석 지도 시작점 (살피구로 main.js 의 modeOf · loadPoints · scheduleLoad · onLevelChanged 담당)
 *  - 줌 3단계 : gu(≤14) 구 경계·라벨만 / cluster(15~17) 격자 도넛 / building(≥18) 건물 핀
 *  - zoom_changed → 모드·범례 갱신, idle → 200ms 디바운스 후 loadPoints()
 *  - 요청 bbox = 화면 범위 + 여유, 요청 순번(pointSeq)으로 늦게 온 옛 응답은 버린다
 *  - 분석 전 : 현장 확인됨 핀 + 구 경계(기본 기준). "분석 실행" 후 : 기준값으로 다시 조회 + 결과 리스트
 *  - 동 선택 2단계(법정동 → 행정동 여러 개, api/map/dongs.do) · 결과 리스트 범위(구 전체 / 동 / "지도 화면 안") · [이 지역에서 재검색] (5-1)
 *  - 구청 계정 (11단계) : 소속 구 밖은 어두운 가림막(구멍 뚫린 다각형) + 경계선, 관할 밖으로 나가면 안내 띠, 이동 범위 = 구 bbox + 2km, 최소 줌 12
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
    criteria: null,      // 확정된 조건 = 마지막으로 [분석 실행]한 값 (시작 때 패널 기본값을 한 번 찍어 둔다). 슬라이더만 움직인 값은 쓰지 않는다
    gu: window.LOCK_GU || '', dong: '', adms: [], q: '',  // 구청 계정이면 소속 구로 고정 (selGu 는 disabled). dong = 법정동 하나 전체, adms = 고른 행정동들 "법정동|행정동" (복수 선택)
    bbox: null,              // [이 지역에서 재검색] 뒤의 리스트 범위 ("지도 화면 안"). 동을 다시 고르면 null
    quietUntil: 0,           // 프로그램이 지도를 옮기는 동안(flyTo · fitTo)은 [재검색] 버튼을 띄우지 않는다
    selectedId: null,
    riskOnly: C.RISK_ONLY,   // 기준점 이상 건물이 있는 묶음 · 기준점 이상 핀만 (지도 위 칩으로 끄고 켬)
    checkedMonths: C.CHECKED_MONTHS, // 현장 확인됨 기간 : 3 | 12 | 0 전체 | -1 끄기 (범례에서 고름)
    lastRaw: null,           // 마지막 응답 { mode, list } : 칩을 눌렀을 때 다시 요청하지 않고 다시 그린다
    pointSeq: 0,
    cellByLevel: {}      // zoom → 묶음 칸 크기 (같은 zoom 에서는 같은 값 → 지도를 옮겨도 묶음이 흔들리지 않음)
  };

  /* ---------- 지도 ---------- */
  /* 첫 화면 : 서울시청 = 3개 구 가운데(zoom 13), 구청 계정 = 소속 구 중심(zoom 14) */
  var lockGu = C.GU.filter(function (x) { return x.code === state.gu; })[0];
  var map = hasMap ? new naver.maps.Map('map', {
    center: new naver.maps.LatLng(lockGu ? lockGu.lat : C.CENTER.lat, lockGu ? lockGu.lng : C.CENTER.lng),
    zoom: lockGu ? C.ZOOM.GU : C.ZOOM.INIT, minZoom: lockGu ? C.ZOOM.LOCK_MIN : C.ZOOM.MIN, maxZoom: C.ZOOM.MAX,
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
    state.quietUntil = Date.now() + 2500;
    if (zoom == null || zoom === map.getZoom()) map.panTo(LL(lat, lng));
    else map.morph(LL(lat, lng), zoom);
  };
  /* 동을 고르면 그 동 건물들의 범위로 (bbox = [minLng, minLat, maxLng, maxLat]) */
  var fitTo = function (box) {
    if (!live() || !box) return;
    state.quietUntil = Date.now() + 2500;
    map.fitBounds(new naver.maps.LatLngBounds(LL(box[1], box[0]), LL(box[3], box[2])), { top: 90, right: 60, bottom: 70, left: 60 });
  };
  var guOf = function (code) { return C.GU.filter(function (x) { return x.code === code; })[0]; };
  var criteria = function () { return state.criteria || (state.criteria = MapLayer.getCriteria()); };
  /* 지도 조회 파라미터 = 확정 조건 + 현장 확인됨 기간 */
  var viewParams = function () { return $.extend({}, criteria(), { checkedMonths: state.checkedMonths }); };

  /* 결과 리스트 파라미터. 범위 = bbox("지도 화면 안") 또는 구·법정동·행정동 (기본 구 전체) */
  function listParams() {
    var p = $.extend({}, criteria(), { q: state.q });
    if (state.bbox) p.bbox = state.bbox;
    else { p.gu = state.gu; p.dong = state.dong; p.adms = state.adms; }
    return p;
  }
  /* 리스트 머리 : "구로구 · 구로동 · 구로2동" / "지도 화면 안" */
  function renderScope() {
    var g = guOf(state.gu);
    $('#resultScope').text(state.bbox ? '지도 화면 안'
      : [g ? g.name : '전체 3개 구', state.dong].concat(state.adms.map(admOfKey)).filter(Boolean).join(' · '));
    /* 법정동·행정동을 고르면 그 경계에 테두리 (행정동 여러 개면 전부). 구만 골랐거나 전체 구면 그리지 않는다 */
    if (window.DongBoundary && (state.dong || state.adms.length)) DongBoundary.show(map, state.gu, state.dong, state.adms.map(admOfKey));
    else if (window.DongBoundary) DongBoundary.show(null);
    if (MapMarker.filterGu) MapMarker.filterGu(state.gu, !!(state.dong || state.adms.length));       // 동을 고르면 구 색칠은 숨김
  }
  /* 지도를 옮겨도 리스트는 그대로 (5-1). 분석 뒤 사용자가 옮겼을 때만 [이 지역에서 재검색] 을 띄운다 */
  function onMoved() {
    var out = checkOutside();
    if (out || !state.analyzed || Date.now() < state.quietUntil) return;
    $('#btnRefetch').addClass('show');
  }
  $('#btnRefetch').on('click', function () {
    if (!live()) return;
    var b = map.getBounds(), sw = b.getSW(), ne = b.getNE();
    state.bbox = [sw.lng(), sw.lat(), ne.lng(), ne.lat()].map(function (v) { return v.toFixed(5); }).join(',');
    $(this).removeClass('show');
    renderScope();
    MapResult.load(true);
  });

  /* ---------- 서버 조회 (살피구로 loadPoints) ---------- */
  function loadPoints() {
    if (!live()) return;
    var z = zoomNow(), mode = modeOf(z);
    var seq = ++state.pointSeq;
    if (mode === 'gu') { state.lastRaw = null; MapMarker.clear(); return; }     // 구 단위에서는 건물·묶음 없음
    var b = map.getBounds(), sw = b.getSW(), ne = b.getNE();
    var pad = mode === 'cluster' ? 0.002 : 0.0005;        // 화면 바깥 조금 (건물 단위는 약 50m)
    var q = viewParams();
    if (state.adms.length) { q.gu = state.gu; q.adms = state.adms; }   // 행정동을 고르면 그 행정동 건물만 (묶음 · 핀)
    else if (state.dong) { q.gu = state.gu; q.dong = state.dong; }     // 법정동을 고르면 그 법정동 건물만
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
    if (r.mode === 'cluster') list = r.list.filter(function (c) { return c.point ? passRisk(c.point) : !state.riskOnly || c.riskCount > 0; });   // 켜져 있으면 기준점 이상이 0인 칸은 안 그린다
    else list = r.list.filter(passRisk);
    MapMarker.render(r.mode, list, state.riskOnly);
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

  /* 구 경계 = 실제 행정경계 (resources/geo/seoul_3gu_gu.geojson, 통계청 행정동 경계 2026-07 기준).
     서버 ring 대신 이 고리를 쓴다. 파일을 못 읽으면 서버 ring 그대로 */
  var realRings = null;
  function loadRealRings() {
    if (realRings) return realRings;
    realRings = $.Deferred();
    $.getJSON('resources/geo/seoul_3gu_gu.geojson').done(function (fc) {
      var m = {};
      (fc.features || []).forEach(function (f) {
        var g = f.geometry, c = g.type === 'MultiPolygon' ? g.coordinates[0] : g.coordinates;
        m[f.properties.gu_code] = c[0];                          // [[lng, lat], ...] 바깥 고리
      });
      realRings.resolve(m);
    }).fail(function () { realRings.resolve({}); });
    return realRings;
  }
  function loadGus() {
    var params = viewParams();
    loadRealRings().done(function (rings) {
      MapApi.gus(params).done(function (list) {
        (list || []).forEach(function (g) { if (rings[g.sggCd]) g.ring = rings[g.sggCd]; });
        MapMarker.renderGu(list); setupLock(list);
      });
    });
    loadAreaLabels();
  }

  /* 동 집계 원 : 법정동 · 행정동을 고르면 구 원 대신 "구로동 위험 N곳" / "구로1동 위험 N곳" (구 단위 줌에서만, 확대하면 도넛).
     숫자는 gus.do 를 그 지역 조건(gu + dong 또는 gu + adms)으로 불러 받는다 = 구 원과 같은 기준 */
  var areaSeq = 0;
  function loadAreaLabels() {
    var seq = ++areaSeq;
    var targets = [];
    if (state.adms.length) state.adms.forEach(function (k) { targets.push({ name: admOfKey(k), dong: '', adm: admOfKey(k), q: { gu: state.gu, adms: [k] } }); });
    else if (state.dong) targets.push({ name: state.dong, dong: state.dong, adm: '', q: { gu: state.gu, dong: state.dong } });
    if (!targets.length || !window.DongBoundary || !DongBoundary.center) { MapMarker.renderArea([]); return; }
    var out = [], left = targets.length;
    targets.forEach(function (t) {
      var c = DongBoundary.center(state.gu, t.dong, t.adm ? [t.adm] : []);
      MapApi.gus($.extend(viewParams(), t.q)).always(function (list) {
        if (seq !== areaSeq) return;                                // 그새 다른 지역을 골랐으면 버림
        var g = $.isArray(list) && list[0];
        if (c) out.push({ name: t.name, lat: c[1], lng: c[0], riskCount: g ? g.riskCount : 0, checkedCnt: g ? g.checkedCnt : 0 });
        if (--left === 0) MapMarker.renderArea(out);
      });
    });
  }

  /* ---------- 구청 계정 : 관할 밖 가림막 · 경계선 · 이동 범위 (11단계). 첫 gus 응답의 소속 구 ring 으로 한 번만 ---------- */
  var lock = { ring: null, bbox: null, mask: null, line: null };
  function setupLock(list) {
    if (!lockGu || lock.ring || !live()) return;
    var g = (list || []).filter(function (x) { return x.sggCd === state.gu; })[0];
    if (!g || !g.ring || g.ring.length < 3) return;
    lock.ring = g.ring;                                           // [[lng, lat], ...] 닫힌 고리
    var minLng = 999, minLat = 999, maxLng = -999, maxLat = -999;
    g.ring.forEach(function (p) { minLng = Math.min(minLng, p[0]); maxLng = Math.max(maxLng, p[0]); minLat = Math.min(minLat, p[1]); maxLat = Math.max(maxLat, p[1]); });
    lock.bbox = [minLng, minLat, maxLng, maxLat];
    var hole = g.ring.map(function (p) { return LL(p[1], p[0]); });
    /* 바깥 사각형(서울 서남부를 넉넉히) + 소속 구 ring 을 구멍으로. 지도 타일에 다크 필터(invert + hue-rotate)가 걸려 있어
       오버레이도 같이 뒤집히므로, 화면에 #0b0d12 로 보이게 하려면 그 반전색(#f4f2ed)을 넣는다 */
    var outer = [LL(37.2, 126.5), LL(37.2, 127.3), LL(37.8, 127.3), LL(37.8, 126.5), LL(37.2, 126.5)];
    lock.mask = new naver.maps.Polygon({ map: map, paths: [outer, hole], fillColor: '#f4f2ed', fillOpacity: 0.6, strokeWeight: 0, strokeOpacity: 0, clickable: false, zIndex: 2 });
    lock.line = new naver.maps.Polyline({ map: map, path: hole, strokeColor: '#4a8fe0', strokeWeight: 2, strokeOpacity: 1, clickable: false, zIndex: 3 });
    /* 이동 범위 = 구 bbox + 사방 2km (위도 0.018°, 경도 0.0227°), 최소 줌 12 */
    var dLat = 0.018, dLng = 0.0227;
    map.setOptions({ maxBounds: new naver.maps.LatLngBounds(LL(minLat - dLat, minLng - dLng), LL(maxLat + dLat, maxLng + dLng)), minZoom: C.ZOOM.LOCK_MIN });
    $('#outGu').text(lockGu.name);
    checkOutside();
  }
  /* 점이 고리 안인지 (ray casting) */
  function inRing(lng, lat, ring) {
    var inside = false;
    for (var i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      var xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
      if ((yi > lat) !== (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside;
    }
    return inside;
  }
  /* 지도 중심이 소속 구 밖이면 안내 띠 (이때 [재검색] 은 숨김) */
  function checkOutside() {
    if (!lock.ring || !live()) return false;
    var c = map.getCenter(), out = !inRing(c.lng(), c.lat(), lock.ring);
    $('#outBar').toggleClass('show', out);
    if (out) $('#btnRefetch').removeClass('show');
    return out;
  }
  $('#btnBackGu').on('click', function () { if (lock.bbox) fitTo(lock.bbox); });
  /* 현장 확인된 건물 목록. 핀 모드에서 핀이 없는 건물만 파란 체크 점으로 그려진다 (MapMarker 가 판단) */
  function loadChecked() {
    MapApi.checked({ gu: state.gu, dong: state.dong, adms: state.adms, checkedMonths: state.checkedMonths }).done(function (list) { MapMarker.renderChecked(list); });
  }
  /* 범례 "현장 확인됨" 기간 바꾸기 → 구 요약 · 묶음/핀 · 체크 점 다시 */
  var CHECK_LABEL = { 3: '최근 3개월', 12: '최근 1년', 0: '전체', '-1': '끄기' };
  $('#legend').on('click', '#lgCheck', function (e) { e.stopPropagation(); $('#chkMenu').toggleClass('open'); });
  $('#legend').on('click', '#chkMenu button', function (e) {
    e.stopPropagation();
    state.checkedMonths = Number($(this).data('m'));
    $('#chkMenu').removeClass('open');
    legendKey = null; renderLegend(modeOf(zoomNow()));
    loadGus(); loadPoints(); loadChecked();
  });
  $(document).on('click', function () { $('#chkMenu').removeClass('open'); });

  /* 줌이 바뀌면 표시 방식 · 범례 (살피구로 onLevelChanged) */
  var legendKey = null;
  function onLevelChanged() {
    var z = zoomNow(), mode = modeOf(z);
    $map.closest('.map-wrap').toggleClass('show-labels', z >= C.ZOOM.MAX);
    if (mode === 'gu') { state.pointSeq++; state.lastRaw = null; MapMarker.clear(); }
    MapMarker.showGu(mode === 'gu');
    if (MapMarker.showArea) MapMarker.showArea(mode === 'gu');
    renderLegend(mode);
  }
  function renderLegend(mode) {
    var key = mode + '|' + state.analyzed + '|' + state.riskOnly + '|' + state.checkedMonths;
    var only = state.riskOnly ? C.SCORE_MIN + '점 이상만 표시 중 · ' : '';
    if (key === legendKey) return;
    legendKey = key;
    var key2 = key + '|' + state.checkedMonths;
    var lv = C.LEVELS.map(function (l) { return '<span class="lg"><i class="sw" style="background:' + C.COLORS[l.key] + '"></i>' + l.label + '</span>'; }).join('');
    /* "현장 확인됨" 은 누르면 기간을 고른다 (최근 3개월 / 1년 / 전체 / 끄기) */
    var html = '<b>범례</b><span class="lg lg-chk" id="lgCheck" title="현장 확인 표시 기간"><i class="lg-check">✓</i> 현장 확인됨 <small>· ' + CHECK_LABEL[state.checkedMonths] + ' ▾</small>'
      + '<span class="lg-menu" id="chkMenu">' + [3, 12, 0, -1].map(function (m) { return '<button type="button" data-m="' + m + '" class="' + (m === state.checkedMonths ? 'on' : '') + '">' + CHECK_LABEL[m] + '</button>'; }).join('') + '</span></span>';
    if (mode === 'gu') {
      html += '<span class="lg lg-mode">구 단위 · 구 라벨 = ' + C.SCORE_MIN + '점 이상 · 현장 확인 건수 · 확대하면 묶음</span>';
    } else if (mode === 'cluster') {
      var sample = MapMarker.svgUri(MapMarker.clusterSvg({ size: 10, hist: [2, 3, 2, 3] }));
      html += lv + '<span class="lg"><img src="' + sample + '" width="22" height="22" alt=""> 숫자 = 건물 수 · 테두리 = 점수 구간 비율</span>'
        + '<span class="lg"><span class="lg-badge">N</span>' + C.SCORE_MIN + '점 이상</span>'
        + '<span class="lg"><span class="lg-badge blue">✓N</span>현장 확인</span>'
        + '<span class="lg lg-mode">' + only + '묶음을 누르면 확대</span>';
    } else {
      html += lv + '<span class="lg"><i class="lg-dot"></i> 점 = 핀 ' + C.PIN_ICON_MAX + '개 밖의 건물</span>'
        + '<span class="lg"><span class="lg-badge blue">✓</span>핀의 체크 = 현장 확인됨</span>'
        + '<span class="lg lg-mode">' + only + '건물 단위 · 점수 높은 순 핀 ' + C.PIN_ICON_MAX + '개</span>';
    }
    $('#legend').html(html);
  }

  if (live()) {
    naver.maps.Event.addListener(map, 'zoom_changed', onLevelChanged);
    naver.maps.Event.addListener(map, 'idle', function () { scheduleLoad(); onMoved(); });
  }

  /* "분석 실행" : 이때 조건을 확정 → 구 요약 · 도넛/핀 · 결과 리스트를 한 번에 다시. 이후 지도 이동 · 재검색 · 보관함 저장은 모두 이 조건 */
  function runAnalysis(c) {
    state.analyzed = true;
    state.criteria = c;
    state.cellByLevel = {};
    $('#panelRight').addClass('open');
    $('#detailView').hide(); $('#resultView').show();
    $('#btnRefetch').removeClass('show');
    loadGus();
    loadPoints();
    legendKey = null; renderLegend(modeOf(zoomNow()));
    renderScope();
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
    onGuClick: function (g) { flyTo(g.lat, g.lng, C.ZOOM.CLUSTER_MIN); },
    onAreaClick: function (a) { flyTo(a.lat, a.lng, C.ZOOM.CLUSTER_MIN); }   // 동 집계 원을 누르면 확대 → 도넛
  });
  MapLayer.init({ onRun: runAnalysis, onToggle: resize });
  MapResult.init({ getParams: listParams, onSelect: function (b) { selectBuilding(b, true); } });
  MapPanel.init({ getCriteria: criteria, onArchived: setChips, onClose: resize });

  function resize() { if (live()) naver.maps.Event.trigger(map, 'resize'); }

  /* ---------- 상단 : 구 · 동(법정동 → 행정동 2단계) · 검색 · 칩 ---------- */
  var esc = Common.esc;
  var DONGS = [];   // api/map/dongs.do : [{sggCd, emdNm, cnt, bbox, adms:[{admNm, cnt, bbox}]}]
  /* reopenEmd : 구가 바뀌어 목록을 다시 받은 뒤에도 그 법정동의 행정동 열을 열어 둔다 (복수 선택 중) */
  function loadDongs(reopenEmd) {
    MapApi.dongs({ gu: state.gu }).done(function (list) {
      DONGS = list; renderDongMenu();
      if (reopenEmd) { var $r = $('#dmEmd .dm-row').filter(function () { return $(this).attr('data-emd') === reopenEmd; }); if ($r.length) showAdm($r); }
    });
  }
  function dongOf(sggCd, emdNm) { return DONGS.filter(function (x) { return x.emdNm === emdNm && x.sggCd === sggCd; })[0]; }
  /* 행정동 키 = "법정동|행정동" (같은 이름의 행정동이 다른 법정동에 걸쳐도 구분) */
  function admKey(emd, adm) { return emd + '|' + adm; }
  function admOfKey(k) { return k.split('|')[1]; }
  function emdOfKey(k) { return k.split('|')[0]; }
  /* 버튼 글자 : 전체 동 / 구로동 / 구로동 · 구로2동 / 구로2동 외 2 */
  function dongLabel() {
    var n = state.adms.length;
    if (n === 1) return emdOfKey(state.adms[0]) + ' · ' + admOfKey(state.adms[0]);
    if (n > 1) return admOfKey(state.adms[0]) + ' 외 ' + (n - 1);
    return state.dong || '전체 동';
  }
  function emdHasSel(d) {
    if (d.sggCd !== state.gu) return false;
    if (d.emdNm === state.dong) return true;
    return state.adms.some(function (k) { return emdOfKey(k) === d.emdNm; });
  }
  function renderDongMenu() {
    var $e = $('#dmEmd').empty();
    $e.append('<div class="dm-row dm-all' + (!state.dong && !state.adms.length ? ' sel' : '') + '" data-sgg="" data-emd="">전체 동</div>');
    DONGS.forEach(function (d) {
      var g = guOf(d.sggCd);
      $e.append('<div class="dm-row' + (emdHasSel(d) ? ' sel' : '') + '" data-sgg="' + d.sggCd + '" data-emd="' + esc(d.emdNm) + '">'
        + '<span>' + esc(d.emdNm) + (!state.gu && g ? ' <small>' + esc(g.name) + '</small>' : '') + '</span>' + (d.adms.length ? '<i>›</i>' : '') + '</div>');
    });
    $('#dmAdm').hide();
    $('#dongBtn span').text(dongLabel());
  }
  /* 법정동 줄에 올리면 오른쪽에 그 행정동 목록 (여러 개 고를 수 있음). data-sgg 는 .data() 가 숫자로 바꾸므로 attr 로 읽는다 */
  function showAdm($row) {
    var d = dongOf($row.attr('data-sgg'), $row.attr('data-emd')), $a = $('#dmAdm').empty();
    $('#dmEmd .dm-row').removeClass('hover'); $row.addClass('hover');
    if (!d || !d.adms.length) { $a.hide(); return; }
    var sameGu = d.sggCd === state.gu, whole = sameGu && d.emdNm === state.dong && !state.adms.length;
    $a.append('<div class="dm-row dm-all' + (whole ? ' sel' : '') + '" data-sgg="' + d.sggCd + '" data-emd="' + esc(d.emdNm) + '" data-adm="">' + esc(d.emdNm) + ' 전체</div>');
    d.adms.forEach(function (a) {
      var on = sameGu && state.adms.indexOf(admKey(d.emdNm, a.admNm)) >= 0;
      $a.append('<div class="dm-row dm-multi' + (on ? ' sel' : '') + '" data-sgg="' + d.sggCd + '" data-emd="' + esc(d.emdNm) + '" data-adm="' + esc(a.admNm) + '">'
        + esc(a.admNm) + '</div>');
    });
    $a.css('top', Math.max(0, $row.position().top)).show();
  }
  /* 고른 범위로 지도 이동 : 행정동 여러 개면 bbox 를 합친다 */
  function areaBox() {
    if (state.adms.length) {
      var box = null;
      state.adms.forEach(function (k) {
        var d = dongOf(state.gu, emdOfKey(k)), a = d && d.adms.filter(function (x) { return x.admNm === admOfKey(k); })[0];
        var b = a ? a.bbox : null;
        if (!b) return;
        box = box ? [Math.min(box[0], b[0]), Math.min(box[1], b[1]), Math.max(box[2], b[2]), Math.max(box[3], b[3])] : b.slice();
      });
      return box;
    }
    var d = dongOf(state.gu, state.dong);
    return d ? d.bbox : null;
  }
  /* 범위가 바뀐 뒤 공통 : 지도 이동 · 메뉴 · 리스트 머리 · 현장 확인 · 결과 */
  function applyArea(guChanged, reopenEmd) {
    state.bbox = null;                                             // 동을 다시 고르면 재검색 범위는 풀린다
    $('#btnRefetch').removeClass('show');
    var box = areaBox();
    var nAdm = state.adms.length;
    /* 실제 경계 범위로 화면 맞춤 : 하나면 가운데로 정해진 줌 (map-config SEL_*), 행정동 여러 개면 전부 들어오게 */
    var pbox = window.DongBoundary && DongBoundary.bbox ? DongBoundary.bbox(state.gu, state.dong, state.adms.map(admOfKey)) : null;
    if (pbox && nAdm > 1) fitTo(pbox);
    else if (pbox) flyTo((pbox[1] + pbox[3]) / 2, (pbox[0] + pbox[2]) / 2, nAdm ? C.ZOOM.SEL_ADM : state.dong ? C.ZOOM.SEL_BJD : C.ZOOM.SEL_GU);
    else if (box) fitTo(box);
    else { var g = guOf(state.gu); flyTo(g ? g.lat : C.CENTER.lat, g ? g.lng : C.CENTER.lng, g ? C.ZOOM.GU : C.ZOOM.INIT); }
    if (guChanged) loadDongs(reopenEmd); else $('#dongBtn span').text(dongLabel());
    renderScope();
    loadAreaLabels();                                              // 동 집계 원 (구로동 위험 N곳)
    onLevelChanged();                                              // 표시 방식 · 범례 다시
    scheduleLoad();                                                // 행정동 조건이 바뀌었으니 묶음 · 핀 다시
    loadChecked();
    if (state.analyzed) MapResult.load(true);
  }
  function setGu(sggCd) {
    var changed = sggCd && sggCd !== state.gu;
    if (changed) { state.gu = sggCd; $('#selGu').val(sggCd); state.adms = []; }   // "전체 구" 에서 동을 고르면 그 구로
    return changed;
  }
  /* 법정동 하나 전체 (또는 전체 동) : 메뉴를 닫는다 */
  function selectDong(sggCd, emdNm) {
    var guChanged = setGu(sggCd);
    state.dong = emdNm || ''; state.adms = [];
    $('#dongDd').removeClass('open');
    if (!guChanged) renderDongMenu();
    applyArea(guChanged);
  }
  /* 행정동 하나 켜고 끄기 : 메뉴는 열어 둔 채로 여러 개 고른다 */
  function toggleAdm($row) {
    var guChanged = setGu($row.attr('data-sgg'));
    var k = admKey($row.attr('data-emd'), $row.attr('data-adm')), i = state.adms.indexOf(k);
    if (i >= 0) state.adms.splice(i, 1); else state.adms.push(k);
    state.dong = '';
    if (guChanged) { applyArea(true, $row.attr('data-emd')); return; }
    $row.toggleClass('sel', i < 0);
    $('#dmAdm .dm-all').removeClass('sel');
    $('#dmEmd .dm-row').each(function () {
      var $r = $(this), d = dongOf($r.attr('data-sgg'), $r.attr('data-emd'));
      $r.toggleClass('sel', d ? emdHasSel(d) : !state.adms.length);
    });
    applyArea(false);
  }
  $('#dongBtn').on('click', function (e) { e.stopPropagation(); $('#dongDd').toggleClass('open'); if ($('#dongDd').hasClass('open')) $('#dmAdm').hide(); });
  $('#dmEmd').on('mouseenter', '.dm-row', function () { showAdm($(this)); });
  $('#dmEmd').on('click', '.dm-row', function () { var $r = $(this); selectDong($r.attr('data-sgg'), $r.attr('data-emd')); });
  $('#dmAdm').on('click', '.dm-row', function (e) {
    e.stopPropagation();
    var $r = $(this);
    if ($r.attr('data-adm')) toggleAdm($r); else selectDong($r.attr('data-sgg'), $r.attr('data-emd'));
  });
  $(document).on('click', function (e) { if (!$(e.target).closest('#dongDd').length) $('#dongDd').removeClass('open'); });

  $('#selGu').on('change', function () {
    state.gu = this.value; state.dong = ''; state.adms = []; state.bbox = null;
    $('#btnRefetch').removeClass('show');
    loadDongs();
    var g = guOf(state.gu);
    var pbox = window.DongBoundary && DongBoundary.bbox ? DongBoundary.bbox(state.gu, '', '') : null;   // 구 경계 범위로 화면 맞춤
    if (pbox) flyTo((pbox[1] + pbox[3]) / 2, (pbox[0] + pbox[2]) / 2, C.ZOOM.SEL_GU);
    else flyTo(g ? g.lat : C.CENTER.lat, g ? g.lng : C.CENTER.lng, g ? C.ZOOM.GU : C.ZOOM.INIT);
    renderScope();
    loadAreaLabels();
    onLevelChanged();
    scheduleLoad();
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
  /* 검색창 [×] : 지우고, 검색한 상태였으면 검색어 없이 다시 */
  $('#searchQ').on('input', function () { $('#searchClear').prop('hidden', this.value === ''); });
  $('#searchClear').on('click', function () {
    $('#searchQ').val('').trigger('focus');
    $(this).prop('hidden', true);
    if (state.q) { state.q = ''; if (state.analyzed) MapResult.load(true); }
  });

  function setChips() { /* 상단 칩은 없앴다 (대시보드로) */ }

  /* 확대 · 축소 */
  $('#zoomIn').on('click', function () { if (live()) map.setZoom(Math.min(C.ZOOM.MAX, map.getZoom() + 1), true); });
  $('#zoomOut').on('click', function () { if (live()) map.setZoom(Math.max(C.ZOOM.MIN, map.getZoom() - 1), true); });

  /* ---------- 시작 : 동 목록 + 구 경계(기본 기준) + 현장 확인됨 핀 ---------- */
  loadDongs();
  renderScope();
  renderLegend(modeOf(zoomNow()));
  loadGus();
  loadChecked();
  /* 05 [지도에서 보기] (map.do?bldId=) : 그 건물로 가서 상세 패널을 연다. 구청 계정이 남의 구 건물을 열려 하면 서버가 bldId 를 비우고 안내만 (15단계 A-2) */
  if (window.OPEN_MSG) Common.toast(window.OPEN_MSG);
  if (window.OPEN_BLD) {
    MapApi.building($.extend({}, criteria(), { bldId: window.OPEN_BLD })).done(function (d) {
      if (d && d.building) selectBuilding(d.building, true);
      else Common.toast('건물을 찾을 수 없습니다.');
    });
  }
});
