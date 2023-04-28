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

public interface QuickWidget extends QuickElement {
	public interface Def<W extends QuickWidget> extends QuickElement.Def<W> {
		QuickContainer2.Def<?, ?> getParent();

		QuickCompiledStyle getStyle();

		String getName();

		CompiledExpression getTooltip();

		CompiledExpression isVisible();

		@Override
		Def<W> update(AbstractQIS<?> session) throws QonfigInterpretationException;

		Interpreted<? extends W> interpret(QuickContainer2.Interpreted<?, ?> parent);

		public abstract class Abstract<W extends QuickWidget> extends QuickElement.Def.Abstract<W> implements Def<W> {
			private QuickCompiledStyle theStyle;
			private String theName;
			private CompiledExpression theTooltip;
			private CompiledExpression isVisible;

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

	public static class QuickInterpretationCache {
		public final Map<CompiledStyleApplication, InterpretedStyleApplication> applications = new HashMap<>();
	}

	public interface Interpreted<W extends QuickWidget> extends QuickElement.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		QuickContainer2.Interpreted<?, ?> getParent();

		TypeToken<W> getWidgetType();

		QuickInterpretedStyle getStyle();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

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

		Interpreted<W> update(InterpretedModelSet models, QuickInterpretationCache cache) throws ExpressoInterpretationException;

		W create(QuickContainer2<?> parent);

		public abstract class Abstract<W extends QuickWidget> extends QuickElement.Interpreted.Abstract<W> implements Interpreted<W> {
			private QuickInterpretedStyle theStyle;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible;

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
			public Interpreted.Abstract<W> update(InterpretedModelSet models, QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
				super.update(models);
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

	public static class QuickInstantiationCache {
		public final Map<CompiledStyleApplication, InterpretedStyleApplication> applications = new HashMap<>();
	}

	@Override
	Interpreted<?> getInterpreted();

	QuickContainer2<?> getParent();

	@Override
	ModelSetInstance getModels();

	SettableValue<String> getTooltip();

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

	QuickWidget update(ModelSetInstance models, QuickInstantiationCache cache) throws ModelInstantiationException;

	// TODO border

	// TODO mouse and key listeners

	public abstract class Abstract extends QuickElement.Abstract implements QuickWidget {
		private final SettableValue<SettableValue<String>> theTooltip;
		private final SettableValue<SettableValue<Boolean>> isVisible;

		public Abstract(QuickWidget.Interpreted<?> interpreted, QuickContainer2<?> parent) {
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
			return SettableValue.flatten(isVisible);
		}

		@Override
		public QuickWidget update(ModelSetInstance models, QuickInstantiationCache cache) throws ModelInstantiationException {
			super.update(models);
			QuickWidget parent = getParent();
			StyleQIS.installParentModels(getModels(), parent == null ? null : parent.getModels());
			if (getInterpreted().getTooltip() != null)
				theTooltip.set(getInterpreted().getTooltip().get(models), null);
			if (getInterpreted().isVisible() != null)
				isVisible.set(getInterpreted().isVisible().get(models), null);
			return this;
		}
	}
}
