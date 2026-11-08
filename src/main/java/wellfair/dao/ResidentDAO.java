package wellfair.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import wellfair.to.ResidentCountTO;
import wellfair.to.ResidentTO;

// ResidentService 에서만 부른다. 거주자 테이블(residency·household·person·arrears)은 mapper-resident.xml 에서만 읽는다
@Component
public class ResidentDAO {

	@Autowired
	SqlSession session;

	public ResidentCountTO getResidentCount(String bldId) {
		return session.selectOne("mapper-resident.getResidentCount", bldId);
	}

	public List<ResidentTO> getResidentList(String bldId) {
		return session.selectList("mapper-resident.getResidentList", bldId);
	}

	public ResidentTO getResident(long residentId) {
		return session.selectOne("mapper-resident.getResident", residentId);
	}

	public String getResidentBldId(long residentId) {
		return session.selectOne("mapper-resident.getResidentBldId", residentId);
	}
}
