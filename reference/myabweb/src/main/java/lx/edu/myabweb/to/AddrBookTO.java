package lx.edu.myabweb.to;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data // getter & setter
//@Getter
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
public class AddrBookTO {
	private int abId;
	private String abName;
	private String abEmail;
	private String abComdept;
	private String abBirth;
	private String abTel;
	private String abMemo;
	
	
	@Override
	public String toString() {
		return "AddrBookTO [abId=" + abId + ", abName=" + abName + ", abEmail=" + abEmail + ", abComdept=" + abComdept
				+ ", abBirth=" + abBirth + ", abTel=" + abTel + ", abMemo=" + abMemo + "]";
	}
	
}
