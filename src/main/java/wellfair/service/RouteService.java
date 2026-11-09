package wellfair.service;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import wellfair.common.BadRequestException;
import wellfair.to.WalkPathTO;

/**
 * 현장확인 동선 경로 (TMAP). 서버에서만 호출 : appKey 를 브라우저에 내보내지 않는다.
 *  mode = walk    : 보행자 경로 (tmap/routes/pedestrian)
 *         car     : 자동차 경로 (tmap/routes)
 *         transit : 대중교통 (transit/routes). 하루 무료 호출이 아주 적어서
 *                   - 직선 TRANSIT_MIN_M 미만 구간은 대중교통을 부르지 않고 보행자 경로로 (걸어가는 게 맞는 거리)
 *                   - 대중교통 결과가 없으면(가까움 · 경로 없음 · 한도 초과) 보행자 경로로
 *  - 출발 → 1 → 2 → … 를 구간(두 점)마다 부르고 이어 붙인다. 구간은 한 번에 하나씩 (TMAP 초당 제한 : 동시에 부르면 429)
 *  - 같은 구간 결과는 기억한다 : 메모리 + 파일(톰캣을 껐다 켜도 대중교통 호출을 다시 쓰지 않게) = {user.home}/.wellfair/tmap-route-cache.json
 *  - 실패한 구간은 직선 (leg.straight = true), 실패는 기억하지 않는다 (다음에 다시 시도)
 * appKey = app.properties 의 app.tmap.app-key (보행자 · 자동차 · 대중교통 상품이 그 앱에 신청돼 있어야 한다)
 */
@Service
public class RouteService {

	private static final Logger log = LoggerFactory.getLogger(RouteService.class);

	static final String WALK_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";
	static final String CAR_URL = "https://apis.openapi.sk.com/tmap/routes?version=1";
	static final String TRANSIT_URL = "https://apis.openapi.sk.com/transit/routes";
	static final int MAX_POINTS = 31;          // 출발 + 건물 30곳
	static final double NEAR_M = 15;           // 이보다 가까운 두 점은 부르지 않고 직선
	static final double TRANSIT_MIN_M = 800;   // 대중교통 모드에서도 이보다 가까운 구간은 걸어서
	static final int LEG_TIMEOUT_SEC = 8, TOTAL_TIMEOUT_SEC = 30;
	static final long GAP_MS = 250, RETRY_MS = 1200;   // 호출 사이 간격 · 429 뒤 다시 부르기 전 대기

