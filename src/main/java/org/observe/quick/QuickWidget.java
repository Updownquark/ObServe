package org.observe.quick;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
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
	/**
	 * The definition of a {@link QuickWidget}
	 *
	 * @param <W> The type of widget that this definition is for
	 */
	public interface Def<W extends QuickWidget> extends QuickTextElement.Def<W> {
		@Override
		QuickWidgetStyle.Def getStyle();

		/** @return The container definition that this widget is a child of */
		QuickContainer2.Def<?, ?> getParent();

		/** @return This widget's border */
		QuickBorder.Def<?> getBorder();

		/** @return This widget's name, typically for debugging */
		String getName();

		/** @return The tool tip to display when the user hovers over this widget */
		CompiledExpression getTooltip();

		/** @return The expression determining when this widget is to be visible */
		CompiledExpression isVisible();

		List<QuickEventListener.Def<?>> getEventListeners();

		@Override
		Def<W> update(ExpressoQIS session) throws QonfigInterpretationException;

		/**
		 * @param parent The parent container interpretation
		 * @return The new widget interpretation
		 */
		Interpreted<? extends W> interpret(QuickElement.Interpreted<?> parent);

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
			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theEventListeners = new ArrayList<>();
			}

			@Override
			public QuickWidgetStyle.Def getStyle() {
				return (QuickWidgetStyle.Def) super.getStyle();
			}

			@Override
			public QuickContainer2.Def<?, ?> getParent() {
				QuickElement.Def<?> parent = getParentElement();
				return parent instanceof QuickContainer2.Def ? (QuickContainer2.Def<?, ?>) parent : null;
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
			public Def.Abstract<W> update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				theBorder = QuickElement.useOrReplace(QuickBorder.Def.class, theBorder, session, "border");
				theName = session.getAttributeText("name");
				theTooltip = session.getAttributeExpression("tooltip");
				isVisible = session.getAttributeExpression("visible");
				CollectionUtils
				.synchronize(theEventListeners, session.forChildren("event-listener"),
					(l, s) -> QuickElement.typesEqual(l.getElement(), s.getElement()))
				.simpleE(s -> s.interpret(QuickEventListener.Def.class).update(s))//
				.onCommonX(el -> el.getLeftValue().update(el.getRightValue())).adjust();
				return this;
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
		QuickContainer2.Interpreted<?, ?> getParent();

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
		W create(QuickElement parent);

		@Override
		Interpreted<W> update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException;

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
			protected Abstract(Def<? super W> definition, QuickElement.Interpreted<?> parent) {
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
			public QuickContainer2.Interpreted<?, ?> getParent() {
				QuickElement.Interpreted<?> parent = getParentElement();
				return parent instanceof QuickContainer2.Interpreted ? (QuickContainer2.Interpreted<?, ?>) parent : null;
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
			public Interpreted.Abstract<W> update(QuickStyledElement.QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
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
				.simpleE(l -> l.interpret(this).update())//
				.onCommonX(el -> el.getLeftValue().update())//
				.adjust();
				return this;
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
	Interpreted<?> getInterpreted();

	@Override
	QuickWidgetStyle getStyle();

	/** @return The parent container, if any */
	QuickContainer2<?> getParent();

	@Override
	ModelSetInstance getModels();

	/** @return This widget's border */
	QuickBorder getBorder();

	/** @return The tool tip to display when the user hovers over this widget */
	SettableValue<String> getTooltip();

	/** @return The value determining when this widget is to be visible */
	SettableValue<Boolean> isVisible();

	ObservableCollection<QuickEventListener> getEventListeners();

	void setContext(WidgetContext ctx) throws ModelInstantiationException;

	WidgetContext getContext();

	/**
	 * Populates and updates this widget instance. Must be called once after being instantiated.
	 *
	 * @param models The model instance for this widget
	 * @return This widget
	 * @throws ModelInstantiationException If any of the models in this widget or its content cannot be instantiated
	 */
	QuickWidget update(ModelSetInstance models) throws ModelInstantiationException;

	/** An abstract {@link QuickWidget} implementation */
	public abstract class Abstract extends QuickStyledElement.Abstract implements QuickWidget {
		private QuickBorder theBorder;
		private final SettableValue<SettableValue<String>> theTooltip;
		private final SettableValue<SettableValue<Boolean>> isVisible;
		private WidgetContext theContext;
		private final ObservableCollection<QuickEventListener> theEventListeners;

		/**
		 * @param interpreted The interpretation instantiating this widget
		 * @param parent The parent element
		 */
		public Abstract(QuickWidget.Interpreted<?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theTooltip = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			isVisible = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			theEventListeners = ObservableCollection.build(QuickEventListener.class).build();
		}

		@Override
		public QuickWidget.Interpreted<?> getInterpreted() {
			return (QuickWidget.Interpreted<?>) super.getInterpreted();
		}

		@Override
		public QuickWidgetStyle getStyle() {
			return (QuickWidgetStyle) super.getStyle();
		}

		@Override
		public QuickContainer2<?> getParent() {
			QuickElement parent = getParentElement();
			return parent instanceof QuickContainer2 ? (QuickContainer2<?>) parent : null;
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
		public ObservableCollection<QuickEventListener> getEventListeners() {
			return theEventListeners.flow().unmodifiable(false).collect();
		}

		@Override
		public void setContext(WidgetContext ctx) throws ModelInstantiationException {
			theContext = ctx;
			satisfyContextValue("hovered", ModelTypes.Value.BOOLEAN, ctx.isHovered());
			satisfyContextValue("focused", ModelTypes.Value.BOOLEAN, ctx.isFocused());
			satisfyContextValue("pressed", ModelTypes.Value.BOOLEAN, ctx.isPressed());
			satisfyContextValue("rightPressed", ModelTypes.Value.BOOLEAN, ctx.isRightPressed());
		}

		@Override
		public WidgetContext getContext() {
			return theContext;
		}

		@Override
		public QuickWidget.Abstract update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			theBorder = getInterpreted().getBorder() == null ? null : getInterpreted().getBorder().create(this);
			if (theBorder != null)
				theBorder.update(getModels());
			if (getInterpreted().getTooltip() != null)
				theTooltip.set(getInterpreted().getTooltip().get(getModels()), null);
			if (getInterpreted().isVisible() != null)
				isVisible.set(getInterpreted().isVisible().get(getModels()), null);
			try (Transaction t = theEventListeners.lock(true, null)) {
				CollectionUtils.synchronize(theEventListeners, getInterpreted().getEventListeners(), (l, i) -> l.getInterpreted() == i)//
				.<ModelInstantiationException> simpleE(l -> l.create(this).update(getModels()))//
				.onCommonX(el -> el.getLeftValue().update(getModels()))//
				.adjust();
			}
			return this;
		}
	}

	public interface QuickWidgetStyle extends QuickTextStyle {
		public interface Def extends QuickTextStyle.Def {
			QuickStyleAttribute<Color> getColor();

			public class Default extends QuickTextStyle.Def.Abstract implements QuickWidgetStyle.Def {
				private final QuickStyleAttribute<Color> theColor;

				public Default(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(getElement(), QuickCore.NAME, QuickCore.VERSION, "widget");
					theColor = (QuickStyleAttribute<Color>) typeStyle.getAttribute("color", Color.class);
				}

				@Override
				public QuickStyleAttribute<Color> getColor() {
					return theColor;
				}

				@Override
				public QuickWidgetStyle.Interpreted interpret(QuickInterpretedStyle parent,
					Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException {
					return new Interpreted.Default(this, parent, getWrapped().interpret(parent, applications));
				}
			}
		}

		public interface Interpreted extends QuickTextStyle.Interpreted {
			@Override
			Def getCompiled();

			QuickElementStyleAttribute<Color> getColor();

			@Override
			QuickWidgetStyle create();

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
				public QuickWidgetStyle create() {
					return new QuickWidgetStyle.Default(this);
				}
			}
		}

		@Override
		QuickWidgetStyle.Interpreted getInterpreted();

		public ObservableValue<Color> getColor();

		public class Default extends QuickTextStyle.Abstract implements QuickWidgetStyle {
			private final SettableValue<ObservableValue<Color>> theColor;

			public Default(QuickWidgetStyle.Interpreted interpreted) {
				super(interpreted);
				theColor = SettableValue
					.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Color>> parameterized(Color.class)).build();
			}

			@Override
			public QuickWidgetStyle.Interpreted getInterpreted() {
				return (QuickWidgetStyle.Interpreted) super.getInterpreted();
			}

			@Override
			public ObservableValue<Color> getColor() {
				return ObservableValue.flatten(theColor);
			}

			@Override
			public void update(ModelSetInstance models) throws ModelInstantiationException {
				super.update(models);
				theColor.set(getInterpreted().getColor().evaluate(models), null);
			}
		}
	}
}
