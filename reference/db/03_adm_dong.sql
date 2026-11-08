-- =====================================================================
-- 건물에 행정동(구로1동, 신정4동 …) 붙이기
-- bld_adm_dong.csv = 건물 좌표를 행정동 경계(행정안전부 2025-01-01 기준, vuski/admdongkor)와 공간 조인한 결과
--   72,961동 전부 매칭 (경계선 위 42동은 가장 가까운 행정동). 3개 구 행정동 52개
-- 01 → 02 다음에 실행
-- =====================================================================
USE wellfair;
SET SQL_SAFE_UPDATES = 0;   -- Workbench 안전 모드(Error 1175) 끄기

ALTER TABLE building
  ADD COLUMN adm_cd CHAR(10)    NULL COMMENT '행정동 코드' AFTER emd_nm,
  ADD COLUMN adm_nm VARCHAR(20) NULL COMMENT '행정동명 (구로1동 등)' AFTER adm_cd,
  ADD KEY idx_building_adm (sgg_cd, adm_nm);

DROP TABLE IF EXISTS stg_adm;
CREATE TABLE stg_adm (bld_id VARCHAR(28) PRIMARY KEY, adm_cd CHAR(10), adm_nm VARCHAR(20)) DEFAULT CHARSET=utf8mb4;

LOAD DATA LOCAL INFILE 'C:/final_data/jusawi_handoff/reference/db/bld_adm_dong.csv' INTO TABLE stg_adm
  CHARACTER SET utf8mb4 FIELDS TERMINATED BY ',' LINES TERMINATED BY '\n' IGNORE 1 LINES
  (bld_id, adm_cd, @adm_nm) SET adm_nm = TRIM(TRAILING '\r' FROM @adm_nm);

UPDATE building b JOIN stg_adm s ON s.bld_id = b.bld_id SET b.adm_cd = s.adm_cd, b.adm_nm = s.adm_nm;

-- 확인: 72961 / 52
SELECT COUNT(*) filled, COUNT(DISTINCT adm_nm) dongs FROM building WHERE adm_nm IS NOT NULL;
DROP TABLE stg_adm;
SET SQL_SAFE_UPDATES = 1;
