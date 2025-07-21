package akin.city_card.route.core.request;

import akin.city_card.route.model.Direction;
import akin.city_card.route.model.TimeSlot;
import lombok.Data;

import java.util.List;

@Data
public class UpdateRouteNodeRequest {
    private Long fromStationId;
    private Long toStationId;
    private Direction direction;
    private List<TimeSlot> weekdayHours;
    private List<TimeSlot> weekendHours;
}
