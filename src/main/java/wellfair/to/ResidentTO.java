package wellfair.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 거주 가구 카드 1장 (가구 단위. residentId = household.hh_id)
 * 이름·생년월일은 서비스에서 가림 처리한 값만 내보낸다. 동·호수는 resident.do 로 따로.
 */
@Data
public class ResidentTO {
	private long residentId;
	private String resFlr;
	private String dongNm;        // resident.do 응답에서만 채움
	private String hoNm;          // resident.do 응답에서만 채움
	private Integer hhMbrCnt;
	private String hhType;
	private String riskGrade;
	private String headNm;        // 가림: 김○희
	private String birthDay;      // 가림: 47****
	private Integer maxAge;
	private int old75Cnt;
	private int severeCnt;
	private int pensionCnt;
	private String nearPoorYn;
	private String basicLivingYn;
	private String careBurdenYn;
	private String arrearsYn;
	private String blindSpotYn;   // 사각지대 (복지 체계 밖, +15)
	private String blindSpotType;
	private int priority;         // 거주자 우선 점수 (mapper-resident priorityExpr)
	private String priorityText;  // 가점 내역 "고위험고립 30 · 지하층 10 · 기초연금 3" (12단계 모달 툴팁, ResidentService 가 priorityExpr 와 같은 규칙으로)
	private String supportType;   // 가구원 지원연계 (공적급여 · 민간자원 · 기타서비스, 여러 개면 ' · ' 로)  → 모달 "복지 연계" 원본 값
	private String detectResult;  // 가구원 처리결과 (조치완료 · 단순상담 · 미처리 …)                       → 지원연계 없을 때 그대로
	private List<String> tags = new ArrayList<>();      // 취약 유형 태그 (가점 요인)
	private List<String> benefits = new ArrayList<>();  // 수급 확인용 태그 (기초생활 · 차상위 · 기초연금) : 취약 유형 칸에 점선으로
}
