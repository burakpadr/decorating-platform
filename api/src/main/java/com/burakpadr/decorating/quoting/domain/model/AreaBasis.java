package com.burakpadr.decorating.quoting.domain.model;

/**
 * Whether the square metres the customer gave are gross or net ({@code quote_request.area_basis}).
 *
 * <p>Turkish listings quote gross, which includes the share of the stairwell nobody paints, so the
 * question is asked in stage 1 (§1.1) and the conversion is priced: a gross figure costs five points
 * of band width (§5.9), because it is an assumption about the walls rather than a measurement of them.
 */
public enum AreaBasis {
	GROSS,
	NET
}
