package org.observe.quick;

import java.util.List;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickCompiledStyle;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.tree.BetterTreeList;

/**
 * A QuickWidget which contains other widgets that are (typically) drawn on top of it
 *
 * @param <C> The type of widgets in this container's content
 */
public interface QuickContainer<C extends QuickWidget> extends QuickWidget {
	public static final String CONTAINER = "container";

	public static final QuickElement.ChildElementGetter<QuickContainer<?>, Interpreted<?, ?>, Def<?, ?>> CONTENTS = new QuickElement.ChildElementGetter<QuickContainer<?>, Interpreted<?, ?>, Def<?, ?>>() {
		@Override
		public String getDescription() {
			return "A widget displayed inside the container";
		}

		@Override
		public List<? extends QuickElement.Def<?>> getChildrenFromDef(Def<?, ?> def) {
			return def.getContents();
		}

		@Override
		public List<? extends QuickElement.Interpreted<?>> getChildrenFromInterpreted(Interpreted<?, ?> interp) {
			return interp.getContents();
		}

		@Override
		public List<? extends QuickElement> getChildrenFromElement(QuickContainer<?> element) {
			return element.getContents();
		}
	};

	/**
	 * The definition of a QuickContainer
	 *
	 * @param <W> The type of the container that this definition is for
	 * @param <C> The type of widgets that the container will contain
	 */
	public interface Def<W extends QuickContainer<C>, C extends QuickWidget> extends QuickWidget.Def<W> {
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
		public abstract class Abstract<W extends QuickContainer<C>, C extends QuickWidget> extends QuickWidget.Def.Abstract<W>
		implements Def<W, C> {
			private final BetterList<QuickWidget.Def<? extends C>> theContents;

			/**
			 * @param parent The parent definition
			 * @param element The element that this definition is interpreted from
			 */
			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theContents = BetterTreeList.<QuickWidget.Def<? extends C>> build().build();
			}

			@Override
			public BetterList<QuickWidget.Def<? extends C>> getContents() {
				return theContents;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, CONTAINER);
				forChild(session.getRole("content").getDeclared(), CONTENTS);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
				QuickElement.syncDefs(QuickWidget.Def.class, theContents, session.forChildren("content"));
			}

			@Override
			protected QuickWidgetStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickWidgetStyle.Def.Default(parentStyle, style);
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
			protected Abstract(Def<? super W, ? super C> definition, QuickElement.Interpreted<?> parent) {
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
			public void update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update(cache);
				CollectionUtils.synchronize(theContents, getDefinition().getContents(), //
					(widget, child) -> widget.getDefinition() == child)//
				.<ExpressoInterpretationException> simpleE(
					child -> (QuickWidget.Interpreted<? extends C>) child.interpret(Interpreted.Abstract.this))//
				.rightOrder()//
				.onRightX(element -> element.getLeftValue().update(cache))//
				.onCommonX(element -> element.getLeftValue().update(cache))//
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
		private final BetterList<C> theContents;

		/**
		 * @param interpreted The interpretation producing this container
		 * @param parent The parent element
		 */
		protected Abstract(QuickContainer.Interpreted<?, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theContents = BetterTreeList.<C> build().build();
		}

		@Override
		public BetterList<C> getContents() {
			return theContents;
		}

		@Override
		protected void updateModel(QuickElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickContainer.Interpreted<?, C> myInterpreted = (QuickContainer.Interpreted<?, C>) interpreted;
			CollectionUtils.synchronize(theContents, myInterpreted.getContents(), //
				(widget, child) -> widget.getIdentity() == child.getDefinition().getIdentity())//
			.<ModelInstantiationException> simpleE(child -> (C) child.create(QuickContainer.Abstract.this))//
			.rightOrder()//
			.onRightX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), myModels);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.onCommonX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), myModels);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.adjust();
		}
	}
}
