package wellfair.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import wellfair.service.FieldService;
import wellfair.to.FieldCheckTO;
import wellfair.to.FieldListTO;
import wellfair.to.MemberTO;

// 현장확인 리스트 (Figma 03 목록 / 03-1 수정 / 03-2 상세)
@Controller
public class FieldController {

	@Value("${app.naver.client-id}")
	String naverClientId;

	@Autowired
	FieldService fieldService;

	// GET fieldlist.do?tab=doing|done&q
	@GetMapping("fieldlist.do")
	public String list(@RequestParam(value = "tab", defaultValue = "doing") String tab,
			@RequestParam(value = "q", required = false) String q, HttpSession session, Model model) {
		long userId = userId(session);
		String gu = userGu(session);
		model.addAttribute("tab", tab);
		model.addAttribute("q", q);
		model.addAttribute("list", fieldService.getFieldList(userId, gu, tab, q, true));
		model.addAttribute("doingCnt", fieldService.getFieldList(userId, gu, "doing", null, false).size());
		model.addAttribute("doneCnt", fieldService.getFieldList(userId, gu, "done", null, false).size());
		return "fieldlist";
	}

	// GET fieldedit.do?listId (0 = 새 리스트)
	@GetMapping("fieldedit.do")
	public String editForm(@RequestParam(value = "listId", defaultValue = "0") long listId, HttpSession session, Model model) {
		long userId = userId(session);
		FieldListTO field = listId > 0 ? fieldService.getFieldDetail(listId, userId, userGu(session)) : null;
		if (field == null) {
			field = new FieldListTO();
			field.setTitle("");
			field.setSggCd(userGu(session));        // 구청 계정 : 새 리스트도 소속 구 (출발지 기본값)
		}
		model.addAttribute("field", field);
		model.addAttribute("pickList", fieldService.getPickList(userId, userGu(session)));
		model.addAttribute("naverClientId", naverClientId);
		return "fieldedit";
	}

	// POST fielddelete.do : listId → redirect:fieldlist.do (확인 기록이 있는 리스트는 남긴다)
	@PostMapping("fielddelete.do")
	public String delete(@RequestParam("listId") long listId, @RequestParam(value = "tab", defaultValue = "doing") String tab,
			HttpSession session, RedirectAttributes ra) {
		int r = fieldService.deleteField(userId(session), userGu(session), listId);
		ra.addFlashAttribute("msg", r > 0 ? "리스트를 삭제했습니다."
				: r < 0 ? "현장 확인 기록이 있는 리스트는 삭제할 수 없습니다. (건물 확인 이력을 지키기 위해)" : "리스트를 찾을 수 없습니다.");
		return "redirect:fieldlist.do?tab=" + ("done".equals(tab) ? "done" : "doing");
	}

	// POST fieldedit.do : listId, title, moveMode(walk/transit/car), factorIds(순서대로) → redirect:fielddetail.do
	@PostMapping("fieldedit.do")
	public String edit(@RequestParam(value = "listId", defaultValue = "0") long listId,
			@RequestParam(value = "title", required = false) String title,   // 비우면 "새 리스트N" (FieldService.nextAutoTitle)
			@RequestParam(value = "moveMode", required = false) String moveMode,
			@RequestParam(value = "factorIds", required = false) List<Long> factorIds,
			HttpSession session, RedirectAttributes ra) {
		long id;
		try {
			id = fieldService.saveField(userId(session), userGu(session), listId, title, moveMode, factorIds);
		} catch (IllegalArgumentException e) {                  // 한 리스트 = 한 구
			ra.addFlashAttribute("msg", e.getMessage());
			return "redirect:fieldedit.do?listId=" + listId;
		}
		if (id == 0) return "redirect:fieldlist.do";
		ra.addFlashAttribute("msg", "변경 내용을 저장했습니다.");
		return "redirect:fielddetail.do?listId=" + id;
	}

