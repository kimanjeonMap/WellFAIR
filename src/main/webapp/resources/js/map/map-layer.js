/*
 * 왼쪽 레이어 패널 → 분석 조건 (AnalysisCriteriaTO 파라미터)
 *  - 요인 체크박스 · 기준값 슬라이더 (map.jsp 의 [data-factor] 행). 요인은 켜고 끄기만
 *  - 가중치 = 위험 유형 4묶음 (주거 공간 · 건물 상태 · 침수 위험 · 경제 여건), 합계 항상 100 (CLAUDE.md 5-1 "가중치·분석 실행·도넛")
 *      슬라이더 하나를 움직이면 나머지 묶음이 비율대로 줄거나 늘어 합 100 유지 (5 단위, 끝수는 가장 큰 묶음에서 맞춤)
 *      묶음의 요인이 전부 꺼지면 그 묶음은 0 으로 잠기고 나머지끼리 합 100
 *  - 조건이 바뀌어도 지도는 그대로. [분석 실행](dirty) 을 눌러야 onRun(getCriteria()) 로 확정된다
 *  - 슬라이더 채움(--pct) : 손잡이 왼쪽 primary, 오른쪽 어두운 회색 (map.css)
 */
window.MapLayer = (function () {
  var C = window.MAP_CONFIG;
  var STEP = C.WEIGHT_STEP || 5;
  var weights = {};      // 묶음 key → 값 (합 100)
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
  function isOn(factorKey) { return $('[data-factor="' + factorKey + '"] .f-chk').is(':checked'); }
  /* 묶음 안 요인이 전부 꺼졌으면 잠김 (가중치 0) */
  function locked(g) { return !g.factors.some(isOn); }
  function r5(v) { return Math.round(v / STEP) * STEP; }

  /* 슬라이더 채움 비율 → CSS 변수 */
  function fill($r) {
    var min = Number($r.attr('min')) || 0, max = Number($r.attr('max')) || 100, v = Number($r.val()) || 0;
    $r.css('--pct', (max > min ? (v - min) / (max - min) * 100 : 0) + '%');
  }

  /* 슬라이더 값 라벨 갱신 */
  function refreshRow($row) {
    var f = factorOf($row.data('factor'));
    if (!f) return;
    var on = $row.find('.f-chk').is(':checked');
    $row.toggleClass('off', !on);
    var $rng = $row.find('.f-rng');
    if ($rng.length) { if (f.fmt) $row.find('.f-val').text(f.fmt($rng.val())); fill($rng); }
  }

  function refreshWeights() {
    var sum = 0;
    C.WEIGHT_GROUPS.forEach(function (g) {
      var w = weights[g.key] || 0, lk = locked(g);
      sum += w;
      var $r = $('#w-' + g.key).val(w).prop('disabled', lk);
      fill($r);
      $('#wv-' + g.key).text(lk ? '꺼짐' : '+' + w);
      $('.wp-row[data-gw="' + g.key + '"]').toggleClass('locked', lk);
      /* 레이어 패널에는 점수를 보이지 않는다 (피그마 02). 묶음 점수는 이 팝업에서만 */
    });
  }

  /**
   * 묶음 changedKey 를 v 로 두고, 잠기지 않은 나머지 묶음을 지금 비율대로 나눠 합 100 을 맞춘다.
   * changedKey 가 null 이면 현재 값들을 (잠김 반영해서) 100 으로 정규화만 한다.
   */
  function rebalance(changedKey, v) {
    C.WEIGHT_GROUPS.forEach(function (g) { if (locked(g)) weights[g.key] = 0; });
    var act = C.WEIGHT_GROUPS.filter(function (g) { return !locked(g); });
    if (!act.length) { refreshWeights(); return; }
    var target = null;
    act.forEach(function (g) { if (g.key === changedKey) target = g; });
    var others = act.filter(function (g) { return g !== target; });
    if (target) {
      v = Math.max(0, Math.min(100, r5(Number(v) || 0)));
      if (!others.length) v = 100;
      weights[target.key] = v;
    }
    var rest = 100 - (target ? weights[target.key] : 0);
    var sum = others.reduce(function (s, g) { return s + (weights[g.key] || 0); }, 0);
    var alloc = {}, total = 0;
    others.forEach(function (g) {
      alloc[g.key] = r5(sum > 0 ? (weights[g.key] || 0) / sum * rest : rest / others.length);
      total += alloc[g.key];
    });
    var diff = rest - total;
    if (diff && others.length) {                       // 끝수는 가장 큰 묶음에서 맞춘다
      var big = others.slice().sort(function (a, b) { return alloc[b.key] - alloc[a.key]; })[0];
      alloc[big.key] = Math.max(0, alloc[big.key] + diff);
    }
    others.forEach(function (g) { weights[g.key] = alloc[g.key]; });
    refreshWeights();
  }

  function resetWeights() {
    C.WEIGHT_GROUPS.forEach(function (g) { weights[g.key] = g.def; });
    rebalance(null);
  }

  /* 현재 패널 상태 → 서버 파라미터 (요인 on/off · 기준값 · 묶음 가중치 4개) */
  function getCriteria() {
    var p = { scoreMin: C.SCORE_MIN };
    C.FACTORS.forEach(function (f) {
      var $row = $('[data-factor="' + f.key + '"]');
      p[f.key + 'On'] = $row.find('.f-chk').is(':checked');
      if (f.param) p[f.param] = $row.find('.f-rng').val();
    });
    C.WEIGHT_GROUPS.forEach(function (g) { p[g.key + 'W'] = weights[g.key] || 0; });
    return p;
  }

  function dirty() { $('#btnRun').addClass('dirty'); }

  function init(opts) {
    onRun = opts.onRun;
    onToggle = opts.onToggle;

    /* 요인 행 */
    $('[data-factor]').each(function () {
      var $row = $(this), f = factorOf($row.data('factor'));
      if (!f) return;
      $row.find('.f-chk').prop('checked', f.on);
      var $rng = $row.find('.f-rng');
      if ($rng.length) $rng.attr({ min: f.min, max: f.max, step: f.step }).val(f.def);
      refreshRow($row);
    });
    resetWeights();

    $('.layer-panel').on('change input', '.f-rng', function () { refreshRow($(this).closest('[data-factor]')); dirty(); });
    $('.layer-panel').on('change', '.f-chk', function () {
      var $row = $(this).closest('[data-factor]'), g = groupOf($row.data('factor'));
      var wasLocked = g && (weights[g.key] || 0) === 0;
      refreshRow($row);
      if (g) {
        if (locked(g)) rebalance(g.key, 0);                          // 전부 꺼짐 → 0 으로 잠그고 나머지가 100
        else if (wasLocked) rebalance(g.key, g.def);                 // 다시 켜짐 → 기본값으로 돌려 넣고 나머지 조정
        else refreshWeights();
      }
      dirty();
    });

    /* 섹션 접기 */
    $('.layer-panel').on('click', '.sec-head', function (e) {
      $(this).closest('.sec').toggleClass('collapsed');
    });

    /* 가중치 팝업 */
    $('#btnWeights').on('click', function () { $('#weightPop').toggleClass('open'); });
    $('#weightClose').on('click', function () { $('#weightPop').removeClass('open'); });
    $('#weightReset').on('click', function () { resetWeights(); dirty(); });
    C.WEIGHT_GROUPS.forEach(function (g) {
      $('#w-' + g.key).attr({ min: 0, max: 100, step: STEP }).on('input change', function () {
        rebalance(g.key, Number(this.value));
        dirty();
      });
    });

    /* 분석 실행 : 이때만 조건이 확정된다 */
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
