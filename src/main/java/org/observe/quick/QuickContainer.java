package org.observe.quick;

import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.tree.BetterTreeList;

/**
 * A QuickWidget which contains other widgets that are (typically) drawn on top of it
 *
 * @param <C> The type of widgets in this container's content
 */
public interface QuickContainer<C extends QuickWidget> extends QuickWidget {
	public static final String CONTAINER = "container";

	/**
	 * The definition of a QuickContainer
	 *
	 * @param <W> The type of the container that this definition is for
	 * @param <C> The type of widgets that the container will contain
	 */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = CONTAINER,
		interpretation = Interpreted.class,
		instance = QuickContainer.class)
	public interface Def<W extends QuickContainer<C>, C extends QuickWidget> extends QuickWidget.Def<W> {
		/** @return The definitions of all widgets that will be contained in the container produced by this definition */
		@QonfigChildGetter("content")
		BetterList<? extends QuickWidget.Def<? extends C>> getContents();

		@Override
		Interpreted<? extends W, ? extends C> interpret(ExElement.Interpreted<?> parent);

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <W> The type of the container that this definition is for
		 * @param <C> The type of widgets that the container will contain
		 */
		public abstract class Abstract<W extends QuickContainer<C>, C extends QuickWidget> extends QuickWidget.Def.Abstract<W>
		implements Def<W, C> {
			private final BetterList<QuickWidget.Def<? extends C>> theContents;

			/**
			 * @param parent The parent definition
			 * @param type The element that this definition is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theContents = BetterTreeList.<QuickWidget.Def<? extends C>> build().build();
			}

			@QonfigChildGetter("content")
			@Override
			public BetterList<QuickWidget.Def<? extends C>> getContents() {
				return theContents;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				ExElement.syncDefs(QuickWidget.Def.class, theContents, session.forChildren("content"));
			}
		}
	}

	/**
	 * An interpretation of a QuickContainer
	 *
	 * @param <W> The type of the container that this interpretation is for
	 * @param <C> The type of widgets that the container will contain
	 */
	public interface Interpreted<W extends QuickContainer<C>, C extends QuickWidget> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W, ? super C> getDefinition();

		/** @return The interpretations of all widgets that will be contained in the container produced by this interpretation */
		BetterList<? extends QuickWidget.Interpreted<? extends C>> getContents();

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <W> The type of the container that this interpretation is for
		 * @param <C> The type of widgets that the container will contain
		 */
		public abstract class Abstract<W extends QuickContainer<C>, C extends QuickWidget> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<W, C> {
			private final BetterList<QuickWidget.Interpreted<? extends C>> theContents;

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The parent interpretation
			 */
			protected Abstract(Def<? super W, ? super C> definition, ExElement.Interpreted<?> parent) {
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
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				CollectionUtils.synchronize(theContents, getDefinition().getContents(), //
					(widget, child) -> widget.getIdentity() == child.getIdentity())//
				.<ExpressoInterpretationException> simpleE(
					child -> (QuickWidget.Interpreted<? extends C>) child.interpret(Interpreted.Abstract.this))//
				.rightOrder()//
				.onRightX(element -> element.getLeftValue().updateElement(getExpressoEnv()))//
				.onCommonX(element -> element.getLeftValue().updateElement(getExpressoEnv()))//
				.adjust();
			}

			@Override
			public void destroy() {
				for (QuickWidget.Interpreted<? extends C> content : theContents.reverse())
					content.destroy();
				theContents.clear();
				super.destroy();
			}
		}
	}

	/** @return The widgets contained in this container */
	BetterList<? extends C> getContents();

	/**
	 * An abstract {@link QuickContainer} implementation
	 *
	 * @param <C> The type of the contained widgets
	 */
	public abstract class Abstract<C extends QuickWidget> extends QuickWidget.Abstract implements QuickContainer<C> {
		private ObservableCollection<C> theContents;

		protected Abstract(Object id) {
			super(id);
			theContents = ObservableCollection.build((Class<C>) QuickWidget.class).build();
		}

		@Override
		public ObservableCollection<C> getContents() {
			return theContents;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			QuickContainer.Interpreted<?, C> myInterpreted = (QuickContainer.Interpreted<?, C>) interpreted;
			CollectionUtils.synchronize(theContents, myInterpreted.getContents(), //
				(widget, child) -> widget.getIdentity() == child.getIdentity())//
			.simple(child -> (C) child.create())//
			.rightOrder()//
			.onRight(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.onCommon(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.adjust();
		}

		@Override
		public void instantiated() {
			super.instantiated();
			for (C content : theContents)
				content.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			for (C content : theContents)
				content.instantiate(myModels);
		}

		@Override
		public QuickContainer.Abstract<C> copy(ExElement parent) {
			QuickContainer.Abstract<C> copy = (QuickContainer.Abstract<C>) super.copy(parent);

			copy.theContents = ObservableCollection.build((Class<C>) QuickWidget.class).build();
			for (C content : theContents)
				copy.theContents.add((C) content.copy(copy));

			return copy;
		}
	}
}
