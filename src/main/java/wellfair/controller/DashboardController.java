package wellfair.controller;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import wellfair.service.ArchiveService;
import wellfair.service.FieldService;
import wellfair.to.DashboardTO;
import wellfair.to.FieldListTO;
import wellfair.to.MemberTO;
import wellfair.to.StatusCountTO;

// 대시보드(분석 리포트, Figma 06). 숫자는 ArchiveService(상태) + FieldService(리스트) 에서
@Controller
public class DashboardController {

	@Autowired
	ArchiveService archiveService;

	@Autowired
	FieldService fieldService;

	// GET dashboard.do?gu&month(1|3|6)&status
	@GetMapping("dashboard.do")
	public String dashboard(@RequestParam(value = "gu", required = false) String gu,
			@RequestParam(value = "month", defaultValue = "1") int month,
			@RequestParam(value = "status", required = false) String status,
			HttpSession session, Model model) {
		MemberTO member = (MemberTO) session.getAttribute("loginMember");
		long userId = member.getUserId();
		if (member.isGuUser()) gu = member.getSggCd();          // 구청 계정 : 소속 구만 (화면의 gu 는 무시)
		if (gu != null && gu.isBlank()) gu = null;
		int months = month == 3 ? 3 : month == 6 ? 6 : 1;

		DashboardTO d = new DashboardTO();
		StatusCountTO sc = archiveService.getStatusCount(userId, gu);
		d.setStatus(sc);
		d.setTotal(sc.getTodo() + sc.getDoing() + sc.getDone());

		List<FieldListTO> doing = fieldService.getFieldList(userId, gu, "doing", null, false);
		d.setDoingLists(doing.size() > 3 ? doing.subList(0, 3) : doing);
		d.setListRemain(doing.size());
		int remain = 0, totalBld = 0;
		for (FieldListTO f : doing) { remain += f.getItemCnt() - f.getDoneCnt(); totalBld += f.getItemCnt(); }
		d.setListRemainBld(remain);
		d.setListTotalBld(totalBld);

		// 월별 완료 : 최근 5개월을 빈 달까지 채운다
		List<DashboardTO.MonthCountTO> raw = archiveService.getMonthlyDone(userId, 5, gu);
		List<DashboardTO.MonthCountTO> monthly = new ArrayList<>();
		YearMonth now = YearMonth.now();
		for (int i = 4; i >= 0; i--) {
			String ym = now.minusMonths(i).toString();
			DashboardTO.MonthCountTO m = new DashboardTO.MonthCountTO();
			m.setYm(ym);
			for (DashboardTO.MonthCountTO r : raw) if (ym.equals(r.getYm())) m.setCnt(r.getCnt());
			monthly.add(m);
		}
		d.setMonthly(monthly);
		d.setMonthDone(monthly.get(4).getCnt());
		d.setPrevMonthDone(monthly.get(3).getCnt());
		d.setCumDone(sc.getDone());

		d.setByDong(archiveService.getStatusByDong(userId, gu));

		model.addAttribute("d", d);
		model.addAttribute("gu", gu == null ? "" : gu);
		model.addAttribute("months", months);
		model.addAttribute("status", status == null ? "" : status);
		model.addAttribute("nowYm", now.toString());
		return "dashboard";
	}
}
