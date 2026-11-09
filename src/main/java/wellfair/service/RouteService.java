package wellfair.service;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import wellfair.common.BadRequestException;
import wellfair.to.WalkPathTO;

/**
 * 현장확인 동선 도보 경로 (TMAP 보행자 경로안내). 서버에서만 호출 : appKey 를 브라우저에 내보내지 않는다.
 *  - 출발 → 1 → 2 → … 를 구간(두 점)마다 TMAP 에 물어보고 이어 붙인다 (구간별 거리 · 시간을 그대로 쓸 수 있게)
 *  - 같은 구간은 메모리에 기억 (리스트 수정 화면에서 순서만 바꿔도 다시 부르지 않게. TMAP 무료 호출 수 절약)
 *  - 구간 하나가 실패해도 전체를 버리지 않고 그 구간만 직선으로 (leg.straight = true)
 * appKey = app.properties 의 app.tmap.app-key
 */
@Service
public class RouteService {

	private static final Logger log = LoggerFactory.getLogger(RouteService.class);

	static final String WALK_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";
	static final int MAX_POINTS = 31;          // 출발 + 건물 30곳
	static final double NEAR_M = 15;           // 이보다 가까운 두 점은 TMAP 을 부르지 않고 직선

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	/** 구간 기억 : "lng,lat>lng,lat" (소수 6자리) → 결과. 앱 재시작하면 비워진다 */
	private final Map<String, LegResult> cache = new ConcurrentHashMap<>();

	@Value("${app.tmap.app-key:}")
	String appKey;

	/** lngs · lats = 출발점부터 방문 순서대로 */
	public WalkPathTO walkPath(double[] lngs, double[] lats) {
		if (appKey == null || appKey.isBlank()) throw new IllegalStateException("TMAP appKey 가 설정되지 않았습니다. (app.properties : app.tmap.app-key)");
		if (lngs == null || lats == null || lngs.length != lats.length || lngs.length < 2) throw new BadRequestException("경로를 만들 지점이 2곳 이상 필요합니다.");
		if (lngs.length > MAX_POINTS) throw new BadRequestException("한 번에 " + (MAX_POINTS - 1) + "곳까지만 길찾기를 할 수 있습니다.");
		for (int i = 0; i < lngs.length; i++) {
			// 서울 근처 좌표만 (아무 좌표나 넣어 TMAP 호출을 쓰지 못하게)
			if (lngs[i] < 126.0 || lngs[i] > 128.0 || lats[i] < 36.5 || lats[i] > 38.5) throw new BadRequestException("좌표가 올바르지 않습니다.");
		}

		WalkPathTO out = new WalkPathTO();
		for (int i = 1; i < lngs.length; i++) {
			LegResult r = leg(lngs[i - 1], lats[i - 1], lngs[i], lats[i]);
			WalkPathTO.Leg leg = new WalkPathTO.Leg();
			leg.setDistance(r.distance);
			leg.setTime(r.time);
			leg.setStraight(r.straight);
			out.getLegs().add(leg);
			out.setTotalDistance(out.getTotalDistance() + r.distance);
			out.setTotalTime(out.getTotalTime() + r.time);
			for (double[] pt : r.path) addPoint(out.getPath(), pt);
		}
		return out;
	}

	private LegResult leg(double sLng, double sLat, double eLng, double eLat) {
		String key = String.format("%.6f,%.6f>%.6f,%.6f", sLng, sLat, eLng, eLat);
		LegResult hit = cache.get(key);
		if (hit != null) return hit;

		double straightM = meters(sLng, sLat, eLng, eLat);
		LegResult r;
		if (straightM < NEAR_M) {
			r = straight(sLng, sLat, eLng, eLat, straightM);
		} else {
			try {
				r = pedestrian(sLng, sLat, eLng, eLat);
			} catch (RuntimeException e) {
				log.warn("TMAP 보행자 경로 실패 ({}) : {}", key, e.getMessage());
				return straight(sLng, sLat, eLng, eLat, straightM);   // 실패는 기억하지 않는다 (다음에 다시 시도)
			}
		}
		cache.put(key, r);
		return r;
	}

	/** TMAP 보행자 경로 : Point 피처의 totalDistance · totalTime, LineString 좌표를 순서대로 이어 붙인다 */
	private LegResult pedestrian(double sLng, double sLat, double eLng, double eLat) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("startX", sLng);
		body.put("startY", sLat);
		body.put("endX", eLng);
		body.put("endY", eLat);
		body.put("reqCoordType", "WGS84GEO");
		body.put("resCoordType", "WGS84GEO");
		body.put("startName", enc("출발"));                // 필수값. TMAP 은 이름을 URL 인코딩해서 받는다
		body.put("endName", enc("도착"));
		body.put("searchOption", "0");                     // 0 = 추천
		String json;
		try { json = JSON.writeValueAsString(body); } catch (Exception e) { throw new IllegalStateException(e); }

		HttpRequest req = HttpRequest.newBuilder(URI.create(WALK_URL))
				.header("appKey", appKey).header("Accept", "application/json").header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();

		JsonNode root;
		try {
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode() + " " + res.body());
			root = JSON.readTree(res.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("중단됨");
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e.toString());
		}

		LegResult r = new LegResult();
		for (JsonNode f : root.path("features")) {
			JsonNode g = f.path("geometry"), p = f.path("properties");
			if (p.has("totalDistance")) {
				r.distance = p.path("totalDistance").asInt();
				r.time = p.path("totalTime").asInt();
			}
			if ("LineString".equals(g.path("type").asText())) {
				for (JsonNode c : g.path("coordinates")) addPoint(r.path, new double[] { c.get(0).asDouble(), c.get(1).asDouble() });
			}
		}
		if (r.path.isEmpty()) throw new IllegalStateException("경로 좌표 없음");
		return r;
	}

	/** 직선 대신 : 거리 = 직선 × 1.3, 시간 = 도보 4km/h (field-map.js 의 기존 계산과 같은 기준) */
	private static LegResult straight(double sLng, double sLat, double eLng, double eLat, double straightM) {
		LegResult r = new LegResult();
		r.straight = true;
		r.distance = (int) Math.round(straightM * 1.3);
		r.time = (int) Math.round(r.distance / (4000.0 / 3600));
		r.path.add(new double[] { sLng, sLat });
		r.path.add(new double[] { eLng, eLat });
		return r;
	}

	/** 구간 경계의 같은 점은 한 번만 */
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

	private static class LegResult {
		int distance, time;
		boolean straight;
		List<double[]> path = new ArrayList<>();
	}
}
