package akin.city_card.user.repository;

import akin.city_card.user.model.GeoAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeoAlertRepository extends JpaRepository<GeoAlert,Long> {
}
