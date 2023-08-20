package org.observe.quick;

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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** The base class for widgets in Quick */
public interface QuickWidget extends QuickTextElement {
	public static final String WIDGET = "widget";
	public static final SingleTypeTraceability<QuickWidget, Interpreted<?>, Def<?>> WIDGET_TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, WIDGET, Def.class, Interpreted.class,
			QuickWidget.class);

	/**
	 * The definition of a {@link QuickWidget}
	 *
	 * @param <W> The type of widget that this definition is for
	 */
	public interface Def<W extends QuickWidget> extends QuickTextElement.Def<W> {
		@Override
		QuickWidgetStyle.Def getStyle();

		/** @return The container definition that this widget is a child of */
		QuickContainer.Def<?, ?> getParent();

		/** @return This widget's border */
		@QonfigChildGetter("border")
		QuickBorder.Def<?> getBorder();

		/** @return This widget's name, typically for debugging */
		@QonfigAttributeGetter("name")
		String getName();

		/** @return The tool tip to display when the user hovers over this widget */
		@QonfigAttributeGetter("tooltip")
		CompiledExpression getTooltip();

		/** @return The expression determining when this widget is to be visible */
		@QonfigAttributeGetter("visible")
		CompiledExpression isVisible();

		@QonfigChildGetter("event-listener")
		List<QuickEventListener.Def<?>> getEventListeners();

		/**
		 * @param parent The parent container interpretation
		 * @return The new widget interpretation
		 */
		Interpreted<? extends W> interpret(ExElement.Interpreted<?> parent);

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <W> The type of widget that this definition is for
		 */
		public abstract class Abstract<W extends QuickWidget> extends QuickStyledElement.Def.Abstract<W> implements Def<W> {
			private QuickBorder.Def<?> theBorder;
			private String theName;
			private CompiledExpression theTooltip;
			private CompiledExpression isVisible;
			private final List<QuickEventListener.Def<?>> theEventListeners;

			/**
			 * @param parent The parent container definition
			 * @param type The element type that this widget is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theEventListeners = new ArrayList<>();
			}

			@Override
			public QuickWidgetStyle.Def getStyle() {
				return (QuickWidgetStyle.Def) super.getStyle();
			}

			@Override
			public QuickContainer.Def<?, ?> getParent() {
				ExElement.Def<?> parent = getParentElement();
				return parent instanceof QuickContainer.Def ? (QuickContainer.Def<?, ?>) parent : null;
			}

			@Override
			public QuickBorder.Def<?> getBorder() {
				return theBorder;
			}

			@Override
			public String getName() {
				return theName;
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
			public List<QuickEventListener.Def<?>> getEventListeners() {
				return Collections.unmodifiableList(theEventListeners);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(WIDGET_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement("styled"));
				theBorder = ExElement.useOrReplace(QuickBorder.Def.class, theBorder, session, "border");
				theName = session.getAttributeText("name");
				theTooltip = session.getAttributeExpression("tooltip");
				isVisible = session.getAttributeExpression("visible");
				CollectionUtils.synchronize(theEventListeners, session.forChildren("event-listener"),
					(l, s) -> ExElement.typesEqual(l.getElement(), s.getElement())).simpleE(s -> {
						QuickEventListener.Def<?> listener = s.interpret(QuickEventListener.Def.class);
						listener.update(s);
						return listener;
					})//
				.onCommonX(el -> el.getLeftValue().update(el.getRightValue())).adjust();
			}

			@Override
			protected QuickWidgetStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickWidgetStyle.Def.Default(parentStyle, this, style);
			}
		}
	}

	/**
	 * An interpretation of a {@link QuickWidget}
	 *
	 * @param <W> The type of widget that this interpretation is for
	 */
	public interface Interpreted<W extends QuickWidget> extends QuickTextElement.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		default String getName() {
			return getDefinition().getName();
		}

		@Override
		QuickWidgetStyle.Interpreted getStyle();

		/** @return The parent container of this widget interpretation, if any */
		QuickContainer.Interpreted<?, ?> getParent();

		/** @return This widget's border */
		QuickBorder.Interpreted<?> getBorder();

		/** @return The type of the widget produced by this interpretation */
		TypeToken<? extends W> getWidgetType() throws ExpressoInterpretationException;

		/** @return The tool tip to display when the user hovers over this widget */
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

		/** @return The value determining when this widget is to be visible */
		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible();

		List<QuickEventListener.Interpreted<?>> getEventListeners();

		/**
		 * Produces a widget instance
		 *
		 * @param parent The parent container, if any
		 * @return The new widget
		 */
		W create(ExElement parent);

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <W> The type of widget that this interpretation is for
		 */
		public abstract class Abstract<W extends QuickWidget> extends QuickStyledElement.Interpreted.Abstract<W> implements Interpreted<W> {
			private QuickBorder.Interpreted<?> theBorder;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible;
			private final List<QuickEventListener.Interpreted<?>> theEventListeners;

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			protected Abstract(Def<? super W> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theEventListeners = new ArrayList<>();
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}

			@Override
			public QuickWidgetStyle.Interpreted getStyle() {
				return (QuickWidgetStyle.Interpreted) super.getStyle();
			}

			@Override
			public QuickContainer.Interpreted<?, ?> getParent() {
				ExElement.Interpreted<?> parent = getParentElement();
				return parent instanceof QuickContainer.Interpreted ? (QuickContainer.Interpreted<?, ?>) parent : null;
			}

			@Override
			public QuickBorder.Interpreted<?> getBorder() {
				return theBorder;
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
			public List<QuickEventListener.Interpreted<?>> getEventListeners() {
				return Collections.unmodifiableList(theEventListeners);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (getDefinition().getBorder() == null)
					theBorder = null;
				else if (theBorder == null || theBorder.getDefinition() != getDefinition().getBorder())
					theBorder = getDefinition().getBorder().interpret(this);
				if (theBorder != null)
					theBorder.updateBorder(env);
				theTooltip = getDefinition().getTooltip() == null ? null
					: getDefinition().getTooltip().interpret(ModelTypes.Value.STRING, env);
				isVisible = getDefinition().isVisible() == null ? null
					: getDefinition().isVisible().interpret(ModelTypes.Value.BOOLEAN, env);
				CollectionUtils
				.synchronize(theEventListeners, getDefinition().getEventListeners(), (l, d) -> l.getIdentity() == d.getIdentity())//
				.simpleE(l -> {
					QuickEventListener.Interpreted<?> listener = l.interpret(this);
					listener.updateListener(env);
					return listener;
				})//
				.onCommonX(el -> el.getLeftValue().updateListener(env))//
				.adjust();
			}

			@Override
			public void destroy() {
				if (theBorder != null) {
					theBorder.destroy();
					theBorder = null;
				}
				for (QuickEventListener.Interpreted<?> listener : theEventListeners)
					listener.destroy();
				theEventListeners.clear();
				super.destroy();
			}
		}
	}

	public interface WidgetContext {
		SettableValue<Boolean> isHovered();

		SettableValue<Boolean> isFocused();

		SettableValue<Boolean> isPressed();

		SettableValue<Boolean> isRightPressed();

		public class Default implements WidgetContext {
			private final SettableValue<Boolean> isHovered;
			private final SettableValue<Boolean> isFocused;
			private final SettableValue<Boolean> isPressed;
			private final SettableValue<Boolean> isRightPressed;

			public Default(SettableValue<Boolean> hovered, SettableValue<Boolean> focused, SettableValue<Boolean> pressed,
				SettableValue<Boolean> rightPressed) {
				isHovered = hovered;
				isFocused = focused;
				isPressed = pressed;
				isRightPressed = rightPressed;
			}

			public Default() {
				this(SettableValue.build(boolean.class).withValue(false).build(), //
					SettableValue.build(boolean.class).withValue(false).build(), //
					SettableValue.build(boolean.class).withValue(false).build(), //
					SettableValue.build(boolean.class).withValue(false).build());
			}

			@Override
			public SettableValue<Boolean> isHovered() {
				return isHovered;
			}

			@Override
			public SettableValue<Boolean> isFocused() {
				return isFocused;
			}

			@Override
			public SettableValue<Boolean> isPressed() {
				return isPressed;
			}

			@Override
			public SettableValue<Boolean> isRightPressed() {
				return isRightPressed;
			}
		}
	}

	@Override
	QuickWidgetStyle getStyle();

	/** @return The parent container, if any */
	QuickContainer<?> getParent();

	ObservableValue<String> getName();

	/** @return This widget's border */
	QuickBorder getBorder();

	/** @return The tool tip to display when the user hovers over this widget */

	SettableValue<String> getTooltip();

	/** @return The value determining when this widget is to be visible */
	SettableValue<Boolean> isVisible();

	SettableValue<Boolean> isHovered();

	SettableValue<Boolean> isFocused();

	SettableValue<Boolean> isPressed();

	SettableValue<Boolean> isRightPressed();

	ObservableCollection<QuickEventListener> getEventListeners();

	void setContext(WidgetContext ctx) throws ModelInstantiationException;

	/** An abstract {@link QuickWidget} implementation */
	public abstract class Abstract extends QuickStyledElement.Abstract implements QuickWidget {
		private final SettableValue<String> theName;
		private final SettableValue<SettableValue<Boolean>> isHovered;
		private final SettableValue<SettableValue<Boolean>> isFocused;
		private final SettableValue<SettableValue<Boolean>> isPressed;
		private final SettableValue<SettableValue<Boolean>> isRightPressed;

		private QuickBorder theBorder;
		private final SettableValue<SettableValue<String>> theTooltip;
		private final SettableValue<SettableValue<Boolean>> isVisible;
		private final ObservableCollection<QuickEventListener> theEventListeners;

		/**
		 * @param interpreted The interpretation instantiating this widget
		 * @param parent The parent element
		 */
		protected Abstract(QuickWidget.Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theName = SettableValue.build(String.class).build();
			theTooltip = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			isVisible = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			theEventListeners = ObservableCollection.build(QuickEventListener.class).build();

			isHovered = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			isFocused = SettableValue.build(isHovered.getType()).build();
			isPressed = SettableValue.build(isHovered.getType()).build();
			isRightPressed = SettableValue.build(isHovered.getType()).build();
		}

		@Override
		public QuickWidgetStyle getStyle() {
			return (QuickWidgetStyle) super.getStyle();
		}

		@Override
		public QuickContainer<?> getParent() {
			ExElement parent = getParentElement();
			return parent instanceof QuickContainer ? (QuickContainer<?>) parent : null;
		}

		@Override
		public ObservableValue<String> getName() {
			return theName.unsettable();
		}

		@Override
		public QuickBorder getBorder() {
			return theBorder;
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
		public void setContext(WidgetContext ctx) throws ModelInstantiationException {
			isHovered.set(ctx.isHovered(), null);
			isFocused.set(ctx.isFocused(), null);
			isPressed.set(ctx.isPressed(), null);
			isRightPressed.set(ctx.isRightPressed(), null);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			ExWithElementModel extModels = getAddOn(ExWithElementModel.class);
			extModels.satisfyElementValue("hovered", SettableValue.flatten(isHovered));
			extModels.satisfyElementValue("focused", SettableValue.flatten(isFocused));
			extModels.satisfyElementValue("pressed", SettableValue.flatten(isPressed));
			extModels.satisfyElementValue("rightPressed", SettableValue.flatten(isRightPressed));
			QuickWidget.Interpreted<?> myInterpreted = (QuickWidget.Interpreted<?>) interpreted;
			theName.set(myInterpreted.getName(), null);
			theBorder = myInterpreted.getBorder() == null ? null : myInterpreted.getBorder().create(this);
			if (theBorder != null)
				theBorder.update(myInterpreted.getBorder(), myModels);
			theTooltip.set(myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().instantiate().get(myModels), null);
			if (myInterpreted.isVisible() != null)
				isVisible.set(myInterpreted.isVisible().instantiate().get(myModels), null);
			try (Transaction t = theEventListeners.lock(true, null)) {
				CollectionUtils
				.synchronize(theEventListeners, myInterpreted.getEventListeners(), (l, i) -> l.getIdentity() == i.getIdentity())//
				.<ModelInstantiationException> simpleE(l -> {
					QuickEventListener listener = l.create(this);
					listener.update(l, myModels);
					return listener;
				})//
				.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), myModels))//
				.adjust();
			}
		}
	}

	public interface QuickWidgetStyle extends QuickTextStyle {
		public interface Def extends QuickTextStyle.Def {
			QuickStyleAttributeDef getColor();

			QuickStyleAttributeDef getMouseCursor();

			public class Default extends QuickTextStyle.Def.Abstract implements QuickWidgetStyle.Def {
				private final QuickStyleAttributeDef theColor;
				private final QuickStyleAttributeDef theMouseCursor;

				public Default(QuickInstanceStyle.Def parent, QuickWidget.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "widget");
					theColor = addApplicableAttribute(typeStyle.getAttribute("color"));
					theMouseCursor = addApplicableAttribute(typeStyle.getAttribute("mouse-cursor"));
				}

				@Override
				public QuickStyleAttributeDef getColor() {
					return theColor;
				}

				@Override
				public QuickStyleAttributeDef getMouseCursor() {
					return theMouseCursor;
				}

				@Override
				public QuickWidgetStyle.Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					return new Interpreted.Default(this, (QuickWidget.Interpreted<?>) parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent, env));
				}
			}
		}

		public interface Interpreted extends QuickTextStyle.Interpreted {
			QuickElementStyleAttribute<Color> getColor();

			QuickElementStyleAttribute<MouseCursor> getMouseCursor();

			@Override
			QuickWidgetStyle create(QuickStyledElement styledElement);

			public class Default extends QuickTextStyle.Interpreted.Abstract implements QuickWidgetStyle.Interpreted {
				private QuickElementStyleAttribute<Color> theColor;
				private QuickElementStyleAttribute<MouseCursor> theMouseCursor;

				public Default(Def definition, QuickWidget.Interpreted<?> styledElement, QuickInstanceStyle.Interpreted parent,
					QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Color> getColor() {
					return theColor;
				}

				@Override
				public QuickElementStyleAttribute<MouseCursor> getMouseCursor() {
					return theMouseCursor;
				}

				@Override
				public void update(InterpretedExpressoEnv env, Applications appCache) throws ExpressoInterpretationException {
					super.update(env, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theColor = get(cache.getAttribute(getDefinition().getColor(), Color.class, env));
					theMouseCursor = get(cache.getAttribute(getDefinition().getMouseCursor(), MouseCursor.class, env));
				}

				@Override
				public QuickWidgetStyle create(QuickStyledElement styledElement) {
					return new QuickWidgetStyle.Default(this, (QuickWidget) styledElement);
				}
			}
		}

		ObservableValue<Color> getColor();

		ObservableValue<MouseCursor> getMouseCursor();

		public class Default extends QuickTextStyle.Abstract implements QuickWidgetStyle {
			private final ObservableValue<Color> theColor;
			private final ObservableValue<MouseCursor> theMouseCursor;

			public Default(QuickWidgetStyle.Interpreted interpreted, QuickWidget widget) {
				super(interpreted, widget);
				theColor = getApplicableAttribute(interpreted.getColor().getAttribute());
				theMouseCursor = getApplicableAttribute(interpreted.getMouseCursor().getAttribute());
			}

			@Override
			public ObservableValue<Color> getColor() {
				return theColor;
			}

			@Override
			public ObservableValue<MouseCursor> getMouseCursor() {
				return theMouseCursor;
			}
		}
	}
}
