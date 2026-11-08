# 주사위(WellFAIR) 중간점검 웹 — Claude 작업 지침

이 파일은 claude.ai 프로젝트 "다예 최종 프로젝트"에서 정한 내용을 VS Code의 Claude Code에 넘기려고 만든 인계 문서다.
코드를 쓰기 전에 끝까지 읽고, 여기 적힌 결정을 바꾸지 말 것. 바꿔야 하면 먼저 다예에게 묻는다.

- 목표: 월요일 중간점검용 웹 (앱은 만들지 않음)
- 화면: 로그인 / 메인(분석 지도) / 현장확인 리스트 / 보관함 / 대시보드(분석 리포트)
- 전체 파일 지도(80개, 화면별 흐름): `reference/spring-mvc-file-map.html` 을 브라우저로 열어 볼 것

---

## 1. 무엇을 따라 하나 (우선순위 순)

| 대상 | 따라 할 곳 |
|---|---|
| 파일 나누기, 패키지 구조, 네이밍, 코드 문법 | `reference/myabweb` (myabweb.zip) — **무조건 이 방식** |
| 화면 구성·디자인(다크) | Figma: https://www.figma.com/design/6WQBZQwvORchI1kAh07SHR/WellFAIR?node-id=113-1035 |
| 지도 영역만 | Figma 무시. `reference/salpiGuro` (살피구로) 지도 동작 그대로 + 네이버 지도 다크 |

### myabweb 방식 (반드시 지킬 것)
- Spring MVC 6.1.9, jakarta, JSP + JSTL(`<%@ taglib prefix="c" uri="jakarta.tags.core" %>`), jQuery 3.7.1, MyBatis + `SqlSession`, MySQL, Lombok. **pom.xml은 myabweb 것을 그대로 쓰고 라이브러리 추가 금지** (Spring Boot, JPA, Spring Security, Thymeleaf, React 금지).
- 설정 파일: `src/main/resources/root-context.xml`, `servlet-context.xml`, `mapper-*.xml`, `logback.xml`, `src/main/webapp/WEB-INF/web.xml` (myabweb 위치 그대로).
- 패키지: `lx.edu.jusawi` (예시, 다예가 정한 이름이 있으면 그걸로) 아래 `controller / service / dao / to / interceptor / common`.
- 주소는 전부 `xxx.do`. 페이지는 `@Controller`가 JSP 이름을 리턴, 지도처럼 데이터만 주는 건 `@RestController`가 JSON 리턴 (myabweb의 `EmpController` / `EmpRESTController` 패턴). JSON 주소는 `api/...do`.
- View: `/WEB-INF/views/*.jsp`, 정적 파일: `/resources/css`, `/resources/js`.
- TO: `@Data`(Lombok) 클래스, 필드는 camelCase. `mapUnderscoreToCamelCase=true`라 SQL 컬럼은 snake_case 그대로.
- DAO: `@Component` + `@Autowired SqlSession session;` 메서드 한 줄씩.
- 로그인 검사: myabweb `LoginInterceptor` 그대로 (세션 키만 `loginMember`).

### myabweb과 다르게 할 것 (유지보수 약속)
1. **service 계층 추가.** Controller는 DAO를 직접 부르지 않는다. Controller → Service → DAO → mapper.
2. **DAO는 자기 Service만 부른다.** (FieldDAO는 FieldService만. 다른 Service가 필요하면 Service → Service.)
3. **mapper id는 namespace까지**: `session.selectList("mapper-field.getItemList", listId)` (myabweb처럼 id만 쓰면 mapper 5개에서 충돌).
4. **이름 4칸 통일**: `MapRESTController.results()` → `MapService.getResultPage()` → `BuildingDAO.getResultList()` → `mapper-building.xml #getResultList`. DAO 메서드명 = mapper id.
5. **진행 상태는 저장하지 않고 SQL로 계산**: `mapper-archive.xml`의 `<sql id="statusExpr">` 한 곳. 보관함의 건물(vulnerability_factor) 기준으로 survey_record 있음 → DONE, survey_plan에 담김 → DOING, 둘 다 없음 → TODO. 라벨은 `common/WorkStatus.java`(TODO 진행해야 하는·신규 / DOING 진행중 / DONE 진행완료)에만.
6. **취약 점수 SQL은 한 곳**: `mapper-building.xml`의 `<sql id="scoreExpr">`. 도넛·마커·결과·상세·대시보드가 include (`<include refid="mapper-building.scoreExpr"/>`).
7. **거주자 데이터는 `mapper-resident.xml`에서만 읽는다.** 개인 가점 `<sql id="priorityExpr">`도 여기 한 곳.

---

## 2. 지도 (가장 중요) — 살피구로 `reference/salpiGuro/src/main/webapp/js/main.js`, `config.js`, `AnalysisService.clusters()` 를 그대로 옮긴다

