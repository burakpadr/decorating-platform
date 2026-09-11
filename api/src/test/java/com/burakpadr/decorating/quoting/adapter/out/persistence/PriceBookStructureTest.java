package com.burakpadr.decorating.quoting.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookStructure;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The shape a fresh install starts from (BOYA-70).
 *
 * <p>Two things are being asserted and the second one is the point. The file has to be complete —
 * every item code §5.6 looks up, every room type §5.3 weighs, every modifier §5.7 applies — because a
 * version built from it goes straight past {@code ActivationCheck} and on to price somebody's home.
 * And it has to contain <b>no money</b>: decision 0024 took the price book out of the migrations so
 * that a stranger's install would not quote from figures nobody in their business entered, and a
 * defaults file with a wage in it puts them right back.
 */
class PriceBookStructureTest {

	private final ClasspathPriceBookStructure structures = new ClasspathPriceBookStructure();

	@Test
	@DisplayName("the shipped structure covers every code the engine looks up")
	void theStructureIsComplete() {
		PriceBookStructure structure = structures.current();

		assertThat(structure.items().keySet())
				.as("§5.6 prices every one of these on every quote")
				.containsExactlyInAnyOrder(ItemCode.values());
		assertThat(structure.roomTypes().keySet())
				.as("§5.3 weighs the home's area by room type")
				.containsExactlyInAnyOrder(RoomType.values());
		assertThat(structure.modifiers().keySet())
				.as("§5.7's four; a missing one silently stops surcharging")
				.containsExactlyInAnyOrder(ModifierCode.values());
	}

	@Test
	@DisplayName("every item takes time — a duration of zero prices the work at nothing")
	void everyItemHasADuration() {
		assertThat(structures.current().items().values())
				.allSatisfy(item -> assertThat(item.labourMinutes()).isPositive());
	}

	@Test
	@DisplayName("acceptance: there is no money in the file at all")
	void theFileCarriesNoMoney() throws IOException {
		// Read as text rather than through the parser: the claim is about the file a reader opens, and
		// a money field the loader happens to ignore would still be a published price. Any number with
		// two decimals and a whole part over 100 is treated as suspicious — the structure's own figures
		// are ratios, weights and minutes, none of which come near it.
		String file = new ClassPathResource("price-book/structure-v1.json")
				.getContentAsString(StandardCharsets.UTF_8);

		Matcher numbers = Pattern.compile("(?<![\\w.])(\\d+)\\.(\\d{2})(?![\\d])").matcher(file);
		while (numbers.find()) {
			assertThat(new BigDecimal(numbers.group(1)))
					.as("a figure that large is a price, and prices are asked for, not shipped: %s",
							numbers.group())
					.isLessThanOrEqualTo(new BigDecimal("100"));
		}

		assertThat(file)
				.as("nor the names of the fields that hold money")
				.doesNotContain("Cost").doesNotContain("cost")
				.doesNotContain("VatRate").doesNotContain("marginRatio");
	}

	@Test
	@DisplayName("the structure carries the engineering coefficients, and none that decide money")
	void theCoefficientsAreEngineeringOnly() {
		PriceBookStructure structure = structures.current();

		assertThat(structure.ceilingHeightM()).isEqualByComparingTo("2.70");
		assertThat(structure.grossToNetRatio()).isEqualByComparingTo("0.8200");
		assertThat(structure.roomTypes().get(RoomType.HALLWAY).perimeterFactor())
				.as("a corridor has more wall per square metre than a square room (§5.4)")
				.isEqualByComparingTo("5.50");
		assertThat(structure.roomTypes().get(RoomType.BATHROOM).paintableRatio())
				.as("most of a bathroom wall is tile")
				.isEqualByComparingTo("0.2000");
	}

	@Test
	@DisplayName("the file names its own version, because a quote has to stay explainable")
	void theStructureIsVersioned() {
		// Same rule as the vision prompt (decision 0006): a released file is never edited in place. A
		// setup that ran against v1 produced a book somebody is quoting from.
		assertThat(structures.current().version()).isEqualTo("v1");
	}
}
