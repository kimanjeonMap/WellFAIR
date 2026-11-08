/* 현장 확인 상세 (fielddetail.jsp) : 출발 핀 + 번호 핀 + 점선 경로. 앱 보내기 버튼은 연동 예정 */
$(function () {
  var items = window.FD_ITEMS || [], start = window.FD_START, FM = window.FieldMap;
  $('#btnSendApp').on('click', function () { Common.toast('현장 확인 앱 연동 예정입니다.'); });

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
