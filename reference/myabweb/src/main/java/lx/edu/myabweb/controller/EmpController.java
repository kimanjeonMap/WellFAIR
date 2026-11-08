package lx.edu.myabweb.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import lx.edu.myabweb.dao.EmpDAO;
import lx.edu.myabweb.to.EmpTO;

@Controller
public class EmpController {

	@Autowired
	EmpDAO dao;
	
	@RequestMapping("deptlist.do")
	public String deptList(HttpServletRequest req) {
		List<Integer> list = dao.getDeptList();
		req.setAttribute("deptList", list);
		return "deptlist";
	}
}
