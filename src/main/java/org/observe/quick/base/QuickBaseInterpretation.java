package org.observe.quick.base;

import java.awt.Image;
import java.net.URL;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.quick.style.StyleQIS;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.ex.ExFunction;

/** {@link QonfigInterpretation} for the Quick-Base toolkit */
public class QuickBaseInterpretation implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String TOOLKIT_NAME = "Quick-Base";

	/** The version of the toolkit */
	public static final Version TOOLKIT_VERSION = new Version(0, 1, 0);

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
	}

	@Override
	public String getToolkitName() {
		return TOOLKIT_NAME;
	}

	@Override
	public Version getVersion() {
		return TOOLKIT_VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(Builder interpreter) {
		interpreter.createWith("box", QuickBox.Def.class, session -> QuickCoreInterpretation.interpretQuick(session, QuickBox.Def::new));
		interpreter.createWith("label", QuickLabel.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickLabel.Def::new));
		interpreter.createWith("inline", QuickInlineLayout.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new QuickInlineLayout.Def(ao, (QuickBox.Def<?>) p)));
		interpreter.createWith("text-field", QuickTextField.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickTextField.Def::new));
		interpreter.createWith("field", QuickField.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new QuickField.Def(ao, (QuickWidget.Def<?>) p)));
		interpreter.createWith("check-box", QuickCheckBox.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickCheckBox.Def::new));
		interpreter.createWith("field-panel", QuickFieldPanel.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickFieldPanel.Def::new));
		interpreter.createWith("button", QuickButton.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickButton.Def::new));
		interpreter.createWith("table", QuickTable.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickTable.Def::new));
		interpreter.createWith("column", QuickTableColumn.SingleColumnSet.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickTableColumn.SingleColumnSet.Def::new));
		return interpreter;
	}

	/**
	 * Evaluates an icon in Quick
	 *
	 * @param expression The expression to parse
	 * @param session The session in which to parse the expression
	 * @return The ModelValueSynth to produce the icon value
	 * @throws ExpressoInterpretationException If the icon could not be evaluated
	 */
	public static ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> evaluateIcon(CompiledExpression expression,
		ExpressoQIS session) throws ExpressoInterpretationException {
		if (expression != null) {
			ModelValueSynth<SettableValue<?>, SettableValue<?>> iconV = expression.evaluate(ModelTypes.Value.any(),
				session.getExpressoEnv());
			Class<?> iconType = TypeTokens.getRawType(iconV.getType().getType(0));
			if (Icon.class.isAssignableFrom(iconType))
				return msi -> (SettableValue<Icon>) iconV.get(msi);
				else if (Image.class.isAssignableFrom(iconType)) {
					return msi -> SettableValue.asSettable(iconV.get(msi).map(img -> img == null ? null : new ImageIcon((Image) img)),
						__ -> "unsettable");
				} else if (URL.class.isAssignableFrom(iconType)) {
					return msi -> SettableValue.asSettable(iconV.get(msi).map(url -> url == null ? null : new ImageIcon((URL) url)),
						__ -> "unsettable");
				} else if (String.class.isAssignableFrom(iconType)) {
					Class<?> callingClass = session.getWrapped().getInterpreter().getCallingClass();
					return msi -> SettableValue.asSettable(iconV.get(msi).map(loc -> loc == null ? null//
						: ObservableSwingUtils.getFixedIcon(callingClass, (String) loc, 16, 16)), __ -> "unsettable");
				} else {
					session.warn("Cannot use value " + expression + ", type " + iconV.getType().getType(0) + " as an icon");
					return msi -> SettableValue.of(Icon.class, null, "unsettable");
				}
		} else {
			return msi -> SettableValue.of(Icon.class, null, "None provided");
		}
	}
}
