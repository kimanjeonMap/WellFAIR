package wellfair.to;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 상세 패널 "점수 구성" 한 줄
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreItemTO {
	private String key;     // ugrnd | gosiwon | rooftop | viol | old | flood | low | cheap
	private String label;   // 지하층 (반지하 1개 층)
	private int points;     // +30
	private int weight;     // 설정된 가중치 (해당 없으면 points 0)
	private boolean hit;    // 이 건물이 해당하는지
	private String desc;    // 판정 근거
}
