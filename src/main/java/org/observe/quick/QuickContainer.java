package org.observe.quick;

import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.tree.BetterTreeList;

/**
 * A QuickWidget which contains other widgets that are (typically) drawn on top of it
 *
 * @param <W> The type of widgets in this container's content
 */
public interface QuickContainer<W extends QuickWidget> extends QuickWidget {
	/** The XML name of this type */
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
		 * @param <C> The type of the container that this definition is for
		 * @param <W> The type of widgets that the container will contain
		 */
		public abstract class Abstract<C extends QuickContainer<W>, W extends QuickWidget> extends QuickWidget.Def.Abstract<C>
		implements Def<C, W> {
			private final BetterList<QuickWidget.Def<? extends W>> theContents;

			/**
			 * @param parent The parent definition
			 * @param type The element that this definition is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theContents = BetterTreeList.<QuickWidget.Def<? extends W>> build().build();
			}

			@QonfigChildGetter("content")
			@Override
			public BetterList<QuickWidget.Def<? extends W>> getContents() {
				return theContents;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				syncChildren(QuickWidget.Def.class, theContents, session.forChildren("content"));
			}
		}
	}

	/**
	 * An interpretation of a QuickContainer
	 *
	 * @param <C> The type of the container that this interpretation is for
	 * @param <W> The type of widgets that the container will contain
	 */
	public interface Interpreted<C extends QuickContainer<W>, W extends QuickWidget> extends QuickWidget.Interpreted<C> {
		@Override
		Def<? super C, ? super W> getDefinition();

		/** @return The interpretations of all widgets that will be contained in the container produced by this interpretation */
		BetterList<? extends QuickWidget.Interpreted<? extends W>> getContents();

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
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				syncChildren(getDefinition().getContents(), theContents,
					def -> (QuickWidget.Interpreted<? extends C>) def.interpret(Interpreted.Abstract.this),
					QuickWidget.Interpreted::updateElement);
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
	BetterList<? extends W> getContents();

	/**
	 * An abstract {@link QuickContainer} implementation
	 *
	 * @param <W> The type of the contained widgets
	 */
	public abstract class Abstract<W extends QuickWidget> extends QuickWidget.Abstract implements QuickContainer<W> {
		private ObservableCollection<W> theContents;

		/** @param id The element identifier for this element */
		protected Abstract(Object id) {
			super(id);
			theContents = ObservableCollection.<W> build().build();
		}

		@Override
		public ObservableCollection<W> getContents() {
			return theContents;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			QuickContainer.Interpreted<?, W> myInterpreted = (QuickContainer.Interpreted<?, W>) interpreted;
			try (Transaction t = theContents.lock(true, null)) {
				CollectionUtils.synchronize(theContents, myInterpreted.getContents(), //
					(widget, child) -> widget.getIdentity() == child.getIdentity())//
				.<ModelInstantiationException> simpleX(child -> (W) child.create())//
				.rightOrder()//
				.onRightX(element -> {
					try {
						element.getLeftValue().update(element.getRightValue(), this);
					} catch (RuntimeException | Error e) {
						element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
					}
				})//
				.onCommonX(element -> {
					try {
						element.getLeftValue().update(element.getRightValue(), this);
					} catch (RuntimeException | Error e) {
						element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
					}
				})//
				.adjust();
			}
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			for (W content : theContents)
				content.instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			for (W content : theContents)
				content.instantiate(myModels);
			return myModels;
		}

		@Override
		public QuickContainer.Abstract<W> copy(ExElement parent) {
			QuickContainer.Abstract<W> copy = (QuickContainer.Abstract<W>) super.copy(parent);

			copy.theContents = ObservableCollection.<W> build().build();
			for (W content : theContents)
				copy.theContents.add((W) content.copy(copy));

			return copy;
		}
	}
}
