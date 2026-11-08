-- =====================================================================
-- wellfair : 주사위(WellFAIR) 중간점검 DB  (MySQL 8)
-- 팀원 초안 기준으로 아래만 고침
--   [1] bld_id  BIGINT → VARCHAR(28)   (실제 값이 28자리라 BIGINT 범위 초과)
--   [2] building 컬럼 = 3gu_bld_db.csv 31개 그대로 (객실수·고시원추정은 units에 합산, 업소종류는 sp_type)
--   [3] mbr_id / hh_id 구별 오프셋 (구로 +1,000,000 / 양천 +2,000,000 / 영등포 +3,000,000) + household.sgg_cd
--   [4] crisis_info 40 → 200
--   [5] vulnerability_factor UNIQUE(vulnerability_id) → UNIQUE(vulnerability_id, bld_id)
--   [6] survey_plan.vulnerability_id → factor_id
--   [7] vulnerability_list + user_id, sgg_cd, title, criteria
--   [8] residency + dong_nm (아파트 동)
--   [9] 발굴 정보(sys_detect_yn ~ support_type)는 household → person 으로 이동
--       (더미에서 같은 가구 안에서도 사람마다 값이 달라서 가구 1줄로 못 담음)
--   [10] residency 의미없는 UNIQUE(residency_id, bld_id) 삭제 → UNIQUE(hh_id) (가구당 거주지 1곳)
--   [11] visit_history + user_id
-- =====================================================================

