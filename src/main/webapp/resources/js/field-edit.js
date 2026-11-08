/*
 * 현장확인 리스트 수정 (fieldedit.jsp)
 *  - 왼쪽 체크 / 지도 빈 원 클릭 → 담기, 오른쪽 "빼기" → 빼기, 끌어서 순서 바꾸기
 *  - [최적 동선으로 정렬] : FieldMap.optimizeRoute(출발점, 미확인 건물) — 확인 완료 건물은 앞에 그대로 둔다
 *  - 출발점 : 구청(선택, 기본 = 첫 건물의 구). 저장되지 않고 순서 계산·시간 표시에만 쓴다
 *  - 지도 : 출발 핀 + 번호 핀 + 점선, 나머지 보관함 건물은 빈 원
 */
$(function () {
  var D = window.FE_DATA, esc = Common.esc, FM = window.FieldMap;
  var items = D.items.slice();            // [{factorId, bldId, title, lat, lng, score, checked, sggCd}]
  var byFactor = {};
  D.pick.forEach(function (p) { byFactor[p.factorId] = p; });
  var mode = ($('#modeSeg button.on').data('mode')) || 'walk';   // 저장된 이동수단 (survey_list.move_mode)

  /* 출발점 (구청) */
  var $start = $('#startSel');
  var firstGu = D.gu || (items[0] && items[0].sggCd) || (D.pick[0] && D.pick[0].sggCd) || '11530';
  $start.val(FM.GU_OFFICE[firstGu] ? firstGu : '11530');
  if (D.lockGu) $start.prop('disabled', true).attr('title', '소속 구청으로 고정됩니다');   // 구청 계정
  function start() { var g = FM.GU_OFFICE[$start.val()] || FM.GU_OFFICE['11530']; return { lat: g.lat, lng: g.lng, name: g.name }; }

  /* ---------- 지도 ---------- */
  var hasMap = !!(window.naver && naver.maps && naver.maps.Map);
  var map = null, line = null, marks = [], pop = null;
  if (hasMap) {
    var c = items[0] || D.pick[0] || start();
    map = new naver.maps.Map('map', { center: new naver.maps.LatLng(c.lat, c.lng), zoom: 15, zoomControl: true, zoomControlOptions: { position: naver.maps.Position.TOP_RIGHT }, mapDataControl: false, scaleControl: false });
  }
  var live = function () { return !!(map && window.naver && naver.maps); };
  var prevFail = window.navermap_authFailure;
  window.navermap_authFailure = function () { map = null; if (prevFail) prevFail(); };

  function has(factorId) { return items.some(function (it) { return it.factorId === factorId; }); }

  function render() {
    var s = start(), prev = s, total = 0;
    var html = items.map(function (it, i) {
      var leg = FM.haversineKm(prev, it); total += leg; prev = it;
      var min = FM.minutes(leg, mode);
      return '<div class="fe-item" data-factor="' + it.factorId + '" draggable="true">'
        + '<span class="drag" title="끌어서 순서 바꾸기">⋮⋮</span>'
        + '<span class="num' + (it.checked ? ' done' : '') + '">' + (it.checked ? '✓' : (i + 1)) + '</span>'
        + '<span>' + esc(it.title) + ' <span class="muted">' + it.score + '점</span></span>'
        + '<span class="leg">' + (i === 0 ? esc(s.name) + '에서 ' : '이전 지점에서 ') + min + '분 (' + leg.toFixed(1) + 'km)</span>'
        + (it.checked ? '<span class="muted" style="font-size:12px">확인 완료</span>' : '<button type="button" class="rm">빼기 ×</button>') + '</div>';
    }).join('');
    $('#feItems').html(html || '<div class="empty">왼쪽에서 건물을 체크하거나 지도의 빈 원을 눌러 담으세요.</div>');
    $('#orderIds').html(items.map(function (it) { return '<input type="hidden" name="factorIds" value="' + it.factorId + '">'; }).join(''));
    $('#mineCnt').text(items.length);
    $('#routeCap').text(s.name + ' 출발 · ' + items.length + '곳 · 직선 ' + total.toFixed(1) + 'km × 1.3');
    $('#tWalk').text(FM.minutes(total, 'walk'));
    $('#tCar').text(FM.minutes(total, 'car'));
    $('#tTransit').text(FM.minutes(total, 'transit'));
    $('#btnOptimize').prop('disabled', items.filter(function (it) { return !it.checked; }).length < 2);
    $('.pick-chk').each(function () { this.checked = has(Number(this.value)); });
    drawMap();
  }

  function drawMap() {
    if (!live()) return;
    marks.forEach(function (m) { m.setMap(null); }); marks = [];
    if (line) { line.setMap(null); line = null; }
    var s = start(), onlyMine = $('#onlyMine').is(':checked');
    marks.push(new naver.maps.Marker({ position: new naver.maps.LatLng(s.lat, s.lng), map: map, zIndex: 90, title: '출발 · ' + s.name,
      icon: { content: '<div class="mk-inv start-pin">출발</div>', anchor: new naver.maps.Point(16, 16) } }));
    if (!onlyMine) D.pick.forEach(function (p) {
      if (has(p.factorId)) return;
      var mk = new naver.maps.Marker({ position: new naver.maps.LatLng(p.lat, p.lng), map: map, zIndex: 10, title: p.title + ' · ' + p.score + '점',
        icon: { content: '<div class="mk-inv hollow-pin"></div>', anchor: new naver.maps.Point(9, 9) } });
      naver.maps.Event.addListener(mk, 'click', function () { showPop(p); });
      marks.push(mk);
    });
    items.forEach(function (it, i) {
      marks.push(new naver.maps.Marker({ position: new naver.maps.LatLng(it.lat, it.lng), map: map, zIndex: 100 + i, title: it.title,
        icon: { content: '<div class="mk-inv num-pin' + (it.checked ? ' done' : '') + '">' + (it.checked ? '✓' : (i + 1)) + '</div>', anchor: new naver.maps.Point(14, 14) } }));
    });
    if (items.length) {
      var path = [new naver.maps.LatLng(s.lat, s.lng)].concat(items.map(function (it) { return new naver.maps.LatLng(it.lat, it.lng); }));
      line = new naver.maps.Polyline({ map: map, path: path, strokeColor: '#4a8fe0', strokeWeight: 3, strokeStyle: 'shortdash', strokeOpacity: 0.9, zIndex: 50 });
    }
  }

  /* 빈 원 → 담기 말풍선 */
  function showPop(p) {
    if (pop) pop.remove();
    var pt = map.getProjection().fromCoordToOffset(new naver.maps.LatLng(p.lat, p.lng));
    pop = $('<div class="map-pop"><b>' + esc(p.title) + '</b><span class="muted">' + esc(p.factor || '요인 없음') + ' · ' + p.score + '점 · ' + (p.surveyId ? '다른 리스트에 있음' : '리스트에 없음') + '</span><br>'
      + '<button type="button" class="btn btn-primary btn-sm add">+ 이 리스트에 담기</button> <button type="button" class="btn btn-line btn-sm close">닫기</button></div>')
      .css({ left: pt.x + 14, top: pt.y - 20 }).appendTo('.fe-map');
    pop.find('.add').on('click', function () { add(p.factorId); pop.remove(); pop = null; });
    pop.find('.close').on('click', function () { pop.remove(); pop = null; });
  }

  function add(factorId) {
    if (has(factorId)) return;
    var p = byFactor[factorId]; if (!p) return;
    /* 한 리스트 = 한 구 (CLAUDE.md 5-1). 서버(FieldService.oneGu)도 같은 검사를 한다 */
    var gu = D.gu || (items[0] && items[0].sggCd);
    if (gu && p.sggCd && p.sggCd !== gu) {
      Common.toast('현장확인 리스트에는 한 구 안의 건물만 담을 수 있어요. 이 리스트는 ' + (FM.GU_OFFICE[gu] ? FM.GU_OFFICE[gu].name.replace('청', '') : gu) + ' 리스트입니다.');
      $('.pick-chk[value="' + factorId + '"]').prop('checked', false);
      return;
    }
    items.push({ factorId: p.factorId, bldId: p.bldId, title: p.title, lat: p.lat, lng: p.lng, score: p.score, checked: false, sggCd: p.sggCd });
    render();
  }
  function remove(factorId) {
    items = items.filter(function (it) { return it.factorId !== factorId; });
    render();
  }

  /* 최적 동선 : 확인 완료 건물은 앞에 그대로, 나머지를 마지막 확인 지점(없으면 출발점)에서 최적화 */
  function optimize() {
    var fixed = items.filter(function (it) { return it.checked; });
    var rest = items.filter(function (it) { return !it.checked; });
    var from = fixed.length ? fixed[fixed.length - 1] : start();
    var before = FM.pathKm(start(), items);
    items = fixed.concat(FM.optimizeRoute(from, rest));
    var after = FM.pathKm(start(), items);
    render();
    Common.toast('최적 동선으로 정렬했습니다. ' + before.toFixed(1) + 'km → ' + after.toFixed(1) + 'km (직선거리)');
  }

  /* 끌어서 순서 바꾸기 (HTML5 drag & drop) */
  var dragId = null;
  $('#feItems').on('dragstart', '.fe-item', function (e) { dragId = Number($(this).data('factor')); $(this).addClass('dragging'); e.originalEvent.dataTransfer.effectAllowed = 'move'; })
    .on('dragend', '.fe-item', function () { $(this).removeClass('dragging'); $('.fe-item').removeClass('over'); })
    .on('dragover', '.fe-item', function (e) { e.preventDefault(); $('.fe-item').removeClass('over'); $(this).addClass('over'); })
    .on('drop', '.fe-item', function (e) {
      e.preventDefault();
      var toId = Number($(this).data('factor'));
      if (dragId == null || dragId === toId) return;
      var from = items.findIndex(function (it) { return it.factorId === dragId; });
      var to = items.findIndex(function (it) { return it.factorId === toId; });
      var moved = items.splice(from, 1)[0];
      items.splice(to, 0, moved);
      dragId = null;
      render();
    });

  $('#pickList').on('change', '.pick-chk', function () { if (this.checked) add(Number(this.value)); else remove(Number(this.value)); });
  $('#feItems').on('click', '.rm', function () { remove(Number($(this).closest('.fe-item').data('factor'))); });
  $('#modeSeg button').on('click', function () {
    $('#modeSeg button').removeClass('on'); $(this).addClass('on');
    mode = $(this).data('mode');
    $('#moveMode').val(mode.toUpperCase());      // 저장 시 survey_list.move_mode
    render();
  });
  $('#btnOptimize').on('click', optimize);
  $start.on('change', render);
  $('#onlyMine').on('change', drawMap);
  if (live()) naver.maps.Event.addListener(map, 'dragstart', function () { if (pop) { pop.remove(); pop = null; } });

  $('#editForm').on('submit', function () {
    if (!$.trim($('#title').val())) { Common.toast('리스트 이름을 입력하세요.'); $('#title').trigger('focus'); return false; }
  });

  render();
});
