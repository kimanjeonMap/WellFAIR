<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%--
  05 건물 확인 이력 (= 보관함 상세, 피그마 05 그대로). ArchiveController.detail → d(ArchiveDetailTO)
  상단 : 건물 · 배지 · 담당자/최근 확인일/확인 횟수/이관 부서/점수 보정 · [지도에서 보기][재확인 요청][부서로 넘기기]
  가운데 : [현장 확인 결과 입력] 폼 + 확인 이력 타임라인 (survey_record 최신순, [전체][요인 맞음][요인 아님] 필터는 JS)
  오른쪽 : 부서 이관 기록 · 분석 반영 · 인수인계 메모
--%>
<c:set var="pageTitle" value="건물 확인 이력"/>
<c:set var="pageCss" value="pages"/>
<c:set var="activeMenu" value="archive"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="b" value="${d.building}"/>
<c:set var="title" value="${fn:substringAfter(fn:replace(b.addr, '서울특별시 ', ''), '구 ')} ${empty b.bldNm ? b.useNm : b.bldNm}"/>
<c:set var="me" value="${sessionScope.loginMember}"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>

<div class="page ad-page">
  <div class="crumb"><a href="${ctx}/archive.do">보관함</a> › <c:out value="${title}"/></div>
  <c:if test="${not empty msg}"><div class="flash"><c:out value="${msg}"/></div></c:if>

  <%-- ===== 상단 ===== --%>
  <div class="card ad-head">
    <div class="ad-head-top">
      <div>
        <h1><c:out value="${title}"/>
          <span class="badge blue">취약도 ${d.savedScore}</span>
          <c:if test="${d.checkCnt > 0}"><span class="badge">현장 확인됨</span></c:if>
        </h1>
        <div class="sub"><c:out value="${b.addr}"/> · <c:out value="${empty b.useNm ? '용도 미상' : b.useNm}"/>
          <c:if test="${b.grndFlr != null}"> · 지상 ${b.grndFlr}층</c:if><c:if test="${b.ugrndFlr != null and b.ugrndFlr > 0}"> / 지하 ${b.ugrndFlr}층</c:if>
          <c:if test="${not empty d.factor}"> · <span class="muted">보관함 "<c:out value="${d.factor.title}"/>" (${d.factor.evaluatedAt} 저장)</span></c:if>
        </div>
      </div>
      <div class="right">
        <a class="btn btn-line" href="${ctx}/map.do?bldId=${b.bldId}">지도에서 보기</a>
        <div class="dd" id="recheckDd">
          <button type="button" class="btn btn-line dd-toggle" ${empty d.factor ? 'disabled title="보관함에 없는 건물입니다"' : ''}>재확인 요청 ▾</button>
          <div class="dd-menu">
            <div class="dd-head">현장확인 리스트에 담기<b><c:out value="${title}"/></b></div>
            <c:forEach var="s" items="${surveys}">
              <button type="button" class="to-survey" data-id="${s.surveyId}"><c:out value="${s.title}"/>에 담기<small><c:out value="${s.firstAddr}"/> · ${s.itemCnt}건</small></button>
            </c:forEach>
            <button type="button" class="to-survey new" data-id="0">+ 새 현장확인 리스트 만들어 담기</button>
          </div>
        </div>
        <button type="button" class="btn btn-primary" id="btnTransfer">부서로 넘기기</button>
      </div>
    </div>
    <div class="ad-stats">
      <div><small>담당자</small><b><c:choose><c:when test="${empty d.lastUserNm}">-</c:when><c:otherwise><c:out value="${d.lastUserNm}"/> <c:out value="${d.lastRankNm}"/></c:otherwise></c:choose></b></div>
      <div><small>최근 확인일</small><b><c:out value="${empty d.lastCheckedAt ? '-' : d.lastCheckedAt}"/></b></div>
      <div><small>확인 횟수</small><b>${d.checkCnt}회</b></div>
      <div class="gap"></div>
      <div><small>이관 부서</small><b><c:out value="${empty d.transferDept ? '없음' : d.transferDept}"/></b></div>
      <div><small>점수 보정</small><b>${d.savedScore} → ${d.adjustedScore} <small>(${d.adjText})</small></b></div>
    </div>
  </div>

  <%-- 부서로 넘기기 폼 (버튼 누르면 펼침). 사람에 대한 판단은 해당 부서가 하고, 우리는 주소만 넘긴다 --%>
  <form class="card ad-form" id="transferForm" method="post" action="${ctx}/archivetransfer.do" style="display:none">
    <input type="hidden" name="bldId" value="${b.bldId}">
    <h3>부서로 넘기기</h3>
    <div class="f-row"><label>넘길 부서</label>
      <select class="field" name="deptNm"><c:forEach var="dp" items="${depts}"><option value="<c:out value='${dp}'/>"><c:out value="${dp}"/></option></c:forEach></select></div>
    <div class="f-row"><label>요청 종류</label>
      <select class="field" name="reqType"><c:forEach var="rt" items="${reqTypes}"><option value="<c:out value='${rt}'/>"><c:out value="${rt}"/></option></c:forEach></select></div>
    <div class="f-row"><label>연결할 현장 기록</label>
      <select class="field" name="recordId" id="transferRecord">
        <option value="">(기록 없이 주소만)</option>
        <c:forEach var="r" items="${d.records}"><option value="${r.recordId}"><c:out value="${r.visitedAt}"/> · <c:out value="${r.userNm}"/> · <c:out value="${fn:substring(r.content, 0, 30)}"/></option></c:forEach>
      </select></div>
    <div class="f-row"><span>넘기는 내용</span><div class="muted" style="padding-top:8px">주소 · 건물 정보 · 현장 기록만 넘깁니다. 거주자 개인정보는 넘기지 않습니다.</div></div>
    <div class="f-foot"><button type="button" class="btn btn-line" data-close="#transferForm">취소</button><button type="submit" class="btn btn-primary">넘기기</button></div>
  </form>

  <div class="ad-grid">
    <%-- ===== 가운데 : 타임라인 ===== --%>
    <section>
      <div class="ad-sec-head">
        <h2>확인 이력 타임라인</h2>
        <button type="button" class="btn btn-primary btn-sm ad-add" id="btnCheckForm">＋ 현장 확인 결과 입력</button>
        <div class="seg" id="tlFilter">
          <button type="button" data-f="all" class="on">전체</button>
          <button type="button" data-f="yes">요인 맞음</button>
          <button type="button" data-f="no">요인 아님</button>
        </div>
      </div>

      <%-- 현장 체크 입력 폼 → POST archivecheck.do (visit_history + survey_record) --%>
      <form class="card ad-form" id="checkForm" method="post" action="${ctx}/archivecheck.do" style="display:none">
        <input type="hidden" name="bldId" value="${b.bldId}">
        <h3>현장 확인 결과 입력</h3>
        <c:if test="${empty d.planId}"><div class="ad-warn">이 건물은 아직 현장확인 리스트에 없습니다. 위 [재확인 요청]으로 리스트에 먼저 담아 주세요.</div></c:if>
        <div class="f-row"><label for="visitedAt">방문 일시</label><input type="datetime-local" class="field" name="visitedAt" id="visitedAt" style="width:240px"></div>
        <div class="f-row"><label>방문 결과</label>
          <select class="field" name="result" style="width:160px"><option value="MET">면담</option><option value="ABSENT">부재</option><option value="REFUSED">거부</option><option value="OTHER">기타</option></select></div>
        <div class="f-row"><span>사전 탐지 요인</span>
          <div class="chk-list">
            <c:set var="hitCnt" value="0"/>
            <c:forEach var="it" items="${d.scoreItems}">
              <c:if test="${it.hit}">
                <c:set var="hitCnt" value="${hitCnt + 1}"/>
                <div class="chk-row"><b><c:out value="${factorLabels[it.key]}"/></b>
                  <span class="radios">
                    <label><input type="radio" name="chk_${it.key}" value="Y"> 맞음</label>
                    <label><input type="radio" name="chk_${it.key}" value="N"> 아님</label>
                    <label><input type="radio" name="chk_${it.key}" value="?" checked> 판단 어려움</label>
                  </span></div>
              </c:if>
            </c:forEach>
            <c:if test="${hitCnt == 0}"><span class="muted">현재 기준으로 해당하는 요인이 없습니다.</span></c:if>
          </div></div>
        <div class="f-row"><span>새로 발견한 요인</span>
          <div id="addedWrap">
            <div class="added-row"><input class="field" name="added" placeholder="예) 반지하 창문 2곳" autocomplete="off"><button type="button" class="btn btn-line btn-sm" id="btnAddMore">＋</button></div>
          </div></div>
        <div class="f-row"><label for="content">관찰 메모</label><textarea class="field" name="content" id="content" rows="3" placeholder="예) 반지하 출입구가 도로보다 낮고 배수구가 막혀 있음."></textarea></div>
        <div class="f-row"><label for="scoreAdj">점수 보정</label>
          <div class="adj-wrap"><input type="range" name="scoreAdj" id="scoreAdj" min="-10" max="10" step="1" value="0"><b id="adjVal">0</b><span class="muted">저장 점수 ${d.savedScore} → <span id="adjTo">${d.savedScore}</span></span></div></div>
        <div class="f-foot"><button type="button" class="btn btn-line" data-close="#checkForm">취소</button><button type="submit" class="btn btn-primary" ${empty d.planId ? 'disabled' : ''}>저장</button></div>
      </form>

      <div class="timeline" id="timeline">
        <c:forEach var="r" items="${d.records}" varStatus="s">
          <div class="tl-item" data-yes="${r.hasYes}" data-no="${r.hasNo}">
            <i class="tl-dot ${s.first ? 'on' : ''}"></i>
            <div class="card tl-card">
              <div class="tl-head"><b><c:out value="${r.visitedAt}"/></b><span class="muted"><c:if test="${s.first}">최근 확인 · </c:if>담당자 <c:out value="${r.userNm}"/> · 방문 결과 <c:out value="${r.resultLabel}"/></span></div>
              <div class="tl-photos" title="사진은 현장 확인 앱 연동 예정"><div>외관</div><div>출입구 · 창문</div><div>우편함</div></div>
              <dl>
                <dt>사전 탐지 요인</dt><dd><c:out value="${empty d.detectedText ? '-' : d.detectedText}"/></dd>
                <dt>현장 체크</dt><dd><c:out value="${empty r.checkText ? '-' : r.checkText}"/></dd>
                <dt>관찰 메모</dt><dd><c:out value="${r.content}"/></dd>
                <c:if test="${r.scoreAdj != null}"><dt>점수 보정</dt><dd>${r.scoreAdj > 0 ? '+' : ''}${r.scoreAdj}</dd></c:if>
                <dt>이관 기록</dt><dd><c:choose><c:when test="${not empty r.transferDept}"><b><c:out value="${r.transferDept}"/> <c:out value="${r.transferReq}"/> (<c:out value="${r.transferAt}"/> ${r.transferStatus == 'REPLIED' ? '회신' : '전달'})</b></c:when><c:otherwise><b>넘긴 부서 없음</b></c:otherwise></c:choose></dd>
              </dl>
              <div class="tl-actions">
                <button type="button" class="btn btn-line btn-sm pdf-btn">확인서 PDF</button>
                <button type="button" class="btn btn-line btn-sm to-transfer" data-record="${r.recordId}">부서로 넘기기</button>
              </div>
            </div>
          </div>
        </c:forEach>
        <c:if test="${empty d.records}"><div class="card empty">아직 현장 확인 기록이 없습니다. 위 [현장 확인 결과 입력]으로 첫 기록을 남기세요.</div></c:if>
        <p class="tl-none muted" id="tlNone" style="display:none">조건에 맞는 기록이 없습니다.</p>
      </div>
    </section>

    <%-- ===== 오른쪽 ===== --%>
    <aside>
      <div class="card">
        <h3>부서 이관 기록</h3>
        <ul class="steps">
          <li class="${d.checkCnt > 0 ? 'done' : ''}"><b>현장 확인 완료</b><span><c:out value="${empty d.lastCheckedAt ? '아직 확인 전' : d.lastCheckedAt}"/></span></li>
          <c:forEach var="t" items="${d.transfers}">
            <li class="done"><b><c:out value="${t.deptNm}"/> <c:out value="${t.reqType}"/></b><span><c:out value="${t.createdAt}"/> 전달 · <c:out value="${t.userNm}"/></span></li>
            <li class="${t.status == 'REPLIED' ? 'done' : ''}"><b>${t.status == 'REPLIED' ? '회신 완료' : '회신 대기'}</b></li>
          </c:forEach>
          <c:if test="${empty d.transfers}"><li><b>넘긴 부서 없음</b><span>[부서로 넘기기]로 주소만 넘길 수 있어요</span></li></c:if>
        </ul>
        <p class="ad-note">사람에 대한 판단은 해당 부서가 합니다. 우리는 주소만 넘깁니다.</p>
      </div>

      <div class="card">
        <h3>분석 반영</h3>
        <small class="muted">취약도</small>
        <div class="ad-score"><b>${d.savedScore} → ${d.adjustedScore}</b><span class="badge ${d.scoreAdj == 0 ? '' : 'blue'}">${d.adjText} 현장 확인 보정</span></div>
        <ul class="ad-bullets">
          <c:if test="${not empty d.records}"><c:forEach var="f" items="${d.records[0].confirmed}"><li>· <c:out value="${f}"/> 요인 현장 확인으로 확정</li></c:forEach></c:if>
          <li>· 02 지도에 "현장 확인됨" 핀으로 표시</li>
          <li>· 06 리포트 정확도 집계에 반영</li>
        </ul>
      </div>

      <div class="card">
        <div class="card-head"><h3>인수인계 메모</h3><button type="button" class="link-muted" id="btnMemo">+ 추가</button></div>
        <c:forEach var="m" items="${d.memos}">
          <div class="memo"><b><c:out value="${m.fromNm}"/><c:if test="${not empty m.toNm}"> → <c:out value="${m.toNm}"/> <c:out value="${m.toRank}"/></c:if> · <c:out value="${m.createdAt}"/></b><p><c:out value="${m.memo}"/></p></div>
        </c:forEach>
        <c:if test="${empty d.memos}"><p class="muted" style="font-size:13px;margin-bottom:10px">인수인계 메모가 없습니다.</p></c:if>
        <form id="memoForm" method="post" action="${ctx}/archivememo.do" style="display:none">
          <input type="hidden" name="bldId" value="${b.bldId}">
          <select class="field" name="toUserId" id="memoTo" style="height:36px;margin-bottom:8px">
            <option value="">받는 담당자 (선택)</option>
            <c:forEach var="u" items="${users}"><c:if test="${u.userId != me.userId}"><option value="${u.userId}"><c:out value="${u.userNm}"/> <c:out value="${u.rankNm}"/> · <c:out value="${u.deptNm}"/></option></c:if></c:forEach>
          </select>
          <textarea class="field" name="memo" id="memoText" rows="3" placeholder="예) 배수구 막힘은 주택과에 사진과 함께 전달했습니다." style="height:auto;padding:8px 12px"></textarea>
          <div class="f-foot" style="margin-top:8px"><button type="button" class="btn btn-line btn-sm" data-close="#memoForm">취소</button><button type="submit" class="btn btn-primary btn-sm">저장</button></div>
        </form>
        <button type="button" class="btn btn-line btn-block" id="btnHandover">담당자 변경(인수인계)</button>
      </div>
    </aside>
  </div>
</div>

<%-- 재확인 요청 = 현장확인 리스트에 담기 (POST fieldadditems.do) --%>
<form id="addItemsForm" method="post" action="${ctx}/fieldadditems.do" style="display:none">
  <input type="hidden" name="listId" id="addListId"><input type="hidden" name="factorIds" value="${empty d.factor ? '' : d.factor.factorId}">
</form>

<script src="${ctx}/resources/js/common.js"></script>
<script src="${ctx}/resources/js/archive-detail.js"></script>
</body>
</html>
