package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
import java.util.List;

/**
 * The districts stage 1's first question offers (§7, workflow §1.1).
 *
 * <p>Read from the active price book rather than a constant. Turning a district off is how the business
 * closes an area, and a list compiled anywhere else would keep sending it work — which is worse than
 * not listing it, because the customer fills in the whole form first.
 */
public interface ListServiceDistricts {

	List<ServiceDistrict> served();
}