- **네이버 지도 JS API v3**, 다크 스타일. Client ID는 app.properties `app.naver.client-id` 에 두고 MapController가 model로 JSP에 넘김. 네이버 클라우드 콘솔 Web 서비스 URL에 `http://localhost:8080` 등록.
- Figma의 지도 안 요소(핫스팟 점선, 숫자 핀 디자인 등)는 쓰지 않는다. 지도 바깥(왼쪽 레이어 패널, 가중치 팝업, 상단 구·동 선택·검색, 범례, 오른쪽 패널)은 Figma대로.

### 2-1. 줌에 따라 3가지 모드 (살피구로 `modeOf()`)
| 모드 | 살피구로(카카오 level) | **네이버 zoom** | 지도에 그리는 것 | 서버 요청 |
|---|---|---|---|---|
| gu | 6 이상 | **14 이하** | 구 경계 + 구 라벨만. **건물·묶음 없음** | 없음 (요약 숫자만) |
| cluster | 3 ~ 5 | **15 ~ 17** | **격자 도넛 묶음**: 화면 `CLUSTER_PX = 120`px 칸마다 건물을 묶어 도넛 1개. 도넛 둘레 = 점수 구간별 비율, 가운데 숫자 = 건물 수, 오른쪽 위 빨간 숫자 = 기준점 이상 건물 수. 칸에 건물이 1동이면 핀 | `GET api/map/clusters.do?bbox=&cell=&기준값` |
| building | 1 ~ 2 | **18 이상** | 건물 **핀**(살피구로 `pointSvg()` 물방울 모양, 점수 구간 색). 점수 낮은 건물은 작게. 한 화면 최대 `BUILDING_LIMIT = 1500`동, 점수 높은 순 | `GET api/map/buildings.do?bbox=&limit=1500&기준값` |
- 카카오 level L ≈ 네이버 zoom (20 − L). 시작 zoom 13(카카오 7), 동 선택 시 16, 건물 선택 시 18.
- **"도넛"은 동(행정동)마다가 아니라 화면 격자 칸마다**다. (예전 설계의 "동 도넛 / DongDonutTO / dongs.do" 는 틀렸음 → `ClusterTO` / `clusters.do` 로 바꾼다.)
- 숫자가 적힌 동그라미 마커(점수 숫자)는 쓰지 않는다. 점수는 색으로만, 숫자는 툴팁·오른쪽 패널에서.

### 2-2. 다시 그리는 방식 (속도의 핵심)
- 네이버 `zoom_changed` → 모드 바꾸고 범례 갱신(살피구로 `onLevelChanged()`), `idle` → **200ms 디바운스** 후 `loadPoints()` (살피구로 `scheduleLoad()`).
- 요청 bbox = 화면 범위 + 여유(cluster 0.002°, building 0.0005°). **화면 밖 건물은 절대 요청하지 않는다.**
- `cell`(경도 도 단위) = `CLUSTER_PX × (화면 경도폭) / 지도 px폭`, zoom마다 한 번 계산해 캐시(`cellByLevel`).
- 마커는 **키로 비교해서 바뀐 것만 추가·삭제**(살피구로 `setMarks()`), 전부 지우고 다시 그리지 않는다. 요청 순번(`pointSeq`)으로 늦게 온 옛 응답은 버린다.
- 도넛 SVG = 살피구로 `clusterSvg()`/`clusterRadius()` 그대로, 핀 = `pointSvg()`/`drawPin()` 그대로. 카카오 `CustomOverlay` → `naver.maps.Marker({ position, icon: { content: el, anchor } })`.

### 2-3. 서버 쪽 (mapper-building.xml)
- **#getClusterList**: 격자 집계를 SQL로. bbox로 먼저 거르고(`lng BETWEEN … AND lat BETWEEN …`, idx_building_coords 사용) `GROUP BY FLOOR(lng/#{cell}), FLOOR(lat/#{cellLat})`, 칸마다 `COUNT(*) size, AVG(lng), AVG(lat), MIN/MAX(lng/lat)`, 점수 구간별 건수(`SUM(CASE WHEN score>=80 …)` 4개 = hist), 기준점 이상 건수(riskCount). `cellLat = cell × cos(37.5°)`. 살피구로 `AnalysisService.clusters()`와 같은 결과.
- **#getMarkerList**: bbox 안 건물, `ORDER BY score DESC LIMIT #{limit}`.
- 점수는 `<include refid="mapper-building.scoreExpr"/>` 한 곳. 옥탑 판정(residency 옥탑층)은 서브쿼리 대신 **건물별 옥탑 여부를 미리 구한 JOIN**(또는 앱 시작 시 한 번 계산)으로, 매 요청마다 100만 명 테이블을 훑지 않게.
- 점수 구간(범례·도넛 색): 80 이상 red `#D93A3A` / 60~79 orange `#EC8B2F` / 40~59 yellow `#E2B52A` / 40 미만 green `#3E9E5A`. 기준점(기본 60) 이상만 "위험"으로 셈.

