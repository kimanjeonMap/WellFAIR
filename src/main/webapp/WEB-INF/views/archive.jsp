<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="보관함"/>
<c:set var="pageCss" value="pages"/>
<c:set var="pageCss2" value="map"/>
<c:set var="activeMenu" value="archive"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>

<div class="page">
  <div class="page-head"><h1>보관함</h1></div>
  <c:if test="${not empty msg}"><div class="flash"><c:out value="${msg}"/></div></c:if>

  <%-- 탭 (상태는 statusExpr 로 계산) --%>
  <c:set var="st" value="${empty criteria.status ? 'TODO' : criteria.status}"/>
  <div class="tabs">
    <a href="${ctx}/archive.do?status=TODO&gu=${criteria.gu}&dong=${criteria.dong}&sort=${criteria.sort}" class="${st == 'TODO' ? 'active' : ''}">신규 <b>${statusCount.todo}</b></a>
    <a href="${ctx}/archive.do?status=DOING&gu=${criteria.gu}&dong=${criteria.dong}&sort=${criteria.sort}" class="${st == 'DOING' ? 'active' : ''}">진행중 <b>${statusCount.doing}</b></a>
    <a href="${ctx}/archive.do?status=DONE&gu=${criteria.gu}&dong=${criteria.dong}&sort=${criteria.sort}" class="${st == 'DONE' ? 'active' : ''}">완료됨 <b>${statusCount.done}</b></a>
  </div>

  <%-- 필터 (구청 계정은 구 선택칸이 소속 구로 잠김 : ArchiveController 가 gu 를 강제) --%>
  <c:set var="lockGu" value="${sessionScope.loginMember.sggCd}"/>
  <form class="filter-bar" method="get" action="${ctx}/archive.do" id="filterForm">
    <input type="hidden" name="status" value="${st}">
    <input type="hidden" name="sort" id="sortField" value="${empty criteria.sort ? 'score' : criteria.sort}">
    <select class="field" name="gu" id="selGu" ${not empty lockGu ? 'disabled title="소속 구로 고정됩니다"' : ''}>
      <c:if test="${empty lockGu}"><option value="">전체 구</option></c:if>
      <option value="11530" ${criteria.gu == '11530' ? 'selected' : ''}>구로구</option>
      <option value="11470" ${criteria.gu == '11470' ? 'selected' : ''}>양천구</option>
      <option value="11560" ${criteria.gu == '11560' ? 'selected' : ''}>영등포구</option>
    </select>
    <select class="field" name="dong" id="selDong">
      <option value="">전체 동</option>
      <c:forEach var="d" items="${dongList}">
        <c:if test="${empty criteria.gu or d.sggCd == criteria.gu}">
          <option value="<c:out value='${d.emdNm}'/>" ${d.emdNm == criteria.dong ? 'selected' : ''}><c:out value="${d.emdNm}"/></option>
        </c:if>
      </c:forEach>
    </select>
    <select class="field" disabled><option>시나리오: 평시</option></select>
    <span class="spacer"></span>
    <div class="seg" id="sortSeg">
      <button type="button" data-sort="score" class="${criteria.sort == 'score' or empty criteria.sort ? 'on' : ''}">취약도 높은 순</button>
      <button type="button" data-sort="priority" class="${criteria.sort == 'priority' ? 'on' : ''}">우선순위 순</button>
      <button type="button" data-sort="recent" class="${criteria.sort == 'recent' ? 'on' : ''}">최근 확인 순</button>
    </div>
    <div class="search-wrap">
      <input class="field" name="q" value="<c:out value='${criteria.q}'/>" placeholder="건물명, 주소 검색">
      <button type="submit" title="검색">⌕</button>
    </div>
  </form>

  <%-- 선택 바 --%>
  <div class="sel-bar">
    <span id="selCount"></span>
    <div class="dd" id="bulkDd" style="display:none">
      <button type="button" class="btn btn-primary btn-sm dd-toggle">현장확인 리스트에 저장 ▾</button>
      <div class="dd-menu">
        <div class="dd-head">선택한 건물을 현장확인 리스트에 저장</div>
        <c:forEach var="s" items="${surveys}">
          <button type="button" class="to-survey" data-id="${s.surveyId}"><c:out value="${s.title}"/><small><c:out value="${s.firstAddr}"/> · ${s.itemCnt}건</small></button>
        </c:forEach>
        <button type="button" class="to-survey new" data-id="0">+ 새 현장확인 리스트 만들어 저장</button>
      </div>
    </div>
    <button type="button" class="btn btn-line btn-sm" id="btnDelete" style="display:none">보관함에서 빼기</button>
  </div>

  <%-- 목록 --%>
  <table class="tbl" id="archiveTbl">
    <thead><tr>
      <th style="width:36px"><input type="checkbox" id="chkAll"></th>
      <th>건물명 / 주소</th><th>취약 요인</th><th style="width:160px">취약도</th><th style="width:110px">우선순위(추정)</th>
      <th style="width:90px">상태</th><th style="width:110px">저장일</th><th style="width:90px"></th>
    </tr></thead>
    <tbody>
    <c:forEach var="r" items="${page.list}">
      <c:set var="shortAddr" value="${fn:replace(fn:replace(r.addr, '서울특별시 ', ''), ' ', ' ')}"/>
      <tr data-id="${r.bldId}" data-factor="${r.factorId}" data-lat="${r.lat}" data-lng="${r.lng}">
        <td><input type="checkbox" class="rowChk" value="${r.factorId}"></td>
        <td><div class="name"><c:out value="${fn:substringAfter(shortAddr, '구 ')}"/> <c:out value="${empty r.bldNm ? r.useNm : r.bldNm}"/></div><div class="addr"><c:out value="${r.addr}"/></div></td>
        <td><c:out value="${empty r.factorText ? '-' : r.factorText}"/></td>
        <td><span class="score-bar"><i><b style="width:${r.score}%"></b></i><span>${r.score}</span></span></td>
        <td>${r.priorityRank}위</td>
        <td><span class="badge ${fn:toLowerCase(r.status)}"><c:out value="${r.statusLabel}"/></span></td>
        <td class="muted"><c:out value="${r.savedAt}"/></td>
        <td>
          <div class="dd row-dd">
            <button type="button" class="btn btn-primary btn-sm dd-toggle">저장 ▾</button>
            <div class="dd-menu">
              <div class="dd-head">현장확인 리스트에 저장<b><c:out value="${fn:substringAfter(shortAddr, '구 ')}"/> <c:out value="${empty r.bldNm ? r.useNm : r.bldNm}"/></b></div>
              <c:forEach var="s" items="${surveys}">
                <button type="button" class="to-survey" data-id="${s.surveyId}" data-factor="${r.factorId}"><c:out value="${s.title}"/>에 저장<small><c:out value="${s.firstAddr}"/> · ${s.itemCnt}건</small></button>
              </c:forEach>
              <button type="button" class="to-survey new" data-id="0" data-factor="${r.factorId}">+ 새 현장확인 리스트 만들어 저장</button>
            </div>
          </div>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty page.list}"><tr><td colspan="8" class="empty">보관한 건물이 없습니다. 지도에서 건물을 골라 "보관함에 저장" 하세요.</td></tr></c:if>
    </tbody>
  </table>

  <%-- 페이지 --%>
  <c:if test="${page.totalPages > 1}">
    <div class="pager">
      <c:forEach var="p" begin="1" end="${page.totalPages}">
        <a href="${ctx}/archive.do?status=${st}&gu=${criteria.gu}&dong=${criteria.dong}&sort=${criteria.sort}&q=${criteria.q}&page=${p}" class="${p == page.page ? 'on' : ''}">${p}</a>
      </c:forEach>
    </div>
  </c:if>
</div>

<%-- 현장확인 리스트에 저장 (POST fieldadditems.do) / 보관함에서 빼기 (POST archivedelete.do) --%>
<form id="addItemsForm" method="post" action="${ctx}/fieldadditems.do" style="display:none"><input type="hidden" name="listId" id="addListId"><div id="addIds"></div></form>
<form id="deleteForm" method="post" action="${ctx}/archivedelete.do" style="display:none"><div id="delIds"></div></form>

<%-- 행 클릭 상세 : 지도의 건물 상세 패널(building-panel.jspf) 공용 --%>
<aside class="right-panel archive-panel" id="panelRight">
  <div class="rp-view" id="detailView" style="display:none">
    <%@ include file="/WEB-INF/views/include/building-panel.jspf" %>
  </div>
</aside>

<script src="${ctx}/resources/js/common.js"></script>
<script src="${ctx}/resources/js/map/map-api.js"></script>
<script src="${ctx}/resources/js/map/map-panel.js"></script>
<script src="${ctx}/resources/js/archive.js"></script>
</body>
</html>
