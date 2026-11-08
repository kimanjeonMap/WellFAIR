package wellfair.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.ArchiveRowTO;
import wellfair.to.ArchiveTO;
import wellfair.to.DashboardTO;
import wellfair.to.StatusCountTO;

// ArchiveService 에서만 부른다. 메서드명 = mapper-archive.xml 의 id (테이블은 vulnerability_list / vulnerability_factor)
@Component
public class ArchiveDAO {

	@Autowired
	SqlSession session;

	public StatusCountTO getStatusCount(long userId, String gu) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("gu", gu);
		return session.selectOne("mapper-archive.getStatusCount", p);
	}

	public ArchiveTO getListByCriteria(ArchiveTO to) {
		return session.selectOne("mapper-archive.getListByCriteria", to);
	}

	public int addList(ArchiveTO to) {
		return session.insert("mapper-archive.addList", to);
	}

	public int addFactor(ArchiveTO to) {
		return session.insert("mapper-archive.addFactor", to);
	}

	public int getArchiveTotal(AnalysisCriteriaTO c) {
		return session.selectOne("mapper-archive.getArchiveTotal", c);
	}

	public List<ArchiveRowTO> getArchiveList(AnalysisCriteriaTO c) {
		return session.selectList("mapper-archive.getArchiveList", c);
	}

	public int deleteFactors(long userId, List<Long> factorIds) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("factorIds", factorIds);
		return session.delete("mapper-archive.deleteFactors", p);
	}

	public List<DashboardTO.DongStatusTO> getStatusByDong(long userId, String gu) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("gu", gu);
		return session.selectList("mapper-archive.getStatusByDong", p);
	}

	public List<DashboardTO.MonthCountTO> getMonthlyDone(long userId, int months, String gu) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("months", months);
		p.put("gu", gu);
		return session.selectList("mapper-archive.getMonthlyDone", p);
	}
}
