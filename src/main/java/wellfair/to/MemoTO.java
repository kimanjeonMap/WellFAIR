package wellfair.to;

import lombok.Data;

// 인수인계 메모 1건 = handover_memo (05 오른쪽 "인수인계 메모")
@Data
public class MemoTO {
	private long memoId;
	private String bldId;
	private long fromUserId;
	private String fromNm;
	private String fromRank;
	private Long toUserId;      // 받는 담당자 (없으면 공용 메모)
	private String toNm;
	private String toRank;
	private String memo;
	private String createdAt;
}
