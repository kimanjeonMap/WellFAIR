package lx.edu.myabweb.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lx.edu.myabweb.to.AddrBookTO;

@Component
public class AddrBookDAO {

	@Autowired
	SqlSession session;
	
	//방명록 목록을 리턴한다.
	public List<AddrBookTO> getList() {
		return session.selectList("getList");
	}
	
	public  int insert(AddrBookTO to) {
	       return session.insert("insert", to);
	}

	public AddrBookTO getById(int abId) {
		return session.selectOne("getById", abId);
	}

	public int update(AddrBookTO ab) {
		int result = session.update("update", ab);
		return result;
	}
	
}