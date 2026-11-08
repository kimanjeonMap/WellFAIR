// 로그인 화면 : "사원번호 저장" (localStorage)
$(function () {
  var KEY = 'wellfair.loginId';
  var $empNo = $('#loginId');
  var $save = $('#saveEmpNo');

  try {
    var saved = localStorage.getItem(KEY);
    if (saved && !$empNo.val()) {
      $empNo.val(saved);
      $save.prop('checked', true);
      $('#pw').trigger('focus');
    } else if (saved) {
      $save.prop('checked', true);
    }
  } catch (e) { /* 사생활 보호 모드 등 */ }

  $('#loginForm').on('submit', function () {
    try {
      if ($save.is(':checked')) {
        localStorage.setItem(KEY, $empNo.val().trim());
      } else {
        localStorage.removeItem(KEY);
      }
    } catch (e) { /* ignore */ }
  });
});
