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
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public interface QuickEventListener extends QuickElement {
	public interface Def<L extends QuickEventListener> extends QuickElement.Def<L> {
		public static final String EVENT_LISTENER = "event-listener";

		List<CompiledExpression> getFilters();

		CompiledExpression getAction();

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
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, EVENT_LISTENER);
				super.update(session);
				theFilters.clear();
				for (ExpressoQIS filter : session.forChildren("filter"))
					theFilters.add(filter.getValueExpression());
				theAction = session.getValueExpression();
				if (theAction.getExpression() == ObservableExpression.EMPTY)
					throw new QonfigInterpretationException("No action for event listener", session.getElement().getPositionInFile(), 0);
			}
		}
	}

	public interface Interpreted<L extends QuickEventListener> extends QuickElement.Interpreted<L> {
		@Override
		Def<? super L> getDefinition();

		List<InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>>> getFilters();

		InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction();

		void update() throws ExpressoInterpretationException;

		L create(QuickElement parent);

		public abstract class Abstract<L extends QuickEventListener> extends QuickElement.Interpreted.Abstract<L>
		implements Interpreted<L> {
			private final List<InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>>> theFilters;
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAction;

			protected Abstract(Def<? super L> definition, QuickElement.Interpreted<?> parent) {
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
			public void update() throws ExpressoInterpretationException {
				super.update();
				theFilters.clear();
				for (CompiledExpression filter : getDefinition().getFilters())
					theFilters.add(filter.evaluate(ModelTypes.Value.BOOLEAN).interpret());
				theAction = getDefinition().getAction().evaluate(ModelTypes.Action.any()).interpret();
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

			public Default() {
				isAltPressed = SettableValue.build(boolean.class).withValue(false).build();
				isCtrlPressed = SettableValue.build(boolean.class).withValue(false).build();
				isShiftPressed = SettableValue.build(boolean.class).withValue(false).build();
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

	void setListenerContext(ListenerContext ctx) throws ModelInstantiationException;

	ObservableValue<Boolean> getFilter();

	ObservableAction<?> getAction();

	public abstract class Abstract extends QuickElement.Abstract implements QuickEventListener {
		private final ObservableCollection<SettableValue<Boolean>> theFilters;
		private final ObservableValue<Boolean> theCondensedFilter;
		private ObservableAction<?> theAction;
		private final SettableValue<SettableValue<Boolean>> isAltPressed;
		private final SettableValue<SettableValue<Boolean>> isCtrlPressed;
		private final SettableValue<SettableValue<Boolean>> isShiftPressed;

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
			isAltPressed = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			isCtrlPressed = SettableValue.build(isAltPressed.getType()).build();
			isShiftPressed = SettableValue.build(isAltPressed.getType()).build();
		}

		@Override
		public void setListenerContext(ListenerContext ctx) throws ModelInstantiationException {
			if (ctx == null)
				return;
			isAltPressed.set(ctx.isAltPressed(), null);
			isCtrlPressed.set(ctx.isCtrlPressed(), null);
			isShiftPressed.set(ctx.isShiftPressed(), null);
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
						reporting().error(e.toString(), e);
						return null;
					}
				} else
					return null;
			});
		}

		@Override
		protected void updateModel(QuickElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickElement.satisfyContextValue("altPressed", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isAltPressed), myModels, this);
			QuickElement.satisfyContextValue("ctrlPressed", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isCtrlPressed), myModels, this);
			QuickElement.satisfyContextValue("shiftPressed", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isShiftPressed), myModels,
				this);
			QuickEventListener.Interpreted<?> myInterpreted = (QuickEventListener.Interpreted<?>) interpreted;
			theFilters.clear();
			for (InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> filter : myInterpreted.getFilters())
				theFilters.add(filter.get(myModels));
			theAction = myInterpreted.getAction().get(myModels);
		}
	}
}
