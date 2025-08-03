package akin.city_card.geoIpService;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class GeoIpService {

    private static final String API_URL = "https://ipapi.co/%s/json/";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.geoip.cache.ttl-minutes:1440}") // default 1 gün
    private long cacheTtlMinutes;

    public GeoLocationData getGeoData(String ipAddress) {
        String cacheKey = "geoip:" + ipAddress;

        try {
            // 1. Önce Redis'ten oku
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, GeoLocationData.class);
            }

            // 2. Değilse API'den veri çek
            String url = String.format(API_URL, ipAddress);
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(url);
                request.addHeader("Accept", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        String result = EntityUtils.toString(entity);
                        GeoLocationData geoData = objectMapper.readValue(result, GeoLocationData.class);

                        // 3. Redis'e yaz
                        redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(cacheTtlMinutes));

                        return geoData;
                    }
                } catch (ParseException e) {
                    throw new RuntimeException("GeoIP yanıtı parse edilemedi", e);
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // Devam et ama fallback yapma
        }

        return null;
    }
}
