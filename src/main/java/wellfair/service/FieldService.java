package wellfair.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import wellfair.dao.FieldDAO;
import wellfair.to.ArchiveRowTO;
import wellfair.to.FieldCheckTO;
import wellfair.to.FieldItemTO;
import wellfair.to.FieldListTO;

/**
 * 현장확인 리스트. FieldDAO 는 여기서만.
 * 경로는 직선거리(하버사인)로 계산 : 도보 4km/h · 대중교통 15km/h · 자동차 20km/h (× 1.3 도로 보정), 건물당 확인 20분
 * 이동수단은 survey_list.move_mode (WALK / TRANSIT / CAR) 에 저장. field-map.js SPEED 와 같은 값
 */
@Service
public class FieldService {

	public static final double WALK_KMH = 4, CAR_KMH = 20, TRANSIT_KMH = 15;
	/** 리스트 이름 길이 한도 = survey_list.title VARCHAR(100) (15단계 D-11) */
	public static final int TITLE_MAX = 100;

	/** move_mode → 평균속도 (모르는 값은 도보) */
	public static double speedOf(String moveMode) {
		if ("CAR".equals(moveMode)) return CAR_KMH;
		if ("TRANSIT".equals(moveMode)) return TRANSIT_KMH;
		return WALK_KMH;
	}
	/** 화면 값(walk/transit/car) → DB 값. 모르는 값은 null */
	public static String moveModeOf(String v) {
		if (v == null) return null;
		String m = v.trim().toUpperCase();
		return m.equals("WALK") || m.equals("TRANSIT") || m.equals("CAR") ? m : null;
	}
	public static final double ROAD_FACTOR = 1.3;          // 직선거리 → 도로 보정 (field-map.js 와 같은 값)
	public static final int CHECK_MIN_PER_BLD = 20;

	/** 기본 출발점 = 해당 구청 (field-map.js GU_OFFICE 와 같은 값). [name, lat, lng] */
	public static Object[] guOffice(String sggCd) {
		if ("11470".equals(sggCd)) return new Object[] { "양천구청", 37.5170, 126.8664 };
		if ("11560".equals(sggCd)) return new Object[] { "영등포구청", 37.5264, 126.8962 };
		return new Object[] { "구로구청", 37.4954, 126.8874 };
	}

	@Autowired
	FieldDAO dao;

	// 목록 (tab = doing | done). userGu = 구청 계정의 소속 구 (서울시청은 null = 전체). 펼침용 항목까지 채운다
	public List<FieldListTO> getFieldList(long userId, String userGu, String tab, String q, boolean withItems) {
		List<FieldListTO> list = dao.getFieldList(userId, userGu, tab, q);
		if (withItems) for (FieldListTO f : list) f.setItems(route(dao.getFieldItemList(f.getSurveyId())));
		return list;
	}

	// 리스트 1개 + 항목 + 경로 (없거나 남의 것 · 다른 구 것이면 null)
	public FieldListTO getFieldDetail(long surveyId, long userId, String userGu) {
		FieldListTO f = dao.getField(surveyId, userId, userGu);
		if (f == null) return null;
		f.setItems(route(dao.getFieldItemList(surveyId)));
		return f;
	}

	// 수정 화면 왼쪽 : 내 보관함 건물 (어느 리스트에 담겼는지 포함). 구청 계정은 소속 구만
	public List<ArchiveRowTO> getPickList(long userId, String userGu) {
		return dao.getPickList(userId, userGu);
	}

