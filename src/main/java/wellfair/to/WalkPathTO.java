package wellfair.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

// api/field/walk.do 응답 : 출발 → 1 → 2 → … 도보 경로 (TMAP 보행자, 구간마다 호출해서 이어 붙임)
@Data
public class WalkPathTO {
	private int totalDistance;                      // 도보 거리 합 m
	private int totalTime;                          // 도보 시간 합 초
	private List<Leg> legs = new ArrayList<>();     // 구간별 (points 순서대로 n-1 개)
	private List<double[]> path = new ArrayList<>(); // [[lng, lat], ...] 지도에 그릴 선 전체

	@Data
	public static class Leg {
		private int distance;     // m
		private int time;         // 초
		private boolean straight; // true = TMAP 실패 / 너무 가까움 → 직선으로 대신 그림
	}
}
