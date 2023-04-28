package org.observe.quick;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.tree.BetterTreeList;

public interface QuickContainer2<C extends QuickWidget> extends QuickWidget {
	public interface Def<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Def<W> {
		BetterList<? extends QuickWidget.Def<? extends C>> getContents();

		@Override
		Interpreted<? extends W, ? extends C> interpret(Interpreted<?, ?> parent);

		public abstract class Abstract<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Def.Abstract<W>
		implements Def<W, C> {
			private final BetterList<QuickWidget.Def<? extends C>> theContents;

			public Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theContents = BetterTreeList.<QuickWidget.Def<? extends C>> build().build();
			}

			@Override
			public BetterList<QuickWidget.Def<? extends C>> getContents() {
				return theContents;
			}

			@Override
			public Def.Abstract<W, C> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				CollectionUtils.synchronize(theContents, session.forChildren("content"), //
					(widget, child) -> QuickElement.typesEqual(widget.getElement(), child.getElement()))//
				.simpleE(child -> child.interpret(QuickWidget.Def.class))//
				.rightOrder()//
				.onRightX(element -> element.getLeftValue().update(element.getRightValue()))//
				.onCommonX(element -> element.getLeftValue().update(element.getRightValue()))//
				.adjust();
				return this;
			}
		}
	}

	public interface Interpreted<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W, ? super C> getDefinition();

		BetterList<? extends QuickWidget.Interpreted<? extends C>> getContents();

		public abstract class Abstract<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<W, C> {
			private final BetterList<QuickWidget.Interpreted<? extends C>> theContents;

			public Abstract(Def<? super W, ? super C> definition, Interpreted<?, ?> parent) {
				super(definition, parent);
				theContents = BetterTreeList.<QuickWidget.Interpreted<? extends C>> build().build();
			}

			@Override
			public Def<? super W, ? super C> getDefinition() {
				return (Def<? super W, ? super C>) super.getDefinition();
			}

			@Override
			public BetterList<QuickWidget.Interpreted<? extends C>> getContents() {
				return theContents;
			}

			@Override
			public Interpreted.Abstract<W, C> update(InterpretedModelSet models, QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
				super.update(models, cache);
				CollectionUtils.synchronize(theContents, getDefinition().getContents(), //
					(widget, child) -> widget.getDefinition() == child)//
				.<ExpressoInterpretationException> simpleE(
					child -> (QuickWidget.Interpreted<? extends C>) child.interpret(Interpreted.Abstract.this))//
				.rightOrder()//
				.onRightX(element -> element.getLeftValue().update(models, cache))//
				.onCommonX(element -> element.getLeftValue().update(models, cache))//
				.adjust();
				return this;
			}
		}
	}

	@Override
	Interpreted<?, C> getInterpreted();

	BetterList<? extends C> getContents();

	public abstract class Abstract<C extends QuickWidget> extends QuickWidget.Abstract implements QuickContainer2<C> {
		private final BetterList<C> theContents;

		public Abstract(QuickContainer2.Interpreted<?, ?> interpreted, QuickContainer2<?> parent) {
			super(interpreted, parent);
			theContents = BetterTreeList.<C> build().build();
		}

		@Override
		public QuickContainer2.Interpreted<?, C> getInterpreted() {
			return (QuickContainer2.Interpreted<?, C>) super.getInterpreted();
		}

		@Override
		public BetterList<C> getContents() {
			return theContents;
		}

		@Override
		public QuickContainer2.Abstract<C> update(ModelSetInstance models, QuickInstantiationCache cache)
			throws ModelInstantiationException {
			super.update(models, cache);

			// TODO At some point, it would be better to adjust the collection instead of this sledgehammer
			theContents.clear();
			for (QuickWidget.Interpreted<? extends C> child : getInterpreted().getContents())
				theContents.add((C) child.create(this).update(getModels(), cache));
			//
			// for (C content : theContents)
			// content.update(getModels());
			return this;
		}
	}
}
