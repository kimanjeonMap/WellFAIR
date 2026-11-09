package wellfair.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

<<<<<<< HEAD
// api/field/walk.do 응답 : 출발 → 1 → 2 → … 도보 경로 (TMAP 보행자, 구간마다 호출해서 이어 붙임)
@Data
public class WalkPathTO {
	private int totalDistance;                      // 도보 거리 합 m
	private int totalTime;                          // 도보 시간 합 초
	private List<Leg> legs = new ArrayList<>();     // 구간별 (points 순서대로 n-1 개)
	private List<double[]> path = new ArrayList<>(); // [[lng, lat], ...] 지도에 그릴 선 전체
=======
// api/field/walk.do 응답 : 출발 → 1 → 2 → … 경로 (TMAP 보행자 · 자동차 · 대중교통, 구간마다 호출해서 이어 붙임)
@Data
public class WalkPathTO {
	private String mode;                              // walk | transit | car
	private int totalDistance;                        // 거리 합 m
	private int totalTime;                            // 시간 합 초
	private List<Leg> legs = new ArrayList<>();       // 구간별 (points 순서대로 n-1 개)
	private List<Segment> segments = new ArrayList<>(); // 지도에 그릴 선 (수단마다 색을 다르게)
	private List<double[]> path = new ArrayList<>();  // [[lng, lat], ...] 전체 좌표 (화면 맞추기용)
>>>>>>> 41792602687852f26f9968d59f66ac24227def2c

	@Data
	public static class Leg {
		private int distance;     // m
		private int time;         // 초
<<<<<<< HEAD
		private boolean straight; // true = TMAP 실패 / 너무 가까움 → 직선으로 대신 그림
=======
		private boolean straight; // true = TMAP 실패 → 직선으로 대신 그림
		private String summary;   // 대중교통 : 탄 노선 "간선:600 (A→B) · 2호선 (C→D)", 걸어간 구간은 null 또는 "도보"
	}

	@Data
	public static class Segment {
		private String mode;      // WALK | CAR | BUS | SUBWAY | EXPRESSBUS | TRAIN ...
		private String route;     // 노선 이름 (대중교통)
		private List<double[]> path;
>>>>>>> 41792602687852f26f9968d59f66ac24227def2c
	}
}
