package org.observe.quick.style;

import java.awt.font.TextAttribute;
import java.text.ParseException;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.expresso.NonStructuredParser;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;

import com.google.common.reflect.TypeToken;

/** Enables easier parsing of several font-related constants */
public class FontValueParser implements NonStructuredParser {
	// Font weights
	/** The weight of normal text */
	public static final double normalWeight = 1;
	/** The weight of extra-light text (the lightest named weight, lighter than {@link #light}) */
	public static final double extraLight = TextAttribute.WEIGHT_EXTRA_LIGHT;
	/** The weight of light text (heavier than {@link #extraLight}, lighter than {@link #demiLight}) */
	public static final double light = TextAttribute.WEIGHT_LIGHT;
	/** The weight of demi-light text (heavier than {@link #light}, lighter than {@link #semiBold}) */
	public static final double demiLight = TextAttribute.WEIGHT_DEMILIGHT;
	/** The weight of semi-bold text (heavier than {@link #demiLight}, lighter than {@link #medium}) */
	public static final double semiBold = TextAttribute.WEIGHT_SEMIBOLD;
	/** The weight of medium text (heavier than {@link #semiBold}, lighter than {@link #demiBold}) */
	public static final double medium = TextAttribute.WEIGHT_MEDIUM;
	/** The weight of demi-bold text (heavier than {@link #medium}, lighter than {@link #bold}) */
	public static final double demiBold = TextAttribute.WEIGHT_DEMIBOLD;
	/** The weight of bold text (heavier than {@link #demiBold}, lighter than {@link #heavy}) */
	public static final double bold = TextAttribute.WEIGHT_BOLD;
	/** The weight of heavy text (heavier than {@link #bold}, lighter than {@link #extraBold}) */
	public static final double heavy = TextAttribute.WEIGHT_HEAVY;
	/** The weight of extra bold text (heavier than {@link #heavy}, lighter than {@link #ultraBold}) */
	public static final double extraBold = TextAttribute.WEIGHT_EXTRABOLD;
	/** The weight of ultra bold text (the heaviest named weight, heavier than {@link #extraBold}) */
	public static final double ultraBold = TextAttribute.WEIGHT_ULTRABOLD;

	// Font slants
	/** The slant of normal text */
	public static final double normalSlant = TextAttribute.POSTURE_REGULAR;
	/** The slant of italic text */
	public static final double italic = TextAttribute.POSTURE_OBLIQUE;

	/** All font weights in this class, stored by the name the user can use to refer to them */
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
		.with("ultra-bold", ultraBold)//
		.getUnmodifiable();
	/** All font slants in this class, stored by the name the user can use to refer to them */
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

	@Override
	public String getDescription() {
		return "Font constant literal";
	}
}
