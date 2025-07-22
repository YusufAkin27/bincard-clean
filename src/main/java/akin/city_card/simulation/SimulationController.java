package akin.city_card.simulation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/simulation")
@Slf4j
@ConditionalOnProperty(name = "simulation.enabled", havingValue = "true")
public class SimulationController {

    @Autowired
    private BusSimulationService simulationService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startAllSimulations() {
        try {
            log.info("Starting all bus simulations via API request");
            simulationService.startAllBusSimulations();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All bus simulations started successfully");
            response.put("activeSimulations", simulationService.getActiveSimulationCount());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to start all simulations", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to start simulations: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopAllSimulations() {
        try {
            log.info("Stopping all bus simulations via API request");
            simulationService.stopAllSimulations();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All bus simulations stopped successfully");
            response.put("activeSimulations", 0);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to stop all simulations", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to stop simulations: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/bus/{busId}/start")
    public ResponseEntity<Map<String, Object>> startBusSimulation(@PathVariable Long busId) {
        try {
            log.info("Starting simulation for bus: {}", busId);
            simulationService.startBusSimulationById(busId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bus simulation started successfully");
            response.put("busId", busId);
            response.put("isRunning", simulationService.isSimulationRunning(busId));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to start simulation for bus: {}", busId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to start bus simulation: " + e.getMessage());
            response.put("busId", busId);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/bus/{busId}/stop")
    public ResponseEntity<Map<String, Object>> stopBusSimulation(@PathVariable Long busId) {
        try {
            log.info("Stopping simulation for bus: {}", busId);
            simulationService.stopBusSimulation(busId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bus simulation stopped successfully");
            response.put("busId", busId);
            response.put("isRunning", false);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to stop simulation for bus: {}", busId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to stop bus simulation: " + e.getMessage());
            response.put("busId", busId);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/bus/{busId}/restart")
    public ResponseEntity<Map<String, Object>> restartBusSimulation(@PathVariable Long busId) {
        try {
            log.info("Restarting simulation for bus: {}", busId);
            simulationService.restartBusSimulation(busId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bus simulation restarted successfully");
            response.put("busId", busId);
            response.put("isRunning", simulationService.isSimulationRunning(busId));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to restart simulation for bus: {}", busId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to restart bus simulation: " + e.getMessage());
            response.put("busId", busId);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("activeSimulations", simulationService.getActiveSimulationCount());
            status.put("simulationEnabled", true);
            status.put("success", true);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get simulation status", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/bus/{busId}/status")
    public ResponseEntity<Map<String, Object>> getBusSimulationStatus(@PathVariable Long busId) {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("busId", busId);
            status.put("isRunning", simulationService.isSimulationRunning(busId));
            status.put("success", true);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get simulation status for bus: {}", busId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get bus status: " + e.getMessage());
            response.put("busId", busId);
            return ResponseEntity.internalServerError().body(response);
        }
    }
}