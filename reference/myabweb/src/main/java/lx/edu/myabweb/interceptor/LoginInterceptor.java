package lx.edu.myabweb.interceptor;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
	
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		//session에서 id를 가져온다. 있으면 true 리턴 없으면 loginform으로 redirect
		HttpSession session = request.getSession();
		String id = (String) session.getAttribute("id");
		System.out.println("session id=" + id);
		if(id!=null && id.length()>0) {
			return true;
		}
		String contextPath = request.getContextPath();
		response.sendRedirect(contextPath + "/loginform.do");
		return false;
		
	}
}
