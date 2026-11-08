package wellfair.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import wellfair.service.ArchiveService;
import wellfair.service.FieldService;
import wellfair.service.MapService;
import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.MemberTO;

// 보관함 페이지 (Figma 04). 행 상세는 ArchiveRESTController api/archive/detail.do
@Controller
public class ArchiveController {

	@Autowired
	ArchiveService archiveService;

	@Autowired
	FieldService fieldService;

	@Autowired
	MapService mapService;

	// GET archive.do?status=&gu&dong&q&sort&page
	@GetMapping("archive.do")
	public String list(@ModelAttribute("criteria") AnalysisCriteriaTO c, HttpSession session, Model model) {
		MemberTO member = (MemberTO) session.getAttribute("loginMember");
		c.setUserId(member.getUserId());
		if (member.isGuUser()) c.setGu(member.getSggCd());          // 구청 계정 : 소속 구만 (화면의 gu 는 무시)
		if (c.getStatus() == null || c.getStatus().isBlank()) c.setStatus("TODO");
		if (c.getSort() == null || c.getSort().isBlank()) c.setSort("score");
		model.addAttribute("page", archiveService.getArchivePage(c));
		model.addAttribute("statusCount", archiveService.getStatusCount(member.getUserId(), c.getGu()));
		model.addAttribute("surveys", fieldService.getFieldList(member.getUserId(), member.getSggCd(), "doing", null, false));   // "저장 ▾" 드롭다운
		model.addAttribute("dongList", mapService.getDongList());
		return "archive";
	}

	// POST archivedelete.do?factorIds=1&factorIds=2 → redirect:archive.do
	@PostMapping("archivedelete.do")
	public String delete(@RequestParam(value = "factorIds", required = false) List<Long> factorIds, HttpSession session, RedirectAttributes ra) {
		MemberTO member = (MemberTO) session.getAttribute("loginMember");
		int n = archiveService.deleteArchive(member.getUserId(), factorIds);
		int asked = factorIds == null ? 0 : factorIds.size();
		ra.addFlashAttribute("msg", n + "건을 보관함에서 뺐습니다." + (asked > n ? " (현장확인 리스트에 담긴 " + (asked - n) + "건은 남겨 둠)" : ""));
		return "redirect:archive.do";
	}
}