### 2-4. JS 파일 나누기
살피구로 `main.js`(933줄)를 역할별로: `js/map/map-config.js`(위 숫자들), `map-api.js`(서버 호출은 여기만), `map-layer.js`(왼쪽 패널→조건), `map-marker.js`(clusterSvg·pointSvg·setMarks·모드 전환), `map-result.js`(오른쪽 결과 리스트), `map-panel.js`(건물 상세·탭), `map-main.js`(시작점·이벤트). 로딩 순서 config → api → 나머지 → main.

---

## 3. 화면·요청 목록 (Controller 기준)

| 화면 | 주소 | Controller.메서드 | 결과 |
|---|---|---|---|
| 로그인 | GET loginform.do / POST login.do / GET logout.do | LoginController | login.jsp, 세션 loginMember → redirect:map.do |
| 메인 | GET map.do | MapController.map | map.jsp (상단 건수 칩 3개 = ArchiveService.getStatusCount → 누르면 dashboard.do?status=) |
| 〃 | GET api/map/checked.do | MapRESTController | 분석 전 현장 확인됨 핀 |
| 〃 | GET api/map/clusters.do?bbox&cell | 〃 | 격자 도넛 묶음 (ClusterTO, zoom 15~17) |
| 〃 | GET api/map/buildings.do?bbox&limit=1500 | 〃 | 건물 핀 (BuildingMarkerTO, zoom 18 이상) |
| 〃 | GET api/map/results.do?sort&page&q&gu&dong&adm (또는 &bbox) | 〃 | 오른쪽 결과 리스트 (PageTO). 범위 규칙은 5-1 참고 |
| 〃 | GET api/map/building.do?bldId | 〃 | 건물 상세 (BuildingTO + ScoreItemTO) |
| 〃 | GET api/map/residents.do?bldId | 〃 | "거주 가구" 탭 (ResidentTO, 가림 처리) |
| 〃 | GET api/map/resident.do?residentId | 〃 | "정보 확인하기" → 동·호수만 |
| 〃 | POST api/archive/add.do | ArchiveRESTController | 보관함 저장 |
| 보관함 | GET archive.do?status=&gu&dong&q&sort&page | ArchiveController.list | archive.jsp |
| 〃 | GET archivedetail.do?bldId | ArchiveController.detail | **archivedetail.jsp = Figma 05 건물 확인 이력** (보관함 행 클릭, 03-2 주소 클릭으로 들어옴). 예전의 archive.jsp 안 슬라이드 패널은 없앤다 |
| 〃 | POST archivecheck.do | ArchiveController.check | 05의 현장 체크 저장 → visit_history + survey_record(factor_check, score_adj) → redirect:archivedetail.do |
| 〃 | POST archivedelete.do | ArchiveController | redirect:archive.do |
| 현장확인 | GET fieldlist.do?tab= | FieldController.list | fieldlist.jsp |
| 〃 | GET/POST fieldedit.do | FieldController | fieldedit.jsp / redirect:fielddetail.do |
| 〃 | POST fieldadditems.do (listId=0이면 새 리스트) | FieldController | redirect:fieldedit.do |
| 〃 | GET fielddetail.do?listId | FieldController | fielddetail.jsp (경로·소요시간은 직선거리 기준, 아래 '최적 동선' 참고) |
| 〃 | POST fieldcheck.do | FieldController | 확인 완료 처리 (원래 앱 기능, 웹 버튼으로 대체) |
| 대시보드 | GET dashboard.do?gu&month&status | DashboardController | dashboard.jsp (Chart.js) |

공통 조각: `views/include/head.jspf`, `header.jspf`(상단 탭 4개, "{dept_nm} · {user_nm} {rank_nm}" 예: "구로구청 복지정책과 · 김구로 주무관", 로그아웃), `building-panel.jspf`(우측 패널: 건물 정보 / 거주 가구 탭).

---

## 4. 데이터와 DB

DB 이름 `wellfair` (MySQL 8). **`reference/db/` 의 01 → 02 → 03 → 04 → 05 순서로 Workbench에서 실행** (테스트 DB에서 전부 검증 완료). 테이블 정의는 그 파일들이 기준이다.
- 01 테이블 · 02 건물·더미 적재 · 03 행정동(building.adm_cd, adm_nm) · 04 계정·현장확인·05 화면용 테이블·건물별 거주자 요약 · 05 시연 데이터

