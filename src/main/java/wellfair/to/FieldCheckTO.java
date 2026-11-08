package wellfair.to;

import lombok.Data;

// 확인 완료 처리 (POST fieldcheck.do) → visit_history + survey_record
@Data
public class FieldCheckTO {
	private long planId;
	private long surveyId;
	private long userId;
	private String bldId;
	private String result = "MET";      // MET / ABSENT / REFUSED / OTHER
	private String content = "현장 확인 완료 (웹 버튼)";
	private Long visitId;               // addVisit 뒤 채워짐
}
