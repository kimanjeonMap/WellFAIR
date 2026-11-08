package wellfair.to;

import lombok.Data;

/**
 * 보관함 = vulnerability_list(분석 1회, 개인) + vulnerability_factor(그 분석 안의 건물 1동).
 * 클래스명은 화면 기준(Archive) 그대로.
 */
@Data
public class ArchiveTO {
	// vulnerability_list
	private Long vulnerabilityId;
	private long userId;
	private String sggCd;
	private String title;
	private String criteria;        // 가중치·필터 JSON 문자열
	private String analyzedAt;

	// vulnerability_factor
	private Long factorId;
	private String bldId;
	private Integer vulnerabilityScore;
	private String vulnerabilityGrade;
	private String evaluatedAt;
}
