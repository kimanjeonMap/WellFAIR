package wellfair.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import wellfair.service.MapService;
import wellfair.to.AnalysisCriteriaTO;

// 메인(분석 지도) 페이지. 데이터는 MapRESTController 가 JSON 으로
@Controller
public class MapController {

	@Value("${app.naver.client-id}")
	String naverClientId;

	@Value("${app.analysis.score-min:80}")
	int scoreMin;

	@Autowired
	MapService mapService;

	// GET map.do
	@GetMapping("map.do")
	public String map(@RequestParam(value = "bldId", required = false) String bldId, HttpSession session, Model model) {
		AnalysisCriteriaTO criteria = new AnalysisCriteriaTO();
		criteria.setScoreMin(scoreMin);

		model.addAttribute("openBldId", bldId == null ? "" : bldId);           // 05 [지도에서 보기] : 그 건물 상세를 바로 연다
		model.addAttribute("naverClientId", naverClientId);
		model.addAttribute("criteria", criteria);                              // 왼쪽 패널 기본값 (동 목록은 api/map/dongs.do)
		return "map";
	}
}
