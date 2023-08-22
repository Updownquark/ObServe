package org.observe.quick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickEventListener extends ExElement {
	public static final String EVENT_LISTENER = "event-listener";
	public static final SingleTypeTraceability<QuickEventListener, Interpreted<?>, Def<?>> LISTENER_TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "event-listener", Def.class,
			Interpreted.class, QuickEventListener.class);

	public interface Def<L extends QuickEventListener> extends ExElement.Def<L> {
		@QonfigChildGetter("filter")
		List<EventFilter.Def> getFilters();

		@QonfigAttributeGetter
		CompiledExpression getAction();

		ModelComponentId getAltPressedValue();

		ModelComponentId getCtrlPressedValue();

		ModelComponentId getShiftPressedValue();

		Interpreted<? extends L> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<L extends QuickEventListener> extends ExElement.Def.Abstract<L> implements Def<L> {
			private final List<EventFilter.Def> theFilters;
			private CompiledExpression theAction;
			private ModelComponentId theAltPressedValue;
			private ModelComponentId theCtrlPressedValue;
			private ModelComponentId theShiftPressedValue;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
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
			public ModelComponentId getAltPressedValue() {
				return theAltPressedValue;
			}

			@Override
			public ModelComponentId getCtrlPressedValue() {
				return theCtrlPressedValue;
			}

			@Override
			public ModelComponentId getShiftPressedValue() {
				return theShiftPressedValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(LISTENER_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theAltPressedValue = elModels.getElementValueModelId("altPressed");
				theCtrlPressedValue = elModels.getElementValueModelId("ctrlPressed");
				theShiftPressedValue = elModels.getElementValueModelId("shiftPressed");
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

		void updateListener(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		L create();

		public abstract class Abstract<L extends QuickEventListener> extends ExElement.Interpreted.Abstract<L> implements Interpreted<L> {
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
			public void updateListener(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				CollectionUtils.synchronize(theFilters, getDefinition().getFilters(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.<ExpressoInterpretationException> simpleE(f -> f.interpret(this))//
				.onLeft(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().updateFilter(getExpressoEnv()))//
				.onCommonX(el -> el.getLeftValue().updateFilter(getExpressoEnv()))//
				.addLast();
				theAction = getDefinition().getAction().interpret(ModelTypes.Action.any(), getExpressoEnv());
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

	@Override
	QuickEventListener copy(ExElement parent);

	public abstract class Abstract extends ExElement.Abstract implements QuickEventListener {
		private List<EventFilter> theFilters;

		private ModelValueInstantiator<? extends ObservableAction<?>> theActionInstantiator;
		private ObservableAction<?> theAction;

		private ModelComponentId theAltPressedValue;
		private ModelComponentId theCtrlPressedValue;
		private ModelComponentId theShiftPressedValue;

		private SettableValue<SettableValue<Boolean>> isAltPressed;
		private SettableValue<SettableValue<Boolean>> isCtrlPressed;
		private SettableValue<SettableValue<Boolean>> isShiftPressed;

		protected Abstract(Object id) {
			super(id);
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
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			QuickEventListener.Interpreted<?> myInterpreted = (QuickEventListener.Interpreted<?>) interpreted;
			theAltPressedValue = myInterpreted.getDefinition().getAltPressedValue();
			theCtrlPressedValue = myInterpreted.getDefinition().getCtrlPressedValue();
			theShiftPressedValue = myInterpreted.getDefinition().getShiftPressedValue();

			theActionInstantiator = myInterpreted.getAction().instantiate();

			CollectionUtils.synchronize(theFilters, myInterpreted.getFilters(), (f, i) -> f.getIdentity() == i.getIdentity())
			.simple(f -> f.create(this))//
			.onRight(el -> el.getLeftValue().update(el.getRightValue(), this))//
			.onCommon(el -> el.getLeftValue().update(el.getRightValue(), this))//
			.adjust();
		}

		@Override
		public void instantiated() {
			super.instantiated();

			theActionInstantiator.instantiate();

			for (EventFilter filter : theFilters)
				filter.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theAltPressedValue, myModels, SettableValue.flatten(isAltPressed));
			ExFlexibleElementModelAddOn.satisfyElementValue(theCtrlPressedValue, myModels, SettableValue.flatten(isCtrlPressed));
			ExFlexibleElementModelAddOn.satisfyElementValue(theShiftPressedValue, myModels, SettableValue.flatten(isShiftPressed));

			for (EventFilter filter : theFilters)
				filter.instantiate(myModels);

			theAction = theActionInstantiator.get(myModels);
		}

		@Override
		public QuickEventListener.Abstract copy(ExElement parent) {
			QuickEventListener.Abstract copy = (QuickEventListener.Abstract) super.copy(parent);

			copy.theFilters = new ArrayList<>();
			for (EventFilter filter : theFilters)
				copy.theFilters.add(filter.copy(copy));
			copy.isAltPressed = SettableValue.build(isAltPressed.getType()).build();
			copy.isCtrlPressed = SettableValue.build(isAltPressed.getType()).build();
			copy.isShiftPressed = SettableValue.build(isAltPressed.getType()).build();

			return copy;
		}
	}

	public class EventFilter extends ExElement.Abstract {
		private static final SingleTypeTraceability<EventFilter, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "filter", Def.class, Interpreted.class,
				EventFilter.class);

		public static class Def extends ExElement.Def.Abstract<EventFilter> {
			private CompiledExpression theCondition;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@QonfigAttributeGetter
			public CompiledExpression getCondition() {
				return theCondition;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session);
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

			public void updateFilter(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theCondition = getDefinition().getCondition().interpret(ModelTypes.Value.BOOLEAN, getExpressoEnv());
			}

			public EventFilter create(ExElement parent) {
				return new EventFilter(parent);
			}
		}

		private ModelValueInstantiator<SettableValue<Boolean>> theConditionInstantiator;
		private SettableValue<Boolean> theCondition;

		public EventFilter(ExElement parent) {
			super(parent);
		}

		public SettableValue<Boolean> getCondition() {
			return theCondition;
		}

		public boolean filterPasses() {
			return Boolean.TRUE.equals(theCondition.get());
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			Interpreted myInterpreted = (Interpreted) interpreted;
			theConditionInstantiator = myInterpreted.getCondition().instantiate();
		}

		@Override
		public void instantiated() {
			super.instantiated();
			theConditionInstantiator.instantiate();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			theCondition = theConditionInstantiator.get(myModels);
		}

		@Override
		public EventFilter copy(ExElement parent) {
			return (EventFilter) super.copy(parent);
		}
	}
}