| 테이블 | 한 줄 = | 화면에서 쓰는 곳 | 비고 |
|---|---|---|---|
| building | 건물 1동 | 지도 도넛·마커, 결과 리스트, 상세 | `3gu_bld_db.csv` 31컬럼 그대로, 72,961행. PK bld_id **VARCHAR(28)** |
| household | 가구 1개 | 거주 가구 탭 (가구 단위 정보) | 460,151. risk_grade, 기초생활·차상위·한부모·독박돌봄, 사각지대 |
| person | 가구원 1명 | 거주 가구 탭 카드, 우선순위 | 999,314. 나이·장애·기초연금 + **발굴 정보(crisis_info 등)는 사람 단위** |
| residency | 가구의 거주지 1곳 | "정보 확인하기" | 가구당 1줄. bld_id, res_flr, **dong_nm, ho_nm** (호수 없으면 NULL) |
| arrears | 가구의 체납 1종류 | 우선순위 가점 | 건보료 체납(가구원 중 한 명이라도 Y면 Y) |
| users | 로그인 사용자 | 로그인, 헤더, 구 고정 | user_id, login_id, password_hash, **sgg_cd(NULL = 서울시청), user_nm, dept_nm, rank_nm**. 계정(비번 모두 1234, 평문 비교): admin(서울시청) · guro(김구로, 구로구청) · yang(이양천) · ydp(최영등포) · guro2(박서울, 05 예전 담당자) |
| vulnerability_list | 분석 1회 | **보관함 목록** (개인) | user_id, sgg_cd, title, criteria(가중치·필터 JSON) |
| vulnerability_factor | 분석 1회 안의 건물 1동 | 보관함 행, 점수 스냅샷 | UNIQUE(vulnerability_id, bld_id). vulnerability_score(100점) |
| survey_list | 현장확인 리스트 1개 | 현장확인 리스트 목록 | user_id, **sgg_cd(한 리스트 = 한 구)**, title, **move_mode(WALK/TRANSIT/CAR)** |
| survey_plan | 리스트에 담긴 건물 1동 | 리스트 수정·상세, 방문 순서 | factor_id(→ 어느 분석의 어느 건물·몇 점), visit_order, plan_date |
| visit_history | 방문 1번 | 현장 확인 상세 | user_id, bld_id, result(MET/ABSENT/REFUSED/OTHER) |
| survey_record | 조사 기록 1건 | 진행완료 판정, 05 타임라인 | plan_id + visit_id + content(관찰 메모) + **factor_check**(JSON: 요인 키별 Y/N/?, added=새로 발견) + **score_adj**(점수 보정) |
| transfer_log | 부서 이관 1건 | 05 "부서 이관 기록" | bld_id, record_id, dept_nm, req_type, status(SENT/REPLIED) |
| handover_memo | 인수인계 메모 1건 | 05 "인수인계 메모" | bld_id, from_user_id, to_user_id, memo |
| bld_resident_stat | 건물 1동의 거주자 요약 | 동점 순서, 옥탑 판정, 인원 집계 | hh_cnt, person_cnt, **vuln_hh_cnt, priority_sum**, 요인별 인원(high_iso_cnt, old75_cnt, severe_cnt, blind_hh_cnt, rooftop_hh_cnt …). 더미가 고정이라 04에서 한 번 계산, 가중치와 무관 |

- **ID**: 더미 원본 mbr_id·hh_id가 구마다 1부터라서 적재 때 구로 +1,000,000 / 양천 +2,000,000 / 영등포 +3,000,000 을 더했다. 구 코드: 구로 11530, 양천 11470, 영등포 11560 (household.sgg_cd, building.sgg_cd).
- **화면 이름 ↔ 테이블**: 보관함 = vulnerability_list + vulnerability_factor / 현장확인 = survey_list + survey_plan (+ visit_history, survey_record) / 거주자(resident) = household + person + residency + arrears / 회원(member) = users. 클래스·mapper 이름(ArchiveDAO, mapper-archive, mapper-resident, MemberTO …)은 화면 기준 그대로 두고, 안의 SQL만 위 테이블을 쓴다.
- **세션 사용자 키는 user_id** (예전 설계의 emp_no 자리).
- **옥탑** = `bld_resident_stat.rooftop_hh_cnt > 0` (옥탑층 거주 가구가 있는 건물). residency를 매번 훑지 말고 이 테이블을 LEFT JOIN.
- 더미 원본 CSV: `C:\final_data\new_pop_dum_final\{guro,yangcheon,yeongdeungpo}_population_dummy.csv` (EUC-KR, 탭, 40컬럼, 1행 = 1명). 적재에서 뺀 컬럼: hh_head_nm(head_rel='본인'으로 찾음), addr·use_nm(building에 있음), orig_res_flr·match_how·move_how(더미 제작 기록).
- TO에서 person.mbr_id는 로그인 회원과 헷갈리니 `residentId`.

