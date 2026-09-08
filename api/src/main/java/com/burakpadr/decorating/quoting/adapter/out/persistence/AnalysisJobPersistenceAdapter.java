package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.AnalysisJob;
import com.burakpadr.decorating.quoting.domain.port.out.AnalysisJobs;
import com.burakpadr.decorating.shared.Uuid7;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code analysis_job}, claimed the way §8 writes it.
 *
 * <p>The claim is one statement: the {@code UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP
 * LOCKED) RETURNING} of §8, verbatim in shape. Two statements — select, then update — would be a
 * window in which two instances hand the same room to two vision calls, which is a bill charged twice
 * and, worse, two analyses racing for one {@code room_analysis} row.
 *
 * <p>{@code ORDER BY id} is a queue order because the ids are UUIDv7. A customer who submitted an
 * hour ago is not overtaken by one who submitted a minute ago, and the ordering costs nothing that
 * the index on {@code (status, run_after)} was not already paying.
 */
@Component
class AnalysisJobPersistenceAdapter implements AnalysisJobs {

	private final JdbcTemplate jdbc;

	AnalysisJobPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void enqueueFor(List<UUID> roomIds) {
		jdbc.batchUpdate("""
				INSERT INTO analysis_job (id, room_id, status) VALUES (?, ?, 'PENDING')
				""", new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement statement, int index) throws SQLException {
				statement.setObject(1, Uuid7.generate());
				statement.setObject(2, roomIds.get(index));
			}

			@Override
			public int getBatchSize() {
				return roomIds.size();
			}
		});
	}

	@Override
	public List<AnalysisJob> claim(int limit) {
		return jdbc.query("""
				WITH claimed AS (
				  UPDATE analysis_job
				  SET status = 'RUNNING', started_at = now(), attempts = attempts + 1
				  WHERE id IN (
				    SELECT id FROM analysis_job
				    WHERE status = 'PENDING' AND run_after <= now()
				    ORDER BY run_after, id
				    LIMIT ?
				    FOR UPDATE SKIP LOCKED
				  )
				  RETURNING id, room_id, attempts, run_after
				)
				SELECT id, room_id, attempts FROM claimed ORDER BY run_after, id
				""", (row, index) -> new AnalysisJob(
						row.getObject("id", UUID.class),
						row.getObject("room_id", UUID.class),
						row.getInt("attempts")),
				limit);
	}

	@Override
	public void done(UUID jobId) {
		jdbc.update("""
				UPDATE analysis_job SET status = 'DONE', finished_at = now(), last_error = NULL
				WHERE id = ?
				""", jobId);
	}

	@Override
	public void retryAfter(UUID jobId, Duration delay, String error) {
		// Back to PENDING with a future run_after, which is the only thing that keeps it out of the next
		// claim: the index the claim reads is (status, run_after) and both halves have to say "not yet".
		jdbc.update("""
				UPDATE analysis_job
				SET status = 'PENDING', run_after = now() + make_interval(secs => ?), last_error = ?
				WHERE id = ?
				""", delay.toSeconds(), error, jobId);
	}

	@Override
	public void failed(UUID jobId, String error) {
		jdbc.update("""
				UPDATE analysis_job SET status = 'FAILED', finished_at = now(), last_error = ?
				WHERE id = ?
				""", error, jobId);
	}
}
