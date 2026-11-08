package wellfair.to;

import java.util.List;

import lombok.Data;

// api/map/building.do 응답 : 건물 + 점수 구성 + 거주자 집계
@Data
public class BuildingDetailTO {
	private BuildingTO building;
	private List<ScoreItemTO> scoreItems;
	private ResidentCountTO residents;
}
