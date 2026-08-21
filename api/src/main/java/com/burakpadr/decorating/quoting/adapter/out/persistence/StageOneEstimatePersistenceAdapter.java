package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.port.out.PendingPhoneWriter;
import com.burakpadr.decorating.quoting.domain.port.out.StageOneEstimateWriter;
import com.burakpadr.decorating.quoting.domain.port.out.StoredEstimates;
import com.burakpadr.decorating.shared.PhoneNumber;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 1's output on the draft it belongs to (§4.2): the range, the version behind it, and the number
 * of somebody who asked for it by SMS.
 *
 * <p>Three ports, one adapter, because all four columns live on {@code quote_request} and a save that
 * touched one of them through a different connection would be a second transaction for one row. The
 * ports stay separate because the callers are: pricing writes the range, §1.5's SMS option writes the
 * number.
 */
@Component
class StageOneEstimatePersistenceAdapter
		implements StageOneEstimateWriter, StoredEstimates, PendingPhoneWriter {

	private final JdbcTemplate jdbc;

	StageOneEstimatePersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Range> find(UUID quoteRequestId) {
		return jdbc.query(
				"SELECT estimate_low, estimate_high FROM quote_request WHERE id = ?",
				row -> row.next()
						? Optional.of(new Range(row.getBigDecimal(1), row.getBigDecimal(2)))
						: Optional.<Range>empty(),
				quoteRequestId);
	}

	@Override
	public void storePendingPhone(UUID quoteRequestId, PhoneNumber phone) {
		int updated = jdbc.update(
				"UPDATE quote_request SET pending_phone = ?, updated_at = now() WHERE id = ?",
				phone.e164(), quoteRequestId);
		if (updated != 1) {
			throw new IllegalStateException("no quote_request " + quoteRequestId + " to keep a number for");
		}
	}

	@Override
	public void recordEstimate(UUID quoteRequestId, BigDecimal netArea, String priceBookVersion,
			BigDecimal low, BigDecimal high) {
		// Resolved in its own statement rather than as a subselect in the UPDATE: a subselect that found
		// nothing would write a null id and succeed, and the row would carry a range nobody can explain —
		// which is the one thing this column exists to prevent (§4.5).
		UUID priceBookId = jdbc.queryForObject(
				"SELECT id FROM price_book WHERE version_code = ?", UUID.class, priceBookVersion);
		int updated = jdbc.update("""
				UPDATE quote_request
				SET net_area = ?, price_book_id = ?, estimate_low = ?, estimate_high = ?, updated_at = now()
				WHERE id = ?
				""", netArea, priceBookId, low, high, quoteRequestId);
		if (updated != 1) {
			throw new IllegalStateException("no quote_request " + quoteRequestId + " to record against");
		}
	}
}
