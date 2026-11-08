package wellfair.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import wellfair.service.MemberService;
import wellfair.to.MemberTO;

@Controller
public class LoginController {

	@Autowired
	MemberService memberService;

	// GET loginform.do : 이미 로그인돼 있으면 지도로
	@GetMapping("loginform.do")
	public String loginForm(HttpServletRequest req) {
		HttpSession session = req.getSession(false);
		if (session != null && session.getAttribute("loginMember") != null) {
			return "redirect:map.do";
		}
		return "login";
	}

	// POST login.do : 성공 → 세션 loginMember(user_id 포함) → map.do, 실패 → login.jsp (msg)
	@PostMapping("login.do")
	public String login(@RequestParam("loginId") String loginId, @RequestParam("pw") String pw, HttpServletRequest req) {
		MemberTO member = memberService.login(loginId, pw);
		if (member == null) {
			req.setAttribute("msg", "아이디 또는 비밀번호가 올바르지 않습니다.");
			req.setAttribute("loginId", loginId);
			return "login";
		}
		HttpSession session = req.getSession();
		session.setAttribute("loginMember", member);
		return "redirect:map.do";
	}

	// GET logout.do
	@GetMapping("logout.do")
	public String logout(HttpServletRequest req) {
		HttpSession session = req.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		return "redirect:loginform.do";
	}
}
