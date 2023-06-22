package org.observe.quick;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.style.CompiledStyleApplication;
import org.observe.quick.style.InterpretedStyleApplication;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** The base class for widgets in Quick */
public interface QuickWidget extends QuickTextElement {
	public static final String WIDGET = "widget";

	public static final ExElement.AttributeValueGetter<QuickWidget, Interpreted<? extends QuickWidget>, Def<? extends QuickWidget>> NAME = ExElement.AttributeValueGetter
		.of(Def::getName, i -> i.getDefinition().getName(), QuickWidget::getName,
			"The name of the widget.  Typically only used for debugging");
	public static final ExElement.AttributeValueGetter.Expression<QuickWidget, Interpreted<? extends QuickWidget>, Def<? extends QuickWidget>, SettableValue<?>, SettableValue<String>> TOOLTIP = ExElement.AttributeValueGetter
		.ofX(Def::getTooltip, Interpreted::getTooltip, QuickWidget::getTooltip,
			"The tooltip to display when the user hovers the mouse over the widget, for user feedback");
	public static final ExElement.AttributeValueGetter.Expression<QuickWidget, Interpreted<? extends QuickWidget>, Def<? extends QuickWidget>, SettableValue<?>, SettableValue<Boolean>> VISIBLE = ExElement.AttributeValueGetter
		.ofX(Def::isVisible, Interpreted::isVisible, QuickWidget::isVisible,
			"Determines when the widget is displayed to the user or hidden");

