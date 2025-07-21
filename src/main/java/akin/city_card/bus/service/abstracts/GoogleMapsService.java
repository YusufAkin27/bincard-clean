package akin.city_card.bus.service.abstracts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleMapsService {

    @Value("${google.maps.api.key}")
    private String apiKey;

    @Value("${google.maps.api.url:https://maps.googleapis.com/maps/api/directions/json}")
    private String directionsApiUrl;

    private final RestTemplate restTemplate;

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
                !apiResponse.getRoutes().isEmpty()) {
                
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
}

// ===== GOOGLE MAPS API RESPONSE CLASSES =====

@lombok.Data
class GoogleMapsApiResponse {
    private String status;
    private java.util.List<Route> routes;
    private String error_message;
}

@lombok.Data
class Route {
    private java.util.List<Leg> legs;
    private String summary;
}

@lombok.Data
class Leg {
    private Duration duration;
    private Duration duration_in_traffic;
    private Distance distance;
    private String start_address;
    private String end_address;
}

@lombok.Data
class Duration {
    private String text;
    private int value; // seconds
}

@lombok.Data
class Distance {
    private String text;
    private int value; // meters
}

// ===== RESPONSE WRAPPER =====

@lombok.Data
@lombok.Builder
class GoogleMapsResponse {
    private boolean success;
    private int durationMinutes;
    private int durationInTrafficMinutes;
    private int distanceMeters;
    private LocalDateTime requestTime;
}

