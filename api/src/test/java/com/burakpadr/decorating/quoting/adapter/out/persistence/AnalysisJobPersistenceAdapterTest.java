package com.burakpadr.decorating.quoting.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AnalysisJob;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms.ConfirmedRoom;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.out.AnalysisJobs;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The claim (§8), against a real Postgres.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} is the whole of why this queue needs no broker, and it cannot be
 * checked without a second connection holding a row: the {@code status = 'RUNNING'} the claim writes
 * would make a sequential test pass with the clause deleted. So one test here takes a lock out of
 * band and asserts the claim walks past it — that is the concurrency claim, and everything else about
 * this table is bookkeeping around it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AnalysisJobPersistenceAdapterTest {

	@Autowired
	private AnalysisJobs jobs;

	@Autowired
	private RoomRepository rooms;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	@Test
	@DisplayName("one job per room, pending and due immediately")
	void enqueuesARoomEach() {
		List<UUID> home = threeRooms();

		jobs.enqueueFor(home);

		assertThat(jdbc.queryForList("""
				SELECT room_id FROM analysis_job WHERE status = 'PENDING' AND run_after <= now()
				""", UUID.class)).containsExactlyInAnyOrderElementsOf(home);
	}

	@Test
	@DisplayName("a claim takes at most the batch size")
	void claimsUpToTheLimit() {
		jobs.enqueueFor(threeRooms());

		assertThat(jobs.claim(2)).hasSize(2);
		assertThat(jobs.claim(2)).hasSize(1);
	}

	@Test
	@DisplayName("the request that has been waiting longer goes first")
	void claimsTheOldestRequestFirst() {
		// The queue order is run_after, which is the column the claim already filters on. Not the job id:
		// the rooms of one home are inserted in a single statement and share a timestamp, so their ids
		// order arbitrarily within it — and which of a customer's own rooms runs first matters to nobody.
		List<UUID> waitingLonger = rooms(RoomType.LIVING_ROOM, RoomType.BEDROOM);
		jobs.enqueueFor(waitingLonger);
		jobs.enqueueFor(rooms(RoomType.KITCHEN, RoomType.BATHROOM));

		assertThat(jobs.claim(2)).extracting(AnalysisJob::roomId)
				.containsExactlyInAnyOrderElementsOf(waitingLonger);
	}

	@Test
	@DisplayName("a claim counts an attempt as it takes the row")
	void countsTheAttempt() {
		// Counted at the claim, not at the end of the work: a crash in between would otherwise leave a
		// row that had run and did not say so, and it would keep being handed out.
		jobs.enqueueFor(oneRoom());

		assertThat(jobs.claim(5)).singleElement().returns(1, AnalysisJob::attempts);
		assertThat(jdbc.queryForObject(
				"SELECT started_at IS NOT NULL FROM analysis_job", Boolean.class)).isTrue();
	}

	@Test
	@DisplayName("a job waiting out its backoff is not due yet")
	void leavesRescheduledJobsAlone() {
		jobs.enqueueFor(oneRoom());
		UUID job = jobs.claim(5).getFirst().id();

		jobs.retryAfter(job, Duration.ofMinutes(2), "connect timed out");

		assertThat(jobs.claim(5)).isEmpty();
		assertThat(jdbc.queryForObject("SELECT last_error FROM analysis_job", String.class))
				.isEqualTo("connect timed out");
		assertThat(jdbc.queryForObject("SELECT status FROM analysis_job", String.class))
				.isEqualTo("PENDING");
	}

	@Test
	@DisplayName("a finished job is never claimed again, however it finished")
	void neverReclaimsAFinishedJob() {
		List<UUID> home = threeRooms();
		jobs.enqueueFor(home);
		List<AnalysisJob> claimed = jobs.claim(3);

		jobs.done(claimed.get(0).id());
		jobs.failed(claimed.get(1).id(), "twice unusable");

		assertThat(jobs.claim(5)).isEmpty();
		assertThat(statusOf(claimed.get(0).id())).isEqualTo("DONE");
		assertThat(statusOf(claimed.get(1).id())).isEqualTo("FAILED");
		assertThat(statusOf(claimed.get(2).id())).isEqualTo("RUNNING");
		// FAILED is the flag §8 asks for, so the reason has to be on the row an operator opens.
		assertThat(jdbc.queryForObject(
				"SELECT last_error FROM analysis_job WHERE status = 'FAILED'", String.class))
				.isEqualTo("twice unusable");
	}

	@Test
	@DisplayName("a row another instance is holding is walked past, not waited on")
	void skipsLockedRows() throws Exception {
		// The clause the whole arrangement rests on. Without SKIP LOCKED this claim blocks until the
		// other transaction ends — every poller tick in the fleet queueing behind one slow analysis.
		List<UUID> home = threeRooms();
		jobs.enqueueFor(home);

		try (Connection other = dataSource.getConnection()) {
			other.setAutoCommit(false);
			try (PreparedStatement lock = other.prepareStatement("""
					SELECT id FROM analysis_job WHERE status = 'PENDING'
					ORDER BY id LIMIT 1 FOR UPDATE
					""")) {
				ResultSet held = lock.executeQuery();
				assertThat(held.next()).isTrue();
				UUID lockedJob = held.getObject(1, UUID.class);

				assertThat(jobs.claim(5))
						.extracting(AnalysisJob::id)
						.doesNotContain(lockedJob)
						.hasSize(2);
			}
			other.rollback();
		}
	}

	// -----------------------------------------------------------------------------------------------

	private String statusOf(UUID jobId) {
		return jdbc.queryForObject("SELECT status FROM analysis_job WHERE id = ?", String.class, jobId);
	}

	private List<UUID> oneRoom() {
		return rooms(RoomType.BEDROOM);
	}

	private List<UUID> threeRooms() {
		return rooms(RoomType.LIVING_ROOM, RoomType.BEDROOM, RoomType.KITCHEN);
	}

	/** The rooms of one more saved request. */
	private List<UUID> rooms(RoomType... types) {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 3, false, WallCondition.MINOR, null));
		requests.save(draft);

		List<ConfirmedRoom> confirmed = new ArrayList<>();
		List<UUID> ids = new ArrayList<>();
		for (int order = 0; order < types.length; order++) {
			UUID id = Uuid7.generate();
			ids.add(id);
			confirmed.add(new ConfirmedRoom(id, types[order], types[order].name(), order,
					List.of(PhotoRole.WALL_1, PhotoRole.CEILING), true));
		}
		rooms.replaceAll(draft.id(), new ConfirmedRooms(confirmed));
		return ids;
	}
}
