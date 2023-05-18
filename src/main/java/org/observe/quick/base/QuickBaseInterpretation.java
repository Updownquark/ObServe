package org.observe.quick.base;

import java.awt.Image;
import java.net.URL;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickDocument2;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickWidget;
import org.observe.quick.style.StyleQIS;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.ex.ExFunction;

import com.google.common.reflect.TypeToken;

/** {@link QonfigInterpretation} for the Quick-Base toolkit */
public class QuickBaseInterpretation implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String TOOLKIT_NAME = "Quick-Base";

	/** The version of the toolkit */
	public static final Version TOOLKIT_VERSION = new Version(0, 1, 0);

	static {
		TypeTokens.get().addSupplementaryCast(Integer.class, QuickSize.class, new TypeTokens.SupplementaryCast<Integer, QuickSize>() {
			@Override
			public TypeToken<? extends QuickSize> getCastType(TypeToken<? extends Integer> sourceType) {
				return TypeTokens.get().of(QuickSize.class);
			}

			@Override
			public QuickSize cast(Integer source) {
				return QuickSize.ofPixels(source.intValue());
			}
		});
	}

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
		// General setup
		interpreter.modifyWith("quick", QuickDocument2.Def.class, new QonfigInterpreterCore.QonfigValueModifier<QuickDocument2.Def>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				ExpressoQIS exS = session.as(ExpressoQIS.class);
				ExpressoEnv env = exS.getExpressoEnv();
				exS.setExpressoEnv(env//
					.withNonStructuredParser(QuickSize.class, new QuickSize.Parser(true))//
					.withOperators(unaryOps(env.getUnaryOperators()), binaryOps(env.getBinaryOperators()))//
					);
				return null;
			}

			@Override
			public QuickDocument2.Def modifyValue(QuickDocument2.Def value, CoreSession session, Object prepared)
				throws QonfigInterpretationException {
				return value;
			}
		});

		// Simple widgets
		interpreter.createWith("label", QuickLabel.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickLabel.Def::new));
		interpreter.createWith("text-field", QuickTextField.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickTextField.Def::new));
		interpreter.createWith("check-box", QuickCheckBox.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickCheckBox.Def::new));
		interpreter.createWith("button", QuickButton.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickButton.Def::new));

		// Containers
		interpreter.createWith("box", QuickBox.Def.class, session -> QuickCoreInterpretation.interpretQuick(session, QuickBox.Def::new));
		interpreter.createWith("field-panel", QuickFieldPanel.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickFieldPanel.Def::new));
		interpreter.createWith("field", QuickField.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new QuickField.Def(ao, (QuickWidget.Def<?>) p)));
		interpreter.createWith("split", QuickSplit.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickSplit.Def::new));

		// Box layouts
		interpreter.createWith("inline-layout", QuickInlineLayout.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new QuickInlineLayout.Def(ao, (QuickBox.Def<?>) p)));
		interpreter.createWith("simple-layout", QuickSimpleLayout.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new QuickSimpleLayout.Def(ao, (QuickBox.Def<?>) p)));
		interpreter.createWith("simple-layout-child", QuickSimpleLayout.Child.Def.class, session -> QuickCoreInterpretation
			.interpretAddOn(session, (p, ao) -> new QuickSimpleLayout.Child.Def(ao, (QuickWidget.Def<?>) p)));
		interpreter.createWith("border-layout", QuickBorderLayout.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new QuickBorderLayout.Def(ao, (QuickBox.Def<?>) p)));
		interpreter.createWith("border-layout-child", QuickBorderLayout.Child.Def.class, session -> QuickCoreInterpretation
			.interpretAddOn(session, (p, ao) -> new QuickBorderLayout.Child.Def(ao, (QuickWidget.Def<?>) p)));
		interpreter.createWith("h-positionable", Positionable.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new Positionable.Def.Horizontal(ao, p)));
		interpreter.createWith("v-positionable", Positionable.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new Positionable.Def.Vertical(ao, p)));
		interpreter.createWith("h-sizeable", Sizeable.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new Sizeable.Def.Horizontal(ao, p)));
		interpreter.createWith("v-sizeable", Sizeable.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new Sizeable.Def.Vertical(ao, p)));

		// Table
		interpreter.createWith("table", QuickTable.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickTable.Def::new));
		interpreter.createWith("column", QuickTableColumn.SingleColumnSet.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session,
				(p, el) -> new QuickTableColumn.SingleColumnSet.Def((RowTyped.Def<?>) p, el)));
		interpreter.createWith("column-edit", QuickTableColumn.ColumnEditing.Def.class, session -> QuickCoreInterpretation
			.interpretQuick(session, (p, el) -> new QuickTableColumn.ColumnEditing.Def((QuickTableColumn.TableColumnSet.Def<?>) p, el)));
		interpreter.createWith("modify-row-value", QuickTableColumn.ColumnEditType.RowModifyEditType.Def.class,
			session -> new QuickTableColumn.ColumnEditType.RowModifyEditType.Def((QonfigAddOn) session.getFocusType(),
				(QuickTableColumn.ColumnEditing.Def) session.get(QuickElement.SESSION_QUICK_ELEMENT)));
		interpreter.createWith("replace-row-value", QuickTableColumn.ColumnEditType.RowReplaceEditType.Def.class,
			session -> new QuickTableColumn.ColumnEditType.RowReplaceEditType.Def((QonfigAddOn) session.getFocusType(),
				(QuickTableColumn.ColumnEditing.Def) session.get(QuickElement.SESSION_QUICK_ELEMENT)));
		return interpreter;
	}

	private static UnaryOperatorSet unaryOps(UnaryOperatorSet unaryOps) {
		return unaryOps.copy()//
			.with("-", QuickSize.class, s -> new QuickSize(-s.percent, s.pixels), s -> new QuickSize(-s.percent, s.pixels))//
			.build();
	}

	private static BinaryOperatorSet binaryOps(BinaryOperatorSet binaryOps) {
		return binaryOps.copy()//
			.with("+", QuickSize.class, Double.class, (s, d) -> new QuickSize(s.percent, s.pixels + (int) Math.round(d)),
				(s, d, o) -> new QuickSize(s.percent, s.pixels - (int) Math.round(d)), null)//
			.with("-", QuickSize.class, Double.class, (p, d) -> new QuickSize(p.percent, p.pixels - (int) Math.round(d)),
				(s1, s2, o) -> new QuickSize(s1.percent, s1.pixels + (int) Math.round(s2)), null)//
			.with("+", QuickSize.class, QuickSize.class, QuickSize::plus,
				(s1, s2, o) -> new QuickSize(s1.percent - s2.percent, s1.pixels - s2.pixels), null)//
			.with("-", QuickSize.class, QuickSize.class, QuickSize::minus,
				(s1, s2, o) -> new QuickSize(s1.percent + s2.percent, s1.pixels + s2.pixels), null)//
			.with("*", QuickSize.class, Double.class, (s, d) -> new QuickSize((float) (s.percent * d), (int) Math.round(s.pixels * d)),
				(s, d, o) -> new QuickSize((float) (s.percent / d), (int) Math.round(s.pixels / d)), null)//
			.with("/", QuickSize.class, Double.class, (s, d) -> new QuickSize((float) (s.percent / d), (int) Math.round(s.pixels / d)),
				(s, d, o) -> new QuickSize((float) (s.percent * d), (int) Math.round(s.pixels * d)), null)//
			.build();
	}

	/**
	 * Evaluates an icon in Quick
	 *
	 * @param expression The expression to parse
	 * @param env The expresso environment in which to parse the expression
	 * @return The ModelValueSynth to produce the icon value
	 * @throws ExpressoInterpretationException If the icon could not be evaluated
	 */
	public static ExFunction<ModelSetInstance, SettableValue<Icon>, ModelInstantiationException> evaluateIcon(CompiledExpression expression,
		ExpressoEnv env, Class<?> callingClass) throws ExpressoInterpretationException {
		if (expression != null) {
			ModelValueSynth<SettableValue<?>, SettableValue<?>> iconV = expression.evaluate(ModelTypes.Value.any(), env);
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
					return msi -> SettableValue.asSettable(iconV.get(msi).map(loc -> loc == null ? null//
						: ObservableSwingUtils.getFixedIcon(callingClass, (String) loc, 16, 16)), __ -> "unsettable");
				} else {
					env.reporting().warn("Cannot use value " + expression + ", type " + iconV.getType().getType(0) + " as an icon");
					return msi -> SettableValue.of(Icon.class, null, "unsettable");
				}
		} else {
			return msi -> SettableValue.of(Icon.class, null, "None provided");
		}
	}
}
