/*
 * 보관함 (archive.jsp) : 정렬 · 선택 · 저장 드롭다운 · 행 클릭 → 05 건물 확인 이력 (archivedetail.do)
 */
$(function () {
  var ctx = Common.ctx;

  /* 정렬 · 구 바꾸면 바로 조회 */
  $('#sortSeg button').on('click', function () { $('#sortField').val($(this).data('sort')); $('#filterForm').trigger('submit'); });
  $('#selGu').on('change', function () { $('#selDong').val(''); $('#selAdm').val(''); $('#filterForm').trigger('submit'); });
  $('#selDong').on('change', function () { $('#selAdm').val(''); $('#filterForm').trigger('submit'); });
  $('#selAdm').on('change', function () { $('#filterForm').trigger('submit'); });

  /* 검색창 [×] : 글자가 있을 때만 보이고, 누르면 지운다. 이미 검색한 상태였으면 전체 목록으로 다시 조회 */
  var searched = $.trim($('#archiveQ').val()) !== '';
  $('#archiveQ').on('input', function () { $('#archiveQClear').prop('hidden', this.value === ''); });
  $('#archiveQClear').on('click', function () {
    $('#archiveQ').val('').trigger('focus');
    $(this).prop('hidden', true);
    if (searched) $('#filterForm').trigger('submit');
  });

  /* [상세 필터] 펼치기 · 접기 (펼침 상태는 fopen 으로 같이 보낸다) */
  $('#btnFilter').on('click', function () {
    var open = !$('#filterDetail').is(':visible');
    $('#filterDetail').toggle(open);
    $(this).toggleClass('on', open);
    $('#fopen').val(open);
  });

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

  /* 보관함에서 빼기 : 선택한 행 전체 [보관함에서 빼기] 또는 행마다 [저장 ▾] 메뉴 맨 아래 */
  function deleteRows(ids) {
    if (!ids.length) return;
    if (!confirm(ids.length + '건을 보관함에서 뺄까요?\n현장확인 리스트에 담긴 건물은 빠지지 않고 남습니다.')) return;
    $('#delIds').html(ids.map(function (id) { return '<input type="hidden" name="factorIds" value="' + id + '">'; }).join(''));
    $('#deleteForm').trigger('submit');
  }
  $('#btnDelete').on('click', function () { deleteRows($('.rowChk:checked').map(function () { return this.value; }).get()); });
  $(document).on('click', '.row-del', function () { deleteRows([String($(this).data('factor'))]); });

  /* 행 클릭 → 05 건물 확인 이력 */
  $('#archiveTbl tbody').on('click', 'tr[data-id]', function (e) {
    if ($(e.target).closest('input, button, .dd, a').length) return;
    location.href = Common.url('archivedetail.do?bldId=' + encodeURIComponent(String($(this).data('id'))));
  });
});
