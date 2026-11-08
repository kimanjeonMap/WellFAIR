package wellfair.to;

import lombok.Data;

// 건물별 거주자 집계 (지도·리스트·상세에는 이것만 노출)
@Data
public class ResidentCountTO {
	private int hhCnt;         // 가구 수
	private int mbrCnt;        // 거주자 수
	private int highRiskHh;    // 고위험고립 가구
	private int socialIsoHh;   // 사회적고립 가구
	private int old75;         // 75세 이상 (명)
	private int severeDsbl;    // 심한 장애 (명)
	private int nearPoorHh;    // 차상위·기초생활 가구
	private int pensionCnt;    // 기초연금 (명)
	private int arrearsHh;     // 건보료 체납 가구
	private int careBurdenHh;  // 독박돌봄 가구
	private int ugrndHh;       // 지하 거주 가구
	private int rooftopHh;     // 옥탑 거주 가구
	private int maxPriority;   // 가구 우선 점수 최대값 (방문 2순위)
	private int vulnHh;        // 취약 유형이 하나라도 있는 가구 (priorityExpr > 0)

	/** "고위험고립 2가구 · 75세 이상 3명" 식 요약 (없으면 빈 문자열) */
	public String getSummary() {
		StringBuilder sb = new StringBuilder();
		append(sb, highRiskHh, "고위험고립 ", "가구");
		append(sb, socialIsoHh, "사회적고립 ", "가구");
		append(sb, old75, "75세 이상 ", "명");
		append(sb, severeDsbl, "심한 장애 ", "명");
		append(sb, nearPoorHh, "차상위·기초 ", "가구");
		append(sb, arrearsHh, "건보료 체납 ", "가구");
		append(sb, careBurdenHh, "독박돌봄 ", "가구");
		return sb.toString();
	}

	/** 상세 패널 "거주 취약 가구" 한 줄 : 많은 유형 3개 ("독거노인 · 기초생활수급 · 장애인" 식) */
	public String getVulnTypes() {
		java.util.List<int[]> idx = new java.util.ArrayList<>();
		String[] names = { "고위험고립", "사회적고립", "75세 이상", "심한 장애", "차상위·기초생활", "건보료 체납", "독박돌봄", "지하 거주", "옥탑 거주" };
		int[] counts = { highRiskHh, socialIsoHh, old75, severeDsbl, nearPoorHh, arrearsHh, careBurdenHh, ugrndHh, rooftopHh };
		for (int i = 0; i < counts.length; i++) if (counts[i] > 0) idx.add(new int[] { counts[i], i });
		idx.sort((a, b) -> b[0] - a[0]);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(3, idx.size()); i++) { if (sb.length() > 0) sb.append(" · "); sb.append(names[idx.get(i)[1]]); }
		return sb.toString();
	}

	private static void append(StringBuilder sb, int n, String label, String unit) {
		if (n <= 0) return;
		if (sb.length() > 0) sb.append(" · ");
		sb.append(label).append(n).append(unit);
	}
}
