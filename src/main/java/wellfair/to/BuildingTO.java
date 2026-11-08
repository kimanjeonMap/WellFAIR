package wellfair.to;

import lombok.Data;

// building 테이블 + 점수 계산 결과 (결과 리스트 · 상세 공용)
@Data
public class BuildingTO {
	// building 컬럼 (snake_case → camelCase 자동)
	private String bldId;
	private String sggCd;
	private String emdNm;
	private String addr;
	private String bldNm;
	private String dongNm;
	private String useCd;
	private String useNm;
	private Integer grndFlr;
	private Integer ugrndFlr;
	private String useaprDay;
	private String violYn;
	private Integer units;
	private int ugrndRes;
	private int nonresHh;
	private String spType;
	private String spNm;
	private double elevM;
	private double slopeDeg;
	private int floodCnt;
	private double floodDepth;
	private String priceType;
	private Double pricePct;
	private String hospNm;
	private double hospDist;
	private String busNm;
	private double busDist;
	private String welfNm;
	private double welfDist;
	private double lng;
	private double lat;

	// 계산 컬럼
	private int score;          // 취약 점수 (100점 만점)
	private int ptUgrnd, ptGosiwon, ptRooftop, ptViol, ptOld, ptFlood, ptLow, ptCheap;  // 요인별 점수
	private Integer bldAge;     // 사용승인 후 경과 년수 (없으면 null)
	private String factorText;  // "지하층 · 노후 · 침수"
	private int rank;           // 결과 리스트 순번 (서비스에서)

	// 상세에서만
	private String checkedAt;   // 현장 확인 완료일 (없으면 null)
	private int archiveCnt;     // 내 보관함에 있으면 1
}
