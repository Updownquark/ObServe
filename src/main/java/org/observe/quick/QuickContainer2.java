package org.observe.quick;

import java.util.ArrayList;
import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;

public interface QuickContainer2<C extends QuickWidget> extends QuickWidget {
	public interface Def<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Def<W> {
		List<? extends QuickWidget.Def<C>> getContents();

		@Override
		Interpreted<? extends W, ? extends C> interpret(Interpreted<?, ?> parent) throws ExpressoInterpretationException;

		public abstract class Abstract<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Def.Abstract<W>
		implements Def<W, C> {
			private final List<QuickWidget.Def<C>> theContents;

			public Abstract(AbstractQIS<?> session) throws QonfigInterpretationException {
				super(session);
				theContents = new ArrayList<>();
			}

			@Override
			public List<QuickWidget.Def<C>> getContents() {
				return theContents;
			}

			@Override
			public Def.Abstract<W, C> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				// TODO At some point, it would be better to adjust the collection instead of this sledgehammer
				theContents.clear();
				for (AbstractQIS<?> child : session.forChildren("content"))
					theContents.add(child.interpret(QuickWidget.Def.class).update(child));
				return this;
			}
		}
	}

	public interface Interpreted<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W, ? super C> getDefinition();

		List<? extends QuickWidget.Interpreted<? extends C>> getContents();

		public abstract class Abstract<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<W, C> {
			private final List<QuickWidget.Interpreted<? extends C>> theContents;

			public Abstract(Def<? super W, ? super C> definition, Interpreted<?, ?> parent) throws ExpressoInterpretationException {
				super(definition, parent);
				theContents = new ArrayList<>();
			}

			@Override
			public Def<? super W, ? super C> getDefinition() {
				return (Def<? super W, ? super C>) super.getDefinition();
			}

			@Override
			public List<QuickWidget.Interpreted<? extends C>> getContents() {
				return theContents;
			}

			@Override
			public Interpreted.Abstract<W, C> update(InterpretedModelSet models, QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
				super.update(models, cache);
				// TODO At some point, it would be better to adjust the collection instead of this sledgehammer
				theContents.clear();
				for (QuickWidget.Def<? super C> child : getDefinition().getContents())
					theContents.add((QuickWidget.Interpreted<? extends C>) child.interpret(this).update(models, cache));
				return this;
			}
		}
	}

	@Override
	Interpreted<?, C> getInterpreted();

	ObservableCollection<? extends C> getContents();

	public abstract class Abstract<C extends QuickWidget> extends QuickWidget.Abstract implements QuickContainer2<C> {
		private final ObservableCollection<C> theContents;

		public Abstract(QuickContainer2.Interpreted<?, ?> interpreted, QuickContainer2<?> parent, ModelSetInstance models, Class<C> type)
			throws ModelInstantiationException {
			super(interpreted, parent, models);
			theContents = ObservableCollection.build(type).build();
		}

		@Override
		public QuickContainer2.Interpreted<?, C> getInterpreted() {
			return (QuickContainer2.Interpreted<?, C>) super.getInterpreted();
		}

		@Override
		public ObservableCollection<C> getContents() {
			return theContents;
		}

		@Override
		public QuickContainer2.Abstract<C> update(ModelSetInstance models, QuickInstantiationCache cache)
			throws ModelInstantiationException {
			super.update(models, cache);

			// TODO At some point, it would be better to adjust the collection instead of this sledgehammer
			theContents.clear();
			for (QuickWidget.Interpreted<? extends C> child : getInterpreted().getContents())
				theContents.add((C) child.create(this, models).update(getModels(), cache));
			//
			// for (C content : theContents)
			// content.update(getModels());
			return this;
		}
	}
}
