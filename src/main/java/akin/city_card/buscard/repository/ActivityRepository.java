package akin.city_card.buscard.repository;

import akin.city_card.buscard.model.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
}
