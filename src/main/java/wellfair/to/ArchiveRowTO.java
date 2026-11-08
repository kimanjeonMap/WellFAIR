package wellfair.to;

import lombok.Data;

// 보관함 한 행 = vulnerability_factor 1건 (+ 리스트 · 건물 · 상태)
@Data
public class ArchiveRowTO {
	private long factorId;
	private long vulnerabilityId;
	private String title;           // 분석 리스트 이름
	private String bldId;
	private String sggCd;
	private String emdNm;
	private String addr;
	private String bldNm;
	private String useNm;
	private double lat;
	private double lng;
	private int score;              // 저장 당시 점수 (vulnerability_score)
	private String factorText;      // 현재 기본 기준으로 계산한 요인 ("지하층 · 노후")
	private int priorityRank;       // 내 보관함 안 점수 순위 (우선순위 추정)
	private String status;          // TODO | DOING | DONE (statusExpr)
	private String savedAt;         // evaluated_at (yyyy-MM-dd)
	private Long surveyId;          // 담긴 현장확인 리스트 (있으면)
	private String surveyTitle;
	private String checkedAt;       // 확인 완료일 (DONE 일 때)

	public String getStatusLabel() {
		try { return wellfair.common.WorkStatus.valueOf(status).getLabel(); } catch (Exception e) { return status; }
	}
}
