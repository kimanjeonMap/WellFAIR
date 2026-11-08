/*
 * 서버 호출은 여기에서만. 주소는 전부 api/map/*.do (MapRESTController) / api/archive/*.do
 * params 는 AnalysisCriteriaTO 와 같은 이름 (ugrndOn, ugrndW, oldYears, scoreMin, gu, dong, bbox, cell, limit, q, sort, page ...)
 */
window.MapApi = (function () {
  var api = Common.api;

  return {
    /* 구 단위 (zoom ≤ 14) : 경계 + "위험 N곳" */
    gus: function (params) { return api('GET', 'api/map/gus.do', params); },

    /* 격자 묶음 (zoom 15~17) : bbox + cell */
    clusters: function (params) { return api('GET', 'api/map/clusters.do', params); },

    /* 건물 핀 (zoom ≥ 18) : bbox + limit */
    buildings: function (params) { return api('GET', 'api/map/buildings.do', params); },

    /* 분석 전 : 현장 확인됨 핀 */
    checked: function (params) { return api('GET', 'api/map/checked.do', params); },

    /* 오른쪽 결과 리스트 (PageTO) */
    results: function (params) { return api('GET', 'api/map/results.do', params); },

    /* 건물 상세 (BuildingDetailTO) */
    building: function (params) { return api('GET', 'api/map/building.do', params); },

    /* 거주 가구 탭 (가림 카드) */
    residents: function (bldId) { return api('GET', 'api/map/residents.do', { bldId: bldId }); },

    /* 정보 확인하기 : 동·호수만 */
    resident: function (residentId) { return api('GET', 'api/map/resident.do', { residentId: residentId }); },

    /* 보관함 저장 */
    archiveAdd: function (bldId, sggCd, score, criteria) {
      return api('POST', 'api/archive/add.do', { bldId: bldId, sggCd: sggCd, score: score, criteria: JSON.stringify(criteria || {}) });
    }
  };
})();
