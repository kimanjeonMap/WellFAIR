package wellfair.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;
import wellfair.service.MapService;
import wellfair.to.AnalysisCriteriaTO;

// 메인(분석 지도) 페이지. 데이터는 MapRESTController 가 JSON 으로
@Controller
public class MapController {

	@Value("${app.naver.client-id}")
	String naverClientId;

	@Value("${app.analysis.score-min:60}")
	int scoreMin;

	@Autowired
	MapService mapService;

	// GET map.do
	@GetMapping("map.do")
	public String map(HttpSession session, Model model) {
		AnalysisCriteriaTO criteria = new AnalysisCriteriaTO();
		criteria.setScoreMin(scoreMin);

		model.addAttribute("naverClientId", naverClientId);
		model.addAttribute("criteria", criteria);                              // 왼쪽 패널 기본값
		model.addAttribute("dongList", mapService.getDongList());               // 구·동 선택
		return "map";
	}
}
