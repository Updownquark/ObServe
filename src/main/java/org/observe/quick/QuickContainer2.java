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

/**
 * A QuickWidget which contains other widgets that are (typically) drawn on top of it
 *
 * @param <C> The type of widgets in this container's content
 */
public interface QuickContainer2<C extends QuickWidget> extends QuickWidget {
	/**
	 * The definition of a QuickContainer
	 *
	 * @param <W> The type of the container that this definition is for
	 * @param <C> The type of widgets that the container will contain
	 */
	public interface Def<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Def<W> {
		/** @return The definitions of all widgets that will be contained in the container produced by this definition */
		BetterList<? extends QuickWidget.Def<? extends C>> getContents();

		@Override
		Interpreted<? extends W, ? extends C> interpret(QuickElement.Interpreted<?> parent);

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <W> The type of the container that this definition is for
		 * @param <C> The type of widgets that the container will contain
		 */
		public abstract class Abstract<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Def.Abstract<W>
		implements Def<W, C> {
			private final BetterList<QuickWidget.Def<? extends C>> theContents;

			/**
			 * @param parent The parent definition
			 * @param element The element that this definition is interpreted from
			 */
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

	/**
	 * An interpretation of a QuickContainer
	 *
	 * @param <W> The type of the container that this interpretation is for
	 * @param <C> The type of widgets that the container will contain
	 */
	public interface Interpreted<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Interpreted<W> {
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
		public abstract class Abstract<W extends QuickContainer2<C>, C extends QuickWidget> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<W, C> {
			private final BetterList<QuickWidget.Interpreted<? extends C>> theContents;

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The parent interpretation
			 */
			public Abstract(Def<? super W, ? super C> definition, QuickElement.Interpreted<?> parent) {
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

	/** @return The widgets contained in this container */
	BetterList<? extends C> getContents();

	/**
	 * An abstract {@link QuickContainer2} implementation
	 *
	 * @param <C> The type of the contained widgets
	 */
	public abstract class Abstract<C extends QuickWidget> extends QuickWidget.Abstract implements QuickContainer2<C> {
		private final BetterList<C> theContents;

		/**
		 * @param interpreted The interpretation producing this container
		 * @param parent The parent element
		 */
		public Abstract(QuickContainer2.Interpreted<?, ?> interpreted, QuickElement parent) {
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
		public QuickContainer2.Abstract<C> update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);

			CollectionUtils.synchronize(theContents, getInterpreted().getContents(), //
				(widget, child) -> widget.getInterpreted() == child)//
			.<ModelInstantiationException> simpleE(child -> (C) child.create(QuickContainer2.Abstract.this))//
			.rightOrder()//
			.onRightX(element -> element.getLeftValue().update(getModels()))//
			.onCommonX(element -> element.getLeftValue().update(getModels()))//
			.adjust();
			return this;
		}
	}
}