---

## 5. 정해진 규칙 (2026-11-07 결정)

- **취약 점수는 100점 만점으로 환산.** 요인: 지하층(ugrnd_res) +30, 옥탑·위반건축물 +20, 노후(useapr_day 30년+)·침수(flood_cnt) +15, 저지대(elev_m) +5, 고시원(sp_type='고시원'), 저가(price_pct). 기준값·가중치는 왼쪽 패널 슬라이더로 바뀜(AnalysisCriteriaTO). 기준점 60점 이상만 마커.
- **에너지 급감 요인은 뺀다.** **옥탑**은 건물 데이터에 없으므로 "res_flr='옥탑층' 거주자가 있는 건물"로 계산.
- **방문 우선순위**: 5-1의 "동점 순서"와 "가구 가점"을 따른다 (두 점수를 더하지 않음).
- **개인정보 노출**: 지도·리스트에는 건물별 인원 집계만("고위험고립 2명 · 75세 이상 3명"). 거주 가구 탭은 세대 카드에 가림 처리("3층 · 김○희 · 47****"). "정보 확인하기"를 누르면 **동·호수만**(예: 301동 502호, 호수 없으면 "호수 미확인 · 행복e음에서 확인"). 이름·생년월일 원문은 웹에 띄우지 않음(행복e음에서 조회).
- 보관함은 **개인 단위**(vulnerability_list.user_id). 공유는 최종 때.
- ~~로그인 부서는 서울시청 가정~~ → 5-1의 "계정 2종류"로 바뀜.
- 현장 확인 결과 입력·앱으로 보내기·부서로 넘기기·LLM 가이드·정확도 카드·xls·PDF는 **버튼만** 두고 "연동 예정".

---

## 5-1. 2026-11-08 변경 (5번과 다르면 이게 우선)

**계정 2종류 (페르소나)**
- 서울시청(users.sgg_cd = NULL): 3개 구를 같은 기준으로 분석 = "발굴". 구 선택 자유, 대시보드에서 구끼리 비교.
- 구청 주무관(sgg_cd 있음, 피그마 화면의 주인공 "구로구청 복지정책과 · 김구로 주무관"): **구 선택칸은 소속 구로 잠금**, 동만 고름. 실제 방문.
- 현장확인 리스트는 **한 구 안의 건물만** 담을 수 있다(survey_list.sgg_cd). 다른 구 건물을 담으려 하면 막고 안내.
- 발표 설명: "점수 기준은 3개 구에 똑같이 적용돼 구 경계 없이 찾아내고, 방문은 관할 구청이 한다. 3개 구는 확장성을 보여주기 위함."

**동 선택 = 2단계** (피그마 02 "동 선택 열림"): 법정동(building.emd_nm, 예: 구로동) → 행정동(building.adm_nm, 예: 구로1동~5동). 행정동은 03에서 좌표로 붙였다. 법정동만 고르면 그 법정동 전체.

**오른쪽 검색 결과 리스트 범위**
- 기본 = 구 전체(구청 계정은 소속 구, 시청은 고른 구. 시청이 "전체 구"면 3개 구). 동을 고르면 그 동만, 지도도 그 동으로 이동.
- 지도를 옮겨도 리스트는 **자동으로 바뀌지 않는다**. 이동이 멈추면 지도 위에 **[이 지역에서 재검색]** 버튼을 띄우고, 누르면 그때만 bbox 안 건물로 리스트를 다시 받는다.
- 리스트 머리에 지금 범위를 표시: "구로구 · 구로동 · 구로2동" / "지도 화면 안". 동을 다시 고르면 재검색 범위는 풀린다.
- 지도 핀·도넛은 지금처럼 항상 화면(bbox) 기준.

**동점 순서**: 취약 점수 DESC → `bld_resident_stat.vuln_hh_cnt` DESC → `priority_sum` DESC → bld_id. (결과 리스트·보관함 "우선순위 순"·현장확인 담기 후보 모두)

**가구 가점** (mapper-resident `priorityExpr` 와 04의 요약 계산이 같은 숫자여야 함)
고위험고립 30 · 사회적고립 15 · 75세 이상 가구원 15 · 심한 장애 가구원 15 · **사각지대(blind_spot_yn) 15** · 건보료 체납 10 · 독박돌봄 10 · 지하·옥탑층 거주 10 · **기초생활/차상위 5** · **기초연금 3**. 취약 가구 = 가점 10 이상.
(수급·차상위는 이미 복지 체계 안이라 가점을 낮추고, 체계 밖 사각지대에 가점)

