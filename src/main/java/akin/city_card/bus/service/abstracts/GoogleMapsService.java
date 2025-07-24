package akin.city_card.bus.service.abstracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleMapsService {

    @Value("${google.maps.api.key}")
    private String apiKey;

    // Directions API URL (sürüş tarifleri için)
    @Value("${google.maps.api.directions.url:https://maps.googleapis.com/maps/api/directions/json}")
    private String directionsApiUrl;

    // Geocoding API URL (adres -> koordinat için)
    @Value("${google.maps.api.geocode.url:https://maps.googleapis.com/maps/api/geocode/json}")
    private String geocodeApiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Google Maps Directions API kullanarak iki nokta arası seyahat süresini hesaplar
     * Trafik durumunu da hesaba katar
     */
    public GoogleMapsResponse getDirections(double originLat, double originLng,
                                            double destLat, double destLng) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(directionsApiUrl)
                    .queryParam("origin", originLat + "," + originLng)
                    .queryParam("destination", destLat + "," + destLng)
                    .queryParam("mode", "driving")
                    .queryParam("departure_time", "now") // Gerçek zamanlı trafik için
                    .queryParam("traffic_model", "best_guess")
                    .queryParam("key", apiKey)
                    .queryParam("language", "tr")
                    .build()
                    .toUriString();

            log.debug("Google Maps API request: {}", url);

            GoogleMapsApiResponse apiResponse = restTemplate.getForObject(url, GoogleMapsApiResponse.class);

            if (apiResponse != null && "OK".equals(apiResponse.getStatus()) &&
                    apiResponse.getRoutes() != null && !apiResponse.getRoutes().isEmpty()) {

                Route route = apiResponse.getRoutes().get(0);
                Leg leg = route.getLegs().get(0);

                return GoogleMapsResponse.builder()
                        .success(true)
                        .durationMinutes((int) Math.ceil(leg.getDuration().getValue() / 60.0))
                        .durationInTrafficMinutes(leg.getDuration_in_traffic() != null ?
                                (int) Math.ceil(leg.getDuration_in_traffic().getValue() / 60.0) :
                                (int) Math.ceil(leg.getDuration().getValue() / 60.0))
                        .distanceMeters(leg.getDistance().getValue())
                        .requestTime(LocalDateTime.now())
                        .build();
            } else {
                log.warn("Google Maps API returned error: {}",
                        apiResponse != null ? apiResponse.getStatus() : "null response");
                return GoogleMapsResponse.builder()
                        .success(false)
                        .requestTime(LocalDateTime.now())
                        .build();
            }

        } catch (Exception e) {
            log.error("Error calling Google Maps API", e);
            return GoogleMapsResponse.builder()
                    .success(false)
                    .requestTime(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Google Geocoding API kullanarak adresin koordinatlarını alır
     */
    public LatLng getCoordinatesFromAddress(String address) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(geocodeApiUrl)
                    .queryParam("address", address)
                    .queryParam("key", apiKey)
                    .build()
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            String status = root.path("status").asText();
            if (!"OK".equals(status)) {
                log.warn("Google Geocoding API returned status: {}", status);
                return null;
            }

            JsonNode locationNode = root.path("results").get(0).path("geometry").path("location");
            double lat = locationNode.path("lat").asDouble();
            double lng = locationNode.path("lng").asDouble();

            return new LatLng(lat, lng);

        } catch (Exception e) {
            log.error("Error during Google Maps Geocoding API call", e);
            return null;
        }
    }

    public record LatLng(double lat, double lng) {}

    // ----- INNER CLASSES -----

    @Data
    static class GoogleMapsApiResponse {
        private String status;
        private List<Route> routes;
        private String error_message;
    }

    @Data
    static class Route {
        private List<Leg> legs;
        private String summary;
    }

    @Data
    static class Leg {
        private Duration duration;
        private Duration duration_in_traffic;
        private Distance distance;
        private String start_address;
        private String end_address;
    }

    @Data
    static class Duration {
        private String text;
        private int value; // saniye cinsinden
    }

    @Data
    static class Distance {
        private String text;
        private int value; // metre cinsinden
    }

    @Data
    @Builder
    static class GoogleMapsResponse {
        private boolean success;
        private int durationMinutes;
        private int durationInTrafficMinutes;
        private int distanceMeters;
        private LocalDateTime requestTime;
    }
}
