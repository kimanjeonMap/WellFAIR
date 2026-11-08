package lx.edu.myabweb.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import java.io.IOException;

//@WebFilter("*.do")
public class AbFilter extends HttpFilter implements Filter {
       
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		System.out.println("AbFilter.doFilter() start");
		chain.doFilter(request, response);
		System.out.println("AbFilter.doFilter() end");
	}

	public void init(FilterConfig fConfig) throws ServletException {
	}

}
