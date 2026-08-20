package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.QuoteCalculation;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculationCommand;

/**
 * Price a job entered by hand, against the live price book (workflow §12, increment 1).
 *
 * <p>Read-only in every sense: nothing is stored, no quote request is created, no event is published.
 * The operator is asking the price book a question, not starting a job — and increment 1 ships with no
 * customer interface precisely so this question can be asked before anything is built on the answer.
 */
public interface CalculateQuote {

	QuoteCalculation calculate(QuoteCalculationCommand command);
}
