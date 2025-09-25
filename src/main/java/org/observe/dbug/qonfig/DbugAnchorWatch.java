package org.observe.dbug.qonfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.dbug.Dbug;
import org.observe.dbug.DbugAnchor;
import org.observe.dbug.DbugAnchorType;
import org.observe.dbug.DbugFieldType;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.SyntheticField;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class DbugAnchorWatch<A> extends ExElement.Abstract {
	public static final String DBUG_ANCHOR = "dbug-anchor";

	@ExElementTraceable(toolkit = ExpressoDbugV0_1.DBUG,
		qonfigType = DBUG_ANCHOR,
		interpretation = Interpreted.class,
		instance = DbugAnchorWatch.class)
	public static class Def extends ExElement.Def.Abstract<DbugAnchorWatch<?>> {
		private VariableType theAnchorType;
		private CompiledExpression theIf;
		private String theTag;
		private Pattern theTagMatch;
		private ModelComponentId theAs;
		private final List<DbugAction.Def<?>> theActions;
		private final List<DbugEventWatch.Def> theEvents;
		private final List<DbugAnchorWatch.Def> theSubWatches;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theActions = new ArrayList<>();
			theEvents = new ArrayList<>();
			theSubWatches = new ArrayList<>();
		}

		@QonfigAttributeGetter("type")
		public VariableType getAnchorType() {
			return theAnchorType;
		}

		@QonfigAttributeGetter("if")
		public CompiledExpression getIf() {
			return theIf;
		}

		@QonfigAttributeGetter("tag")
		public String getTag() {
			return theTag;
		}

		@QonfigAttributeGetter("tag-matches")
		public Pattern getTagMatch() {
			return theTagMatch;
		}

		@QonfigAttributeGetter("as")
		public ModelComponentId getAs() {
			return theAs;
		}

		@QonfigChildGetter("action")
		public List<DbugAction.Def<?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@QonfigChildGetter("event")
		public List<DbugEventWatch.Def> getEvents() {
			return Collections.unmodifiableList(theEvents);
		}

		@QonfigChildGetter("sub-watch")
		public List<DbugAnchorWatch.Def> getSubWatches() {
			return Collections.unmodifiableList(theSubWatches);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			QonfigElement.AttributeValue type = session.attributes().get("type").get();
			theAnchorType = type.text.isEmpty() ? null
				: VariableType.parseType(LocatedPositionedContent.of(type.fileLocation, type.position));
			theIf = getAttributeExpression("if", session);
			theTag = session.getAttributeText("tag");
			String tagMatch = session.getAttributeText("tag-matches");
			try {
				theTagMatch = theTagMatch == null ? null : Pattern.compile(tagMatch);
			} catch (PatternSyntaxException e) {
				session.reporting().at(session.attributes().get("tag-matches").getContent()).at(e.getIndex())
				.error("Bad pattern: " + e.getMessage(), e);
			}

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theAs = elModels.getElementValueModelId(session.getAttributeText("as"));
			elModels.<Interpreted<?>, SettableValue<?>> satisfyElementSingleValueType(theAs, ModelTypes.Value, Interpreted::getAnchorType);

			syncChildren(DbugAction.Def.class, theActions, session.forChildren("action"));
			syncChildren(DbugEventWatch.Def.class, theEvents, session.forChildren("event"));
			syncChildren(DbugAnchorWatch.Def.class, theSubWatches, session.forChildren("sub-watch"));
		}

		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted<A> extends ExElement.Interpreted.Abstract<DbugAnchorWatch<A>> {
		private TypeToken<A> theAnchorType;
		private DbugAnchorType<? super A> theDbugAnchorType;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theIf;
		private final List<DbugAction.Interpreted<?>> theActions;
		private final List<DbugEventWatch.Interpreted> theEvents;
		private final List<DbugAnchorWatch.Interpreted<?>> theSubWatches;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theActions = new ArrayList<>();
			theEvents = new ArrayList<>();
			theSubWatches = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public TypeToken<A> getAnchorType() throws ExpressoInterpretationException {
			if (theAnchorType == null) {
				SubAnchorWatch.Interpreted<A> saw = getAddOn(SubAnchorWatch.Interpreted.class);
				if (saw != null)
					theAnchorType = saw.getAnchorType();
				else if (getDefinition().getAnchorType() != null)
					theAnchorType = (TypeToken<A>) interpretType(getDefinition().getAnchorType());
				else {
					reporting().error("type missing");
					theAnchorType = (TypeToken<A>) TypeTokens.get().OBJECT;
				}
			}
			return theAnchorType;
		}

		public DbugAnchorType<? super A> getDbugAnchorType() {
			return theDbugAnchorType;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getIf() {
			return theIf;
		}

		public List<DbugAction.Interpreted<?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		public List<DbugEventWatch.Interpreted> getEvents() {
			return Collections.unmodifiableList(theEvents);
		}

		public List<DbugAnchorWatch.Interpreted<?>> getSubWatches() {
			return Collections.unmodifiableList(theSubWatches);
		}

		public void updateWatch() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			TypeToken<A> type = getAnchorType();
			theDbugAnchorType = Dbug.common().getAnchor(TypeTokens.getRawType(type), false);
			if (theDbugAnchorType != null) {
				for (DbugFieldType<? super A, ?> field : theDbugAnchorType.getFields().values()) {
					addSyntheticField(field);
				}
			} else if (getAddOn(SubAnchorWatch.Interpreted.class) == null)
				reporting().warn("No Dbug anchor type found for " + type + ". This element is useless.");
			theIf = interpret(getDefinition().getIf(), ModelTypes.Value.BOOLEAN);
			syncChildren(getDefinition().getActions(), theActions, a -> a.interpret(this), a -> a.updateAction());
			syncChildren(getDefinition().getEvents(), theEvents, a -> a.interpret(this), a -> a.updateWatch());
			syncChildren(getDefinition().getSubWatches(), theSubWatches, a -> a.interpret(this), a -> a.updateWatch());
		}

		private <A2, F> void addSyntheticField(DbugFieldType<A2, F> field) {
			getDefaultEnv().withSyntheticField(field.getAnchor().getType(), field.getName(), new SyntheticField.Def<A2, F>() {
				@Override
				public <E2 extends A2> SyntheticField<E2, ? extends F> get(TypeToken<E2> entityType) {
					return new SyntheticField<E2, F>() {
						@Override
						public TypeToken<F> getType() {
							return field.getType();
						}

						@Override
						public SettableValue<F> get(SettableValue<? extends E2> entity) {
							return SettableValue.asSettable(
								ObservableValue.flatten(entity.map(e -> e == null ? null
									: (ObservableValue<F>) field.getAnchor().getInstance(e).observeField(field.getName()))),
								__ -> "Unsettable");
						}
					};
				}
			});
		}

		public DbugAnchorWatch<A> create() {
			return new DbugAnchorWatch<>(getIdentity());
		}
	}

	private Class<A> theAnchorType;
	private DbugAnchorType<? super A> theDbugAnchorType;
	private ModelValueInstantiator<SettableValue<Boolean>> theIfInstantiator;
	private String theTag;
	private Pattern theTagMatch;
	private ModelComponentId theAs;

	private SettableValue<A> theAsValue;
	private SettableValue<SettableValue<Boolean>> theIf;
	private List<DbugAction> theActions;
	private List<DbugEventWatch> theEvents;
	private List<DbugAnchorWatch<?>> theSubWatches;

	DbugAnchorWatch(Object id) {
		super(id);
		theAsValue = SettableValue.create();
		theIf = SettableValue.create();
		theActions = new ArrayList<>();
		theEvents = new ArrayList<>();
		theSubWatches = new ArrayList<>();
		persistModels();
	}

	public Class<A> getAnchorType() {
		return theAnchorType;
	}

	public String getTag() {
		return theTag;
	}

	public Pattern getTagMatch() {
		return theTagMatch;
	}

	public ModelComponentId getAs() {
		return theAs;
	}

	public SettableValue<Boolean> getIf() {
		return SettableValue.flatten(theIf, () -> true);
	}

	public List<DbugAction> getActions() {
		return Collections.unmodifiableList(theActions);
	}

	public List<DbugEventWatch> getEvents() {
		return Collections.unmodifiableList(theEvents);
	}

	public List<DbugAnchorWatch<?>> getSubWatches() {
		return Collections.unmodifiableList(theSubWatches);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<A> myInterpreted = (Interpreted<A>) interpreted;
		try {
			theAnchorType = TypeTokens.getRawType(myInterpreted.getAnchorType());
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("What?", e);
		}
		theDbugAnchorType = myInterpreted.getDbugAnchorType();
		theIfInstantiator = myInterpreted.getIf() == null ? null : myInterpreted.getIf().instantiate();
		theTag = myInterpreted.getDefinition().getTag();
		theTagMatch = myInterpreted.getDefinition().getTagMatch();
		theAs = myInterpreted.getDefinition().getAs();

		CollectionUtils.synchronize(theActions, myInterpreted.getActions(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();

		CollectionUtils.synchronize(theEvents, myInterpreted.getEvents(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();

		CollectionUtils
		.synchronize(theSubWatches, myInterpreted.getSubWatches(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
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
		for (DbugEventWatch event : theEvents)
			event.instantiated();
		for (DbugAnchorWatch<?> subWatch : theSubWatches)
			subWatch.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);
		ExFlexibleElementModelAddOn.satisfyElementValue(theAs, myModels, theAsValue);
		theIf.set(theIfInstantiator == null ? null : theIfInstantiator.get(myModels), null);
		for (DbugAction action : theActions)
			action.instantiate(myModels);
		return myModels;
	}

	public void watch(DbugAnchor<? super A> anchor, Observable<?> until) {
		Transaction typeActive = theDbugAnchorType.activate();
		Transaction instActive = anchor.activate();
		until.take(1).act(__ -> {
			instActive.close();
			typeActive.close();
		});
		ModelSetInstance instanceModels;
		ObservableValue<Boolean> ifV;
		try {
			instanceModels = getUpdatingModels().copy(until).build();
			ExFlexibleElementModelAddOn.satisfyElementValue(theAs, instanceModels,
				SettableValue.of((A) anchor.getInstance(), "Unmodifiable"));
			ifV = theIfInstantiator == null ? ObservableValue.of(true) : theIfInstantiator.get(instanceModels);
		} catch (ModelInstantiationException e) {
			reporting().error("Could not instantiate models for anchor " + anchor.getInstance(), e);
			return;
		}
		Observable<?> ifTerm = Observable.or(until, ifV.noInitChanges().filter(evt -> !evt.getNewValue()));
		List<DbugAction> actions = new ArrayList<>(theActions.size());
		for (DbugAction action : theActions) {
			DbugAction copy = action.copy(this);
			try {
				copy.instantiate(instanceModels);
				actions.add(copy);
			} catch (ModelInstantiationException e) {
				copy.reporting().error("Could not instantiate action for " + anchor.getInstance(), e);
			}
		}
		List<DbugEventWatch> events = new ArrayList<>(theEvents.size());
		for (DbugEventWatch event : theEvents) {
			DbugEventWatch copy = event.copy(this);
			try {
				copy.instantiate(instanceModels);
				events.add(copy);
			} catch (ModelInstantiationException e) {
				copy.reporting().error("Could not instantiate event watch for " + anchor.getInstance(), e);
			}
		}
		List<DbugAnchorWatch<?>> subWatches = new ArrayList<>(theSubWatches.size());
		for (DbugAnchorWatch<?> watch : theSubWatches) {
			DbugAnchorWatch<?> copy = watch.copy(this);
			try {
				copy.instantiate(instanceModels);
				subWatches.add(copy);
			} catch (ModelInstantiationException e) {
				copy.reporting().error("Could not instantiate sub-watch for " + anchor.getInstance(), e);
			}
		}
		ifV.changes().takeUntil(until).act(evt -> {
			if (!evt.getNewValue() || evt.isUpdate())
				return;
			for (DbugAction action : actions)
				action.execute(evt);
			for (DbugEventWatch event : events)
				event.watchEvent(anchor, ifTerm);
			for (DbugAnchorWatch<?> watch : subWatches)
				watch.watchAsSub(ifTerm);
		});
	}

	public void watchAsSub(Observable<?> until) {
		Transaction typeActive = theDbugAnchorType.activate();
		SimpleObservable<java.lang.Void> valueUntil = new SimpleObservable<>();
		until.take(1).act(__ -> {
			valueUntil.onNext(null);
			typeActive.close();
		});
		((SettableValue<A>) getAddOn(SubAnchorWatch.class).getWatch()).changes().takeUntil(until).act(evt -> {
			if (evt.isUpdate())
				return;
			valueUntil.onNext(null);
			if (evt.getNewValue() == null)
				return;
			DbugAnchor<? super A> anchor = theDbugAnchorType.getInstance(evt.getNewValue());
			if (anchor == null) {
				reporting().info("Dbug is not implemented for " + evt.getNewValue().getClass() + " instance");
				return;
			}
			watch(anchor, valueUntil);
		});
	}

	@Override
	public DbugAnchorWatch<A> copy(ExElement parent) {
		DbugAnchorWatch<A> copy = (DbugAnchorWatch<A>) super.copy(parent);

		copy.theAsValue = SettableValue.create();
		copy.theIf = SettableValue.create();

		copy.theActions = new ArrayList<>();
		for (DbugAction action : theActions)
			copy.theActions.add(action.copy(copy));

		copy.theEvents = new ArrayList<>();
		for (DbugEventWatch event : theEvents)
			copy.theEvents.add(event.copy(copy));

		copy.theSubWatches = new ArrayList<>();
		for (DbugAnchorWatch<?> subWatch : theSubWatches)
			copy.theSubWatches.add(subWatch.copy(copy));

		return copy;
	}

	@Override
	public void destroy() {
		super.destroy();
		for (DbugAction action : theActions)
			action.destroy();
		for (DbugEventWatch event : theEvents)
			event.destroy();
		for (DbugAnchorWatch<?> subWatch : theSubWatches)
			subWatch.destroy();
	}
}
