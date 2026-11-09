/* 현장 확인 상세 (fielddetail.jsp) : 출발 핀 + 번호 핀 + 점선 경로. 앱 보내기 버튼은 연동 예정
 *  - 소요시간 카드의 이동수단 3칸은 다 누를 수 있다. 누르면 api/field/mode.do 로 survey_list.move_mode 저장 후
 *    총 소요 · 예상 종료 · 구간별 분을 그 수단(도보 4 / 대중교통 15 / 자동차 20 km/h, 직선 × 1.3)으로 다시 계산 (5-1) */
$(function () {
  var items = window.FD_ITEMS || [], start = window.FD_START, FM = window.FieldMap, R = window.FD_ROUTE || {};
  /* 현장 확인 앱으로 보내기 버튼은 나중에 따로 만든다 (지금은 [현장 확인 리스트로 돌아가기]) */

  /* ---------- 이동수단 전환 ---------- */
  var loadRoute = function () {};   // 지도가 준비되면 아래에서 채운다 (TMAP 경로 다시 받기)
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function applyMode(mode) {
    $('#routeModes > div').removeClass('on').filter('[data-mode="' + mode + '"]').addClass('on');
    var move = FM.minutes(R.km || 0, mode), total = move + (R.checkMin || 0), end = 9 * 60 + 30 + total;
    $('#rtTotal').text(total >= 60 ? Math.floor(total / 60) + '시간 ' + (total % 60) + '분' : total + '분');
    $('#rtEnd').text('09:30 → ' + pad(Math.floor(end / 60) % 24) + ':' + pad(end % 60));
    $('.leg-min').each(function () { $(this).text(FM.minutes(Number($(this).data('km')) || 0, mode)); });
  }
  $('#routeModes').on('click', '> div', function () {
    var mode = $(this).data('mode');
    if (mode === R.mode) return;
    Common.api('POST', 'api/field/mode.do', { listId: R.listId, moveMode: mode }).done(function (res) {
      if (!res || !res.ok) { Common.toast('이동수단을 저장하지 못했습니다.'); return; }
      R.mode = mode;
      applyMode(mode);
      loadRoute(mode);
      Common.toast({ walk: '도보', transit: '대중교통', car: '자동차' }[mode] + ' 기준으로 저장했습니다.');
    });
  });
  if (R.mode) applyMode(R.mode);

  if (!(window.naver && naver.maps && naver.maps.Map)) return;
  var c = items[0] || start || { lat: 37.495, lng: 126.858 };
  var map = new naver.maps.Map('map', { center: new naver.maps.LatLng(c.lat, c.lng), zoom: 16, zoomControl: false, mapDataControl: false, scaleControl: false });
  /* 확대 · 축소 버튼 (fielddetail.jsp .map-ctrl) */
  $('#zoomIn').on('click', function () { if (map) map.setZoom(map.getZoom() + 1, true); });
  $('#zoomOut').on('click', function () { if (map) map.setZoom(map.getZoom() - 1, true); });
  var prevFail = window.navermap_authFailure;
  window.navermap_authFailure = function () { map = null; if (prevFail) prevFail(); };

  var path = [];
  if (start) {
    new naver.maps.Marker({ position: new naver.maps.LatLng(start.lat, start.lng), map: map, zIndex: 90, title: '출발 · ' + start.name,
      icon: { content: '<div class="mk-inv start-pin">출발</div>', anchor: new naver.maps.Point(16, 16) } });
    path.push(new naver.maps.LatLng(start.lat, start.lng));
  }
  items.forEach(function (it, i) {
    new naver.maps.Marker({ position: new naver.maps.LatLng(it.lat, it.lng), map: map, zIndex: 100 + i, title: it.title,
      icon: { content: '<div class="mk-inv num-pin' + (it.checked ? ' done' : '') + '">' + (it.checked ? '✓' : it.order) + '</div>', anchor: new naver.maps.Point(14, 14) } });
    path.push(new naver.maps.LatLng(it.lat, it.lng));
  });
  if (path.length > 1) {
    var dash = new naver.maps.Polyline({ map: map, path: path, strokeColor: '#4a8fe0', strokeWeight: 4, strokeStyle: 'shortdash', strokeOpacity: 0.95, zIndex: 50 });
    /* 경로 (TMAP) : 고른 이동수단으로 받아서 점선을 바꾼다. 이동수단을 바꾸면 다시 받는다 (같은 구간은 서버가 기억). 실패하면 점선 그대로 + 안내 */
    var $walkInfo = $('<div class="map-float tc walk-info"></div>').appendTo('.fd-map');
    var pts = (start ? [start] : []).concat(items).map(function (p) { return { lat: p.lat, lng: p.lng }; });
    var routeLine = null, routeSeq = 0;
    loadRoute = function (mode) {
      var my = ++routeSeq;
      $walkInfo.text('경로 찾는 중…').show();
      FM.walkPath(pts, mode).done(function (r) {
        if (my !== routeSeq || !map) return;
        dash.setMap(null);
        if (routeLine) routeLine.setMap(null);
        routeLine = FM.drawWalk(map, r, 5);
        $walkInfo.html(FM.walkText(r));
      }).fail(function (msg) {
        if (my !== routeSeq) return;
        if (routeLine) { routeLine.setMap(null); routeLine = null; }
        dash.setMap(map);
        if (msg) $walkInfo.text(msg + ' (직선으로 표시)'); else $walkInfo.hide();
      });
    };
    loadRoute(R.mode || 'walk');
    var b = new naver.maps.LatLngBounds(path[0], path[0]);
    path.forEach(function (p) { b.extend(p); });
    map.fitBounds(b, { top: 40, right: 400, bottom: 40, left: 40 });
  }
});
