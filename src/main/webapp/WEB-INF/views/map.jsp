<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="취약공간 분석 지도"/>
<c:set var="pageCss" value="map"/>
<c:set var="activeMenu" value="map"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>

<div class="map-layout">

  <%-- ================= 왼쪽 : 레이어 패널 (Figma 02) ================= --%>
  <aside class="layer-panel" id="panelLeft">
    <div class="layer-scroll">

      <section class="sec">
        <div class="sec-head"><span class="caret"></span>1. 건물 · 주거</div>
        <div class="sec-body">
          <div class="f-row" data-factor="ugrnd">
            <label class="chk"><input type="checkbox" class="f-chk"> 지하층(반지하)</label>
            <span class="f-val">지하 거주 등록</span><span class="f-w"></span>
          </div>
          <div class="f-row" data-factor="rooftop">
            <label class="chk"><input type="checkbox" class="f-chk"> 옥탑</label>
            <span class="f-val">옥탑층 거주</span><span class="f-w"></span>
          </div>
          <div class="f-row" data-factor="gosiwon">
            <label class="chk"><input type="checkbox" class="f-chk"> 고시원(다중생활시설)</label>
            <span class="f-val">대장 유형</span><span class="f-w"></span>
          </div>
          <div class="f-row" data-factor="viol">
            <label class="chk"><input type="checkbox" class="f-chk"> 위반건축물</label>
            <span class="f-val">대장 등재</span><span class="f-w"></span>
          </div>
          <div class="f-row has-rng" data-factor="old">
            <label class="chk"><input type="checkbox" class="f-chk"> 노후주택 (준공 30년+)</label>
            <input type="range" class="f-rng"><span class="f-val"></span><span class="f-w"></span>
          </div>
          <div class="f-row has-rng ref" data-factor="cheap">
            <label class="chk"><input type="checkbox" class="f-chk"> 저가 월세 밀집 · 참고</label>
            <input type="range" class="f-rng"><span class="f-val"></span><span class="f-w"></span>
          </div>
        </div>
      </section>

      <section class="sec">
        <div class="sec-head"><span class="caret"></span>2. 재해 · 환경</div>
        <div class="sec-body">
          <div class="f-row has-rng" data-factor="flood">
            <label class="chk"><input type="checkbox" class="f-chk"> 침수흔적</label>
            <input type="range" class="f-rng"><span class="f-val"></span><span class="f-w"></span>
          </div>
          <div class="f-row off ref">
            <label class="chk"><input type="checkbox" disabled> 하천범람 (약하게)</label><span class="f-val">연동 예정</span>
          </div>
          <div class="f-row has-rng" data-factor="low">
            <label class="chk"><input type="checkbox" class="f-chk"> 저지대 (DEM)</label>
            <input type="range" class="f-rng"><span class="f-val"></span><span class="f-w"></span>
          </div>
          <div class="f-row off ref">
            <label class="chk"><input type="checkbox" disabled> 재해위험지구 · 참고</label><span class="f-val">연동 예정</span>
          </div>
          <div class="f-row off ref">
            <label class="chk"><input type="checkbox" disabled> 지표면 온도 · 참고</label><span class="f-val">연동 예정</span>
          </div>
        </div>
      </section>

      <section class="sec">
        <div class="sec-head"><span class="caret"></span>3. 에너지</div>
        <div class="sec-body">
          <div class="f-row off ref">
            <label class="chk"><input type="checkbox" disabled> 에너지 급감 (이상탐지 ML)</label><span class="f-val">연동 예정</span>
          </div>
          <div class="f-row off ref">
            <label class="chk"><input type="checkbox" disabled> 동별 전력 기준선 · 참고</label><span class="f-val">연동 예정</span>
          </div>
        </div>
      </section>

      <section class="sec collapsed">
        <div class="sec-head"><span class="caret"></span>4. 인구 · 가구 <span class="badge">우선순위</span></div>
        <div class="sec-body">
          <p class="sec-note">거주자 우선 점수(고위험고립 · 75세 이상 · 심한 장애 · 차상위·기초연금 · 건보료 체납 · 독박돌봄 · 지하·옥탑)는 취약 점수와 더하지 않고 <b>2순위</b>로 봅니다. 건물 상세 → 거주 가구 탭에서 확인.</p>
        </div>
      </section>

      <section class="sec collapsed">
        <div class="sec-head"><span class="caret"></span>5. 자원 · 접근성 <span class="badge">참고</span></div>
        <div class="sec-body">
          <p class="sec-note">가장 가까운 병원 · 복지시설 · 버스정류장까지 거리는 건물 상세에 표시합니다. 점수에는 넣지 않습니다.</p>
        </div>
      </section>

    </div>

    <div class="layer-foot">
      <button type="button" class="btn btn-line" id="btnWeights">가중치 조정</button>
      <button type="button" class="btn btn-primary" id="btnRun">▶ 분석 실행</button>
    </div>

    <%-- 가중치 조정 팝업 --%>
    <div class="weight-pop" id="weightPop">
      <div class="wp-head"><b>가중치 조정</b><button type="button" class="link-muted" id="weightReset">초기화</button><button type="button" class="wp-close" id="weightClose">×</button></div>
      <div class="wp-row"><span>지하층 · 고시원</span><b id="wv-a"></b><input type="range" id="w-a"></div>
      <div class="wp-row"><span>옥탑 · 위반건축물</span><b id="wv-b"></b><input type="range" id="w-b"></div>
      <div class="wp-row"><span>노후 · 침수</span><b id="wv-c"></b><input type="range" id="w-c"></div>
      <div class="wp-row"><span>저지대 · 저가</span><b id="wv-d"></b><input type="range" id="w-d"></div>
      <p class="wp-note">요인 점수 합계(100점 만점)가 <b>${criteria.scoreMin}점 이상</b>인 건물만 지도와 결과에 표시합니다.</p>
    </div>
  </aside>

  <button type="button" class="side-toggle" id="toggleLeft" aria-expanded="true" title="왼쪽 패널 접기">‹</button>

  <%-- ================= 가운데 : 지도 ================= --%>
  <main class="map-wrap">
    <div id="map"></div>

    <%-- 구 선택 : 구청 계정(loginMember.sggCd 있음)은 소속 구로 잠금, 서울시청은 자유 (CLAUDE.md 5-1 계정 2종류) --%>
    <c:set var="lockGu" value="${sessionScope.loginMember.sggCd}"/>
    <div class="map-top">
      <select class="field sel" id="selGu" ${not empty lockGu ? 'disabled title="소속 구로 고정됩니다"' : ''}>
        <c:if test="${empty lockGu}"><option value="">전체 구</option></c:if>
        <option value="11530" ${lockGu == '11530' ? 'selected' : ''}>구로구</option>
        <option value="11470" ${lockGu == '11470' ? 'selected' : ''}>양천구</option>
        <option value="11560" ${lockGu == '11560' ? 'selected' : ''}>영등포구</option>
      </select>
      <select class="field sel" id="selDong"><option value="">전체 동</option></select>

      <%-- 살피구로 #risk-only : 켜져 있으면 기준점 이상 건물이 있는 묶음(빨간 숫자)과 그런 건물 핀만 지도에 표시 --%>
      <button type="button" class="chip chip-toggle on" id="riskOnly" aria-pressed="true" title="누르면 모든 건물을 표시합니다">
        <span class="ck">✓</span><span><b>${criteria.scoreMin}</b>점 이상만 보기</span>
      </button>

      <div class="search">
        <input class="field" id="searchQ" placeholder="건물명 · 주소로 검색" autocomplete="off">
        <button type="button" class="search-btn" id="btnSearch" title="검색">⌕</button>
      </div>
    </div>

    <%-- 범례 : 모드(구/묶음/건물)에 따라 map-main.js 가 채운다 --%>
    <div class="map-legend" id="legend"></div>

    <div class="map-ctrl">
      <button type="button" id="zoomIn" title="확대">+</button>
      <button type="button" id="zoomOut" title="축소">−</button>
    </div>
  </main>

  <%-- ================= 오른쪽 : 결과 리스트 / 건물 상세 ================= --%>
  <aside class="right-panel" id="panelRight">

    <div class="rp-view" id="resultView">
      <div class="rp-head"><h2>검색 결과</h2><button type="button" class="rp-close" id="resultClose">×</button></div>
      <div class="rs-bar">
        <span class="rs-count" id="resultCount"><b>0</b> 건</span>
        <select class="field sel-sm" id="resultSort">
          <option value="score">취약 점수 높은 순</option>
          <option value="old">오래된 건물 순</option>
          <option value="addr">주소 순</option>
        </select>
      </div>
      <div class="rs-list" id="resultList"></div>
      <button type="button" class="rs-more" id="resultMore" style="display:none">목록 더 불러오기</button>
    </div>

    <div class="rp-view" id="detailView" style="display:none">
      <%@ include file="/WEB-INF/views/include/building-panel.jspf" %>
    </div>

  </aside>
