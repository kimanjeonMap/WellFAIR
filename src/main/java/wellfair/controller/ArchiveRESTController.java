package wellfair.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import wellfair.service.ArchiveService;
import wellfair.to.MemberTO;

// 보관함 JSON API. 행 상세는 페이지(archivedetail.do)로 바뀌어 detail.do 는 없앴다
@RestController
public class ArchiveRESTController {

	@Autowired
	ArchiveService archiveService;

	/**
	 * POST api/archive/add.do?bldId&score&criteria : 지도 상세 패널 "보관함에 저장".
	 * 구는 화면이 보낸 sggCd 를 쓰지 않고 DB 의 building.sgg_cd 로 (15단계 A-3). score 가 없으면 서버 계산값.
	 * 응답 {ok, code: ADDED | UPDATED | DUP, msg, bldId, statusCount} (13단계 : 저장만 돼 있고 조사 이력이 없으면 DUP 로 저장 안 함)
	 * 없는 건물 → 400 {ok:false, msg:"없는 건물입니다"}, 구청 계정이 다른 구 건물 → 403 (GlobalExceptionHandler)
	 */
	@PostMapping(value = "api/archive/add.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> add(@RequestParam("bldId") String bldId,
			@RequestParam(value = "sggCd", required = false) String sggCdIgnored,      // 화면 값은 믿지 않는다 (호환용으로 받기만)
			@RequestParam(value = "score", required = false) Integer score,
			@RequestParam(value = "criteria", required = false) String criteria,
			HttpSession session) {
		MemberTO member = (MemberTO) session.getAttribute("loginMember");
		Map<String, Object> out = archiveService.addArchive(member, bldId, score, criteria);
		if (Boolean.TRUE.equals(out.get("ok"))) out.put("statusCount", archiveService.getStatusCount(member.getUserId(), member.getSggCd()));
		return out;
	}
}
