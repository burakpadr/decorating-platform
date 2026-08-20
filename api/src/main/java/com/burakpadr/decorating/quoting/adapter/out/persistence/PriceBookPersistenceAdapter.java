package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a whole price book version and hands it over as a value (§2, §4.5).
 *
 * <p>The engine never sees this class. It is handed a {@link PriceBook} it cannot refetch from,
 * cannot lazily expand, and cannot mutate — which is what keeps the pricing arithmetic testable with
 * no database at all, the single most important testability requirement in the system.
 *
 * <p>Read-only and transactional per call: the four child reads must see one consistent version, or a
 * book could be assembled half from the version being replaced and half from its replacement.
 *
 * <p>Absent under the {@code openapi} profile. That profile exports the contract by booting the web
 * layer with no datasource, JPA or Flyway, so a bean that needs a Spring Data repository cannot be
 * created — and a failed boot there fails CI, not a test. Every persistence adapter carries this for
 * the same reason; {@code OpenApiProfileContextTest} is what notices when one forgets.
 */
@Repository
@Profile("!openapi")
@Transactional(readOnly = true)
class PriceBookPersistenceAdapter implements PriceBookRepository {

	private final PriceBookJpaRepository books;
	private final PriceBookItemJpaRepository items;
	private final PriceModifierJpaRepository modifiers;
	private final RoomTypeConfigJpaRepository roomTypes;
	private final ServiceDistrictJpaRepository districts;

	PriceBookPersistenceAdapter(
			PriceBookJpaRepository books,
			PriceBookItemJpaRepository items,
			PriceModifierJpaRepository modifiers,
			RoomTypeConfigJpaRepository roomTypes,
			ServiceDistrictJpaRepository districts) {
		this.books = books;
		this.items = items;
		this.modifiers = modifiers;
		this.roomTypes = roomTypes;
		this.districts = districts;
	}

	@Override
	public Optional<PriceBook> findActive() {
		return books.findByActiveTrue().map(this::assemble);
	}

	@Override
	public Optional<PriceBook> findByVersionCode(String versionCode) {
		return books.findByVersionCode(versionCode).map(this::assemble);
	}

	private PriceBook assemble(PriceBookEntity book) {
		UUID id = book.getId();
		return PriceBookMapper.toDomain(
				book,
				items.findByPriceBookId(id),
				modifiers.findByPriceBookId(id),
				roomTypes.findByPriceBookId(id),
				districts.findByPriceBookId(id));
	}
}
