// NextBusDTO.java - Güncellenmiş
package akin.city_card.route.core.response;

import akin.city_card.route.model.DirectionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NextBusDTO {
    private String plate;
    private Integer arrivalInMinutes;
    private DirectionType direction;
    private String directionName;
    private String currentLocation;
    private Integer occupancyRate;
    private String busStatus;
}