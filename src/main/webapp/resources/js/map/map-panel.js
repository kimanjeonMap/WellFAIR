/*
 * 오른쪽 "건물 상세 정보" 패널 (include/building-panel.jspf, Figma 02-2)
 *  - 점수 구성 · 건축물대장 · 복지 사각(가까운 시설 거리) · 거주 취약 가구(집계) · 메모 · 보관함 저장
 *  - [개인 정보 보기] → 취약 가구 정보 모달 (Figma 02-2-1) : 성만 보이는 세대주 · 층수 · 연령대 · 가구 구성 · 취약 유형 · 복지 연계
 */
window.MapPanel = (function () {
  var esc = Common.esc;
  var getCriteria = null, onArchived = null;
  var current = null;        // BuildingDetailTO

  /* 묶음 한 줄 (피그마 02-2) : "주거 공간 (반지하 · 옥탑)" · 막대 · "+45". 막대 = 받은 점수 ÷ 가장 큰 묶음 가중치 */
  function scoreRow(it, maxW) {
    var w = maxW ? Math.round(Math.min(it.points, maxW) / maxW * 100) : 0;
    return '<div class="sc-row' + (it.hit ? '' : ' miss') + '" title="가중치 ' + it.weight + ' · ' + esc(it.desc) + '">'
      + '<span class="sc-label">' + esc(it.label) + ' <small>(' + esc(it.desc) + ')</small></span>'
      + '<span class="sc-bar"><i style="width:' + w + '%"></i></span>'
      + '<b class="sc-pt">' + (it.hit ? '+' + it.points : (it.weight ? '0' : '-')) + '</b></div>';
  }

  function infoRow(k, v) { return '<tr><th>' + esc(k) + '</th><td>' + esc(v == null || v === '' ? '-' : v) + '</td></tr>'; }

  function distText(m) { return m == null ? '-' : m >= 1000 ? (m / 1000).toFixed(1) + 'km' : Math.round(m) + 'm'; }
  function welfRow(k, nm, dist) {
    return '<div class="welf-row"><span class="welf-k">' + esc(k) + '</span><span class="welf-nm">' + esc(nm || '-') + '</span><b class="welf-d">' + distText(dist) + '</b></div>';
  }

  function titleOf(b) {
    return b.bldNm ? Common.shortAddr(b.addr) + ' ' + b.bldNm : Common.shortAddr(b.addr) + (b.useNm ? ' ' + b.useNm : '');
  }

  function renderInfo(d) {
    var b = d.building;
    $('#dTitle').text(titleOf(b));
    $('#dAddr').text(b.addr);
    $('#dScore').text(b.score + '점');
    $('#dChecked').toggle(!!b.checkedAt).text('현장 확인됨 ' + (b.checkedAt || '').slice(5));
    var maxW = d.scoreItems.reduce(function (m, it) { return Math.max(m, it.weight || 0); }, 0);
    $('#dScoreItems').html(d.scoreItems.map(function (it) { return scoreRow(it, maxW); }).join(''));

    var floors = (b.grndFlr != null ? '지상 ' + b.grndFlr + '층' : '') + (b.ugrndFlr ? ' / 지하 ' + b.ugrndFlr + '층' : '');
    var apr = b.useaprDay && b.useaprDay.length === 8 ? b.useaprDay.slice(0, 4) + '.' + b.useaprDay.slice(4, 6) + '.' + b.useaprDay.slice(6) + (b.bldAge != null ? ' (' + b.bldAge + '년)' : '') : '자료 없음';
    /* 건축물대장에 있는 값만 */
    $('#dInfo').html(
      infoRow('주용도', b.useNm || '미상') + infoRow('층수', floors || '-') + infoRow('사용승인', apr)
      + infoRow('위반건축물', b.violYn === '1' ? '해당' : b.violYn === '0' ? '없음' : '대장 미연계')
      + infoRow('세대수', b.units != null ? b.units + '세대' : '-')
    );
    /* 복지 사각 : 가장 가까운 시설까지 거리 */
    $('#dWelf').html(welfRow('복지시설', b.welfNm, b.welfDist) + welfRow('병원', b.hospNm, b.hospDist) + welfRow('정류장', b.busNm, b.busDist));

    /* 거주 취약 가구 (02-2) : 세대 수 + 많은 유형 3개 */
    var rc = d.residents || {};
    $('#dVulnCnt').html('취약 가구 ' + (rc.vulnHh || 0) + '세대' + (rc.hhCnt ? ' <small>· 전체 ' + rc.hhCnt + '가구 ' + rc.mbrCnt + '명</small>' : ''));
    $('#dVulnTypes').text(rc.vulnTypes || (rc.hhCnt ? '취약 유형 없음' : '거주 가구 자료 없음'));
    $('#btnResidents').prop('disabled', !rc.hhCnt);
    $('#dMemo').val('');
    $('#btnArchive').prop('disabled', false).text(b.archiveCnt > 0 ? '보관함에 저장됨 (점수 갱신)' : '보관함에 저장');
  }

  /* ---------- 취약 가구 정보 모달 (02-2-1) ---------- */
  function ageBand(age) { return age == null ? '-' : age < 10 ? '10세 미만' : (Math.floor(age / 10) * 10) + '대'; }
  function household(r) {
    var t = r.hhType || '';
    if (t === '1인') return '1인 가구';
    return t + (r.hhMbrCnt ? ' · ' + r.hhMbrCnt + '인' : '');
  }
  /* 복지 연계 = 원본 값 그대로 (5-1). 임의 규칙("상담 필요" 등) 없음
   *  - 가구원 중 지원연계(support_type: 공적급여 / 민간자원 / 기타서비스)가 있으면 "{값} 연계"
   *  - 없고 처리결과(detect_result: 조치완료 / 단순상담 / 미처리 / 요청접수 / 미지원 …)가 있으면 그 값
   *  - 둘 다 없으면 "이력 없음" */
  function welfare(r) {
    if (r.supportType) return { c: 'ok', t: r.supportType + ' 연계' };
    if (r.detectResult) return { c: 'blue', t: r.detectResult };
    return { c: 'none', t: '이력 없음' };
  }
  function rmRow(r) {
    var w = welfare(r);
    var tags = (r.tags || []).map(function (t) { return '<span class="tag">' + esc(t) + '</span>'; }).join('')
      + (r.benefits || []).map(function (t) { return '<span class="tag benefit" title="수급 확인용 (이미 복지 체계 안)">' + esc(t) + '</span>'; }).join('');
    return '<tr><td class="rm-flr">' + esc(r.resFlr || '-') + '</td><td>' + esc(r.headNm) + '</td><td>' + ageBand(r.maxAge) + '</td>'
      + '<td>' + esc(household(r)) + '</td><td>' + (tags || '<span class="muted">-</span>') + '</td>'
      + '<td><i class="rm-dot ' + w.c + '"></i>' + esc(w.t) + '</td></tr>';
  }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function openResidents() {
    if (!current) return;
    var b = current.building, now = new Date();
    $('#rmSub').text(titleOf(b) + ' · ' + b.addr);
    $('#rmViewer').text(($('#resModal').data('viewer') || '').trim() + ' · ' + now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()) + ' ' + pad(now.getHours()) + ':' + pad(now.getMinutes()));
    $('#rmRows').html('<tr><td colspan="6" class="rs-empty">불러오는 중…</td></tr>');
    $('#resModal').addClass('open');
    MapApi.residents(b.bldId).done(function (list) {
      var vuln = list.filter(function (r) { return r.priority >= 10; });   // 취약 가구 = 가구 가점 10 이상 (5-1)
      $('#rmRows').html(vuln.length ? vuln.map(rmRow).join('') : '<tr><td colspan="6" class="rs-empty">취약 유형에 해당하는 가구가 없습니다. (전체 ' + list.length + '가구)</td></tr>');
    });
  }
  function closeResidents() { $('#resModal').removeClass('open'); }

  function show(bldId) {
    current = null;
    $('#panelRight').addClass('open');
    $('#resultView').hide(); $('#detailView').show();
    $('#dTitle').text('불러오는 중…'); $('#dAddr').text(''); $('#dScoreItems, #dInfo, #dWelf').empty();
    MapApi.building($.extend({}, getCriteria(), { bldId: bldId })).done(function (d) {
      if (!d || !d.building) { Common.toast('건물 정보를 찾을 수 없습니다.'); return; }
      current = d;
      renderInfo(d);
    });
  }

  function init(opts) {
    getCriteria = opts.getCriteria;
    onArchived = opts.onArchived;

    $('#btnResidents').on('click', openResidents);
    $('#rmClose, #rmClose2').on('click', closeResidents);
    $('#resModal').on('click', function (e) { if (e.target === this) closeResidents(); });
    $(document).on('keydown', function (e) { if (e.key === 'Escape') closeResidents(); });

    $('#btnArchive').on('click', function () {
      if (!current) return;
      var $b = $(this).prop('disabled', true);
      MapApi.archiveAdd(current.building.bldId, current.building.sggCd, current.building.score, getCriteria()).done(function (res) {
        if (res && res.ok === false) { Common.toast(res.msg || '저장할 수 없습니다.'); return; }
        Common.toast('보관함에 저장했습니다.');
        $b.text('보관함에 저장됨');
        current.building.archiveCnt = 1;
        if (onArchived) onArchived(res.statusCount);
      }).always(function () { $b.prop('disabled', false); });
    });

    $('#btnWhy').on('click', function () { Common.toast('기준 가이드(LLM)는 연동 예정입니다.'); });
    $('#detailBack').on('click', function () { $('#detailView').hide(); $('#resultView').show(); });
    $('#detailClose, #resultClose').on('click', function () { $('#panelRight').removeClass('open'); if (opts.onClose) setTimeout(opts.onClose, 280); });
  }

  return { init: init, show: show };
})();
