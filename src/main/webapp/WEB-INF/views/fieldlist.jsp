<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="현장 확인 리스트"/>
<c:set var="pageCss" value="pages"/>
<c:set var="activeMenu" value="field"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>

<div class="page">
  <div class="page-head"><h1>현장 확인 리스트</h1></div>
  <c:if test="${not empty msg}"><div class="flash"><c:out value="${msg}"/></div></c:if>

  <div class="page-head" style="margin-bottom:0">
    <div class="tabs" style="margin-bottom:0;border:0">
      <a href="${ctx}/fieldlist.do?tab=doing" class="${tab == 'doing' ? 'active' : ''}">진행중 <b>${doingCnt}</b></a>
      <a href="${ctx}/fieldlist.do?tab=done" class="${tab == 'done' ? 'active' : ''}">완료됨 <b>${doneCnt}</b></a>
    </div>
    <div class="right">
      <form method="get" action="${ctx}/fieldlist.do" class="search-wrap">
        <input type="hidden" name="tab" value="${tab}">
        <input class="field" name="q" value="<c:out value='${q}'/>" placeholder="리스트 이름 검색">
        <button type="submit" title="검색">⌕</button>
      </form>
      <a class="btn btn-primary" href="${ctx}/fieldedit.do?listId=0">+ 새 현장확인 리스트</a>
    </div>
  </div>
  <div style="border-bottom:1px solid var(--line);margin:0 0 6px"></div>

  <div class="fl-head"><span>리스트 이름</span><span>건물</span><span>확인 진행</span><span></span></div>

  <c:forEach var="f" items="${list}">
    <div class="fl-card" data-id="${f.surveyId}">
      <div class="fl-row">
        <div>
          <div class="t"><c:out value="${f.title}"/></div>
          <div class="s">
            <c:if test="${not empty f.emdNm}"><span class="badge"><c:out value="${f.guName}"/> <c:out value="${f.emdNm}"/></span></c:if>
            <c:choose>
              <c:when test="${f.itemCnt > 0}"><span><c:out value="${f.firstAddr}"/><c:if test="${f.itemCnt > 1}"> 외 ${f.itemCnt - 1}동</c:if></span></c:when>
              <c:otherwise><span class="muted">담긴 건물이 없습니다</span></c:otherwise>
            </c:choose>
          </div>
        </div>
        <div class="n">${f.itemCnt}동</div>
        <div class="prog"><i><b style="width:${f.progressPct}%"></b></i><span>${f.doneCnt}/${f.itemCnt}</span></div>
        <div class="fl-actions">
          <a class="btn btn-primary btn-sm" href="${ctx}/fieldedit.do?listId=${f.surveyId}">수정</a>
          <a class="btn btn-line btn-sm" href="${ctx}/fielddetail.do?listId=${f.surveyId}">상세보기</a>
          <button type="button" class="caret" title="펼치기">⌄</button>
        </div>
      </div>
      <div class="fl-body">
        <div class="cap">건물 ${f.itemCnt}동 · 동선 순서</div>
        <c:forEach var="it" items="${f.items}">
          <div class="fl-item">
            <span class="num ${it.checked ? 'done' : ''}">${it.checked ? '✓' : it.visitOrder}</span>
            <div class="ti"><b><c:out value="${it.title}"/></b><span><c:out value="${it.addr}"/></span></div>
            <span class="sc">${it.score}</span>
            <span class="badge ${it.checked ? 'done' : 'doing'}">${it.checked ? '확인 완료' : '미확인'}</span>
          </div>
        </c:forEach>
        <c:if test="${empty f.items}"><div class="muted" style="padding:8px 10px">"수정"에서 보관함 건물을 담으세요.</div></c:if>
        <div class="fl-body-foot">
          <c:choose>
            <c:when test="${f.doneCnt > 0}"><button type="button" class="fl-del" disabled title="현장 확인 기록이 있는 리스트는 건물 확인 이력을 지키기 위해 삭제할 수 없습니다">리스트 삭제</button></c:when>
            <c:otherwise><button type="button" class="fl-del" data-id="${f.surveyId}" data-title="<c:out value='${f.title}'/>">리스트 삭제</button></c:otherwise>
          </c:choose>
        </div>
      </div>
    </div>
  </c:forEach>

  <c:if test="${empty list}"><div class="empty">${tab == 'done' ? '완료된 리스트가 없습니다.' : '진행 중인 리스트가 없습니다.'}</div></c:if>
  <a class="fl-new" href="${ctx}/fieldedit.do?listId=0" title="새 현장확인 리스트">+</a>
</div>

<form id="flDeleteForm" method="post" action="${ctx}/fielddelete.do" style="display:none"><input type="hidden" name="listId" id="flDeleteId"><input type="hidden" name="tab" value="${tab}"></form>

<script src="${ctx}/resources/js/common.js"></script>
<script>
  $(function () {
    /* 카드 머리 어디를 눌러도 펼침. 하나를 펼치면 나머지는 접는다 (버튼·링크는 제외) */
    $('.fl-row').on('click', function (e) {
      if ($(e.target).closest('a, button:not(.caret)').length) return;
      var $card = $(this).closest('.fl-card'), open = !$card.hasClass('open');
      $('.fl-card').removeClass('open');
      $card.toggleClass('open', open);
    });
    $('.fl-card').first().addClass('open');

    /* 리스트 삭제 */
    $('.fl-del[data-id]').on('click', function () {
      if (!confirm('"' + $(this).data('title') + '" 리스트를 삭제할까요?\n담긴 건물은 보관함에 그대로 남습니다.')) return;
      $('#flDeleteId').val($(this).data('id'));
      $('#flDeleteForm').trigger('submit');
    });
  });
</script>
</body>
</html>
