package org.observe.quick;

import java.util.HashMap;
import java.util.Map;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
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
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickWidget {
	public static final String QUICK_DEF = "QuickWidgetDef";

	public interface Def<W extends QuickWidget> extends QuickCompiledStructure {
		QuickContainer2.Def<?, ?> getParent();

		QuickCompiledStyle getStyle();

		String getName();

		CompiledExpression getTooltip();

		CompiledExpression isVisible();

		Def<W> update(AbstractQIS<?> session) throws QonfigInterpretationException;

		Interpreted<? extends W> interpret(QuickContainer2.Interpreted<?, ?> parent) throws ExpressoInterpretationException;

		public abstract class Abstract<W extends QuickWidget> implements Def<W> {
			private final QuickContainer2.Def<?, ?> theParent;
			private StyleQIS theStyleSession;
			private ExpressoQIS theExpressoSession;
			private QuickCompiledStyle theStyle;
			private String theName;
			private CompiledExpression theTooltip;
			private CompiledExpression isVisible;

			public Abstract(AbstractQIS<?> session) throws QonfigInterpretationException {
				QuickContainer2.Def<?, ?> parent = (QuickContainer2.Def<?, ?>) session.get(QUICK_DEF);
				session.put(QUICK_DEF, this);
				theParent = parent;
			}

			@Override
			public StyleQIS getStyleSession() {
				return theStyleSession;
			}

			@Override
			public ExpressoQIS getExpressoSession() {
				return theExpressoSession;
			}

			@Override
			public QuickContainer2.Def<?, ?> getParent() {
				return theParent;
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
			public Abstract<W> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				theStyleSession = session.as(StyleQIS.class);
				theExpressoSession = session.as(ExpressoQIS.class);
				theStyle = theStyleSession.getStyle();
				theName = theExpressoSession.getAttributeText("name");
				theTooltip = theExpressoSession.getAttributeExpression("tooltip");
				isVisible = theExpressoSession.getAttributeExpression("visible");
				return this;
			}
		}
	}

	public static class QuickInterpretationCache {
		public final Map<CompiledStyleApplication, InterpretedStyleApplication> applications = new HashMap<>();
	}

	public interface Interpreted<W extends QuickWidget> {
		Def<? super W> getDefinition();

		TypeToken<W> getWidgetType();

		QuickInterpretedStyle getStyle();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible();

		Interpreted<W> update(InterpretedModelSet models, QuickInterpretationCache cache) throws ExpressoInterpretationException;

		W create(QuickContainer2<?> parent, ModelSetInstance models) throws ModelInstantiationException;

		public abstract class Abstract<W extends QuickWidget> implements Interpreted<W> {
			private final Def<? super W> theDefinition;
			private final QuickContainer2.Interpreted<?, ?> theParent;
			private QuickInterpretedStyle theStyle;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible;

			public Abstract(Def<? super W> definition, QuickContainer2.Interpreted<?, ?> parent) throws ExpressoInterpretationException {
				theDefinition = definition;
				theParent = parent;
			}

			@Override
			public Def<? super W> getDefinition() {
				return theDefinition;
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
			public Abstract<W> update(InterpretedModelSet models, QuickInterpretationCache cache) throws ExpressoInterpretationException {
				theDefinition.getExpressoSession().interpretLocalModel();
				theStyle = theDefinition.getStyle().interpret(theParent == null ? null : theParent.getStyle(), cache.applications);
				theTooltip = theDefinition.getTooltip() == null ? null
					: theDefinition.getTooltip().evaluate(ModelTypes.Value.STRING).interpret();
				isVisible = theDefinition.isVisible() == null ? null
					: theDefinition.isVisible().evaluate(ModelTypes.Value.BOOLEAN).interpret();
				return this;
			}
		}
	}

	public static class QuickInstantiationCache {
		public final Map<CompiledStyleApplication, InterpretedStyleApplication> applications = new HashMap<>();
	}

	Interpreted<?> getInterpreted();

	QuickContainer2<?> getParent();

	ModelSetInstance getModels();

	SettableValue<String> getTooltip();

	SettableValue<Boolean> isVisible();

	QuickWidget update(ModelSetInstance models, QuickInstantiationCache cache) throws ModelInstantiationException;

	// TODO border

	// TODO mouse and key listeners

	public abstract class Abstract implements QuickWidget {
		private final Interpreted<?> theInterpreted;
		private final QuickContainer2<?> theParent;
		private ModelSetInstance theModels;
		private final SettableValue<SettableValue<String>> theTooltip;
		private final SettableValue<SettableValue<Boolean>> isVisible;

		public Abstract(Interpreted<?> interpreted, QuickContainer2<?> parent, ModelSetInstance models) throws ModelInstantiationException {
			theInterpreted = interpreted;
			theParent = parent;
			theTooltip = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			isVisible = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
		}

		@Override
		public QuickWidget update(ModelSetInstance models, QuickInstantiationCache cache) throws ModelInstantiationException {
			models = theInterpreted.getDefinition().getExpressoSession().wrapLocal(models);
			StyleQIS.installParentModels(models, theParent == null ? null : theParent.getModels());
			theModels = models;
			if (theInterpreted.getTooltip() != null)
				theTooltip.set(theInterpreted.getTooltip().get(models), null);
			if (theInterpreted.isVisible() != null)
				isVisible.set(theInterpreted.isVisible().get(models), null);
			return this;
		}

		@Override
		public Interpreted<?> getInterpreted() {
			return theInterpreted;
		}

		@Override
		public QuickContainer2<?> getParent() {
			return theParent;
		}

		@Override
		public ModelSetInstance getModels() {
			return theModels;
		}

		@Override
		public SettableValue<String> getTooltip() {
			return SettableValue.flatten(theTooltip);
		}

		@Override
		public SettableValue<Boolean> isVisible() {
			return SettableValue.flatten(isVisible);
		}
	}
}
