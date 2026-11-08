-- =====================================================================
-- WellFAIR DB 변경 (2026-11-08)  01 → 02 → 03 다음에 실행. 여러 번 실행하지 말 것
--   [A] 계정 2종류 : users.sgg_cd 비어 있음 = 서울시청(3개 구 분석) / 있음 = 구청 주무관(소속 구 고정)
--   [B] 현장확인 리스트 : 구 하나로만 (sgg_cd) + 이동수단 저장 (move_mode)
--   [C] 05 건물 확인 이력 : 현장 체크(요인 맞음/아님/추가), 점수 보정, 부서 이관, 인수인계 메모
--   [D] 건물별 거주자 요약 (bld_resident_stat) : 동점 순서·옥탑 판정·인원 집계를 매번 100만 명에서 계산하지 않게
-- =====================================================================
USE wellfair;
SET SQL_SAFE_UPDATES = 0;   -- Workbench 안전 모드(Error 1175) 끄기

-- ---------------------------------------------------------------------
-- [A] 계정
-- ---------------------------------------------------------------------
ALTER TABLE users
  ADD COLUMN sgg_cd  CHAR(5)     NULL COMMENT '소속 구 (NULL = 서울시청, 3개 구 전체)' AFTER password_hash,
  ADD COLUMN user_nm VARCHAR(30) NULL COMMENT '이름' AFTER sgg_cd,
  ADD COLUMN dept_nm VARCHAR(50) NULL COMMENT '소속 (서울시청 복지정책과 / 구로구청 복지정책과 …)' AFTER user_nm,
  ADD COLUMN rank_nm VARCHAR(20) NULL COMMENT '직급' AFTER dept_nm;

UPDATE users SET user_nm = '관리자', dept_nm = '서울시청 복지정책과', rank_nm = '주무관' WHERE login_id = 'admin';

-- 시연 계정 (비밀번호 1234, 평문 비교)
INSERT INTO users (login_id, password_hash, sgg_cd, user_nm, dept_nm, rank_nm) VALUES
  ('guro',  '1234', '11530', '김구로',   '구로구청 복지정책과',   '주무관'),
  ('yang',  '1234', '11470', '이양천',   '양천구청 복지정책과',   '주무관'),
  ('ydp',   '1234', '11560', '최영등포', '영등포구청 복지정책과', '주무관'),
  ('guro2', '1234', '11530', '박서울',   '구로구청 복지정책과',   '주무관');   -- 05 이전 이력의 예전 담당자

-- ---------------------------------------------------------------------
-- [B] 현장확인 리스트
-- ---------------------------------------------------------------------
ALTER TABLE survey_list
  ADD COLUMN sgg_cd    CHAR(5) NULL COMMENT '리스트의 구 (한 리스트 = 한 구)' AFTER user_id,
  ADD COLUMN move_mode ENUM('WALK','TRANSIT','CAR') NOT NULL DEFAULT 'WALK' COMMENT '이동수단' AFTER title;

-- ---------------------------------------------------------------------
-- [C] 05 건물 확인 이력
-- ---------------------------------------------------------------------
ALTER TABLE survey_record
  ADD COLUMN factor_check VARCHAR(1000) NULL COMMENT '현장 체크 JSON {"ugrnd":"Y","rooftop":"N",…,"added":["옥탑 증축"]}' AFTER content,
  ADD COLUMN score_adj    TINYINT       NULL COMMENT '현장 확인 점수 보정 (+2 등)' AFTER factor_check;

