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
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A listener for some kind of user event on a Quick widget */
public interface QuickEventListener extends ExElement {
	/** The XML name of this type */
	public static final String EVENT_LISTENER = "event-listener";

	/**
	 * The definition of a {@link QuickEventListener}
	 *
	 * @param <L> The sub-type of listener to create
	 */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = EVENT_LISTENER,
		interpretation = Interpreted.class,
		instance = QuickEventListener.class)
	public interface Def<L extends QuickEventListener> extends ExElement.Def<L> {
		/** @return The list of filters that must be passed by an event if this listener's action is to be performed on it */
		@QonfigChildGetter("filter")
		List<EventFilter.Def> getFilters();

		/** @return The action to perform when the event occurs */
		@QonfigAttributeGetter
		CompiledExpression getAction();

		/** @return The model ID of the boolean value that is true if the user is pressing the ALT key when the event occurs */
		ModelComponentId getAltPressedValue();

		/** @return The model ID of the boolean value that is true if the user is pressing the CTRL key when the event occurs */
		ModelComponentId getCtrlPressedValue();

		/** @return The model ID of the boolean value that is true if the user is pressing the SHIFT key when the event occurs */
		ModelComponentId getShiftPressedValue();

		/**
		 * @param parent The parent for the interpreted listener
		 * @return The interpreted listener
		 */
		Interpreted<? extends L> interpret(ExElement.Interpreted<?> parent);

		/**
		 * Abstract {@link QuickEventListener} definition implementation
		 *
		 * @param <L> The sub-type of listener to create
		 */
		public abstract class Abstract<L extends QuickEventListener> extends ExElement.Def.Abstract<L> implements Def<L> {
			private final List<EventFilter.Def> theFilters;
			private CompiledExpression theAction;
			private ModelComponentId theAltPressedValue;
			private ModelComponentId theCtrlPressedValue;
			private ModelComponentId theShiftPressedValue;

			/**
			 * @param parent The parent of this listener
			 * @param type The Qonfig type of this listener
			 */
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
				super.doUpdate(session);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theAltPressedValue = elModels.getElementValueModelId("altPressed");
				theCtrlPressedValue = elModels.getElementValueModelId("ctrlPressed");
				theShiftPressedValue = elModels.getElementValueModelId("shiftPressed");
				syncChildren(EventFilter.Def.class, theFilters, session.forChildren("filter"));
				theAction = getValueExpression(session);
				if (theAction.getExpression() == ObservableExpression.EMPTY)
					throw new QonfigInterpretationException("No action for event listener", session.getElement().getPositionInFile(), 0);
			}
		}
	}

	/**
	 * The interpretation of a {@link QuickEventListener}
	 *
	 * @param <L> The sub-type of listener to create
	 */
	public interface Interpreted<L extends QuickEventListener> extends ExElement.Interpreted<L> {
		@Override
		Def<? super L> getDefinition();

		/** @return The list of filters that must be passed by an event if this listener's action is to be performed on it */
		List<EventFilter.Interpreted> getFilters();

		/** @return The action to perform when the event occurs */
		InterpretedValueSynth<ObservableAction, ObservableAction> getAction();

		/**
		 * Initializes or updates this listener
		 *
		 * @param env The expresso environment to interpret expressions
		 * @throws ExpressoInterpretationException If this listener could not be interpreted
		 */
		void updateListener(InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		/** @return The listener instance */
		L create();

		/**
		 * Abstract {@link QuickEventListener} interpretation implementation
		 *
		 * @param <L> The sub-type of listener to create
		 */
		public abstract class Abstract<L extends QuickEventListener> extends ExElement.Interpreted.Abstract<L> implements Interpreted<L> {
			private final List<EventFilter.Interpreted> theFilters;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element of this listener
			 */
			protected Abstract(Def<? super L> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theFilters = new ArrayList<>();
			}

			@Override
			public Def<? super L> getDefinition() {
				return (Def<? super L>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<ObservableAction, ObservableAction> getAction() {
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
				syncChildren(getDefinition().getFilters(), theFilters, def -> def.interpret(this), EventFilter.Interpreted::updateFilter);
				theAction = interpret(getDefinition().getAction(), ModelTypes.Action.instance());
			}
		}
	}

	/** Context for an event listener */
	public interface ListenerContext {
		/** @return Whether the user is currently pressing the ALT key */
		SettableValue<Boolean> isAltPressed();

		/** @return Whether the user is currently pressing the CTRL key */
		SettableValue<Boolean> isCtrlPressed();

		/** @return Whether the user is currently pressing the SHIFT key */
		SettableValue<Boolean> isShiftPressed();

		/** Default {@link ListenerContext} implementation */
		public class Default implements ListenerContext {
			private final SettableValue<Boolean> isAltPressed;
			private final SettableValue<Boolean> isCtrlPressed;
			private final SettableValue<Boolean> isShiftPressed;

			/**
			 * @param altPressed Whether the user is currently pressing the ALT key
			 * @param ctrlPressed Whether the user is currently pressing the CTRL key
			 * @param shiftPressed Whether the user is currently pressing the SHIFT key
			 */
			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed) {
				isAltPressed = altPressed;
				isCtrlPressed = ctrlPressed;
				isShiftPressed = shiftPressed;
			}

			/** Creates context with default value containers */
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

	/**
	 * Sets the event listener context to populate this listener's models with context-specific values
	 *
	 * @param ctx The listener context from the Quick implementation
	 */
	void setListenerContext(ListenerContext ctx);

	/** @return The list of filters that must be passed by an event if this listener's action is to be performed on it */
	List<EventFilter> getFilters();

	/** @return Runs all the tests in this listener's {@link #getFilters() filters} on the current model values */
	default boolean testFilter() {
		for (EventFilter filter : getFilters())
			if (!filter.filterPasses())
				return false;
		return true;
	}

	/** @return The action to perform when the event occurs */
	ObservableAction getAction();

	@Override
	QuickEventListener copy(ExElement parent);

	/** Abstract {@link QuickEventListener} implementation */
	public abstract class Abstract extends ExElement.Abstract implements QuickEventListener {
		private List<EventFilter> theFilters;

		private ModelValueInstantiator<? extends ObservableAction> theActionInstantiator;
		private ObservableAction theAction;

		private ModelComponentId theAltPressedValue;
		private ModelComponentId theCtrlPressedValue;
		private ModelComponentId theShiftPressedValue;

		private SettableValue<SettableValue<Boolean>> isAltPressed;
		private SettableValue<SettableValue<Boolean>> isCtrlPressed;
		private SettableValue<SettableValue<Boolean>> isShiftPressed;

		/** @param id The element identifier for this listener */
		protected Abstract(Object id) {
			super(id);
			theFilters = new ArrayList<>();
			isAltPressed = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			isCtrlPressed = SettableValue.build(isAltPressed.getType()).build();
			isShiftPressed = SettableValue.build(isAltPressed.getType()).build();
		}

		@Override
		public void setListenerContext(ListenerContext ctx) {
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
		public ObservableAction getAction() {
			return ObservableAction.of(cause -> {
				if (theAction != null) {
					try {
						theAction.act(cause);
					} catch (RuntimeException | Error e) {
						reporting().error(e.toString(), e);
					}
				}
			});
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			QuickEventListener.Interpreted<?> myInterpreted = (QuickEventListener.Interpreted<?>) interpreted;
			theAltPressedValue = myInterpreted.getDefinition().getAltPressedValue();
			theCtrlPressedValue = myInterpreted.getDefinition().getCtrlPressedValue();
			theShiftPressedValue = myInterpreted.getDefinition().getShiftPressedValue();

			theActionInstantiator = myInterpreted.getAction().instantiate();

			CollectionUtils.synchronize(theFilters, myInterpreted.getFilters(), (f, i) -> f.getIdentity() == i.getIdentity())
				.<ModelInstantiationException> simpleX(f -> f.create(this))//
				.onRightX(el -> el.getLeftValue().update(el.getRightValue(), this))//
				.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), this))//
			.adjust();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
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

	/** A test that must be passed by an event if a listener's action is to be performed on it */
	public class EventFilter extends ExElement.Abstract {
		/** The definition of a {@link EventFilter} */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = "event-filter",
			interpretation = Interpreted.class,
			instance = EventFilter.class)
		public static class Def extends ExElement.Def.Abstract<EventFilter> {
			private CompiledExpression theCondition;

			/**
			 * @param parent The parent element for this filter
			 * @param type The Qonfig type of this filter
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/** @return This filter's condition */
			@QonfigAttributeGetter
			public CompiledExpression getCondition() {
				return theCondition;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theCondition = getValueExpression(session);
			}

			/**
			 * @param parent The parent element for the interpreted filter
			 * @return The interpreted filter
			 */
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** The interpretation of a {@link EventFilter} */
		public static class Interpreted extends ExElement.Interpreted.Abstract<EventFilter> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theCondition;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this filter
			 */
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			/** @return This filter's condition */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getCondition() {
				return theCondition;
			}

			/**
			 * Initializes or updates this filter
			 *
			 * @param env The expresso environment to interpret expressions
			 * @throws ExpressoInterpretationException If this filter could not be interpreted
			 */
			public void updateFilter(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theCondition = interpret(getDefinition().getCondition(), ModelTypes.Value.BOOLEAN);
			}

			/**
			 * @param parent The parent element for the filter
			 * @return The filter instance
			 */
			public EventFilter create(ExElement parent) {
				return new EventFilter(parent);
			}
		}

		private ModelValueInstantiator<SettableValue<Boolean>> theConditionInstantiator;
		private SettableValue<Boolean> theCondition;

		/** @param parent The owner of this filter (typically a {@link QuickEventListener}) */
		public EventFilter(ExElement parent) {
			super(parent);
		}

		/** @return This filter's condition */
		public SettableValue<Boolean> getCondition() {
			return theCondition;
		}

		/** @return Whether the filter passes on the event whose data is installed in this filter's model */
		public boolean filterPasses() {
			return Boolean.TRUE.equals(theCondition.get());
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted myInterpreted = (Interpreted) interpreted;
			theConditionInstantiator = myInterpreted.getCondition().instantiate();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
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
