<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="현장 확인 리스트 편집"/>
<c:set var="pageCss" value="pages"/>
<c:set var="activeMenu" value="field"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>

<div class="page">
  <div class="page-head" style="margin-bottom:23px">
    <h1>현장 확인 리스트 ${field.surveyId > 0 ? '편집' : '만들기'}</h1>
    <div class="right"><a class="btn btn-primary" id="btnBackList" href="${ctx}/fieldlist.do">‹ 목록으로</a></div>
  </div>
  <c:if test="${not empty msg}"><div class="flash"><c:out value="${msg}"/></div></c:if>

  <form method="post" action="${ctx}/fieldedit.do" id="editForm">
    <input type="hidden" name="listId" value="${field.surveyId}">
    <input type="hidden" name="moveMode" id="moveMode" value="${empty field.moveMode ? 'WALK' : field.moveMode}">
    <div id="orderIds"></div>

    <div class="fe-grid">
      <%-- 왼쪽 : 보관함 건물 --%>
      <div class="card">
        <div class="card-head"><h3>보관함 건물 <span id="pickCnt">${fn:length(pickList)}</span></h3></div>
        <div class="pick-list" id="pickList">
          <c:forEach var="p" items="${pickList}">
            <label class="pick-row" data-factor="${p.factorId}">
              <input type="checkbox" class="pick-chk" value="${p.factorId}">
              <span class="pk-ti"><c:out value="${fn:substringAfter(fn:replace(p.addr, '서울특별시 ', ''), '구 ')}"/> <c:out value="${empty p.bldNm ? p.useNm : p.bldNm}"/></span>
              <span class="tag"><c:out value="${empty p.factorText ? '요인 없음' : fn:split(p.factorText, '·')[0]}"/><c:if test="${p.vulnHhCnt > 0}"> · 가구 ${p.vulnHhCnt}</c:if></span>
              <span class="sc">${p.score}</span>
              <span>
                <c:choose>
                  <c:when test="${p.surveyId == field.surveyId and field.surveyId > 0}"><span class="badge blue">이 리스트</span></c:when>
                  <c:when test="${not empty p.surveyId}"><span class="badge" title="<c:out value='${p.surveyTitle}'/>">다른 리스트</span></c:when>
                  <c:otherwise><span class="badge">없음</span></c:otherwise>
                </c:choose>
              </span>
            </label>
          </c:forEach>
          <c:if test="${empty pickList}"><div class="empty">보관함이 비어 있습니다. 지도에서 먼저 저장하세요.</div></c:if>
        </div>
      </div>

      <%-- 가운데 화살표 : 보관함 건물 → 리스트에 담는 방향 --%>
      <div class="fe-arrow" aria-hidden="true"><span>→</span><small>담기</small></div>

      <%-- 오른쪽 : 리스트 이름 · 순서 --%>
      <div class="card">
        <div class="card-head"><h3><c:out value="${empty field.title ? '새 현장확인 리스트' : field.title}"/></h3></div>
        <div class="fe-title"><span>✎</span><input class="field" name="title" id="title" value="<c:out value='${field.title}'/>" placeholder="${field.surveyId > 0 ? '리스트 이름 (비워 두면 원래 이름 유지)' : '리스트 이름 (비워 두면 새 리스트1, 새 리스트2 … 로 저장)'}" maxlength="100"></div>
        <div class="fe-tools">
          <label class="fe-start">출발지 <select class="field sel-sm" id="startSel"><option value="11530">구로구청</option><option value="11470">양천구청</option><option value="11560">영등포구청</option></select></label>
          <button type="button" class="btn btn-line btn-sm" id="btnOptimize" title="출발지에서 가까운 순으로 초안을 만들고 2-opt 로 교차 구간을 풀어 다시 매깁니다 (직선거리)">⇅ 최적 동선으로 정렬</button>
        </div>
        <div class="fe-items" id="feItems"></div>
        <div class="fe-foot">
          <%-- 이동수단 : 저장하면 survey_list.move_mode (5-1) --%>
          <c:set var="mm" value="${empty field.moveMode ? 'WALK' : field.moveMode}"/>
          <div class="seg" id="modeSeg">
            <button type="button" data-mode="walk" class="${mm == 'WALK' ? 'on' : ''}">도보</button>
            <button type="button" data-mode="transit" class="${mm == 'TRANSIT' ? 'on' : ''}">대중교통</button>
            <button type="button" data-mode="car" class="${mm == 'CAR' ? 'on' : ''}">자동차</button>
          </div>
          <button type="submit" class="btn btn-primary">변경 내용 저장</button>
        </div>
      </div>
    </div>
  </form>

  <%-- 지도 : 담긴 건물 = 번호 핀 + 선, 나머지 보관함 건물 = 빈 원 (누르면 담기) --%>
  <div class="fe-map">
    <div class="nmap" id="map"></div>
    <%-- 확대 · 축소 : 메인 지도와 같은 버튼 (네이버 기본 컨트롤은 끔) --%>
    <div class="map-ctrl"><button type="button" id="zoomIn" title="확대">+</button><button type="button" id="zoomOut" title="축소">−</button></div>
    <div class="map-float tl"><label class="chk"><input type="checkbox" id="onlyMine"> 리스트 건물만 보기 (<span id="mineCnt">0</span>)</label></div>
    <div class="map-float bl"><span><i class="lg-dot fill"></i>리스트에 담긴 건물</span><span><i class="lg-dot"></i>담을 수 있는 보관함 건물</span></div>
    <%@ include file="/WEB-INF/views/include/mode-icons.jspf" %>
    <div class="map-float br rt-box" id="rtBox">
      <div class="rt" data-mode="walk" title="도보 (4km/h)">${icoWalk}<span><small>도보</small><b id="tWalk">0</b>분</span></div>
      <div class="rt" data-mode="transit" title="대중교통 (15km/h)">${icoTransit}<span><small>대중교통</small><b id="tTransit">0</b>분</span></div>
      <div class="rt" data-mode="car" title="자동차 (20km/h)">${icoCar}<span><small>자동차</small><b id="tCar">0</b>분</span></div>
    </div>
  </div>
