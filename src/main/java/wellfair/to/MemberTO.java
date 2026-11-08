package wellfair.to;

import java.io.Serializable;

import lombok.Data;

/**
 * 로그인 사용자 = users 테이블. 클래스명은 화면 기준(Member) 그대로.
 * 세션 키 loginMember 에 담기므로 Serializable (Tomcat 재배포 때 세션 유지). 세션에는 passwordHash 를 비워서 넣는다.
 *
 * 계정 2종류 (CLAUDE.md 5-1)
 *  - 서울시청  : sggCd NULL. 3개 구를 같은 기준으로 분석(발굴). 구 선택 자유.
 *  - 구청 주무관 : sggCd 있음. 구 선택칸은 소속 구로 잠금, 보관함·현장확인·대시보드도 소속 구만.
 */
@Data
public class MemberTO implements Serializable {
	private static final long serialVersionUID = 3L;

	private long userId;          // 세션 사용자 키 (예전 설계의 emp_no 자리)
	private String loginId;
	private String passwordHash;  // 중간점검은 평문 비교

	private String sggCd;         // 소속 구 (11530 구로 / 11470 양천 / 11560 영등포), NULL = 서울시청
	private String userNm;        // 김구로
	private String deptNm;        // 구로구청 복지정책과
	private String rankNm;        // 주무관

	/** 구청 계정인지 (소속 구 잠금). JSP 에서는 ${loginMember.guUser} */
	public boolean isGuUser() {
		return sggCd != null && !sggCd.isBlank();
	}

	/** 소속 구 이름 (서울시청이면 빈 문자열) */
	public String getGuName() {
		if ("11530".equals(sggCd)) return "구로구";
		if ("11470".equals(sggCd)) return "양천구";
		if ("11560".equals(sggCd)) return "영등포구";
		return "";
	}
}
