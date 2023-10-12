package org.observe.quick.base;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.observe.util.swing.MultiRangeSlider;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickMultiSlider extends QuickWidget.Abstract {
	public static final String MULTI_SLIDER = "multi-slider";
	public static final String SLIDER_HANDLE_RENDERER = "slider-handle-renderer";
	public static final String SLIDER_BG_RENDERER = "slider-bg-renderer";

	public static class SliderHandleRenderer extends QuickWithBackground.Abstract {
		public static class Def extends QuickWithBackground.Def.Abstract<SliderHandleRenderer> {
			private ModelComponentId theHandleValueVariable;
			private ModelComponentId theHandleIndexVariable;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			public ModelComponentId getHandleValueVariable() {
				return theHandleValueVariable;
			}

			public ModelComponentId getHandleIndexVariable() {
				return theHandleIndexVariable;
			}

			@Override
			public SliderHandleStyle.Def getStyle() {
				return (SliderHandleStyle.Def) super.getStyle();
			}

			@Override
			protected SliderHandleStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new SliderHandleStyle.Def(parentStyle, this, style);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theHandleValueVariable = elModels.getElementValueModelId("handleValue");
				theHandleIndexVariable = elModels.getElementValueModelId("handleIndex");
			}

			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickWithBackground.Interpreted.Abstract<SliderHandleRenderer> {
			Interpreted(SliderHandleRenderer.Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SliderHandleRenderer.Def getDefinition() {
				return (SliderHandleRenderer.Def) super.getDefinition();
			}

			@Override
			public SliderHandleStyle.Interpreted getStyle() {
				return (SliderHandleStyle.Interpreted) super.getStyle();
			}

			public void updateRenderer(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			public SliderHandleRenderer create() {
				return new SliderHandleRenderer(getIdentity());
			}
		}

		public interface HandleRenderContext {
			SettableValue<Double> getHandleValue();

			SettableValue<Integer> getHandleIndex();

			public class Default implements HandleRenderContext {
				private final SettableValue<Double> theHandleValue;
				private final SettableValue<Integer> theHandleIndex;

				public Default(SettableValue<Double> handleValue, SettableValue<Integer> handleIndex) {
					theHandleValue = handleValue;
					theHandleIndex = handleIndex;
				}

				public Default() {
					this(SettableValue.build(double.class).withValue(0.0).build(), //
						SettableValue.build(int.class).withValue(0).build());
				}

				@Override
				public SettableValue<Double> getHandleValue() {
					return theHandleValue;
				}

				@Override
				public SettableValue<Integer> getHandleIndex() {
					return theHandleIndex;
				}
			}
		}

		private ModelComponentId theHandleValueVariable;
		private ModelComponentId theHandleIndexVariable;

		private SettableValue<SettableValue<Double>> theHandleValue;
		private SettableValue<SettableValue<Integer>> theHandleIndex;

		public SliderHandleRenderer(Object id) {
			super(id);
			theHandleValue = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Double>> parameterized(double.class)).build();
			theHandleIndex = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class)).build();
		}

		public ModelComponentId getHandleValueVariable() {
			return theHandleValueVariable;
		}

		public ModelComponentId getHandleIndexVariable() {
			return theHandleIndexVariable;
		}

		@Override
		public SliderHandleStyle getStyle() {
			return (SliderHandleStyle) super.getStyle();
		}

		public void setHandleContext(HandleRenderContext context) throws ModelInstantiationException {
			theHandleValue.set(context.getHandleValue(), null);
			theHandleIndex.set(context.getHandleIndex(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			Interpreted myInterpreted = (Interpreted) interpreted;
			theHandleValueVariable = myInterpreted.getDefinition().getHandleValueVariable();
			theHandleIndexVariable = myInterpreted.getDefinition().getHandleIndexVariable();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theHandleValueVariable, myModels, SettableValue.flatten(theHandleValue));
			ExFlexibleElementModelAddOn.satisfyElementValue(theHandleIndexVariable, myModels, SettableValue.flatten(theHandleIndex));
		}

		@Override
		public SliderHandleRenderer copy(ExElement parent) {
			SliderHandleRenderer copy = (SliderHandleRenderer) super.copy(parent);

			copy.theHandleValue = SettableValue.build(theHandleValue.getType()).build();
			copy.theHandleIndex = SettableValue.build(theHandleIndex.getType()).build();

			return copy;
		}

		public static class SliderHandleStyle extends QuickBackgroundStyle.Default {
			public static class Def extends QuickBackgroundStyle.Def.Default {
				private QuickStyleAttributeDef theLineColor;
				private QuickStyleAttributeDef theLineThickness;

				Def(QuickInstanceStyle.Def parent, SliderHandleRenderer.Def styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickXInterpretation.NAME, QuickXInterpretation.VERSION, SLIDER_HANDLE_RENDERER);
					theLineColor = addApplicableAttribute(typeStyle.getAttribute("line-color"));
					theLineThickness = addApplicableAttribute(typeStyle.getAttribute("line-thickness"));
				}

				public QuickStyleAttributeDef getLineColor() {
					return theLineColor;
				}

				public QuickStyleAttributeDef getLineThickness() {
					return theLineThickness;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted(this, (SliderHandleRenderer.Interpreted) parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent, env));
				}
			}

			public static class Interpreted extends QuickBackgroundStyle.Interpreted.Default {
				private QuickElementStyleAttribute<Color> theLineColor;
				private QuickElementStyleAttribute<Integer> theLineThickness;

				public Interpreted(SliderHandleStyle.Def definition, SliderHandleRenderer.Interpreted styledElement,
					QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public SliderHandleStyle.Def getDefinition() {
					return (SliderHandleStyle.Def) super.getDefinition();
				}

				public QuickElementStyleAttribute<Color> getLineColor() {
					return theLineColor;
				}

				public QuickElementStyleAttribute<Integer> getLineThickness() {
					return theLineThickness;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, styleSheet, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theLineColor = get(cache.getAttribute(getDefinition().getLineColor(), Color.class, env));
					theLineThickness = get(cache.getAttribute(getDefinition().getLineThickness(), Integer.class, env));
				}

				@Override
				public SliderHandleStyle create(QuickStyledElement styledElement) {
					return new SliderHandleStyle();
				}
			}

			private QuickStyleAttribute<Color> theLineColorAttr;
			private QuickStyleAttribute<Integer> theLineThicknessAttr;
			private ObservableValue<Color> theLineColor;
			private ObservableValue<Integer> theLineThickness;

			public SliderHandleStyle() {}

			public ObservableValue<Color> getLineColor() {
				return theLineColor;
			}

			public ObservableValue<Integer> getLineThickness() {
				return theLineThickness;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement) {
				super.update(interpreted, styledElement);

				SliderHandleStyle.Interpreted myInterpreted = (SliderHandleStyle.Interpreted) interpreted;

				theLineColorAttr = myInterpreted.getLineColor().getAttribute();
				theLineThicknessAttr = myInterpreted.getLineThickness().getAttribute();

				theLineColor = getApplicableAttribute(theLineColorAttr);
				theLineThickness = getApplicableAttribute(theLineThicknessAttr);
			}

			@Override
			public SliderHandleStyle copy(QuickStyledElement styledElement) {
				SliderHandleStyle copy = (SliderHandleStyle) super.copy(styledElement);

				copy.theLineColor = copy.getApplicableAttribute(theLineColorAttr);
				copy.theLineThickness = copy.getApplicableAttribute(theLineThicknessAttr);

				return copy;
			}
		}
	}

	public static class SliderBgRenderer extends QuickWithBackground.Abstract {
		public static class Def extends QuickWithBackground.Def.Abstract<SliderBgRenderer> {
			private CompiledExpression theMinValue;
			private CompiledExpression theMaxValue;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			public CompiledExpression getMinValue() {
				return theMinValue;
			}

			public CompiledExpression getMaxValue() {
				return theMaxValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theMinValue = session.getAttributeExpression("min-value");
				theMaxValue = session.getAttributeExpression("max-value");
			}

			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickWithBackground.Interpreted.Abstract<SliderBgRenderer> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMinValue;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMaxValue;

			Interpreted(SliderBgRenderer.Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SliderBgRenderer.Def getDefinition() {
				return (SliderBgRenderer.Def) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMinValue() {
				return theMinValue;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMaxValue() {
				return theMaxValue;
			}

			public void updateRenderer(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				theMinValue = getDefinition().getMinValue() == null ? null
					: getDefinition().getMinValue().interpret(ModelTypes.Value.DOUBLE, env);
				theMaxValue = getDefinition().getMaxValue() == null ? null
					: getDefinition().getMaxValue().interpret(ModelTypes.Value.DOUBLE, env);
			}

			public SliderBgRenderer create() {
				return new SliderBgRenderer(getIdentity());
			}
		}

		private ModelValueInstantiator<SettableValue<Double>> theMinValueInstantiator;
		private ModelValueInstantiator<SettableValue<Double>> theMaxValueInstantiator;

		private SettableValue<Double> theMinValue;
		private SettableValue<Double> theMaxValue;

		public SliderBgRenderer(Object id) {
			super(id);
		}

		public SettableValue<Double> getMinValue() {
			return theMinValue;
		}

		public SettableValue<Double> getMaxValue() {
			return theMaxValue;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			Interpreted myInterpreted = (Interpreted) interpreted;
			theMinValueInstantiator = myInterpreted.getMinValue() == null ? null : myInterpreted.getMinValue().instantiate();
			theMaxValueInstantiator = myInterpreted.getMaxValue() == null ? null : myInterpreted.getMaxValue().instantiate();
		}

		@Override
		public void instantiated() {
			super.instantiated();

			if (theMinValueInstantiator != null)
				theMinValueInstantiator.instantiate();
			if (theMaxValueInstantiator != null)
				theMaxValueInstantiator.instantiate();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			theMinValue = theMinValueInstantiator == null ? null : theMinValueInstantiator.get(myModels);
			theMaxValue = theMaxValueInstantiator == null ? null : theMaxValueInstantiator.get(myModels);
		}

		@Override
		public SliderBgRenderer copy(ExElement parent) {
			return (SliderBgRenderer) super.copy(parent);
		}
	}

	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = MULTI_SLIDER,
		interpretation = Interpreted.class,
		instance = QuickMultiSlider.class)
	public static class Def extends QuickWidget.Def.Abstract<QuickMultiSlider> {
		private CompiledExpression theValues;
		private boolean isVertical;
		private boolean isOrderEnforced;
		private CompiledExpression theMin;
		private CompiledExpression theMax;
		private CompiledExpression theBgRenderer;
		private CompiledExpression theValueRenderer;
		private SliderHandleRenderer.Def theHandleRenderer;
		private final List<SliderBgRenderer.Def> theBgRenderers;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theBgRenderers = new ArrayList<>();
		}

		@QonfigAttributeGetter("values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@QonfigAttributeGetter("orientation")
		public boolean isVertical() {
			return isVertical;
		}

		@QonfigAttributeGetter("enforce-order")
		public boolean isOrderEnforced() {
			return isOrderEnforced;
		}

		@QonfigAttributeGetter("min")
		public CompiledExpression getMin() {
			return theMin;
		}

		@QonfigAttributeGetter("max")
		public CompiledExpression getMax() {
			return theMax;
		}

		@QonfigAttributeGetter("bg-renderer")
		public CompiledExpression getBGRenderer() {
			return theBgRenderer;
		}

		@QonfigAttributeGetter("value-renderer")
		public CompiledExpression getValueRenderer() {
			return theValueRenderer;
		}

		@QonfigChildGetter("handle-renderer")
		public SliderHandleRenderer.Def getHandleRenderer() {
			return theHandleRenderer;
		}

		@QonfigChildGetter("bg-renderer")
		public List<SliderBgRenderer.Def> getBgRenderers() {
			return Collections.unmodifiableList(theBgRenderers);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theValues = session.getAttributeExpression("values");
			isVertical = session.getAttributeText("orientation").equals("vertical");
			isOrderEnforced = session.getAttribute("enforce-order", boolean.class);
			theMin = session.getAttributeExpression("min");
			theMax = session.getAttributeExpression("max");
			theBgRenderer = session.getAttributeExpression("bg-renderer");
			theValueRenderer = session.getAttributeExpression("value-renderer");
			theHandleRenderer = ExElement.useOrReplace(SliderHandleRenderer.Def.class, theHandleRenderer, session, "handle-renderer");
			ExElement.syncDefs(SliderBgRenderer.Def.class, theBgRenderers, session.forChildren("bg-renderer"));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickWidget.Interpreted.Abstract<QuickMultiSlider> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<Double>> theValues;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMin;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMax;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<MultiRangeSlider.MRSliderRenderer>> theBgRenderer;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<MultiRangeSlider.RangeRenderer>> theValueRenderer;
		private SliderHandleRenderer.Interpreted theHandleRenderer;
		private final List<SliderBgRenderer.Interpreted> theBgRenderers;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theBgRenderers = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<Double>> getValues() {
			return theValues;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMin() {
			return theMin;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMax() {
			return theMax;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<MultiRangeSlider.MRSliderRenderer>> getBGRenderer() {
			return theBgRenderer;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<MultiRangeSlider.RangeRenderer>> getValueRenderer() {
			return theValueRenderer;
		}

		public SliderHandleRenderer.Interpreted getHandleRenderer() {
			return theHandleRenderer;
		}

		public List<SliderBgRenderer.Interpreted> getBgRenderers() {
			return Collections.unmodifiableList(theBgRenderers);
		}

		@Override
		public TypeToken<? extends QuickMultiSlider> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().of(QuickMultiSlider.class);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theValues = getDefinition().getValues().interpret(ModelTypes.Collection.forType(double.class), env);
			theMin = getDefinition().getMin().interpret(ModelTypes.Value.DOUBLE, env);
			theMax = getDefinition().getMax().interpret(ModelTypes.Value.DOUBLE, env);
			theBgRenderer = getDefinition().getBGRenderer() == null ? null
				: getDefinition().getBGRenderer().interpret(ModelTypes.Value.forType(MultiRangeSlider.MRSliderRenderer.class), env);
			theValueRenderer = getDefinition().getValueRenderer() == null ? null
				: getDefinition().getValueRenderer().interpret(ModelTypes.Value.forType(MultiRangeSlider.RangeRenderer.class), env);

			if (theHandleRenderer != null && (getDefinition().getHandleRenderer() == null
				|| theHandleRenderer.getIdentity() != getDefinition().getHandleRenderer().getIdentity())) {
				theHandleRenderer.destroy();
				theHandleRenderer = null;
			}
			if (theHandleRenderer == null && getDefinition().getHandleRenderer() != null)
				theHandleRenderer = getDefinition().getHandleRenderer().interpret(this);
			if (theHandleRenderer != null)
				theHandleRenderer.updateElement(env);

			CollectionUtils
			.synchronize(theBgRenderers, getDefinition().getBgRenderers(), (interp, def) -> interp.getIdentity() == def.getIdentity())//
			.<ExpressoInterpretationException> simpleE(def -> def.interpret(this))//
			.onLeft(el -> el.getLeftValue().destroy())//
			.onRightX(el -> el.getLeftValue().updateRenderer(env))//
			.onCommonX(el -> el.getLeftValue().updateRenderer(env))//
			.rightOrder()//
			.adjust();
		}

		@Override
		public QuickMultiSlider create() {
			return new QuickMultiSlider(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<Double>> theValuesInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMinInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMaxInstantiator;
	private ModelValueInstantiator<SettableValue<MultiRangeSlider.MRSliderRenderer>> theBgRendererInstantiator;
	private ModelValueInstantiator<SettableValue<MultiRangeSlider.RangeRenderer>> theValueRendererInstantiator;

	private boolean isVertical;
	private boolean isOrderEnforced;
	private SettableValue<ObservableCollection<Double>> theValues;
	private SettableValue<SettableValue<Double>> theMin;
	private SettableValue<SettableValue<Double>> theMax;
	private SettableValue<SettableValue<MultiRangeSlider.MRSliderRenderer>> theBgRenderer;
	private SettableValue<SettableValue<MultiRangeSlider.RangeRenderer>> theValueRenderer;
	private SliderHandleRenderer theHandleRenderer;
	private List<SliderBgRenderer> theBgRenderers;

	QuickMultiSlider(Object id) {
		super(id);
		theValues = SettableValue
			.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<Double>> parameterized(double.class)).build();
		theMin = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Double>> parameterized(double.class))
			.build();
		theMax = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Double>> parameterized(double.class))
			.build();
		theBgRenderer = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<MultiRangeSlider.MRSliderRenderer>> parameterized(
				MultiRangeSlider.MRSliderRenderer.class))
			.build();
		theValueRenderer = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<MultiRangeSlider.RangeRenderer>> parameterized(
				MultiRangeSlider.RangeRenderer.class))
			.build();
		theBgRenderers = new ArrayList<>();
	}

	public ObservableCollection<Double> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	public boolean isVertical() {
		return isVertical;
	}

	public boolean isOrderEnforced() {
		return isOrderEnforced;
	}

	public SettableValue<Double> getMin() {
		return SettableValue.flatten(theMin, () -> 0.0);
	}

	public SettableValue<Double> getMax() {
		return SettableValue.flatten(theMax, () -> 0.0);
	}

	public SettableValue<MultiRangeSlider.MRSliderRenderer> getBGRenderer() {
		return SettableValue.flatten(theBgRenderer);
	}

	public SettableValue<MultiRangeSlider.RangeRenderer> getValueRenderer() {
		return SettableValue.flatten(theValueRenderer);
	}

	public SliderHandleRenderer getHandleRenderer() {
		return theHandleRenderer;
	}

	public List<SliderBgRenderer> getBgRenderers() {
		return Collections.unmodifiableList(theBgRenderers);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		isVertical = myInterpreted.getDefinition().isVertical();
		isOrderEnforced = myInterpreted.getDefinition().isOrderEnforced();
		theValuesInstantiator = myInterpreted.getValues().instantiate();
		theMinInstantiator = myInterpreted.getMin().instantiate();
		theMaxInstantiator = myInterpreted.getMax().instantiate();
		theBgRendererInstantiator = myInterpreted.getBGRenderer() == null ? null : myInterpreted.getBGRenderer().instantiate();
		theValueRendererInstantiator = myInterpreted.getValueRenderer() == null ? null : myInterpreted.getValueRenderer().instantiate();

		if (theHandleRenderer != null && (myInterpreted.getHandleRenderer() == null
			|| theHandleRenderer.getIdentity() != myInterpreted.getHandleRenderer().getIdentity())) {
			theHandleRenderer.destroy();
			theHandleRenderer = null;
		}
		if (theHandleRenderer == null && myInterpreted.getHandleRenderer() != null)
			theHandleRenderer = myInterpreted.getHandleRenderer().create();
		if (theHandleRenderer != null)
			theHandleRenderer.update(myInterpreted.getHandleRenderer(), this);

		CollectionUtils
		.synchronize(theBgRenderers, myInterpreted.getBgRenderers(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.simpleE(interp -> interp.create())//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRight(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommon(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();
	}

	@Override
	public void instantiated() {
		super.instantiated();

		theValuesInstantiator.instantiate();
		theMinInstantiator.instantiate();
		theMaxInstantiator.instantiate();
		if (theBgRendererInstantiator != null)
			theBgRendererInstantiator.instantiate();
		if (theValueRendererInstantiator != null)
			theValueRendererInstantiator.instantiate();
		if (theHandleRenderer != null)
			theHandleRenderer.instantiated();
		for (SliderBgRenderer bgRenderer : theBgRenderers)
			bgRenderer.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theValues.set(theValuesInstantiator.get(myModels), null);
		SettableValue<Double> min = theMinInstantiator.get(myModels);
		SettableValue<Double> max = theMaxInstantiator.get(myModels);
		// Don't cross the ranges
		if (max.get() >= getMin().get()) {
			theMax.set(max, null);
			theMin.set(min, null);
		} else {
			theMin.set(min, null);
			theMax.set(max, null);
		}
		theBgRenderer.set(theBgRendererInstantiator == null ? null : theBgRendererInstantiator.get(myModels), null);
		theValueRenderer.set(theValueRendererInstantiator == null ? null : theValueRendererInstantiator.get(myModels), null);
		if (theHandleRenderer != null)
			theHandleRenderer.instantiate(myModels);
		for (SliderBgRenderer bgRenderer : theBgRenderers)
			bgRenderer.instantiate(myModels);
	}

	@Override
	public QuickMultiSlider copy(ExElement parent) {
		QuickMultiSlider copy = (QuickMultiSlider) super.copy(parent);

		copy.theValues = SettableValue.build(theValues.getType()).build();
		copy.theMin = SettableValue.build(theMin.getType()).build();
		copy.theMax = SettableValue.build(theMax.getType()).build();
		copy.theBgRenderer = SettableValue.build(theBgRenderer.getType()).build();
		copy.theValueRenderer = SettableValue.build(theValueRenderer.getType()).build();
		if (theHandleRenderer != null)
			copy.theHandleRenderer = theHandleRenderer.copy(copy);
		copy.theBgRenderers = new ArrayList<>();
		for (SliderBgRenderer bgRenderer : theBgRenderers)
			copy.theBgRenderers.add(bgRenderer.copy(copy));

		return copy;
	}
}
