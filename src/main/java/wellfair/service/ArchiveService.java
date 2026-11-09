package wellfair.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import wellfair.common.ForbiddenException;
import wellfair.dao.ArchiveDAO;
import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.ArchiveDetailTO;
import wellfair.to.ArchiveRowTO;
import wellfair.to.ArchiveTO;
import wellfair.to.BuildingDetailTO;
import wellfair.to.DashboardTO;
import wellfair.to.FieldCheckTO;
import wellfair.to.MemberTO;
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

	/** 입력 한도 · 허용 값 (DB 컬럼과 맞춤, 15단계 D-11·12) : handover_memo.memo VARCHAR(500), visit_history.result ENUM */
	public static final int MEMO_MAX = 500;
	public static final List<String> VISIT_RESULTS = Arrays.asList("MET", "ABSENT", "REFUSED", "OTHER");

	private static final ObjectMapper JSON = new ObjectMapper();
	/** 화면이 보낸 기준 JSON 읽기용 (모르는 키는 무시) */
	private static final ObjectMapper LOOSE = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	@Autowired
	ArchiveDAO dao;

	@Autowired
	MapService mapService;

	@Autowired
	FieldService fieldService;

	@Autowired
	MemberService memberService;

	// 탭 숫자 (신규 / 진행중 / 완료됨). gu 가 있으면 그 구의 보관함만 (구청 계정 = 소속 구, 서울시청 = 고른 구)
	public StatusCountTO getStatusCount(long userId, String gu) {
		StatusCountTO cnt = dao.getStatusCount(userId, gu);
		return cnt == null ? new StatusCountTO() : cnt;
	}

	/**
	 * 보관함에 건물 저장 (13단계 · 15단계 A-3). 화면이 보낸 구는 쓰지 않고 DB 의 building.sgg_cd 를 쓴다.
	 *  archived = 내 보관함에 같은 건물이 있음, visited = 그 건물의 방문 기록 1건 이상 (누가 갔든)
	 *   A) archived && !visited → 저장하지 않음. {ok:false, code:"DUP", msg:"이미 보관함에 저장된 건물입니다 (날짜 저장 · N점)", bldId}
	 *   B) archived &&  visited → 조사 이력이 있으니 점수 갱신 목적으로 보고 묻지 않고 다시 저장. code:"UPDATED"
	 *   C) !archived            → 저장. code:"ADDED"
	 * 같은 구 · 같은 기준(JSON)의 분석 리스트가 있으면 거기에, 없으면 리스트를 새로 만들어 넣는다 (분석 1회 = 리스트 1개).
	 * 없는 건물 → IllegalArgumentException(400), 구청 계정이 다른 구 건물 → ForbiddenException(403). score 가 null 이면 기준(JSON)으로 계산한 값.
	 */
	public Map<String, Object> addArchive(MemberTO member, String bldId, Integer score, String criteriaJson) {
		long userId = member.getUserId();
		AnalysisCriteriaTO c = parseCriteria(criteriaJson);
		MapService.assertWeights(c);
		c.setBldId(bldId);
		c.setUserId(userId);
		BuildingDetailTO bd = mapService.getBuildingDetail(c);
		if (bd == null) throw new IllegalArgumentException("없는 건물입니다.");
		String sggCd = bd.getBuilding().getSggCd();
		if (member.isGuUser() && !member.getSggCd().equals(sggCd))
			throw new ForbiddenException(member.getGuName() + " 건물만 보관함에 저장할 수 있습니다.");
		int saveScore = score == null ? bd.getBuilding().getScore() : score;

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("bldId", bldId);
		ArchiveTO prev = dao.getFactorByBld(userId, bldId);
		boolean archived = prev != null;
		boolean visited = archived && dao.getVisitCount(bldId) > 0;
		if (archived && !visited) {
			out.put("ok", false);
			out.put("code", "DUP");
			out.put("msg", "이미 보관함에 저장된 건물입니다 (" + prev.getEvaluatedAt() + " 저장 · " + prev.getVulnerabilityScore() + "점)");
			out.put("savedAt", prev.getEvaluatedAt());              // 안내 모달(map-panel.js)에 따로 보여 준다
			out.put("savedScore", prev.getVulnerabilityScore());
			return out;
		}

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
		to.setVulnerabilityScore(saveScore);
		to.setVulnerabilityGrade(grade(saveScore));
		dao.addFactor(to);

		out.put("ok", true);
		out.put("code", archived ? "UPDATED" : "ADDED");
		out.put("msg", archived ? "점수를 갱신해 다시 저장했습니다." : "보관함에 저장했습니다.");
		return out;
	}

	/** 화면이 보낸 기준 JSON → AnalysisCriteriaTO (모르는 키는 무시, 비었거나 깨진 JSON 은 기본값) */
	static AnalysisCriteriaTO parseCriteria(String json) {
		if (json == null || json.isBlank()) return new AnalysisCriteriaTO();
		try {
			return LOOSE.readValue(json, AnalysisCriteriaTO.class);
		} catch (Exception e) {
			return new AnalysisCriteriaTO();
		}
	}

	/**
	 * 이 건물이 내 관할인지 (15단계 A-4). 05 의 archivecheck · archivetransfer · archivememo 가 저장 전에 부른다.
	 * 없는 건물 → IllegalArgumentException, 구청 계정이 다른 구 건물 → ForbiddenException. 건물의 sgg_cd 리턴
	 */
	public String assertMyGu(MemberTO member, String bldId) {
		String sgg = mapService.getBuildingSgg(bldId);
		if (sgg == null) throw new IllegalArgumentException("없는 건물입니다.");
		if (member.isGuUser() && !member.getSggCd().equals(sgg)) throw new ForbiddenException("관할 구역의 건물만 볼 수 있습니다.");
		return sgg;
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
	 * 방문 일시가 형식에 안 맞거나 미래, 방문 결과가 정해진 값이 아니면 IllegalArgumentException (15단계 D-12)
	 */
	public void addCheck(long userId, String bldId, String visitedAt, String result, Map<String, String> checks,
			List<String> added, String content, Integer scoreAdj) {
		Long planId = dao.getPlanByBld(userId, bldId);
		if (planId == null) throw new IllegalStateException("현장확인 리스트에 먼저 담아 주세요.");
		if (result == null || result.isBlank()) result = "MET";
		if (!VISIT_RESULTS.contains(result)) throw new IllegalArgumentException("방문 결과를 확인해 주세요.");
		String visited = parseVisitedAt(visitedAt);           // null = 지금

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
		c.setResult(result);
		c.setContent(content == null || content.isBlank() ? "현장 확인" : content.trim());
		c.setVisitedAt(visited);
		try { c.setFactorCheck(fc.isEmpty() ? null : JSON.writeValueAsString(fc)); } catch (Exception e) { c.setFactorCheck(null); }
		int a = scoreAdj == null ? 0 : Math.max(-10, Math.min(10, scoreAdj));
		c.setScoreAdj(a == 0 ? null : a);
		fieldService.addCheck(c);
	}

	/** 방문 일시 'yyyy-MM-ddTHH:mm' 또는 'yyyy-MM-dd HH:mm'(초가 붙어도 됨) → 'yyyy-MM-dd HH:mm:ss'. 비었으면 null(= 지금).
	 *  형식이 틀리거나 미래면 IllegalArgumentException("방문 일시를 확인해 주세요") (15단계 D-12) */
	static String parseVisitedAt(String v) {
		if (v == null || v.isBlank()) return null;
		LocalDateTime t;
		try {
			t = LocalDateTime.parse(v.trim().replace(' ', 'T'), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]"));
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("방문 일시를 확인해 주세요.");
		}
		if (t.isAfter(LocalDateTime.now().plusMinutes(1))) throw new IllegalArgumentException("방문 일시를 확인해 주세요. (미래 시각은 저장할 수 없습니다)");
		return t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}

	// 부서로 넘기기 (transfer_log INSERT). 부서 · 요청 종류는 정해진 목록 값만 (15단계 D-11)
	public boolean addTransfer(long userId, String bldId, String deptNm, String reqType, Long recordId) {
		if (deptNm == null || !DEPTS.contains(deptNm.trim())) throw new IllegalArgumentException("넘길 부서를 목록에서 골라 주세요.");
		String rt = reqType == null || reqType.isBlank() ? REQ_TYPES.get(0) : reqType.trim();
		if (!REQ_TYPES.contains(rt)) throw new IllegalArgumentException("요청 종류를 확인해 주세요.");
		TransferTO t = new TransferTO();
		t.setUserId(userId);
		t.setBldId(bldId);
		t.setRecordId(recordId);
		t.setDeptNm(deptNm.trim());
		t.setReqType(rt);
		return dao.addTransfer(t) > 0;
	}

	/**
	 * 인수인계 메모 (+추가 · 담당자 변경). 메모 500자까지 (15단계 D-11).
	 * 받는 담당자(toUserId)는 있는 사용자여야 하고, 구청 계정이면 같은 구 사용자만 (서울시청은 예외) (15단계 A-4)
	 */
	public boolean addMemo(MemberTO member, String bldId, Long toUserId, String memo) {
		if (memo == null || memo.isBlank()) throw new IllegalArgumentException("메모 내용을 입력하세요.");
		String text = memo.trim();
		if (text.length() > MEMO_MAX) throw new IllegalArgumentException("메모는 " + MEMO_MAX + "자까지 입력할 수 있습니다.");
		if (toUserId != null) {
			MemberTO to = memberService.getMember(toUserId);
			if (to == null) throw new IllegalArgumentException("받는 담당자를 찾을 수 없습니다.");
			if (member.isGuUser() && !member.getSggCd().equals(to.getSggCd()))
				throw new IllegalArgumentException("같은 구(" + member.getGuName() + ") 담당자에게만 인수인계할 수 있습니다.");
		}
		MemoTO m = new MemoTO();
		m.setBldId(bldId);
		m.setFromUserId(member.getUserId());
		m.setToUserId(toUserId);
		m.setMemo(text);
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
