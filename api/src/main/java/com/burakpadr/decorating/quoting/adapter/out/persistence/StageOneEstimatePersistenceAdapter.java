package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.port.out.StageOneEstimateWriter;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes stage 1's output onto the draft it belongs to (§4.2). */
@Component
class StageOneEstimatePersistenceAdapter implements StageOneEstimateWriter {

	private final JdbcTemplate jdbc;

	StageOneEstimatePersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
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
