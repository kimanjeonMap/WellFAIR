/* 현장 확인 상세 (fielddetail.jsp) : 출발 핀 + 번호 핀 + 점선 경로. 앱 보내기 버튼은 연동 예정
 *  - 소요시간 카드의 이동수단 3칸은 다 누를 수 있다. 누르면 api/field/mode.do 로 survey_list.move_mode 저장 후
 *    총 소요 · 예상 종료 · 구간별 분을 그 수단(도보 4 / 대중교통 15 / 자동차 20 km/h, 직선 × 1.3)으로 다시 계산 (5-1) */
$(function () {
  var items = window.FD_ITEMS || [], start = window.FD_START, FM = window.FieldMap, R = window.FD_ROUTE || {};
  $('#btnSendApp').on('click', function () { Common.toast('현장 확인 앱 연동 예정입니다.'); });

  /* ---------- 이동수단 전환 ---------- */
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
      Common.toast({ walk: '도보', transit: '대중교통', car: '자동차' }[mode] + ' 기준으로 저장했습니다.');
    });
  });
  if (R.mode) applyMode(R.mode);

  if (!(window.naver && naver.maps && naver.maps.Map)) return;
  var c = items[0] || start || { lat: 37.495, lng: 126.858 };
  var map = new naver.maps.Map('map', { center: new naver.maps.LatLng(c.lat, c.lng), zoom: 16, zoomControl: true, zoomControlOptions: { position: naver.maps.Position.BOTTOM_RIGHT }, mapDataControl: false, scaleControl: false });
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
    new naver.maps.Polyline({ map: map, path: path, strokeColor: '#4a8fe0', strokeWeight: 4, strokeStyle: 'shortdash', strokeOpacity: 0.95, zIndex: 50 });
    var b = new naver.maps.LatLngBounds(path[0], path[0]);
    path.forEach(function (p) { b.extend(p); });
    map.fitBounds(b, { top: 40, right: 400, bottom: 40, left: 40 });
  }
});