	public static final ExElement.ChildElementGetter<QuickWidget, Interpreted<?>, Def<?>> BORDER = new ExElement.ChildElementGetter<QuickWidget, Interpreted<?>, Def<?>>() {
		@Override
		public String getDescription() {
			return "The border to draw around the widget";
		}

		@Override
		public List<? extends org.observe.expresso.qonfig.ExElement.Def<?>> getChildrenFromDef(Def<?> def) {
			return def.getBorder() == null ? Collections.emptyList() : Collections.singletonList(def.getBorder());
		}

		@Override
		public List<? extends org.observe.expresso.qonfig.ExElement.Interpreted<?>> getChildrenFromInterpreted(Interpreted<?> interp) {
			return interp.getBorder() == null ? Collections.emptyList() : Collections.singletonList(interp.getBorder());
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(QuickWidget element) {
			return element.getBorder() == null ? Collections.emptyList() : Collections.singletonList(element.getBorder());
		}
	};

	public static final ExElement.ChildElementGetter<QuickWidget, Interpreted<?>, Def<?>> EVENT_LISTENERS = new ExElement.ChildElementGetter<QuickWidget, Interpreted<?>, Def<?>>() {
		@Override
		public String getDescription() {
			return "Listeners to events like mouse clicks or key presses";
		}

		@Override
		public List<? extends org.observe.expresso.qonfig.ExElement.Def<?>> getChildrenFromDef(Def<?> def) {
			return def.getEventListeners();
		}

		@Override
		public List<? extends org.observe.expresso.qonfig.ExElement.Interpreted<?>> getChildrenFromInterpreted(Interpreted<?> interp) {
			return interp.getEventListeners();
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(QuickWidget element) {
			return element.getEventListeners();
		}
	};

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
		QuickBorder.Def<?> getBorder();

		/** @return This widget's name, typically for debugging */
		String getName();

		/** @return The tool tip to display when the user hovers over this widget */
		CompiledExpression getTooltip();

		/** @return The expression determining when this widget is to be visible */
		CompiledExpression isVisible();

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
			 * @param element The element that this widget is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
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
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, WIDGET);
				forAttribute(session.getAttributeDef(null, null, "name").getDeclared(), NAME);
				forAttribute(session.getAttributeDef(null, null, "tooltip").getDeclared(), TOOLTIP);
				forAttribute(session.getAttributeDef(null, null, "visible").getDeclared(), VISIBLE);
				forChild(session.getRole("border").getDeclared(), BORDER);
				forChild(session.getRole("event-listener").getDeclared(), EVENT_LISTENERS);
				super.update(session);
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
				return new QuickWidgetStyle.Def.Default(parentStyle, style);
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

		@Override
		QuickWidgetStyle.Interpreted getStyle();

		/** @return The parent container of this widget interpretation, if any */
		QuickContainer.Interpreted<?, ?> getParent();

		/** @return This widget's border */
		QuickBorder.Interpreted<?> getBorder();

		/** @return The type of the widget produced by this interpretation */
		TypeToken<W> getWidgetType();

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
			public void update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update(cache);
				if (getDefinition().getBorder() == null)
					theBorder = null;
				else if (theBorder == null || theBorder.getDefinition() != getDefinition().getBorder())
					theBorder = getDefinition().getBorder().interpret(this);
				if (theBorder != null)
					theBorder.update(cache);
				theTooltip = getDefinition().getTooltip() == null ? null
					: getDefinition().getTooltip().evaluate(ModelTypes.Value.STRING).interpret();
				isVisible = getDefinition().isVisible() == null ? null
					: getDefinition().isVisible().evaluate(ModelTypes.Value.BOOLEAN).interpret();
				CollectionUtils.synchronize(theEventListeners, getDefinition().getEventListeners(), (l, d) -> l.getDefinition() == d)//
				.simpleE(l -> {
					QuickEventListener.Interpreted<?> listener = l.interpret(this);
					listener.update();
					return listener;
				})//
				.onCommonX(el -> el.getLeftValue().update())//
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
			satisfyContextValue("hovered", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isHovered), myModels);
			satisfyContextValue("focused", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isFocused), myModels);
			satisfyContextValue("pressed", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isPressed), myModels);
			satisfyContextValue("rightPressed", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isRightPressed), myModels);
			QuickWidget.Interpreted<?> myInterpreted = (QuickWidget.Interpreted<?>) interpreted;
			theName.set(myInterpreted.getDefinition().getName(), null);
			theBorder = myInterpreted.getBorder() == null ? null : myInterpreted.getBorder().create(this);
			if (theBorder != null)
				theBorder.update(myInterpreted.getBorder(), myModels);
			theTooltip.set(myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().get(myModels), null);
			if (myInterpreted.isVisible() != null)
				isVisible.set(myInterpreted.isVisible().get(myModels), null);
			try (Transaction t = theEventListeners.lock(true, null)) {
				CollectionUtils
				.synchronize(theEventListeners, myInterpreted.getEventListeners(),
					(l, i) -> l.getIdentity() == i.getDefinition().getIdentity())//
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
			QuickStyleAttribute<Color> getColor();

			public class Default extends QuickTextStyle.Def.Abstract implements QuickWidgetStyle.Def {
				private final QuickStyleAttribute<Color> theColor;

				public Default(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "widget");
					theColor = (QuickStyleAttribute<Color>) typeStyle.getAttribute("color", Color.class);
				}

				@Override
				public QuickStyleAttribute<Color> getColor() {
					return theColor;
				}

				@Override
				public QuickWidgetStyle.Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException {
					return new Interpreted.Default(this, parent, getWrapped().interpret(parentEl, parent, applications));
				}
			}
		}

		public interface Interpreted extends QuickTextStyle.Interpreted {
			@Override
			Def getCompiled();

			QuickElementStyleAttribute<Color> getColor();

			@Override
			QuickWidgetStyle create(QuickStyledElement styledElement);

			public class Default extends QuickTextStyle.Interpreted.Abstract implements QuickWidgetStyle.Interpreted {
				private QuickElementStyleAttribute<Color> theColor;

				public Default(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(definition, parent, wrapped);
					theColor = wrapped.get(getCompiled().getColor());
				}

				@Override
				public QuickWidgetStyle.Def getCompiled() {
					return (QuickWidgetStyle.Def) super.getCompiled();
				}

				@Override
				public QuickElementStyleAttribute<Color> getColor() {
					return theColor;
				}

				@Override
				public QuickWidgetStyle create(QuickStyledElement styledElement) {
					return new QuickWidgetStyle.Default(this, (QuickWidget) styledElement);
				}
			}
		}

		public ObservableValue<Color> getColor();

		public class Default extends QuickTextStyle.Abstract implements QuickWidgetStyle {
			private final SettableValue<ObservableValue<Color>> theColor;

			public Default(QuickWidgetStyle.Interpreted interpreted, QuickWidget widget) {
				super(interpreted, widget);
				theColor = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
			}

			@Override
			public ObservableValue<Color> getColor() {
				return ObservableValue.flatten(theColor);
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, ModelSetInstance models) throws ModelInstantiationException {
				super.update(interpreted, models);
				QuickWidgetStyle.Interpreted myInterpreted = (QuickWidgetStyle.Interpreted) interpreted;
				theColor.set(myInterpreted.getColor().evaluate(models), null);
			}
		}
	}
}
