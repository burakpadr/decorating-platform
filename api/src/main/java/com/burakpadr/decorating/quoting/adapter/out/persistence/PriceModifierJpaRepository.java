package com.burakpadr.decorating.quoting.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PriceModifierJpaRepository extends JpaRepository<PriceModifierEntity, UUID> {

	List<PriceModifierEntity> findByPriceBookId(UUID priceBookId);
}
