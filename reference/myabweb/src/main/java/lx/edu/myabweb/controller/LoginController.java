package lx.edu.myabweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class LoginController {
	
	@RequestMapping("loginform.do")
	public String loginForm() {
		return "login";
	}
	@RequestMapping("login.do")
	public String login(@RequestParam("id") String id, @RequestParam("pw") String pw, HttpServletRequest req) {
		//id와 pw가 동일하다면 로그인된걸로 틀리다면 로그인을 다시하도록 
		if(id!=null && id.equals(pw)) {
			HttpSession session = req.getSession();
			session.setAttribute("id", id);
			return "index";
		}
		return "login";
	}

}
