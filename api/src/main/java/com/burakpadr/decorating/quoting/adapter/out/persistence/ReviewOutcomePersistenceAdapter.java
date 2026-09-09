package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.Review;
import com.burakpadr.decorating.quoting.domain.port.out.ReviewOutcomes;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code quote_request.review_decision} and {@code review_reasons} (§6, V11).
 *
 * <p>Overwritten rather than appended: a re-analysis after a recapture produces a new reading and the
 * previous reasons are about photographs that no longer exist. Keeping both would put two answers in
 * front of the operator with nothing to say which reading each came from.
 */
@Component
class ReviewOutcomePersistenceAdapter implements ReviewOutcomes {

	private final JdbcTemplate jdbc;

	ReviewOutcomePersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void record(UUID quoteRequestId, Review review) {
		jdbc.update("""
				UPDATE quote_request SET review_decision = ?, review_reasons = ? WHERE id = ?
				""", (PreparedStatement statement) -> {
			statement.setString(1, review.decision().name());
			statement.setArray(2, statement.getConnection()
					.createArrayOf("text", review.reasons().toArray(String[]::new)));
			statement.setObject(3, quoteRequestId);
		});
	}
}
