package wellfair.to;

import lombok.Data;

/**
 * 취약 점수 기준값 + 지도 조회 조건 (쿼리 파라미터 → 그대로 바인딩).
 * 점수 SQL 은 mapper-building.xml 의 scoreExpr 한 곳. 여기 값이 #{...} 로 들어간다.
 * 기본값 = Figma 가중치 조정 팝업 (지하층·고시원 +30 / 옥탑·위반건축물 +20 / 노후·침수 +15 / 저지대 +5)
 */
@Data
public class AnalysisCriteriaTO {

	// ---- 요인 on/off · 기준값 · 가중치 ----
	private boolean ugrndOn = true;   private int ugrndW = 30;          // 지하층 거주 (ugrnd_res = 1)
	private boolean gosiwonOn = true; private int gosiwonW = 30;        // 고시원 (sp_type = '고시원')
	private boolean rooftopOn = true; private int rooftopW = 20;        // 옥탑 (res_flr = '옥탑층' 거주자 있는 건물)
	private boolean violOn = true;    private int violW = 20;           // 위반건축물 (viol_yn = '1')
	private boolean oldOn = true;     private int oldYears = 30;  private int oldW = 15;   // 노후 (사용승인 N년+)
	private boolean floodOn = true;   private int floodCnt = 1;   private int floodW = 15; // 침수 (flood_cnt N회+)
	private boolean lowOn = true;     private double lowElev = 15; private int lowW = 5;   // 저지대 (elev_m N m 이하)
	private boolean cheapOn = false;  private double cheapPct = 20; private int cheapW = 5; // 저가 (price_pct N% 이하, 참고)

	private int scoreMin = 70;        // 이 점수 이상만 "위험" (결과 리스트 · 도넛 빨간 숫자 · 70점 이상만 보기)

	// ---- 지역 · 검색 ----
	private String gu;                // sgg_cd (11530 구로 / 11470 양천 / 11560 영등포), 없으면 전체
	private String dong;              // emd_nm, 없으면 전체
	private String q;                 // 건물명·주소 검색어
	private String status;            // 보관함 탭 : TODO | DOING | DONE (없으면 전체)

	// ---- 지도 범위 (bbox=minLng,minLat,maxLng,maxLat → 서비스에서 풀어 넣음) ----
	private String bbox;
	private Double minLng, minLat, maxLng, maxLat;
	private int limit = 1500;         // 한 화면 핀 최대 수 (BUILDING_LIMIT)

	// ---- 격자 묶음 (clusters.do) : cell = 경도 도 단위 칸 크기, cellLat = cell × cos(기준위도) (서비스가 채움) ----
	private double cell;
	private double cellLat;
	private boolean snap = true;      // bbox 를 칸 경계까지 넓혀 가장자리 묶음도 칸 전체 건물 수로

	// ---- 결과 리스트 ----
	private String sort = "score";    // score | old | addr
	private int page = 1;
	private int size = 20;

	// ---- 상세 ----
	private String bldId;
	private Long userId;              // 보관함 여부 확인용 (세션 loginMember.userId)

	public int getOffset() {
		return (Math.max(page, 1) - 1) * size;
	}

	/** bbox 문자열을 min/max 로 풀어 넣는다. 형식이 다르면 범위 없음 */
	public void applyBbox() {
		if (bbox == null || bbox.isBlank()) return;
		String[] p = bbox.split(",");
		if (p.length != 4) return;
		try {
			minLng = Double.parseDouble(p[0].trim());
			minLat = Double.parseDouble(p[1].trim());
			maxLng = Double.parseDouble(p[2].trim());
			maxLat = Double.parseDouble(p[3].trim());
		} catch (NumberFormatException e) {
			minLng = minLat = maxLng = maxLat = null;
		}
	}
}
