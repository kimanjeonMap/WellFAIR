package wellfair.to;

import lombok.Data;

// 현장확인 리스트 항목 1건 = survey_plan (+ factor · building · 확인 기록)
@Data
public class FieldItemTO {
	private Long planId;
	private long surveyId;
	private long factorId;
	private String bldId;
	private Integer visitOrder;
	private String planDate;
	// building
	private String addr;
	private String bldNm;
	private String useNm;
	private String emdNm;
	private String sggCd;
	private String useaprDay;
	private Integer grndFlr;
	private Integer ugrndFlr;
	private double lat;
	private double lng;
	private String welfNm;
	private double welfDist;
	private String hospNm;
	private double hospDist;
	// factor
	private int score;              // 저장 당시 점수
	private String factorText;
	// 확인 기록 (survey_record + visit_history)
	private String checkedAt;
	private String checkResult;     // MET / ABSENT / REFUSED / OTHER
	private String checkContent;
	// 경로 (서비스가 계산)
	private double legKm;           // 이전 지점에서 거리
	private int legMin;             // 이전 지점에서 도보 분

	public boolean isChecked() { return checkedAt != null; }
	public String getTitle() {
		String s = Common.shortAddr(addr);
		return bldNm != null && !bldNm.isBlank() ? s + " " + bldNm : useNm != null ? s + " " + useNm : s;
	}
	public String getBuildingInfo() {
		String yr = useaprDay != null && useaprDay.length() == 8 ? useaprDay.substring(0, 4) + "년" : "준공 미상";
		String fl = (grndFlr != null ? "지상 " + grndFlr + "층" : "") + (ugrndFlr != null && ugrndFlr > 0 ? " · 지하 " + ugrndFlr + "층" : "");
		return yr + (fl.isEmpty() ? "" : " · " + fl);
	}

	/** JSP 에서 쓰는 주소 줄이기 ("서울특별시 구로구 가리봉동 12-34" → "가리봉동 12-34") */
	public static class Common {
		public static String shortAddr(String addr) {
			if (addr == null) return "";
			return addr.replaceFirst("^서울특별시\\s+", "").replaceFirst("^\\S+구\\s+", "");
		}
	}
}
