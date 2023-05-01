package org.observe.quick;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.CompiledStyleApplication;
import org.observe.quick.style.InterpretedStyleApplication;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.StyleQIS;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** The base class for widgets in Quick */
public interface QuickWidget extends QuickElement {
	/**
	 * The definition of a {@link QuickWidget}
	 *
	 * @param <W> The type of widget that this definition is for
	 */
	public interface Def<W extends QuickWidget> extends QuickElement.Def<W> {
		/** @return The container definition that this widget is a child of */
		QuickContainer2.Def<?, ?> getParent();

		/** @return This widget's style */
		QuickCompiledStyle getStyle();

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
		public abstract class Abstract<W extends QuickWidget> extends QuickElement.Def.Abstract<W> implements Def<W> {
			private QuickCompiledStyle theStyle;
			private String theName;
			private CompiledExpression theTooltip;
			private CompiledExpression isVisible;

			/**
			 * @param parent The parent container definition
			 * @param element The element that this widget is interpreted from
			 */
			public Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public QuickContainer2.Def<?, ?> getParent() {
				QuickElement.Def<?> parent = getParentElement();
				return parent instanceof QuickContainer2.Def ? (QuickContainer2.Def<?, ?>) parent : null;
			}

			@Override
			public QuickCompiledStyle getStyle() {
				return theStyle;
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
				theStyle = getStyleSession().getStyle();
				theName = getExpressoSession().getAttributeText("name");
				theTooltip = getExpressoSession().getAttributeExpression("tooltip");
				isVisible = getExpressoSession().getAttributeExpression("visible");
				return this;
			}
		}
	}

	/** Needed to {@link Interpreted#update(InterpretedModelSet, QuickInterpretationCache) update} an interpreted widget */
	public static class QuickInterpretationCache {
		/** A cache of interpreted style applications */
		public final Map<CompiledStyleApplication, InterpretedStyleApplication> applications = new HashMap<>();
	}

	/**
	 * An interpretation of a {@link QuickWidget}
	 *
	 * @param <W> The type of widget that this interpretation is for
	 */
	public interface Interpreted<W extends QuickWidget> extends QuickElement.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		/** @return The parent container of this widget interpretation, if any */
		QuickContainer2.Interpreted<?, ?> getParent();

		/** @return The type of the widget produced by this interpretation */
		TypeToken<W> getWidgetType();

		/** @return This widget's interpreted style */
		QuickInterpretedStyle getStyle();

		/** @return The tool tip to display when the user hovers over this widget */
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

		/** @return The value determining when this widget is to be visible */
		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible();

		@Override
		<AO extends QuickAddOn.Interpreted<? super W, ?>> AO getAddOn(Class<AO> addOn);

		@Override
		Collection<QuickAddOn.Interpreted<? super W, ?>> getAddOns();

		@Override
		default <AO extends QuickAddOn.Interpreted<? super W, ?>, T> T getAddOnValue(Class<AO> addOn,
			Function<? super AO, ? extends T> fn) {
			AO ao = getAddOn(addOn);
			return ao == null ? null : fn.apply(ao);
		}

		/**
		 * Populates and updates this interpretation. Must be called once after being produced by the {@link #getDefinition() definition}.
		 *
		 * @param cache The cache to use to interpret the widget
		 * @return This interpretation
		 * @throws ExpressoInterpretationException If any models could not be interpreted from their expressions in this widget or its
		 *         content
		 */
		Interpreted<W> update(QuickInterpretationCache cache) throws ExpressoInterpretationException;

		/**
		 * Produces a widget instance
		 *
		 * @param parent The parent container, if any
		 * @return The new widget
		 */
		W create(QuickElement parent);

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <W> The type of widget that this interpretation is for
		 */
		public abstract class Abstract<W extends QuickWidget> extends QuickElement.Interpreted.Abstract<W> implements Interpreted<W> {
			private QuickInterpretedStyle theStyle;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible;

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			public Abstract(Def<? super W> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}

			@Override
			public QuickContainer2.Interpreted<?, ?> getParent() {
				QuickElement.Interpreted<?> parent = getParentElement();
				return parent instanceof QuickContainer2.Interpreted ? (QuickContainer2.Interpreted<?, ?>) parent : null;
			}

			@Override
			public QuickInterpretedStyle getStyle() {
				return theStyle;
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
			public Interpreted.Abstract<W> update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update();
				QuickContainer2.Interpreted<?, ?> parent = getParent();
				theStyle = getDefinition().getStyle().interpret(parent == null ? null : parent.getStyle(), cache.applications);
				theTooltip = getDefinition().getTooltip() == null ? null
					: getDefinition().getTooltip().evaluate(ModelTypes.Value.STRING).interpret();
				isVisible = getDefinition().isVisible() == null ? null
					: getDefinition().isVisible().evaluate(ModelTypes.Value.BOOLEAN).interpret();
				return this;
			}
		}
	}

	@Override
	Interpreted<?> getInterpreted();

	/** @return The parent container, if any */
	QuickContainer2<?> getParent();

	@Override
	ModelSetInstance getModels();

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

	/**
	 * Populates and updates this widget instance. Must be called once after being instantiated.
	 *
	 * @param models The model instance for this widget
	 * @return This widget
	 * @throws ModelInstantiationException If any of the models in this widget or its content cannot be instantiated
	 */
	QuickWidget update(ModelSetInstance models) throws ModelInstantiationException;

	// TODO border

	// TODO mouse and key listeners

	/** An abstract {@link QuickWidget} implementation */
	public abstract class Abstract extends QuickElement.Abstract implements QuickWidget {
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
		public QuickContainer2<?> getParent() {
			QuickElement parent = getParentElement();
			return parent instanceof QuickContainer2 ? (QuickContainer2<?>) parent : null;
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
		public QuickWidget.Abstract update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			QuickWidget parent = getParent();
			StyleQIS.installParentModels(getModels(), parent == null ? null : parent.getModels());
			if (getInterpreted().getTooltip() != null)
				theTooltip.set(getInterpreted().getTooltip().get(getModels()), null);
			if (getInterpreted().isVisible() != null)
				isVisible.set(getInterpreted().isVisible().get(getModels()), null);
			return this;
		}
	}
}
