package wellfair.to;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * 격자 묶음 1개 (살피구로 Cluster 와 같은 모양). SQL(#getClusterList) 이 칸마다 집계한 한 줄.
 *  lng/lat   묶음 표시 위치 (구성 건물 좌표 평균)
 *  size      건물 수
 *  riskCount 기준점(scoreMin) 이상 건물 수
 *  maxScore  묶음 안 최고 점수
 *  hist      점수 구간별 건물 수 [80+, 60~79, 40~59, 40 미만] (범례 LEVELS 순서)
 *  extent    구성 건물 범위 [minLng, minLat, maxLng, maxLat]
 *  cell      격자 칸 범위 [minLng, minLat, maxLng, maxLat] (서비스가 채움)
 *  point     건물이 1동이면 그 건물, 아니면 null (서비스가 채움)
 */
@Data
public class ClusterTO {
	private double lng;
	private double lat;
	private int size;
	private int riskCount;
	private int maxScore;
	private double[] cell;
	private BuildingMarkerTO point;

	// ---- SQL 원본 컬럼 (JSON 에는 안 내보냄) ----
	@JsonIgnore private long ix;
	@JsonIgnore private long iy;
	@JsonIgnore private int hRed, hOrange, hYellow, hGreen;
	@JsonIgnore private double minLng, minLat, maxLng, maxLat;
	@JsonIgnore private String oneBldId, oneBldNm, oneAddr, oneUseNm;

	public int[] getHist() {
		return new int[] { hRed, hOrange, hYellow, hGreen };
	}

	public double[] getExtent() {
		return new double[] { minLng, minLat, maxLng, maxLat };
	}
}
