package wellfair.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import wellfair.dao.FieldDAO;
import wellfair.to.ArchiveRowTO;
import wellfair.to.FieldCheckTO;
import wellfair.to.FieldItemTO;
import wellfair.to.FieldListTO;

/**
 * 현장확인 리스트. FieldDAO 는 여기서만.
 * 경로는 직선거리(하버사인)로 계산 : 도보 4km/h · 자동차 20km/h · 대중교통 12km/h, 건물당 확인 20분
 */
@Service
public class FieldService {

	public static final double WALK_KMH = 4, CAR_KMH = 20, TRANSIT_KMH = 12;
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
	 */
	public long saveField(long userId, String userGu, long surveyId, String title, List<Long> factorIds) {
		List<Long> ids = factorIds == null ? new ArrayList<>() : factorIds;
		FieldListTO old = surveyId > 0 ? dao.getField(surveyId, userId, userGu) : null;
		if (surveyId > 0 && old == null) return 0;
		String listGu = oneGu(userGu, old == null ? null : old.getSggCd(), ids);

		FieldListTO f = new FieldListTO();
		f.setUserId(userId);
		f.setSggCd(listGu);
		f.setTitle(title == null || title.isBlank() ? "새 현장확인 리스트" : title.trim());
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

	// 보관함에서 "현장확인 리스트에 저장" : 기존 항목 뒤에 붙인다 (surveyId 0 = 새 리스트). 다른 구 건물이면 IllegalArgumentException
	public long addItems(long userId, String userGu, long surveyId, List<Long> factorIds) {
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
			f.setTitle("새 현장확인 리스트");
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
