package wellfair.to;

import lombok.Data;

// 확대 상태 건물 핀 / 묶음 안 1동 / 현장 확인됨 핀
@Data
public class BuildingMarkerTO {
	private String bldId;
	private double lat;
	private double lng;
	private int score;
	private String bldNm;
	private String addr;
	private String useNm;       // 핀 머리 그림(용도) 용
	private String checkedAt;   // 현장 확인됨 핀일 때만 (yyyy-MM-dd)
}
