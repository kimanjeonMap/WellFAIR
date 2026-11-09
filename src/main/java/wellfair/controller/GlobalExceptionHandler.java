package wellfair.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

import jakarta.servlet.http.HttpServletRequest;
import wellfair.common.BadRequestException;
import wellfair.common.ForbiddenException;

/**
 * 500 화면 대신 한 곳에서 (15단계 D-13).
 *  - API(api/...do)  : {ok:false, msg} + 상태 코드 (403 관할 밖 / 400 잘못된 값 / 500 DB 오류)
 *  - 화면(.do)       : 알림(flash msg) + 원래 페이지(Referer, 없으면 archive.do)로 redirect
 * 받는 예외 : ForbiddenException(관할 밖) · BadRequestException · IllegalArgumentException · IllegalStateException(입력값) · DataAccessException(DB)
 */
@ControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ForbiddenException.class)
	public ModelAndView forbidden(ForbiddenException e, HttpServletRequest req) {
		return handle(req, HttpStatus.FORBIDDEN, e.getMessage());
	}

	@ExceptionHandler({ BadRequestException.class, IllegalArgumentException.class, IllegalStateException.class })
	public ModelAndView badRequest(RuntimeException e, HttpServletRequest req) {
		return handle(req, HttpStatus.BAD_REQUEST, e.getMessage() == null ? "요청값을 확인해 주세요." : e.getMessage());
	}

	@ExceptionHandler(DataAccessException.class)
	public ModelAndView dbError(DataAccessException e, HttpServletRequest req) {
		log.warn("DB error at {} : {}", req.getRequestURI(), e.getMostSpecificCause() == null ? e.getMessage() : e.getMostSpecificCause().getMessage());
		return handle(req, HttpStatus.INTERNAL_SERVER_ERROR, "저장하지 못했습니다. 입력값(길이 · 형식)을 확인해 주세요.");
	}

	private static ModelAndView handle(HttpServletRequest req, HttpStatus status, String msg) {
		if (req.getRequestURI().contains("/api/")) {
			MappingJackson2JsonView view = new MappingJackson2JsonView();
			view.setContentType("application/json;charset=UTF-8");
			ModelAndView mv = new ModelAndView(view);
			mv.setStatus(status);
			mv.addObject("ok", false);
			mv.addObject("msg", msg);
			return mv;
		}
		FlashMap flash = RequestContextUtils.getOutputFlashMap(req);
		if (flash != null) flash.put("msg", msg);
		return new ModelAndView("redirect:" + backTo(req));
	}

	/** 돌아갈 곳 = 같은 앱 안의 Referer, 없으면 보관함 (redirect: 는 컨텍스트 기준 경로라 contextPath 는 뺀다) */
	private static String backTo(HttpServletRequest req) {
		String ctx = req.getContextPath();
		String ref = req.getHeader("Referer");
		if (ref != null) {
			String base = req.getScheme() + "://" + req.getServerName()
					+ (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort()) + ctx + "/";
			if (ref.startsWith(base) && !ref.contains("loginform.do")) return ref.substring(base.length() - 1);   // "/xxx.do?..."
		}
		return "/archive.do";
	}
}
