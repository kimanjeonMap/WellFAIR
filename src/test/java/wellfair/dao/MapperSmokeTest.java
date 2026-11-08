package wellfair.dao;

import java.util.List;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import wellfair.to.AnalysisCriteriaTO;
import wellfair.to.BuildingMarkerTO;
import wellfair.to.BuildingTO;
import wellfair.to.ClusterTO;
import wellfair.to.GuTO;
import wellfair.to.DongTO;
import wellfair.to.ResidentCountTO;
import wellfair.to.ResidentTO;

/**
 * mapper XML 이 실제 DB 에서 도는지 확인하는 스모크 테스트 (Tomcat 없이).
 * 실행: mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=cp.txt
 *       java -cp "target/classes;target/test-classes;$(cat cp.txt)" wellfair.MapperSmokeTest
 */
public class MapperSmokeTest {

	public static void main(String[] args) throws Exception {
		try (ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("root-context.xml")) {
			// DAO 는 component-scan 대상이 아니므로 직접 만들고 SqlSession 만 꽂는다
			org.apache.ibatis.session.SqlSession session = ctx.getBean(org.apache.ibatis.session.SqlSession.class);
			BuildingDAO bdao = new BuildingDAO(); bdao.session = session;
			ResidentDAO rdao = new ResidentDAO(); rdao.session = session;
			ArchiveDAO adao = new ArchiveDAO(); adao.session = session;
			MemberDAO mdao = new MemberDAO(); mdao.session = session;

			long t0 = System.currentTimeMillis();
			System.out.println("member 2026001 = " + mdao.getMemberByLoginId("admin"));

			List<DongTO> dongs = bdao.getDongList();
			System.out.println("dongs = " + dongs.size() + " first=" + dongs.get(0));

			AnalysisCriteriaTO c = new AnalysisCriteriaTO();
			long t1 = System.currentTimeMillis();
			List<GuTO> gus = bdao.getGuSummary(c);
			System.out.println("gus = " + gus.size() + " (" + (System.currentTimeMillis() - t1) + "ms) first=" + gus.get(0));
			t1 = System.currentTimeMillis();
			System.out.println("coords = " + bdao.getCoordList().size() + " (" + (System.currentTimeMillis() - t1) + "ms)");

			AnalysisCriteriaTO cl = new AnalysisCriteriaTO();
			cl.setBbox("126.84,37.47,126.92,37.52"); cl.applyBbox();
			cl.setCell(0.0016); cl.setCellLat(0.0016 * Math.cos(Math.toRadians(37.5)));
			t1 = System.currentTimeMillis();
			List<ClusterTO> clusters = bdao.getClusterList(cl);
			int singles = 0; for (ClusterTO x : clusters) if (x.getSize() == 1) singles++;
			System.out.println("clusters = " + clusters.size() + " singles=" + singles + " (" + (System.currentTimeMillis() - t1) + "ms) first=" + clusters.get(0));

			c.setBbox("126.87,37.48,126.90,37.50");
			c.applyBbox();
			t1 = System.currentTimeMillis();
			List<BuildingMarkerTO> markers = bdao.getMarkerList(c);
			System.out.println("markers = " + markers.size() + " (" + (System.currentTimeMillis() - t1) + "ms) first=" + markers.get(0));

			AnalysisCriteriaTO r = new AnalysisCriteriaTO();
			r.setGu("11530"); r.setQ("가리봉");
			t1 = System.currentTimeMillis();
			int total = bdao.getResultCount(r);
			List<BuildingTO> page = bdao.getResultList(r);
			System.out.println("results total=" + total + " page=" + page.size() + " (" + (System.currentTimeMillis() - t1) + "ms)");
			System.out.println("  first = " + page.get(0).getAddr() + " / " + page.get(0).getScore() + " / " + page.get(0).getFactorText());

			AnalysisCriteriaTO d = new AnalysisCriteriaTO();
			d.setBldId(markers.get(0).getBldId()); d.setUserId(1L);
			BuildingTO b = bdao.getBuilding(d);
			System.out.println("building = " + b.getAddr() + " score=" + b.getScore() + " age=" + b.getBldAge() + " factors=" + b.getFactorText() + " checked=" + b.getCheckedAt() + " archive=" + b.getArchiveCnt());

			// 거주자: 옥탑 거주자가 있는 건물 하나
			String rtBld = session.selectOne("mapper-resident.getResident", 1001080L) != null ? "1991189376174433299400000000" : b.getBldId();
			t1 = System.currentTimeMillis();
			ResidentCountTO rc = rdao.getResidentCount(rtBld);
			List<ResidentTO> rl = rdao.getResidentList(rtBld);
			System.out.println("residents of " + rtBld + " count=" + rc + " (" + (System.currentTimeMillis() - t1) + "ms)");
			System.out.println("  summary = " + rc.getSummary());
			for (ResidentTO x : rl) System.out.println("  " + x.getResidentId() + " " + x.getResFlr() + " " + x.getHeadNm() + " " + x.getBirthDay() + " pri=" + x.getPriority() + " age=" + x.getMaxAge());
			System.out.println("resident 1001080 = " + rdao.getResident(1001080L));

			c.setUserId(1L);
			System.out.println("checked = " + bdao.getCheckedList(c).size());
			System.out.println("status = " + adao.getStatusCount(1L, null));
			System.out.println("TOTAL " + (System.currentTimeMillis() - t0) + "ms");
		}
	}
}
