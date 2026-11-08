package wellfair.to;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 상세 패널 "점수 구성" 한 줄 = 위험 유형 묶음 ("+45 주거 공간 (반지하 · 옥탑)")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreItemTO {
	private String key;     // space | bld | flood | econ
	private String label;   // 주거 공간
	private int points;     // 맞으면 묶음 가중치, 아니면 0
	private int weight;     // 설정된 묶음 가중치 (묶음 요인이 전부 꺼져 있으면 0)
	private boolean hit;    // 이 건물이 해당하는지
	private String desc;    // 맞은 요인 이름 ("반지하 · 옥탑") / "해당 없음" / "분석 조건에서 제외"
}