</div>

<%-- 네이버 지도 JS API v3 (Client ID : app.properties app.naver.client-id) --%>
<script>
  window.navermap_authFailure = function () {
    document.getElementById('map').innerHTML = '<div class="map-error"><b>네이버 지도 인증 실패</b><p>Client ID 또는 Web 서비스 URL 등록을 확인하세요.</p></div>';
  };
  window.LOCK_GU = '<c:out value="${lockGu}"/>';   // 구청 계정이면 소속 구 코드, 서울시청이면 ''
  window.DONG_LIST = [
    <c:forEach var="d" items="${dongList}" varStatus="s">{ sggCd: '${d.sggCd}', emdNm: '<c:out value="${d.emdNm}"/>', lat: ${d.lat}, lng: ${d.lng}, cnt: ${d.cnt} }<c:if test="${!s.last}">,</c:if></c:forEach>
  ];
</script>
<script src="https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=<c:out value='${naverClientId}'/>"></script>
<script src="${pageContext.request.contextPath}/resources/js/common.js"></script>
<script src="${pageContext.request.contextPath}/resources/js/map/map-config.js"></script>
<script src="${pageContext.request.contextPath}/resources/js/map/map-api.js"></script>
<script src="${pageContext.request.contextPath}/resources/js/map/map-layer.js"></script>
<script src="${pageContext.request.contextPath}/resources/js/map/map-marker.js"></script>
<script src="${pageContext.request.contextPath}/resources/js/map/map-result.js"></script>
<script src="${pageContext.request.contextPath}/resources/js/map/map-panel.js"></script>
<script src="${pageContext.request.contextPath}/resources/js/map/map-main.js"></script>
</body>
</html>
