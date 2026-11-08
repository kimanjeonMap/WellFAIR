package lx.edu.myabweb.controller;

import java.awt.PageAttributes.MediaType;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import lx.edu.myabweb.dao.EmpDAO;
import lx.edu.myabweb.to.EmpTO;

@RestController
public class EmpRESTController {
	
	@Autowired
	EmpDAO dao;

	
	//empjsonlist.do?deptId=100, 해당 부서의 부서원 목록을 리턴
	@RequestMapping(value="empjsonlist.do")
	public List<EmpTO> getJsonList(@RequestParam("deptId") int deptId) throws Exception {
		List<EmpTO> result = dao.getEmpListByDeptId(deptId);
		return result;
	}
	
	@RequestMapping(value="/emp/{id}", produces = "application/json;charset=UTF-8")
	public @ResponseBody EmpTO getById(@PathVariable("id") int id) throws Exception {
		EmpTO to = dao.getEmpById(id);
		System.out.println(to);
		return to;
	}
	
}
