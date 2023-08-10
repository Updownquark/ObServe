package org.observe.quick.base;

import java.awt.Image;
import java.awt.MediaTracker;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickDocument;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

import com.google.common.reflect.TypeToken;

/** {@link QonfigInterpretation} for the Quick-Base toolkit */
public class QuickBaseInterpretation implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String NAME = "Quick-Base";

	/** The version of the toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	static {
		TypeTokens.get().addSupplementaryCast(Integer.class, QuickSize.class, new TypeTokens.SupplementaryCast<Integer, QuickSize>() {
			@Override
			public TypeToken<? extends QuickSize> getCastType(TypeToken<? extends Integer> sourceType) {
				return TypeTokens.get().of(QuickSize.class);
			}

			@Override
			public QuickSize cast(Integer source) {
				return source == null ? null : QuickSize.ofPixels(source.intValue());
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public String canCast(Integer source) {
				return null;
			}
		});
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(Builder interpreter) {
		// General setup
		interpreter.modifyWith(QuickDocument.QUICK, QuickDocument.Def.class,
			new QonfigInterpreterCore.QonfigValueModifier<QuickDocument.Def>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				ExpressoQIS exS = session.as(ExpressoQIS.class);
				CompiledExpressoEnv env = exS.getExpressoEnv();
				exS.setExpressoEnv(env//
					.withNonStructuredParser(QuickSize.class, new QuickSize.Parser(true))//
					.withOperators(unaryOps(env.getUnaryOperators()), binaryOps(env.getBinaryOperators()))//
					);
				return null;
			}

			@Override
			public QuickDocument.Def modifyValue(QuickDocument.Def value, CoreSession session, Object prepared)
				throws QonfigInterpretationException {
				return value;
			}
		});

		// Simple widgets
		interpreter.createWith(QuickLabel.LABEL, QuickLabel.Def.class, ExElement.creator(QuickLabel.Def::new));
		interpreter.createWith(QuickTextField.TEXT_FIELD, QuickTextField.Def.class, ExElement.creator(QuickTextField.Def::new));
		interpreter.createWith(QuickCheckBox.CHECK_BOX, QuickCheckBox.Def.class, ExElement.creator(QuickCheckBox.Def::new));
		interpreter.createWith(QuickButton.BUTTON, QuickButton.Def.class, ExElement.creator(QuickButton.Def::new));
		interpreter.createWith(QuickTextArea.TEXT_AREA, QuickTextArea.Def.class, ExElement.creator(QuickTextArea.Def::new));
		interpreter.createWith(StyledTextArea.STYLED_TEXT_AREA, StyledTextArea.Def.class, ExElement.creator(StyledTextArea.Def::new));
		interpreter.createWith(StyledTextArea.TEXT_STYLE, StyledTextArea.TextStyleElement.Def.class,
			ExElement.creator(StyledTextArea.Def.class, StyledTextArea.TextStyleElement.Def::new));

		// Containers
		interpreter.createWith(QuickBox.BOX, QuickBox.Def.class, ExElement.creator(QuickBox.Def::new));
		interpreter.createWith(QuickFieldPanel.FIELD_PANEL, QuickFieldPanel.Def.class, ExElement.creator(QuickFieldPanel.Def::new));
		interpreter.createWith("field", QuickField.Def.class,
			ExAddOn.creator((Class<QuickWidget.Def<?>>) (Class<?>) QuickWidget.Def.class, QuickField.Def::new));
		interpreter.createWith(QuickSplit.SPLIT, QuickSplit.Def.class, ExElement.creator(QuickSplit.Def::new));
		interpreter.createWith(QuickScrollPane.SCROLL, QuickScrollPane.Def.class, ExElement.creator(QuickScrollPane.Def::new));

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
		interpreter.createWith("h-positionable", Positionable.Def.Horizontal.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new Positionable.Def.Horizontal(ao, p)));
		interpreter.createWith("v-positionable", Positionable.Def.Vertical.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new Positionable.Def.Vertical(ao, p)));
		interpreter.createWith("h-sizeable", Sizeable.Def.Horizontal.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new Sizeable.Def.Horizontal(ao, p)));
		interpreter.createWith("v-sizeable", Sizeable.Def.Vertical.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new Sizeable.Def.Vertical(ao, p)));

		// Table
		interpreter.createWith(QuickTable.TABLE, QuickTable.Def.class, ExElement.creator(QuickTable.Def::new));
		interpreter.createWith(QuickTableColumn.SingleColumnSet.COLUMN, QuickTableColumn.SingleColumnSet.Def.class,
			ExElement.creator(RowTyped.Def.class, QuickTableColumn.SingleColumnSet.Def::new));
		interpreter.createWith(QuickTableColumn.ColumnEditing.COLUMN_EDITING, QuickTableColumn.ColumnEditing.Def.class,
			ExElement.creator(QuickTableColumn.TableColumnSet.Def.class, QuickTableColumn.ColumnEditing.Def::new));
		interpreter.createWith("modify-row-value", QuickTableColumn.ColumnEditType.RowModifyEditType.Def.class,
			session -> new QuickTableColumn.ColumnEditType.RowModifyEditType.Def((QonfigAddOn) session.getFocusType(),
				(QuickTableColumn.ColumnEditing.Def) session.getElementRepresentation()));
		interpreter.createWith("replace-row-value", QuickTableColumn.ColumnEditType.RowReplaceEditType.Def.class,
			session -> new QuickTableColumn.ColumnEditType.RowReplaceEditType.Def((QonfigAddOn) session.getFocusType(),
				(QuickTableColumn.ColumnEditing.Def) session.getElementRepresentation()));
		interpreter.createWith(ValueAction.Single.SINGLE_VALUE_ACTION, ValueAction.Single.Def.class, ExElement.creator(ValueAction.Single.Def::new));
		interpreter.createWith(ValueAction.Multi.MULTI_VALUE_ACTION, ValueAction.Multi.Def.class, ExElement.creator(ValueAction.Multi.Def::new));
		return interpreter;
	}

	private static UnaryOperatorSet unaryOps(UnaryOperatorSet unaryOps) {
		return unaryOps.copy()//
			.with("-", QuickSize.class, s -> new QuickSize(-s.percent, s.pixels), s -> new QuickSize(-s.percent, s.pixels),
				"Size negation operator")//
			.build();
	}

	private static BinaryOperatorSet binaryOps(BinaryOperatorSet binaryOps) {
		return binaryOps.copy()//
			.with("+", QuickSize.class, Double.class, (s, d) -> new QuickSize(s.percent, s.pixels + (int) Math.round(d)),
				(s, d, o) -> new QuickSize(s.percent, s.pixels - (int) Math.round(d)), null, "Size addition operator")//
			.with("-", QuickSize.class, Double.class, (p, d) -> new QuickSize(p.percent, p.pixels - (int) Math.round(d)),
				(s1, s2, o) -> new QuickSize(s1.percent, s1.pixels + (int) Math.round(s2)), null, "Size subtraction operator")//
			.with("+", QuickSize.class, QuickSize.class, QuickSize::plus,
				(s1, s2, o) -> new QuickSize(s1.percent - s2.percent, s1.pixels - s2.pixels), null, "Size addition operator")//
			.with("-", QuickSize.class, QuickSize.class, QuickSize::minus,
				(s1, s2, o) -> new QuickSize(s1.percent + s2.percent, s1.pixels + s2.pixels), null, "Size subtraction operator")//
			.with("*", QuickSize.class, Double.class, (s, d) -> new QuickSize((float) (s.percent * d), (int) Math.round(s.pixels * d)),
				(s, d, o) -> new QuickSize((float) (s.percent / d), (int) Math.round(s.pixels / d)), null, "Size multiplication operator")//
			.with("/", QuickSize.class, Double.class, (s, d) -> new QuickSize((float) (s.percent / d), (int) Math.round(s.pixels / d)),
				(s, d, o) -> new QuickSize((float) (s.percent * d), (int) Math.round(s.pixels * d)), null, "Size division operator")//
			.build();
	}

	/**
	 * Evaluates an icon in Quick
	 *
	 * @param expression The expression to parse
	 * @param env The expresso environment in which to parse the expression
	 * @param sourceDocument The location of the document that the icon source may be relative to
	 * @return The ModelValueSynth to produce the icon value
	 * @throws ExpressoInterpretationException If the icon could not be evaluated
	 */
	public static InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> evaluateIcon(CompiledExpression expression,
		InterpretedExpressoEnv env, String sourceDocument) throws ExpressoInterpretationException {
		if (expression != null) {
			InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>> iconV = expression.interpret(ModelTypes.Value.any(), env);
			Class<?> iconType = TypeTokens.getRawType(iconV.getType().getType(0));
			if (Icon.class.isAssignableFrom(iconType))
				return (InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>>) iconV;
			else if (Image.class.isAssignableFrom(iconType)) {
				return iconV.map(ModelTypes.Value.forType(Icon.class),
					sv -> SettableValue.asSettable(sv.map(img -> img == null ? null : new ImageIcon((Image) img)), __ -> "Unsettable"));
			} else if (URL.class.isAssignableFrom(iconType)) {
				return iconV.map(ModelTypes.Value.forType(Icon.class),
					sv -> SettableValue.asSettable(sv.map(url -> url == null ? null : new ImageIcon((URL) url)), __ -> "unsettable"));
			} else if (String.class.isAssignableFrom(iconType)) {
				return iconV.map(ModelTypes.Value.forType(Icon.class), sv -> SettableValue.asSettable(sv.map(loc -> {
					if (loc == null)
						return null;
					String relLoc;
					try {
						relLoc = QommonsConfig.resolve((String) loc, sourceDocument);
					} catch (IOException e) {
						System.err.println("Could not resolve icon location '" + loc + "' relative to document " + sourceDocument);
						e.printStackTrace();
						return null;
					}
					ImageIcon relIcon = new ImageIcon(relLoc);
					if (relIcon.getImageLoadStatus() == MediaTracker.ERRORED)
						return ObservableSwingUtils.getFixedIcon(null, (String) loc, 16, 16);
					else {
						relIcon = new ImageIcon(relIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
						return relIcon;
					}
				}), __ -> "unsettable"));
			} else {
				env.reporting().warn("Cannot use value " + expression + ", type " + iconV.getType().getType(0) + " as an icon");
				return InterpretedValueSynth.literal(TypeTokens.get().of(Icon.class), null, "Icon not provided");
			}
		} else
			return InterpretedValueSynth.literal(TypeTokens.get().of(Icon.class), null, "None provided");
	}
}
