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

	// 법정동 목록 (보관함 필터 등)
	public List<DongTO> getDongList() {
		return dao.getDongList(null);
	}

	/** 동 선택 2단계 (5-1) : 법정동 줄마다 행정동 목록(adms)을 단다. gu 가 없으면 3개 구 전체 */
	public List<DongTO> getDongTree(String gu) {
		List<DongTO> emds = dao.getDongList(gu);
		Map<String, DongTO> byKey = new HashMap<>();
		for (DongTO e : emds) byKey.put(e.getSggCd() + "|" + e.getEmdNm(), e);
		for (DongTO a : dao.getAdmList(gu)) {
			DongTO e = byKey.get(a.getSggCd() + "|" + a.getEmdNm());
			if (e != null) e.getAdms().add(a);
		}
		return emds;
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

	// 오른쪽 결과 리스트. 범위 = gu/dong/adm (기본 구 전체) 또는 bbox ("지도 화면 안", [이 지역에서 재검색])
	public PageTO<BuildingTO> getResultPage(AnalysisCriteriaTO c) {
		c.applyBbox();
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

	/** 점수 구성 = 위험 유형 4묶음 (10단계 규칙). 주거 공간 · 경제 여건 = 하나라도 맞으면 전부, 건물 상태 · 침수 위험 = 맞은 수 ÷ 켜진 수 */
	private List<ScoreItemTO> buildScoreItems(BuildingTO b, AnalysisCriteriaTO c) {
		List<ScoreItemTO> items = new ArrayList<>();
		items.add(anyGroup("space", "주거 공간", c.getSpaceW(),
				new Object[][] { { "반지하", b.getPtUgrnd(), c.isUgrndOn() }, { "옥탑", b.getPtRooftop(), c.isRooftopOn() }, { "고시원", b.getPtGosiwon(), c.isGosiwonOn() } }));
		items.add(ratioGroup("bld", "건물 상태", c.getBldW(),
				new Object[][] { { "노후 " + c.getOldYears() + "년+" + (b.getBldAge() != null ? " (" + b.getBldAge() + "년 경과)" : ""), b.getPtOld(), c.isOldOn() }, { "위반건축물", b.getPtViol(), c.isViolOn() } }));
		items.add(ratioGroup("flood", "침수 위험", c.getFloodW(),
				new Object[][] { { "침수 " + b.getFloodCnt() + "회", b.getPtFlood(), c.isFloodOn() }, { "저지대 해발 " + b.getElevM() + "m", b.getPtLow(), c.isLowOn() } }));
		items.add(anyGroup("econ", "경제 여건", c.getEconW(),
				new Object[][] { { "저가 주택" + (b.getPricePct() != null ? " (백분위 " + b.getPricePct() + "%)" : ""), b.getPtCheap(), c.isCheapOn() } }));
		return items;
	}

	/** 하나라도 맞으면 묶음 가중치 전부. members = { 요인 이름, 요인 점수(맞으면 > 0), 켜짐 } */
	private static ScoreItemTO anyGroup(String key, String label, int weight, Object[][] members) {
		List<String> hits = new ArrayList<>();
		boolean anyOn = false;
		for (Object[] m : members) {
			anyOn |= (Boolean) m[2];
			if ((Boolean) m[2] && (Integer) m[1] > 0) hits.add((String) m[0]);
		}
		int points = hits.isEmpty() ? 0 : weight;
		String desc = !anyOn ? "분석 조건에서 제외" : hits.isEmpty() ? "해당 없음" : String.join(" · ", hits);
		return new ScoreItemTO(key, label, points, anyOn ? weight : 0, points > 0, desc);
	}

	/** 묶음 가중치 × (맞은 요인 수 ÷ 켜진 요인 수). "노후 · 2개 중 1개" */
	private static ScoreItemTO ratioGroup(String key, String label, int weight, Object[][] members) {
		List<String> hits = new ArrayList<>();
		int on = 0;
		for (Object[] m : members) {
			if (!(Boolean) m[2]) continue;
			on++;
			if ((Integer) m[1] > 0) hits.add((String) m[0]);
		}
		int points = on == 0 ? 0 : (int) Math.round(weight * (double) hits.size() / on);
		String desc = on == 0 ? "분석 조건에서 제외"
				: (hits.isEmpty() ? "해당 없음" : String.join(" · ", hits)) + " · " + on + "개 중 " + hits.size() + "개";
		return new ScoreItemTO(key, label, points, on == 0 ? 0 : weight, points > 0, desc);
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
