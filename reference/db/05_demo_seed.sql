-- =====================================================================
-- 시연용 데이터 (05 건물 확인 이력 · 보관함 · 현장확인)  04 다음에 실행
--   김구로(guro) 계정의 보관함 5동 + 현장확인 리스트 1개(3동)
--   1번 건물은 방문 2회(2025-07-18 박서울 / 2026-11-04 김구로) + 부서 이관 + 인수인계 메모 → 05 화면이 꽉 차게
--   다시 넣고 싶으면 맨 아래 "지우기" 블록을 먼저 실행
-- factor_check 키 = 점수 요인 키 : ugrnd 지하 · gosiwon 고시원 · rooftop 옥탑 · viol 위반 · old 노후 · flood 침수 · low 저지대 · cheap 저가
--   값 Y = 요인 맞음, N = 요인 아님, ? = 판단 어려움 / added = 현장에서 새로 발견한 것
-- =====================================================================
USE wellfair;
SET SQL_SAFE_UPDATES = 0;   -- Workbench 안전 모드(Error 1175) 끄기

SELECT user_id INTO @guro  FROM users WHERE login_id = 'guro';
SELECT user_id INTO @guro2 FROM users WHERE login_id = 'guro2';

-- 구로구에서 지하 거주 + 침수 이력 + 취약 가구 많은 건물 5동
DROP TEMPORARY TABLE IF EXISTS demo_bld;
CREATE TEMPORARY TABLE demo_bld (n INT AUTO_INCREMENT PRIMARY KEY, bld_id VARCHAR(28), score DECIMAL(6,2)) AS
SELECT NULL n, b.bld_id, NULL score
  FROM building b JOIN bld_resident_stat s ON s.bld_id = b.bld_id
 WHERE b.sgg_cd = '11530' AND b.ugrnd_res = 1 AND b.flood_cnt > 0
   AND s.hh_cnt BETWEEN 4 AND 30                 -- 아파트 단지 말고 다세대·다가구 규모로
 ORDER BY s.vuln_hh_cnt DESC, b.bld_id
 LIMIT 5;
UPDATE demo_bld SET score = ELT(n, 82, 78, 74, 71, 66);

-- 보관함 (분석 1회)
INSERT INTO vulnerability_list (user_id, sgg_cd, title, criteria, analyzed_at)
VALUES (@guro, '11530', '구로구 · 평상시 · 지하·침수 중심', '{"scenario":"normal","scoreMin":60}', '2026-11-03 09:10:00');
SET @vl = LAST_INSERT_ID();
INSERT INTO vulnerability_factor (vulnerability_id, bld_id, is_applicable, vulnerability_score, vulnerability_grade, evaluated_at)
SELECT @vl, bld_id, 1, score, IF(score >= 80, '80점 이상', '60~79점'), '2026-11-03 09:10:00' FROM demo_bld;

-- 현장확인 리스트 (1~3번 건물)
INSERT INTO survey_list (user_id, sgg_cd, title, move_mode, created_at) VALUES (@guro, '11530', '구로2동 침수 취약', 'WALK', '2026-11-03 10:00:00');
SET @sl = LAST_INSERT_ID();
INSERT INTO survey_plan (survey_id, factor_id, plan_date, visit_order)
SELECT @sl, f.factor_id, '2026-11-04', d.n
  FROM demo_bld d JOIN vulnerability_factor f ON f.vulnerability_id = @vl AND f.bld_id = d.bld_id
 WHERE d.n <= 3;

SELECT d.bld_id, p.plan_id INTO @b1, @p1
  FROM demo_bld d JOIN vulnerability_factor f ON f.vulnerability_id = @vl AND f.bld_id = d.bld_id
  JOIN survey_plan p ON p.factor_id = f.factor_id WHERE d.n = 1;

-- 1번 건물 : 예전 방문 (박서울, 2025-07-18)
INSERT INTO visit_history (user_id, bld_id, visited_at, result) VALUES (@guro2, @b1, '2025-07-18 14:00:00', 'MET');
INSERT INTO survey_record (plan_id, visit_id, content, factor_check, score_adj, created_at)
VALUES (@p1, LAST_INSERT_ID(), '외관상 특이사항 적음. 우편함 6개.',
        '{"ugrnd":"Y","flood":"?"}', NULL, '2025-07-18 14:30:00');

-- 1번 건물 : 최근 방문 (김구로, 2026-11-04)
INSERT INTO visit_history (user_id, bld_id, visited_at, result) VALUES (@guro, @b1, '2026-11-04 10:20:00', 'MET');
INSERT INTO survey_record (plan_id, visit_id, content, factor_check, score_adj, created_at)
VALUES (@p1, LAST_INSERT_ID(), '반지하 출입구가 도로보다 낮고 배수구가 막혀 있음.',
        '{"ugrnd":"Y","flood":"Y","old":"Y","rooftop":"N","added":["반지하 창문 2곳","우편함 8개"]}', 2, '2026-11-04 10:40:00');
SET @r2 = LAST_INSERT_ID();

-- 부서 이관 + 인수인계
INSERT INTO transfer_log (bld_id, record_id, dept_nm, req_type, status, user_id, created_at)
VALUES (@b1, @r2, '주택과', '점검 요청', 'SENT', @guro, '2026-11-04 11:00:00');
INSERT INTO handover_memo (bld_id, from_user_id, to_user_id, memo, created_at)
VALUES (@b1, @guro2, @guro, '배수구 막힘은 주택과에 사진과 함께 전달했습니다.', '2026-11-04 11:02:00');

-- 2번 건물 : 방문했지만 부재 (기록은 남김 → 진행완료로 셈)
SELECT d.bld_id, p.plan_id INTO @b2, @p2
  FROM demo_bld d JOIN vulnerability_factor f ON f.vulnerability_id = @vl AND f.bld_id = d.bld_id
  JOIN survey_plan p ON p.factor_id = f.factor_id WHERE d.n = 2;
INSERT INTO visit_history (user_id, bld_id, visited_at, result) VALUES (@guro, @b2, '2026-11-04 11:30:00', 'ABSENT');
INSERT INTO survey_record (plan_id, visit_id, content, factor_check, created_at)
VALUES (@p2, LAST_INSERT_ID(), '부재. 우편함에 안내문 남김. 재방문 필요.', '{"ugrnd":"Y","flood":"?"}', '2026-11-04 11:35:00');

DROP TEMPORARY TABLE demo_bld;

-- 확인 : 보관함 5 / 리스트 3 / 기록 3
SELECT (SELECT COUNT(*) FROM vulnerability_factor WHERE vulnerability_id = @vl) archive_cnt,
       (SELECT COUNT(*) FROM survey_plan WHERE survey_id = @sl) plan_cnt,
       (SELECT COUNT(*) FROM survey_record) record_cnt, @b1 demo_building;

/* ---------- 지우기 (다시 넣을 때) ----------
DELETE FROM handover_memo; DELETE FROM transfer_log; DELETE FROM survey_record; DELETE FROM visit_history;
DELETE FROM survey_plan; DELETE FROM survey_list; DELETE FROM vulnerability_factor; DELETE FROM vulnerability_list;
*/
