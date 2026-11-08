<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="보관함"/>
<c:set var="pageCss" value="pages"/>
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

  <%-- 탭 : 전체 / 신규 / 진행중 / 완료됨 (상태는 statusExpr 로 계산). 링크에 filterQs 를 붙여 상세 필터를 유지 --%>
  <c:set var="st" value="${empty criteria.status ? '' : criteria.status}"/>
  <div class="tabs">
    <a href="${ctx}/archive.do?status=&${filterQs}" class="${empty st ? 'active' : ''}">전체 <b>${statusCount.todo + statusCount.doing + statusCount.done}</b></a>
    <a href="${ctx}/archive.do?status=TODO&${filterQs}" class="${st == 'TODO' ? 'active' : ''}">신규 <b>${statusCount.todo}</b></a>
    <a href="${ctx}/archive.do?status=DOING&${filterQs}" class="${st == 'DOING' ? 'active' : ''}">진행중 <b>${statusCount.doing}</b></a>
    <a href="${ctx}/archive.do?status=DONE&${filterQs}" class="${st == 'DONE' ? 'active' : ''}">완료됨 <b>${statusCount.done}</b></a>
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
    <%-- 행정동 (법정동을 고르면 그 아래 행정동, 5-1 동 선택 2단계) --%>
    <select class="field" name="adm" id="selAdm" ${empty criteria.dong ? 'disabled' : ''}>
      <option value="">전체 행정동</option>
      <c:forEach var="d" items="${dongTree}">
        <c:if test="${d.emdNm == criteria.dong and (empty criteria.gu or d.sggCd == criteria.gu)}">
          <c:forEach var="a" items="${d.adms}"><option value="<c:out value='${a.admNm}'/>" ${a.admNm == criteria.adm ? 'selected' : ''}><c:out value="${a.admNm}"/> (${a.cnt})</option></c:forEach>
        </c:if>
      </c:forEach>
    </select>
    <select class="field" disabled><option>시나리오: 평시</option></select>
    <button type="button" class="btn btn-line btn-sm ${criteria.fopen ? 'on' : ''}" id="btnFilter">상세 필터<c:if test="${criteria.filterCount > 0}"> <b class="fcnt">${criteria.filterCount}</b></c:if> ▾</button>
    <input type="hidden" name="fopen" id="fopen" value="${criteria.fopen}">
    <span class="spacer"></span>
    <div class="seg" id="sortSeg">
      <button type="button" data-sort="score" class="${criteria.sort == 'score' or empty criteria.sort ? 'on' : ''}">취약도 높은 순</button>
      <button type="button" data-sort="priority" class="${criteria.sort == 'priority' ? 'on' : ''}">우선순위 순</button>
      <button type="button" data-sort="recent" class="${criteria.sort == 'recent' ? 'on' : ''}">최근 확인 순</button>
    </div>
    <div class="search-wrap">
      <input class="field" name="q" id="archiveQ" value="<c:out value='${criteria.q}'/>" placeholder="건물명, 주소 검색" autocomplete="off">
      <button type="button" class="search-clear" id="archiveQClear" title="검색어 지우기" aria-label="검색어 지우기" ${empty criteria.q ? 'hidden' : ''}>×</button>
      <button type="submit" title="검색">⌕</button>
    </div>

    <%-- [상세 필터] 펼침칸 (5-1). 값은 GET 파라미터로 가고 mapper-archive.archiveRows 의 <if> 가 거른다 --%>
    <div class="filter-detail" id="filterDetail" style="${criteria.fopen ? '' : 'display:none'}">
      <div class="fd-row"><span class="fd-label">취약 요인</span>
        <div class="fd-opts">
          <c:forEach var="f" items="${factorOpts}">
            <label class="chk"><input type="checkbox" name="factors" value="${f.key}" ${fn:contains(factorsSel, ','.concat(f.key).concat(',')) ? 'checked' : ''}> <c:out value="${f.value}"/></label>
          </c:forEach>
          <span class="fd-mode">
            <label class="chk"><input type="radio" name="factorMode" value="all" ${criteria.factorMode != 'any' ? 'checked' : ''}> 모두 가진 건물</label>
            <label class="chk"><input type="radio" name="factorMode" value="any" ${criteria.factorMode == 'any' ? 'checked' : ''}> 하나라도</label>
          </span>
        </div></div>
      <div class="fd-row"><span class="fd-label">점수 구간</span>
        <div class="fd-opts">
          <label class="chk"><input type="checkbox" name="bands" value="80" ${fn:contains(bandsSel, ',80,') ? 'checked' : ''}> 80점 이상</label>
          <label class="chk"><input type="checkbox" name="bands" value="60" ${fn:contains(bandsSel, ',60,') ? 'checked' : ''}> 60~79점</label>
          <label class="chk"><input type="checkbox" name="bands" value="40" ${fn:contains(bandsSel, ',40,') ? 'checked' : ''}> 40~59점</label>
          <span class="muted">(저장 당시 점수)</span>
        </div></div>
      <div class="fd-row"><span class="fd-label">건물 용도</span>
        <div class="fd-opts">
          <select class="field" name="use"><option value="">전체 용도</option>
            <c:forEach var="u" items="${useList}"><option value="<c:out value='${u}'/>" ${u == criteria.use ? 'selected' : ''}><c:out value="${u}"/></option></c:forEach>
          </select>
          <span class="muted">법정동 · 행정동은 위 선택칸에서</span>
        </div></div>
      <div class="fd-row"><span class="fd-label">거주자 조건</span>
        <div class="fd-opts">
          <label class="chk"><input type="checkbox" name="resHigh" value="true" ${criteria.resHigh ? 'checked' : ''}> 고위험고립 있음</label>
          <label class="chk"><input type="checkbox" name="resOld75" value="true" ${criteria.resOld75 ? 'checked' : ''}> 75세 이상 있음</label>
          <label class="chk"><input type="checkbox" name="resSevere" value="true" ${criteria.resSevere ? 'checked' : ''}> 심한 장애 있음</label>
          <span class="muted">(건물별 요약으로 거름 · 사람 정보는 보이지 않음)</span>
        </div></div>
      <div class="fd-row"><span class="fd-label">방문 결과</span>
        <div class="fd-opts">
          <label class="chk"><input type="checkbox" name="visits" value="MET" ${fn:contains(visitsSel, ',MET,') ? 'checked' : ''}> 만남</label>
          <label class="chk"><input type="checkbox" name="visits" value="ABSENT" ${fn:contains(visitsSel, ',ABSENT,') ? 'checked' : ''}> 부재</label>
          <label class="chk"><input type="checkbox" name="visits" value="REFUSED" ${fn:contains(visitsSel, ',REFUSED,') ? 'checked' : ''}> 거부</label>
        </div></div>
      <div class="fd-row"><span class="fd-label">저장일</span>
        <div class="fd-opts">
          <input type="date" class="field" name="savedFrom" value="<c:out value='${criteria.savedFrom}'/>"> <span class="muted">~</span>
          <input type="date" class="field" name="savedTo" value="<c:out value='${criteria.savedTo}'/>">
        </div></div>
      <div class="fd-foot">
        <a class="btn btn-line btn-sm" href="${ctx}/archive.do?status=${st}&gu=${criteria.gu}&sort=${criteria.sort}">초기화</a>
        <button type="submit" class="btn btn-primary btn-sm">필터 적용</button>
      </div>
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
    <%-- 행 클릭 → archivedetail.do?bldId= (05 건물 확인 이력) --%>
    <c:forEach var="r" items="${page.list}">
      <c:set var="shortAddr" value="${fn:replace(fn:replace(r.addr, '서울특별시 ', ''), ' ', ' ')}"/>
      <tr data-id="${r.bldId}" data-factor="${r.factorId}" title="클릭하면 건물 확인 이력으로">
        <td><input type="checkbox" class="rowChk" value="${r.factorId}"></td>
        <td><div class="name"><c:out value="${fn:substringAfter(shortAddr, '구 ')}"/> <c:out value="${empty r.bldNm ? r.useNm : r.bldNm}"/></div><div class="addr"><c:out value="${r.addr}"/></div></td>
        <td><c:out value="${empty r.factorText ? '-' : r.factorText}"/><c:if test="${r.vulnHhCnt > 0}"> <small class="muted">· 취약 가구 ${r.vulnHhCnt}</small></c:if></td>
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
        <a href="${ctx}/archive.do?status=${st}&page=${p}&${filterQs}" class="${p == page.page ? 'on' : ''}">${p}</a>
      </c:forEach>
    </div>
  </c:if>
</div>

<%-- 현장확인 리스트에 저장 (POST fieldadditems.do) / 보관함에서 빼기 (POST archivedelete.do) --%>
<form id="addItemsForm" method="post" action="${ctx}/fieldadditems.do" style="display:none"><input type="hidden" name="listId" id="addListId"><div id="addIds"></div></form>
<form id="deleteForm" method="post" action="${ctx}/archivedelete.do" style="display:none"><div id="delIds"></div></form>

<script src="${ctx}/resources/js/common.js"></script>
<script src="${ctx}/resources/js/archive.js"></script>
</body>
</html>
