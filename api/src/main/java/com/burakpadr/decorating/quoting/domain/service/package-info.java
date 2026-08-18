/**
 * Pure domain services: PricingEngine, RoomListDeriver, ConfidenceEvaluator.
 *
 * <p>PricingEngine takes PricingInput plus PriceBook and returns PricedQuote. It has zero
 * dependencies and must stay unit-testable without a Spring context or a database.
 */
package com.burakpadr.decorating.quoting.domain.service;
