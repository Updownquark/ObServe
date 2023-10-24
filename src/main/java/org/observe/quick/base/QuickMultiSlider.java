package org.observe.quick.base;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickMultiSlider extends QuickWidget.Abstract {
	public static final String MULTI_SLIDER = "multi-slider";
	public static final String SLIDER_HANDLE_RENDERER = "slider-handle-renderer";
	public static final String SLIDER_BG_RENDERER = "slider-bg-renderer";

	public static class SliderHandleRenderer extends QuickWithBackground.Abstract {
		@ExElementTraceable(toolkit = QuickXInterpretation.X,
			qonfigType = SLIDER_HANDLE_RENDERER,
			interpretation = Interpreted.class,
			instance = SliderHandleRenderer.class)
		public static class Def extends QuickWithBackground.Def.Abstract<SliderHandleRenderer> {
			private ModelComponentId theHandleValueVariable;
			private ModelComponentId theHandleIndexVariable;
			private CompiledExpression theTooltip;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			public ModelComponentId getHandleValueVariable() {
				return theHandleValueVariable;
			}

			public ModelComponentId getHandleIndexVariable() {
				return theHandleIndexVariable;
			}

			@QonfigAttributeGetter("tooltip")
			public CompiledExpression getTooltip() {
				return theTooltip;
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
				theTooltip = getAttributeExpression("tooltip", session);
			}

			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickWithBackground.Interpreted.Abstract<SliderHandleRenderer> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;

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

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip() {
				return theTooltip;
			}

			public void updateRenderer(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theTooltip = interpret(getDefinition().getTooltip(), ModelTypes.Value.STRING);
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
		private ModelValueInstantiator<SettableValue<String>> theTooltipInstantiator;

		private SettableValue<SettableValue<Double>> theHandleValue;
		private SettableValue<SettableValue<Integer>> theHandleIndex;
		private SettableValue<String> theTooltip;

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

		public SettableValue<String> getTooltip() {
			return theTooltip;
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
			theTooltipInstantiator = myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().instantiate();
		}

		@Override
		public void instantiated() {
			super.instantiated();

			if (theTooltipInstantiator != null)
				theTooltipInstantiator.instantiate();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theHandleValueVariable, myModels, SettableValue.flatten(theHandleValue));
			ExFlexibleElementModelAddOn.satisfyElementValue(theHandleIndexVariable, myModels, SettableValue.flatten(theHandleIndex));
			theTooltip = theTooltipInstantiator == null ? null : theTooltipInstantiator.get(myModels);
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
		@ExElementTraceable(toolkit = QuickXInterpretation.X,
			qonfigType = SLIDER_BG_RENDERER,
			interpretation = Interpreted.class,
			instance = SliderBgRenderer.class)
		public static class Def extends QuickWithBackground.Def.Abstract<SliderBgRenderer> {
			private CompiledExpression theMaxValue;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@QonfigAttributeGetter("max-value")
			public CompiledExpression getMaxValue() {
				return theMaxValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theMaxValue = getAttributeExpression("max-value", session);
			}

			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickWithBackground.Interpreted.Abstract<SliderBgRenderer> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMaxValue;

			Interpreted(SliderBgRenderer.Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SliderBgRenderer.Def getDefinition() {
				return (SliderBgRenderer.Def) super.getDefinition();
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

				theMaxValue = interpret(getDefinition().getMaxValue(), ModelTypes.Value.DOUBLE);
			}

			public SliderBgRenderer create() {
				return new SliderBgRenderer(getIdentity());
			}
		}

		private ModelValueInstantiator<SettableValue<Double>> theMaxValueInstantiator;

		private SettableValue<Double> theMaxValue;

		public SliderBgRenderer(Object id) {
			super(id);
		}

		public SettableValue<Double> getMaxValue() {
			return theMaxValue;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			Interpreted myInterpreted = (Interpreted) interpreted;
			theMaxValueInstantiator = myInterpreted.getMaxValue() == null ? null : myInterpreted.getMaxValue().instantiate();
		}

		@Override
		public void instantiated() {
			super.instantiated();

			if (theMaxValueInstantiator != null)
				theMaxValueInstantiator.instantiate();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

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

			theValues = getAttributeExpression("values", session);
			isVertical = session.getAttributeText("orientation").equals("vertical");
			isOrderEnforced = session.getAttribute("enforce-order", boolean.class);
			theMin = getAttributeExpression("min", session);
			theMax = getAttributeExpression("max", session);
			theHandleRenderer = syncChild(SliderHandleRenderer.Def.class, theHandleRenderer, session, "handle-renderer");
			syncChildren(SliderBgRenderer.Def.class, theBgRenderers, session.forChildren("bg-renderer"));
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

		public SliderHandleRenderer.Interpreted getHandleRenderer() {
			return theHandleRenderer;
		}

		public List<SliderBgRenderer.Interpreted> getBgRenderers() {
			return Collections.unmodifiableList(theBgRenderers);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<Number>> numberValues = interpret(
				getDefinition().getValues(), ModelTypes.Collection.forType(Number.class));
			Class<?> numberType = TypeTokens.get().unwrap(TypeTokens.getRawType(numberValues.getType().getType(0)));
			Function<Double, Number> reverse;
			boolean inexact;
			if (numberType == double.class) {
				reverse = Double::valueOf;
				inexact = false;
			} else {
				inexact = true;
				if (numberType == float.class) {
					reverse = d -> Float.valueOf(d.floatValue());
				} else if (numberType == long.class) {
					reverse = d -> Long.valueOf(Math.round(d));
				} else if (numberType == int.class) {
					reverse = d -> Integer.valueOf(Math.round(d.floatValue()));
				} else if (numberType == short.class) {
					reverse = d -> {
						int i = Math.round(d.floatValue());
						if (i > Short.MAX_VALUE)
							return Short.MAX_VALUE;
						else if (i < Short.MIN_VALUE)
							return Short.MIN_VALUE;
						else
							return Short.valueOf((short) i);
					};
				} else if (numberType == byte.class) {
					reverse = d -> {
						int i = Math.round(d.floatValue());
						if (i > Short.MAX_VALUE)
							return Short.MAX_VALUE;
						else if (i < Short.MIN_VALUE)
							return Short.MIN_VALUE;
						else
							return Short.valueOf((short) i);
					};
				} else
					throw new ExpressoInterpretationException("Cannot create a multi-slider for number type " + numberType.getName(),
						getDefinition().getElement().getPositionInFile(), 0);
			}
			theValues = numberValues.mapValue(ModelTypes.Collection.forType(double.class),
				numberColl -> numberColl.flow().transform(double.class, tx -> tx//
					.cache(false)//
					.map(Number::doubleValue).replaceSource(reverse, rev -> rev.allowInexactReverse(inexact)))//
				.collectPassive());

			theMin = interpret(getDefinition().getMin(), ModelTypes.Value.DOUBLE);
			theMax = interpret(getDefinition().getMax(), ModelTypes.Value.DOUBLE);

			theHandleRenderer = syncChild(getDefinition().getHandleRenderer(), theHandleRenderer, def -> def.interpret(this),
				(r, rEnv) -> r.updateElement(rEnv));

			syncChildren(getDefinition().getBgRenderers(), theBgRenderers, def -> def.interpret(this),
				SliderBgRenderer.Interpreted::updateRenderer);
		}

		@Override
		public QuickMultiSlider create() {
			return new QuickMultiSlider(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<Double>> theValuesInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMinInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMaxInstantiator;

	private boolean isVertical;
	private boolean isOrderEnforced;
	private SettableValue<ObservableCollection<Double>> theValues;
	private SettableValue<SettableValue<Double>> theMin;
	private SettableValue<SettableValue<Double>> theMax;
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
		if (theHandleRenderer != null)
			copy.theHandleRenderer = theHandleRenderer.copy(copy);
		copy.theBgRenderers = new ArrayList<>();
		for (SliderBgRenderer bgRenderer : theBgRenderers)
			copy.theBgRenderers.add(bgRenderer.copy(copy));

		return copy;
	}
}
