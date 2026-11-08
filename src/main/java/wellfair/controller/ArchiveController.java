package wellfair.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import wellfair.service.ArchiveService;
import wellfair.service.FieldService;
import wellfair.service.MapService;
import wellfair.service.MemberService;
import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.ArchiveDetailTO;
import wellfair.to.MemberTO;

// 보관함 페이지 (Figma 04) + 건물 확인 이력 (Figma 05, archivedetail.do)
@Controller
public class ArchiveController {

	@Autowired
	ArchiveService archiveService;

	@Autowired
	FieldService fieldService;

	@Autowired
	MapService mapService;

	@Autowired
	MemberService memberService;

	// GET archive.do?status=(전체)&gu&dong&adm&q&sort&page + 상세 필터(factors, factorMode, bands, use, resHigh, resOld75, resSevere, visits, savedFrom, savedTo)
	@GetMapping("archive.do")
	public String list(@ModelAttribute("criteria") AnalysisCriteriaTO c, HttpServletRequest req, HttpSession session, Model model) {
		MemberTO member = member(session);
		c.setUserId(member.getUserId());
		if (member.isGuUser()) c.setGu(member.getSggCd());          // 구청 계정 : 소속 구만 (화면의 gu 는 무시)
		if (c.getStatus() == null) c.setStatus("");                  // 기본 탭 = 전체
		if (c.getSort() == null || c.getSort().isBlank()) c.setSort("score");
		if (c.getFilterCount() > 0) c.setFopen(true);               // 필터가 걸려 있으면 펼친 채로
		model.addAttribute("page", archiveService.getArchivePage(c));
		model.addAttribute("statusCount", archiveService.getStatusCount(member.getUserId(), c.getGu()));
		model.addAttribute("surveys", fieldService.getFieldList(member.getUserId(), member.getSggCd(), "doing", null, false));   // "저장 ▾" 드롭다운
		model.addAttribute("dongList", mapService.getDongList());
		model.addAttribute("dongTree", mapService.getDongTree(c.getGu()));                      // 법정동 → 행정동
		model.addAttribute("useList", archiveService.getArchiveUseList(member.getUserId(), c.getGu()));
		model.addAttribute("filterQs", filterQs(req));                                           // 탭 · 페이지 링크가 필터를 유지
		model.addAttribute("factorOpts", ArchiveService.FILTER_FACTOR_LABELS);
		model.addAttribute("factorsSel", csv(c.getFactors()));                                   // ",ugrnd,flood," (JSP fn:contains 용)
		model.addAttribute("bandsSel", csv(c.getBands()));
		model.addAttribute("visitsSel", csv(c.getVisits()));
		return "archive";
	}

	private static String csv(List<String> l) {
		return l == null ? "," : "," + String.join(",", l) + ",";
	}

