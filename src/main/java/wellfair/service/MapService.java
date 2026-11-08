package wellfair.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import wellfair.dao.BuildingDAO;
import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.BuildingDetailTO;
import wellfair.to.BuildingMarkerTO;
import wellfair.to.BuildingTO;
import wellfair.to.ClusterTO;
import wellfair.to.DongTO;
import wellfair.to.GuTO;
import wellfair.to.PageTO;
import wellfair.to.ResidentCountTO;
import wellfair.to.ScoreItemTO;

// 메인(분석 지도). BuildingDAO 는 여기서만. 거주자는 ResidentService 를 거친다
@Service
public class MapService {

	/** 격자의 기준위도 (살피구로 refLat : 전체 건물 평균 위도로 고정) */
	static final double REF_LAT = 37.5;

	static final Map<String, String> GU_NAME = new LinkedHashMap<>();
	static {
		GU_NAME.put("11530", "구로구");
		GU_NAME.put("11470", "양천구");
		GU_NAME.put("11560", "영등포구");
	}

	@Autowired
	BuildingDAO dao;

	@Autowired
	ResidentService residentService;

	private Map<String, List<double[]>> ringCache;   // sgg_cd → 볼록 껍질 [[lng,lat],...] (한 번 계산)

	// 구·동 선택 목록
	public List<DongTO> getDongList() {
		return dao.getDongList();
	}

	/** 구 단위 (zoom 14 이하) : 경계 + "위험 N곳". 경계는 building 좌표의 볼록 껍질 */
	public List<GuTO> getGuList(AnalysisCriteriaTO c) {
		Map<String, List<double[]>> rings = rings();
		List<GuTO> out = new ArrayList<>();
		for (GuTO g : dao.getGuSummary(c)) {
			g.setName(GU_NAME.getOrDefault(g.getSggCd(), g.getSggCd()));
			g.setRing(rings.get(g.getSggCd()));
			out.add(g);
		}
		return out;
	}

	/** 격자 묶음 (살피구로 AnalysisService.clusters() 를 SQL 로) */
	public List<ClusterTO> getClusterList(AnalysisCriteriaTO c) {
		c.applyBbox();
		if (c.getMinLat() == null) return new ArrayList<>();
		double cell = Math.max(1e-5, Math.min(c.getCell(), 5.0));
		double cellLat = cell * Math.cos(Math.toRadians(REF_LAT));
		c.setCell(cell);
		c.setCellLat(cellLat);
		if (c.isSnap()) {   // bbox 를 칸 경계까지 넓혀서 가장자리 묶음도 칸 전체 건물 수를 보여준다
			c.setMinLng(Math.floor(c.getMinLng() / cell) * cell);
			c.setMinLat(Math.floor(c.getMinLat() / cellLat) * cellLat);
			c.setMaxLng((Math.floor(c.getMaxLng() / cell) + 1) * cell);
			c.setMaxLat((Math.floor(c.getMaxLat() / cellLat) + 1) * cellLat);
		}
		List<ClusterTO> list = dao.getClusterList(c);
		for (ClusterTO cl : list) {
			cl.setCell(new double[] { cl.getIx() * cell, cl.getIy() * cellLat, (cl.getIx() + 1) * cell, (cl.getIy() + 1) * cellLat });
			if (cl.getSize() == 1) {   // 칸에 건물이 1동이면 핀으로
				BuildingMarkerTO p = new BuildingMarkerTO();
				p.setBldId(cl.getOneBldId());
				p.setLat(cl.getLat());
				p.setLng(cl.getLng());
				p.setScore(cl.getMaxScore());
				p.setBldNm(cl.getOneBldNm());
				p.setAddr(cl.getOneAddr());
				p.setUseNm(cl.getOneUseNm());
				cl.setPoint(p);
			}
		}
		return list;
	}

	// 확대 : bbox 안 건물 핀 (점수 높은 순 limit)
	public List<BuildingMarkerTO> getMarkerList(AnalysisCriteriaTO c) {
		c.applyBbox();
		if (c.getMinLat() == null) return new ArrayList<>();   // 범위 없이는 조회하지 않음
		if (c.getLimit() <= 0 || c.getLimit() > 3000) c.setLimit(1500);
		return dao.getMarkerList(c);
	}

	// 분석 전 : 현장 확인됨 핀
	public List<BuildingMarkerTO> getCheckedList(AnalysisCriteriaTO c) {
		return dao.getCheckedList(c);
	}

	// 오른쪽 결과 리스트
	public PageTO<BuildingTO> getResultPage(AnalysisCriteriaTO c) {
		if (c.getSize() <= 0 || c.getSize() > 100) c.setSize(20);
		int total = dao.getResultCount(c);
		List<BuildingTO> list = dao.getResultList(c);
		int rank = c.getOffset();
		for (BuildingTO b : list) b.setRank(++rank);
		return new PageTO<>(c.getPage(), c.getSize(), total, list);
	}

