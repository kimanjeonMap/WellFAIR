package wellfair.to;

import lombok.Data;

// 상단 건수 칩 3개 (내 보관함 기준, 상태는 statusExpr 로 계산)
@Data
public class StatusCountTO {
	private int todo;
	private int doing;
	private int done;
}
