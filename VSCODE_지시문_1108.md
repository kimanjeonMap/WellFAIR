# VS Code Claude 지시문 (2026-11-08 수정 묶음)

## 시작 전에 (직접 할 것)
1. `C:\final_data\jusawi_handoff\CLAUDE.md` 를 wellFAIR 프로젝트 루트의 CLAUDE.md 에 덮어쓰기
2. `C:\final_data\jusawi_handoff\reference\db\` 폴더를 wellFAIR 의 `reference\db\` 에 덮어쓰기
3. MySQL Workbench 에서 **03 → 04 → 05** 순서로 실행 (01·02는 DB를 처음부터 다시 만들 때만)
   - 04는 한 번만. 05 끝의 확인 결과가 `보관함 5 / 리스트 3 / 기록 3` 이면 성공
   - 03을 이미 돌렸다면 04부터
4. 아래 프롬프트를 **1번부터 하나씩** 붙여 넣고, 하나 끝날 때마다 Eclipse에서 서버 재시작 후 화면 확인

---

## 1. 계정·헤더·구 잠금
```
CLAUDE.md 를 처음부터 다시 읽고, 특히 4번 표의 users 와 "## 5-1. 2026-11-08 변경" 의 "계정 2종류" 를 기준으로 고쳐 줘.
DB는 reference/db/04_update.sql 까지 실행된 상태야 (users 에 sgg_cd, user_nm, dept_nm, rank_nm 추가됨).

1) MemberTO 에 sggCd, userNm, deptNm, rankNm 필드 추가. 지금 dept 기본값 "서울시청" 같은 하드코딩은 지워.
2) mapper-member.xml 의 getMemberByLoginId 가 새 컬럼 4개를 같이 읽게.
3) header.jspf 오른쪽 사용자 표시를 "{deptNm} · {userNm} {rankNm}" 로 (예: 구로구청 복지정책과 · 김구로 주무관).
4) 구청 계정(sggCd 있음)은 메인 지도 왼쪽 패널의 구 선택칸을 소속 구로 고정하고 disabled. 서울시청(sggCd 없음)은 지금처럼 자유.
   지도 첫 화면도 구청 계정이면 그 구로 이동.
5) 보관함·현장확인·대시보드도 구청 계정이면 소속 구 데이터만 보이게 (서비스에서 loginMember.sggCd 로 거르기).

끝나면 guro/1234, admin/1234 로 각각 로그인했을 때 무엇이 다르게 보이는지 정리해 줘.
```

## 2. 동 2단계 선택 + 결과 리스트 범위 + [이 지역에서 재검색]
```
CLAUDE.md 5-1 의 "동 선택 = 2단계" 와 "오른쪽 검색 결과 리스트 범위" 대로 고쳐 줘. 피그마 PNG 02 "동 선택 열림" 화면을 참고해.
building 테이블에 adm_cd, adm_nm (행정동) 이 03_adm_dong.sql 로 들어가 있어.

1) 동 선택: 법정동(emd_nm) 목록 → 하나 고르면 그 아래 행정동(adm_nm) 목록. 법정동만 골라도 됨.
   목록은 MapRESTController 에 api/map/dongs.do?gu= 같은 요청 하나로 (법정동-행정동 쌍을 DISTINCT 로).
2) results.do 는 gu / dong(법정동) / adm(행정동) / bbox 파라미터를 받는다. 기본은 구 전체.
   동을 고르면 그 동만 + 지도를 그 동 건물들의 범위로 이동.
3) 지도를 옮겨도 리스트는 그대로. idle 이벤트에서 "지도 위에 [이 지역에서 재검색] 버튼 보이기"만 하고,
   버튼을 누르면 그때 bbox 로 results.do 를 다시 부른다. 동을 다시 고르면 bbox 범위는 풀린다.