	// 건물 상세 : 건물 + 점수 구성 + 거주자 집계
	public BuildingDetailTO getBuildingDetail(AnalysisCriteriaTO c) {
		BuildingTO b = dao.getBuilding(c);
		if (b == null) return null;
		BuildingDetailTO d = new BuildingDetailTO();
		d.setBuilding(b);
		d.setScoreItems(buildScoreItems(b, c));
		ResidentCountTO rc = residentService.getResidentCount(b.getBldId());
		d.setResidents(rc);
		return d;
	}

	/** 점수 구성 (요인별 점수는 SQL 이 계산, 여기서는 라벨·근거만 붙인다) */
	private List<ScoreItemTO> buildScoreItems(BuildingTO b, AnalysisCriteriaTO c) {
		List<ScoreItemTO> items = new ArrayList<>();
		items.add(item("ugrnd", "지하층 (지하 거주 등록)", b.getPtUgrnd(), c.getUgrndW(), c.isUgrndOn(),
				b.getUgrndRes() == 1 ? "지하층 거주 등록 건물" : "지하 거주 없음"));
		items.add(item("gosiwon", "고시원 (다중생활시설)", b.getPtGosiwon(), c.getGosiwonW(), c.isGosiwonOn(),
				"고시원".equals(b.getSpType()) ? "특수 유형 : 고시원" + (b.getSpNm() == null ? "" : " · " + b.getSpNm()) : "해당 없음"));
		items.add(item("rooftop", "옥탑 (옥탑층 거주자)", b.getPtRooftop(), c.getRooftopW(), c.isRooftopOn(),
				b.getPtRooftop() > 0 ? "옥탑층 거주 가구 있음" : "옥탑층 거주 없음"));
		items.add(item("viol", "위반건축물", b.getPtViol(), c.getViolW(), c.isViolOn(),
				"1".equals(b.getViolYn()) ? "건축물대장 위반건축물 등재" : b.getViolYn() == null ? "대장 미연계" : "해당 없음"));
		items.add(item("old", "노후 (준공 " + c.getOldYears() + "년+)", b.getPtOld(), c.getOldW(), c.isOldOn(),
				b.getBldAge() == null ? "사용승인일 자료 없음" : b.getUseaprDay().substring(0, 4) + "년 준공, " + b.getBldAge() + "년 경과"));
		items.add(item("flood", "침수흔적 (" + c.getFloodCnt() + "회 이상)", b.getPtFlood(), c.getFloodW(), c.isFloodOn(),
				"침수흔적 중첩 " + b.getFloodCnt() + "회" + (b.getFloodDepth() > 0 ? " · 깊이 " + b.getFloodDepth() + "m" : "")));
		items.add(item("low", "저지대 (" + fmt(c.getLowElev()) + "m 이하)", b.getPtLow(), c.getLowW(), c.isLowOn(),
				"해발 " + b.getElevM() + "m · 경사 " + b.getSlopeDeg() + "°"));
		items.add(item("cheap", "저가 월세 밀집 (" + fmt(c.getCheapPct()) + "% 이하)", b.getPtCheap(), c.getCheapW(), c.isCheapOn(),
				b.getPricePct() == null ? "공시가격 자료 없음" : "가격 백분위 " + b.getPricePct() + "%"));
		return items;
	}

	private static ScoreItemTO item(String key, String label, int points, int weight, boolean on, String desc) {
		return new ScoreItemTO(key, label, points, on ? weight : 0, points > 0, on ? desc : "분석 조건에서 제외");
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
	}

	// ---------- 구 경계 (볼록 껍질, 한 번만) ----------
	private synchronized Map<String, List<double[]>> rings() {
		if (ringCache != null) return ringCache;
		Map<String, List<double[]>> pts = new HashMap<>();
		for (DongTO p : dao.getCoordList()) {
			pts.computeIfAbsent(p.getSggCd(), k -> new ArrayList<>()).add(new double[] { p.getLng(), p.getLat() });
		}
		Map<String, List<double[]>> out = new HashMap<>();
		pts.forEach((k, v) -> out.put(k, convexHull(v)));
		ringCache = out;
		return out;
	}

	/** Andrew monotone chain. 입력 [lng, lat], 출력은 닫힌 고리 (첫 점 = 끝 점) */
	static List<double[]> convexHull(List<double[]> in) {
		List<double[]> p = new ArrayList<>(in);
		p.sort(Comparator.<double[]>comparingDouble(a -> a[0]).thenComparingDouble(a -> a[1]));
		if (p.size() < 3) return p;
		List<double[]> lower = new ArrayList<>(), upper = new ArrayList<>();
		for (double[] q : p) {
			while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), q) <= 0) lower.remove(lower.size() - 1);
			lower.add(q);
		}
		for (int i = p.size() - 1; i >= 0; i--) {
			double[] q = p.get(i);
			while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), q) <= 0) upper.remove(upper.size() - 1);
			upper.add(q);
		}
		List<double[]> hull = new ArrayList<>(lower.subList(0, lower.size() - 1));
		hull.addAll(upper.subList(0, upper.size() - 1));
		hull.add(hull.get(0));
		return hull;
	}

	private static double cross(double[] o, double[] a, double[] b) {
		return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
	}
}
