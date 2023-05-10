package org.observe.quick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ExpressoRuntimeException;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public interface QuickEventListener extends QuickElement {
	public interface Def<L extends QuickEventListener> extends QuickElement.Def<L> {
		List<CompiledExpression> getFilters();

		CompiledExpression getAction();

		@Override
		Def<L> update(AbstractQIS<?> session) throws QonfigInterpretationException;

		Interpreted<? extends L> interpret(QuickElement.Interpreted<?> parent);

		public abstract class Abstract<L extends QuickEventListener> extends QuickElement.Def.Abstract<L> implements Def<L> {
			private final List<CompiledExpression> theFilters;
			private CompiledExpression theAction;

			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theFilters = new ArrayList<>();
			}

			@Override
			public CompiledExpression getAction() {
				return theAction;
			}

			@Override
			public List<CompiledExpression> getFilters() {
				return Collections.unmodifiableList(theFilters);
			}

			@Override
			public Def.Abstract<L> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				theFilters.clear();
				for (ExpressoQIS filter : getExpressoSession().forChildren("filter"))
					theFilters.add(filter.getValueExpression());
				theAction = getExpressoSession().getValueExpression();
				if (theAction.getExpression() == ObservableExpression.EMPTY)
					throw new QonfigInterpretationException("No action for event listener", session.getElement().getPositionInFile(), 0);
				return this;
			}
		}
	}

	public interface Interpreted<L extends QuickEventListener> extends QuickElement.Interpreted<L> {
		@Override
		Def<? super L> getDefinition();

		List<InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>>> getFilters();

		InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction();

		Interpreted<L> update() throws ExpressoInterpretationException;

		L create(QuickElement parent);

		public abstract class Abstract<L extends QuickEventListener> extends QuickElement.Interpreted.Abstract<L>
		implements Interpreted<L> {
			private final List<InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>>> theFilters;
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAction;

			public Abstract(Def<? super L> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
				theFilters = new ArrayList<>();
			}

			@Override
			public Def<? super L> getDefinition() {
				return (Def<? super L>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction() {
				return theAction;
			}

			@Override
			public List<InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>>> getFilters() {
				return Collections.unmodifiableList(theFilters);
			}

			@Override
			public Interpreted.Abstract<L> update() throws ExpressoInterpretationException {
				super.update();
				theFilters.clear();
				for (CompiledExpression filter : getDefinition().getFilters())
					theFilters.add(filter.evaluate(ModelTypes.Value.BOOLEAN).interpret());
				theAction = getDefinition().getAction().evaluate(ModelTypes.Action.any()).interpret();
				return this;
			}
		}
	}

	public interface ListenerContext {
		SettableValue<Boolean> isAltPressed();

		SettableValue<Boolean> isCtrlPressed();

		SettableValue<Boolean> isShiftPressed();

		public class Default implements ListenerContext {
			private final SettableValue<Boolean> isAltPressed;
			private final SettableValue<Boolean> isCtrlPressed;
			private final SettableValue<Boolean> isShiftPressed;

			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed) {
				isAltPressed = altPressed;
				isCtrlPressed = ctrlPressed;
				isShiftPressed = shiftPressed;
			}

			@Override
			public SettableValue<Boolean> isAltPressed() {
				return isAltPressed;
			}

			@Override
			public SettableValue<Boolean> isCtrlPressed() {
				return isCtrlPressed;
			}

			@Override
			public SettableValue<Boolean> isShiftPressed() {
				return isShiftPressed;
			}
		}
	}

	@Override
	Interpreted<?> getInterpreted();

	void setListenerContext(ListenerContext ctx) throws ModelInstantiationException;

	ObservableValue<Boolean> getFilter();

	ObservableAction<?> getAction();

	QuickEventListener update(ModelSetInstance models) throws ModelInstantiationException;

	public abstract class Abstract extends QuickElement.Abstract implements QuickEventListener {
		private final ObservableCollection<SettableValue<Boolean>> theFilters;
		private final ObservableValue<Boolean> theCondensedFilter;
		private ObservableAction<?> theAction;

		protected Abstract(QuickEventListener.Interpreted<?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theFilters = ObservableCollection
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			theCondensedFilter = theFilters.flow()//
				.flattenValues(boolean.class, v -> v)//
				.collect()//
				.observeFind(b -> !b)//
				.anywhere()//
				.withDefault(() -> false).find()//
				.map(found -> !found);
		}

		@Override
		public QuickEventListener.Interpreted<?> getInterpreted() {
			return (QuickEventListener.Interpreted<?>) super.getInterpreted();
		}

		@Override
		public void setListenerContext(ListenerContext ctx) throws ModelInstantiationException {
			if (ctx == null)
				return;
			satisfyContextValue("altPressed", ModelTypes.Value.BOOLEAN, ctx.isAltPressed());
			satisfyContextValue("ctrlPressed", ModelTypes.Value.BOOLEAN, ctx.isCtrlPressed());
			satisfyContextValue("shiftPressed", ModelTypes.Value.BOOLEAN, ctx.isShiftPressed());
		}

		@Override
		public ObservableValue<Boolean> getFilter() {
			return theCondensedFilter;
		}

		@Override
		public ObservableAction<?> getAction() {
			return ObservableAction.of(TypeTokens.get().OBJECT, cause -> {
				if (theAction != null) {
					try {
						return theAction.act(cause);
					} catch (RuntimeException | Error e) {
						throw new ExpressoRuntimeException(e.toString(), getInterpreted().getDefinition().getElement().getPositionInFile(), e);
					}
				} else
					return null;
			});
		}

		@Override
		public QuickEventListener.Abstract update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			theFilters.clear();
			for (InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> filter : getInterpreted().getFilters())
				theFilters.add(filter.get(models));
			theAction = getInterpreted().getAction().get(getModels());
			return this;
		}
	}
}