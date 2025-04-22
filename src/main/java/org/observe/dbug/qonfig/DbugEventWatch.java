package org.observe.dbug.qonfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.dbug.DbugAnchor;
import org.observe.dbug.DbugEvent;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
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
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class DbugEventWatch extends ExElement.Abstract {
	public static final String DBUG_EVENT = "dbug-event";

	@ExElementTraceable(toolkit = ExpressoDbugV0_1.DBUG,
		qonfigType = DBUG_EVENT,
		interpretation = Interpreted.class,
		instance = DbugEventWatch.class)
	public static class Def extends ExElement.Def.Abstract<DbugEventWatch> {
		private String theName;
		private ModelComponentId theEventAs;
		private CompiledExpression theIf;
		private final List<DbugAction.Def<?>> theActions;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theActions = new ArrayList<>();
		}

		@QonfigAttributeGetter("name")
		public String getName() {
			return theName;
		}

		@QonfigAttributeGetter("event-as")
		public ModelComponentId getEventAs() {
			return theEventAs;
		}

		@QonfigAttributeGetter("if")
		public CompiledExpression getIf() {
			return theIf;
		}

		@QonfigChildGetter("action")
		public List<DbugAction.Def<?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theName = session.getAttributeText("name");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theEventAs = elModels.getElementValueModelId(session.getAttributeText("event-as"));
			elModels.satisfyElementValueType(theEventAs, ModelTypes.Value.forType(DbugEvent.class));
			theIf = getAttributeExpression("if", session);
			syncChildren(DbugAction.Def.class, theActions, session.forChildren("action"));
		}

		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<DbugEventWatch> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theIf;
		private final List<DbugAction.Interpreted<?>> theActions;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theActions = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getIf() {
			return theIf;
		}

		public List<DbugAction.Interpreted<?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		public void updateWatch() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			// TODO Don't know how I'll implement access to the event fields yet
			theIf = interpret(getDefinition().getIf(), ModelTypes.Value.BOOLEAN);
			syncChildren(getDefinition().getActions(), theActions, a -> a.interpret(this), a -> a.updateAction());
		}

		public DbugEventWatch create() {
			return new DbugEventWatch(getIdentity());
		}
	}

	private String theName;
	private ModelComponentId theEventAs;
	private SettableValue<DbugEvent<?>> theEvent;
	private ModelValueInstantiator<SettableValue<Boolean>> theIfInstantiator;

	private SettableValue<SettableValue<Boolean>> theIf;
	private List<DbugAction> theActions;

	DbugEventWatch(Object id) {
		super(id);
		theEvent = SettableValue.create();
		theIf = SettableValue.create();
		theActions = new ArrayList<>();
	}

	public SettableValue<Boolean> getIf() {
		return SettableValue.flatten(theIf, () -> true);
	}

	public List<DbugAction> getActions() {
		return Collections.unmodifiableList(theActions);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theName = myInterpreted.getDefinition().getName();
		theEventAs = myInterpreted.getDefinition().getEventAs();
		theIfInstantiator = myInterpreted.getIf() == null ? null : myInterpreted.getIf().instantiate();
		CollectionUtils.synchronize(theActions, myInterpreted.getActions(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theIfInstantiator != null)
			theIfInstantiator.instantiate();
		for (DbugAction action : theActions)
			action.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theEventAs, myModels, theEvent);
		theIf.set(theIfInstantiator == null ? null : theIfInstantiator.get(myModels), null);
		for (DbugAction action : theActions)
			action.instantiate(myModels);
		return myModels;
	}

	public void watchEvent(DbugAnchor<?> anchor, Observable<?> until) {
		anchor.listenFor(theName, evt -> {
			theEvent.set(evt, evt);
			SettableValue<Boolean> iff = theIf.get();
			if (iff != null && !iff.get())
				return;
			for (DbugAction action : theActions)
				action.execute(evt);
		});
	}

	@Override
	public DbugEventWatch copy(ExElement parent) {
		DbugEventWatch copy = (DbugEventWatch) super.copy(parent);
		copy.theEvent = SettableValue.create();
		copy.theIf = SettableValue.create();
		copy.theActions = new ArrayList<>();
		for (DbugAction action : theActions)
			copy.theActions.add(action.copy(copy));
		return copy;
	}

	@Override
	public void destroy() {
		super.destroy();
		for (DbugAction action : theActions)
			action.destroy();
	}
}