	// POST fieldadditems.do : listId(0이면 새 리스트), factorIds → redirect:fieldedit.do
	@PostMapping("fieldadditems.do")
	public String addItems(@RequestParam(value = "listId", defaultValue = "0") long listId,
			@RequestParam(value = "factorIds", required = false) List<Long> factorIds,
			HttpSession session, RedirectAttributes ra) {
		if (factorIds == null || factorIds.isEmpty()) {
			ra.addFlashAttribute("msg", "선택한 건물이 없습니다.");
			return "redirect:archive.do";
		}
		long id;
		try {
			id = fieldService.addItems(userId(session), userGu(session), listId, factorIds);
		} catch (IllegalArgumentException e) {                  // 한 리스트 = 한 구
			ra.addFlashAttribute("msg", e.getMessage());
			return "redirect:archive.do";
		}
		if (id == 0) return "redirect:archive.do";
		ra.addFlashAttribute("msg", factorIds.size() + "건을 현장확인 리스트에 담았습니다.");
		return "redirect:fieldedit.do?listId=" + id;
	}

	// GET fielddetail.do?listId
	@GetMapping("fielddetail.do")
	public String detail(@RequestParam("listId") long listId, HttpSession session, Model model) {
		FieldListTO field = fieldService.getFieldDetail(listId, userId(session), userGu(session));
		if (field == null) return "redirect:fieldlist.do";
		double km = FieldService.totalKm(field.getItems());
		int walk = FieldService.minutes(km, FieldService.speedOf(field.getMoveMode()));   // 저장된 이동수단 기준 (변수명은 그대로)
		int check = field.getItems().size() * FieldService.CHECK_MIN_PER_BLD;
		Object[] g = FieldService.guOffice(field.getItems().isEmpty() ? field.getSggCd() : field.getItems().get(0).getSggCd());
		model.addAttribute("startName", g[0]);
		model.addAttribute("startLat", g[1]);
		model.addAttribute("startLng", g[2]);
		model.addAttribute("field", field);
		model.addAttribute("km", km);
		model.addAttribute("walkMin", FieldService.minutes(km, FieldService.WALK_KMH));
		model.addAttribute("moveMin", walk);
		model.addAttribute("carMin", FieldService.minutes(km, FieldService.CAR_KMH));
		model.addAttribute("transitMin", FieldService.minutes(km, FieldService.TRANSIT_KMH));
		model.addAttribute("checkMin", check);
		model.addAttribute("totalMin", walk + check);
		int tm = walk + check;
		model.addAttribute("totalText", tm >= 60 ? (tm / 60) + "시간 " + (tm % 60) + "분" : tm + "분");
		model.addAttribute("endTime", String.format("%02d:%02d", (9 * 60 + 30 + walk + check) / 60, (9 * 60 + 30 + walk + check) % 60));
		model.addAttribute("naverClientId", naverClientId);
		return "fielddetail";
	}

	// POST fieldcheck.do : planId, listId, result, content → 확인 완료 (원래 앱 기능, 웹 버튼으로 대체)
	@PostMapping("fieldcheck.do")
	public String check(@RequestParam("planId") long planId, @RequestParam("listId") long listId,
			@RequestParam(value = "result", defaultValue = "MET") String result,
			@RequestParam(value = "content", required = false) String content,
			HttpSession session, RedirectAttributes ra) {
		FieldCheckTO c = new FieldCheckTO();
		c.setPlanId(planId);
		c.setSurveyId(listId);
		c.setUserId(userId(session));
		c.setResult(result);
		if (content != null && !content.isBlank()) c.setContent(content.trim());
		ra.addFlashAttribute("msg", fieldService.checkItem(c) ? "확인 완료로 처리했습니다." : "처리하지 못했습니다.");
		return "redirect:fielddetail.do?listId=" + listId;
	}

	private static long userId(HttpSession session) {
		return ((MemberTO) session.getAttribute("loginMember")).getUserId();
	}

	/** 구청 계정이면 소속 구 코드, 서울시청이면 null (= 전체) */
	private static String userGu(HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		return m.isGuUser() ? m.getSggCd() : null;
	}
}
