package wellfair.common;

// 진행 상태 라벨 (상태는 저장하지 않고 mapper-archive.xml 의 statusExpr 로 계산)
public enum WorkStatus {
	TODO("진행해야 하는"),
	DOING("진행중"),
	DONE("진행완료");

	private final String label;

	WorkStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
