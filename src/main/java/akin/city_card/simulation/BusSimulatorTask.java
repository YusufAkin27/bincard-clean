package akin.city_card.simulation;

import akin.city_card.admin.core.request.UpdateLocationRequest;
import akin.city_card.bus.model.Bus;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.station.model.Station;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Slf4j
public class BusSimulatorTask implements Runnable {

    private final Bus bus;
    private final List<RouteStationNode> routeNodes;
    private final RestTemplate restTemplate;
    private final double speedKmh;
    private final int updateIntervalSeconds;

    private int currentNodeIndex = 0;
    private double currentProgress = 0.0; // 0.0 to 1.0 between stations
    private final Random random = new Random();

    // Speed and movement variation
    private static final double SPEED_VARIATION = 0.2; // ±20% speed variation
    private static final double POSITION_NOISE = 0.00001; // Small GPS noise

    public BusSimulatorTask(Bus bus, List<RouteStationNode> routeNodes,
                            RestTemplate restTemplate, double speedKmh, int updateIntervalSeconds) {
        this.bus = bus;
        this.restTemplate = restTemplate;
        this.speedKmh = speedKmh;
        this.updateIntervalSeconds = updateIntervalSeconds;

        // Filter and sort valid route nodes
        this.routeNodes = routeNodes.stream()
                .filter(node -> node.getFromStation() != null &&
                        node.getToStation() != null &&
                        node.getFromStation().getLocation() != null &&
                        node.getToStation().getLocation() != null)
                .sorted(Comparator.comparingInt(RouteStationNode::getSequenceOrder))
                .toList();

        if (this.routeNodes.isEmpty()) {
            log.warn("No valid route nodes found for bus: {}", bus.getId());
        } else {
            // Initialize with random starting position
            this.currentNodeIndex = random.nextInt(this.routeNodes.size());
            this.currentProgress = random.nextDouble();
            log.debug("Initialized bus {} simulation with {} nodes", bus.getId(), this.routeNodes.size());
        }
    }

    @Override
    public void run() {
        try {
            if (routeNodes.isEmpty()) {
                log.warn("No route nodes available for bus: {}", bus.getId());
                return;
            }

            RouteStationNode currentNode = getCurrentNode();
            if (currentNode == null) {
                log.error("Current node is null for bus: {}", bus.getId());
                return;
            }

            Station fromStation = currentNode.getFromStation();
            Station toStation = currentNode.getToStation();

            if (fromStation == null || toStation == null ||
                    fromStation.getLocation() == null || toStation.getLocation() == null) {
                log.warn("Invalid station data for bus: {} at node: {}", bus.getId(), currentNodeIndex);
                moveToNextNode();
                return;
            }

            // Calculate current position
            double[] currentPosition = calculateCurrentPosition(fromStation, toStation, currentProgress);

            // Add small GPS noise for realism
            double lat = currentPosition[0] + (random.nextGaussian() * POSITION_NOISE);
            double lon = currentPosition[1] + (random.nextGaussian() * POSITION_NOISE);

            // Send location update
            sendLocationUpdate(lat, lon);

            // Update progress
            updateProgress(fromStation, toStation);

        } catch (Exception e) {
            log.error("Error in bus simulation task for bus: {}", bus.getId(), e);
        }
    }

    private RouteStationNode getCurrentNode() {
        if (currentNodeIndex >= 0 && currentNodeIndex < routeNodes.size()) {
            return routeNodes.get(currentNodeIndex);
        }
        return null;
    }

    private double[] calculateCurrentPosition(Station from, Station to, double progress) {
        double fromLat = from.getLocation().getLatitude();
        double fromLon = from.getLocation().getLongitude();
        double toLat = to.getLocation().getLatitude();
        double toLon = to.getLocation().getLongitude();

        // Linear interpolation between stations
        double currentLat = fromLat + progress * (toLat - fromLat);
        double currentLon = fromLon + progress * (toLon - fromLon);

        return new double[]{currentLat, currentLon};
    }

    private void updateProgress(Station from, Station to) {
        double distanceKm = haversine(
                from.getLocation().getLatitude(), from.getLocation().getLongitude(),
                to.getLocation().getLatitude(), to.getLocation().getLongitude()
        );

        if (distanceKm < 0.01) {
            currentProgress = 1.0;
            moveToNextNode();
            return;
        }

        double currentSpeedKmh = speedKmh * (1.0 + (random.nextGaussian() * SPEED_VARIATION));
        currentSpeedKmh = Math.max(currentSpeedKmh, speedKmh * 0.3); // min %30

        double speedMps = currentSpeedKmh * 1000.0 / 3600.0; // m/s

        double distanceMeters = distanceKm * 1000.0;

        double traveledMeters = speedMps * updateIntervalSeconds;

        double progressIncrement = traveledMeters / distanceMeters;
        currentProgress += progressIncrement;

        if (currentProgress >= 1.0) {
            currentProgress = 0.0;
            moveToNextNode();
        }
    }

    private void moveToNextNode() {
        currentNodeIndex = (currentNodeIndex + 1) % routeNodes.size();

        log.debug("Bus {} moved to node {}",
                bus.getId(), currentNodeIndex);
    }

    private void sendLocationUpdate(double lat, double lon) {
        try {
            UpdateLocationRequest requestBody = new UpdateLocationRequest();
            requestBody.setLatitude(lat);
            requestBody.setLongitude(lon);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UpdateLocationRequest> entity = new HttpEntity<>(requestBody, headers);

            String url = "http://localhost:8080/v1/api/bus/" + bus.getId() + "/location";

            restTemplate.postForEntity(url, entity, Void.class);

            log.debug("Location updated for bus: {} - Lat: {}, Lon: {}",
                    bus.getId(), String.format("%.6f", lat), String.format("%.6f", lon));

        } catch (Exception e) {
            log.error("Failed to send location update for bus: {}", bus.getId(), e);
        }
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // Earth radius in kilometers

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}