/**
 * Customer identity, shared across all modules.
 *
 * <p>Owns the {@code customer} table. Other modules reference {@code customer.id}
 * as a plain UUID and nothing else — no foreign keys cross this boundary.
 */
package com.burakpadr.decorating.customer;
