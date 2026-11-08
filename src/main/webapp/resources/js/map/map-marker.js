/*
 * 지도 위 그리기만 (살피구로 main.js 의 clusterSvg · pointSvg · drawPin · drawCluster · setMarks · renderGu 를 네이버로 옮김)
 *  - 카카오 CustomOverlay → naver.maps.Marker({ position, icon: { content: el, anchor } })
 *  - 다시 불러와도 그대로인 마커는 두고, 키가 바뀐 것만 넣고 뺀다 (setMarks)
 *  - 지도 타일은 CSS filter 로 다크 처리하므로 마커 내용은 .mk-inv 로 되돌린다 (map.css)
 */
window.MapMarker = (function () {
  var C = window.MAP_CONFIG;
  var esc = Common.esc;
  var map = null, handlers = {};
  var marks = {};          // key → { key, kind: 'cluster' | 'building', data, el, mk }
  var riskOnly = true;     // 도넛 가운데 숫자 : 켜져 있으면 기준점 이상 건물 수, 꺼져 있으면 전체 건물 수
  var pinById = {};        // 건물 id → mark
  var checked = [];        // 핀 모드에서 핀이 없는 현장 확인 건물의 파란 체크 점 (마커)
  var checkedList = [];    // api/map/checked.do 응답 (기간 안 현장 확인된 건물)
  var gus = [];            // { polygon, label }
  var guShown = false;
  var selectedId = null;

  function noMap() { return !map || !window.naver || !naver.maps; }
  function LL(lat, lng) { return new naver.maps.LatLng(lat, lng); }
  function levelOf(score) {
    for (var i = 0; i < C.LEVELS.length; i++) if (score >= C.LEVELS[i].min) return C.LEVELS[i];
    return C.LEVELS[C.LEVELS.length - 1];
  }
  function guLevelOf(n) {
    for (var i = 0; i < C.GU_LEVELS.length; i++) if (n >= C.GU_LEVELS[i].min) return C.GU_LEVELS[i];
    return C.GU_LEVELS[C.GU_LEVELS.length - 1];
  }
  function useGroupOf(useNm) { return C.USE_GROUP[useNm] || '기타'; }
  var compact = function (n) { return n >= 1000 ? (n / 1000).toFixed(n >= 10000 ? 0 : 1) + '천' : String(n); };
  var svgUri = function (svg) { return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg); };

  /* ---------- 아이콘 (살피구로 그대로) ---------- */
  var GLYPH = {
    '단독주택': function () { return '<path d="M6.8 12.6 12 8l5.2 4.6V17.4H6.8z" fill="#fff"/>'; },
    '공동주택': function (c) { return '<rect x="8" y="6.6" width="8" height="11" rx=".6" fill="#fff"/><rect x="9.5" y="8.4" width="1.8" height="1.8" fill="' + c + '"/><rect x="12.7" y="8.4" width="1.8" height="1.8" fill="' + c + '"/><rect x="9.5" y="11.6" width="1.8" height="1.8" fill="' + c + '"/><rect x="12.7" y="11.6" width="1.8" height="1.8" fill="' + c + '"/>'; },
    '업무시설': function () { return '<rect x="6.8" y="10" width="10.4" height="7" rx="1.2" fill="#fff"/><rect x="9.8" y="7.6" width="4.4" height="3" rx=".8" fill="none" stroke="#fff" stroke-width="1.3"/>'; },
    '기타': function () { return '<circle cx="12" cy="12" r="3.2" fill="#fff"/>'; }
  };
  /* 건물 아이콘 : 지도 핀 모양 (끝이 건물 위치). 머리 부분에 용도 그림 */
  function pointSvg(color, use, selected, size) {
    var s = size || (selected ? 34 : 26);
    var h = Math.round(s * 32 / 24);
    var edge = selected ? 'stroke="#1E3A5F" stroke-width="2.2"' : 'stroke="#fff" stroke-width="1.8"';
    return '<svg xmlns="http://www.w3.org/2000/svg" width="' + s + '" height="' + h + '" viewBox="0 0 24 32"><path d="M12 30.5C12 30.5 2.5 19.6 2.5 12a9.5 9.5 0 0 1 19 0c0 7.6-9.5 18.5-9.5 18.5z" fill="' + color + '" ' + edge + '/>' + (GLYPH[use] || GLYPH['기타'])(color) + '</svg>';
  }
  /* 도넛 테두리 = 점수 구간 비율 (12시 방향부터 80+ → 40 미만) */
  function clusterRadius(size) { return Math.round(Math.min(28, 15 + 4.5 * Math.log10(Math.max(size, 1)))); }
  function clusterSvg(c) {
    var r = clusterRadius(c.size), ring = 6, S = (r + 2) * 2, m = S / 2;
    var rr = r - ring / 2, L = 2 * Math.PI * rr;
    var counts = c.hist;
    var parts = counts.filter(Boolean).length;
    var off = 0, segs = '';
    counts.forEach(function (n, i) {
      if (!n) return;
      var len = L * n / c.size;
      var gap = parts > 1 ? Math.min(2, len / 2) : 0;   // 단계 사이 2px 틈
      segs += '<circle cx="' + m + '" cy="' + m + '" r="' + rr + '" fill="none" stroke="' + C.COLORS[C.LEVELS[i].key] + '" stroke-width="' + ring + '"'
        + ' stroke-dasharray="' + (len - gap).toFixed(2) + ' ' + L.toFixed(2) + '" stroke-dashoffset="' + (-off).toFixed(2) + '" transform="rotate(-90 ' + m + ' ' + m + ')"/>';
      off += len;
    });
    return '<svg xmlns="http://www.w3.org/2000/svg" width="' + S + '" height="' + S + '" viewBox="0 0 ' + S + ' ' + S + '"><circle cx="' + m + '" cy="' + m + '" r="' + (r + 1.5) + '" fill="#fff"/>' + segs + '</svg>';
  }

  /* ---------- 마커 만들기 / 바꾸기 ---------- */
  function putMarker(m, pos, anchor, zIndex, title) {
    if (m.mk) { m.mk.setMap(null); }   // 크기가 바뀌면 기준점을 다시 잡도록 새로 얹는다
    m.mk = new naver.maps.Marker({
      position: pos, map: map, title: title || '',
      icon: { content: m.el, anchor: anchor },
      zIndex: zIndex
    });
    naver.maps.Event.addListener(m.mk, 'click', function () { if (handlers.onMarkClick) handlers.onMarkClick(m); });
  }
  function drawPin(m) {
    var b = m.data, lv = levelOf(b.score), sel = b.bldId === selectedId;
    var title = (b.bldNm ? b.bldNm + ' · ' : '') + Common.shortAddr(b.addr) + ' · ' + b.score + '점' + (b.vulnHhCnt ? ' · 취약 가구 ' + b.vulnHhCnt : '') + (b.checked ? ' · 현장 확인됨' : '');
    /* 점수 순 상위 PIN_ICON_MAX 밖의 건물은 지름 8px 색 점 (아이콘 · 라벨 없음). 눌러도 상세는 열린다. 현장 확인된 점은 파란 테두리 */
    if (b.dot && !sel) {
      m.el.className = 'mk-inv kdot' + (b.checked ? ' chk' : '');
      m.el.style.width = m.el.style.height = '8px';
      m.el.style.background = C.COLORS[lv.key];
      m.el.innerHTML = '';
      putMarker(m, LL(b.lat, b.lng), new naver.maps.Point(4, 4), 60 + lv.rank, title);
      return;
    }
    /* 점수 낮은 건물은 작게, 나머지는 크게 → 취약한 건물이 먼저 눈에 들어오도록 */
    var s = sel ? 36 : lv.rank === 1 ? 20 : 28, h = Math.round(s * 32 / 24);
    m.el.className = 'mk-inv kpin' + (sel ? ' sel' : '');
    m.el.style.width = s + 'px';
    m.el.style.height = h + 'px';
    m.el.innerHTML = '<img src="' + svgUri(pointSvg(C.COLORS[lv.key], useGroupOf(b.useNm), sel, s)) + '" width="' + s + '" height="' + h + '" alt="" draggable="false">'
      + (b.checked ? '<i class="kpin-check" title="현장 확인됨">✓</i>' : '')     /* 9단계 : 따로 핀을 띄우지 않고 그 건물 핀에 파란 체크 배지 */
      + '<span class="kpin-label">' + esc(Common.shortAddr(b.addr)) + '</span>';
    putMarker(m, LL(b.lat, b.lng), new naver.maps.Point(s / 2, h), sel ? 999 : 100 + lv.rank, title);
  }
  /* 도넛 묶음 (10단계에서 8단계 이전으로 되돌림) : 가운데 큰 숫자 = 칸 안 전체 건물 수, 오른쪽 위 빨간 배지 = 기준점 이상 건물 수,
     오른쪽 아래 파란 체크 배지 = 현장 확인된 건물 수 (9단계). 링 색 비율은 그대로 */
  function drawCluster(m) {
    var c = m.data, r = clusterRadius(c.size), S = (r + 2) * 2, k = Math.round(r * 0.8);
    m.el.className = 'mk-inv kcl';
    m.el.style.width = m.el.style.height = S + 'px';
    m.el.innerHTML = '<img src="' + svgUri(clusterSvg(c)) + '" width="' + S + '" height="' + S + '" alt="" draggable="false">'
      + '<b style="font-size:' + (r >= 22 ? 15 : 14) + 'px">' + compact(c.size) + '</b>'
      + (c.riskCount > 0 ? '<i style="left:' + (S / 2 + k) + 'px;top:' + (S / 2 - k) + 'px">' + compact(c.riskCount) + '</i>' : '')
      + (c.checkedCnt > 0 ? '<em style="left:' + (S / 2 + k) + 'px;top:' + (S / 2 + k) + 'px" title="현장 확인 ' + c.checkedCnt + '동">✓' + compact(c.checkedCnt) + '</em>' : '');
    putMarker(m, LL(c.lat, c.lng), new naver.maps.Point(S / 2, S / 2), 10 + Math.min(c.riskCount, 80),
      '건물 ' + c.size + '동 · ' + C.SCORE_MIN + '점 이상 ' + c.riskCount + '동 · 현장 확인 ' + (c.checkedCnt || 0) + '동 · 최고 ' + c.maxScore + '점');
  }

  /* 화면 픽셀 거리 MERGE_PX 안의 도넛끼리 하나로 (큰 쪽에 숫자 · 구간을 더하고, 위치는 건물 수 가중 평균). 그리기 직전에 */
  function mergeClusters(list) {
    if (noMap() || !list.length) return list;
    var proj = map.getProjection();
    var px = function (c) { var p = proj.fromCoordToOffset(LL(c.lat, c.lng)); return [p.x, p.y]; };
    var kept = [];
    list.slice().sort(function (a, b) { return b.size - a.size; }).forEach(function (c) {
      var p = px(c), hit = null;
      for (var i = 0; i < kept.length && !hit; i++) {
        var q = kept[i].p, dx = p[0] - q[0], dy = p[1] - q[1];
        if (dx * dx + dy * dy < C.MERGE_PX * C.MERGE_PX) hit = kept[i];
      }
      if (!hit) { kept.push({ p: p, c: $.extend({}, c, { hist: c.hist.slice() }) }); return; }
      var k = hit.c, tot = k.size + c.size;
      k.lat = (k.lat * k.size + c.lat * c.size) / tot;
      k.lng = (k.lng * k.size + c.lng * c.size) / tot;
      k.size = tot;
      k.riskCount += c.riskCount;
      k.checkedCnt = (k.checkedCnt || 0) + (c.checkedCnt || 0);
      k.maxScore = Math.max(k.maxScore, c.maxScore);
      for (var j = 0; j < k.hist.length; j++) k.hist[j] += c.hist[j] || 0;
      hit.p = px(k);
    });
    return kept.map(function (k) { return k.c; });
  }
  function addMark(it) {
    var m = $.extend({ el: document.createElement('div'), mk: null }, it);
    if (m.kind === 'cluster') drawCluster(m);
    else { drawPin(m); pinById[m.data.bldId] = m; }
    marks[m.key] = m;
  }
  function removeMark(m) {
    m.mk.setMap(null);
    delete marks[m.key];
    if (m.kind === 'building' && pinById[m.data.bldId] === m) delete pinById[m.data.bldId];
  }
  /* items : [{ key, kind, data }] 를 지도에 반영. 키가 같은 마커는 그대로 둔다 */
  function setMarks(items) {
    if (noMap()) return;
    var next = {};
    items.forEach(function (it) { next[it.key] = it; });
    Object.keys(marks).forEach(function (k) { if (!next[k]) removeMark(marks[k]); });
    items.forEach(function (it) {
      var m = marks[it.key];
      if (m) m.data = it.data; else addMark(it);
    });
  }
  var pinItem = function (b, dot) { b.dot = !!dot; return { key: 'b|' + b.bldId + '|' + b.score + '|' + (b.useNm || '') + '|' + (dot ? 'd' : 'p') + (b.checked ? 'c' : ''), kind: 'building', data: b }; };
  var clusterItem = function (c) { return { key: 'c|' + c.lng.toFixed(6) + '|' + c.lat.toFixed(6) + '|' + c.size + '|' + c.riskCount + '|' + (c.checkedCnt || 0) + '|' + c.hist.join('.'), kind: 'cluster', data: c }; };

  /* 서버 응답 → 지도. cluster 모드 : 1동짜리 칸은 핀, 도넛은 70px 안이면 합침 / building 모드 : 점수 순 200개 핀 + 나머지 점 */
  function render(mode, list, risk) {
    riskOnly = risk !== false;
    var items;
    if (mode === 'cluster') {
      var pins = list.filter(function (c) { return c.point; }).map(function (c) { return pinItem(c.point, false); });
      var donuts = mergeClusters(list.filter(function (c) { return !c.point; })).map(clusterItem);
      items = pins.concat(donuts);
    } else {
      items = list.map(function (b, i) { return pinItem(b, i >= C.PIN_ICON_MAX); });
    }
    setMarks(items);
    refreshChecked(mode);
  }
  function clear() { setMarks([]); refreshChecked('gu'); }

  /* 선택이 바뀐 핀만 다시 그린다 */
  function select(bldId) {
    var prev = selectedId;
    selectedId = bldId;
    if (noMap()) return;
    [prev, bldId].forEach(function (id) { var m = id && pinById[id]; if (m) drawPin(m); });
  }

  /* ---------- 구 경계 + 라벨 (gu 모드에서만) ---------- */
  function renderGu(list) {
    if (noMap()) return;
    gus.forEach(function (g) { g.polygon.setMap(null); g.label.setMap(null); });
    gus = [];
    (list || []).forEach(function (g) {
      if (!g.ring || g.ring.length < 3) return;
      var col = C.COLORS[guLevelOf(g.riskCount).key];
      var polygon = new naver.maps.Polygon({
        paths: g.ring.map(function (p) { return LL(p[1], p[0]); }),
        strokeColor: col, strokeWeight: 2.5, strokeOpacity: 1, fillColor: col, fillOpacity: 0.22, zIndex: 1, clickable: true
      });
      var el = document.createElement('div');
      el.className = 'mk-inv kgu';
      el.style.borderColor = col;
      el.innerHTML = '<b>' + esc(g.name) + '</b>위험 ' + Common.num(g.riskCount) + '곳'
        + (g.checkedCnt > 0 ? '<small>현장 확인 ' + Common.num(g.checkedCnt) + '곳</small>' : '');   /* 9단계 : 구 원 안에 한 줄 */
      var label = new naver.maps.Marker({ position: LL(g.lat, g.lng), icon: { content: el, anchor: new naver.maps.Point(48, 48) }, zIndex: 5 });
      naver.maps.Event.addListener(polygon, 'click', function () { if (handlers.onGuClick) handlers.onGuClick(g); });
      naver.maps.Event.addListener(label, 'click', function () { if (handlers.onGuClick) handlers.onGuClick(g); });
      gus.push({ polygon: polygon, label: label });
    });
    guShown = false;
    showGu(handlers.mode ? handlers.mode() === 'gu' : true);
  }
  function showGu(show) {
    if (show === guShown) return;
    guShown = show;
    gus.forEach(function (g) { g.polygon.setMap(show ? map : null); g.label.setMap(show ? map : null); });
  }

  /* ---------- 현장 확인됨 (9단계) ----------
     구 · 묶음 모드 : 개별 표시 없음 (구 라벨 · 도넛 배지가 대신). 핀 모드 : 핀이 있는 건물은 핀의 체크 배지, 핀이 없는 건물만 작은 파란 체크 점 */
  function renderChecked(list) {
    checkedList = list || [];
    refreshChecked(handlers.mode ? handlers.mode() : 'gu');
  }
  function refreshChecked(mode) {
    if (noMap()) return;
    checked.forEach(function (mk) { mk.setMap(null); });
    checked = [];
    if (mode !== 'building') return;
    checkedList.forEach(function (b) {
      if (pinById[b.bldId]) return;                       // 이미 핀(또는 점)이 있는 건물은 그쪽 배지로
      var mk = new naver.maps.Marker({
        position: LL(b.lat, b.lng), map: map,
        icon: { content: '<div class="mk-inv mk-check-dot" title="현장 확인됨">✓</div>', anchor: new naver.maps.Point(8, 8) },
        zIndex: 55, title: '현장 확인됨 ' + (b.checkedAt || '') + ' · ' + Common.shortAddr(b.addr) + ' (점수 기준 아래)'
      });
      naver.maps.Event.addListener(mk, 'click', function () { if (handlers.onMarkClick) handlers.onMarkClick({ kind: 'building', data: b }); });
      checked.push(mk);
    });
  }

  function init(m, h) { map = m; handlers = h || {}; }

  return {
    init: init, render: render, clear: clear, select: select, renderGu: renderGu, showGu: showGu, renderChecked: renderChecked,
    levelOf: levelOf, clusterSvg: clusterSvg, pointSvg: pointSvg, svgUri: svgUri
  };
})();
