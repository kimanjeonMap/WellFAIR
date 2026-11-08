package wellfair.to;

import java.util.List;

import lombok.Data;

// 구 단위 (zoom 14 이하) : 경계 + 라벨 "위험 N곳". ring 은 building 좌표의 볼록 껍질 (서비스가 한 번 계산해 캐시)
@Data
public class GuTO {
	private String sggCd;
	private String name;
	private double lat;
	private double lng;
	private int total;
	private int riskCount;              // scoreMin 이상 건물 수
	private int checkedCnt;             // 현장 확인된 건물 수 (checkedMonths 기간)
	private List<double[]> ring;        // [[lng, lat], ...]
}
