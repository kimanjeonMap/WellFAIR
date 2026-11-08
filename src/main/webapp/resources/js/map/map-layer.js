/*
 * 왼쪽 레이어 패널 → 분석 조건 (AnalysisCriteriaTO 파라미터)
 *  - 요인 체크박스 · 기준값 슬라이더 (map.jsp 의 [data-factor] 행)
 *  - 가중치 조정 팝업 (묶음 4개 → 요인별 가중치)
 *  - 섹션 접기 · 패널 접기
 * 조건이 바뀌면 "분석 실행" 을 눌러야 지도가 다시 그려진다 (onRun)
 */
window.MapLayer = (function () {
  var C = window.MAP_CONFIG;
  var weights = {};      // 묶음 key → 값
  var onRun = null, onToggle = null;

  function factorOf(key) {
    for (var i = 0; i < C.FACTORS.length; i++) if (C.FACTORS[i].key === key) return C.FACTORS[i];
    return null;
  }
  function groupOf(factorKey) {
    for (var i = 0; i < C.WEIGHT_GROUPS.length; i++) {
      if (C.WEIGHT_GROUPS[i].factors.indexOf(factorKey) >= 0) return C.WEIGHT_GROUPS[i];
    }
    return null;
  }

  /* 슬라이더 값 라벨 갱신 */
  function refreshRow($row) {
    var f = factorOf($row.data('factor'));
    if (!f) return;
    var on = $row.find('.f-chk').is(':checked');
    $row.toggleClass('off', !on);
    var $rng = $row.find('.f-rng');
    if ($rng.length && f.fmt) $row.find('.f-val').text(f.fmt($rng.val()));
  }

  function refreshWeights() {
    C.WEIGHT_GROUPS.forEach(function (g) {
      $('#w-' + g.key).val(weights[g.key]);
      $('#wv-' + g.key).text('+' + weights[g.key]);
      /* 요인 행 오른쪽에 가중치 배지 */
      g.factors.forEach(function (k) { $('[data-factor="' + k + '"] .f-w').text('+' + weights[g.key]); });
    });
  }

  function resetWeights() {
    C.WEIGHT_GROUPS.forEach(function (g) { weights[g.key] = g.def; });
    refreshWeights();
  }

  /* 현재 패널 상태 → 서버 파라미터 */
  function getCriteria() {
    var p = { scoreMin: C.SCORE_MIN };
    C.FACTORS.forEach(function (f) {
      var $row = $('[data-factor="' + f.key + '"]');
      var on = $row.find('.f-chk').is(':checked');
      var g = groupOf(f.key);
      p[f.key + 'On'] = on;
      p[f.key + 'W'] = g ? weights[g.key] : 0;
      if (f.param) p[f.param] = $row.find('.f-rng').val();
    });
    return p;
  }

  function init(opts) {
    onRun = opts.onRun;
    onToggle = opts.onToggle;
    resetWeights();

    /* 요인 행 */
    $('[data-factor]').each(function () {
      var $row = $(this), f = factorOf($row.data('factor'));
      if (!f) return;
      $row.find('.f-chk').prop('checked', f.on);
      var $rng = $row.find('.f-rng');
      if ($rng.length) $rng.attr({ min: f.min, max: f.max, step: f.step }).val(f.def);
      refreshRow($row);
    });
    $('.layer-panel').on('change input', '.f-chk, .f-rng', function () {
      refreshRow($(this).closest('[data-factor]'));
      $('#btnRun').addClass('dirty');
    });

    /* 섹션 접기 */
    $('.layer-panel').on('click', '.sec-head', function () {
      $(this).closest('.sec').toggleClass('collapsed');
    });

    /* 가중치 팝업 */
    $('#btnWeights').on('click', function () { $('#weightPop').toggleClass('open'); });
    $('#weightClose').on('click', function () { $('#weightPop').removeClass('open'); });
    $('#weightReset').on('click', function () { resetWeights(); $('#btnRun').addClass('dirty'); });
    C.WEIGHT_GROUPS.forEach(function (g) {
      $('#w-' + g.key).attr({ min: 0, max: g.max, step: 5 }).on('input change', function () {
        weights[g.key] = Number(this.value);
        refreshWeights();
        $('#btnRun').addClass('dirty');
      });
    });

    /* 분석 실행 */
    $('#btnRun').on('click', function () {
      $(this).removeClass('dirty');
      $('#weightPop').removeClass('open');
      if (onRun) onRun(getCriteria());
    });

    /* 패널 접기 */
    $('#toggleLeft').on('click', function () {
      var closed = $('.map-layout').toggleClass('left-closed').hasClass('left-closed');
      $(this).attr('aria-expanded', String(!closed));
      if (onToggle) setTimeout(onToggle, 280);
    });
  }

  return { init: init, getCriteria: getCriteria };
})();
