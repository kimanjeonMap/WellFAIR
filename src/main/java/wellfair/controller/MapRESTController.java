package wellfair.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import wellfair.common.ForbiddenException;
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
 *
 * 구청 계정 (15단계 A) : 목록류는 gu 를 세션 구로 강제(scope), 건물·가구 상세는 그 건물의 sgg_cd 가 내 구와 다르면 403 {ok:false, msg}.
 * 가중치 (15단계 B) : 합 100 · 0~100 이 아니면 400 (MapService.assertWeights). 응답 모양은 GlobalExceptionHandler.
 */
@RestController
public class MapRESTController {

	static final String MSG_OUT_OF_GU = "관할 구역의 건물만 볼 수 있습니다.";

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

	// 건물 상세. 구청 계정은 소속 구 건물만 (다른 구 건물이면 403)
	@GetMapping(value = "api/map/building.do", produces = "application/json;charset=UTF-8")
	public BuildingDetailTO building(@ModelAttribute AnalysisCriteriaTO c, HttpSession session) {
		MemberTO m = member(session);
		c.setUserId(m.getUserId());
		MapService.assertWeights(c);
		BuildingDetailTO d = mapService.getBuildingDetail(c);
		if (d != null) assertMyGu(m, d.getBuilding().getSggCd());
		return d;
	}

	// 거주 가구 탭. 구청 계정은 소속 구 건물만
	@GetMapping(value = "api/map/residents.do", produces = "application/json;charset=UTF-8")
	public List<ResidentTO> residents(@RequestParam("bldId") String bldId, HttpSession session) {
		assertMyGu(member(session), guOfBuilding(bldId));
		return residentService.getResidentList(bldId);
	}

	// 정보 확인하기 (동·호수). residentId → bld_id → sgg_cd 로 관할 확인
	@GetMapping(value = "api/map/resident.do", produces = "application/json;charset=UTF-8")
	public ResidentTO resident(@RequestParam("residentId") long residentId, HttpSession session) {
		String bldId = residentService.getResidentBldId(residentId);
		if (bldId == null) throw new IllegalArgumentException("없는 가구입니다.");
		assertMyGu(member(session), guOfBuilding(bldId));
		return residentService.getResidentLocation(residentId);
	}

	private String guOfBuilding(String bldId) {
		String sgg = mapService.getBuildingSgg(bldId);
		if (sgg == null) throw new IllegalArgumentException("없는 건물입니다.");
		return sgg;
	}

	/** 구청 계정인데 건물의 구가 내 구와 다르면 403 */
	private static void assertMyGu(MemberTO m, String sggCd) {
		if (m != null && m.isGuUser() && !m.getSggCd().equals(sggCd)) throw new ForbiddenException(MSG_OUT_OF_GU);
	}

	private static MemberTO member(HttpSession session) {
		return (MemberTO) session.getAttribute("loginMember");
	}

	/** 세션 사용자 반영 : userId + 구청 계정이면 gu 를 소속 구로 강제 (화면에서 보낸 gu 는 무시) + 가중치 확인 */
	private static AnalysisCriteriaTO scope(AnalysisCriteriaTO c, HttpSession session) {
		MemberTO m = member(session);
		if (m != null) {
			c.setUserId(m.getUserId());
			if (m.isGuUser()) c.setGu(m.getSggCd());
		}
		MapService.assertWeights(c);
		return c;
	}
}
