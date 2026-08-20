package com.burakpadr.decorating.quoting.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoomTypeConfigJpaRepository extends JpaRepository<RoomTypeConfigEntity, UUID> {

	List<RoomTypeConfigEntity> findByPriceBookId(UUID priceBookId);
}
