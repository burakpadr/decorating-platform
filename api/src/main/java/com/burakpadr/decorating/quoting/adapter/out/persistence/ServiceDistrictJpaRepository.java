package com.burakpadr.decorating.quoting.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ServiceDistrictJpaRepository extends JpaRepository<ServiceDistrictEntity, UUID> {

	List<ServiceDistrictEntity> findByPriceBookId(UUID priceBookId);
}
