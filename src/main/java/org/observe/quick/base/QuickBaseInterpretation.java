package org.observe.quick.base;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickDocument;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
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

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String BASE = "Quick-Base v0.1";

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
	public void init(QonfigToolkit toolkit) {}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(Builder interpreter) {
		// General setup
		interpreter.modifyWith(QuickDocument.QUICK, QuickDocument.Def.class,
			new QonfigInterpreterCore.QonfigValueModifier<QuickDocument.Def>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				ExpressoQIS exS = session.as(ExpressoQIS.class);
				CompiledExpressoEnv env = exS.getExpressoEnv();
				env = env//
					.withNonStructuredParser(QuickSize.class, new QuickSize.Parser(true))//
					.withOperators(unaryOps(env.getUnaryOperators()), binaryOps(env.getBinaryOperators()));
				exS.setExpressoEnv(env);
				return null;
			}

			@Override
			public QuickDocument.Def modifyValue(QuickDocument.Def value, CoreSession session, Object prepared)
				throws QonfigInterpretationException {
				return value;
			}
		});
		interpreter.createWith(QuickCustomShadingElement.CUSTOM_SHADING, QuickCustomShadingElement.class,
			ExElement.creator(QuickCustomShadingElement::new));
		interpreter.createWith(QuickRaisedShadingElement.RAISED_SHADING, QuickRaisedShadingElement.class,
			ExElement.creator(QuickRaisedShadingElement::new));

		// Simple widgets
		interpreter.createWith(QuickLabel.LABEL, QuickLabel.Def.class, ExElement.creator(QuickLabel.Def::new));
		interpreter.createWith(QuickTextField.TEXT_FIELD, QuickTextField.Def.class, ExElement.creator(QuickTextField.Def::new));
		interpreter.createWith(QuickCheckBox.CHECK_BOX, QuickCheckBox.Def.class, ExElement.creator(QuickCheckBox.Def::new));
		interpreter.createWith(QuickToggleButton.TOGGLE_BUTTON, QuickToggleButton.Def.class, ExElement.creator(QuickToggleButton.Def::new));
		interpreter.createWith(QuickRadioButton.RADIO_BUTTON, QuickRadioButton.Def.class, ExElement.creator(QuickRadioButton.Def::new));
		interpreter.createWith(QuickButton.BUTTON, QuickButton.Def.class, ExElement.creator(QuickButton.Def::new));
		interpreter.createWith(QuickFileButton.FILE_BUTTON, QuickFileButton.Def.class, ExElement.creator(QuickFileButton.Def::new));
		interpreter.createWith(QuickComboBox.COMBO_BOX, QuickComboBox.Def.class, ExElement.creator(QuickComboBox.Def::new));
		interpreter.createWith(QuickSlider.SLIDER, QuickSlider.Def.class, ExElement.creator(QuickSlider.Def::new));
		interpreter.createWith(QuickSpinner.SPINNER, QuickSpinner.Def.class, ExElement.creator(QuickSpinner.Def::new));
		interpreter.createWith(QuickColorChooser.COLOR_CHOOSER, QuickColorChooser.Def.class, ExElement.creator(QuickColorChooser.Def::new));
		interpreter.createWith(QuickRadioButtons.RADIO_BUTTONS, QuickRadioButtons.Def.class, ExElement.creator(QuickRadioButtons.Def::new));
		interpreter.createWith(QuickToggleButtons.TOGGLE_BUTTONS, QuickToggleButtons.Def.class,
			ExElement.creator(QuickToggleButtons.Def::new));
		interpreter.createWith(QuickTextArea.TEXT_AREA, QuickTextArea.Def.class, ExElement.creator(QuickTextArea.Def::new));
		interpreter.createWith(QuickProgressBar.PROGRESS_BAR, QuickProgressBar.Def.class, ExElement.creator(QuickProgressBar.Def::new));
		interpreter.createWith(DynamicStyledDocument.DYNAMIC_STYLED_DOCUMENT, DynamicStyledDocument.Def.class,
			ExElement.creator(DynamicStyledDocument.Def::new));
		interpreter.createWith(StyledDocument.TEXT_STYLE, StyledDocument.TextStyleElement.Def.class,
			ExElement.creator(StyledDocument.Def.class, StyledDocument.TextStyleElement.Def::new));
		interpreter.createWith(QuickSpacer.SPACER, QuickSpacer.Def.class, ExElement.creator(QuickSpacer.Def::new));

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
			ExElement.creator(ValueTyped.Def.class, QuickTableColumn.SingleColumnSet.Def::new));
		interpreter.createWith(QuickTableColumn.ColumnEditing.COLUMN_EDITING, QuickTableColumn.ColumnEditing.Def.class,
			ExElement.creator(QuickTableColumn.TableColumnSet.Def.class, QuickTableColumn.ColumnEditing.Def::new));
		interpreter.createWith("modify-row-value", QuickTableColumn.ColumnEditType.RowModifyEditType.Def.class,
			session -> new QuickTableColumn.ColumnEditType.RowModifyEditType.Def((QonfigAddOn) session.getFocusType(),
				(QuickTableColumn.ColumnEditing.Def) session.getElementRepresentation()));
		interpreter.createWith("replace-row-value", QuickTableColumn.ColumnEditType.RowReplaceEditType.Def.class,
			session -> new QuickTableColumn.ColumnEditType.RowReplaceEditType.Def((QonfigAddOn) session.getFocusType(),
				(QuickTableColumn.ColumnEditing.Def) session.getElementRepresentation()));
		interpreter.createWith(ValueAction.Single.SINGLE_VALUE_ACTION, ValueAction.Single.Def.class,
			ExElement.creator(ValueAction.Single.Def::new));
		interpreter.createWith(ValueAction.Multi.MULTI_VALUE_ACTION, ValueAction.Multi.Def.class,
			ExElement.creator(ValueAction.Multi.Def::new));

		// Tabs
		interpreter.createWith(QuickTabs.TABS, QuickTabs.Def.class, ExElement.creator(QuickTabs.Def::new));
		interpreter.createWith(QuickTabs.AbstractTab.ABSTRACT_TAB, QuickTabs.AbstractTab.Def.class,
			ExAddOn.creator(QuickTabs.AbstractTab.Def::new));
		interpreter.createWith(QuickTabs.Tab.TAB, QuickTabs.Tab.Def.class, ExAddOn.creator(QuickWidget.Def.class, QuickTabs.Tab.Def::new));
		interpreter.createWith(QuickTabs.TabSet.TAB_SET, QuickTabs.TabSet.Def.class, ExElement.creator(QuickTabs.TabSet.Def::new));

		// Tree
		interpreter.createWith(QuickTree.TREE, QuickTree.Def.class, ExElement.creator(QuickTree.Def::new));
		interpreter.createWith(DynamicTreeModel.DYNAMIC_TREE_MODEL, DynamicTreeModel.Def.class,
			ExElement.creator(DynamicTreeModel.Def::new));
		interpreter.createWith(StaticTreeNode.TREE_NODE, StaticTreeNode.Def.class, ExElement.creator(StaticTreeNode.Def::new));

		// Dialogs
		interpreter.createWith(QuickInfoDialog.INFO_DIALOG, QuickInfoDialog.Def.class, ExElement.creator(QuickInfoDialog.Def::new));
		interpreter.createWith(QuickConfirm.CONFIRM, QuickConfirm.Def.class, ExElement.creator(QuickConfirm.Def::new));
		interpreter.createWith(QuickFileChooser.FILE_CHOOSER, QuickFileChooser.Def.class, ExElement.creator(QuickFileChooser.Def::new));
		interpreter.createWith(GeneralDialog.GENERAL_DIALOG, GeneralDialog.Def.class, ExElement.creator(GeneralDialog.Def::new));

		// Menus
		interpreter.createWith(QuickMenuContainer.MENU_CONTAINER, QuickMenuContainer.Def.class,
			ExAddOn.creator(QuickMenuContainer.Def::new));
		interpreter.createWith(QuickMenuBar.MENU_BAR, QuickMenuBar.Def.class, ExElement.creator(QuickMenuBar.Def::new));
		interpreter.createWith(QuickMenu.MENU, QuickMenu.Def.class, ExElement.creator(QuickMenu.Def::new));
		interpreter.createWith(QuickMenuItem.MENU_ITEM, QuickMenuItem.Def.class, ExElement.creator(QuickMenuItem.Def::new));
		interpreter.createWith(QuickCheckBoxMenuItem.CHECK_BOX_MENU_ITEM, QuickCheckBoxMenuItem.Def.class,
			ExElement.creator(QuickCheckBoxMenuItem.Def::new));

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
				(s1, s2, o) -> new QuickSize(s1.percent - s2.percent, s1.pixels + s2.pixels), null, "Size subtraction operator")//
			.with("*", QuickSize.class, Double.class, (s, d) -> new QuickSize((float) (s.percent * d), (int) Math.round(s.pixels * d)),
				(s, d, o) -> new QuickSize((float) (s.percent / d), (int) Math.round(s.pixels / d)), null, "Size multiplication operator")//
			.with2("*", Double.class, QuickSize.class, QuickSize.class,
				(d, s) -> new QuickSize((float) (s.percent * d), (int) Math.round(s.pixels * d)), (d, s, o) -> {
					if (s == null)
						return Double.NaN;
					if (s.percent != 0.0f)
						return o.percent * 1.0 / s.percent;
					else
						return o.pixels * 1.0 / s.pixels;
				}, null, "Size multiplication operator")//
			.with("/", QuickSize.class, Double.class, (s, d) -> new QuickSize((float) (s.percent / d), (int) Math.round(s.pixels / d)),
				(s, d, o) -> new QuickSize((float) (s.percent * d), (int) Math.round(s.pixels * d)), null, "Size division operator")//
			.build();
	}

	@ExElementTraceable(toolkit = BASE,
		qonfigType = QuickCustomShadingElement.CUSTOM_SHADING,
		interpretation = QuickCustomShadingElement.Interpreted.class)
	static class QuickCustomShadingElement
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<QuickShading>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<QuickShading>>> {
		public static final String CUSTOM_SHADING = "custom-shading";

		private CompiledExpression theUnitWidth;
		private CompiledExpression theUnitHeight;
		private boolean isStretchX;
		private boolean isStretchY;
		private CompiledExpression theLit;
		private CompiledExpression theOpacity;
		private CompiledExpression theRefresh;

		private ModelComponentId theWidthVariable;
		private ModelComponentId theHeightVariable;
		private ModelComponentId theXVariable;
		private ModelComponentId theYVariable;
		private ModelComponentId thePXVariable;
		private ModelComponentId thePYVariable;

		public QuickCustomShadingElement(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@QonfigAttributeGetter("unit-width")
		public CompiledExpression getUnitWidth() {
			return theUnitWidth;
		}

		@QonfigAttributeGetter("unit-height")
		public CompiledExpression getUnitHeight() {
			return theUnitHeight;
		}

		@QonfigAttributeGetter("stretch-x")
		public boolean isStretchX() {
			return isStretchX;
		}

		@QonfigAttributeGetter("stretch-y")
		public boolean isStretchY() {
			return isStretchY;
		}

		@QonfigAttributeGetter("lit")
		public CompiledExpression getLit() {
			return theLit;
		}

		@QonfigAttributeGetter("opacity")
		public CompiledExpression getOpacity() {
			return theOpacity;
		}

		@QonfigAttributeGetter("refresh")
		public CompiledExpression getRefresh() {
			return theRefresh;
		}

		public ModelComponentId getWidthVariable() {
			return theWidthVariable;
		}

		public ModelComponentId getHeightVariable() {
			return theHeightVariable;
		}

		public ModelComponentId getXVariable() {
			return theXVariable;
		}

		public ModelComponentId getYVariable() {
			return theYVariable;
		}

		public ModelComponentId getPXVariable() {
			return thePXVariable;
		}

		public ModelComponentId getPYVariable() {
			return thePYVariable;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			theUnitWidth = getAttributeExpression("unit-width", session);
			theUnitHeight = getAttributeExpression("unit-height", session);
			isStretchX = session.getAttribute("stretch-x", boolean.class);
			isStretchY = session.getAttribute("stretch-y", boolean.class);
			theLit = getAttributeExpression("lit", session);
			theOpacity = getAttributeExpression("opacity", session);
			theRefresh = getAttributeExpression("refresh", session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theWidthVariable = elModels.getElementValueModelId("width");
			theHeightVariable = elModels.getElementValueModelId("height");
			theXVariable = elModels.getElementValueModelId("x");
			theYVariable = elModels.getElementValueModelId("y");
			thePXVariable = elModels.getElementValueModelId("px");
			thePYVariable = elModels.getElementValueModelId("py");
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<QuickShading>, ModelValueElement<SettableValue<?>, SettableValue<QuickShading>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<QuickShading>, ModelValueElement<SettableValue<?>, SettableValue<QuickShading>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theUnitWidth;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theUnitHeight;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> theLit;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> theOpacity;
			private InterpretedValueSynth<Observable<?>, Observable<?>> theRefresh;

			Interpreted(QuickCustomShadingElement definition) {
				super(definition, null);
			}

			@Override
			public QuickCustomShadingElement getDefinition() {
				return (QuickCustomShadingElement) super.getDefinition();
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getUnitWidth() {
				return theUnitWidth;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getUnitHeight() {
				return theUnitHeight;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> getLit() {
				return theLit;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> getOpaque() {
				return theOpacity;
			}

			public InterpretedValueSynth<Observable<?>, Observable<?>> getRefresh() {
				return theRefresh;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				theUnitWidth = interpret(getDefinition().getUnitWidth(), ModelTypes.Value.INT);
				theUnitHeight = interpret(getDefinition().getUnitHeight(), ModelTypes.Value.INT);
				theLit = interpret(getDefinition().getLit(), ModelTypes.Value.forType(float.class));
				theOpacity = interpret(getDefinition().getOpacity(), ModelTypes.Value.forType(float.class));
				theRefresh = interpret(getDefinition().getRefresh(), ModelTypes.Event.any());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theUnitWidth, theUnitHeight, theLit, theOpacity, theRefresh).quickFilter(v -> v != null);
			}

			@Override
			public ModelValueInstantiator<SettableValue<QuickShading>> instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<QuickShading>> {
			private final ModelInstantiator theLocalModel;
			private final ModelValueInstantiator<SettableValue<Integer>> theUnitWidth;
			private final ModelValueInstantiator<SettableValue<Integer>> theUnitHeight;
			private final boolean isStretchX;
			private final boolean isStretchY;
			private final ModelValueInstantiator<SettableValue<Float>> theLit;
			private final ModelValueInstantiator<SettableValue<Float>> theOpacity;
			private final ModelValueInstantiator<Observable<?>> theRefresh;

			private final ModelComponentId theWidthVariable;
			private final ModelComponentId theHeightVariable;
			private final ModelComponentId theXVariable;
			private final ModelComponentId theYVariable;
			private final ModelComponentId thePXVariable;
			private final ModelComponentId thePYVariable;

			Instantiator(Interpreted interpreted) {
				theLocalModel = interpreted.getModels().instantiate();
				theUnitWidth = interpreted.getUnitWidth() == null ? null : interpreted.getUnitWidth().instantiate();
				theUnitHeight = interpreted.getUnitHeight() == null ? null : interpreted.getUnitHeight().instantiate();
				isStretchX = interpreted.getDefinition().isStretchX();
				isStretchY = interpreted.getDefinition().isStretchY();
				theLit = interpreted.getLit().instantiate();
				theOpacity = interpreted.getOpaque() == null ? null : interpreted.getOpaque().instantiate();
				theRefresh = interpreted.getRefresh() == null ? null : interpreted.getRefresh().instantiate();
				theWidthVariable = interpreted.getDefinition().getWidthVariable();
				theHeightVariable = interpreted.getDefinition().getHeightVariable();
				theXVariable = interpreted.getDefinition().getXVariable();
				theYVariable = interpreted.getDefinition().getYVariable();
				thePXVariable = interpreted.getDefinition().getPXVariable();
				thePYVariable = interpreted.getDefinition().getPYVariable();
			}

			@Override
			public void instantiate() {
				theLocalModel.instantiate();
				if (theUnitWidth != null)
					theUnitWidth.instantiate();
				if (theUnitHeight != null)
					theUnitHeight.instantiate();
				if (theLit != null)
					theLit.instantiate();
				if (theOpacity != null)
					theOpacity.instantiate();
				if (theRefresh != null)
					theRefresh.instantiate();
			}

			@Override
			public SettableValue<QuickShading> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				models = theLocalModel.wrap(models);
				SettableValue<Integer> width = SettableValue.build(int.class).withDescription("width").withValue(0).build();
				SettableValue<Integer> height = SettableValue.build(int.class).withDescription("height").withValue(0).build();
				SettableValue<Integer> x = SettableValue.build(int.class).withDescription("x").withValue(0).build();
				SettableValue<Integer> y = SettableValue.build(int.class).withDescription("y").withValue(0).build();
				SettableValue<Float> px = SettableValue.asSettable(x.transform(float.class, tx -> tx//
					.combineWith(width).combine((xv, wv) -> xv * 1.0f / wv)), __ -> "Not Settable");
				SettableValue<Float> py = SettableValue.asSettable(y.transform(float.class, tx -> tx//
					.combineWith(height).combine((yv, hv) -> yv * 1.0f / hv)), __ -> "Not Settable");
				ExFlexibleElementModelAddOn.satisfyElementValue(theWidthVariable, models, width);
				ExFlexibleElementModelAddOn.satisfyElementValue(theHeightVariable, models, height);
				ExFlexibleElementModelAddOn.satisfyElementValue(theXVariable, models, x);
				ExFlexibleElementModelAddOn.satisfyElementValue(theYVariable, models, y);
				ExFlexibleElementModelAddOn.satisfyElementValue(thePXVariable, models, px);
				ExFlexibleElementModelAddOn.satisfyElementValue(thePYVariable, models, py);

				SettableValue<Integer> unitWidth = theUnitWidth == null ? null : theUnitWidth.get(models);
				SettableValue<Integer> unitHeight = theUnitHeight == null ? null : theUnitHeight.get(models);
				SettableValue<Float> lit = theLit.get(models);
				SettableValue<Float> opacity = theOpacity == null ? null : theOpacity.get(models);
				Observable<?> refresh = theRefresh == null ? null : theRefresh.get(models);
				return SettableValue.of(QuickShading.class,
					new QuickCustomShading(width, height, x, y, unitWidth, unitHeight, isStretchX, isStretchY, lit, opacity, refresh),
					"Not Settable");
			}

			@Override
			public SettableValue<QuickShading> forModelCopy(SettableValue<QuickShading> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return get(newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = BASE,
		qonfigType = QuickRaisedShadingElement.RAISED_SHADING,
		interpretation = QuickCustomShadingElement.Interpreted.class)
	static class QuickRaisedShadingElement
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<QuickShading>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<QuickShading>>> {
		public static final String RAISED_SHADING = "raised-shading";

		private boolean isRound;
		private boolean isHorizontal;
		private boolean isVertical;
		private CompiledExpression theOpacity;

		public QuickRaisedShadingElement(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@QonfigAttributeGetter("round")
		public boolean isRound() {
			return isRound;
		}

		@QonfigAttributeGetter("horizontal")
		public boolean isHorizontal() {
			return isHorizontal;
		}

		@QonfigAttributeGetter("vertical")
		public boolean isVertical() {
			return isVertical;
		}

		@QonfigAttributeGetter("opacity")
		public CompiledExpression getOpacity() {
			return theOpacity;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			isRound = session.getAttribute("round", boolean.class);
			isHorizontal = session.getAttribute("horizontal", boolean.class);
			isVertical = session.getAttribute("vertical", boolean.class);
			theOpacity = getAttributeExpression("opacity", session);

			if (!isVertical && !isHorizontal)
				reporting().warn("Useless raised-shading with neither horizontal nor vertical");
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<QuickShading>, ModelValueElement<SettableValue<?>, SettableValue<QuickShading>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<QuickShading>, ModelValueElement<SettableValue<?>, SettableValue<QuickShading>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theOpacity;

			Interpreted(QuickRaisedShadingElement definition) {
				super(definition, null);
			}

			@Override
			public QuickRaisedShadingElement getDefinition() {
				return (QuickRaisedShadingElement) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getOpacity() {
				return theOpacity;
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theOpacity = interpret(getDefinition().getOpacity(), ModelTypes.Value.DOUBLE);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<SettableValue<QuickShading>> instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<QuickShading>> {
			private boolean isRound;
			private boolean isHorizontal;
			private boolean isVertical;
			private ModelValueInstantiator<SettableValue<Double>> theOpacity;

			Instantiator(Interpreted interpreted) {
				isRound = interpreted.getDefinition().isRound();
				isHorizontal = interpreted.getDefinition().isHorizontal();
				isVertical = interpreted.getDefinition().isVertical();
				theOpacity = interpreted.getOpacity() == null ? null : interpreted.getOpacity().instantiate();
			}

			@Override
			public void instantiate() {}

			@Override
			public SettableValue<QuickShading> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(QuickShading.class,
					new QuickRaisedShading(isRound, isHorizontal, isVertical, theOpacity == null ? null : theOpacity.get(models)),
					"Not Settable");
			}

			@Override
			public SettableValue<QuickShading> forModelCopy(SettableValue<QuickShading> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return get(newModels);
			}
		}
	}
}
