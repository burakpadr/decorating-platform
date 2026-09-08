package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.AnalysisJob;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysisRequest;
import com.burakpadr.decorating.quoting.domain.port.in.AnalyseRoom;
import com.burakpadr.decorating.quoting.domain.port.out.AnalysisJobs;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomAnalysisRepository;
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
 * <p>What it does not do is decide what the analysis means. Whether the room's confidence is good
 * enough, whether a frame has to be retaken, whether the request may leave ANALYZING at all — that is
 * §6's evaluator (BOYA-51) reading the rows this writes. Until it exists a fully analysed request
 * stays in ANALYZING, which is a stated gap rather than a quiet one.
 */
@Service
class RoomAnalysisService implements AnalyseRoom {

	private final PhotoRepository photos;
	private final VisionAnalysisPort vision;
	private final RoomAnalysisRepository analyses;
	private final AnalysisJobs jobs;

	RoomAnalysisService(PhotoRepository photos, VisionAnalysisPort vision,
			RoomAnalysisRepository analyses, AnalysisJobs jobs) {
		this.photos = photos;
		this.vision = vision;
		this.analyses = analyses;
		this.jobs = jobs;
	}

	@Override
	@Transactional
	public void analyse(AnalysisJob job) {
		RoomAnalysisRequest request =
				RoomAnalysisRequest.of(job.roomId(), photos.findByRoom(job.roomId()));

		RoomAnalysis analysis = vision.analyse(request);

		analyses.save(analysis);
		jobs.done(job.id());
	}
}
