package org.observe.dbug.qonfig;

import org.observe.ObservableAction;
import org.observe.expresso.ExpressoInterpretationException;
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
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class DbugDo extends ExElement.Abstract implements DbugAction {
	public static final String DO = "do";

	@ExElementTraceable(toolkit = ExpressoDbugV0_1.DBUG, qonfigType = DO, interpretation = Interpreted.class, instance = DbugDo.class)
	public static class Def extends ExElement.Def.Abstract<DbugDo> implements DbugAction.Def<DbugDo> {
		private CompiledExpression theAction;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter
		public CompiledExpression getAction() {
			return theAction;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theAction = getValueExpression(session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<DbugDo> implements DbugAction.Interpreted<DbugDo> {
		private InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableAction, ObservableAction> getAction() {
			return theAction;
		}

		@Override
		public void updateAction() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			theAction = interpret(getDefinition().getAction(), ModelTypes.Action.instance());
		}

		@Override
		public DbugDo create() {
			return new DbugDo(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableAction> theActionInstantiator;

	private ObservableAction theAction;

	DbugDo(Object id) {
		super(id);
	}

	@Override
	public void execute(Object cause) {
		theAction.act(cause);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theActionInstantiator = myInterpreted.getAction().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theActionInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);
		theAction = theActionInstantiator.get(myModels);
		return myModels;
	}

	@Override
	public DbugDo copy(ExElement parent) {
		return (DbugDo) super.copy(parent);
	}
}
