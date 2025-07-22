package akin.city_card.simulation;

import akin.city_card.bus.model.Bus;
import akin.city_card.bus.repository.BusRepository;
import akin.city_card.route.model.RouteStationNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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

            List<Bus> activeBuses = busRepository.findByActiveTrueAndDeletedFalse();
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

            if (runningSimulations.containsKey(bus.getId())) {
                log.warn("Simulation already running for bus: {}", bus.getId());
                return;
            }

            if (bus.getRoute() == null) {
                log.warn("Bus {} has no route assigned. Skipping simulation.", bus.getId());
                return;
            }

            // Force initialize the lazy collection within transaction
            List<RouteStationNode> nodes = bus.getRoute().getStationNodes();
            if (nodes == null || nodes.isEmpty()) {
                log.warn("No route nodes found for bus: {} ({})", bus.getId(), bus.getNumberPlate());
                return;
            }

            // Force initialize all necessary lazy properties
            nodes.forEach(node -> {
                if (node.getFromStation() != null && node.getFromStation().getLocation() != null) {
                    // Access to initialize
                    node.getFromStation().getLocation().getLatitude();
                }
                if (node.getToStation() != null && node.getToStation().getLocation() != null) {
                    // Access to initialize
                    node.getToStation().getLocation().getLatitude();
                }
            });

            // Validate that route nodes have valid stations
            boolean hasValidNodes = nodes.stream()
                    .anyMatch(node -> node.getFromStation() != null &&
                            node.getToStation() != null &&
                            node.getFromStation().getLocation() != null &&
                            node.getToStation().getLocation() != null);

            if (!hasValidNodes) {
                log.warn("No valid route nodes found for bus: {} ({})", bus.getId(), bus.getNumberPlate());
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

            runningSimulations.put(bus.getId(), future);
            log.info("Started simulation for bus: {} ({})", bus.getId(), bus.getNumberPlate());

        } catch (Exception e) {
            log.error("Failed to start simulation for bus: {} ({})", bus.getId(),
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