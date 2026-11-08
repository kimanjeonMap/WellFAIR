<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="대시보드"/>
<c:set var="pageCss" value="pages"/>
<c:set var="activeMenu" value="dashboard"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>

<div class="page">
  <form class="page-head" method="get" action="${ctx}/dashboard.do">
    <h1>대시보드</h1>
    <div class="right">
      <%-- 구청 계정은 소속 구로 잠금 (DashboardController 가 gu 를 강제). 서울시청은 구끼리 비교 --%>
      <c:set var="lockGu" value="${sessionScope.loginMember.sggCd}"/>
      <select class="field" name="gu" style="width:150px;height:36px" ${not empty lockGu ? 'disabled title="소속 구로 고정됩니다"' : ''}>
        <c:if test="${empty lockGu}"><option value="">전체 3개 구</option></c:if>
        <option value="11530" ${gu == '11530' ? 'selected' : ''}>구로구</option>
        <option value="11470" ${gu == '11470' ? 'selected' : ''}>양천구</option>
        <option value="11560" ${gu == '11560' ? 'selected' : ''}>영등포구</option>
      </select>
      <select class="field" disabled style="width:130px;height:36px"><option>${nowYm}</option></select>
      <div class="seg">
        <button type="submit" name="month" value="1" class="${months == 1 ? 'on' : ''}">1개월</button>
        <button type="submit" name="month" value="3" class="${months == 3 ? 'on' : ''}">3개월</button>
        <button type="submit" name="month" value="6" class="${months == 6 ? 'on' : ''}">6개월</button>
      </div>
      <button type="submit" class="btn btn-primary btn-sm">조회</button>
    </div>
  </form>

  <%-- KPI 4 --%>
  <div class="kpi-grid">
    <div class="card kpi"><div class="k-title"><i style="background:var(--ok)"></i>조사 대기 중</div><div class="k-val">${d.status.todo}<small>동</small></div><div class="k-sub">취약공간 ${d.total}동 중 현장 확인 전</div><span class="k-ico">⧗</span></div>
    <div class="card kpi"><div class="k-title"><i style="background:var(--warn)"></i>조사 중</div><div class="k-val">${d.status.doing}<small>동</small></div><div class="k-sub">현장확인 리스트에 담겨 확인 중</div><span class="k-ico">⌖</span></div>
    <div class="card kpi"><div class="k-title"><i style="background:var(--warn)"></i>현장 확인 리스트 잔여</div><div class="k-val">${d.listRemain}<small>개</small></div><div class="k-sub">남은 건물 ${d.listRemainBld}곳 · 전체 ${d.listTotalBld}곳</div><span class="k-ico">☰</span></div>
    <div class="card kpi"><div class="k-title"><i style="background:var(--primary)"></i>최근 한 달 완료</div><div class="k-val">${d.monthDone}<small>건</small></div><div class="k-sub"><c:choose><c:when test="${d.monthDone >= d.prevMonthDone}">지난달보다 ${d.monthDone - d.prevMonthDone}건 늘었어요</c:when><c:otherwise>지난달보다 ${d.prevMonthDone - d.monthDone}건 줄었어요</c:otherwise></c:choose> · 누적 ${d.cumDone}건</div><span class="k-ico">✓</span></div>
  </div>

  <div class="dash-grid">
    <%-- 조사 상태 분포 (도넛) --%>
    <div class="card">
      <div class="card-head"><h3>조사 상태 분포</h3><small>전체 취약공간 ${d.total}동 기준</small></div>
      <div class="donut-wrap">
        <div class="donut-box"><canvas id="donut"></canvas><div class="donut-center"><b>${d.total}</b><span>취약공간(동)</span></div></div>
        <div>
          <c:set var="tot" value="${d.total == 0 ? 1 : d.total}"/>
          <div class="lg-row"><span><i style="background:var(--primary)"></i>확인 완료</span><span>${d.status.done}동 · <fmt:formatNumber value="${d.status.done * 100 / tot}" maxFractionDigits="0"/>%</span></div>
          <div class="lg-bar"><b style="width:${d.status.done * 100 / tot}%;background:var(--primary)"></b></div>
          <div class="lg-row"><span><i style="background:var(--warn)"></i>조사 중</span><span>${d.status.doing}동 · <fmt:formatNumber value="${d.status.doing * 100 / tot}" maxFractionDigits="0"/>%</span></div>
          <div class="lg-bar"><b style="width:${d.status.doing * 100 / tot}%;background:var(--warn)"></b></div>
          <div class="lg-row"><span><i style="background:var(--ok)"></i>조사 대기</span><span>${d.status.todo}동 · <fmt:formatNumber value="${d.status.todo * 100 / tot}" maxFractionDigits="0"/>%</span></div>
          <div class="lg-bar"><b style="width:${d.status.todo * 100 / tot}%;background:var(--ok)"></b></div>
        </div>
      </div>
    </div>

    <%-- 월별 현장 확인 완료 (막대) --%>
    <div class="card">
      <div class="card-head"><h3>월별 현장 확인 완료</h3><small>최근 5개월 · 단위 건</small></div>
      <p class="muted" style="font-size:12px;margin-bottom:10px">이번 달 ${d.monthDone}건 확인했어요. 지난달보다 ${d.monthDone - d.prevMonthDone}건 차이예요.</p>
      <div class="chart-box"><canvas id="monthly"></canvas></div>
    </div>
  </div>

  <div class="dash-grid">
    <%-- 진행 중인 현장확인 리스트 --%>
    <div class="card">
      <div class="card-head"><h3>진행 중인 현장확인 리스트</h3><a href="${ctx}/fieldlist.do" style="color:var(--primary);font-size:13px">현장 확인 리스트로 이동 ›</a></div>
      <div class="mini-cards">
        <c:forEach var="f" items="${d.doingLists}">
          <div class="mini-card">
            <div class="mc-top"><span class="badge"><c:out value="${f.guName}"/> <c:out value="${f.emdNm}"/></span><span>진행 중</span></div>
            <b><c:out value="${f.title}"/></b>
            <div class="mc-sub"><c:out value="${f.firstAddr}"/></div>
            <div class="prog"><span class="muted">확인 진행</span><i><b style="width:${f.progressPct}%"></b></i><span>${f.doneCnt}/${f.itemCnt}</span></div>
            <div class="mc-foot"><span>남은 ${f.itemCnt - f.doneCnt}곳</span><a href="${ctx}/fielddetail.do?listId=${f.surveyId}">상세보기 ›</a></div>
          </div>
        </c:forEach>
        <c:if test="${empty d.doingLists}"><div class="empty" style="grid-column:1/-1">진행 중인 리스트가 없습니다.</div></c:if>
      </div>
    </div>

    <%-- 동별 진행 현황 --%>
    <div class="card">
      <div class="card-head"><h3>동별 진행 현황</h3><small><i class="lg-dot fill" style="border-color:var(--primary)"></i>확인 완료 <i class="lg-dot fill" style="border-color:var(--warn);background:var(--warn)"></i>조사 중 <i class="lg-dot fill" style="border-color:var(--ok);background:var(--ok)"></i>조사 대기</small></div>
      <p class="muted" style="font-size:12px;margin-bottom:12px">취약 건물이 많은 6개 동</p>
      <c:forEach var="r" items="${d.byDong}">
        <c:set var="rt" value="${r.total == 0 ? 1 : r.total}"/>
        <div class="dong-row"><span><c:out value="${r.emdNm}"/></span>
          <div class="bar"><b style="width:${r.done * 100 / rt}%;background:var(--primary)"></b><b style="width:${r.doing * 100 / rt}%;background:var(--warn)"></b><b style="width:${r.todo * 100 / rt}%;background:var(--ok)"></b></div>
          <span class="tot">${r.total}동</span></div>
      </c:forEach>
      <c:if test="${empty d.byDong}"><div class="empty">보관함에 건물이 없습니다.</div></c:if>
    </div>
  </div>
</div>

<script>
  window.DASH = {
    status: { done: ${d.status.done}, doing: ${d.status.doing}, todo: ${d.status.todo} },
    monthly: [<c:forEach var="m" items="${d.monthly}" varStatus="s">{ ym: '${m.ym}', cnt: ${m.cnt} }<c:if test="${!s.last}">,</c:if></c:forEach>]
  };
</script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js"></script>
<script src="${ctx}/resources/js/common.js"></script>
<script src="${ctx}/resources/js/dashboard.js"></script>
</body>
</html>
