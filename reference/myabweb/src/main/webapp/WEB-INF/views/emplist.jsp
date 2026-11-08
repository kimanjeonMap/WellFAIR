<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core"%>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>사원 목록</title>
</head>
<body>

	<h2>${deptId}번부서 사원 목록</h2>

	<table border="1">
		<tr>
			<td>사번</td>
			<td>이름</td>
			<td>급여</td>
			<td>부서번호</td>
		</tr>
		<tr>
			<c:if test="${not empty empList}">
				<c:forEach items="${empList}" var="emp">
					<tr>
						<td>${emp.empId}</td>
						<td>${emp.firstName}</td>
						<td>${emp.salary}</td>
						<td>${emp.departmentId}</td>
					</tr>
				</c:forEach>
			</c:if>

			<c:if test="${empty empList}">
				<tr>
					<td colspan="4">해당 부서에 사원이 없습니다.</td>
				</tr>
			</c:if>
		</tr>
	</table>

	<br>
	<a href="deptlist.do">부서 목록으로 돌아가기</a>
</body>
</html>