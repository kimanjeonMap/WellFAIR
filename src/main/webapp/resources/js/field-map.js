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
  var SPEED = { walk: 4, transit: 12, car: 20 };   // km/h

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

  return { GU_OFFICE: GU_OFFICE, ROAD_FACTOR: ROAD_FACTOR, SPEED: SPEED, haversineKm: haversineKm, pathKm: pathKm, optimizeRoute: optimizeRoute, minutes: minutes };
})();
