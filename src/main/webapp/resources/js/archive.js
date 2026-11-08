/*
 * 보관함 (archive.jsp) : 정렬 · 선택 · 저장 드롭다운 · 행 클릭 상세 패널
 *  - 행 상세는 지도의 MapPanel 을 그대로 쓰되 주소만 api/archive/detail.do 로
 */
$(function () {
  var ctx = Common.ctx;

  /* 상세는 보관함 API 로 */
  MapApi.building = function (params) { return Common.api('GET', 'api/archive/detail.do', params); };
  MapPanel.init({ getCriteria: function () { return {}; }, onArchived: function () {}, onClose: function () {} });
  $('#btnArchive').hide();   // 이미 보관함에 있는 건물

  /* 정렬 · 구 바꾸면 바로 조회 */
  $('#sortSeg button').on('click', function () { $('#sortField').val($(this).data('sort')); $('#filterForm').trigger('submit'); });
  $('#selGu').on('change', function () { $('#selDong').val(''); $('#filterForm').trigger('submit'); });
  $('#selDong').on('change', function () { $('#filterForm').trigger('submit'); });

  /* 선택 */
  function refreshSel() {
    var n = $('.rowChk:checked').length;
    $('#selCount').text(n ? n + '건 선택됨' : '');
    $('#bulkDd, #btnDelete').toggle(n > 0);
    $('.rowChk').each(function () { $(this).closest('tr').toggleClass('sel', this.checked); });
  }
  $('#chkAll').on('change', function () { $('.rowChk').prop('checked', this.checked); refreshSel(); });
  $(document).on('change', '.rowChk', refreshSel);

  /* 드롭다운 */
  $(document).on('click', '.dd-toggle', function (e) {
    e.stopPropagation();
    var $dd = $(this).closest('.dd');
    $('.dd').not($dd).removeClass('open');
    $dd.toggleClass('open');
    /* 표(overflow:hidden) 밖으로 나오도록 버튼 아래에 fixed 로 띄운다 */
    if ($dd.hasClass('open')) {
      var r = this.getBoundingClientRect(), $m = $dd.find('.dd-menu'), w = $m.outerWidth() || 280;
      $m.css({ position: 'fixed', top: r.bottom + 4, left: Math.max(8, Math.min(r.right - w, window.innerWidth - w - 8)), right: 'auto' });
    }
  });
  $(document).on('click', function () { $('.dd').removeClass('open'); });
  $(window).on('scroll resize', function () { $('.dd').removeClass('open'); });
  $(document).on('click', '.dd-menu', function (e) { e.stopPropagation(); });

  /* 현장확인 리스트에 저장 */
  $(document).on('click', '.to-survey', function () {
    var ids = $(this).data('factor') ? [$(this).data('factor')] : $('.rowChk:checked').map(function () { return this.value; }).get();
    if (!ids.length) { Common.toast('선택한 건물이 없습니다.'); return; }
    $('#addListId').val($(this).data('id'));
    $('#addIds').html(ids.map(function (id) { return '<input type="hidden" name="factorIds" value="' + id + '">'; }).join(''));
    $('#addItemsForm').trigger('submit');
  });

  /* 보관함에서 빼기 */
  $('#btnDelete').on('click', function () {
    var ids = $('.rowChk:checked').map(function () { return this.value; }).get();
    if (!ids.length) return;
    $('#delIds').html(ids.map(function (id) { return '<input type="hidden" name="factorIds" value="' + id + '">'; }).join(''));
    $('#deleteForm').trigger('submit');
  });

  /* 행 클릭 → 상세 패널 */
  $('#archiveTbl tbody').on('click', 'tr[data-id]', function (e) {
    if ($(e.target).closest('input, button, .dd').length) return;
    $('#archiveTbl tr').removeClass('sel');
    $(this).addClass('sel');
    MapPanel.show(String($(this).data('id')));
  });
});
