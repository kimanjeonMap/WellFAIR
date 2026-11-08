package wellfair.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import wellfair.service.MapService;
import wellfair.service.ResidentService;
import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.BuildingDetailTO;
import wellfair.to.BuildingMarkerTO;
import wellfair.to.BuildingTO;
import wellfair.to.ClusterTO;
import wellfair.to.DongTO;
import wellfair.to.GuTO;
import wellfair.to.MemberTO;
import wellfair.to.PageTO;
import wellfair.to.ResidentTO;

/**
 * 메인(분석 지도) JSON API. 기준값은 AnalysisCriteriaTO 쿼리 파라미터로 받는다.
 *  api/map/gus.do        구 단위 (zoom 14 이하) 경계 + "위험 N곳"   ?기준값
 *  api/map/clusters.do   격자 묶음 (zoom 15~17)                   ?bbox=minLng,minLat,maxLng,maxLat&cell=&기준값
 *  api/map/buildings.do  건물 핀 (zoom 18 이상)                    ?bbox=&limit=1500&기준값
 *  api/map/dongs.do      동 선택 2단계 (법정동 → 행정동)             ?gu
 *  api/map/results.do    오른쪽 결과 리스트                        ?sort&page&q&gu&dong&adm&adms (또는 &bbox) &기준값
 *  api/map/building.do   건물 상세                                ?bldId&기준값
 *  api/map/checked.do    분석 전 현장 확인됨 핀                     ?gu&dong&adms
 *  api/map/residents.do  거주 가구 탭                              ?bldId
 *  api/map/resident.do   정보 확인하기(동·호수)                     ?residentId
 */
@RestController
public class MapRESTController {

	@Autowired
	MapService mapService;

	@Autowired
	ResidentService residentService;

	// 구 단위 요약. 구청 계정은 소속 구만 (11단계 : 다른 두 구의 경계 · 숫자 원은 아예 안 보낸다)
	@GetMapping(value = "api/map/gus.do", produces = "application/json;charset=UTF-8")
	public List<GuTO> gus(@ModelAttribute AnalysisCriteriaTO c, HttpSession session) {
		return mapService.getGuList(scope(c, session));
	}

	@GetMapping(value = "api/map/clusters.do", produces = "application/json;charset=UTF-8")
	public List<ClusterTO> clusters(@ModelAttribute AnalysisCriteriaTO c, HttpSession session) {
		return mapService.getClusterList(scope(c, session));
	}

	@GetMapping(value = "api/map/buildings.do", produces = "application/json;charset=UTF-8")
	public List<BuildingMarkerTO> buildings(@ModelAttribute AnalysisCriteriaTO c, HttpSession session) {
		return mapService.getMarkerList(scope(c, session));
	}

	// 동 선택 2단계 : 법정동 → 행정동 (?gu=, 구청 계정은 소속 구로 고정)
	@GetMapping(value = "api/map/dongs.do", produces = "application/json;charset=UTF-8")
	public List<DongTO> dongs(@ModelAttribute AnalysisCriteriaTO c, HttpSession session) {
		return mapService.getDongTree(scope(c, session).getGu());
	}

	@GetMapping(value = "api/map/checked.do", produces = "application/json;charset=UTF-8")
	public List<BuildingMarkerTO> checked(@ModelAttribute AnalysisCriteriaTO c, HttpSession session) {
		return mapService.getCheckedList(scope(c, session));
	}

	@GetMapping(value = "api/map/results.do", produces = "application/json;charset=UTF-8")
	public PageTO<BuildingTO> results(@ModelAttribute AnalysisCriteriaTO c, HttpSession session) {
		return mapService.getResultPage(scope(c, session));
	}

	@GetMapping(value = "api/map/building.do", produces = "application/json;charset=UTF-8")
	public BuildingDetailTO building(@ModelAttribute AnalysisCriteriaTO c, HttpSession session) {
		c.setUserId(userId(session));
		return mapService.getBuildingDetail(c);
	}

	@GetMapping(value = "api/map/residents.do", produces = "application/json;charset=UTF-8")
	public List<ResidentTO> residents(@RequestParam("bldId") String bldId) {
		return residentService.getResidentList(bldId);
	}

	@GetMapping(value = "api/map/resident.do", produces = "application/json;charset=UTF-8")
	public ResidentTO resident(@RequestParam("residentId") long residentId) {
		return residentService.getResidentLocation(residentId);
	}

	private static Long userId(HttpSession session) {
		Object m = session.getAttribute("loginMember");
		return m == null ? null : ((MemberTO) m).getUserId();
	}

	/** 세션 사용자 반영 : userId + 구청 계정이면 gu 를 소속 구로 강제 (화면에서 보낸 gu 는 무시) */
	private static AnalysisCriteriaTO scope(AnalysisCriteriaTO c, HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		if (m != null) {
			c.setUserId(m.getUserId());
			if (m.isGuUser()) c.setGu(m.getSggCd());
		}
		return c;
	}
}
