package com.burakpadr.decorating.quoting.domain.model;

/**
 * How many rooms the home has, in the format Turkish customers state it in
 * ({@code quote_request.layout}, workflow §1.1).
 *
 * <p>"3+1" means three rooms plus a living room. It says nothing about the kitchen, the bathroom or
 * the hallway — which is exactly why the derived list has to be confirmed by the customer (§2.2):
 * "3+1" is four rooms to them and seven areas to us.
 */
public enum Layout {
	STUDIO,
	ONE_PLUS_ONE,
	TWO_PLUS_ONE,
	THREE_PLUS_ONE,
	FOUR_PLUS_ONE,
	FIVE_PLUS_ONE
}
