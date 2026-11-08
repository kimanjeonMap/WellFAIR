package wellfair.to;

import lombok.Data;

// 구·동 선택 목록 (building 에서 집계)
@Data
public class DongTO {
	private String sggCd;
	private String emdNm;
	private double lat;
	private double lng;
	private int cnt;
}
