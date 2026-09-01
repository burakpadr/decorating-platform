package com.burakpadr.decorating.quoting.domain.model;

import java.util.List;

/**
 * The model answered and the answer cannot be believed (§6).
 *
 * <p>Something was said, so asking again is a coin toss rather than a wait: §6 allows exactly one
 * retry and then fails the job. The validator's complaints are carried because they are the only
 * record of what came back — the response itself is not persisted, since nothing partial is ever
 * written, and an operator looking at a FAILED job otherwise has a room and no reason.
 */
public class UnusableAnalysis extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient List<String> problems;

	public UnusableAnalysis(String message, List<String> problems) {
		super(message + ": " + String.join("; ", problems));
		this.problems = List.copyOf(problems);
	}

	public List<String> problems() {
		return problems;
	}
}
