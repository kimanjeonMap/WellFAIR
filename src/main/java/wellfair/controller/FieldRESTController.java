package wellfair.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import wellfair.service.FieldService;
import wellfair.service.RouteService;
import wellfair.to.MemberTO;
import wellfair.to.WalkPathTO;

// 현장확인 JSON API (myabweb EmpRESTController 패턴)
@RestController
public class FieldRESTController {

	@Autowired
	FieldService fieldService;

	@Autowired
	RouteService routeService;

<<<<<<< HEAD
	// POST api/field/walk.do?lngs=..&lats=.. (출발점부터 방문 순서대로) : 현장확인 동선 도보 경로 (TMAP). appKey 는 서버에만
	@PostMapping(value = "api/field/walk.do", produces = "application/json;charset=UTF-8")
	public WalkPathTO walk(@RequestParam("lngs") double[] lngs, @RequestParam("lats") double[] lats) {
		return routeService.walkPath(lngs, lats);
=======
	// POST api/field/walk.do?lngs=..&lats=..&mode=walk|transit|car (출발점부터 방문 순서대로) : 현장확인 동선 경로 (TMAP). appKey 는 서버에만
	@PostMapping(value = "api/field/walk.do", produces = "application/json;charset=UTF-8")
	public WalkPathTO walk(@RequestParam("lngs") double[] lngs, @RequestParam("lats") double[] lats,
			@RequestParam(value = "mode", defaultValue = "walk") String mode) {
		return routeService.walkPath(lngs, lats, mode);
>>>>>>> 41792602687852f26f9968d59f66ac24227def2c
	}

	// POST api/field/mode.do?listId&moveMode(walk|transit|car) : 03-2 소요시간 카드에서 이동수단 바꾸면 저장
	@PostMapping(value = "api/field/mode.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> mode(@RequestParam("listId") long listId, @RequestParam("moveMode") String moveMode, HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		boolean ok = fieldService.updateMoveMode(m.getUserId(), m.isGuUser() ? m.getSggCd() : null, listId, moveMode);
		Map<String, Object> out = new HashMap<>();
		out.put("ok", ok);
		out.put("moveMode", FieldService.moveModeOf(moveMode));
		return out;
	}
}
