package wellfair.to;

import java.util.List;

import lombok.Data;

// 페이지 응답 (결과 리스트 등)
@Data
public class PageTO<T> {
	private int page;
	private int size;
	private int total;
	private int totalPages;
	private List<T> list;

	public PageTO() {}

	public PageTO(int page, int size, int total, List<T> list) {
		this.page = page;
		this.size = size;
		this.total = total;
		this.totalPages = size > 0 ? (int) Math.ceil(total / (double) size) : 0;
		this.list = list;
	}
}
