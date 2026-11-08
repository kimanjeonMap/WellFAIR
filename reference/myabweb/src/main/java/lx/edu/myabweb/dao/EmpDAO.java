package lx.edu.myabweb.dao;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lx.edu.myabweb.to.EmpTO;

@Component
public class EmpDAO {
	
	@Autowired
	SqlSession session;
	
	public List<Integer> getDeptList() {
		return session.selectList("getDeptList");
	}

	// 이름을 전달받아서 직원의 정보를 리턴함
	public List<EmpTO> getEmpListByFirstName(String firstName) {
		List<EmpTO> result = new ArrayList<EmpTO>();
		
		return result;
	}
	// 입사년도를 전달받아서 부서원목록을 리턴
	public List<EmpTO> getEmpListByHireYear(int hireYear) {
		List<EmpTO> result = new ArrayList<EmpTO>();
		
		return result;
	}
	// 부서번호를 전달받아서 부서원목록을 리턴
	public List<EmpTO> getEmpListByDeptId(int deptId) throws Exception {
		List<EmpTO> empList = session.selectList("getEmpListByDeptId", deptId);
		return empList;
	}
	
	// 사번을 전달받아서 사원정보를 리턴
	//Emp getEmpById
	public EmpTO getEmpById(int empId) throws Exception {
		EmpTO emp = session.selectOne("getEmpById", empId);
		return emp;
	}
	public List<EmpTO> getEmpListByJobId(String jobId) throws Exception {
		List<EmpTO> empList = session.selectOne("getEmpListByJobId", jobId);
		return empList;
	}
}
