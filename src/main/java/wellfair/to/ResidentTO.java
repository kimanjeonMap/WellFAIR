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
	private int priority;         // 거주자 우선 점수 (mapper-resident priorityExpr)
	private List<String> tags = new ArrayList<>();   // 카드에 붙는 가점 태그
}
