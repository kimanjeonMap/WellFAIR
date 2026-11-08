package wellfair.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import wellfair.dao.ResidentDAO;
import wellfair.to.ResidentCountTO;
import wellfair.to.ResidentTO;

/**
 * 거주자 조회. 개인정보는 여기서 가림 처리하고 원문은 내보내지 않는다.
 * 지도·리스트·상세 = 집계만 / 거주 가구 탭 = 가림 카드 / 정보 확인하기 = 동·호수만
 * 모달 "복지 연계" 칸은 원본 값(support_type / detect_result) 그대로 — 임의 규칙 없음 (5-1). 판단은 map-panel.js welfare()
 */
@Service
public class ResidentService {

	@Autowired
	ResidentDAO dao;

	// 건물별 집계 ("고위험고립 2가구 · 75세 이상 3명")
	public ResidentCountTO getResidentCount(String bldId) {
		ResidentCountTO cnt = dao.getResidentCount(bldId);
		return cnt == null ? new ResidentCountTO() : cnt;
	}

	// 거주 가구 탭 : 세대 카드 (이름·생년월일 가림, 태그 생성)
	public List<ResidentTO> getResidentList(String bldId) {
		List<ResidentTO> list = dao.getResidentList(bldId);
		for (ResidentTO r : list) {
			r.setHeadNm(maskName(r.getHeadNm()));
			r.setBirthDay(maskBirth(r.getBirthDay()));
			r.setDongNm(null);   // 동·호수는 "정보 확인하기" 에서만
			r.setHoNm(null);
			fillTags(r);
		}
		return list;
	}

	// "정보 확인하기" : 동·호수만 (호수 없으면 안내 문구)
	public ResidentTO getResidentLocation(long residentId) {
		ResidentTO r = dao.getResident(residentId);
		if (r == null) return null;
		ResidentTO out = new ResidentTO();
		out.setResidentId(r.getResidentId());
		out.setResFlr(r.getResFlr());
		out.setDongNm(r.getDongNm());
		out.setHoNm(r.getHoNm() == null || r.getHoNm().isBlank() ? "호수 미확인 · 행복e음에서 확인" : r.getHoNm());
		return out;
	}

	/** Figma 02-2-1 : 성만 보이고 나머지는 ** (김다희 → 김**) */
	static String maskName(String name) {
		if (name == null || name.isBlank()) return "이름 미확인";
		return name.trim().charAt(0) + "**";
	}

	/** 880708 → 88**** */
	static String maskBirth(String birth) {
		if (birth == null || birth.length() < 2) return "";
		return birth.substring(0, 2) + "****";
	}

	private static void fillTags(ResidentTO r) {
		List<String> t = r.getTags();
		if ("고위험고립".equals(r.getRiskGrade()) || "사회적고립".equals(r.getRiskGrade())) t.add(r.getRiskGrade());
		if (r.getOld75Cnt() > 0) t.add("75세 이상");
		if (r.getSevereCnt() > 0) t.add("심한 장애");
		if ("Y".equals(r.getBlindSpotYn())) t.add("사각지대" + (r.getBlindSpotType() == null || r.getBlindSpotType().isBlank() ? "" : " · " + r.getBlindSpotType()));
		if ("Y".equals(r.getArrearsYn())) t.add("건보료 체납");
		if ("Y".equals(r.getCareBurdenYn())) t.add("독박돌봄");
		String flr = r.getResFlr() == null ? "" : r.getResFlr();
		if (flr.startsWith("지하")) t.add("지하 거주");
		else if (flr.equals("옥탑층")) t.add("옥탑 거주");
		/* 수급 확인용 (이미 복지 체계 안) : 취약 유형 칸에 점선 태그로 (5-1) */
		List<String> b = r.getBenefits();
		if ("Y".equals(r.getBasicLivingYn())) b.add("기초생활");
		else if ("Y".equals(r.getNearPoorYn())) b.add("차상위");
		if (r.getPensionCnt() > 0) b.add("기초연금");
	}
}
