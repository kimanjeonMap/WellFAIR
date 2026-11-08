package wellfair.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import wellfair.dao.ArchiveDAO;
import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.ArchiveRowTO;
import wellfair.to.ArchiveTO;
import wellfair.to.DashboardTO;
import wellfair.to.PageTO;
import wellfair.to.StatusCountTO;

// 보관함 (개인 단위 = vulnerability_list.user_id)
@Service
public class ArchiveService {

	@Autowired
	ArchiveDAO dao;

	// 탭 숫자 (신규 / 진행중 / 완료됨). gu 가 있으면 그 구의 보관함만 (구청 계정 = 소속 구, 서울시청 = 고른 구)
	public StatusCountTO getStatusCount(long userId, String gu) {
		StatusCountTO cnt = dao.getStatusCount(userId, gu);
		return cnt == null ? new StatusCountTO() : cnt;
	}

	/**
	 * 보관함에 건물 저장.
	 * 같은 구 · 같은 기준(JSON)의 분석 리스트가 있으면 거기에, 없으면 리스트를 새로 만들어 넣는다 (분석 1회 = 리스트 1개).
	 */
	public void addArchive(long userId, String bldId, String sggCd, Integer score, String criteriaJson) {
		ArchiveTO to = new ArchiveTO();
		to.setUserId(userId);
		to.setSggCd(sggCd);
		to.setCriteria(criteriaJson);

		ArchiveTO list = dao.getListByCriteria(to);
		if (list == null) {
			to.setTitle(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 분석 · " + guName(sggCd));
			dao.addList(to);                       // vulnerabilityId 채워짐
		} else {
			to.setVulnerabilityId(list.getVulnerabilityId());
		}

		to.setBldId(bldId);
		to.setVulnerabilityScore(score);
		to.setVulnerabilityGrade(grade(score));
		dao.addFactor(to);
	}

	// 보관함 목록 한 페이지 (archive.do)
	public PageTO<ArchiveRowTO> getArchivePage(AnalysisCriteriaTO c) {
		if (c.getSize() <= 0 || c.getSize() > 100) c.setSize(20);
		int total = dao.getArchiveTotal(c);
		List<ArchiveRowTO> list = dao.getArchiveList(c);
		return new PageTO<>(c.getPage(), c.getSize(), total, list);
	}

	// 보관함에서 빼기 (현장확인 리스트에 담긴 건물은 남는다). 뺀 건수 리턴
	public int deleteArchive(long userId, List<Long> factorIds) {
		if (factorIds == null || factorIds.isEmpty()) return 0;
		return dao.deleteFactors(userId, factorIds);
	}

	// 대시보드 조각
	public List<DashboardTO.DongStatusTO> getStatusByDong(long userId, String gu) {
		return dao.getStatusByDong(userId, gu);
	}

	public List<DashboardTO.MonthCountTO> getMonthlyDone(long userId, int months, String gu) {
		return dao.getMonthlyDone(userId, months, gu);
	}

	static String grade(Integer score) {
		if (score == null) return null;
		if (score >= 80) return "매우높음";
		if (score >= 60) return "높음";
		if (score >= 40) return "보통";
		return "낮음";
	}

	public static String guName(String sggCd) {
		if ("11530".equals(sggCd)) return "구로구";
		if ("11470".equals(sggCd)) return "양천구";
		if ("11560".equals(sggCd)) return "영등포구";
		return sggCd == null ? "" : sggCd;
	}
}
