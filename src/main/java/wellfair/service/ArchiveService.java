package wellfair.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import wellfair.dao.ArchiveDAO;
import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.ArchiveDetailTO;
import wellfair.to.ArchiveRowTO;
import wellfair.to.ArchiveTO;
import wellfair.to.BuildingDetailTO;
import wellfair.to.DashboardTO;
import wellfair.to.FieldCheckTO;
import wellfair.to.MemoTO;
import wellfair.to.PageTO;
import wellfair.to.RecordTO;
import wellfair.to.StatusCountTO;
import wellfair.to.TransferTO;

/**
 * 보관함 (개인 단위 = vulnerability_list.user_id) + 05 건물 확인 이력.
 * ArchiveDAO 는 여기서만. 건물·점수는 MapService, 방문·기록 INSERT 는 FieldService 를 거친다 (Service → Service).
 */
@Service
public class ArchiveService {

	/** factor_check JSON 의 키 = 점수 요인 키 (05 현장 체크 라디오 · 타임라인 문구) */
	public static final Map<String, String> FACTOR_LABELS = new LinkedHashMap<>();
	static {
		FACTOR_LABELS.put("ugrnd", "지하층");
		FACTOR_LABELS.put("gosiwon", "고시원");
		FACTOR_LABELS.put("rooftop", "옥탑");
		FACTOR_LABELS.put("viol", "위반건축물");
		FACTOR_LABELS.put("old", "노후");
		FACTOR_LABELS.put("flood", "침수");
		FACTOR_LABELS.put("low", "저지대");
		FACTOR_LABELS.put("cheap", "저가");
	}
	/** 부서로 넘기기 : 넘길 부서 · 요청 종류 (transfer_log.dept_nm / req_type) */
	public static final List<String> DEPTS = Arrays.asList("주택과", "건축과", "복지정책과", "사회복지과", "안전총괄과");
	public static final List<String> REQ_TYPES = Arrays.asList("점검 요청", "조회 요청");

	private static final ObjectMapper JSON = new ObjectMapper();

	@Autowired
	ArchiveDAO dao;

	@Autowired
	MapService mapService;

	@Autowired
	FieldService fieldService;

	// 탭 숫자 (신규 / 진행중 / 완료됨). gu 가 있으면 그 구의 보관함만 (구청 계정 = 소속 구, 서울시청 = 고른 구)
	public StatusCountTO getStatusCount(long userId, String gu) {
		StatusCountTO cnt = dao.getStatusCount(userId, gu);
		return cnt == null ? new StatusCountTO() : cnt;
	}

