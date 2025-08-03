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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class GeoIpService {

    private static final Logger logger = LoggerFactory.getLogger(GeoIpService.class);

    private static final String API_URL = "https://ipapi.co/%s/json/";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.geoip.cache.ttl-minutes:1440}") // Default 1 gün (1440 dakika)
    private long cacheTtlMinutes;

    public GeoLocationData getGeoData(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            logger.warn("IP address is null or empty");
            return null;
        }

        String cacheKey = "geoip:" + ipAddress;

        try {
            // 1. Önce Redis'ten cache'i oku
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                logger.debug("Cache hit for IP: {}", ipAddress);
                return objectMapper.readValue(cachedJson, GeoLocationData.class);
            }

            // 2. Cache yoksa API'den veri çek
            String url = String.format(API_URL, ipAddress);
            logger.debug("Fetching GeoIP data from API for IP: {}", ipAddress);

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(url);
                request.addHeader("Accept", "application/json");

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getCode();
                    if (statusCode != 200) {
                        logger.error("GeoIP API returned non-OK status: {}", statusCode);
                        return null;
                    }

                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        logger.error("GeoIP API response entity is null");
                        return null;
                    }

                    String result = EntityUtils.toString(entity);
                    GeoLocationData geoData = objectMapper.readValue(result, GeoLocationData.class);

                    // 3. Redis cache'e yaz (TTL ile)
                    redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(cacheTtlMinutes));
                    logger.debug("GeoIP data cached for IP: {}", ipAddress);

                    return geoData;
                }
            }

        } catch (ParseException e) {
            logger.error("Failed to parse GeoIP API response", e);
        } catch (IOException e) {
            logger.error("IOException during GeoIP data fetch", e);
        } catch (Exception e) {
            logger.error("Unexpected exception in GeoIpService", e);
        }

        return null;
    }
}
