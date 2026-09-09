package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.AppliedModifier;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceUnit;
import com.burakpadr.decorating.quoting.domain.model.PricedQuote;
import com.burakpadr.decorating.quoting.domain.model.Quote;
import com.burakpadr.decorating.quoting.domain.model.QuoteAuthor;
import com.burakpadr.decorating.quoting.domain.model.QuoteLine;
import com.burakpadr.decorating.quoting.domain.model.QuotePortion;
import com.burakpadr.decorating.quoting.domain.model.QuoteState;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code quote} and {@code quote_line_item} (§4.6).
 *
 * <p>Superseding and inserting are one transaction. Split, a request would hold two live quotes for
 * the length of the gap, and the operator queue reads "the current one" — the one thing that must
 * never be ambiguous on a screen somebody approves a price from.
 *
 * <p>The price book arrives as a version code and is resolved to an id here, the way
 * {@code StageOneEstimatePersistenceAdapter} does it: the domain's {@code PriceBook} has no id and
 * should not grow one to satisfy a column. {@code version_code} is unique, so the answer is exact.
 *
 * <p>{@code applied_modifiers} is written as jsonb by hand, and safely: the only values are enum
 * names, which cannot contain a character worth escaping, and four-decimal numbers.
 */
@Component
class QuotePersistenceAdapter implements QuoteRepository {

	private final JdbcTemplate jdbc;

	QuotePersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional
	public void save(Quote quote) {
		// Everything earlier is out of play the moment a new revision exists. Done first so the unique
		// constraint on (quote_request_id, revision) is the only thing that can refuse the insert.
		jdbc.update("""
				UPDATE quote SET status = ? WHERE quote_request_id = ? AND status <> ?
				""", QuoteState.SUPERSEDED.name(), quote.quoteRequestId(), QuoteState.SUPERSEDED.name());

		PricedQuote priced = quote.priced();
		jdbc.update("""
				INSERT INTO quote (
				  id, quote_request_id, price_book_id, revision, status,
				  total_cost, subtotal, vat_amount, total, band_low, band_high,
				  margin_ratio, minimum_applied, estimated_days, total_wall_sqm, total_ceiling_sqm,
				  created_by)
				VALUES (?, ?, (SELECT id FROM price_book WHERE version_code = ?), ?, ?,
				        ?, ?, ?, ?, ?, ?,
				        (SELECT margin_ratio FROM price_book WHERE version_code = ?), ?, ?, ?, ?,
				        ?)
				""",
				quote.id(),
				quote.quoteRequestId(),
				quote.priceBookVersion(),
				quote.revision(),
				quote.state().name(),
				priced.totalCost(),
				priced.subtotalExVat(),
				priced.vatAmount(),
				priced.total(),
				priced.bandLow(),
				priced.bandHigh(),
				// §4.6 keeps the margin on the quote rather than only on the book: the book is a version
				// somebody can look up, but "what margin was this priced at" has to survive on the row
				// that priced it.
				quote.priceBookVersion(),
				priced.minimumBinding(),
				priced.billableDays(),
				priced.totalWallSqm(),
				priced.totalCeilingSqm(),
				quote.createdBy().name());

		List<QuoteLine> lines = priced.lines();
		jdbc.batchUpdate("""
				INSERT INTO quote_line_item (
				  id, quote_id, item_code, quantity, unit, labour_cost, material_cost,
				  applied_modifiers, line_total, labour_minutes, sort_order)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
				""", new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement statement, int index) throws SQLException {
				QuoteLine line = lines.get(index);
				statement.setObject(1, Uuid7.generate());
				statement.setObject(2, quote.id());
				statement.setString(3, line.code().name());
				statement.setBigDecimal(4, line.quantity());
				statement.setString(5, line.unit().name());
				statement.setBigDecimal(6, money(line.labourCost()));
				statement.setBigDecimal(7, money(line.materialCost()));
				statement.setString(8, asJson(line.appliedModifiers()));
				statement.setBigDecimal(9, line.lineTotal());
				statement.setBigDecimal(10, line.labourMinutes());
				statement.setInt(11, index);
			}

			@Override
			public int getBatchSize() {
				return lines.size();
			}
		});
	}

	@Override
	public int nextRevision(UUID quoteRequestId) {
		Integer highest = jdbc.queryForObject(
				"SELECT coalesce(max(revision), 0) FROM quote WHERE quote_request_id = ?",
				Integer.class, quoteRequestId);
		return (highest == null ? 0 : highest) + 1;
	}

	private static BigDecimal money(BigDecimal amount) {
		return amount.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	private static String asJson(List<AppliedModifier> applied) {
		StringBuilder json = new StringBuilder("[");
		for (AppliedModifier modifier : applied) {
			if (json.length() > 1) {
				json.append(',');
			}
			json.append("{\"code\":\"").append(modifier.code().name())
					.append("\",\"labour\":").append(modifier.labourFactor().toPlainString())
					.append(",\"material\":").append(modifier.materialFactor().toPlainString())
					.append('}');
		}
		return json.append(']').toString();
	}

}
