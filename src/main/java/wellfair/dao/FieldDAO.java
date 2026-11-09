package wellfair.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import wellfair.to.ArchiveRowTO;
import wellfair.to.FieldCheckTO;
import wellfair.to.FieldItemTO;
import wellfair.to.FieldListTO;

// FieldService 에서만 부른다. 메서드명 = mapper-field.xml 의 id (테이블은 survey_list / survey_plan / visit_history / survey_record)
@Component
public class FieldDAO {

	@Autowired
	SqlSession session;

	public List<FieldListTO> getFieldList(long userId, String gu, String tab, String q) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId); p.put("gu", gu); p.put("tab", tab); p.put("q", q);
		return session.selectList("mapper-field.getFieldList", p);
	}

	public FieldListTO getField(long surveyId, long userId, String gu) {
		Map<String, Object> p = new HashMap<>();
		p.put("surveyId", surveyId); p.put("userId", userId); p.put("gu", gu);
		return session.selectOne("mapper-field.getField", p);
	}

	public List<String> getFactorSggList(List<Long> factorIds) {
		return session.selectList("mapper-field.getFactorSggList", factorIds);
	}

	// factorIds 중 내 보관함(user_id) 것의 수 (서로 다른 id 기준)
	public int countMyFactors(long userId, List<Long> factorIds) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId); p.put("factorIds", factorIds);
		return session.selectOne("mapper-field.countMyFactors", p);
	}

	/** 내 리스트 중 이름이 prefix 로 시작하는 것들의 이름 (자동 이름 "새 리스트N" 번호 매기기용) */
	public List<String> getAutoTitles(long userId, String prefix) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId); p.put("prefix", prefix);
		return session.selectList("mapper-field.getAutoTitles", p);
	}

	public int addField(FieldListTO to) {
		return session.insert("mapper-field.addField", to);
	}

	public int updateField(FieldListTO to) {
		return session.update("mapper-field.updateField", to);
	}

	public int updateMoveMode(long surveyId, long userId, String moveMode) {
		Map<String, Object> p = new HashMap<>();
		p.put("surveyId", surveyId); p.put("userId", userId); p.put("moveMode", moveMode);
		return session.update("mapper-field.updateMoveMode", p);
	}

	public List<FieldItemTO> getFieldItemList(long surveyId) {
		return session.selectList("mapper-field.getFieldItemList", surveyId);
	}

	public int clearItemOrder(long surveyId) {
		return session.update("mapper-field.clearItemOrder", surveyId);
	}

	public int deleteItemsNotIn(long surveyId, List<Long> factorIds) {
		Map<String, Object> p = new HashMap<>();
		p.put("surveyId", surveyId); p.put("factorIds", factorIds);
		return session.delete("mapper-field.deleteItemsNotIn", p);
	}

	public int countFieldRecords(long surveyId) {
		return session.selectOne("mapper-field.countFieldRecords", surveyId);
	}

	public int deleteField(long surveyId, long userId) {
		Map<String, Object> p = new HashMap<>();
		p.put("surveyId", surveyId); p.put("userId", userId);
		return session.delete("mapper-field.deleteField", p);
	}

	public int addItem(long surveyId, long factorId, int visitOrder) {
		Map<String, Object> p = new HashMap<>();
		p.put("surveyId", surveyId); p.put("factorId", factorId); p.put("visitOrder", visitOrder);
		return session.insert("mapper-field.addItem", p);
	}

	public int addVisit(FieldCheckTO to) {
		return session.insert("mapper-field.addVisit", to);
	}

	public int addRecord(FieldCheckTO to) {
		return session.insert("mapper-field.addRecord", to);
	}

	public List<ArchiveRowTO> getPickList(long userId, String gu) {
		Map<String, Object> p = new HashMap<>();
		p.put("userId", userId); p.put("gu", gu);
		return session.selectList("mapper-field.getPickList", p);
	}
}
