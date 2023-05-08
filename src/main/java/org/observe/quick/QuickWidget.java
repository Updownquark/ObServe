package org.observe.quick;

import java.awt.Color;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.TypeConversionException;
import org.observe.quick.style.CompiledStyleApplication;
import org.observe.quick.style.InterpretedStyleApplication;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
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

		@Override
		Def<W> update(AbstractQIS<?> session) throws QonfigInterpretationException;

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

			/**
			 * @param parent The parent container definition
			 * @param element The element that this widget is interpreted from
			 */
			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
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
			public Def.Abstract<W> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				theBorder = QuickElement.useOrReplace(QuickBorder.Def.class, theBorder, session, "border");
				theName = getExpressoSession().getAttributeText("name");
				theTooltip = getExpressoSession().getAttributeExpression("tooltip");
				isVisible = getExpressoSession().getAttributeExpression("visible");
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

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			protected Abstract(Def<? super W> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
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
			public Interpreted.Abstract<W> update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
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

	@Override
	<AO extends QuickAddOn<?>> AO getAddOn(Class<AO> addOn);

	@Override
	Collection<QuickAddOn<?>> getAddOns();

	@Override
	default <AO extends QuickAddOn<?>, T> T getAddOnValue(Class<AO> addOn, Function<? super AO, ? extends T> fn) {
		AO ao = getAddOn(addOn);
		return ao == null ? null : fn.apply(ao);
	}

	void setContext(WidgetContext ctx) throws ModelInstantiationException;

	/**
	 * Populates and updates this widget instance. Must be called once after being instantiated.
	 *
	 * @param models The model instance for this widget
	 * @return This widget
	 * @throws ModelInstantiationException If any of the models in this widget or its content cannot be instantiated
	 */
	QuickWidget update(ModelSetInstance models) throws ModelInstantiationException;

	// TODO mouse and key listeners

	/** An abstract {@link QuickWidget} implementation */
	public abstract class Abstract extends QuickStyledElement.Abstract implements QuickWidget {
		private QuickBorder theBorder;
		private final SettableValue<SettableValue<String>> theTooltip;
		private final SettableValue<SettableValue<Boolean>> isVisible;

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
		public void setContext(WidgetContext ctx) throws ModelInstantiationException {
			satisfyContextValue("hovered", ModelTypes.Value.BOOLEAN, ctx.isHovered());
			satisfyContextValue("focused", ModelTypes.Value.BOOLEAN, ctx.isFocused());
			satisfyContextValue("pressed", ModelTypes.Value.BOOLEAN, ctx.isPressed());
			satisfyContextValue("rightPressed", ModelTypes.Value.BOOLEAN, ctx.isRightPressed());
		}

		protected <M, MV extends M> void satisfyContextValue(String valueName, ModelInstanceType<M, MV> type, MV value)
			throws ModelInstantiationException {
			if (value != null) {
				try {
					DynamicModelValue.satisfyDynamicValue(valueName, type, getModels(), value);
				} catch (ModelException e) {
					throw new ModelInstantiationException("No " + valueName + " value?",
						getInterpreted().getDefinition().getExpressoSession().getElement().getPositionInFile(), 0, e);
				} catch (TypeConversionException e) {
					throw new IllegalStateException(valueName + " is not a " + type + "?", e);
				}
			}
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
