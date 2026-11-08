package lx.edu.myabweb.controller;

import java.util.List;

import lx.edu.myabweb.aop.LogAdvice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lx.edu.myabweb.dao.AddrBookDAO;
import lx.edu.myabweb.to.AddrBookTO;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequiredArgsConstructor
public class AbController {
	
	//@Autowired
	private final AddrBookDAO dao;

	@RequestMapping("/list.do")
	public String list(HttpServletRequest req) {
		//login하지 않았다면 loginform redirect한다. 
		System.out.println("list()");
		List<AddrBookTO> list = dao.getList();
		req.setAttribute("data", list);
		return "addrbook_list";
	}
	
	@RequestMapping("/form.do")
	public ModelAndView form() {
		ModelAndView result = new ModelAndView();
		result.setViewName("addrbook_form");
		return result;
	}
	
	@RequestMapping(value="/form.do", method=RequestMethod.POST)
	public String insert(AddrBookTO to) {
		System.out.println(to);
		int result = dao.insert(to);
		System.out.println(result);
		return "redirect:list.do";
	}
	
	@RequestMapping("editform.do")
	public String editForm(@RequestParam("abId") int abId, HttpServletRequest request) {
		//요청에서 뭘? 방명록아이디 
		AddrBookTO ab = dao.getById(abId);
		request.setAttribute("ab", ab);
		return "addrbook_edit_form";
	}
	
	@RequestMapping("edit.do")
	public String edit(AddrBookTO to) {
		//1. client에서 전송한 수정된 방명록 정보를 꺼내온다. 
		System.out.println(to);
		dao.update(to);
		return "redirect:list.do";
	}
	
}