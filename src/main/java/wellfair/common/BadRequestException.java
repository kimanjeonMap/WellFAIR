package wellfair.common;

/**
 * 잘못된 요청값 (15단계 B·D). 예) 가중치 합이 100 이 아님, 주소를 직접 고친 경우.
 * GlobalExceptionHandler 가 API 는 400 + {ok:false, msg}, 화면은 알림 + 원래 페이지로 돌려보낸다.
 */
public class BadRequestException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public BadRequestException(String msg) {
		super(msg);
	}
}
