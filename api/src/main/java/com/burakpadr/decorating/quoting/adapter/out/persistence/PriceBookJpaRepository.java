package com.burakpadr.decorating.quoting.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The {@code price_book} row itself.
 *
 * <p>The four child tables have their own interfaces rather than {@code @OneToMany} collections. One
 * fetch-joined query across four collections multiplies its rows — 14 items × 4 modifiers × 8 room
 * types × 39 districts — and lazy collections would need an open session in a layer with no business
 * holding one. Four indexed reads of tables with tens of rows is the cheaper honest answer.
 */
interface PriceBookJpaRepository extends JpaRepository<PriceBookEntity, UUID> {

	/** The one version quotes are priced against; the partial unique index guarantees at most one. */
	Optional<PriceBookEntity> findByActiveTrue();

	Optional<PriceBookEntity> findByVersionCode(String versionCode);
}
