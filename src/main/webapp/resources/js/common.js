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

  /* "서울특별시 구로구 가리봉동 12-34" → "가리봉동 12-34" */
  function shortAddr(addr) {
    return String(addr || '').replace(/^서울특별시\s+/, '').replace(/^\S+구\s+/, '');
  }

  function num(n) { return Number(n || 0).toLocaleString(); }

  return { ctx: CTX, esc: esc, url: url, api: api, toast: toast, shortAddr: shortAddr, num: num };
})();
