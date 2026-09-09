package com.burakpadr.decorating.quoting.domain.model;

/**
 * Who produced a quote ({@code quote.created_by}, §4.6).
 *
 * <p>{@code SYSTEM} is the engine pricing findings. {@code OPERATOR} is a quote that exists because
 * somebody adjusted one (BOYA-54), and the distinction is the calibration question: comparing what the
 * engine produced against what was actually charged only means something if the rows say which is
 * which.
 */
public enum QuoteAuthor {
	SYSTEM,
	OPERATOR
}
