package wellfair.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import wellfair.service.ArchiveService;
import wellfair.service.MapService;
import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.BuildingDetailTO;
import wellfair.to.MemberTO;

// 보관함 JSON API
@RestController
public class ArchiveRESTController {

	@Autowired
	ArchiveService archiveService;

	@Autowired
	MapService mapService;

	// POST api/archive/add.do?bldId&sggCd&score&criteria : 지도 상세 패널 "보관함에 저장"
	@PostMapping(value = "api/archive/add.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> add(@RequestParam("bldId") String bldId,
			@RequestParam("sggCd") String sggCd,
			@RequestParam(value = "score", required = false) Integer score,
			@RequestParam(value = "criteria", required = false) String criteria,
			HttpSession session) {
		MemberTO member = (MemberTO) session.getAttribute("loginMember");
		Map<String, Object> out = new HashMap<>();
		if (member.isGuUser() && !member.getSggCd().equals(sggCd)) {       // 구청 계정은 소속 구 건물만
			out.put("ok", false);
			out.put("msg", member.getGuName() + " 건물만 보관함에 저장할 수 있습니다.");
			return out;
		}
		archiveService.addArchive(member.getUserId(), bldId, sggCd, score, criteria);
		out.put("ok", true);
		out.put("statusCount", archiveService.getStatusCount(member.getUserId(), member.getSggCd()));
		return out;
	}

	// GET api/archive/detail.do?bldId : 보관함 행 클릭 → archive.jsp 안 상세 패널 (building-panel.jspf 공용)
	@GetMapping(value = "api/archive/detail.do", produces = "application/json;charset=UTF-8")
	public BuildingDetailTO detail(@ModelAttribute AnalysisCriteriaTO c, HttpSession session) {
		MemberTO member = (MemberTO) session.getAttribute("loginMember");
		c.setUserId(member.getUserId());
		return mapService.getBuildingDetail(c);
	}
}
