package wellfair.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.BuildingMarkerTO;
import wellfair.to.BuildingTO;
import wellfair.to.ClusterTO;
import wellfair.to.DongTO;
import wellfair.to.GuTO;

// MapService 에서만 부른다. 메서드명 = mapper-building.xml 의 id
@Component
public class BuildingDAO {

	@Autowired
	SqlSession session;

	// 법정동 목록 (gu 없으면 전체)
	public List<DongTO> getDongList(String gu) {
		return session.selectList("mapper-building.getDongList", gu);
	}

	// 법정동 → 행정동 쌍
	public List<DongTO> getAdmList(String gu) {
		return session.selectList("mapper-building.getAdmList", gu);
	}

	// 구 경계 계산용 좌표 (앱에서 한 번만)
	public List<DongTO> getCoordList() {
		return session.selectList("mapper-building.getCoordList");
	}

	// 구 단위 요약 : 구마다 전체 · 기준점 이상 건물 수
	public List<GuTO> getGuSummary(AnalysisCriteriaTO c) {
		return session.selectList("mapper-building.getGuSummary", c);
	}

	// 격자 묶음 (bbox + cell)
	public List<ClusterTO> getClusterList(AnalysisCriteriaTO c) {
		return session.selectList("mapper-building.getClusterList", c);
	}

	// 건물 핀 (bbox, 점수 높은 순 limit)
	public List<BuildingMarkerTO> getMarkerList(AnalysisCriteriaTO c) {
		return session.selectList("mapper-building.getMarkerList", c);
	}

	public List<BuildingMarkerTO> getCheckedList(AnalysisCriteriaTO c) {
		return session.selectList("mapper-building.getCheckedList", c);
	}

	public int getResultCount(AnalysisCriteriaTO c) {
		return session.selectOne("mapper-building.getResultCount", c);
	}

	public List<BuildingTO> getResultList(AnalysisCriteriaTO c) {
		return session.selectList("mapper-building.getResultList", c);
	}

	public BuildingTO getBuilding(AnalysisCriteriaTO c) {
		return session.selectOne("mapper-building.getBuilding", c);
	}
}
