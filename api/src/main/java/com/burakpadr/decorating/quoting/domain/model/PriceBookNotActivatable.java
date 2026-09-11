package com.burakpadr.decorating.quoting.domain.model;

import java.util.List;

/**
 * A version that may not go live, and why (BOYA-20a, ADR 0016).
 *
 * <p>Its own type rather than an {@link IllegalStateException}: the operator asked for something
 * reasonable and the answer is a list of things to fix, which the panel has to render. The problems
 * travel with it because an operator who is told only that the version is inconsistent has fourteen
 * items to go through.
 */
public class PriceBookNotActivatable extends RuntimeException {

	private final transient List<ActivationProblem> problems;

	public PriceBookNotActivatable(String versionCode, List<ActivationProblem> problems) {
		super(versionCode + " yürürlüğe alınamaz: " + problems.size() + " sorun var");
		this.problems = List.copyOf(problems);
	}

	public List<ActivationProblem> problems() {
		return problems;
	}
}
