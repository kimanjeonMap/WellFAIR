/*
 * 오른쪽 "검색 결과" 리스트 (api/map/results.do, PageTO)
 *  - 정렬 · 더 불러오기 · 행 클릭 → onSelect(building)
 *  - 조건(criteria + gu/dong/q)은 getParams() 로 매번 받아 온다
 */
window.MapResult = (function () {
  var esc = Common.esc;
  var C = window.MAP_CONFIG;
  var getParams = null, onSelect = null;
  var page = 0, totalPages = 0, loading = false, seq = 0;

  /* 점수 색 = 지도 핀 · 도넛과 같은 구간 색 (MAP_CONFIG.LEVELS → COLORS) */
  function scoreColor(score) {
    for (var i = 0; i < C.LEVELS.length; i++) if (score >= C.LEVELS[i].min) return C.COLORS[C.LEVELS[i].key];
    return C.COLORS[C.LEVELS[C.LEVELS.length - 1].key];
  }

  function row(b) {
    var col = scoreColor(b.score);
    var title = (b.bldNm ? Common.shortAddr(b.addr) + ' ' + b.bldNm : Common.shortAddr(b.addr) + (b.useNm ? ' ' + b.useNm : ''));
    return '<div class="rs-row" data-id="' + esc(b.bldId) + '" data-lat="' + b.lat + '" data-lng="' + b.lng + '">'
      + '<span class="rs-rank">' + b.rank + '</span>'
      + '<div class="rs-body"><div class="rs-title">' + esc(title) + '</div>'
      + '<div class="rs-sub">' + esc(b.factorText || '해당 요인 없음') + (b.vulnHhCnt ? '<small class="rs-hh">취약 가구 ' + b.vulnHhCnt + '</small>' : '') + '</div></div>'
      /* 검색(q)일 때만 기준 미만 건물도 온다 (15단계 C-9) → 점수 옆에 회색 "기준 미만" */
      + '<div class="rs-score"><b style="color:' + col + '">' + b.score + '</b>' + (b.score < C.SCORE_MIN ? '<em class="rs-under">기준 미만</em>' : '')
      + '<span class="rs-track"><i style="width:' + Math.min(100, b.score) + '%;background:' + col + '"></i></span></div>'
      + '</div>';
  }

  function load(reset) {
    if (loading) return;
    if (reset) { page = 0; totalPages = 0; $('#resultList').empty(); }
    if (totalPages && page >= totalPages) return;
    loading = true;
    var my = ++seq;
    var p = $.extend({}, getParams(), { sort: $('#resultSort').val(), page: page + 1, size: 20 });
    $('#resultMore').text('목록을 불러오는 중').show();
    MapApi.results(p).done(function (res) {
      if (my !== seq) return;
      page = res.page; totalPages = res.totalPages;
      $('#resultCount').html('<b>' + Common.num(res.total) + '</b> 건');
      var html = res.list.map(row).join('');
      if (page === 1 && !res.list.length) {
        html = p.q ? '<p class="rs-empty">"' + esc(p.q) + '" 에 맞는 건물이 없습니다. 주소나 건물명을 다시 확인해 보세요.</p>'
          : '<p class="rs-empty">기준 점수 이상인 건물이 없습니다. 조건을 바꿔 다시 분석해 보세요.</p>';
      }
      $('#resultList').append(html);
      if (page < totalPages) $('#resultMore').text('목록 더 불러오기').show();
      else $('#resultMore').hide();
    }).always(function () { loading = false; });
  }

  function highlight(bldId) {
    $('#resultList .rs-row').removeClass('sel').filter('[data-id="' + bldId + '"]').addClass('sel');
  }

  function init(opts) {
    getParams = opts.getParams;
    onSelect = opts.onSelect;
    $('#resultSort').on('change', function () { load(true); });
    $('#resultMore').on('click', function () { load(false); });
    $('#resultList').on('click', '.rs-row', function () {
      var $r = $(this);
      highlight($r.data('id'));
      if (onSelect) onSelect({ bldId: String($r.data('id')), lat: Number($r.data('lat')), lng: Number($r.data('lng')) });
    });
    /* 스크롤 끝에 닿으면 자동으로 더 불러오기 */
    $('#resultList').on('scroll', function () {
      if (this.scrollTop + this.clientHeight >= this.scrollHeight - 40) load(false);
    });
  }

  return { init: init, load: load, highlight: highlight };
})();
