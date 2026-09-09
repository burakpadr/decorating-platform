package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.AnalysisJob;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysisRequest;
import com.burakpadr.decorating.quoting.domain.port.in.AnalyseRoom;
import com.burakpadr.decorating.quoting.domain.port.in.ConcludeAnalysis;
import com.burakpadr.decorating.quoting.domain.port.out.AnalysisJobs;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomAnalysisRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.quoting.domain.port.out.VisionAnalysisPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One room, from its frames to its findings (§6, workflow §4.1, BOYA-48).
 *
 * <p>Three steps and a transaction around them. The frames are gathered, the model is asked, the
 * findings are written and the job is closed — and if any of it fails, none of it happened. The state
 * that arrangement forbids is the expensive one: an analysis on the row with the job still saying
 * PENDING, which the next tick pays a second provider call to produce again.
 *
 * <p>What the analysis <em>means</em> is not decided here. When the last room of a request lands,
 * {@link ConcludeAnalysis} prices what was found and reads it (§6) — in this same transaction, so a
 * request either has every analysis, a quote and a decision, or has none of them. Asked after every
 * room and not only the last, because nothing here knows which room is last: the check for
 * completeness is the conclusion's own, and a room analysed while another is still running answers
 * "not yet".
 */
@Service
class RoomAnalysisService implements AnalyseRoom {

	private final PhotoRepository photos;
	private final VisionAnalysisPort vision;
	private final RoomAnalysisRepository analyses;
	private final AnalysisJobs jobs;
	private final RoomRepository rooms;
	private final ConcludeAnalysis conclusion;

	RoomAnalysisService(PhotoRepository photos, VisionAnalysisPort vision,
			RoomAnalysisRepository analyses, AnalysisJobs jobs, RoomRepository rooms,
			ConcludeAnalysis conclusion) {
		this.photos = photos;
		this.vision = vision;
		this.analyses = analyses;
		this.jobs = jobs;
		this.rooms = rooms;
		this.conclusion = conclusion;
	}

	@Override
	@Transactional
	public void analyse(AnalysisJob job) {
		RoomAnalysisRequest request =
				RoomAnalysisRequest.of(job.roomId(), photos.findByRoom(job.roomId()));

		RoomAnalysis analysis = vision.analyse(request);

		analyses.save(analysis);
		jobs.done(job.id());

		rooms.quoteRequestOf(job.roomId()).ifPresent(conclusion::concludeIfComplete);
	}
}