	/**
	 * 보관함에 건물 저장.
	 * 같은 구 · 같은 기준(JSON)의 분석 리스트가 있으면 거기에, 없으면 리스트를 새로 만들어 넣는다 (분석 1회 = 리스트 1개).
	 */
	public void addArchive(long userId, String bldId, String sggCd, Integer score, String criteriaJson) {
		ArchiveTO to = new ArchiveTO();
		to.setUserId(userId);
		to.setSggCd(sggCd);
		to.setCriteria(criteriaJson);

		ArchiveTO list = dao.getListByCriteria(to);
		if (list == null) {
			to.setTitle(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 분석 · " + guName(sggCd));
			dao.addList(to);                       // vulnerabilityId 채워짐
		} else {
			to.setVulnerabilityId(list.getVulnerabilityId());
		}

		to.setBldId(bldId);
		to.setVulnerabilityScore(score);
		to.setVulnerabilityGrade(grade(score));
		dao.addFactor(to);
	}

	/** [상세 필터] 취약 요인 키 허용 목록 (mapper 에서 열 이름 t.pt_<키> 로 쓰므로 여기서 거른다) */
	public static final List<String> FILTER_FACTORS = Arrays.asList("ugrnd", "rooftop", "gosiwon", "viol", "old", "flood", "low");
	public static final Map<String, String> FILTER_FACTOR_LABELS = new LinkedHashMap<>();
	static {
		FILTER_FACTOR_LABELS.put("ugrnd", "지하");
		FILTER_FACTOR_LABELS.put("rooftop", "옥탑");
		FILTER_FACTOR_LABELS.put("gosiwon", "고시원");
		FILTER_FACTOR_LABELS.put("viol", "위반");
		FILTER_FACTOR_LABELS.put("old", "노후");
		FILTER_FACTOR_LABELS.put("flood", "침수");
		FILTER_FACTOR_LABELS.put("low", "저지대");
	}

	// [상세 필터] 건물 용도 목록
	public List<String> getArchiveUseList(long userId, String gu) {
		return dao.getArchiveUseList(userId, gu);
	}

	// 보관함 목록 한 페이지 (archive.do). 상세 필터 값은 여기서 정리한다
	public PageTO<ArchiveRowTO> getArchivePage(AnalysisCriteriaTO c) {
		if (c.getSize() <= 0 || c.getSize() > 100) c.setSize(20);
		if (c.getFactors() != null) {
			List<String> ok = new ArrayList<>();
			for (String k : c.getFactors()) if (FILTER_FACTORS.contains(k)) ok.add(k);
			c.setFactors(ok);
		}
		if (c.getBands() != null) {
			List<String> ok = new ArrayList<>();
			for (String b : c.getBands()) if ("80".equals(b) || "60".equals(b) || "40".equals(b)) ok.add(b);
			c.setBands(ok);
		}
		if (c.getVisits() != null) {
			List<String> ok = new ArrayList<>();
			for (String v : c.getVisits()) if ("MET".equals(v) || "ABSENT".equals(v) || "REFUSED".equals(v)) ok.add(v);
			c.setVisits(ok);
		}
		if (c.getSavedFrom() != null && !c.getSavedFrom().matches("\\d{4}-\\d{2}-\\d{2}")) c.setSavedFrom(null);
		if (c.getSavedTo() != null && !c.getSavedTo().matches("\\d{4}-\\d{2}-\\d{2}")) c.setSavedTo(null);
		int total = dao.getArchiveTotal(c);
		List<ArchiveRowTO> list = dao.getArchiveList(c);
		return new PageTO<>(c.getPage(), c.getSize(), total, list);
	}

	// 보관함에서 빼기 (현장확인 리스트에 담긴 건물은 남는다). 뺀 건수 리턴
	public int deleteArchive(long userId, List<Long> factorIds) {
		if (factorIds == null || factorIds.isEmpty()) return 0;
		return dao.deleteFactors(userId, factorIds);
	}

	// 대시보드 조각
	public List<DashboardTO.DongStatusTO> getStatusByDong(long userId, String gu) {
		return dao.getStatusByDong(userId, gu);
	}

	public List<DashboardTO.MonthCountTO> getMonthlyDone(long userId, int months, String gu) {
		return dao.getMonthlyDone(userId, months, gu);
	}

	// ================= 05 건물 확인 이력 (archivedetail.do) =================

	/** 한 화면 분량. 구청 계정은 소속 구 건물만 (userGu). 건물이 없으면 null */
	public ArchiveDetailTO getArchiveDetail(String bldId, long userId, String userGu) {
		AnalysisCriteriaTO c = new AnalysisCriteriaTO();
		c.setBldId(bldId);
		c.setUserId(userId);
		if (userGu != null && !userGu.isBlank()) c.setGu(userGu);
		BuildingDetailTO bd = mapService.getBuildingDetail(c);
		if (bd == null) return null;

		ArchiveDetailTO d = new ArchiveDetailTO();
		d.setBuilding(bd.getBuilding());
		d.setScoreItems(bd.getScoreItems());
		d.setResidents(bd.getResidents());
		d.setFactor(dao.getFactorByBld(userId, bldId));
		d.setPlanId(dao.getPlanByBld(userId, bldId));

		List<RecordTO> recs = dao.getRecordList(bldId);
		int adj = 0;
		for (RecordTO r : recs) {
			parseCheck(r);
			if (r.getScoreAdj() != null) adj += r.getScoreAdj();
		}
		d.setRecords(recs);
		d.setTransfers(dao.getTransferList(bldId));
		d.setMemos(dao.getMemoList(bldId));

		d.setCheckCnt(recs.size());
		if (!recs.isEmpty()) {
			RecordTO last = recs.get(0);
			d.setLastCheckedAt(last.getVisitedAt());
			d.setLastUserNm(last.getUserNm());
			d.setLastRankNm(last.getRankNm());
		}
		d.setScoreAdj(adj);
		if (!d.getTransfers().isEmpty()) {
			TransferTO t = d.getTransfers().get(0);
			d.setTransferDept(t.getDeptNm() + " " + t.getReqType());
		}
		return d;
	}

	/** factor_check JSON → checks(키→Y/N/?) · added · checkText · confirmed */
	@SuppressWarnings("unchecked")
	private static void parseCheck(RecordTO r) {
		if (r.getFactorCheck() != null && !r.getFactorCheck().isBlank()) {
			try {
				Map<String, Object> m = JSON.readValue(r.getFactorCheck(), Map.class);
				for (Map.Entry<String, Object> e : m.entrySet()) {
					if ("added".equals(e.getKey())) {
						if (e.getValue() instanceof List) for (Object o : (List<Object>) e.getValue()) if (o != null) r.getAdded().add(String.valueOf(o));
					} else if (e.getValue() != null) {
						r.getChecks().put(e.getKey(), String.valueOf(e.getValue()));
					}
				}
			} catch (Exception ignore) {
				// 깨진 JSON 은 원문만 둔다
			}
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : r.getChecks().entrySet()) {
			String label = FACTOR_LABELS.getOrDefault(e.getKey(), e.getKey());
			String v = "Y".equals(e.getValue()) ? "맞음" : "N".equals(e.getValue()) ? "아님" : "판단 어려움";
			if (sb.length() > 0) sb.append(" · ");
			sb.append(label).append(' ').append(v);
			if ("Y".equals(e.getValue())) r.getConfirmed().add(label);
		}
		if (!r.getAdded().isEmpty()) {
			if (sb.length() > 0) sb.append(" · ");
			sb.append("새로 발견: ").append(String.join(", ", r.getAdded()));
		}
		r.setCheckText(sb.toString());
	}

	/**
	 * 현장 확인 결과 입력 (POST archivecheck.do) = visit_history 1건 + survey_record 1건.
	 * 그 건물이 내 현장확인 리스트에 없으면 IllegalStateException("현장확인 리스트에 먼저 담아 주세요").
	 */
	public void addCheck(long userId, String bldId, String visitedAt, String result, Map<String, String> checks,
			List<String> added, String content, Integer scoreAdj) {
		Long planId = dao.getPlanByBld(userId, bldId);
		if (planId == null) throw new IllegalStateException("현장확인 리스트에 먼저 담아 주세요.");

		Map<String, Object> fc = new LinkedHashMap<>();
		if (checks != null) for (Map.Entry<String, String> e : checks.entrySet()) {
			String v = e.getValue();
			if (FACTOR_LABELS.containsKey(e.getKey()) && v != null && (v.equals("Y") || v.equals("N") || v.equals("?"))) fc.put(e.getKey(), v);
		}
		List<String> add = new ArrayList<>();
		if (added != null) for (String a : added) if (a != null && !a.isBlank()) add.add(a.trim());
		if (!add.isEmpty()) fc.put("added", add);

		FieldCheckTO c = new FieldCheckTO();
		c.setPlanId(planId);
		c.setUserId(userId);
		c.setBldId(bldId);
		c.setResult(result == null || result.isBlank() ? "MET" : result);
		c.setContent(content == null || content.isBlank() ? "현장 확인" : content.trim());
		c.setVisitedAt(visitedAt == null || visitedAt.isBlank() ? null : visitedAt.trim().replace('T', ' '));
		try { c.setFactorCheck(fc.isEmpty() ? null : JSON.writeValueAsString(fc)); } catch (Exception e) { c.setFactorCheck(null); }
		int a = scoreAdj == null ? 0 : Math.max(-10, Math.min(10, scoreAdj));
		c.setScoreAdj(a == 0 ? null : a);
		fieldService.addCheck(c);
	}

	// 부서로 넘기기 (transfer_log INSERT)
	public boolean addTransfer(long userId, String bldId, String deptNm, String reqType, Long recordId) {
		if (deptNm == null || deptNm.isBlank()) return false;
		TransferTO t = new TransferTO();
		t.setUserId(userId);
		t.setBldId(bldId);
		t.setRecordId(recordId);
		t.setDeptNm(deptNm.trim());
		t.setReqType(reqType == null || reqType.isBlank() ? REQ_TYPES.get(0) : reqType.trim());
		return dao.addTransfer(t) > 0;
	}

	// 인수인계 메모 (+추가 · 담당자 변경)
	public boolean addMemo(long userId, String bldId, Long toUserId, String memo) {
		if (memo == null || memo.isBlank()) return false;
		MemoTO m = new MemoTO();
		m.setBldId(bldId);
		m.setFromUserId(userId);
		m.setToUserId(toUserId);
		m.setMemo(memo.trim());
		return dao.addMemo(m) > 0;
	}

	static String grade(Integer score) {
		if (score == null) return null;
		if (score >= 80) return "매우높음";
		if (score >= 60) return "높음";
		if (score >= 40) return "보통";
		return "낮음";
	}

	public static String guName(String sggCd) {
		if ("11530".equals(sggCd)) return "구로구";
		if ("11470".equals(sggCd)) return "양천구";
		if ("11560".equals(sggCd)) return "영등포구";
		return sggCd == null ? "" : sggCd;
	}
}
