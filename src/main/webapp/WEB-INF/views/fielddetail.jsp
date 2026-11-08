<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="현장 확인"/>
<c:set var="pageCss" value="pages"/>
<c:set var="activeMenu" value="field"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>

<div class="page">
  <div class="page-head">
    <div>
      <h1>현장 확인</h1>
      <div class="sub"><c:out value="${field.createdAt}"/> <c:out value="${field.title}"/> · 건물 ${field.itemCnt}동 · 담당 <c:out value="${sessionScope.loginMember.userNm}"/> <c:out value="${sessionScope.loginMember.rankNm}"/></div>
    </div>
    <div class="right">
      <a class="btn btn-line" href="${ctx}/fieldedit.do?listId=${field.surveyId}">수정</a>
      <button type="button" class="btn btn-primary" id="btnSendApp">현장 확인 앱으로 보내기</button>
    </div>
  </div>
  <c:if test="${not empty msg}"><div class="flash"><c:out value="${msg}"/></div></c:if>

  <%-- 경로 지도 + 소요시간 카드 --%>
  <div class="fd-map">
    <div class="nmap" id="map"></div>
    <div class="card route-card">
      <div class="card-head"><h3>경로 · 소요시간</h3><small><c:out value="${startName}"/> 출발 · ${field.itemCnt}곳 · 직선거리 × 1.3</small></div>
      <div class="route-modes">
        <div class="on">도보<b>${walkMin}<small>분</small></b></div>
        <div>자동차<b>${carMin}<small>분</small></b></div>
        <div>대중교통<b>${transitMin}<small>분</small></b></div>
      </div>
      <div class="route-stats">
        <div>이동 거리<b>${km}km</b></div>
        <div>현장 확인 예상<b>${checkMin}분 <small>(${field.itemCnt}동 × 20분)</small></b></div>
        <div>총 소요<b><c:out value="${totalText}"/></b></div>
        <div>출발 → 예상 종료<b>09:30 → ${endTime}</b></div>
      </div>
    </div>
  </div>

  <%-- 방문 순서 표 --%>
  <div class="card" style="padding:0">
    <div class="card-head" style="padding:16px 20px 0"><h3>현장 확인 리스트 <span class="badge">${field.itemCnt}건</span></h3><small>지도 방문 순서대로 정렬됨</small></div>
    <table class="tbl" style="border:0">
      <thead><tr><th style="width:60px">순서</th><th>건물명</th><th>건물 정보</th><th>취약 요인</th><th>이동 소요시간</th><th>가까운 자원</th><th style="width:200px">상태</th></tr></thead>
      <tbody>
      <c:forEach var="it" items="${field.items}" varStatus="s">
        <tr style="cursor:default">
          <td><span class="num ${it.checked ? 'done' : ''}">${it.checked ? '✓' : it.visitOrder}</span></td>
          <td><div class="name"><c:out value="${it.title}"/></div><div class="addr"><c:out value="${it.addr}"/></div></td>
          <td class="muted"><c:out value="${it.buildingInfo}"/></td>
          <td><c:forEach var="f" items="${fn:split(it.factorText, '·')}"><span class="tag"><c:out value="${fn:trim(f)}"/></span></c:forEach></td>
          <td><c:choose><c:when test="${s.first}"><c:out value="${startName}"/>에서 ${it.legMin}분 <span class="muted">(${it.legKm}km)</span></c:when><c:otherwise>이전 지점에서 ${it.legMin}분 <span class="muted">(${it.legKm}km)</span></c:otherwise></c:choose></td>
          <td><c:out value="${empty it.welfNm ? '-' : it.welfNm}"/> <span class="muted"><fmt:formatNumber value="${it.welfDist / 1000}" maxFractionDigits="1"/>km</span></td>
          <td>
            <c:choose>
              <c:when test="${it.checked}"><span class="badge done">확인 완료</span> <span class="muted" style="font-size:12px"><c:out value="${it.checkedAt}"/></span></c:when>
              <c:otherwise>
                <form method="post" action="${ctx}/fieldcheck.do" class="check-form">
                  <input type="hidden" name="planId" value="${it.planId}"><input type="hidden" name="listId" value="${field.surveyId}">
                  <select name="result"><option value="MET">면담</option><option value="ABSENT">부재</option><option value="REFUSED">거부</option><option value="OTHER">기타</option></select>
                  <button type="submit" class="btn btn-line btn-sm">확인 완료</button>
                </form>
              </c:otherwise>
            </c:choose>
          </td>
        </tr>
      </c:forEach>
      <c:if test="${empty field.items}"><tr><td colspan="7" class="empty">담긴 건물이 없습니다. "수정"에서 보관함 건물을 담으세요.</td></tr></c:if>
      </tbody>
    </table>
  </div>
</div>

<script>
  window.navermap_authFailure = function () { document.getElementById('map').innerHTML = '<div class="map-error"><b>네이버 지도 인증 실패</b></div>'; };
  window.FD_START = { name: '<c:out value="${startName}"/>', lat: ${startLat}, lng: ${startLng} };
  window.FD_ITEMS = [<c:forEach var="it" items="${field.items}" varStatus="s">{ title: '<c:out value="${it.title}"/>', lat: ${it.lat}, lng: ${it.lng}, order: ${it.visitOrder == null ? s.index + 1 : it.visitOrder}, checked: ${it.checked} }<c:if test="${!s.last}">,</c:if></c:forEach>];
</script>
<script src="https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=<c:out value='${naverClientId}'/>"></script>
<script src="${ctx}/resources/js/common.js"></script>
<script src="${ctx}/resources/js/field-map.js"></script>
<script src="${ctx}/resources/js/field-detail.js"></script>
</body>
</html>