</div>

<script>
  window.navermap_authFailure = function () { document.getElementById('map').innerHTML = '<div class="map-error"><b>네이버 지도 인증 실패</b></div>'; };
  window.FE_DATA = {
    surveyId: ${field.surveyId},
    gu: '<c:out value="${field.sggCd}"/>',   /* 리스트의 구 (구청 계정 = 소속 구). 출발지 기본값 */
    lockGu: '<c:out value="${sessionScope.loginMember.sggCd}"/>',
    items: [<c:forEach var="it" items="${field.items}" varStatus="s">{ factorId: ${it.factorId}, bldId: '${it.bldId}', title: '<c:out value="${it.title}"/>', lat: ${it.lat}, lng: ${it.lng}, score: ${it.score}, checked: ${it.checked}, sggCd: '${it.sggCd}' }<c:if test="${!s.last}">,</c:if></c:forEach>],
    pick: [<c:forEach var="p" items="${pickList}" varStatus="s">{ factorId: ${p.factorId}, bldId: '${p.bldId}', title: '<c:out value="${fn:substringAfter(fn:replace(p.addr, '서울특별시 ', ''), '구 ')}"/> <c:out value="${empty p.bldNm ? p.useNm : p.bldNm}"/>', lat: ${p.lat}, lng: ${p.lng}, score: ${p.score}, factor: '<c:out value="${p.factorText}"/>', surveyId: ${empty p.surveyId ? 'null' : p.surveyId}, sggCd: '${p.sggCd}' }<c:if test="${!s.last}">,</c:if></c:forEach>]
  };
</script>
<script src="https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=<c:out value='${naverClientId}'/>"></script>
<script src="${ctx}/resources/js/common.js"></script>
<script src="${ctx}/resources/js/field-map.js"></script>
<script src="${ctx}/resources/js/field-edit.js"></script>
</body>
</html>
