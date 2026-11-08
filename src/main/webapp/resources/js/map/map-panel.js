/*
 * 오른쪽 "건물 상세 정보" 패널 (include/building-panel.jspf, Figma 02-2)
 *  - 점수 구성 · 건축물대장 · 거주 취약 가구(집계) · 메모 · 보관함 저장
 *  - [개인 정보 보기] → 취약 가구 정보 모달 (Figma 02-2-1) : 성만 보이는 세대주 · 층수 · 연령대 · 가구 구성 · 취약 유형 · 복지 연계
 */
window.MapPanel = (function () {
  var esc = Common.esc;
  var getCriteria = null, onArchived = null;
  var current = null;        // BuildingDetailTO

  function scoreRow(it) {
    var max = 30, w = Math.round(Math.min(it.points, max) / max * 100);
    return '<div class="sc-row' + (it.hit ? '' : ' miss') + '" title="' + esc(it.desc) + '">'
      + '<span class="sc-label">' + esc(it.label) + '</span>'
      + '<span class="sc-bar"><i style="width:' + w + '%"></i></span>'
      + '<b class="sc-pt">' + (it.hit ? '+' + it.points : (it.weight ? '0' : '-')) + '</b></div>';
  }

  function infoRow(k, v) { return '<tr><th>' + esc(k) + '</th><td>' + esc(v == null || v === '' ? '-' : v) + '</td></tr>'; }

  function titleOf(b) {
    return b.bldNm ? Common.shortAddr(b.addr) + ' ' + b.bldNm : Common.shortAddr(b.addr) + (b.useNm ? ' ' + b.useNm : '');
  }

  function renderInfo(d) {
    var b = d.building;
    $('#dTitle').text(titleOf(b));
    $('#dAddr').text(b.addr);
    $('#dScore').text(b.score + '점');
    $('#dChecked').toggle(!!b.checkedAt).text('현장 확인됨 ' + (b.checkedAt || '').slice(5));
    $('#dScoreItems').html(d.scoreItems.map(scoreRow).join(''));

    var floors = (b.grndFlr != null ? '지상 ' + b.grndFlr + '층' : '') + (b.ugrndFlr ? ' / 지하 ' + b.ugrndFlr + '층' : '');
    var apr = b.useaprDay && b.useaprDay.length === 8 ? b.useaprDay.slice(0, 4) + '.' + b.useaprDay.slice(4, 6) + '.' + b.useaprDay.slice(6) + (b.bldAge != null ? ' (' + b.bldAge + '년)' : '') : '자료 없음';
    $('#dInfo').html(
      infoRow('주용도', b.useNm || '미상') + infoRow('층수', floors || '-') + infoRow('사용승인', apr)
      + infoRow('위반건축물', b.violYn === '1' ? '해당' : b.violYn === '0' ? '없음' : '대장 미연계')
      + infoRow('세대수', b.units != null ? b.units + '세대' : '-')
      + infoRow('특수유형', b.spType ? b.spType + (b.spNm ? ' · ' + b.spNm : '') : '-')
      + infoRow('지하 거주', b.ugrndRes === 1 ? '등록' : '없음')
      + infoRow('해발 · 경사', b.elevM + 'm · ' + b.slopeDeg + '°')
      + infoRow('침수흔적', b.floodCnt + '회' + (b.floodDepth ? ' · ' + b.floodDepth + 'm' : ''))
      + infoRow('병원', (b.hospNm || '') + ' ' + Math.round(b.hospDist) + 'm')
      + infoRow('복지시설', (b.welfNm || '') + ' ' + Math.round(b.welfDist) + 'm')
      + infoRow('정류장', (b.busNm || '') + ' ' + Math.round(b.busDist) + 'm')
    );

    /* 거주 취약 가구 (02-2) : 세대 수 + 많은 유형 3개 */
    var rc = d.residents || {};
    $('#dVulnCnt').text('취약 가구 ' + (rc.vulnHh || 0) + '세대' + (rc.hhCnt ? ' / 전체 ' + rc.hhCnt + '가구 · ' + rc.mbrCnt + '명' : ''));
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
  /* 복지 연계 : 데이터에 있는 급여·수급 사실로 표시 (상담·지원 기록은 연동 예정) */
  function welfare(r) {
    if (r.basicLivingYn === 'Y') return { c: 'ok', t: '기초생활급여 수급' };
    if (r.pensionCnt > 0) return { c: 'ok', t: '기초연금 수급' };
    if (r.nearPoorYn === 'Y') return { c: 'blue', t: '차상위 지원' };
    if (r.arrearsYn === 'Y' || r.riskGrade === '고위험고립') return { c: 'warn', t: '상담 필요' };
    return { c: 'none', t: '연계 없음' };
  }
  function rmRow(r) {
    var w = welfare(r);
    var tags = (r.tags || []).map(function (t) { return '<span class="tag">' + esc(t) + '</span>'; }).join('');
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
      var vuln = list.filter(function (r) { return (r.tags || []).length > 0; });
      $('#rmRows').html(vuln.length ? vuln.map(rmRow).join('') : '<tr><td colspan="6" class="rs-empty">취약 유형에 해당하는 가구가 없습니다. (전체 ' + list.length + '가구)</td></tr>');
    });
  }
  function closeResidents() { $('#resModal').removeClass('open'); }

  function show(bldId) {
    current = null;
    $('#panelRight').addClass('open');
    $('#resultView').hide(); $('#detailView').show();
    $('#dTitle').text('불러오는 중…'); $('#dAddr').text(''); $('#dScoreItems, #dInfo').empty();
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
