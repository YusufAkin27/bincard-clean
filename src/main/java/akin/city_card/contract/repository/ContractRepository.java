// ContractRepository.java
package akin.city_card.contract.repository;

import akin.city_card.contract.model.Contract;
import akin.city_card.contract.model.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findAllByOrderByCreatedAtDesc();
    List<Contract> findByActiveOrderByCreatedAtDesc(boolean active);
    List<Contract> findByMandatoryAndActiveOrderByCreatedAtDesc(boolean mandatory, boolean active);
    List<Contract> findByTypeAndActiveOrderByCreatedAtDesc(ContractType type, boolean active);
    List<Contract> findByMandatoryAndActive(boolean mandatory, boolean active);
}