4) 리스트 머리에 지금 범위 문구: "구로구 · 구로동 · 구로2동" 또는 "지도 화면 안".
5) 지도 도넛·핀(clusters.do, buildings.do)은 지금처럼 항상 화면 bbox 기준. 건드리지 마.
```

## 3. 동점 순서·옥탑·가구 가점
```
CLAUDE.md 5-1 의 "동점 순서" 와 "가구 가점", 4번의 "옥탑" 줄대로 고쳐 줘. bld_resident_stat 테이블이 04에서 만들어져 있어.

1) mapper-building.xml 의 getResultList 정렬이 지금 score DESC, bld_age DESC 인데,
   bld_resident_stat 를 LEFT JOIN 해서 score DESC → vuln_hh_cnt DESC → priority_sum DESC → bld_id 로 바꿔.
   보관함 "우선순위 순", 현장확인 담기 후보 정렬도 같은 규칙으로.
2) 옥탑 요인 판정을 residency 를 매번 훑는 대신 bld_resident_stat.rooftop_hh_cnt > 0 로.
3) mapper-resident.xml 의 priorityExpr 가중치를 04_update.sql [D] 와 똑같이:
   고위험고립 30, 사회적고립 15, 75세 이상 15, 심한 장애 15, 사각지대(blind_spot_yn) 15, 건보료 체납 10,
   독박돌봄 10, 지하·옥탑층 10, 기초생활/차상위 5, 기초연금 3. 취약 가구 = 10점 이상.
   (지금은 수급·차상위 +10, 기초연금 +5 이고 사각지대가 없음)
4) 리스트 각 행에 "취약 가구 N" 을 작게 보여줘 (vuln_hh_cnt).

확인: 구로구에서 같은 점수인 가리봉동 127-15(취약 가구 9) 가 구로동 554-19(1) 보다 위에 나와야 해.
```

## 4. 복지 연계 칸 = 원본 값
```
CLAUDE.md 5-1 의 "취약 가구 정보 모달의 복지 연계 칸" 대로 고쳐 줘.
map-panel.js 의 welfare() 가 체납이나 고위험고립이면 "상담 필요" 를 만들어 내는데, 이런 임의 규칙은 지워.

- 가구원 중 support_type(공적급여/민간자원/기타서비스) 이 있으면 "{값} 연계"
- 없고 detect_result 가 있으면 그 값 그대로
- 둘 다 없으면 "이력 없음"
- 기초생활·차상위·기초연금은 "취약 유형" 태그 쪽에 "수급 확인용" 으로.
필요하면 residents.do 응답(ResidentTO)에 supportType, detectResult 를 가구 단위로 모아서 넣어 (mapper-resident 에서 GROUP_CONCAT DISTINCT).
```

## 5. 현장확인: 이동수단 저장 + 한 구만
```
CLAUDE.md 5-1 의 "현장확인 이동수단" 과 "계정 2종류" 의 현장확인 줄대로 고쳐 줘. survey_list 에 sgg_cd, move_mode(WALK/TRANSIT/CAR) 가 추가돼 있어.

1) 03-1(fieldedit.jsp)에서 고른 이동수단을 저장하면 survey_list.move_mode 로 들어가게.
2) 03-2(fielddetail.jsp)는 지금 도보에 class="on" 이 박혀 있고 클릭이 안 돼.
   저장된 move_mode 에 on 표시, 세 칸 다 클릭 가능, 누르면 api 로 move_mode 저장 후
   총 소요·예상 종료 시간을 그 수단 기준으로 다시 계산 (도보 4, 대중교통 15, 차량 20 km/h, 거리 × 1.3).
3) 리스트를 새로 만들 때 처음 담는 건물의 구로 survey_list.sgg_cd 를 정하고,
   다른 구 건물을 담으려 하면 서버에서 막고 "한 리스트에는 한 구의 건물만 담을 수 있어요" 안내.
