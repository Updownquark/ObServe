package org.observe.quick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public interface QuickEventListener extends ExElement {
	public static final String EVENT_LISTENER = "event-listener";
	public static final ElementTypeTraceability<QuickEventListener, Interpreted<?>, Def<?>> LISTENER_TRACEABILITY = ElementTypeTraceability
		.<QuickEventListener, Interpreted<?>, Def<?>> build(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "event-listener")//
		.reflectMethods(Def.class, Interpreted.class, QuickEventListener.class)//
		.build();

	public interface Def<L extends QuickEventListener> extends ExElement.Def<L> {
		@QonfigChildGetter("filter")
		List<EventFilter.Def> getFilters();

		@QonfigAttributeGetter
		CompiledExpression getAction();

		Interpreted<? extends L> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<L extends QuickEventListener> extends ExElement.Def.Abstract<L> implements Def<L> {
			private final List<EventFilter.Def> theFilters;
			private CompiledExpression theAction;

			protected Abstract(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theFilters = new ArrayList<>();
			}

			@Override
			public CompiledExpression getAction() {
				return theAction;
			}

			@Override
			public List<EventFilter.Def> getFilters() {
				return Collections.unmodifiableList(theFilters);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(LISTENER_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.update(session);
				ExElement.syncDefs(EventFilter.Def.class, theFilters, session.forChildren("filter"));
				theAction = session.getValueExpression();
				if (theAction.getExpression() == ObservableExpression.EMPTY)
					throw new QonfigInterpretationException("No action for event listener", session.getElement().getPositionInFile(), 0);
			}
		}
	}

	public interface Interpreted<L extends QuickEventListener> extends ExElement.Interpreted<L> {
		@Override
		Def<? super L> getDefinition();

		List<EventFilter.Interpreted> getFilters();

		InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction();

		void update() throws ExpressoInterpretationException;

		L create(ExElement parent);

		public abstract class Abstract<L extends QuickEventListener> extends ExElement.Interpreted.Abstract<L>
		implements Interpreted<L> {
			private final List<EventFilter.Interpreted> theFilters;
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAction;

			protected Abstract(Def<? super L> definition, ExElement.Interpreted<?> parent) {
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
			public List<EventFilter.Interpreted> getFilters() {
				return Collections.unmodifiableList(theFilters);
			}

			@Override
			public void update() throws ExpressoInterpretationException {
				super.update();
				CollectionUtils.synchronize(theFilters, getDefinition().getFilters(), (i, d) -> i.getDefinition() == d)//
				.<ExpressoInterpretationException> simpleE(f -> f.interpret(this))//
				.onLeft(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().update())//
				.onCommonX(el -> el.getLeftValue().update())//
				.addLast();
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

	List<EventFilter> getFilters();

	default boolean testFilter() {
		for (EventFilter filter : getFilters())
			if (!filter.filterPasses())
				return false;
		return true;
	}

	ObservableAction<?> getAction();

	public abstract class Abstract extends ExElement.Abstract implements QuickEventListener {
		private final List<EventFilter> theFilters;
		private ObservableAction<?> theAction;
		private final SettableValue<SettableValue<Boolean>> isAltPressed;
		private final SettableValue<SettableValue<Boolean>> isCtrlPressed;
		private final SettableValue<SettableValue<Boolean>> isShiftPressed;

		protected Abstract(QuickEventListener.Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theFilters = new ArrayList<>();
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
		public List<EventFilter> getFilters() {
			return Collections.unmodifiableList(theFilters);
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
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			ExElement.satisfyContextValue("altPressed", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isAltPressed), myModels, this);
			ExElement.satisfyContextValue("ctrlPressed", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isCtrlPressed), myModels, this);
			ExElement.satisfyContextValue("shiftPressed", ModelTypes.Value.BOOLEAN, SettableValue.flatten(isShiftPressed), myModels,
				this);
			QuickEventListener.Interpreted<?> myInterpreted = (QuickEventListener.Interpreted<?>) interpreted;
			CollectionUtils.synchronize(theFilters, myInterpreted.getFilters(), (f, i) -> f.getIdentity() == i.getIdentity())
			.<ModelInstantiationException> simpleE(f -> f.create(this))//
			.onRightX(el -> el.getLeftValue().update(el.getRightValue(), myModels))//
			.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), myModels))//
			.adjust();
			theAction = myInterpreted.getAction().get(myModels);
		}
	}

	public class EventFilter extends ExElement.Abstract {
		private static final ElementTypeTraceability<EventFilter, Interpreted, Def> TRACEABILITY = ElementTypeTraceability.<QuickEventListener.EventFilter, Interpreted, Def> build(
			QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "filter")//
			.reflectMethods(Def.class, Interpreted.class, EventFilter.class)//
			.build();

		public static class Def extends ExElement.Def.Abstract<EventFilter> {
			private CompiledExpression theCondition;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@QonfigAttributeGetter
			public CompiledExpression getCondition() {
				return theCondition;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.update(session);
				theCondition = session.getValueExpression();
			}

			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends ExElement.Interpreted.Abstract<EventFilter> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theCondition;

			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getCondition() {
				return theCondition;
			}

			@Override
			public void update() throws ExpressoInterpretationException {
				super.update();
				theCondition = getDefinition().getCondition().evaluate(ModelTypes.Value.BOOLEAN).interpret();
			}

			public EventFilter create(ExElement parent) {
				return new EventFilter(this, parent);
			}
		}

		private SettableValue<Boolean> theCondition;

		public EventFilter(Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
		}

		public SettableValue<Boolean> getCondition() {
			return theCondition;
		}

		public boolean filterPasses() {
			return Boolean.TRUE.equals(theCondition.get());
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			Interpreted myInterpreted = (Interpreted) interpreted;
			theCondition = myInterpreted.getCondition().get(myModels);
		}
	}
}
