package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickDialog extends ExElement {
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = "dialog",
		interpretation = Interpreted.class,
		instance = QuickDialog.class)
	public static interface Def<D extends QuickDialog> extends ExElement.Def<D> {
		@QonfigAttributeGetter("visible")
		CompiledExpression isVisible();

		@QonfigAttributeGetter("title")
		CompiledExpression getTitle();

		Interpreted<? extends D> interpret(ExElement.Interpreted<?> parent);

		public static abstract class Abstract<D extends QuickDialog> extends ExElement.Def.Abstract<D> implements Def<D> {
			private CompiledExpression isVisible;
			private CompiledExpression theTitle;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			public CompiledExpression isVisible() {
				return isVisible;
			}

			@Override
			public CompiledExpression getTitle() {
				return theTitle;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				isVisible = session.getAttributeExpression("visible");
				theTitle = session.getAttributeExpression("title");
			}
		}
	}

	public static interface Interpreted<D extends QuickDialog> extends ExElement.Interpreted<D> {
		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible();

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle();

		void updateDialog(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException;

		D create();

		public static abstract class Abstract<D extends QuickDialog> extends ExElement.Interpreted.Abstract<D> implements Interpreted<D> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTitle;

			protected Abstract(Def<? super D> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super D> getDefinition() {
				return (Def<? super D>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible() {
				return isVisible;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle() {
				return theTitle;
			}

			@Override
			public void updateDialog(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				update(expressoEnv);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);

				isVisible = getDefinition().isVisible().interpret(ModelTypes.Value.BOOLEAN, expressoEnv);
				theTitle = getDefinition().getTitle().interpret(ModelTypes.Value.STRING, expressoEnv);
			}
		}
	}

	SettableValue<Boolean> isVisible();

	SettableValue<String> getTitle();

	@Override
	QuickDialog copy(ExElement parent);

	public static abstract class Abstract extends ExElement.Abstract implements QuickDialog {
		private ModelValueInstantiator<SettableValue<Boolean>> theVisibleInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theTitleInstantiator;

		private SettableValue<SettableValue<Boolean>> isVisible;
		private SettableValue<SettableValue<String>> theTitle;

		protected Abstract(Object id) {
			super(id);
			isVisible = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			theTitle = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
				.build();
		}

		@Override
		public SettableValue<Boolean> isVisible() {
			return SettableValue.flatten(isVisible, () -> false);
		}

		@Override
		public SettableValue<String> getTitle() {
			return SettableValue.flatten(theTitle);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			QuickDialog.Interpreted<?> myInterpreted = (QuickDialog.Interpreted<?>) interpreted;
			theVisibleInstantiator = myInterpreted.isVisible().instantiate();
			theTitleInstantiator = myInterpreted.getTitle().instantiate();
		}

		@Override
		public void instantiated() {
			super.instantiated();

			theVisibleInstantiator.instantiate();
			theTitleInstantiator.instantiate();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			isVisible.set(theVisibleInstantiator.get(myModels), null);
			theTitle.set(theTitleInstantiator.get(myModels), null);
		}

		@Override
		public QuickDialog.Abstract copy(ExElement parent) {
			QuickDialog.Abstract copy = (QuickDialog.Abstract) super.copy(parent);

			copy.isVisible = SettableValue.build(isVisible.getType()).build();
			copy.theTitle = SettableValue.build(theTitle.getType()).build();

			return copy;
		}
	}
}