-- 부서 이관 기록 (오른쪽 "부서 이관 기록" 단계)
DROP TABLE IF EXISTS transfer_log;
CREATE TABLE transfer_log (
  transfer_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  bld_id       VARCHAR(28)     NOT NULL COMMENT '건물',
  record_id    BIGINT UNSIGNED NULL     COMMENT '어느 현장 기록에서 넘겼나',
  dept_nm      VARCHAR(50)     NOT NULL COMMENT '넘긴 부서 (주택과 등)',
  req_type     VARCHAR(30)     NOT NULL COMMENT '점검 요청 / 조회 요청',
  status       ENUM('SENT','REPLIED') NOT NULL DEFAULT 'SENT' COMMENT '전달 / 회신',
  user_id      BIGINT UNSIGNED NOT NULL COMMENT '넘긴 사람',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (transfer_id),
  KEY idx_transfer_bld (bld_id),
  CONSTRAINT fk_transfer_bld    FOREIGN KEY (bld_id)    REFERENCES building (bld_id),
  CONSTRAINT fk_transfer_record FOREIGN KEY (record_id) REFERENCES survey_record (record_id),
  CONSTRAINT fk_transfer_user   FOREIGN KEY (user_id)   REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='부서 이관';

-- 인수인계 메모 (담당자 변경)
DROP TABLE IF EXISTS handover_memo;
CREATE TABLE handover_memo (
  memo_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  bld_id        VARCHAR(28)     NOT NULL,
  from_user_id  BIGINT UNSIGNED NOT NULL,
  to_user_id    BIGINT UNSIGNED NULL,
  memo          VARCHAR(500)    NOT NULL,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (memo_id),
  KEY idx_handover_bld (bld_id),
  CONSTRAINT fk_handover_bld  FOREIGN KEY (bld_id)       REFERENCES building (bld_id),
  CONSTRAINT fk_handover_from FOREIGN KEY (from_user_id) REFERENCES users (user_id),
  CONSTRAINT fk_handover_to   FOREIGN KEY (to_user_id)   REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='인수인계 메모';

-- ---------------------------------------------------------------------
-- [D] 건물별 거주자 요약  (더미는 고정값이라 한 번만 계산. 가중치를 바꿔도 안 바뀜)
--   가구 가점 : 고위험고립 30 · 사회적고립 15 · 75세 이상 15 · 심한 장애 15 · 사각지대 15
--              건보료 체납 10 · 독박돌봄 10 · 지하·옥탑층 10 · 기초생활/차상위 5 · 기초연금 3
--   취약 가구 = 가점 10 이상 (수급·연금만 있는 가구는 제외)
--   목록 순서 : 취약 점수 DESC → vuln_hh_cnt DESC → priority_sum DESC
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS bld_resident_stat;
CREATE TABLE bld_resident_stat (
  bld_id          VARCHAR(28) NOT NULL,
  hh_cnt          INT NOT NULL COMMENT '가구 수',
  person_cnt      INT NOT NULL COMMENT '인원',
  vuln_hh_cnt     INT NOT NULL COMMENT '취약 가구 수 (가점 10 이상)',
  priority_sum    INT NOT NULL COMMENT '가구 가점 합',
  high_iso_cnt    INT NOT NULL COMMENT '고위험고립 가구',
  social_iso_cnt  INT NOT NULL COMMENT '사회적고립 가구',
  old75_cnt       INT NOT NULL COMMENT '75세 이상 인원',
  severe_cnt      INT NOT NULL COMMENT '심한 장애 인원',
  blind_hh_cnt    INT NOT NULL COMMENT '사각지대 가구',
  arrears_hh_cnt  INT NOT NULL COMMENT '건보료 체납 가구',
  care_hh_cnt     INT NOT NULL COMMENT '독박돌봄 가구',
  ugrnd_hh_cnt    INT NOT NULL COMMENT '지하층 거주 가구',
  rooftop_hh_cnt  INT NOT NULL COMMENT '옥탑층 거주 가구 (>0 이면 옥탑 요인)',
  poor_hh_cnt     INT NOT NULL COMMENT '기초생활·차상위 가구 (표시·소량 가점용)',
  PRIMARY KEY (bld_id),
  CONSTRAINT fk_stat_bld FOREIGN KEY (bld_id) REFERENCES building (bld_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='건물별 거주자 요약';

DROP TEMPORARY TABLE IF EXISTS tmp_hh;
CREATE TEMPORARY TABLE tmp_hh (PRIMARY KEY (hh_id), KEY (bld_id)) AS
SELECT r.hh_id, r.bld_id, r.res_flr, h.hh_mbr_cnt, h.risk_grade, h.blind_spot_yn, h.care_burden_yn,
       (h.basic_living_yn = 'Y' OR h.near_poor_yn = 'Y') poor,
       IFNULL(a.nhis_arrears_yn, 'N') = 'Y' arrears,
       x.old75, x.severe, x.pension
  FROM residency r
  JOIN household h ON h.hh_id = r.hh_id
  LEFT JOIN arrears a ON a.residency_id = r.residency_id AND a.arrears_type = 'HEALTH_INS'
  JOIN (SELECT hh_id, SUM(IFNULL(age, 0) >= 75) old75, SUM(IFNULL(dsbl_grade_nm, '') = '심한 장애') severe, SUM(IFNULL(basic_pension_yn, 'N') = 'Y') pension
          FROM person GROUP BY hh_id) x ON x.hh_id = r.hh_id;

ALTER TABLE tmp_hh ADD COLUMN pt INT;
UPDATE tmp_hh SET pt =
    (CASE IFNULL(risk_grade, '') WHEN '고위험고립' THEN 30 WHEN '사회적고립' THEN 15 ELSE 0 END)
  + IF(old75 > 0, 15, 0) + IF(severe > 0, 15, 0) + IF(IFNULL(blind_spot_yn, 'N') = 'Y', 15, 0)
  + IF(arrears, 10, 0) + IF(IFNULL(care_burden_yn, 'N') = 'Y', 10, 0)
  + IF(IFNULL(res_flr, '') LIKE '지하%' OR IFNULL(res_flr, '') = '옥탑층', 10, 0)
  + IF(IFNULL(poor, 0), 5, 0) + IF(pension > 0, 3, 0);

INSERT INTO bld_resident_stat
SELECT bld_id, COUNT(*), SUM(hh_mbr_cnt), SUM(pt >= 10), SUM(pt),
       SUM(IFNULL(risk_grade, '') = '고위험고립'), SUM(IFNULL(risk_grade, '') = '사회적고립'), SUM(old75), SUM(severe),
       SUM(IFNULL(blind_spot_yn, 'N') = 'Y'), SUM(arrears), SUM(IFNULL(care_burden_yn, 'N') = 'Y'),
       SUM(IFNULL(res_flr, '') LIKE '지하%'), SUM(IFNULL(res_flr, '') = '옥탑층'), SUM(IFNULL(poor, 0))
  FROM tmp_hh GROUP BY bld_id;
DROP TEMPORARY TABLE tmp_hh;

-- 확인
SELECT COUNT(*) bld_with_residents, SUM(rooftop_hh_cnt > 0) rooftop_bld, SUM(vuln_hh_cnt) vuln_hh FROM bld_resident_stat;
