/*
 * 공통 : 컨텍스트 경로, HTML 이스케이프, 서버 호출(jQuery), 토스트
 * ctx 는 head.jspf 의 <meta name="ctx"> 에서 읽는다.
 */
window.Common = (function () {
  var CTX = ($('meta[name="ctx"]').attr('content') || '/').replace(/\/$/, '');

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function url(path) {
    return CTX + '/' + String(path).replace(/^\//, '');
  }

  /* GET/POST JSON. 세션이 끊겨 로그인 화면(HTML)이 오면 로그인으로 보낸다 */
  function api(method, path, data) {
    return $.ajax({
      url: url(path),
      method: method,
      data: data || {},
      dataType: 'json',
      traditional: true
    }).fail(function (xhr) {
      if (xhr.status === 200 || xhr.status === 302 || xhr.status === 401) {
        location.href = url('loginform.do');
        return;
      }
      /* 403(관할 밖) · 400(가중치 등) · 500 은 GlobalExceptionHandler 가 {ok:false, msg} 로 준다 → 그 문구 그대로 */
      var m = xhr.responseJSON && xhr.responseJSON.msg;
      toast(m || '서버 요청에 실패했습니다. (' + xhr.status + ')');
    });
  }

  var toastTimer = null;
  function toast(msg) {
    var $el = $('#app-toast');
    if (!$el.length) $el = $('<div id="app-toast" class="toast" role="status"></div>').appendTo('body');
    $el.text(msg).addClass('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { $el.removeClass('show'); }, 2600);
  }

  /**
   * 안내 · 확인 모달 (브라우저 기본 alert / confirm 대신. 모양 = common.css .dlg-*, 건물 상세의 모달과 같은 톤)
   *   Common.dialog({ title, html | text, okText, cancelText }) → jQuery promise. [확인] = resolve(true), 닫기 · 취소 · Esc · 바깥 클릭 = resolve(false)
   *   cancelText 를 null 로 주면 버튼 하나(확인)만
   */
  function dialog(opt) {
    opt = opt || {};
    var dfd = $.Deferred();
    var cancelText = opt.cancelText === undefined ? '취소' : opt.cancelText;
    var $bg = $('<div class="dlg-bg" role="dialog" aria-modal="true"></div>');
    var $box = $('<div class="dlg"></div>').appendTo($bg);
    $('<div class="dlg-head"></div>')
      .append($('<h3></h3>').text(opt.title || '안내'))
      .append('<button type="button" class="dlg-x" aria-label="닫기">×</button>').appendTo($box);
    var $body = $('<div class="dlg-body"></div>').appendTo($box);
    if (opt.html != null) $body.html(opt.html); else $body.text(opt.text || '');
    var $foot = $('<div class="dlg-foot"></div>').appendTo($box);
    if (cancelText) $('<button type="button" class="btn btn-line btn-sm dlg-cancel"></button>').text(cancelText).appendTo($foot);
    var $ok = $('<button type="button" class="btn btn-primary btn-sm dlg-ok"></button>').text(opt.okText || '확인').appendTo($foot);

    var prevFocus = document.activeElement;
    function close(v) {
      $(document).off('keydown.dlg');
      $bg.removeClass('open');
      setTimeout(function () { $bg.remove(); }, 160);
      if (prevFocus && prevFocus.focus) try { prevFocus.focus(); } catch (e) {}
      dfd.resolve(v);
    }
    $ok.on('click', function () { close(true); });
    $box.find('.dlg-x, .dlg-cancel').on('click', function () { close(false); });
    $bg.on('mousedown', function (e) { if (e.target === this) close(false); });
    $(document).off('keydown.dlg').on('keydown.dlg', function (e) {
      if (e.key === 'Escape') { e.stopImmediatePropagation(); close(false); }
      else if (e.key === 'Enter' && document.activeElement === $ok[0]) { e.preventDefault(); close(true); }
    });
    $bg.appendTo('body');
    setTimeout(function () { $bg.addClass('open'); $ok.trigger('focus'); }, 0);
    return dfd.promise();
  }

  /* "서울특별시 구로구 가리봉동 12-34" → "가리봉동 12-34" */
  function shortAddr(addr) {
    return String(addr || '').replace(/^서울특별시\s+/, '').replace(/^\S+구\s+/, '');
  }

  function num(n) { return Number(n || 0).toLocaleString(); }

  return { ctx: CTX, esc: esc, url: url, api: api, toast: toast, dialog: dialog, shortAddr: shortAddr, num: num };
})();
