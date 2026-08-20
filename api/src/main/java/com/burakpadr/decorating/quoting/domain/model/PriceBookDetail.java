package com.burakpadr.decorating.quoting.domain.model;

/**
 * A version with its figures, for the operator panel (§7, workflow §6).
 *
 * <p>{@code editable} travels with it because the panel has to decide what to offer before the
 * operator taps anything: a version that has priced quotes shows its items read-only and offers "copy
 * and edit the copy" instead. Learning that from a 409 after typing a figure is a worse way to find
 * out.
 */
public record PriceBookDetail(PriceBookSummary summary, PriceBook book, boolean editable) {}
