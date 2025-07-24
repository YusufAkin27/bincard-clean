package akin.city_card.driver.core.response;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverPerformanceDto {
    private Long totalDrivingHours;
    private Double totalDistanceDriven;
    private Long totalPassengersTransported;
    private BigDecimal totalEarnings;
    private Double averageRating;
}
