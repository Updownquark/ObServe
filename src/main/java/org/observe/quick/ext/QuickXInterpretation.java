package org.observe.quick.ext;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** {@link QonfigInterpretation} for the Quick-X toolkit */
public class QuickXInterpretation implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String NAME = "Quick-X";

	/** The version of the toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String X = "Quick-X v0.1";

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
		// Shading
		interpreter.createWith(QuickShaded.SHADED, QuickShaded.Def.class, ExAddOn.creator(QuickShaded.Def::new));
		interpreter.createWith(QuickCustomShadingElement.CUSTOM_SHADING, QuickCustomShadingElement.class,
			ExElement.creator(QuickCustomShadingElement::new));
		interpreter.createWith(QuickRaisedShadingElement.RAISED_SHADING, QuickRaisedShadingElement.class,
			ExElement.creator(QuickRaisedShadingElement::new));

		interpreter.createWith(QuickCollapsePane.COLLAPSE_PANE, QuickCollapsePane.Def.class, ExElement.creator(QuickCollapsePane.Def::new));
		interpreter.createWith(QuickTreeTable.TREE_TABLE, QuickTreeTable.Def.class, ExElement.creator(QuickTreeTable.Def::new));
		interpreter.createWith(QuickComboButton.COMBO_BUTTON, QuickComboButton.Def.class, ExElement.creator(QuickComboButton.Def::new));
		interpreter.createWith(QuickMultiSlider.MULTI_SLIDER, QuickMultiSlider.Def.class, ExElement.creator(QuickMultiSlider.Def::new));
		interpreter.createWith(QuickMultiSlider.SLIDER_HANDLE_RENDERER, QuickMultiSlider.SliderHandleRenderer.Def.class,
			ExElement.creator(QuickMultiSlider.SliderHandleRenderer.Def::new));
		interpreter.createWith(QuickMultiSlider.SLIDER_BG_RENDERER, QuickMultiSlider.SliderBgRenderer.Def.class,
			ExElement.creator(QuickMultiSlider.SliderBgRenderer.Def::new));

		interpreter.createWith(QuickTiledPane.TILED_PANE, QuickTiledPane.Def.class, ExElement.creator(QuickTiledPane.Def::new));

		return interpreter;
	}

	@ExElementTraceable(toolkit = X,
		qonfigType = QuickCustomShadingElement.CUSTOM_SHADING,
		interpretation = QuickCustomShadingElement.Interpreted.class)
	static class QuickCustomShadingElement
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<QuickShading>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<QuickShading>>> {
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
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<QuickShading>, ModelValueElement<SettableValue<QuickShading>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<QuickShading>, ModelValueElement<SettableValue<QuickShading>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theUnitWidth;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theUnitHeight;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> theLit;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> theOpacity;
			private InterpretedValueSynth<Observable<?>, Observable<?>> theRefresh;

			Interpreted(QuickCustomShadingElement definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
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
			public ModelValueElement<SettableValue<QuickShading>> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends ModelValueElement.Abstract<SettableValue<QuickShading>> {
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

			Instantiator(QuickCustomShadingElement.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
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
			public void instantiate() throws ModelInstantiationException {
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
				SettableValue<Integer> width = SettableValue.<Integer> build().withDescription("width").withValue(0).build();
				SettableValue<Integer> height = SettableValue.<Integer> build().withDescription("height").withValue(0).build();
				SettableValue<Integer> x = SettableValue.<Integer> build().withDescription("x").withValue(0).build();
				SettableValue<Integer> y = SettableValue.<Integer> build().withDescription("y").withValue(0).build();
				SettableValue<Float> px = SettableValue.asSettable(x.transform(tx -> tx//
					.combineWith(width).combine((xv, wv) -> xv * 1.0f / wv)), __ -> "Not Settable");
				SettableValue<Float> py = SettableValue.asSettable(y.transform(tx -> tx//
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
				return SettableValue.of(
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

	@ExElementTraceable(toolkit = X,
		qonfigType = QuickRaisedShadingElement.RAISED_SHADING,
		interpretation = QuickCustomShadingElement.Interpreted.class)
	static class QuickRaisedShadingElement
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<QuickShading>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<QuickShading>>> {
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
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<QuickShading>, ModelValueElement<SettableValue<QuickShading>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<QuickShading>, ModelValueElement<SettableValue<QuickShading>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theOpacity;

			Interpreted(QuickRaisedShadingElement definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
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
			public ModelValueElement<SettableValue<QuickShading>> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends ModelValueElement.Abstract<SettableValue<QuickShading>> {
			private boolean isRound;
			private boolean isHorizontal;
			private boolean isVertical;
			private ModelValueInstantiator<SettableValue<Double>> theOpacity;

			Instantiator(QuickRaisedShadingElement.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				isRound = interpreted.getDefinition().isRound();
				isHorizontal = interpreted.getDefinition().isHorizontal();
				isVertical = interpreted.getDefinition().isVertical();
				theOpacity = interpreted.getOpacity() == null ? null : interpreted.getOpacity().instantiate();
			}

			@Override
			public SettableValue<QuickShading> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(
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
