package wellfair.common;

/**
 * 관할 밖 접근 (15단계 A). 구청 계정이 다른 구 건물·가구를 보거나 저장하려 할 때 던진다.
 * GlobalExceptionHandler 가 API 는 403 + {ok:false, msg}, 화면은 알림 + 원래 페이지로 돌려보낸다.
 */
public class ForbiddenException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ForbiddenException(String msg) {
		super(msg);
	}
}