	/** 지금 요청의 필터 파라미터를 status · page 만 빼고 쿼리스트링으로 (탭 · 페이지 이동 때 그대로 붙인다) */
	private static String filterQs(HttpServletRequest req) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String[]> e : req.getParameterMap().entrySet()) {
			if (e.getKey().equals("status") || e.getKey().equals("page")) continue;
			for (String v : e.getValue()) {
				if (v == null || v.isBlank()) continue;
				if (sb.length() > 0) sb.append('&');
				sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
			}
		}
		return sb.toString();
	}

	// POST archivedelete.do?factorIds=1&factorIds=2 → redirect:archive.do
	@PostMapping("archivedelete.do")
	public String delete(@RequestParam(value = "factorIds", required = false) List<Long> factorIds, HttpSession session, RedirectAttributes ra) {
		MemberTO member = member(session);
		int n = archiveService.deleteArchive(member.getUserId(), factorIds);
		int asked = factorIds == null ? 0 : factorIds.size();
		ra.addFlashAttribute("msg", n + "건을 보관함에서 뺐습니다." + (asked > n ? " (현장확인 리스트에 담긴 " + (asked - n) + "건은 남겨 둠)" : ""));
		return "redirect:archive.do";
	}

	// ================= 05 건물 확인 이력 =================

	// GET archivedetail.do?bldId= → archivedetail.jsp (보관함 행 클릭 · 03-2 주소 클릭)
	@GetMapping("archivedetail.do")
	public String detail(@RequestParam("bldId") String bldId, HttpSession session, Model model, RedirectAttributes ra) {
		MemberTO member = member(session);
		ArchiveDetailTO d = archiveService.getArchiveDetail(bldId, member.getUserId(), userGu(member));
		if (d == null) {
			ra.addFlashAttribute("msg", "건물을 찾을 수 없습니다.");
			return "redirect:archive.do";
		}
		model.addAttribute("d", d);
		model.addAttribute("factorLabels", ArchiveService.FACTOR_LABELS);
		model.addAttribute("depts", ArchiveService.DEPTS);
		model.addAttribute("reqTypes", ArchiveService.REQ_TYPES);
		model.addAttribute("surveys", fieldService.getFieldList(member.getUserId(), member.getSggCd(), "doing", null, false));   // [재확인 요청] 드롭다운
		model.addAttribute("users", memberService.getUserList(member.getSggCd()));                                            // 인수인계 받는 담당자
		return "archivedetail";
	}

	// POST archivecheck.do : 현장 확인 결과 입력 → visit_history + survey_record → redirect:archivedetail.do
	@PostMapping("archivecheck.do")
	public String check(@RequestParam("bldId") String bldId,
			@RequestParam(value = "visitedAt", required = false) String visitedAt,
			@RequestParam(value = "result", defaultValue = "MET") String result,
			@RequestParam(value = "content", required = false) String content,
			@RequestParam(value = "scoreAdj", defaultValue = "0") int scoreAdj,
			@RequestParam(value = "added", required = false) List<String> added,
			@RequestParam Map<String, String> all,
			HttpSession session, RedirectAttributes ra) {
		Map<String, String> checks = new LinkedHashMap<>();                 // 라디오 이름 chk_<요인키> = Y | N | ?
		for (Map.Entry<String, String> e : all.entrySet()) if (e.getKey().startsWith("chk_")) checks.put(e.getKey().substring(4), e.getValue());
		try {
			archiveService.addCheck(member(session).getUserId(), bldId, visitedAt, result, checks, added, content, scoreAdj);
			ra.addFlashAttribute("msg", "현장 확인 결과를 저장했습니다.");
		} catch (IllegalStateException e) {
			ra.addFlashAttribute("msg", e.getMessage());
		}
		return "redirect:archivedetail.do?bldId=" + bldId;
	}

	// POST archivetransfer.do : 부서로 넘기기 (transfer_log INSERT) → redirect:archivedetail.do
	@PostMapping("archivetransfer.do")
	public String transfer(@RequestParam("bldId") String bldId,
			@RequestParam("deptNm") String deptNm,
			@RequestParam(value = "reqType", required = false) String reqType,
			@RequestParam(value = "recordId", required = false) Long recordId,
			HttpSession session, RedirectAttributes ra) {
		boolean ok = archiveService.addTransfer(member(session).getUserId(), bldId, deptNm, reqType, recordId);
		ra.addFlashAttribute("msg", ok ? deptNm + "에 " + (reqType == null ? "점검 요청" : reqType) + "을 넘겼습니다. (주소만 전달)" : "넘기지 못했습니다.");
		return "redirect:archivedetail.do?bldId=" + bldId;
	}

	// POST archivememo.do : 인수인계 메모 (+추가 · 담당자 변경) → redirect:archivedetail.do
	@PostMapping("archivememo.do")
	public String memo(@RequestParam("bldId") String bldId,
			@RequestParam("memo") String memo,
			@RequestParam(value = "toUserId", required = false) Long toUserId,
			HttpSession session, RedirectAttributes ra) {
		boolean ok = archiveService.addMemo(member(session).getUserId(), bldId, toUserId, memo);
		ra.addFlashAttribute("msg", ok ? "인수인계 메모를 남겼습니다." : "메모 내용을 입력하세요.");
		return "redirect:archivedetail.do?bldId=" + bldId;
	}

	private static MemberTO member(HttpSession session) {
		return (MemberTO) session.getAttribute("loginMember");
	}

	/** 구청 계정이면 소속 구 코드, 서울시청이면 null */
	private static String userGu(MemberTO m) {
		return m.isGuUser() ? m.getSggCd() : null;
	}
}