**취약 가구 정보 모달의 "복지 연계" 칸 = 원본 값 그대로.** 임의 규칙("상담 필요" 등) 금지.
- 세대 안 가구원 중 지원연계(support_type: 공적급여/민간자원/기타서비스)가 있으면 "{값} 연계", 없고 처리결과(detect_result: 조치완료/단순상담/미처리/요청접수/미지원 …)가 있으면 그 값, 둘 다 없으면 "이력 없음".
- 기초생활·차상위·기초연금은 "취약 유형" 태그 쪽에 "수급 확인용"으로 표시.
- 표 한 줄 = 한 가구, 태그는 가구원 전체 요인을 합친 것 (지금 방식 유지).

**현장확인 이동수단**: 03-1에서 고른 이동수단을 survey_list.move_mode 로 저장. 03-2 소요시간 카드는 저장된 수단에 선택 표시, 세 칸 모두 눌러서 바꿀 수 있고(바꾸면 저장), 총 소요·예상 종료가 그 수단 기준으로 다시 계산. 대중교통 평균속도 15km/h.

**보관함**
- 탭: **전체** / 신규 / 진행중 / 완료됨 (전체가 맨 앞).
- 행 클릭 → `archivedetail.do?bldId=` (05 페이지). 03-2 표의 건물 주소 클릭도 같은 곳.
- [상세 필터] 펼침칸: 취약 요인 다중 선택(지하·옥탑·고시원·위반·노후·침수·저지대) / 점수 구간(80+, 60~79, 40~59) / 건물 용도 / 법정동·행정동 / 거주자 조건(고위험고립 있음, 75세 이상 있음, 심한 장애 있음 → bld_resident_stat로 거름, 사람 정보는 안 보임) / 방문 결과(만남·부재·거부) / 저장일 기간.

**05 건물 확인 이력 (= 보관함 상세, 피그마 05 그대로)**
- 상단: 건물명·주소·용도·층, 취약도 배지, 현장 확인됨 배지 / 담당자(최근 방문자) · 최근 확인일 · 확인 횟수 · 이관 부서 · 점수 보정(저장 점수 → 저장 점수 + score_adj) / [지도에서 보기](map.do?bldId=) [재확인 요청](현장확인 리스트에 담기) [부서로 넘기기](transfer_log INSERT).
- 확인 이력 타임라인: survey_record 최신순 한 칸씩. 사진 3칸은 빈 상자(연동 예정) / 사전 탐지 요인(그 건물 점수 요인) / **현장 체크**(factor_check: 맞음·아님·판단 어려움, added) / 관찰 메모(content) / 이관 기록. [전체][요인 맞음][요인 아님] 필터.
- 오른쪽: 부서 이관 기록(단계 표시), 분석 반영(점수 보정과 그 이유), 인수인계 메모(+추가, 담당자 변경).
- **현장 체크 입력 폼**(타임라인 맨 위 [현장 확인 결과 입력]): 방문 일시, 방문 결과(MET/ABSENT/REFUSED/OTHER), 사전 탐지 요인마다 맞음/아님/판단 어려움, 새로 발견한 요인(자유 입력 여러 개), 관찰 메모, 점수 보정(-10~+10). 저장 = visit_history + survey_record. 그 건물이 아직 현장확인 리스트에 없으면 "현장확인 리스트에 먼저 담아 주세요".
- 시연 데이터: 05_demo_seed.sql 이 김구로 계정에 보관함 5동, 리스트 1개, 1번 건물 방문 2회 + 이관 + 인수인계를 넣는다.

**네이버 Client ID**는 숫자 0이 들어간 `qttr0pq1es` 가 맞다(app.properties 그대로).