	/**
	 * 리스트 저장 (제목 + 건물 순서). surveyId 0 이면 새로 만든다. 저장된 surveyId 리턴 (내 리스트가 아니면 0).
	 * 확인 기록이 있는 항목은 목록에서 빠져도 남는다(FK).
	 * 한 리스트 = 한 구 : 다른 구 건물이 섞이면 IllegalArgumentException (메시지를 화면에 그대로 띄운다)
	 * 빈 제목 = 자동 이름 "새 리스트N" (기존 리스트는 원래 이름 유지). 100자 초과 · 내 보관함이 아닌 factorId 도 IllegalArgumentException. 트랜잭션이라 걸리면 빈 리스트가 남지 않는다 (15단계 D-10·11)
	 */
	@Transactional
	public long saveField(long userId, String userGu, long surveyId, String title, String moveMode, List<Long> factorIds) {
		List<Long> ids = factorIds == null ? new ArrayList<>() : factorIds;
		FieldListTO old = surveyId > 0 ? dao.getField(surveyId, userId, userGu) : null;
		if (surveyId > 0 && old == null) return 0;
		/* 이름을 비워 두면 : 새 리스트 = "새 리스트N" 자동, 기존 리스트 = 원래 이름 유지 */
		String name = cleanTitle(title);
		if (name.isEmpty()) name = old != null && old.getTitle() != null && !old.getTitle().isEmpty() ? old.getTitle() : nextAutoTitle(userId);
		assertMine(userId, ids);
		String listGu = oneGu(userGu, old == null ? null : old.getSggCd(), ids);

		FieldListTO f = new FieldListTO();
		f.setUserId(userId);
		f.setSggCd(listGu);
		f.setMoveMode(moveModeOf(moveMode));       // null 이면 새 리스트 WALK / 기존 값 유지
		f.setTitle(name);
		if (surveyId <= 0) {
			dao.addField(f);
			surveyId = f.getSurveyId();
		} else {
			f.setSurveyId(surveyId);
			dao.updateField(f);
		}
		dao.clearItemOrder(surveyId);
		dao.deleteItemsNotIn(surveyId, ids);
		int order = 1;
		for (Long fid : ids) dao.addItem(surveyId, fid, order++);
		return surveyId;
	}

	/**
	 * 리스트 삭제. 1 = 지움 / 0 = 내 리스트가 아님 / -1 = 현장 확인 기록이 있어서 남김 (05 이력 보호)
	 */
	public int deleteField(long userId, String userGu, long surveyId) {
		if (dao.getField(surveyId, userId, userGu) == null) return 0;
		if (dao.countFieldRecords(surveyId) > 0) return -1;
		return dao.deleteField(surveyId, userId) > 0 ? 1 : 0;
	}

	/** 리스트 이름 : 앞뒤 공백 정리 (비어 있으면 "" → 호출한 쪽에서 자동 이름), TITLE_MAX 넘으면 안내 (15단계 D-11) */
	static String cleanTitle(String title) {
		String t = title == null ? "" : title.trim();
		if (t.length() > TITLE_MAX) throw new IllegalArgumentException("리스트 이름은 " + TITLE_MAX + "자까지 입력할 수 있습니다.");
		return t;
	}

	/** 자동 이름 앞부분. "새 리스트1", "새 리스트2" … */
	public static final String AUTO_TITLE = "새 리스트";

	/** 내 리스트 이름 중 "새 리스트N" 에서 안 쓰인 가장 작은 N (1부터). 새 리스트1 이 있으면 새 리스트2 */
	String nextAutoTitle(long userId) {
		Set<Integer> used = new HashSet<>();
		for (String t : dao.getAutoTitles(userId, AUTO_TITLE)) {       // title LIKE '새 리스트%'
			if (t == null || !t.trim().startsWith(AUTO_TITLE)) continue;
			String rest = t.trim().substring(AUTO_TITLE.length()).trim();
			if (rest.matches("\\d{1,6}")) used.add(Integer.parseInt(rest));
		}
		int n = 1;
		while (used.contains(n)) n++;
		return AUTO_TITLE + n;
	}

	/** 담으려는 factorId 가 전부 내 보관함 것인지. 하나라도 아니면 IllegalArgumentException (15단계 D-10, 남의 건물 · 없는 id) */
	private void assertMine(long userId, List<Long> factorIds) {
		if (factorIds == null || factorIds.isEmpty()) return;
		int distinct = new HashSet<>(factorIds).size();
		if (dao.countMyFactors(userId, factorIds) != distinct) throw new IllegalArgumentException("내 보관함 건물만 담을 수 있습니다.");
	}

	// 보관함에서 "현장확인 리스트에 저장" : 기존 항목 뒤에 붙인다 (surveyId 0 = 새 리스트). 다른 구 · 남의 건물이면 IllegalArgumentException
	@Transactional
	public long addItems(long userId, String userGu, long surveyId, List<Long> factorIds) {
		assertMine(userId, factorIds);
		FieldListTO old = surveyId > 0 ? dao.getField(surveyId, userId, userGu) : null;
		if (surveyId > 0 && old == null) return 0;
		List<Long> ids = new ArrayList<>();
		if (old != null) for (FieldItemTO it : dao.getFieldItemList(surveyId)) ids.add(it.getFactorId());
		for (Long fid : factorIds) if (!ids.contains(fid)) ids.add(fid);
		String listGu = oneGu(userGu, old == null ? null : old.getSggCd(), ids);

		if (old == null) {
			FieldListTO f = new FieldListTO();
			f.setUserId(userId);
			f.setSggCd(listGu);
			f.setTitle(nextAutoTitle(userId));
			dao.addField(f);
			surveyId = f.getSurveyId();
		} else if (old.getSggCd() == null && listGu != null) {
			old.setSggCd(listGu);
			dao.updateField(old);                 // 옛 리스트에 구 채우기
		}
		dao.clearItemOrder(surveyId);
		int order = 1;
		for (Long fid : ids) dao.addItem(surveyId, fid, order++);
		return surveyId;
	}

