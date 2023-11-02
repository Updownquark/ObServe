package org.observe.quick.style;

import java.awt.Image;
import java.awt.MediaTracker;
import java.io.IOException;
import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.LocatedExpression;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.config.QommonsConfig;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.ErrorReporting;

public class QuickStyleUtils {
	/**
	 * Evaluates an icon in Quick
	 *
	 * @param expression The expression to parse
	 * @param env The expresso environment in which to parse the expression
	 * @return The ModelValueSynth to produce the icon value
	 * @throws ExpressoInterpretationException If the icon could not be evaluated
	 */
	public static InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> evaluateIcon(LocatedExpression expression,
		InterpretedExpressoEnv env) throws ExpressoInterpretationException {
		if (expression != null) {
			ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, NeverThrown, NeverThrown> tce = ExceptionHandler
				.holder2();
			InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> iconV = expression.interpret(ModelTypes.Value.forType(Icon.class),
				env, tce);
			if (iconV != null)
				return iconV;
			InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> imageV = expression
				.interpret(ModelTypes.Value.forType(Image.class), env, tce.clear());
			if (imageV != null)
				return imageV.map(ModelTypes.Value.forType(Icon.class), mvi -> mvi
					.map(sv -> SettableValue.asSettable(sv.map(img -> img == null ? null : new ImageIcon(img)), __ -> "Unsettable")));
			InterpretedValueSynth<SettableValue<?>, SettableValue<URL>> urlV = expression.interpret(ModelTypes.Value.forType(URL.class),
				env, tce.clear());
			if (urlV != null)
				return urlV.map(ModelTypes.Value.forType(Icon.class), mvi -> mvi
					.map(sv -> SettableValue.asSettable(sv.map(url -> url == null ? null : new ImageIcon(url)), __ -> "unsettable")));
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> stringV = expression
				.interpret(ModelTypes.Value.forType(String.class), env, tce.clear());
			ErrorReporting reporting = env.reporting().at(expression.getFilePosition());
			String sourceDocument = expression.getFilePosition().getFileLocation();
			if (stringV != null) {
				return stringV.map(ModelTypes.Value.forType(Icon.class), mvi -> mvi.map(sv -> SettableValue.asSettable(sv.map(loc -> {
					if (loc == null)
						return null;
					String relLoc;
					try {
						relLoc = QommonsConfig.resolve(loc, sourceDocument);
					} catch (IOException e) {
						reporting.at(expression.getFilePosition())
						.error("Could not resolve icon location '" + loc + "' relative to document " + sourceDocument);
						e.printStackTrace();
						return null;
					}
					Icon icon = ObservableSwingUtils.getFixedIcon(null, relLoc, 16, 16);
					if (icon == null)
						icon = ObservableSwingUtils.getFixedIcon(null, loc, 16, 16);
					if (icon == null)
						reporting.at(expression.getFilePosition()).error("Icon file not found: '" + loc);
					else if (icon instanceof ImageIcon && ((ImageIcon) icon).getImageLoadStatus() == MediaTracker.ERRORED)
						reporting.at(expression.getFilePosition()).error("Icon file could not be loaded: '" + loc);
					return icon;
				}), __ -> "unsettable")));
			}
			reporting.warn("Cannot evaluate '" + expression + "' as an icon");
			return InterpretedValueSynth.literalValue(TypeTokens.get().of(Icon.class), null, "Icon not provided");
		} else
			return InterpretedValueSynth.literalValue(TypeTokens.get().of(Icon.class), null, "None provided");
	}
}
