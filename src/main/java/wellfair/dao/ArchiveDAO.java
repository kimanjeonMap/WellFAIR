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
import wellfair.to.MemoTO;
import wellfair.to.RecordTO;
import wellfair.to.StatusCountTO;
import wellfair.to.TransferTO;

// ArchiveService 에서만 부른다. 메서드명 = mapper-archive.xml 의 id (테이블은 vulnerability_list / vulnerability_factor + 05 의 survey_record · transfer_log · handover_memo)
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

	public List<String> getArchiveUseList(long userId, String gu) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("gu", gu);
		return session.selectList("mapper-archive.getArchiveUseList", p);
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

	// ---------- 05 건물 확인 이력 ----------
	public ArchiveTO getFactorByBld(long userId, String bldId) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("bldId", bldId);
		return session.selectOne("mapper-archive.getFactorByBld", p);
	}

	public Long getPlanByBld(long userId, String bldId) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId);
		p.put("bldId", bldId);
		return session.selectOne("mapper-archive.getPlanByBld", p);
	}

	public List<RecordTO> getRecordList(String bldId) {
		return session.selectList("mapper-archive.getRecordList", bldId);
	}

	public List<TransferTO> getTransferList(String bldId) {
		return session.selectList("mapper-archive.getTransferList", bldId);
	}

	public int addTransfer(TransferTO to) {
		return session.insert("mapper-archive.addTransfer", to);
	}

	public List<MemoTO> getMemoList(String bldId) {
		return session.selectList("mapper-archive.getMemoList", bldId);
	}

	public int addMemo(MemoTO to) {
		return session.insert("mapper-archive.addMemo", to);
	}
}
