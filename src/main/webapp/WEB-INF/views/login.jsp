<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="로그인"/>
<c:set var="pageCss" value="login"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<div class="login-page">

  <%-- 왼쪽 : 브랜드 소개 --%>
  <section class="login-visual">
    <div class="login-visual-body">
      <h1 class="login-logo"><span class="brand-logo"><span class="b1">Well</span><span class="b2">FAIR</span></span></h1>
      <p class="login-sub">주거 취약공간 분석 플랫폼</p>
      <p class="login-desc">개인정보 없이 공간 데이터만으로 비적정 주거 건물을 찾고, 현장 확인 결과로 분석을 다시 고칩니다.</p>
      <ul class="login-points">
        <li>레이어 중첩 기반 취약도 분석 · 재해 시나리오</li>
        <li>현장 확인 그룹·동선 → 현장 확인 앱 연동</li>
        <li>현장 결과가 점수에 반영되는 순환 구조</li>
      </ul>
    </div>
    <p class="login-foot">구로·양천·영등포 구청 내부 업무 전용 · 개인정보를 보관하지 않습니다</p>
  </section>

  <%-- 오른쪽 : 로그인 폼 (POST login.do) --%>
  <section class="login-form-wrap">
    <form class="login-form" id="loginForm" action="${pageContext.request.contextPath}/login.do" method="post" autocomplete="off">
      <h2>로그인</h2>
      <p class="desc">사원번호와 비밀번호를 입력해 주세요.</p>

      <label for="loginId">사원번호 (아이디)</label>
      <input class="field" id="loginId" name="loginId" value="<c:out value='${loginId}'/>" placeholder="아이디 입력" required autofocus>

      <label for="pw">비밀번호</label>
      <input class="field" id="pw" name="pw" type="password" placeholder="비밀번호 입력" required>

      <div class="login-options">
        <label class="chk"><input type="checkbox" id="saveEmpNo"> 사원번호 저장</label>
        <a href="#" class="link-muted" onclick="alert('연동 예정'); return false;">비밀번호 찾기</a>
      </div>

      <c:if test="${not empty msg}">
        <p class="login-error"><c:out value="${msg}"/></p>
      </c:if>

      <button class="btn btn-primary btn-block" type="submit">로그인</button>

      <div class="login-hint">
        <p>· 비밀번호 5회 연속 오류 시 계정 잠금 → 본인 인증 후 해제</p>
        <p>· 30분 동안 입력이 없으면 자동 로그아웃</p>
      </div>
    </form>
  </section>

</div>
<script src="${pageContext.request.contextPath}/resources/js/login.js"></script>
</body>
</html>