CREATE DATABASE IF NOT EXISTS wellfair DEFAULT CHARACTER SET utf8mb4;
USE wellfair;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS bld_resident_stat;   -- 04_update.sql 에서 만드는 테이블
DROP TABLE IF EXISTS handover_memo;
DROP TABLE IF EXISTS transfer_log;
DROP TABLE IF EXISTS survey_record;
DROP TABLE IF EXISTS visit_history;
DROP TABLE IF EXISTS survey_plan;
DROP TABLE IF EXISTS vulnerability_factor;
DROP TABLE IF EXISTS vulnerability_list;
DROP TABLE IF EXISTS arrears;
DROP TABLE IF EXISTS residency;
DROP TABLE IF EXISTS person;
DROP TABLE IF EXISTS household;
DROP TABLE IF EXISTS building;
DROP TABLE IF EXISTS survey_list;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 1. 사용자
-- ---------------------------------------------------------------------
CREATE TABLE users (
  user_id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '사용자ID (PK)',
  login_id       VARCHAR(50)     NOT NULL COMMENT '로그인 ID',
  password_hash  VARCHAR(255)    NOT NULL COMMENT '비밀번호 해시',
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_users_login_id (login_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 2. 현장조사리스트
-- ---------------------------------------------------------------------
CREATE TABLE survey_list (
  survey_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '현장조사ID (PK)',
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '소유 사용자',
  title       VARCHAR(100)    NOT NULL COMMENT '리스트명',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (survey_id),
  KEY idx_survey_list_user (user_id),
  CONSTRAINT fk_survey_list_user
    FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. 건물  [1][2]  = 3gu_bld_db.csv 31개 컬럼, 같은 순서
-- ---------------------------------------------------------------------
CREATE TABLE building (
  bld_id      VARCHAR(28)  NOT NULL COMMENT '건물 고유 ID 28자리 (PK)',
  sgg_cd      CHAR(5)      NOT NULL COMMENT '구 코드 11530 구로 / 11470 양천 / 11560 영등포',
  emd_nm      VARCHAR(20)  NOT NULL COMMENT '법정동명',
  addr        VARCHAR(100) NOT NULL COMMENT '지번 주소',
  bld_nm      VARCHAR(100) NULL     COMMENT '건물 이름',
  dong_nm     VARCHAR(50)  NULL     COMMENT '아파트 동 이름(101동 등)',
  use_cd      VARCHAR(5)   NULL     COMMENT '용도 코드',
  use_nm      VARCHAR(30)  NULL     COMMENT '대장 주용도명',
  grnd_flr    INT          NULL     COMMENT '지상 층수',
  ugrnd_flr   INT          NULL     COMMENT '지하 층수',
  useapr_day  CHAR(8)      NULL     COMMENT '사용승인일 YYYYMMDD',
  viol_yn     CHAR(1)      NULL     COMMENT '위반건축물 1/0',
  units       INT          NULL     COMMENT '사는 단위 수 (세대·가구 + 숙박객실 + 고시원추정 합산)',
  ugrnd_res   TINYINT      NOT NULL COMMENT '반지하 주거 가능 1/0',
  nonres_hh   TINYINT      NOT NULL COMMENT '비주거 용도인데 세대·가구 있음 1/0',
  sp_type     VARCHAR(20)  NULL     COMMENT '고시원 / 여관 / 여인숙',
  sp_nm       VARCHAR(200) NULL     COMMENT '업소 이름',
  elev_m      DOUBLE       NOT NULL COMMENT '고도 m',
  slope_deg   DOUBLE       NOT NULL COMMENT '경사 도',
  flood_cnt   INT          NOT NULL COMMENT '침수된 연도 수',
  flood_depth DOUBLE       NOT NULL COMMENT '최대 침수 깊이 m',
  price_type  VARCHAR(4)   NULL     COMMENT '개별 / 공동',
  price_pct   DOUBLE       NULL     COMMENT '같은 구·유형 내 공시가격 백분위 (낮을수록 쌈)',
  hosp_nm     VARCHAR(100) NOT NULL COMMENT '가장 가까운 병원',
  hosp_dist   DOUBLE       NOT NULL COMMENT '병원 거리 m',
  bus_nm      VARCHAR(100) NOT NULL COMMENT '가장 가까운 정류장',
  bus_dist    DOUBLE       NOT NULL COMMENT '정류장 거리 m',
  welf_nm     VARCHAR(100) NOT NULL COMMENT '가장 가까운 복지시설',
  welf_dist   DOUBLE       NOT NULL COMMENT '복지시설 거리 m',
  lng         DOUBLE       NOT NULL COMMENT '경도 WGS84',
  lat         DOUBLE       NOT NULL COMMENT '위도 WGS84',
  PRIMARY KEY (bld_id),
  KEY idx_building_emd (sgg_cd, emd_nm),
  KEY idx_building_coords (lat, lng)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='건물';

-- ---------------------------------------------------------------------
-- 4. 가구  [3][4][9]  가구 안에서 값이 같은 컬럼만
-- ---------------------------------------------------------------------
CREATE TABLE household (
  hh_id             BIGINT UNSIGNED NOT NULL COMMENT '세대_고유번호 (PK) = 원본 hh_id + 구 오프셋',
  sgg_cd            CHAR(5)         NOT NULL COMMENT '구 코드',
  hh_mbr_cnt        INT             NULL COMMENT '가구_규모',
  single_hh_yn      CHAR(1)         NULL COMMENT '1인가구_여부',
  hh_type           VARCHAR(10)     NULL COMMENT '가구유형',
  single_parent_yn  CHAR(1)         NULL COMMENT '한부모가족_해당여부',
  basic_living_yn   CHAR(1)         NULL COMMENT '기초생활수급_여부',
  near_poor_yn      CHAR(1)         NULL COMMENT '차상위계층_해당여부',
  care_burden_yn    CHAR(1)         NULL COMMENT '다인가구_독박돌봄_위험',
  risk_grade        VARCHAR(10)     NULL COMMENT '위험_등급',
  blind_spot_yn     CHAR(1)         NULL COMMENT '복지사각지대_해당여부',
  blind_spot_type   VARCHAR(10)     NULL COMMENT '사각지대_유형',
  created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (hh_id),
  KEY idx_household_sgg (sgg_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='가구';

-- ---------------------------------------------------------------------
-- 5. 개인 (가구원)  [3][4][9]
-- ---------------------------------------------------------------------
CREATE TABLE person (
  mbr_id            BIGINT UNSIGNED NOT NULL COMMENT '가구원_고유번호 (PK) = 원본 mbr_id + 구 오프셋',
  hh_id             BIGINT UNSIGNED NOT NULL COMMENT '세대_고유번호 (FK)',
  mbr_nm            VARCHAR(50)     NULL COMMENT '성명',
  head_rel          VARCHAR(10)     NULL COMMENT '세대주와의_관계 (본인 = 세대주)',
  sex               VARCHAR(2)      NULL COMMENT '성별',
  age               INT             NULL COMMENT '연령',
  birth_day         CHAR(6)         NULL COMMENT '생년월일 YYMMDD',
  res_area          VARCHAR(20)     NULL COMMENT '거주지',
  res_unknown_yn    CHAR(1)         NULL COMMENT '거주불명_말소_이력',
  dsbl_yn           CHAR(1)         NULL COMMENT '장애인등록여부',
  dsbl_grade_nm     VARCHAR(20)     NULL COMMENT '종합장애정도명',
  dsbl_type         VARCHAR(10)     NULL COMMENT '장애유형',
  basic_pension_yn  CHAR(1)         NULL COMMENT '기초연금수급_여부',
  -- 발굴 정보 (더미에서 사람마다 다름)
  sys_detect_yn     CHAR(1)         NULL COMMENT '시스템발굴_여부',
  detect_cnt        INT             NULL COMMENT '발굴횟수',
  crisis_info       VARCHAR(200)    NULL COMMENT '위기정보 (| 구분)',
  detect_result     VARCHAR(10)     NULL COMMENT '발굴_처리결과',
  non_target_rsn    VARCHAR(20)     NULL COMMENT '비대상_사유',
  support_type      VARCHAR(10)     NULL COMMENT '지원연계_유형',
  PRIMARY KEY (mbr_id),
  KEY idx_person_hh (hh_id),
  CONSTRAINT fk_person_household
    FOREIGN KEY (hh_id) REFERENCES household (hh_id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='개인(가구원)';

-- ---------------------------------------------------------------------
-- 6. 입주 (건물 ↔ 가구)  [1][8][10]   한 가구 = 한 줄
-- ---------------------------------------------------------------------
CREATE TABLE residency (
  residency_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '입주ID (PK)',
  bld_id        VARCHAR(28)     NOT NULL COMMENT '건물 (FK)',
  hh_id         BIGINT UNSIGNED NOT NULL COMMENT '가구 (FK)',
  res_flr       VARCHAR(10)     NULL COMMENT '거주 층 (지하1층 / 3층 / 옥탑층 …)',
  dong_nm       VARCHAR(50)     NULL COMMENT '아파트 동 (없으면 NULL)',
  ho_nm         VARCHAR(30)     NULL COMMENT '호실 (전유부 실제 호만, 단독·다가구는 NULL)',
  tenure_type   ENUM('JEONSE','MONTHLY') NULL COMMENT '전월세 여부 (더미에 없음, NULL)',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (residency_id),
  UNIQUE KEY uq_residency_hh (hh_id),
  KEY idx_residency_bld (bld_id),
  CONSTRAINT fk_residency_building
    FOREIGN KEY (bld_id) REFERENCES building (bld_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_residency_household
    FOREIGN KEY (hh_id) REFERENCES household (hh_id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='입주(거주지)';

-- ---------------------------------------------------------------------
-- 7. 체납  (건보료는 세대 단위 → 가구원 중 한 명이라도 Y면 Y)
-- ---------------------------------------------------------------------
CREATE TABLE arrears (
  arrears_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '체납ID (PK)',
  residency_id     BIGINT UNSIGNED NOT NULL COMMENT '입주ID (FK)',
  arrears_type     VARCHAR(30)     NOT NULL DEFAULT 'HEALTH_INS' COMMENT '체납 종류',
  nhis_arrears_yn  CHAR(1)         NOT NULL DEFAULT 'N' COMMENT '건보료_체납_여부',
  amount           DECIMAL(12,0)   NULL COMMENT '체납액',
  PRIMARY KEY (arrears_id),
  UNIQUE KEY uq_arrears_residency_type (residency_id, arrears_type),
  CONSTRAINT fk_arrears_residency
    FOREIGN KEY (residency_id) REFERENCES residency (residency_id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='체납';

-- ---------------------------------------------------------------------
-- 8. 취약리스트 (분석 1회 = 1줄, 보관함 단위)  [7]
-- ---------------------------------------------------------------------
CREATE TABLE vulnerability_list (
  vulnerability_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '취약리스트ID (PK)',
  user_id           BIGINT UNSIGNED NOT NULL COMMENT '분석한 사용자 (보관함 주인)',
  sgg_cd            CHAR(5)         NOT NULL COMMENT '분석한 구',
  title             VARCHAR(100)    NOT NULL COMMENT '보관함에 보이는 이름',
  criteria          VARCHAR(500)    NULL COMMENT '가중치·필터 JSON 문자열 (다시 열 때 복원)',
  analyzed_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_active         TINYINT(1)      NOT NULL DEFAULT 1,
  PRIMARY KEY (vulnerability_id),
  KEY idx_vuln_user (user_id, is_active),
  CONSTRAINT fk_vuln_user
    FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 9. 취약요인 (분석 1회 안의 건물별 점수)  [1][5]
-- ---------------------------------------------------------------------
CREATE TABLE vulnerability_factor (
  factor_id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '취약요인ID (PK)',
  vulnerability_id     BIGINT UNSIGNED NOT NULL COMMENT '취약리스트ID (FK)',
  bld_id               VARCHAR(28)     NOT NULL COMMENT '건물 (FK)',
  residency_id         BIGINT UNSIGNED NULL COMMENT '입주ID (건물 단위 점수면 NULL)',
  is_applicable        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '취약 해당 여부',
  score                DECIMAL(6,2)    NULL COMMENT '요인 점수',
  vulnerability_score  DECIMAL(6,2)    NULL COMMENT '취약 종합 점수 (100점 환산)',
  vulnerability_grade  VARCHAR(20)     NULL COMMENT '취약 등급',
  evaluated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (factor_id),
  UNIQUE KEY uq_factor_vuln_bld (vulnerability_id, bld_id),
  KEY idx_factor_building (bld_id),
  CONSTRAINT fk_factor_vuln
    FOREIGN KEY (vulnerability_id) REFERENCES vulnerability_list (vulnerability_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_factor_residency
    FOREIGN KEY (residency_id) REFERENCES residency (residency_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_factor_building
    FOREIGN KEY (bld_id) REFERENCES building (bld_id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 10. 현장조사 사전관리 (리스트에 담긴 건물)  [6]
-- ---------------------------------------------------------------------
CREATE TABLE survey_plan (
  plan_id      BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT '사전관리ID (PK)',
  survey_id    BIGINT UNSIGNED   NOT NULL COMMENT '현장조사ID (FK)',
  factor_id    BIGINT UNSIGNED   NOT NULL COMMENT '취약요인ID (FK) → 어느 분석의 어느 건물·몇 점',
  plan_date    DATE              NULL COMMENT '조사 예정 날짜',
  visit_order  SMALLINT UNSIGNED NULL COMMENT '방문 순서',
  created_at   DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (plan_id),
  UNIQUE KEY uq_plan_survey_factor (survey_id, factor_id),
  UNIQUE KEY uq_plan_survey_order (survey_id, visit_order),
  KEY idx_plan_factor (factor_id),
  CONSTRAINT fk_plan_survey
    FOREIGN KEY (survey_id) REFERENCES survey_list (survey_id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_plan_factor
    FOREIGN KEY (factor_id) REFERENCES vulnerability_factor (factor_id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 11. 현장 방문이력  [1][11]
-- ---------------------------------------------------------------------
CREATE TABLE visit_history (
  visit_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '방문이력ID (PK)',
  user_id       BIGINT UNSIGNED NOT NULL COMMENT '방문한 사용자 (FK)',
  bld_id        VARCHAR(28)     NOT NULL COMMENT '방문 건물 (FK)',
  residency_id  BIGINT UNSIGNED NULL COMMENT '방문 호실',
  visited_at    DATETIME        NOT NULL COMMENT '방문 일시',
  result        ENUM('MET','ABSENT','REFUSED','OTHER') NULL COMMENT '방문 결과',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (visit_id),
  KEY idx_visit_building_time (bld_id, visited_at),
  KEY idx_visit_user (user_id),
  CONSTRAINT fk_visit_user
    FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_visit_building
    FOREIGN KEY (bld_id) REFERENCES building (bld_id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 12. 현장조사기록
-- ---------------------------------------------------------------------
CREATE TABLE survey_record (
  record_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '기록ID (PK)',
  plan_id      BIGINT UNSIGNED NOT NULL COMMENT '사전관리ID (FK)',
  visit_id     BIGINT UNSIGNED NOT NULL COMMENT '방문이력ID (FK)',
  content      TEXT            NOT NULL COMMENT '관찰/조사 내용',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (record_id),
  KEY idx_record_plan (plan_id),
  KEY idx_record_visit (visit_id),
  CONSTRAINT fk_record_plan
    FOREIGN KEY (plan_id) REFERENCES survey_plan (plan_id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_record_visit
    FOREIGN KEY (visit_id) REFERENCES visit_history (visit_id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_residency_flr_bld ON residency (res_flr, bld_id);
