package lx.edu.myabweb;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lx.edu.myabweb.to.AddrBookTO;

//@WebServlet("*.do")
public class AddrBookController extends HttpServlet {
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String page = "index.jsp";
		String uri = req.getRequestURI(); //http://localhost:8080/empapp/list.do
		String requestUri = uri.substring(uri.lastIndexOf("/"), uri.length());
		System.out.println("requestUri=" + requestUri);
		switch (requestUri) {
			case "/edit.do" : {
				//AddrBookTO ab = makeAddrBook(req);
				//System.out.println(ab);
				//AddrBookDAO.update(ab);
//				List<AddrBookTO> list = AddrBookDAO.getList();
//				req.setAttribute("data", list);
				page = "addrbook_list.jsp";
				break;
			}
			case "/editform.do" : {
				String abIdStr = req.getParameter("abId");
				int abId = 1;
				if(abIdStr!=null) abId = Integer.parseInt(abIdStr);
				//AddrBookTO ab = AddrBookDAO.getById(abId);
				//req.setAttribute("ab", ab);
				page = "addrbook_edit_form.jsp";
				break;
			}
			case "/form.do" : {
				page = "addrbook_form.jsp";
				break;
			}
			case "/list.do" : {
//				//model의 메서드 호출하고 결과를 request에 넣는다.
//				List<AddrBookTO> list = AddrBookDAO.getList();
//				req.setAttribute("data", list);
//				page = "addrbook_list.jsp";
				break;
			}
			case "/insert.do": {
//	           AddrBookTO ab = makeAddrBook(req);
//	           AddrBookDAO.insert(ab);
//	           List<AddrBookTO> list = AddrBookDAO.getList();
//			   req.setAttribute("data", list);
			   page = "addrbook_list.jsp";
	           break;
		       }
		    default:
		}
		RequestDispatcher rd = req.getRequestDispatcher(page);
		rd.forward(req, resp);
	}
	
	AddrBookTO makeAddrBook(HttpServletRequest req) {
	       AddrBookTO ab = new AddrBookTO();
	       if(req.getParameter("abId")!=null) {
	    	   int abId = Integer.parseInt(req.getParameter("abId"));
	    	   //ab.setAbId(abId);
	       }
//	       ab.setAbName(req.getParameter("abName"));
//	       ab.setAbEmail(req.getParameter("abEmail"));
//	       ab.setAbComdept(req.getParameter("abComdept"));
//	       ab.setAbBirth(req.getParameter("abBirth"));
//	       ab.setAbTel(req.getParameter("abTel"));
//	       ab.setAbMemo(req.getParameter("abMemo"));
	       
	       return ab;
	   }


}
