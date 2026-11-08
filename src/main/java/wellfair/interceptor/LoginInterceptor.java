package wellfair.interceptor;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		//session에서 loginMember를 가져온다. 있으면 true 리턴 없으면 loginform으로 redirect
		HttpSession session = request.getSession();
		Object loginMember = session.getAttribute("loginMember");
		System.out.println("session loginMember=" + loginMember);
		if(loginMember != null) {
			return true;
		}
		String contextPath = request.getContextPath();
		response.sendRedirect(contextPath + "/loginform.do");
		return false;

	}
}
