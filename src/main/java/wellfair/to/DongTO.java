package wellfair.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 구·동 선택 목록 (building 에서 집계).
 * 동 선택 2단계 (CLAUDE.md 5-1) : 법정동(emd_nm) 한 줄에 행정동(adm_nm) 목록 adms 가 달린다.
 * bbox = [minLng, minLat, maxLng, maxLat] : 동을 고르면 지도를 이 범위로 옮긴다.
 */
@Data
public class DongTO {
	private String sggCd;
	private String emdNm;          // 법정동 (구로동)
	private String admCd;          // 행정동 코드 (행정동 줄에만)
	private String admNm;          // 행정동 (구로2동, 행정동 줄에만)
	private double lat;
	private double lng;
	private int cnt;               // 건물 수
	private Double minLat, minLng, maxLat, maxLng;
	private List<DongTO> adms = new ArrayList<>();   // 법정동 줄 : 그 아래 행정동들

	public double[] getBbox() {
		return minLat == null ? null : new double[] { minLng, minLat, maxLng, maxLat };
	}
}
