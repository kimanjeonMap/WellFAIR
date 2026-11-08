package wellfair.to;

import lombok.Data;

// 확인 완료 처리 (POST fieldcheck.do) · 05 현장 확인 결과 입력 (POST archivecheck.do) → visit_history + survey_record
@Data
public class FieldCheckTO {
	private long planId;
	private long surveyId;
	private long userId;
	private String bldId;
	private String result = "MET";      // MET / ABSENT / REFUSED / OTHER
	private String content = "현장 확인 완료 (웹 버튼)";
	private String visitedAt;           // 'yyyy-MM-dd HH:mm' (없으면 NOW())
	private String factorCheck;         // 05 현장 체크 JSON {"ugrnd":"Y","flood":"?","added":[...]} (없으면 null)
	private Integer scoreAdj;           // 05 점수 보정 -10 ~ +10 (없으면 null)
	private Long visitId;               // addVisit 뒤 채워짐
	private Long recordId;              // addRecord 뒤 채워짐
}
