package org.observe.quick.style;

import java.awt.font.TextAttribute;
import java.text.ParseException;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.expresso.NonStructuredParser;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;

import com.google.common.reflect.TypeToken;

public class FontValueParser implements NonStructuredParser {
	// Font weights
	/** The {@link #weight} of normal text */
	public static final double normalWeight = 1;
	/** The {@link #weight} of extra-light text (the lightest named weight, lighter than {@link #light}) */
	public static final double extraLight = TextAttribute.WEIGHT_EXTRA_LIGHT;
	/** The {@link #weight} of light text (heavier than {@link #extraLight}, lighter than {@link #demiLight}) */
	public static final double light = TextAttribute.WEIGHT_LIGHT;
	/** The {@link #weight} of demi-light text (heavier than {@link #light}, lighter than {@link #semiBold}) */
	public static final double demiLight = TextAttribute.WEIGHT_DEMILIGHT;
	/** The {@link #weight} of semi-bold text (heavier than {@link #demiLight}, lighter than {@link #medium}) */
	public static final double semiBold = TextAttribute.WEIGHT_SEMIBOLD;
	/** The {@link #weight} of medium text (heavier than {@link #semiBold}, lighter than {@link #demiBold}) */
	public static final double medium = TextAttribute.WEIGHT_MEDIUM;
	/** The {@link #weight} of demi-bold text (heavier than {@link #medium}, lighter than {@link #bold}) */
	public static final double demiBold = TextAttribute.WEIGHT_DEMIBOLD;
	/** The {@link #weight} of bold text (heavier than {@link #demiBold}, lighter than {@link #heavy}) */
	public static final double bold = TextAttribute.WEIGHT_BOLD;
	/** The {@link #weight} of heavy text (heavier than {@link #bold}, lighter than {@link #extraBold}) */
	public static final double heavy = TextAttribute.WEIGHT_HEAVY;
	/** The {@link #weight} of extra bold text (heavier than {@link #heavy}, lighter than {@link #ultraBold}) */
	public static final double extraBold = TextAttribute.WEIGHT_EXTRABOLD;
	/** The {@link #weight} of ultra bold text (the heaviest named weight, heavier than {@link #extraBold}) */
	public static final double ultraBold = TextAttribute.WEIGHT_ULTRABOLD;

	// Font slants
	/** The {@link #slant} of normal text */
	public static final double normalSlant = TextAttribute.POSTURE_REGULAR;
	/** The {@link #slant} of italic text */
	public static final double italic = TextAttribute.POSTURE_OBLIQUE;

	public static final Map<String, Double> NAMED_WEIGHTS = QommonsUtils.<String, Double> buildMap(null)//
		.with("normal", normalWeight)//
		.with("extra-light", extraLight)//
		.with("light", light)//
		.with("demi-light", demiLight)//
		.with("semi-bold", semiBold)//
		.with("medium", medium)//
		.with("demi-bold", demiBold)//
		.with("bold", bold)//
		.with("heavy", heavy)//
		.with("extra-bold", extraBold)//
		.with("ulter-bold", ultraBold)//
		.getUnmodifiable();
	public static final Map<String, Double> NAMED_SLANTS = QommonsUtils.<String, Double> buildMap(null)//
		.with("normal", normalSlant)//
		.with("italic", italic)//
		.getUnmodifiable();

	@Override
	public boolean canParse(TypeToken<?> type, String text) {
		if (!TypeTokens.get().isAssignable(type, TypeTokens.get().DOUBLE))
			return false;
		return NAMED_WEIGHTS.containsKey(text) || NAMED_SLANTS.containsKey(text);
	}

	@Override
	public <T> ObservableValue<? extends T> parse(TypeToken<T> type, String text) throws ParseException {
		Double value = NAMED_WEIGHTS.get(text);
		if (value == null)
			value = NAMED_SLANTS.get(text);
		return (ObservableValue<? extends T>) ObservableValue.of(double.class, value);
	}
}
