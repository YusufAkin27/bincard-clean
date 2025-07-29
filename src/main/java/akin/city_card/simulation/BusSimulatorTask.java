package akin.city_card.simulation;

import akin.city_card.admin.core.request.UpdateLocationRequest;
import akin.city_card.bus.model.Bus;
import akin.city_card.route.model.RouteDirection;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.station.model.Station;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Slf4j
public class BusSimulatorTask implements Runnable {

    private final Bus bus;
    private List<RouteStationNode> currentRouteNodes;
    private final RestTemplate restTemplate;
    private final int updateIntervalSeconds;

    // Movement state
    private int currentNodeIndex = 0;
    private double currentProgress = 0.0; // 0.0 to 1.0 between stations
    private boolean isAtStation = false;
    private LocalDateTime stationArrivalTime;
    private boolean isDirectionSwitched = false;

    // Speed configuration (km/h)
    private static final double MIN_SPEED_KMH = 35.0;
    private static final double MAX_SPEED_KMH = 40.0;
    private static final double STATION_STOP_DURATION_MINUTES = 1.0;

    // Randomization
    private final Random random = new Random();
    private static final double SPEED_VARIATION = 0.15; // ±15% speed variation
    private static final double POSITION_NOISE = 0.000005; // GPS noise

    public BusSimulatorTask(Bus bus, List<RouteStationNode> routeNodes,
                            RestTemplate restTemplate, double speedKmh, int updateIntervalSeconds) {
        this.bus = bus;
        this.restTemplate = restTemplate;
        this.updateIntervalSeconds = updateIntervalSeconds;

        // Initialize with current direction
        initializeRoute(bus.getCurrentDirection());

        if (!this.currentRouteNodes.isEmpty()) {
            // Start from a random position for variety
            this.currentNodeIndex = random.nextInt(this.currentRouteNodes.size());
            this.currentProgress = random.nextDouble() * 0.3; // Start closer to beginning of segment
            log.info("Initialized bus {} simulation with {} nodes, starting at node {}",
                    bus.getId(), this.currentRouteNodes.size(), currentNodeIndex);
        }
    }

    private void initializeRoute(RouteDirection direction) {
        if (direction == null || direction.getStationNodes() == null) {
            this.currentRouteNodes = List.of();
            log.warn("No route direction or nodes found for bus: {}", bus.getId());
            return;
        }

        this.currentRouteNodes = direction.getStationNodes().stream()
                .filter(node -> node.getFromStation() != null &&
                        node.getToStation() != null &&
                        node.getFromStation().getLocation() != null &&
                        node.getToStation().getLocation() != null &&
                        node.isActive())
                .sorted(Comparator.comparingInt(RouteStationNode::getSequenceOrder))
                .toList();

        log.debug("Loaded {} valid route nodes for direction: {}",
                this.currentRouteNodes.size(), direction.getName());
    }

    @Override
    public void run() {
        try {
            if (currentRouteNodes.isEmpty()) {
                log.warn("No route nodes available for bus: {}", bus.getId());
                return;
            }

            // Check if we're at a station and need to wait
            if (isAtStation) {
                handleStationStop();
                return;
            }

            RouteStationNode currentNode = getCurrentNode();
            if (currentNode == null) {
                log.error("Current node is null for bus: {}", bus.getId());
                moveToNextNode();
                return;
            }

            Station fromStation = currentNode.getFromStation();
            Station toStation = currentNode.getToStation();

            if (!isValidStationPair(fromStation, toStation)) {
                log.warn("Invalid station data for bus: {} at node: {}", bus.getId(), currentNodeIndex);
                moveToNextNode();
                return;
            }

            // Calculate and send current position
            double[] currentPosition = calculateCurrentPosition(fromStation, toStation, currentProgress);
            double lat = currentPosition[0] + (random.nextGaussian() * POSITION_NOISE);
            double lon = currentPosition[1] + (random.nextGaussian() * POSITION_NOISE);

            sendLocationUpdate(lat, lon, currentPosition[2]); // currentPosition[2] is speed

            // Update movement progress
            updateMovementProgress(fromStation, toStation);

        } catch (Exception e) {
            log.error("Error in bus simulation task for bus: {}", bus.getId(), e);
        }
    }

