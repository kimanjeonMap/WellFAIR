<%@page import="java.util.List"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.7.1/jquery.min.js"></script>

</head>
<body>
	<h1>department list</h1>
	<form action="emplist.do" id="myform">
		<select name="deptId" id="deptId">
		<c:forEach items="${deptList}" var="deptId">
			<option value="${deptId}">${deptId}</option>
		</c:forEach>
		</select>
	</form>
	<a href="naver.do">naver</a>
	<div>
		<p id="myp"></p>
		<table>
			<thead>
				<tr>
					<th>empId</th>
					<th>last name</th>
				</tr>
			</thead>
			<tbody id="mytbody"></tbody>
		</table>
	</div>
	<script type="text/javascript">
		$('#deptId').on('change', function(){
			let deptId = $('#deptId').val();
			$.ajax({
				url: 'empjsonlist.do?deptId=' + deptId,
				contentType : 'application/json',
				success : function (result) {
					//alert(JSON.stringify(result));
					renderTable(result);
				}
			});
		});
		function renderTable(data){
			$('#mytbody').empty();
			$.each(data, function(index, item){
				let tr = $('<tr>');
				tr.append($('<td>').text(item.empId));
				tr.append($('<td>').text(item.lastName));
				$('#mytbody').append(tr);
			});
		}
	</script>
</body>
</html>