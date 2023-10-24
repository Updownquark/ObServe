package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickMenuBar extends ExElement.Abstract {
	public static final String MENU_BAR = "menu-bar";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MENU_BAR,
		interpretation = Interpreted.class,
		instance = QuickMenuBar.class)
	public static class Def extends ExElement.Def.Abstract<QuickMenuBar> {
		private final List<QuickMenu.Def<?>> theMenus;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theMenus = new ArrayList<>();
		}

		@QonfigChildGetter("menu")
		public List<QuickMenu.Def<?>> getMenus() {
			return Collections.unmodifiableList(theMenus);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			syncChildren(QuickMenu.Def.class, theMenus, session.forChildren("menu"));
		}

		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<QuickMenuBar> {
		private final List<QuickMenu.Interpreted<?, ?>> theMenus;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theMenus = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public List<QuickMenu.Interpreted<?, ?>> getMenus() {
			return Collections.unmodifiableList(theMenus);
		}

		public void updateMenuBar(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			update(env);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			syncChildren(getDefinition().getMenus(), theMenus, def -> def.interpret(this), QuickMenu.Interpreted::updateElement);
		}

		public QuickMenuBar create() {
			return new QuickMenuBar(getIdentity());
		}
	}

	private ObservableCollection<QuickMenu<?>> theMenus;

	public QuickMenuBar(Object id) {
		super(id);
		theMenus = ObservableCollection.build(TypeTokens.get().keyFor(QuickMenu.class).<QuickMenu<?>> wildCard()).build();
	}

	public ObservableCollection<QuickMenu<?>> getMenus() {
		return theMenus.flow().unmodifiable(false).collect();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		CollectionUtils.synchronize(theMenus, myInterpreted.getMenus(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.simple(interp -> interp.create())//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRight(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommon(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();
	}

	@Override
	public void instantiated() {
		super.instantiated();

		for (QuickMenu<?> menuItem : theMenus)
			menuItem.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		for (QuickMenu<?> menuItem : theMenus)
			menuItem.instantiate(myModels);
	}

	@Override
	public QuickMenuBar copy(ExElement parent) {
		QuickMenuBar copy = (QuickMenuBar) super.copy(parent);

		copy.theMenus = ObservableCollection.build(theMenus.getType()).build();
		for (QuickMenu<?> menuItem : theMenus)
			copy.theMenus.add(menuItem.copy(copy));

		return copy;
	}
}
