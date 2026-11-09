/*
 * 현장확인 경로 계산 (03-1 수정 · 03-2 상세 공용). 서버 키 없이 브라우저에서 직선거리(하버사인)로.
 *  optimizeRoute(start, points) : 출발점에서 가장 가까운 건물부터 고르는 nearest-neighbor 로 초안을 만들고,
 *                                 2-opt 로 교차하는 구간을 풀어 개선한다. 편도(돌아오는 길 없음). 입력 배열은 바꾸지 않는다.
 *  GU_OFFICE : 기본 출발점 = 해당 구청 좌표
 *  ROAD_FACTOR : 소요시간 = 총 직선거리 × 1.3(도로 보정) ÷ 평균속도
 */
window.FieldMap = (function () {
  var GU_OFFICE = {
    '11530': { name: '구로구청',   lat: 37.4954, lng: 126.8874 },
    '11470': { name: '양천구청',   lat: 37.5170, lng: 126.8664 },
    '11560': { name: '영등포구청', lat: 37.5264, lng: 126.8962 }
  };
  var ROAD_FACTOR = 1.3;
  var SPEED = { walk: 4, transit: 15, car: 20 };   // km/h (FieldService 와 같은 값)

  function haversineKm(a, b) {
    var R = 6371.0088, toRad = Math.PI / 180;
    var dLat = (b.lat - a.lat) * toRad, dLng = (b.lng - a.lng) * toRad;
    var x = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(a.lat * toRad) * Math.cos(b.lat * toRad) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.asin(Math.sqrt(x));
  }

  /* 출발점 → 순서대로 방문했을 때 총 거리 (편도) */
  function pathKm(start, points) {
    var km = 0, prev = start;
    for (var i = 0; i < points.length; i++) { km += haversineKm(prev, points[i]); prev = points[i]; }
    return km;
  }

  function optimizeRoute(start, points) {
    var n = points.length;
    if (n < 2) return points.slice();

    /* 1) nearest-neighbor 초안 */
    var left = points.slice(), route = [], cur = start;
    while (left.length) {
      var best = 0, bestD = Infinity;
      for (var i = 0; i < left.length; i++) {
        var d = haversineKm(cur, left[i]);
        if (d < bestD) { bestD = d; best = i; }
      }
      cur = left.splice(best, 1)[0];
      route.push(cur);
    }

    /* 2) 2-opt : 구간 [i..k] 를 뒤집어서 전체 거리가 줄면 채택. 더 이상 줄지 않을 때까지 (수십 동이면 즉시 끝난다) */
    var improved = true, guard = 0;
    while (improved && guard++ < 200) {
      improved = false;
      for (var a = 0; a < n - 1; a++) {
        for (var k = a + 1; k < n; k++) {
          var pa = a === 0 ? start : route[a - 1];     // 구간 앞 점
          var before = haversineKm(pa, route[a]) + (k + 1 < n ? haversineKm(route[k], route[k + 1]) : 0);
          var after  = haversineKm(pa, route[k]) + (k + 1 < n ? haversineKm(route[a], route[k + 1]) : 0);
          if (after < before - 1e-9) {
            var seg = route.slice(a, k + 1).reverse();
            for (var j = 0; j < seg.length; j++) route[a + j] = seg[j];
            improved = true;
          }
        }
      }
    }
    return route;
  }

  function minutes(km, modeKey) { return Math.round(km * ROAD_FACTOR / SPEED[modeKey] * 60); }

  /* ---------- TMAP 경로 (03-1 수정 · 03-2 상세 공용) ----------
   *  서버 api/field/walk.do (RouteService) 가 구간마다 TMAP 을 불러 이어 붙인다. appKey 는 서버에만.
   *  walkPath(points, mode) : points = [출발, 1, 2, …] ({lat, lng}), mode = walk | transit | car
   *    → promise { mode, totalDistance(m), totalTime(초), legs:[{distance, time, straight, summary}], segments:[{mode, route, path}], path }
   *    대중교통 : 800m 안쪽 구간 · 대중교통 결과가 없는 구간은 서버가 걸어가는 경로로 채운다
   *    실패(appKey 없음 등)는 토스트 없이 reject(msg) : 화면은 기존 점선을 그대로 둔다 */
  var MODE_NAME = { walk: '도보', transit: '대중교통', car: '자동차' };
  function walkPath(points, mode) {
    var dfd = $.Deferred(), nm = MODE_NAME[mode] || '도보';
    if (!points || points.length < 2) return dfd.reject('').promise();
    $.ajax({
      url: Common.url('api/field/walk.do'), method: 'POST', dataType: 'json', traditional: true, timeout: 45000,
      data: { mode: mode || 'walk', lngs: points.map(function (p) { return p.lng; }), lats: points.map(function (p) { return p.lat; }) }
    }).done(function (r) {
      if (r && r.path && r.path.length) dfd.resolve(r); else dfd.reject((r && r.msg) || nm + ' 경로를 찾지 못했습니다.');
    }).fail(function (xhr) {
      dfd.reject((xhr.responseJSON && xhr.responseJSON.msg) || (xhr.statusText === 'timeout' ? nm + ' 경로 응답이 너무 늦습니다.' : nm + ' 경로를 불러오지 못했습니다. (' + xhr.status + ')'));
    });
    return dfd.promise();
  }

  /* 수단별 선 색 : 걷기 = 파랑(기존 점선과 같은 색), 버스 = 초록, 지하철 · 기차 = 보라, 자동차 = 주황 */
  var SEG_COLOR = { WALK: '#4a8fe0', CAR: '#f08c2e', BUS: '#2fb35a', EXPRESSBUS: '#2fb35a', SUBWAY: '#a05cdb', TRAIN: '#a05cdb' };
  /* 경로 선 그리기. 반환값 = { setMap(null) } 하나로 전부 지운다 (기존 Polyline 처럼 쓰려고) */
  function drawWalk(map, r, weight) {
    var segs = r.segments && r.segments.length ? r.segments : [{ mode: 'WALK', path: r.path }];
    var lines = segs.map(function (sg) {
      var ride = sg.mode !== 'WALK' && sg.mode !== 'CAR';
      return new naver.maps.Polyline({
        map: map, path: sg.path.map(function (c) { return new naver.maps.LatLng(c[1], c[0]); }),
        strokeColor: SEG_COLOR[sg.mode] || '#f08c2e', strokeWeight: (weight || 4) + (ride ? 2 : 0), strokeOpacity: 0.95,
        strokeStyle: r.mode === 'transit' && sg.mode === 'WALK' ? 'shortdot' : 'solid',   // 대중교통 안에서 걷는 부분은 점선
        strokeLineCap: 'round', strokeLineJoin: 'round', zIndex: ride ? 52 : 50
      });
    });
    return { setMap: function (m) { lines.forEach(function (l) { l.setMap(m); }); } };
  }

  function distText(m) { return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : Math.round(m) + 'm'; }
  function timeText(s) { var m = Math.max(1, Math.round(s / 60)); return m >= 60 ? Math.floor(m / 60) + '시간 ' + (m % 60) + '분' : m + '분'; }
  /* 지도 위 안내 : "대중교통 경로 (TMAP) 3.1km · 약 24분" + 대중교통이면 탄 노선 한 줄 */
  function walkText(r) {
    var approx = (r.legs || []).some(function (l) { return l.straight; });
    var html = (MODE_NAME[r.mode] || '도보') + ' 경로 (TMAP) ' + distText(r.totalDistance) + ' · 약 ' + timeText(r.totalTime) + (approx ? ' <small>(일부 구간 직선)</small>' : '');
    if (r.mode === 'transit') {
      var rides = (r.legs || []).map(function (l) { return l.summary; }).filter(function (t) { return t && t !== '도보'; });
      html += '<small class="walk-sub">' + (rides.length ? Common.esc(rides.join(' · ')) : '가까운 구간이라 걸어서 이동') + '</small>';
    }
    return html;
  }

  return { GU_OFFICE: GU_OFFICE, ROAD_FACTOR: ROAD_FACTOR, SPEED: SPEED, haversineKm: haversineKm, pathKm: pathKm, optimizeRoute: optimizeRoute, minutes: minutes,
           walkPath: walkPath, drawWalk: drawWalk, walkText: walkText };
})();
