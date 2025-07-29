package akin.city_card.simulation;

import akin.city_card.bus.model.Bus;
import akin.city_card.bus.repository.BusRepository;
import akin.city_card.paymentPoint.model.Location;
import akin.city_card.route.model.RouteDirection;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.station.model.Station;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true", matchIfMissing = false)
public class BusSimulationService {

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${simulation.thread-pool-size:20}")
    private int threadPoolSize;

    @Value("${simulation.update-interval-seconds:2}")
    private int updateIntervalSeconds;

    @Value("${simulation.auto-start:false}")
    private boolean autoStart;

    private ScheduledExecutorService executorService;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> runningSimulations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, BusSimulatorTask> simulatorTasks = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    @PostConstruct
    public void initializeService() {
        log.info("Initializing Bus Simulation Service...");
        initializeExecutorService();

        if (autoStart) {
            log.info("Auto-start enabled, starting all bus simulations...");
            startAllBusSimulations();
        }
    }

    private void initializeExecutorService() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    log.info("Creating ScheduledExecutorService with {} threads", threadPoolSize);
                    executorService = Executors.newScheduledThreadPool(threadPoolSize);
                    initialized = true;
                }
            }
        }
    }

    @PreDestroy
    public void shutdownSimulation() {
        log.info("Shutting down Bus Location Simulation Service...");
        stopAllSimulations();

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("ExecutorService did not terminate gracefully, forcing shutdown...");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for executor termination");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Bus simulation service shutdown completed");
    }

    @Transactional(readOnly = true)
    public void startAllBusSimulations() {
        try {
            initializeExecutorService();

            // Find active buses (note: using isActiveFalse might be a typo in original)
            // Assuming you want active buses: isActiveTrue and isDeletedFalse
            List<Bus> activeBuses = busRepository.findAllByIsActiveTrueAndIsDeletedFalse();
            log.info("Found {} active buses for simulation", activeBuses.size());

            int startedCount = 0;
            int skippedCount = 0;

            for (Bus bus : activeBuses) {
                try {
                    if (canStartSimulation(bus)) {
                        startBusSimulation(bus);
                        startedCount++;
                    } else {
                        skippedCount++;
                        log.debug("Skipped simulation for bus {} ({}): No valid route configuration",
                                bus.getId(), bus.getNumberPlate());
                    }
                } catch (Exception e) {
                    skippedCount++;
                    log.error("Failed to start simulation for bus: {} ({})",
                            bus.getId(), bus.getNumberPlate(), e);
                }
            }

            log.info("Simulation startup completed - Started: {}, Skipped: {}, Total Active: {}",
                    startedCount, skippedCount, getActiveSimulationCount());

        } catch (Exception e) {
            log.error("Failed to start bus simulations", e);
            throw new RuntimeException("Failed to start all bus simulations", e);
        }
    }

    private boolean canStartSimulation(Bus bus) {
        if (bus.getCurrentDirection() == null) {
            log.debug("Bus {} has no current direction assigned", bus.getId());
            return false;
        }

        if (bus.getAssignedRoute() == null) {
            log.debug("Bus {} has no assigned route", bus.getId());
            return false;
        }

        List<RouteStationNode> nodes = bus.getCurrentDirection().getStationNodes();
        if (nodes == null || nodes.isEmpty()) {
            log.debug("Bus {} has no station nodes in current direction", bus.getId());
            return false;
        }

        boolean hasValidNodes = nodes.stream().anyMatch(node ->
                node.isActive() &&
                        node.getFromStation() != null && node.getFromStation().isActive() &&
                        node.getToStation() != null && node.getToStation().isActive() &&
                        node.getFromStation().getLocation() != null &&
                        node.getToStation().getLocation() != null
        );

        if (!hasValidNodes) {
            log.debug("Bus {} has no valid station nodes for simulation", bus.getId());
            return false;
        }

        return true;
    }

    @Transactional(readOnly = true)
    public void startBusSimulation(Bus bus) {
        try {
            initializeExecutorService();

            Long busId = bus.getId();
            String plate = bus.getNumberPlate();

            if (runningSimulations.containsKey(busId)) {
                log.warn("⏱️ Simulation already running for bus: {} ({})", busId, plate);
                return;
            }

            if (!canStartSimulation(bus)) {
                throw new IllegalStateException("Bus is not suitable for simulation");
            }

            RouteDirection direction = bus.getCurrentDirection();
            List<RouteStationNode> nodes = direction.getStationNodes();

            // Force-fetch all related entities to avoid lazy loading issues
            preloadStationData(nodes);

            // Create and store the simulator task
            BusSimulatorTask task = new BusSimulatorTask(
                    bus,
                    nodes,
                    restTemplate,
                    0, // speedKmh not used anymore, handled internally
                    updateIntervalSeconds
            );

            // Schedule the task
            ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                    task,
                    0,
                    updateIntervalSeconds,
                    TimeUnit.SECONDS
            );

            // Store both the future and the task
            runningSimulations.put(busId, future);
            simulatorTasks.put(busId, task);

            log.info("✅ Started simulation for bus: {} ({}) on route: {} - Direction: {}",
                    busId, plate, bus.getRouteDisplayName(), direction.getName());

        } catch (Exception e) {
            log.error("❌ Error while starting simulation for bus: {} ({})",
                    bus.getId(), bus.getNumberPlate() != null ? bus.getNumberPlate() : "N/A", e);
            throw new RuntimeException("Failed to start simulation for bus: " + bus.getId(), e);
        }
    }

    private void preloadStationData(List<RouteStationNode> nodes) {
        for (RouteStationNode node : nodes) {
            try {
                // Force loading of station data
                Station fromStation = node.getFromStation();
                Station toStation = node.getToStation();

                if (fromStation != null) {
                    fromStation.getName(); // Force load
                    Location fromLocation = fromStation.getLocation();
                    if (fromLocation != null) {
                        fromLocation.getLatitude(); // Force load
                        fromLocation.getLongitude();
                    }
                }

                if (toStation != null) {
                    toStation.getName(); // Force load
                    Location toLocation = toStation.getLocation();
                    if (toLocation != null) {
                        toLocation.getLatitude(); // Force load
                        toLocation.getLongitude();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to preload station data for node: {}", node.getId(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public void startBusSimulationById(Long busId) {
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new RuntimeException("Bus not found: " + busId));

        if (!bus.isActive() || bus.isDeleted()) {
            throw new RuntimeException("Bus is not active or deleted: " + busId);
        }

        startBusSimulation(bus);
    }

    public void stopBusSimulation(Long busId) {
        ScheduledFuture<?> future = runningSimulations.remove(busId);
        BusSimulatorTask task = simulatorTasks.remove(busId);

        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.info("Stopped simulation for bus: {}", busId);
        } else {
            log.warn("No running simulation found for bus: {}", busId);
        }
    }

    public void stopAllSimulations() {
        log.info("Stopping {} active simulations...", runningSimulations.size());

        runningSimulations.forEach((busId, future) -> {
            if (!future.isCancelled()) {
                future.cancel(false);
            }
        });

        runningSimulations.clear();
        simulatorTasks.clear();
        log.info("Stopped all bus simulations");
    }

    @Transactional(readOnly = true)
    public void restartBusSimulation(Long busId) {
        stopBusSimulation(busId);

        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new RuntimeException("Bus not found: " + busId));

        if (bus.isActive() && !bus.isDeleted()) {
            startBusSimulation(bus);
        }
    }

    public boolean isSimulationRunning(Long busId) {
        ScheduledFuture<?> future = runningSimulations.get(busId);
        return future != null && !future.isCancelled() && !future.isDone();
    }

    public int getActiveSimulationCount() {
        return (int) runningSimulations.values().stream()
                .filter(future -> !future.isCancelled() && !future.isDone())
                .count();
    }

    public BusSimulationStatus getSimulationStatus(Long busId) {
        BusSimulatorTask task = simulatorTasks.get(busId);
        boolean isRunning = isSimulationRunning(busId);

        if (task != null && isRunning) {
            return BusSimulationStatus.builder()
                    .busId(busId)
                    .isRunning(true)
                    .currentNodeIndex(task.getCurrentNodeIndex())
                    .currentProgress(task.getCurrentProgress())
                    .isAtStation(task.isAtStation())
                    .currentRouteSegment(task.getCurrentRouteSegment())
                    .build();
        } else {
            return BusSimulationStatus.builder()
                    .busId(busId)
                    .isRunning(false)
                    .build();
        }
    }

    // Inner class for status reporting
    public static class BusSimulationStatus {
        private Long busId;
        private boolean isRunning;
        private int currentNodeIndex;
        private double currentProgress;
        private boolean isAtStation;
        private String currentRouteSegment;

        // Builder pattern
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final BusSimulationStatus status = new BusSimulationStatus();

            public Builder busId(Long busId) {
                status.busId = busId;
                return this;
            }

            public Builder isRunning(boolean isRunning) {
                status.isRunning = isRunning;
                return this;
            }

            public Builder currentNodeIndex(int currentNodeIndex) {
                status.currentNodeIndex = currentNodeIndex;
                return this;
            }

            public Builder currentProgress(double currentProgress) {
                status.currentProgress = currentProgress;
                return this;
            }

            public Builder isAtStation(boolean isAtStation) {
                status.isAtStation = isAtStation;
                return this;
            }

            public Builder currentRouteSegment(String currentRouteSegment) {
                status.currentRouteSegment = currentRouteSegment;
                return this;
            }

            public BusSimulationStatus build() {
                return status;
            }
        }

        // Getters
        public Long getBusId() { return busId; }
        public boolean isRunning() { return isRunning; }
        public int getCurrentNodeIndex() { return currentNodeIndex; }
        public double getCurrentProgress() { return currentProgress; }
        public boolean isAtStation() { return isAtStation; }
        public String getCurrentRouteSegment() { return currentRouteSegment; }
    }
}