    private void handleStationStop() {
        if (stationArrivalTime == null) {
            stationArrivalTime = LocalDateTime.now();
            log.debug("Bus {} arrived at station, stopping for {} minutes",
                    bus.getId(), STATION_STOP_DURATION_MINUTES);
            return;
        }

        // Check if stop duration has passed
        LocalDateTime now = LocalDateTime.now();
        long minutesSinceArrival = java.time.Duration.between(stationArrivalTime, now).toMinutes();

        if (minutesSinceArrival >= STATION_STOP_DURATION_MINUTES) {
            // Resume movement
            isAtStation = false;
            stationArrivalTime = null;
            moveToNextNode();
            log.debug("Bus {} resuming journey from station", bus.getId());
        }
    }

    private RouteStationNode getCurrentNode() {
        if (currentNodeIndex >= 0 && currentNodeIndex < currentRouteNodes.size()) {
            return currentRouteNodes.get(currentNodeIndex);
        }
        return null;
    }

    private boolean isValidStationPair(Station from, Station to) {
        return from != null && to != null &&
                from.getLocation() != null && to.getLocation() != null &&
                from.isActive() && to.isActive();
    }

    private double[] calculateCurrentPosition(Station from, Station to, double progress) {
        double fromLat = from.getLocation().getLatitude();
        double fromLon = from.getLocation().getLongitude();
        double toLat = to.getLocation().getLatitude();
        double toLon = to.getLocation().getLongitude();

        // Linear interpolation between stations
        double currentLat = fromLat + progress * (toLat - fromLat);
        double currentLon = fromLon + progress * (toLon - fromLon);

        // Calculate current speed with variation
        double baseSpeed = MIN_SPEED_KMH + random.nextDouble() * (MAX_SPEED_KMH - MIN_SPEED_KMH);
        double currentSpeed = baseSpeed * (1.0 + (random.nextGaussian() * SPEED_VARIATION));
        currentSpeed = Math.max(currentSpeed, MIN_SPEED_KMH * 0.7); // Minimum speed limit

        return new double[]{currentLat, currentLon, currentSpeed};
    }

    private void updateMovementProgress(Station from, Station to) {
        // Calculate distance between stations
        double distanceKm = haversine(
                from.getLocation().getLatitude(), from.getLocation().getLongitude(),
                to.getLocation().getLatitude(), to.getLocation().getLongitude()
        );

        // If distance is very small, immediately move to next station
        if (distanceKm < 0.01) {
            arrivedAtStation();
            return;
        }

        // Use node's estimated travel time if available, otherwise calculate
        double segmentTravelTimeMinutes;
        RouteStationNode currentNode = getCurrentNode();

        if (currentNode != null && currentNode.getEstimatedTravelTimeMinutes() != null) {
            segmentTravelTimeMinutes = currentNode.getEstimatedTravelTimeMinutes();
        } else {
            // Calculate based on average speed and distance
            double averageSpeedKmh = (MIN_SPEED_KMH + MAX_SPEED_KMH) / 2.0;
            segmentTravelTimeMinutes = (distanceKm / averageSpeedKmh) * 60.0;
        }

        // Calculate progress increment based on real time
        double totalTravelTimeSeconds = segmentTravelTimeMinutes * 60.0;
        double progressIncrement = updateIntervalSeconds / totalTravelTimeSeconds;

        // Add some speed variation to progress
        double speedFactor = 0.85 + (random.nextDouble() * 0.3); // 0.85 to 1.15 variation
        progressIncrement *= speedFactor;

        currentProgress += progressIncrement;

        // Check if we've reached the destination station
        if (currentProgress >= 1.0) {
            arrivedAtStation();
        }
    }

