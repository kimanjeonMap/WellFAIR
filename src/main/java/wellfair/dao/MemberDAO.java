package wellfair.dao;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import wellfair.to.MemberTO;

// MemberService 에서만 부른다. 메서드명 = mapper-member.xml 의 id (테이블은 users)
@Component
public class MemberDAO {

	@Autowired
	SqlSession session;

	// 로그인 ID 로 사용자 1명 (없으면 null)
	public MemberTO getMemberByLoginId(String loginId) {
		return session.selectOne("mapper-member.getMemberByLoginId", loginId);
	}
}
