package org.observe.dbug.qonfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.dbug.Dbug;
import org.observe.dbug.DbugAnchor;
import org.observe.dbug.DbugAnchorType;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.debug.Debug;
import org.qommons.debug.Debug.DebugData;
import org.qommons.ex.ExceptionHandler;

import com.google.common.reflect.TypeToken;

public class DbugModelValue<A> extends ExAddOn.Abstract<ExElement> {
	public static final String DBUG_VALUE = "dbug-value";

	@ExElementTraceable(toolkit = ExpressoDbugV0_1.DBUG,
		qonfigType = DBUG_VALUE,
		interpretation = Interpreted.class,
		instance = DbugModelValue.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, DbugModelValue<?>> {
		private final List<DbugTag.Def> theTags;
		private final List<DbugAnchorWatch.Def> theWatches;

		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
			theTags = new ArrayList<>();
			theWatches = new ArrayList<>();
		}

		@QonfigChildGetter("tag")
		public List<DbugTag.Def> getTags() {
			return Collections.unmodifiableList(theTags);
		}

		@QonfigChildGetter("watch")
		public List<DbugAnchorWatch.Def> getWatches() {
			return Collections.unmodifiableList(theWatches);
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			element.syncChildren(DbugTag.Def.class, theTags, session.forChildren("tag"));
			element.syncChildren(DbugAnchorWatch.Def.class, theWatches, session.forChildren("watch"));
		}

		@Override
		public <E2 extends ExElement> Interpreted<?> interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted<>(this, element);
		}
	}

	public static class Interpreted<A> extends ExAddOn.Interpreted.Abstract<ExElement, DbugModelValue<A>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<A>> theValue;
		private TypeToken<A> theAnchorType;
		private DbugAnchorType<? super A> theDbugAnchorType;
		private final List<DbugTag.Interpreted> theTags;
		private final List<DbugAnchorWatch.Interpreted<A>> theWatches;

		Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
			theTags = new ArrayList<>();
			theWatches = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<A>> getValue() throws ExpressoInterpretationException {
			if (theValue == null && (!getDefinition().getTags().isEmpty() || !getDefinition().getWatches().isEmpty())) {
				ModelValueElement.Interpreted<?, ?, ?> mve = getElement().as(ModelValueElement.Interpreted.class, null);
				try {
					theValue = mve.getElementValue().as(ModelTypes.Value.anyAsV(),
						getElement().getExpressoEnv(getElement().getDefinition().getElement().getValue().fileLocation),
						ExceptionHandler.thrower());
				} catch (TypeConversionException e) {
					getElement().reporting()
					.error("Could not express value " + mve + " (" + mve.getElementValue().getType() + ") as a value", e);
					return null;
				}
				theAnchorType = (TypeToken<A>) theValue.getType().getType(0);
				theDbugAnchorType = Dbug.common().getAnchor(TypeTokens.getRawType(theAnchorType), false);
				if (theDbugAnchorType == null) {
					getElement().reporting()
					.warn("No Dbug anchor registered for type " + theAnchorType + ". Dbugging cannot be performed.");
				}
			}
			return theValue;
		}

		public TypeToken<A> getAnchorType() {
			return theAnchorType;
		}

		public DbugAnchorType<? super A> getDbugAnchorType() {
			return theDbugAnchorType;
		}

		public List<DbugTag.Interpreted> getTags() {
			return Collections.unmodifiableList(theTags);
		}

		public List<DbugAnchorWatch.Interpreted<A>> getWatches() {
			return Collections.unmodifiableList(theWatches);
		}

		@Override
		public Class<DbugModelValue<A>> getInstanceType() {
			return (Class<DbugModelValue<A>>) (Class<?>) DbugModelValue.class;
		}

		@Override
		public void postUpdate(ExElement.Interpreted<? extends ExElement> element) throws ExpressoInterpretationException {
			super.postUpdate(element);
			getValue();
			element.syncChildren(getDefinition().getTags(), theTags, d -> d.interpret(element), DbugTag.Interpreted::updateTag);
			element.syncChildren(getDefinition().getWatches(), theWatches, d -> (DbugAnchorWatch.Interpreted<A>) d.interpret(element),
				DbugAnchorWatch.Interpreted::updateWatch);
		}

		@Override
		public DbugModelValue<A> create(ExElement element) {
			return new DbugModelValue<>(element);
		}
	}

	private ModelValueInstantiator<SettableValue<A>> theValueInstantiator;
	private SettableValue<A> theValue;
	private DbugAnchorType<? super A> theDbugAnchorType;
	private List<DbugTag> theTags;
	private List<DbugAnchorWatch<A>> theWatches;

	DbugModelValue(ExElement element) {
		super(element);
		theTags = new ArrayList<>();
		theWatches = new ArrayList<>();
	}

	public List<DbugTag> getTags() {
		return Collections.unmodifiableList(theTags);
	}

	public List<DbugAnchorWatch<A>> getWatches() {
		return Collections.unmodifiableList(theWatches);
	}

	@Override
	public Class<? extends ExAddOn.Interpreted<ExElement, ?>> getInterpretationType() {
		return (Class<? extends ExAddOn.Interpreted<ExElement, ?>>) (Class<?>) Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);

		Interpreted<A> myInterpreted = (Interpreted<A>) interpreted;

		try {
			theValueInstantiator = myInterpreted.getValue() == null ? null : myInterpreted.getValue().instantiate();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Should not happen", e);
		}
		theDbugAnchorType = myInterpreted.getDbugAnchorType();

		CollectionUtils.synchronize(theTags, myInterpreted.getTags(), (inst, interp) -> inst.getIdentity().equals(interp.getIdentity()))//
		.<ModelInstantiationException> simpleX(DbugTag.Interpreted::create)//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), element))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), element))//
		.rightOrder()//
		.adjust();

		CollectionUtils
		.synchronize(theWatches, myInterpreted.getWatches(), (inst, interp) -> inst.getIdentity().equals(interp.getIdentity()))//
		.<ModelInstantiationException> simpleX(DbugAnchorWatch.Interpreted::create)//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), element))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), element))//
		.rightOrder()//
		.adjust();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		for (DbugTag tag : theTags)
			tag.instantiated();
		for (DbugAnchorWatch<A> watch : theWatches)
			watch.instantiated();
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);

		theValue = theValueInstantiator == null ? null : theValueInstantiator.get(models);

		for (DbugTag tag : theTags)
			tag.instantiate(models);
		for (DbugAnchorWatch<A> watch : theWatches)
			watch.instantiate(models);

		if (theValue != null) {
			Observable<?> typeUntil = Observable.or(models.getUntil(), getElement().onDestroy());
			Debug.d().start();
			Transaction typeActive = theDbugAnchorType.activate();
			SimpleObservable<java.lang.Void> valueUntil = new SimpleObservable<>();
			typeUntil.take(1).act(__ -> {
				valueUntil.onNext(null);
				typeActive.close();
			});
			theValue.changes().takeUntil(typeUntil).act(evt -> {
				if (evt.isUpdate())
					return;
				valueUntil.onNext(null);
				if (evt.getNewValue() == null)
					return;
				DebugData dd = Debug.d().debug(evt.getNewValue());
				for (DbugTag tag : theTags)
					dd.setField(tag.getTag(), true);
				DbugAnchor<? super A> anchor = theDbugAnchorType.getInstance(evt.getNewValue());
				if (anchor == null) {
					getElement().reporting().info("Dbug is not implemented by " + evt.getNewValue().getClass() + " instance");
					return;
				}
				Transaction[] uninstall = new Transaction[theTags.size()];
				for (int t = 0; t < uninstall.length; t++)
					uninstall[t] = anchor.tag(theTags.get(t).getTag());
				valueUntil.take(1).act(__ -> Transaction.and(uninstall).close());
				for (DbugAnchorWatch<A> watch : theWatches)
					watch.watch(anchor, valueUntil);
			});
		}
		return models;
	}

	@Override
	public DbugModelValue<A> copy(ExElement element) {
		DbugModelValue<A> copy = (DbugModelValue<A>) super.copy(element);

		copy.theTags = new ArrayList<>();
		for (DbugTag tag : theTags)
			copy.theTags.add(tag.copy(element));
		copy.theWatches = new ArrayList<>();
		for (DbugAnchorWatch<A> watch : theWatches)
			copy.theWatches.add(watch.copy(element));
		return copy;
	}

	@Override
	public void destroy() {
		super.destroy();

		for (DbugTag tag : theTags)
			tag.destroy();
		for (DbugAnchorWatch<A> watch : theWatches)
			watch.destroy();
	}
}
