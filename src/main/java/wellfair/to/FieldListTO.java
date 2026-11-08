package wellfair.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

// 현장확인 리스트 1개 = survey_list (+ 건물 수 · 진행). 클래스명은 화면 기준(Field) 그대로
@Data
public class FieldListTO {
	private long surveyId;
	private long userId;
	private String title;
	private String createdAt;
	private String updatedAt;
	private int itemCnt;            // survey_plan 수
	private int doneCnt;            // survey_record 가 있는 plan 수
	private String sggCd;           // 리스트의 구 (survey_list.sgg_cd, 한 리스트 = 한 구). 옛 리스트는 첫 건물 기준
	private String moveMode;        // WALK | TRANSIT | CAR
	private String emdNm;
	private String firstAddr;       // "가리봉동 12-34 다세대주택 외 3동" 의 앞부분
	private List<FieldItemTO> items = new ArrayList<>();   // 상세 · 펼침용

	public boolean isDone() { return itemCnt > 0 && doneCnt >= itemCnt; }
	public int getProgressPct() { return itemCnt == 0 ? 0 : (int) Math.round(doneCnt * 100.0 / itemCnt); }
	public String getGuName() {
		if ("11530".equals(sggCd)) return "구로구";
		if ("11470".equals(sggCd)) return "양천구";
		if ("11560".equals(sggCd)) return "영등포구";
		return "";
	}
}