**가중치·분석 실행·도넛 (2026-11-08 추가, 8단계 반영)**
- 가중치 = 위험 유형 4묶음, 합계 100: **주거 공간**(지하층·옥탑·고시원) 45 / **건물 상태**(노후·위반건축물) 20 / **침수 위험**(침수 이력·저지대) 25 / **경제 여건**(저가 주택) 10. 가중치 팝업은 이 4줄(묶음 이름 크게, 요인은 작은 회색). 슬라이더 하나를 움직이면 나머지 묶음이 비율대로 줄거나 늘어 합 100 유지(5 단위, 끝수는 가장 큰 묶음에서 맞춤). 팝업 아래 "합계 100 / 100". 초기화 = 기본값.
- **점수 규칙 (10단계, 8단계 보완)**: 주거 공간(지하층·옥탑·고시원)은 셋 중 하나라도 맞으면 묶음 점수 전부(사는 곳의 종류라 대체 관계). 건물 상태(노후·위반건축물)와 침수 위험(침수 이력·저지대)은 **묶음 점수 × (맞은 요인 수 ÷ 켜진 요인 수)** — 둘 다 맞아야 만점, 하나만 맞으면 절반, 요인 하나를 끄면 남은 하나가 맞을 때 만점. 경제 여건(저가)은 맞으면 전부. 최종 점수는 ROUND(합) 정수. 예(45/20/25/10): 반지하 + 노후 + 저지대 = 45 + 10 + 12.5 = 68점, 반지하 + 노후 + 위반 + 침수 + 저지대 = 90점. `mapper-building.xml`: 요인 점수 `pt_*` = 맞으면 (주거·경제) 묶음 가중치 / (건물·침수) 묶음 가중치 ÷ 켜진 요인 수, `scoreExpr` = `ROUND(GREATEST(주거 3개) + 건물 2개 합 + 침수 2개 합 + 경제)`. `AnalysisCriteriaTO` 는 요인 8개 on/off·기준값은 그대로, 가중치는 `spaceW / bldW / floodW / econW` 4개(기본 45/20/25/10). 테스트 DB 기준 80점 이상 약 3,400동, 70점 이상 약 6,000동.
- 왼쪽 패널 요인 옆 숫자는 묶음 점수가 아니라 **그 요인 하나가 주는 점수**(주거 공간 요인 45, 건물 상태 요인 각 10, 침수 위험 요인 각 12.5→13, 저가 10). 점수 상세는 "+10 건물 상태 (노후 · 2개 중 1개)" 처럼 몇 개 중 몇 개인지 보이게.
- 왼쪽 패널 요인 목록도 같은 4묶음 제목 아래(주거 공간 / 건물 상태 / 침수 위험 / 경제 여건). 요인은 켜고 끄기만, 새 필드 추가 없음. 한 묶음의 요인이 전부 꺼지면 그 묶음 슬라이더는 0으로 잠기고 나머지 묶음끼리 합 100.
- 점수 상세(ScoreItemTO)는 묶음별 "+45 주거 공간 (반지하 · 옥탑)" / "+10 건물 상태 (노후 · 2개 중 1개)" 처럼 맞은 요인 이름을 괄호에.
- **분석 실행**: 왼쪽 패널·가중치를 바꿔도 지도·리스트는 그대로, 버튼만 "변경됨". [분석 실행]을 누르면 그때 조건을 확정해 도넛·핀·결과 리스트를 한 번에 다시 받는다. 확정 조건은 `state.criteria`(시작 때 패널 기본값을 한 번 찍어 둠)에 두고 이후 지도 이동·재검색·보관함 저장은 모두 이 값(보관함 저장 때 vulnerability_list.criteria 에 이 JSON 그대로).
- **기준점 기본 80**(app.properties `app.analysis.score-min`, map.jsp 가 `APP_SCORE_MIN` 으로 map-config `SCORE_MIN` 에 넘김, 체크박스 "80점 이상만 보기").
- **핀 모드(zoom 18+)**: 화면 안 점수 순(동점은 취약 가구 수 순) 상위 `PIN_ICON_MAX = 200` 개만 아이콘 핀, 나머지 기준점 이상 건물은 지름 8px 색 점(아이콘·라벨 없음, 눌러도 상세 열림).
- **도넛**: 위치 = 칸 안 "기준점 이상 건물들의 평균"(없으면 전체 평균, SQL). 그린 뒤 화면 픽셀 `MERGE_PX = 70` 안의 도넛끼리 하나로 합친다(큰 쪽에 숫자·구간 더하기, 위치는 건물 수 가중 평균, `map-marker.js mergeClusters`). "80점 이상만 보기"가 켜져 있으면 기준점 이상 건물이 0인 칸은 안 그린다. 표시는 8단계 이전 그대로(10단계에서 되돌림): **가운데 큰 숫자 = 칸 안 전체 건물 수, 오른쪽 위 빨간 배지 = 기준점 이상 건물 수**, 링 색 비율은 그대로.
- **현장 확인됨 (9단계)**: 줌과 상관없이 떠서 쌓이던 개별 핀을 없앴다. 구 단위 = 구 원 안에 "현장 확인 N곳" 한 줄(GuTO.checkedCnt). 묶음 = 칸에 확인 건물이 있으면 도넛 오른쪽 아래 파란 체크 배지 + 숫자(ClusterTO.checkedCnt, `getClusterList` 의 `SUM(checked)`). 핀 = 그 건물 핀에 파란 체크 배지(BuildingMarkerTO.checked), 점수 기준 아래라 핀이 없는 건물만 작은 파란 체크 점(api/map/checked.do, `MapMarker.refreshChecked`). 기간 필터 = 범례의 "현장 확인됨"을 누르면 [최근 3개월 / 1년 / 전체 / 끄기], 기본 최근 3개월(`AnalysisCriteriaTO.checkedMonths` 3 | 12 | 0 | -1, `mapper-building.checkedExists` 가 checked.do · checked_cnt · 핀 flag 에 같은 기간 조건). 구청 계정은 소속 구 확인 건물만(gu 강제).
- **구청 계정 관할 밖 (11단계)**: 구청 계정은 첫 gus.do 응답의 소속 구 ring 으로 "구멍 뚫린 큰 다각형"(fill #0b0d12 · 0.6, 타일 다크 필터 때문에 코드에는 반전색 #f4f2ed) 가림막 + 경계선(primary 2px)을 한 번 그린다. gus/clusters/buildings/checked/results 는 모두 서버가 sgg_cd 를 세션 값으로 강제(gu 파라미터 무시, gus.do 도 소속 구만 → 다른 두 구의 경계·원은 안 그림). idle 때 지도 중심이 ring 밖이면 "관할 구역(구로구) 밖입니다 [돌아가기]" 띠(재검색 버튼은 숨김), 돌아가기 = 구 bbox 로 fitBounds. 이동 범위 maxBounds = 구 bbox + 2km, 최소 줌 12(`ZOOM.LOCK_MIN`). 서울시청 계정은 그대로.
- **슬라이더 모양**: 기준값 슬라이더(.f-rng)와 가중치 슬라이더는 CSS 로 — 막대 4px 테두리 없음, 손잡이 왼쪽 primary · 오른쪽 어두운 회색(input 때 `--pct` 갱신 → linear-gradient), 손잡이 지름 14px 원, 테두리 없음, 포커스 outline 대신 손잡이 그림자.

---

### 최적 동선 (현장확인 03-1 수정 · 03-2 상세)
- 03-1에 **[최적 동선으로 정렬]** 버튼. 누르면 `fieldedit.js`가 담긴 건물들의 방문 순서를 다시 매기고, 저장하면 survey_plan.visit_order로 들어간다. 사용자가 끌어서 순서를 바꿀 수도 있음.
- 계산은 **브라우저 JS에서, 직선거리(하버사인) 기준**: 출발점에서 가장 가까운 건물부터 고르는 nearest-neighbor로 초안 → **2-opt**로 교차하는 구간을 풀어 개선. 건물 수십 동이면 즉시 끝나고 API 키가 필요 없다. 함수는 `field-map.js`의 `optimizeRoute(start, points)` 하나에 둔다.
- 출발점: 리스트 화면의 출발지 선택(기본 = 리스트의 구 구청 좌표. 구로구청 37.4954,126.8874 / 양천구청 37.5170,126.8664 / 영등포구청 37.5264,126.8962). 돌아오는 길은 계산하지 않음(편도).
- 03-2 지도: 순서대로 번호 핀 + 점선 연결. 소요시간 = 총 직선거리 × 1.3(도로 보정) ÷ 평균속도(도보 4km/h, 차량 20km/h).
- 실제 도로 경로(네이버 Directions 5)는 서버 키·경유지 15개 제한이 있어 **최종 때**. 중간점검 발표에서는 "직선거리 기반 최적화, 도로 경로는 연동 예정"으로 말한다.

---

## 6. 작업 순서

DB가 아직이면 **앞단 먼저**: JSP/CSS/JS를 위 파일 이름 그대로 만들고, 서버 대신 `resources/mock/*.json`(TO와 같은 모양)을 읽게 한다. `map-api.js`의 주소만 `mock/xxx.json` → `api/map/xxx.do`로 바꾸면 백엔드에 붙도록.

1. myabweb 복사 → 패키지명 변경 → Tomcat에서 뜨는지 확인
2. 로그인 + LoginInterceptor + header.jspf(탭 4개) + 다크 common.css
3. map.jsp + 네이버 다크 지도 → 건물 마커 → 도넛(줌 전환) → 결과 리스트 → 상세 패널 → 거주 가구 탭
4. 보관함 → 현장확인 리스트(목록/수정/상세) → 대시보드
5. DB가 되면(`reference/db/` SQL 2개 실행) mock을 하나씩 실제 API로 교체 (아래 계층부터: SQL을 Workbench에서 먼저 돌려 보고 mapper에 붙임)

## 7. 참고 파일 (이 폴더 기준)
- `reference/spring-mvc-file-map.html` — 80개 파일 전체 표, 화면별 흐름, 예시 시나리오
- `reference/myabweb/` , `reference/salpiGuro/` — 원본 소스
- `reference/design_WellFAIR/` — 화면별 피그마 PNG. 화면 만들 때 파일명 번호(01 로그인, 02 지도, 03 현장확인, 04 보관함, 05 건물 확인 이력, 06 대시보드)에 맞는 PNG를 열어서 그대로 구현. 지도 안쪽만 무시하고 살피구로 지도로.
- `reference/db/01_create_tables.sql`, `02_load_data.sql` — DB 만들기·적재 (테이블 정의 기준)