	// 03-2 소요시간 카드 : 이동수단만 바꿔 저장 (내 리스트가 아니거나 값이 이상하면 false)
	public boolean updateMoveMode(long userId, String userGu, long surveyId, String moveMode) {
		String m = moveModeOf(moveMode);
		if (m == null || dao.getField(surveyId, userId, userGu) == null) return false;
		return dao.updateMoveMode(surveyId, userId, m) > 0;
	}

	/**
	 * 리스트의 구를 정한다 (CLAUDE.md 5-1 : 현장확인 리스트는 한 구 안의 건물만).
	 *  userGu(구청 계정) > 리스트에 이미 정해진 구 > 담는 건물들의 구. 건물이 두 구에 걸치거나 정해진 구와 다르면 예외.
	 */
	private String oneGu(String userGu, String listGu, List<Long> factorIds) {
		String fixed = userGu != null && !userGu.isBlank() ? userGu : listGu;
		List<String> gus = factorIds.isEmpty() ? new ArrayList<>() : dao.getFactorSggList(factorIds);
		if (gus.size() > 1)
			throw new IllegalArgumentException("현장확인 리스트에는 한 구 안의 건물만 담을 수 있습니다. 구가 다른 건물은 따로 리스트를 만들어 주세요.");
		if (fixed != null && !gus.isEmpty() && !fixed.equals(gus.get(0)))
			throw new IllegalArgumentException(ArchiveService.guName(fixed) + " 리스트에는 " + ArchiveService.guName(gus.get(0)) + " 건물을 담을 수 없습니다.");
		return fixed != null ? fixed : gus.isEmpty() ? null : gus.get(0);
	}

	/** 05 현장 확인 결과 입력 (ArchiveService 가 plan 을 확인한 뒤 부른다) : visit_history 1건 + survey_record 1건 */
	public void addCheck(FieldCheckTO c) {
		dao.addVisit(c);
		dao.addRecord(c);
	}

	// 확인 완료 처리 : visit_history 1건 + survey_record 1건
	public boolean checkItem(FieldCheckTO c) {
		FieldListTO f = dao.getField(c.getSurveyId(), c.getUserId(), null);
		if (f == null) return false;
		for (FieldItemTO it : dao.getFieldItemList(c.getSurveyId())) {
			if (it.getPlanId() != null && it.getPlanId() == c.getPlanId()) {
				c.setBldId(it.getBldId());
				dao.addVisit(c);           // visitId 채워짐
				dao.addRecord(c);
				return true;
			}
		}
		return false;
	}

	// ---------- 경로 (직선거리) ----------
	/** 출발점(첫 건물의 구청) → 순서대로. 구간 거리는 직선, 분은 × 1.3 보정한 도보 */
	static List<FieldItemTO> route(List<FieldItemTO> items) {
		if (items.isEmpty()) return items;
		Object[] g = guOffice(items.get(0).getSggCd());
		double pLat = (Double) g[1], pLng = (Double) g[2];
		for (FieldItemTO it : items) {
			double km = haversineKm(pLat, pLng, it.getLat(), it.getLng());
			it.setLegKm(Math.round(km * 100) / 100.0);
			it.setLegMin(minutes(km, WALK_KMH));
			pLat = it.getLat(); pLng = it.getLng();
		}
		return items;
	}

	public static double totalKm(List<FieldItemTO> items) {
		double s = 0;
		for (FieldItemTO it : items) s += it.getLegKm();
		return Math.round(s * 10) / 10.0;
	}
	public static int minutes(double km, double kmh) { return (int) Math.round(km * ROAD_FACTOR / kmh * 60); }

	static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
		double R = 6371.0088;
		double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * R * Math.asin(Math.sqrt(a));
	}
}
