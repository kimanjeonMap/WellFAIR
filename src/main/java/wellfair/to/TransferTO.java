package wellfair.to;

import lombok.Data;

// 부서 이관 1건 = transfer_log (05 오른쪽 "부서 이관 기록")
@Data
public class TransferTO {
	private long transferId;
	private String bldId;
	private Long recordId;      // 어느 현장 기록에서 넘겼나 (없을 수 있음)
	private String deptNm;      // 주택과 …
	private String reqType;     // 점검 요청 / 조회 요청
	private String status;      // SENT / REPLIED
	private long userId;
	private String userNm;
	private String createdAt;
}
