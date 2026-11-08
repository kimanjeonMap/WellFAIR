package wellfair.to;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 05 건물 확인 이력 타임라인 한 칸 = survey_record 1건 (+ visit_history · 담당자 · 이 기록에서 넘긴 이관).
 * factor_check JSON({"ugrnd":"Y","flood":"?","added":["반지하 창문 2곳"]}) 은 ArchiveService 가 checks / added 로 풀어 준다.
 */
@Data
public class RecordTO {
	private long recordId;
	private long planId;
	private long visitId;
	private String visitedAt;        // yyyy-MM-dd HH:mm
	private String result;           // MET / ABSENT / REFUSED / OTHER
	private long userId;
	private String userNm;
	private String rankNm;
	private String content;          // 관찰 메모
	private String factorCheck;      // 원본 JSON
	private Integer scoreAdj;        // 점수 보정 (-10 ~ +10)
	private String createdAt;

	// 이 기록에서 넘긴 부서 이관 (최근 1건)
	private String transferDept;
	private String transferReq;
	private String transferStatus;   // SENT / REPLIED
	private String transferAt;       // MM-dd

	// 서비스가 채움
	private Map<String, String> checks = new LinkedHashMap<>();   // 요인 키 → Y / N / ?
	private List<String> added = new ArrayList<>();               // 새로 발견한 요인
	private String checkText;                                     // "지하층 맞음 · 옥탑 아님 · 침수 판단 어려움 · 새로 발견: …"
	private List<String> confirmed = new ArrayList<>();           // Y 로 확정된 요인 라벨 (분석 반영 칸)

	public String getResultLabel() {
		if ("MET".equals(result)) return "면담";
		if ("ABSENT".equals(result)) return "부재";
		if ("REFUSED".equals(result)) return "거부";
		if ("OTHER".equals(result)) return "기타";
		return "-";
	}
	public boolean isHasYes() { return checks.containsValue("Y"); }
	public boolean isHasNo() { return checks.containsValue("N"); }
}