    private void arrivedAtStation() {
        currentProgress = 1.0; // Ensure we're exactly at the station
        isAtStation = true;
        stationArrivalTime = null; // Will be set in handleStationStop()

        RouteStationNode currentNode = getCurrentNode();
        if (currentNode != null) {
            log.debug("Bus {} arrived at station: {} -> {}",
                    bus.getId(),
                    currentNode.getFromStation().getName(),
                    currentNode.getToStation().getName());
        }
    }

    private void moveToNextNode() {
        currentProgress = 0.0;
        currentNodeIndex++;

        // Check if we've reached the end of current route
        if (currentNodeIndex >= currentRouteNodes.size()) {
            switchDirection();
        }

        if (currentNodeIndex < currentRouteNodes.size()) {
            RouteStationNode nextNode = currentRouteNodes.get(currentNodeIndex);
            log.debug("Bus {} moving to next segment: {} -> {}",
                    bus.getId(),
                    nextNode.getFromStation().getName(),
                    nextNode.getToStation().getName());
        }
    }

    private void switchDirection() {
        try {
            log.info("Bus {} reached end of route, switching direction", bus.getId());

            // Switch direction in the bus entity
            if (bus.getAssignedRoute() != null) {
                RouteDirection currentDirection = bus.getCurrentDirection();
                RouteDirection newDirection;

                if (currentDirection.equals(bus.getAssignedRoute().getOutgoingDirection())) {
                    newDirection = bus.getAssignedRoute().getReturnDirection();
                } else {
                    newDirection = bus.getAssignedRoute().getOutgoingDirection();
                }

                if (newDirection != null) {
                    // Update bus direction (this would normally be persisted to database)
                    bus.setCurrentDirection(newDirection);

                    // Reinitialize route with new direction
                    initializeRoute(newDirection);
                    currentNodeIndex = 0;
                    currentProgress = 0.0;
                    isDirectionSwitched = true;

                    log.info("Bus {} switched to direction: {}", bus.getId(), newDirection.getName());
                } else {
                    log.error("Could not find return direction for bus: {}", bus.getId());
                    currentNodeIndex = 0; // Reset to beginning
                }
            }
        } catch (Exception e) {
            log.error("Error switching direction for bus: {}", bus.getId(), e);
            currentNodeIndex = 0; // Reset to beginning as fallback
        }
    }

    private void sendLocationUpdate(double lat, double lon, double speed) {
        try {
            UpdateLocationRequest requestBody = new UpdateLocationRequest();
            requestBody.setLatitude(lat);
            requestBody.setLongitude(lon);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UpdateLocationRequest> entity = new HttpEntity<>(requestBody, headers);

            String url = "http://localhost:8080/v1/api/bus/" + bus.getId() + "/location";

            restTemplate.postForEntity(url, entity, Void.class);

            if (log.isDebugEnabled()) {
                RouteStationNode currentNode = getCurrentNode();
                String routeInfo = currentNode != null ?
                        String.format("%s -> %s",
                                currentNode.getFromStation().getName(),
                                currentNode.getToStation().getName()) : "Unknown";

                log.debug("Bus {} location updated - Lat: {}, Lon: {}, Speed: {:.1f} km/h, Progress: {:.1f}%, Route: {}",
                        bus.getId(),
                        String.format("%.6f", lat),
                        String.format("%.6f", lon),
                        speed,
                        currentProgress * 100,
                        routeInfo);
            }

        } catch (Exception e) {
            log.error("Failed to send location update for bus: {}", bus.getId(), e);
        }
    }

    /**
     * Calculate distance between two points using Haversine formula
     */
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

    public int getCurrentNodeIndex() {
        return currentNodeIndex;
    }

    public double getCurrentProgress() {
        return currentProgress;
    }

    public boolean isAtStation() {
        return isAtStation;
    }

    public String getCurrentRouteSegment() {
        RouteStationNode currentNode = getCurrentNode();
        if (currentNode != null) {
            return currentNode.getFromStation().getName() + " -> " + currentNode.getToStation().getName();
        }
        return "Unknown";
    }
}