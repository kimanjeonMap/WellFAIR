package wellfair.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

// 대시보드 (분석 리포트) 한 화면 치 숫자
@Data
public class DashboardTO {
	private StatusCountTO status = new StatusCountTO();    // 조사 대기 / 조사 중 / 완료 (내 보관함)
	private int total;                                     // 보관함 건물 수
	private int listRemain;                                // 진행 중인 현장확인 리스트 수
	private int listRemainBld;                             // 그 리스트들의 남은 건물 수
	private int listTotalBld;
	private int monthDone;                                 // 이번 달 완료
	private int prevMonthDone;                             // 지난 달 완료
	private int cumDone;                                   // 누적 완료
	private List<MonthCountTO> monthly = new ArrayList<>();   // 월별 완료
	private List<DongStatusTO> byDong = new ArrayList<>();    // 동별 진행 현황
	private List<FieldListTO> doingLists = new ArrayList<>(); // 진행 중 리스트 카드

	@Data public static class MonthCountTO { private String ym; private int cnt; }
	@Data public static class DongStatusTO { private String emdNm; private int todo; private int doing; private int done; public int getTotal() { return todo + doing + done; } }
}
