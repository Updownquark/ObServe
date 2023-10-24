package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
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
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class CollapsePane extends QuickContainer.Abstract<QuickWidget> {
	public static final String COLLAPSE_PANE = "collapse-pane";

	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = COLLAPSE_PANE,
		interpretation = Interpreted.class,
		instance = CollapsePane.class)
	public static class Def extends QuickContainer.Def.Abstract<CollapsePane, QuickWidget> {
		private CompiledExpression isCollapsed;
		private QuickWidget.Def<?> theHeader;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("collapsed")
		public CompiledExpression isCollapsed() {
			return isCollapsed;
		}

		@QonfigChildGetter("header")
		public QuickWidget.Def<?> getHeader() {
			return theHeader;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			isCollapsed = getAttributeExpression("collapsed", session);
			theHeader = syncChild(QuickWidget.Def.class, theHeader, session, "header");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickContainer.Interpreted.Abstract<CollapsePane, QuickWidget> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isCollapsed;
		private QuickWidget.Interpreted<?> theHeader;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isCollapsed() {
			return isCollapsed;
		}

		public QuickWidget.Interpreted<?> getHeader() {
			return theHeader;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			isCollapsed = interpret(getDefinition().isCollapsed(), ModelTypes.Value.BOOLEAN);
			theHeader = syncChild(getDefinition().getHeader(), theHeader, def -> def.interpret(this), (h, hEnv) -> h.updateElement(hEnv));
		}

		@Override
		public CollapsePane create() {
			return new CollapsePane(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Boolean>> isCollapsedInstantiator;
	private SettableValue<SettableValue<Boolean>> isCollapsed;
	private QuickWidget theHeader;

	public CollapsePane(Object id) {
		super(id);
		isCollapsed = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
	}

	public SettableValue<Boolean> isCollapsed() {
		return SettableValue.flatten(isCollapsed);
	}

	public QuickWidget getHeader() {
		return theHeader;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		isCollapsedInstantiator = myInterpreted.isCollapsed() == null ? null : myInterpreted.isCollapsed().instantiate();

		if (theHeader != null
			&& (myInterpreted.getHeader() == null || theHeader.getIdentity() != myInterpreted.getHeader().getIdentity())) {
			theHeader.destroy();
			theHeader = null;
		}
		if (theHeader == null && myInterpreted.getHeader() != null)
			theHeader = myInterpreted.getHeader().create();
		if (theHeader != null)
			theHeader.update(myInterpreted.getHeader(), this);
	}

	@Override
	public void instantiated() {
		super.instantiated();

		if (isCollapsedInstantiator != null)
			isCollapsedInstantiator.instantiate();

		if (theHeader != null)
			theHeader.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		isCollapsed.set(isCollapsedInstantiator == null ? null : isCollapsedInstantiator.get(myModels), null);

		if (theHeader != null)
			theHeader.instantiate(myModels);
	}

	@Override
	public CollapsePane copy(ExElement parent) {
		CollapsePane copy = (CollapsePane) super.copy(parent);

		copy.isCollapsed = SettableValue.build(isCollapsed.getType()).build();
		if (theHeader != null)
			copy.theHeader = theHeader.copy(copy);

		return copy;
	}
}