4) 최적 동선 출발점 기본값 = 그 리스트 구의 구청 좌표 (CLAUDE.md '최적 동선' 참고).
```

## 6. 05 건물 확인 이력 페이지 (보관함 상세)
```
CLAUDE.md 3번 표의 archivedetail.do / archivecheck.do 와 5-1 의 "05 건물 확인 이력" 대로 새 페이지를 만들어 줘.
피그마 PNG 05 "건물 확인 이력" 을 그대로 따라 하고, 빼는 칸 없이 다 만들어. 다크 테마는 기존 CSS 변수 그대로.

1) 지금 archive.jsp 안의 <aside class="right-panel archive-panel"> + building-panel.jspf 는 지워.
   (map.css 의 .right-panel 이 margin-right:-400px 라 화면 왼쪽 아래로 떨어지는 버그가 있음)
2) ArchiveController.detail: GET archivedetail.do?bldId= → archivedetail.jsp
   - 상단: 건물명·주소·용도·층, 취약도 배지, 현장 확인됨 배지, 담당자(최근 방문자 user_nm)·최근 확인일·확인 횟수·이관 부서·점수 보정(저장 점수 → +score_adj)
   - 버튼: [지도에서 보기](map.do?bldId=) [재확인 요청](현장확인 리스트에 담기) [부서로 넘기기](transfer_log INSERT)
   - 타임라인: survey_record + visit_history 최신순. 사진 3칸 빈 상자(연동 예정), 사전 탐지 요인, 현장 체크(factor_check JSON 의 Y=맞음 N=아님 ?=판단 어려움, added=새로 발견), 관찰 메모, 이관 기록. [전체][요인 맞음][요인 아님] 필터는 JS.
   - 오른쪽: 부서 이관 기록(transfer_log, 단계 표시), 분석 반영(점수 보정과 이유), 인수인계 메모(handover_memo, +추가).
   factor_check 키: ugrnd 지하 · gosiwon 고시원 · rooftop 옥탑 · viol 위반 · old 노후 · flood 침수 · low 저지대 · cheap 저가
3) 타임라인 맨 위 [현장 확인 결과 입력] 폼 → POST archivecheck.do
   방문 일시, 방문 결과(MET/ABSENT/REFUSED/OTHER), 사전 탐지 요인마다 맞음/아님/판단 어려움 라디오,
   새로 발견한 요인(+ 버튼으로 여러 개), 관찰 메모, 점수 보정(-10~+10).
   저장 = visit_history INSERT + survey_record INSERT(plan_id 는 그 건물의 survey_plan, factor_check 는 JSON 문자열로) → redirect:archivedetail.do
   그 건물이 어떤 현장확인 리스트에도 없으면 저장 막고 "현장확인 리스트에 먼저 담아 주세요".
4) 링크: 보관함 행 클릭 → archivedetail.do?bldId=, 03-2 표의 건물 주소 클릭 → 같은 곳.
5) 새 DAO/mapper 는 mapper-archive.xml 에 추가 (전체 namespace 로 부르는 myabweb 방식 그대로).

확인: guro/1234 로그인 → 보관함 → 1번 건물(05_demo_seed 의 첫 건물) 을 열면 방문 2회, 주택과 이관, 인수인계 메모가 보여야 해.
```

## 7. 보관함 [전체] 탭 + 상세 필터
```
CLAUDE.md 5-1 의 "보관함" 대로 고쳐 줘.
1) 탭 순서: 전체 / 신규 / 진행중 / 완료됨. 기본 탭 = 전체.
2) 목록 위에 [상세 필터] 버튼 → 펼침칸:
   취약 요인 다중 선택(지하·옥탑·고시원·위반·노후·침수·저지대) / 점수 구간(80+, 60~79, 40~59) / 건물 용도 /
   법정동·행정동 / 거주자 조건(고위험고립 있음, 75세 이상 있음, 심한 장애 있음 → bld_resident_stat 로 거름) /
   방문 결과(만남·부재·거부) / 저장일 기간.
   GET archive.do 파라미터로 넘기고 mapper-archive 에서 <if> 로 거름. 페이지 이동해도 필터 유지.
```
