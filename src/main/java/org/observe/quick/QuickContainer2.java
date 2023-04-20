package org.observe.quick;

import java.util.ArrayList;
import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;

public interface QuickContainer2<C extends QuickWidget> extends QuickWidget {
	public interface Def<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Def<W> {
		List<? extends QuickWidget.Def<C>> getContents();

		@Override
		Interpreted<? extends W, ? extends C> interpret(Interpreted<?, ?> parent, QuickInterpretationCache cache)
			throws ExpressoInterpretationException;

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
		}
	}

	public interface Interpreted<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W, ? super C> getDefinition();

		List<? extends QuickWidget.Interpreted<C>> getContents();

		public abstract class Abstract<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<W, C> {
			private final List<QuickWidget.Interpreted<C>> theContents;

			public Abstract(Def<? super W, ? super C> definition, Interpreted<?, ?> parent, QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
				super(definition, parent, cache);
				theContents = new ArrayList<>();
			}

			@Override
			public Def<? super W, ? super C> getDefinition() {
				return (Def<? super W, ? super C>) super.getDefinition();
			}

			@Override
			public List<QuickWidget.Interpreted<C>> getContents() {
				return theContents;
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
		public void update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);

			// TODO Adjust contents with parent

			for (C content : theContents)
				content.update(getModels());
		}
	}
}
