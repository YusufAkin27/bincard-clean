package akin.city_card.simulation;

import akin.city_card.bus.model.Bus;
import akin.city_card.bus.repository.BusRepository;
import akin.city_card.paymentPoint.model.Location;
import akin.city_card.route.model.RouteDirection;
import akin.city_card.route.model.RouteStationNode;
import akin.city_card.station.model.Station;
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

    @Value("${simulation.thread-pool-size:10}")
    private int threadPoolSize;

    @Value("${simulation.update-interval-seconds:2}")
    private int updateIntervalSeconds;

    @Value("${simulation.bus-speed-kmh:40}")
    private double busSpeedKmh;

    private ScheduledExecutorService executorService;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> runningSimulations = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    private void initializeExecutorService() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    log.info("Initializing Bus Simulation ExecutorService...");
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
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
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

            List<Bus> activeBuses = busRepository.findAllByIsActiveFalseAndIsDeletedFalse();
            log.info("Starting simulation for {} active buses", activeBuses.size());

            for (Bus bus : activeBuses) {
                try {
                    startBusSimulation(bus);
                } catch (Exception e) {
                    log.error("Failed to start simulation for bus: {} ({})", bus.getId(), bus.getNumberPlate(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to start bus simulations", e);
        }
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

            RouteDirection direction = bus.getCurrentDirection();
            if (direction == null) {
                log.warn("🚫 Bus {} ({}) has no assigned outgoing route.", busId, plate);
                return;
            }

            List<RouteStationNode> nodes = direction.getStationNodes();
            if (nodes == null || nodes.isEmpty()) {
                log.warn("🚫 No station nodes found for bus: {} ({})", busId, plate);
                return;
            }

            // Force-fetch locations for lazy-loading
            nodes.forEach(node -> {
                Optional.ofNullable(node.getFromStation())
                        .map(Station::getLocation)
                        .ifPresent(Location::getLatitude);
                Optional.ofNullable(node.getToStation())
                        .map(Station::getLocation)
                        .ifPresent(Location::getLatitude);
            });

            // Check if there's at least one valid node
            boolean hasValidNodes = nodes.stream().anyMatch(node ->
                    node.getFromStation() != null && node.getFromStation().getLocation() != null &&
                            node.getToStation() != null && node.getToStation().getLocation() != null
            );

            if (!hasValidNodes) {
                log.warn("🚫 No valid route nodes for simulation. Bus: {} ({})", busId, plate);
                return;
            }

            BusSimulatorTask task = new BusSimulatorTask(
                    bus,
                    nodes,
                    restTemplate,
                    busSpeedKmh,
                    updateIntervalSeconds
            );

            ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                    task,
                    0,
                    updateIntervalSeconds,
                    TimeUnit.SECONDS
            );

            runningSimulations.put(busId, future);
            log.info("✅ Started simulation for bus: {} ({})", busId, plate);

        } catch (Exception e) {
            log.error("❌ Error while starting simulation for bus: {} ({})", bus.getId(),
                    bus.getNumberPlate() != null ? bus.getNumberPlate() : "N/A", e);
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
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.info("Stopped simulation for bus: {}", busId);
        } else {
            log.warn("No running simulation found for bus: {}", busId);
        }
    }

    public void stopAllSimulations() {
        runningSimulations.forEach((busId, future) -> {
            if (!future.isCancelled()) {
                future.cancel(false);
            }
        });
        runningSimulations.clear();
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
}