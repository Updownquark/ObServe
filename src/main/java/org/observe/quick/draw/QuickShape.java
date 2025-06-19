package org.observe.quick.draw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.Observable;
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
import org.observe.quick.QuickEventListener;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyled;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickShape extends QuickWithBackground, QuickShapePublisher {
	public static final String SHAPE = "shape";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = SHAPE,
		interpretation = Interpreted.class,
		instance = QuickShape.class)
	public interface Def<E extends QuickShape> extends QuickWithBackground.Def<E>, QuickShapePublisher.Def<E> {
		@Override
		QuickShapeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style);

		@Override
		QuickShapeStyle.Def getStyle();

		/** @return The tool tip to display when the user hovers over this shape */
		@QonfigAttributeGetter("tooltip")
		CompiledExpression getTooltip();

		/** @return The expression determining when this shape is to be visible */
		@QonfigAttributeGetter("visible")
		CompiledExpression isVisible();

		/** @return An event that causes this shape to repaint itself */
		@QonfigAttributeGetter("repaint")
		CompiledExpression getRepaint();

		/** @return All event listeners configured for this shape */
		@QonfigChildGetter("event-listener")
		List<QuickEventListener.Def<?>> getEventListeners();

		@Override
		Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<E extends QuickShape> extends QuickWithBackground.Def.Abstract<E> implements QuickShape.Def<E> {
			private CompiledExpression theTooltip;
			private CompiledExpression isVisible;
			private CompiledExpression theRepaint;

			private ModelComponentId theHoveredValue;
			private ModelComponentId theFocusedValue;
			private ModelComponentId thePressedValue;
			private ModelComponentId theRightPressedValue;

			private final List<QuickEventListener.Def<?>> theEventListeners;

			/**
			 * @param parent The parent container definition
			 * @param type The element type that this shape is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theEventListeners = new ArrayList<>();
			}

			@Override
			public QuickShapeStyle.Def getStyle() {
				return (QuickShapeStyle.Def) super.getStyle();
			}

			@Override
			public CompiledExpression getTooltip() {
				return theTooltip;
			}

			@Override
			public CompiledExpression isVisible() {
				return isVisible;
			}

			@Override
			public CompiledExpression getRepaint() {
				return theRepaint;
			}

			@Override
			public ModelComponentId getHoveredValue() {
				return theHoveredValue;
			}

			@Override
			public ModelComponentId getFocusedValue() {
				return theFocusedValue;
			}

			@Override
			public ModelComponentId getPressedValue() {
				return thePressedValue;
			}

			@Override
			public ModelComponentId getRightPressedValue() {
				return theRightPressedValue;
			}

			@Override
			public List<QuickEventListener.Def<?>> getEventListeners() {
				return Collections.unmodifiableList(theEventListeners);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement("styled"));
				theTooltip = getAttributeExpression("tooltip", session);
				isVisible = getAttributeExpression("visible", session);
				theRepaint = getAttributeExpression("repaint", session);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theHoveredValue = elModels.getElementValueModelId("hovered");
				theFocusedValue = elModels.getElementValueModelId("focused");
				thePressedValue = elModels.getElementValueModelId("pressed");
				theRightPressedValue = elModels.getElementValueModelId("rightPressed");

				syncChildren(QuickEventListener.Def.class, theEventListeners, session.forChildren("event-listener"));
			}

			@Override
			public QuickShapeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickShapeStyle.Def.Default(parentStyle, this, style);
			}
		}
	}

	public interface Interpreted<E extends QuickShape> extends QuickWithBackground.Interpreted<E>, QuickShapePublisher.Interpreted<E> {
		@Override
		QuickShape.Def<? super E> getDefinition();

		@Override
		public QuickShapeStyle.Interpreted getStyle();

		/** @return The tool tip to display when the user hovers over this shape */
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

		/** @return The value determining when this shape is to be visible */
		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible();

		/** @return An event that causes this shape to repaint itself */
		InterpretedValueSynth<Observable<?>, Observable<?>> getRepaint();

		/** @return All event listeners configured for this shape */
		List<QuickEventListener.Interpreted<?>> getEventListeners();

		@Override
		public abstract E create();

		public abstract class Abstract<E extends QuickShape> extends QuickWithBackground.Interpreted.Abstract<E>
		implements QuickShape.Interpreted<E> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible;
			private InterpretedValueSynth<Observable<?>, Observable<?>> theRepaint;
			private final List<QuickEventListener.Interpreted<?>> theEventListeners;

			protected Abstract(QuickShape.Def<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theEventListeners = new ArrayList<>();
			}

			@Override
			public QuickShape.Def<? super E> getDefinition() {
				return (QuickShape.Def<? super E>) super.getDefinition();
			}

			@Override
			public QuickShapeStyle.Interpreted getStyle() {
				return (QuickShapeStyle.Interpreted) super.getStyle();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip() {
				return theTooltip;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible() {
				return isVisible;
			}

			@Override
			public InterpretedValueSynth<Observable<?>, Observable<?>> getRepaint() {
				return theRepaint;
			}

			@Override
			public List<QuickEventListener.Interpreted<?>> getEventListeners() {
				return Collections.unmodifiableList(theEventListeners);
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				theTooltip = interpret(getDefinition().getTooltip(), ModelTypes.Value.STRING);
				isVisible = interpret(getDefinition().isVisible(), ModelTypes.Value.BOOLEAN);
				theRepaint = interpret(getDefinition().getRepaint(), ModelTypes.Event.any());

				syncChildren(getDefinition().getEventListeners(), theEventListeners, def -> def.interpret(this),
					QuickEventListener.Interpreted::updateListener);
			}

			@Override
			public void destroy() {
				for (QuickEventListener.Interpreted<?> listener : theEventListeners)
					listener.destroy();
				theEventListeners.clear();
				super.destroy();
			}
		}
	}

	@Override
	public QuickShapeStyle getStyle();

	/** @return The tool tip to display when the user hovers over this shape */
	SettableValue<String> getTooltip();

	/** @return The value determining when this shape is to be visible */
	SettableValue<Boolean> isVisible();

	/** @return An event that causes this shape to repaint itself */
	Observable<?> getRepaint();

	/** @return All event listeners for this shape */
	ObservableCollection<QuickEventListener> getEventListeners();

	@Override
	public QuickShape copy(ExElement parent);

	public abstract class Abstract extends QuickWithBackground.Abstract implements QuickShape {
		private SettableValue<SettableValue<Boolean>> isHovered;
		private SettableValue<SettableValue<Boolean>> isFocused;
		private SettableValue<SettableValue<Boolean>> isPressed;
		private SettableValue<SettableValue<Boolean>> isRightPressed;

		private ModelValueInstantiator<SettableValue<String>> theTooltipInstantiator;
		private ModelValueInstantiator<SettableValue<Boolean>> theVisibleInstantiator;
		private ModelValueInstantiator<Observable<?>> theRepaintInstantiator;
		private ModelComponentId theHoveredValue;
		private ModelComponentId theFocusedValue;
		private ModelComponentId thePressedValue;
		private ModelComponentId theRightPressedValue;

		private SettableValue<SettableValue<String>> theTooltip;
		private SettableValue<SettableValue<Boolean>> isVisible;
		private SettableValue<Observable<?>> theRepaint;

		private ObservableCollection<QuickEventListener> theEventListeners;

		protected Abstract(Object id) {
			super(id);

			theTooltip = SettableValue.create();
			isVisible = SettableValue.create();
			theRepaint = SettableValue.create();
			theEventListeners = ObservableCollection.create();

			isHovered = SettableValue.create();
			isFocused = SettableValue.create();
			isPressed = SettableValue.create();
			isRightPressed = SettableValue.create();
		}

		@Override
		public QuickShapeStyle getStyle() {
			return (QuickShapeStyle) super.getStyle();
		}

		@Override
		public SettableValue<String> getTooltip() {
			return SettableValue.flatten(theTooltip);
		}

		@Override
		public SettableValue<Boolean> isVisible() {
			return SettableValue.flatten(isVisible, () -> true);
		}

		@Override
		public Observable<?> getRepaint() {
			return ObservableValue.flattenObservableValue(theRepaint);
		}

		@Override
		public SettableValue<Boolean> isHovered() {
			return SettableValue.flatten(isHovered);
		}

		@Override
		public SettableValue<Boolean> isFocused() {
			return SettableValue.flatten(isFocused);
		}

		@Override
		public SettableValue<Boolean> isPressed() {
			return SettableValue.flatten(isPressed);
		}

		@Override
		public SettableValue<Boolean> isRightPressed() {
			return SettableValue.flatten(isRightPressed);
		}

		@Override
		public ObservableCollection<QuickEventListener> getEventListeners() {
			return theEventListeners.flow().unmodifiable(false).collect();
		}

		@Override
		public void setContext(BackgroundContext ctx) {
			isHovered.set(ctx.isHovered(), null);
			isFocused.set(ctx.isFocused(), null);
			isPressed.set(ctx.isPressed(), null);
			isRightPressed.set(ctx.isRightPressed(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			QuickShape.Interpreted<?> myInterpreted = (QuickShape.Interpreted<?>) interpreted;

			theHoveredValue = myInterpreted.getDefinition().getHoveredValue();
			theFocusedValue = myInterpreted.getDefinition().getFocusedValue();
			thePressedValue = myInterpreted.getDefinition().getPressedValue();
			theRightPressedValue = myInterpreted.getDefinition().getRightPressedValue();

			theTooltipInstantiator = myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().instantiate();
			theVisibleInstantiator = myInterpreted.isVisible() == null ? null : myInterpreted.isVisible().instantiate();
			theRepaintInstantiator = myInterpreted.getRepaint() == null ? null : myInterpreted.getRepaint().instantiate();

			syncChildren(myInterpreted.getEventListeners(), theEventListeners, el -> el.create(), QuickEventListener::update);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			if (theTooltipInstantiator != null)
				theTooltipInstantiator.instantiate();
			if (theVisibleInstantiator != null)
				theVisibleInstantiator.instantiate();
			if (theRepaintInstantiator != null)
				theRepaintInstantiator.instantiate();

			for (QuickEventListener listener : theEventListeners)
				listener.instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theHoveredValue, myModels, SettableValue.flatten(isHovered));
			ExFlexibleElementModelAddOn.satisfyElementValue(theFocusedValue, myModels, SettableValue.flatten(isFocused));
			ExFlexibleElementModelAddOn.satisfyElementValue(thePressedValue, myModels, SettableValue.flatten(isPressed));
			ExFlexibleElementModelAddOn.satisfyElementValue(theRightPressedValue, myModels, SettableValue.flatten(isRightPressed));

			theTooltip.set(theTooltipInstantiator == null ? null : theTooltipInstantiator.get(myModels), null);
			isVisible.set(theVisibleInstantiator == null ? null : theVisibleInstantiator.get(myModels), null);
			theRepaint.set(theRepaintInstantiator == null ? null : theRepaintInstantiator.get(myModels), null);

			for (QuickEventListener listener : theEventListeners)
				listener.instantiate(myModels);

			return myModels;
		}

		@Override
		public QuickShape.Abstract copy(ExElement parent) {
			QuickShape.Abstract copy = (QuickShape.Abstract) super.copy(parent);

			copy.theTooltip = SettableValue.create();
			copy.isVisible = SettableValue.create();
			copy.theRepaint = SettableValue.create();
			copy.theEventListeners = ObservableCollection.create();

			copy.isHovered = SettableValue.create();
			copy.isFocused = SettableValue.create();
			copy.isPressed = SettableValue.create();
			copy.isRightPressed = SettableValue.create();

			for (QuickEventListener listener : theEventListeners)
				copy.theEventListeners.add(listener.copy(copy));

			return copy;
		}
	}

	public interface QuickShapeStyle extends QuickWithBackground.QuickBackgroundStyle {
		public static interface Def extends QuickWithBackground.QuickBackgroundStyle.Def {
			QuickStyleAttributeDef getOpacity();

			public static class Default extends QuickWithBackground.QuickBackgroundStyle.Def.Default implements Def {
				private final QuickStyleAttributeDef theOpacity;

				protected Default(QuickStyled.QuickInstanceStyle.Def parent, ExElement.Def styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyled.getTypeStyle(wrapped.getStyleTypes(), getElement(), QuickDrawInterpretation.NAME,
						QuickDrawInterpretation.VERSION, SHAPE);
					theOpacity = addApplicableAttribute(typeStyle.getAttribute("opacity"));
				}

				@Override
				public QuickStyleAttributeDef getOpacity() {
					return theOpacity;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new QuickShapeStyle.Interpreted.Default(this, parentEl,
						(QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent));
				}
			}
		}

		public static interface Interpreted extends QuickWithBackground.QuickBackgroundStyle.Interpreted {
			QuickElementStyleAttribute<Float> getOpacity();

			public static class Default extends QuickWithBackground.QuickBackgroundStyle.Interpreted.Default implements Interpreted {
				private QuickElementStyleAttribute<Float> theOpacity;

				protected Default(QuickShapeStyle.Def definition, ExElement.Interpreted<?> styledElement,
					QuickStyled.QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public QuickShapeStyle.Def getDefinition() {
					return (QuickShapeStyle.Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Float> getOpacity() {
					return theOpacity;
				}

				@Override
				public void update(ExElement.Interpreted<?> element, org.observe.quick.style.QuickStyleSheet.Interpreted styleSheet)
					throws ExpressoInterpretationException {
					super.update(element, styleSheet);
					InterpretedExpressoEnv env = element.getDefaultEnv();
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theOpacity = get(cache.getAttribute(getDefinition().getOpacity(), float.class, env));
				}

				@Override
				public QuickShapeStyle create(QuickStyled styled) {
					return new QuickShapeStyle.Default();
				}
			}
		}

		public ObservableValue<Float> getOpacity();

		public static class Default extends QuickWithBackground.QuickBackgroundStyle.Default implements QuickShapeStyle {
			private QuickStyleAttribute<Float> theOpacityAttr;
			private ObservableValue<Float> theOpacity;

			protected Default() {
				super();
			}

			@Override
			public ObservableValue<Float> getOpacity() {
				return theOpacity;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyled styled) throws ModelInstantiationException {
				super.update(interpreted, styled);

				QuickShapeStyle.Interpreted myInterpreted = (QuickShapeStyle.Interpreted) interpreted;

				theOpacityAttr = myInterpreted.getOpacity().getAttribute();

				theOpacity = getApplicableAttribute(theOpacityAttr);
			}

			@Override
			public QuickShapeStyle.Default copy(QuickStyled styled) {
				QuickShapeStyle.Default copy = (QuickShapeStyle.Default) super.copy(styled);

				copy.theOpacity = copy.getApplicableAttribute(theOpacityAttr);

				return copy;
			}
		}
	}
}
