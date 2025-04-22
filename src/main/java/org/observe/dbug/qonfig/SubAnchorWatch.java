package org.observe.dbug.qonfig;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class SubAnchorWatch<A> extends ExAddOn.Abstract<DbugAnchorWatch<A>> {
	public static final String SUB_ANCHOR_WATCH = "sub-anchor-watch";

	@ExElementTraceable(toolkit = ExpressoDbugV0_1.DBUG,
		qonfigType = SUB_ANCHOR_WATCH,
		interpretation = Interpreted.class,
		instance = SubAnchorWatch.class)
	public static class Def extends ExAddOn.Def.Abstract<DbugAnchorWatch<?>, SubAnchorWatch<?>> {
		private CompiledExpression theWatch;

		public Def(QonfigAddOn type, ExElement.Def<? extends DbugAnchorWatch<?>> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("watch")
		public CompiledExpression getWatch() {
			return theWatch;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends DbugAnchorWatch<?>> element) throws QonfigInterpretationException {
			super.update(session, element);
			theWatch = element.getAttributeExpression("watch", session);
		}

		@Override
		public <E2 extends DbugAnchorWatch<?>> Interpreted<?> interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted<>(this, element);
		}
	}

	public static class Interpreted<A> extends ExAddOn.Interpreted.Abstract<DbugAnchorWatch<A>, SubAnchorWatch<A>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<A>> theWatch;
		private TypeToken<A> theAnchorType;

		Interpreted(Def definition, ExElement.Interpreted<? extends DbugAnchorWatch<A>> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<A>> getWatch() {
			return theWatch;
		}

		@Override
		public Class<SubAnchorWatch<A>> getInstanceType() {
			return (Class<SubAnchorWatch<A>>) (Class<?>) SubAnchorWatch.class;
		}

		public TypeToken<A> getAnchorType() throws ExpressoInterpretationException {
			if (theAnchorType == null) {
				theWatch = getElement().interpret(getDefinition().getWatch(), ModelTypes.Value.anyAs());
				ExElement.Interpreted<?> parent = getElement().as(ExElement.Interpreted.class, null).getParentElement();
				if (theWatch != null)
					theAnchorType = (TypeToken<A>) theWatch.getType().getType(0);
				else if (theWatch == null && parent.getAddOn(DbugModelValue.Interpreted.class) != null) {
					theWatch = parent.getAddOn(DbugModelValue.Interpreted.class).getValue();
					theAnchorType = (TypeToken<A>) theWatch.getType().getType(0);
				} else if (parent instanceof DbugAnchorWatch.Interpreted)
					theAnchorType = ((DbugAnchorWatch.Interpreted<A>) parent).getAnchorType();
				else {
					getElement().reporting().error("Unable to determine type of anchor");
					theAnchorType = (TypeToken<A>) TypeTokens.get().OBJECT;
				}
			}
			return theAnchorType;
		}

		@Override
		public void update(ExElement.Interpreted<? extends DbugAnchorWatch<A>> element) throws ExpressoInterpretationException {
			super.update(element);
			getAnchorType();
		}

		@Override
		public SubAnchorWatch<A> create(ExElement element) {
			return new SubAnchorWatch<>(element);
		}
	}

	private ModelValueInstantiator<SettableValue<A>> theWatchInstantiator;

	private SettableValue<A> theWatch;

	SubAnchorWatch(ExElement element) {
		super(element);
	}

	public SettableValue<A> getWatch() {
		return theWatch;
	}

	@Override
	public Class<? extends ExAddOn.Interpreted<DbugAnchorWatch<A>, ?>> getInterpretationType() {
		return (Class<ExAddOn.Interpreted<DbugAnchorWatch<A>, ?>>) (Class<?>) Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? super DbugAnchorWatch<A>, ?> interpreted, ExElement element)
		throws ModelInstantiationException {
		super.update(interpreted, element);
		Interpreted<A> myInterpreted = (Interpreted<A>) interpreted;
		theWatchInstantiator = myInterpreted.getWatch() == null ? null : myInterpreted.getWatch().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theWatchInstantiator != null)
			theWatchInstantiator.instantiate();
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);
		theWatch = theWatchInstantiator == null ? null : theWatchInstantiator.get(models);
		return models;
	}
}