	private static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(4)).build();
	private static final File CACHE_FILE = new File(System.getProperty("user.home"), ".wellfair/tmap-route-cache.json");

	/** 구간 기억 : "mode:lng,lat>lng,lat" (소수 6자리) → 결과 */
	private final Map<String, LegResult> cache = new ConcurrentHashMap<>();

	@Value("${app.tmap.app-key:}")
	String appKey;

	public RouteService() {
		loadCache();
	}

	/** lngs · lats = 출발점부터 방문 순서대로, mode = walk | transit | car */
	public WalkPathTO walkPath(double[] lngs, double[] lats, String mode) {
		if (appKey == null || appKey.isBlank()) throw new IllegalStateException("TMAP appKey 가 설정되지 않았습니다. (app.properties : app.tmap.app-key)");
		if (lngs == null || lats == null || lngs.length != lats.length || lngs.length < 2) throw new BadRequestException("경로를 만들 지점이 2곳 이상 필요합니다.");
		if (lngs.length > MAX_POINTS) throw new BadRequestException("한 번에 " + (MAX_POINTS - 1) + "곳까지만 길찾기를 할 수 있습니다.");
		for (int i = 0; i < lngs.length; i++) {
			// 서울 근처 좌표만 (아무 좌표나 넣어 TMAP 호출을 쓰지 못하게)
			if (lngs[i] < 126.0 || lngs[i] > 128.0 || lats[i] < 36.5 || lats[i] > 38.5) throw new BadRequestException("좌표가 올바르지 않습니다.");
		}
		String m = "transit".equals(mode) || "car".equals(mode) ? mode : "walk";

		long t0 = System.currentTimeMillis();
		int n = lngs.length - 1;
		log.info("TMAP 경로 요청 ({}) : {}구간", m, n);
		WalkPathTO out = new WalkPathTO();
		out.setMode(m);
		int straightCnt = 0;
		for (int i = 1; i <= n; i++) {
			LegResult r;
			if (System.currentTimeMillis() - t0 > TOTAL_TIMEOUT_SEC * 1000L) {
				log.warn("TMAP 구간 {} : 전체 시간 초과 → 직선", i);
				r = straight(lngs[i - 1], lats[i - 1], lngs[i], lats[i]);
			} else {
				r = leg(m, lngs[i - 1], lats[i - 1], lngs[i], lats[i], i);
			}
			if (r.straight) straightCnt++;
			WalkPathTO.Leg leg = new WalkPathTO.Leg();
			leg.setDistance(r.distance);
			leg.setTime(r.time);
			leg.setStraight(r.straight);
			leg.setSummary(r.summary);
			out.getLegs().add(leg);
			out.setTotalDistance(out.getTotalDistance() + r.distance);
			out.setTotalTime(out.getTotalTime() + r.time);
			for (Seg s : r.segs) {
				WalkPathTO.Segment seg = new WalkPathTO.Segment();
				seg.setMode(s.mode);
				seg.setRoute(s.route);
				seg.setPath(s.path);
				out.getSegments().add(seg);
				for (double[] pt : s.path) addPoint(out.getPath(), pt);
			}
		}
		log.info("TMAP 경로 ({}) : {}구간 중 직선 {}구간 · {}m · {}초 · 걸린 시간 {}ms", m, n, straightCnt, out.getTotalDistance(), out.getTotalTime(), System.currentTimeMillis() - t0);
		return out;
	}

	/** 구간 하나 : 기억 → (가까우면 직선) → 수단별 호출 → 실패하면 보행자 / 직선 */
	private LegResult leg(String mode, double sLng, double sLat, double eLng, double eLat, int no) {
		double d = meters(sLng, sLat, eLng, eLat);
		if (d < NEAR_M) {
			LegResult r = straight(sLng, sLat, eLng, eLat);
			r.straight = false;                            // 같은 건물 수준으로 가까움 : 실패가 아니라 "일부 구간 직선" 표시 안 함
			return r;
		}
		// 대중교통이라도 가까운 구간은 걸어서
		if ("transit".equals(mode) && d < TRANSIT_MIN_M) mode = "walk";

		String key = String.format("%s:%.6f,%.6f>%.6f,%.6f", mode, sLng, sLat, eLng, eLat);
		LegResult hit = cache.get(key);
		if (hit != null) return hit;

		LegResult r = null;
		try {
			r = "transit".equals(mode) ? transit(sLng, sLat, eLng, eLat) : routeGeo(mode, sLng, sLat, eLng, eLat);
		} catch (RuntimeException e) {
			log.warn("TMAP 구간 {} ({}) 실패 : {}", no, mode, e.getMessage());
		}
		if (r == null && !"walk".equals(mode)) {
			// 대중교통 · 자동차가 안 되면 보행자 경로로 (대중교통은 "너무 가까움" · "경로 없음" 도 여기로)
			return leg("walk", sLng, sLat, eLng, eLat, no);
		}
		if (r == null) return straight(sLng, sLat, eLng, eLat);
		cache.put(key, r);
		if ("transit".equals(mode)) saveCache();        // 대중교통은 하루 호출이 적어서 파일에도 남긴다
		return r;
	}

	/* ---------------- 보행자 · 자동차 : GeoJSON (Point 피처 totalDistance · totalTime + LineString 좌표) ---------------- */
	private LegResult routeGeo(String mode, double sLng, double sLat, double eLng, double eLat) {
		boolean car = "car".equals(mode);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("startX", sLng);
		body.put("startY", sLat);
		body.put("endX", eLng);
		body.put("endY", eLat);
		body.put("reqCoordType", "WGS84GEO");
		body.put("resCoordType", "WGS84GEO");
		body.put("searchOption", "0");                     // 0 = 추천
		if (car) body.put("trafficInfo", "N");
		else {
			body.put("startName", enc("출발"));            // 보행자는 이름 필수. URL 인코딩해서 받는다
			body.put("endName", enc("도착"));
		}
		JsonNode root = postRetry(car ? CAR_URL : WALK_URL, body);

		LegResult r = new LegResult();
		Seg seg = new Seg();
		seg.mode = car ? "CAR" : "WALK";
		for (JsonNode f : root.path("features")) {
			JsonNode g = f.path("geometry"), p = f.path("properties");
			if (p.has("totalDistance")) {
				r.distance = p.path("totalDistance").asInt();
				r.time = p.path("totalTime").asInt();
			}
			if ("LineString".equals(g.path("type").asText())) {
				for (JsonNode c : g.path("coordinates")) addPoint(seg.path, new double[] { c.get(0).asDouble(), c.get(1).asDouble() });
			}
		}
		if (seg.path.isEmpty()) throw new IllegalStateException("경로 좌표 없음");
		r.segs.add(seg);
		return r;
	}

	/* ---------------- 대중교통 : metaData.plan.itineraries[0].legs[] ----------------
	 *  leg.mode = WALK | BUS | SUBWAY | EXPRESSBUS | TRAIN | AIRPLANE | FERRY
	 *  좌표 : 버스 · 지하철 = passShape.linestring ("lng,lat lng,lat ..."), 걷기 = steps[].linestring (없으면 start → end 직선)
	 *  경로가 없으면 result.status (11 = 너무 가까움 등) 로 온다 → null (보행자로 대신) */
	private LegResult transit(double sLng, double sLat, double eLng, double eLat) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("startX", String.valueOf(sLng));
		body.put("startY", String.valueOf(sLat));
		body.put("endX", String.valueOf(eLng));
		body.put("endY", String.valueOf(eLat));
		body.put("count", 1);
		body.put("lang", 0);
		body.put("format", "json");
		JsonNode root = postRetry(TRANSIT_URL, body);

		JsonNode it = root.path("metaData").path("plan").path("itineraries").path(0);
		if (it.isMissingNode()) {
			log.info("TMAP 대중교통 결과 없음 → 보행자 : {}", root.path("result").toString());
			return null;
		}
		LegResult r = new LegResult();
		r.distance = it.path("totalDistance").asInt();
		r.time = it.path("totalTime").asInt();
		List<String> rides = new ArrayList<>();
		for (JsonNode lg : it.path("legs")) {
			Seg seg = new Seg();
			seg.mode = lg.path("mode").asText("WALK");
			seg.route = lg.path("route").asText(null);
			if ("WALK".equals(seg.mode)) {
				for (JsonNode st : lg.path("steps")) parseLine(st.path("linestring").asText(""), seg.path);
			} else {
				parseLine(lg.path("passShape").path("linestring").asText(""), seg.path);
				rides.add((seg.route != null ? seg.route : seg.mode) + " (" + lg.path("start").path("name").asText("") + "→" + lg.path("end").path("name").asText("") + ")");
			}
			if (seg.path.isEmpty()) {   // 좌표가 없으면 그 leg 의 시작 → 끝
				JsonNode a = lg.path("start"), b = lg.path("end");
				if (a.has("lon") && b.has("lon")) {
					seg.path.add(new double[] { a.path("lon").asDouble(), a.path("lat").asDouble() });
					seg.path.add(new double[] { b.path("lon").asDouble(), b.path("lat").asDouble() });
				}
			}
			if (!seg.path.isEmpty()) r.segs.add(seg);
		}
		if (r.segs.isEmpty()) return null;
		r.summary = rides.isEmpty() ? "도보" : String.join(" · ", rides);
		return r;
	}

	private static void parseLine(String line, List<double[]> into) {
		for (String p : line.trim().split("\\s+")) {
			String[] xy = p.split(",");
			if (xy.length < 2) continue;
			try { addPoint(into, new double[] { Double.parseDouble(xy[0]), Double.parseDouble(xy[1]) }); } catch (NumberFormatException ignore) {}
		}
	}

	/* ---------------- 호출 ---------------- */

	/** 앞 호출과 GAP_MS 간격을 두고 POST. 429(초당 제한) · 5xx · 시간 초과면 RETRY_MS 쉬고 한 번 더 */
	private JsonNode postRetry(String url, Map<String, Object> body) {
		for (int attempt = 0; ; attempt++) {
			pace(attempt == 0 ? GAP_MS : RETRY_MS);
			try {
				return post(url, body);
			} catch (IllegalStateException e) {
				String m = String.valueOf(e.getMessage());
				boolean again = m.startsWith("HTTP 429") || m.startsWith("HTTP 5") || m.contains("Timeout");
				if (attempt >= 1 || !again) throw e;
				log.info("TMAP 다시 시도 : {}", m.length() > 120 ? m.substring(0, 120) : m);
			}
		}
	}

	private JsonNode post(String url, Map<String, Object> body) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
					.header("appKey", appKey).header("Accept", "application/json").header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(LEG_TIMEOUT_SEC))
					.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8)).build();
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode() + " " + res.body());
			return JSON.readTree(res.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("중단됨");
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e.toString());
		}
	}

	private long lastCall = 0;
	private synchronized void pace(long gapMs) {
		long wait = lastCall + gapMs - System.currentTimeMillis();
		if (wait > 0) {
			try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		}
		lastCall = System.currentTimeMillis();
	}

	/* ---------------- 기억 (파일) ---------------- */
	private void loadCache() {
		try {
			if (CACHE_FILE.isFile()) {
				Map<String, LegResult> m = JSON.readValue(CACHE_FILE, new TypeReference<Map<String, LegResult>>() {});
				cache.putAll(m);
				log.info("TMAP 경로 기억 {}건 불러옴 ({})", m.size(), CACHE_FILE);
			}
		} catch (Exception e) {
			log.warn("TMAP 경로 기억 파일을 읽지 못했습니다 : {}", e.toString());
		}
	}

	private synchronized void saveCache() {
		try {
			CACHE_FILE.getParentFile().mkdirs();
			JSON.writeValue(CACHE_FILE, cache);
		} catch (Exception e) {
			log.warn("TMAP 경로 기억 파일을 쓰지 못했습니다 : {}", e.toString());
		}
	}

	/* ---------------- 도우미 ---------------- */

	/** 직선 대신 : 거리 = 직선 × 1.3, 시간 = 도보 4km/h (field-map.js 의 기존 계산과 같은 기준) */
	private static LegResult straight(double sLng, double sLat, double eLng, double eLat) {
		LegResult r = new LegResult();
		r.straight = true;
		r.distance = (int) Math.round(meters(sLng, sLat, eLng, eLat) * 1.3);
		r.time = (int) Math.round(r.distance / (4000.0 / 3600));
		Seg seg = new Seg();
		seg.mode = "WALK";
		seg.path.add(new double[] { sLng, sLat });
		seg.path.add(new double[] { eLng, eLat });
		r.segs.add(seg);
		return r;
	}

	/** 같은 점이 연달아 오면 한 번만 */
	private static void addPoint(List<double[]> path, double[] pt) {
		double[] last = path.isEmpty() ? null : path.get(path.size() - 1);
		if (last == null || last[0] != pt[0] || last[1] != pt[1]) path.add(pt);
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	/** 두 점 사이 거리 m (하버사인) */
	static double meters(double lng1, double lat1, double lng2, double lat2) {
		double R = 6371000, dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * R * Math.asin(Math.sqrt(a));
	}

	/** 구간 결과 (파일에 JSON 으로 저장되므로 public 필드) */
	public static class LegResult {
		public int distance, time;
		public boolean straight;
		public String summary;                       // 대중교통 : "간선:600 (A→B) · 2호선 (C→D)"
		public List<Seg> segs = new ArrayList<>();
	}

	public static class Seg {
		public String mode;                          // WALK | CAR | BUS | SUBWAY | ...
		public String route;                         // 대중교통 노선 이름
		public List<double[]> path = new ArrayList<>();
	}
}
