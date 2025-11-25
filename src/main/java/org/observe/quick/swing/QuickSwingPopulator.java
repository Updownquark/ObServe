package org.observe.quick.swing;

import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.event.CaretListener;
import javax.swing.text.JTextComponent;

import org.observe.Observable;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.quick.QuickBorder;
import org.observe.quick.QuickDialog;
import org.observe.quick.QuickEventListener;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.QuickLayout;
import org.observe.quick.base.StyledDocument;
import org.observe.quick.base.ValueAction;
import org.observe.util.swing.ComponentDecorator;
import org.observe.util.swing.ObservableStyledDocument;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.ContainerPopulator;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.Transformer;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExTriConsumer;

/**
 * <p>
 * Populates a {@link PanelPopulator} for a Quick widget.
 * </p>
 * <p>
 * This class contains lots of {@link Transformer} interpretation utilities for turning standard Quick libraries into Java Swing components.
 * </p>
 *
 * @param <W> The type of the Quick widget
 */
public interface QuickSwingPopulator<W extends QuickWidget> {
	/**
	 * @param panel The panel to populate
	 * @param quick The Quick widget to populate for
	 * @throws ModelInstantiationException If a problem occurs instantiating any components
	 */
	void populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

	/**
	 * Adds a modifier to this populator to be called when the component is added
	 *
	 * @param modify The modifier
	 * @return An action to remove the modifier
	 */
	Runnable addModifier(ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modify);

	/**
	 * Performs this populator's modifiers (e.g. decoration) on a component
	 *
	 * @param component The component editor to modify
	 * @param widget The QuickWidget to modify
	 */
	void modify(ComponentEditor<?, ?> component, W widget);

	/**
	 * Abstract {@link QuickSwingPopulator} implementation
	 *
	 * @param <W> The type of the Quick widget
	 */
	public abstract class Abstract<W extends QuickWidget> implements QuickSwingPopulator<W> {
		private final List<ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException>> theModifiers;

		/** Creates the populator */
		protected Abstract() {
			theModifiers = new LinkedList<>();
		}

		/**
		 * @param panel The panel to populate
		 * @param quick The Quick widget to populate for
		 * @param component A consumer to be given the populated component editor as it is configured
		 * @throws ModelInstantiationException If an problem occurs instantiating any components
		 */
		protected abstract void doPopulate(PanelPopulator<?, ?> panel, W quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException;

		@Override
		public void populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
			populate((ContainerPopulator<?, ?>) panel, quick);
		}

		/**
		 * @param <P> The type of the populator
		 * @param panel The populator
		 * @param quick The Quick widget
		 * @throws ModelInstantiationException If the UI population fails
		 */
		protected <P extends ContainerPopulator<?, ?>> void populate(P panel, W quick) throws ModelInstantiationException {
			boolean[] modified = new boolean[1];
			try {
				doPopulate((PanelPopulator<?, ?>) panel, quick, comp -> {
					modified[0] = true;
					if (comp != null) {
						modify(comp, quick);
					}
				});
			} catch (CheckedExceptionWrapper w) {
				throw CheckedExceptionWrapper.getThrowable(w, ModelInstantiationException.class);
			} catch (Throwable e) {
				quick.reporting().error("Unexpected error", e);
				return;
			}
			if (!modified[0])
				throw new IllegalStateException("Component modifier not invoked by " + getClass().getName());
		}

		@Override
		public Runnable addModifier(ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modify) {
			theModifiers.add(modify);
			return () -> theModifiers.remove(modify);
		}

		@Override
		public void modify(ComponentEditor<?, ?> component, W widget) {
			try {
				for (ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modifier : theModifiers)
					modifier.accept(component, widget);
			} catch (ModelInstantiationException e) {
				throw new CheckedExceptionWrapper(e);
			}
		}
	}

	/**
	 * Quick Swing populator for a container
	 *
	 * @param <W> The type of Quick widget
	 */
	public interface QuickSwingContainerPopulator<W extends QuickWidget> extends QuickSwingPopulator<W> {
		/**
		 * @param panel The container to populate
		 * @param quick The quick widget to populate the container with
		 * @throws ModelInstantiationException If a problem occurs instantiating any components
		 */
		void populateContainer(ContainerPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

		@Override
		default void populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
			populateContainer(panel, quick);
		}

		/**
		 * Abstract {@link QuickSwingContainerPopulator}
		 *
		 * @param <W> The type of Quick widget
		 */
		public abstract class Abstract<W extends QuickWidget> extends QuickSwingPopulator.Abstract<W>
		implements QuickSwingContainerPopulator<W> {
			/**
			 * @param panel The container to populate
			 * @param quick The quick widget to populate the container with
			 * @param component A consumer to be given the populated component editor as it is configured
			 * @throws ModelInstantiationException If a problem occurs instantiating any components
			 */
			protected abstract void doPopulateContainer(ContainerPopulator<?, ?> panel, W quick, Consumer<ComponentEditor<?, ?>> component)
				throws ModelInstantiationException;

			@Override
			public void populateContainer(ContainerPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
				populate(panel, quick);
			}

			@Override
			protected void doPopulate(PanelPopulator<?, ?> panel, W quick, Consumer<ComponentEditor<?, ?>> component)
				throws ModelInstantiationException {
				doPopulateContainer(panel, quick, component);
			}
		}
	}

	/**
	 * A modifier for a Swing window by a Quick add-on
	 *
	 * @param <AO> The type of add-on to modify the window with
	 */
	public interface WindowModifier<AO extends ExAddOn<?>> {
		/**
		 * @param window The window to modify
		 * @param quick The add-on to modify the window with
		 * @throws ModelInstantiationException If a problem occurs with the quick add-on
		 */
		void modifyWindow(PanelPopulation.WindowBuilder<?, ?> window, AO quick) throws ModelInstantiationException;
	}

	/**
	 * Creates a Swing layout from a {@link QuickLayout}
	 *
	 * @param <L> The type of the Quick layout
	 */
	public interface QuickSwingLayout<L extends QuickLayout> {
		/**
		 * @param panel The populator of the panel whose components to manage
		 * @param quick The Quick layout to create the layout for
		 * @return The swing layout interpretation of the Quick layout
		 * @throws ModelInstantiationException If a problem occurs instantiating the layout
		 */
		LayoutManager create(ContainerPopulator<?, ?> panel, L quick) throws ModelInstantiationException;

		/**
		 * @param child The child populator to modify
		 * @throws ExpressoInterpretationException If an exception occurs interpreting anything in this layout's child modification
		 */
		void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException;
	}

	/** A swing border configured by a {@link QuickBorder} */
	public interface QuickSwingBorder {
		/**
		 * @param deco The component decorator to configure
		 * @param border The Quick border
		 * @param component A 1-element array in which the bordered component will be placed when it becomes available
		 * @throws ModelInstantiationException If a problem occurs with the Quick border
		 */
		void decorate(ComponentDecorator deco, QuickBorder border, Component[] component) throws ModelInstantiationException;
	}

	/**
	 * An Quick event listener in Swing
	 *
	 * @param <L> The type of the Quick listener
	 */
	public interface QuickSwingEventListener<L extends QuickEventListener> {
		/**
		 * @param c The component to install the listener on
		 * @param listener The Quick listener
		 * @throws ModelInstantiationException If a problem occurs with the Quick listener
		 */
		void addListener(Component c, L listener) throws ModelInstantiationException;
	}

	/**
	 * A Quick Swing dialog populator
	 *
	 * @param <D> The type of Quick dialog
	 */
	public interface QuickSwingDialog<D extends QuickDialog> {
		/**
		 * @param dialog The Quick dialog to configure the Swing dialog
		 * @param parent The parent anchor for the dialog
		 * @param until The observable to kill the dialog installation
		 * @throws ModelInstantiationException If a problem occurs with the Quick dialog
		 */
		void initialize(D dialog, Component parent, Observable<?> until) throws ModelInstantiationException;
	}

	/**
	 * A Quick-backed Swing styled document
	 *
	 * @param <T> The type of values in the document
	 */
	public interface QuickSwingDocument<T> {
		/**
		 * @param quickDoc The Quick styled document
		 * @param until The observable to kill the document installation
		 * @return The Swing styled document
		 * @throws ModelInstantiationException If a problem occurs interpreting the Quick document
		 */
		ObservableStyledDocument<T> interpret(StyledDocument<T> quickDoc, Observable<?> until) throws ModelInstantiationException;

		/**
		 * @param quickDoc The Quick styled document
		 * @param doc The Swing styled document
		 * @param widget The Swing text component
		 * @param until The observable to kill the document installation
		 * @return The mouse listener to install in the text component
		 */
		MouseAdapter mouseListener(StyledDocument<T> quickDoc, ObservableStyledDocument<T> doc, JTextComponent widget, Observable<?> until);

		/**
		 * @param quickDoc The Quick styled document
		 * @param doc The Swing styled document
		 * @param widget The Swing text component
		 * @param until The observable to kill the document installation
		 * @return The caret listener to install in the document
		 */
		CaretListener caretListener(StyledDocument<T> quickDoc, ObservableStyledDocument<T> doc, JTextComponent widget,
			Observable<?> until);
	}

	/**
	 * A Quick-backed Swing table action
	 *
	 * @param <R> The type of rows in the Quick table widget
	 * @param <A> The type of Quick action
	 */
	public interface QuickSwingTableAction<R, A extends ValueAction<R>> {
		/**
		 * @param <R2> The type of rows in the PanelPopulation table
		 * @param table The PanelPopulation table
		 * @param update The function to update a PanelPopulation value when a row changes in the Quick widget's row collection
		 * @param reverse The function to produce a Quick widget's row value from a PanelPopulation row
		 * @param action The Quick table action
		 * @throws ModelInstantiationException If a problem occurs interpreting the Quick table action
		 */
		<R2> void addAction(PanelPopulation.CollectionWidgetBuilder<R2, ?, ?> table, Function<R2, R> reverse, A action)
			throws ModelInstantiationException;
	}

	/**
	 * Utility for modification of swing widgets by Quick abstract widgets
	 *
	 * @param <W> The type of the Quick widget to interpret
	 * @param <I> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param interpretedType The type of the interpreted quick widget
	 * @param modifier Modifies a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> void modifyForWidget(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<I> interpretedType,
		ExTriConsumer<? super I, QuickSwingPopulator<W>, Transformer<ExpressoInterpretationException>, ExpressoInterpretationException> modifier) {
		transformer.modifyWith(interpretedType, (Class<QuickSwingPopulator<W>>) (Class<?>) QuickSwingPopulator.class,
			new Transformer.Modifier<I, QuickSwingPopulator<W>, ExpressoInterpretationException>() {
			@Override
			public <T2 extends QuickSwingPopulator<W>> T2 modify(I source, T2 value, Transformer<ExpressoInterpretationException> tx)
				throws ExpressoInterpretationException {
				modifier.accept(source, value, tx);
				return value;
			}
		});
	}

	/**
	 * Utility for modification of swing widgets by Quick add-ons
	 *
	 * @param <W> The type of the Quick widget to interpret
	 * @param <AOI> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param interpretedType The interpreted type of the quick widget
	 * @param widgetType The instance type of the quick widget
	 * @param modifier Modifies a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, AO extends ExAddOn<? super W>, AOI extends ExAddOn.Interpreted<? super W, ? extends AO>> void modifyForAddOn(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<AOI> interpretedType,
		Class<? extends QuickWidget.Interpreted<W>> widgetType,
			ExTriConsumer<? super AOI, QuickSwingPopulator<?>, Transformer<ExpressoInterpretationException>, ExpressoInterpretationException> modifier) {
		transformer.modifyWith(widgetType, (Class<QuickSwingPopulator<W>>) (Class<?>) QuickSwingPopulator.class,
			new Transformer.Modifier<QuickWidget.Interpreted<W>, QuickSwingPopulator<W>, ExpressoInterpretationException>() {
			@Override
			public <T2 extends QuickSwingPopulator<W>> T2 modify(QuickWidget.Interpreted<W> source, T2 value,
				Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
				AOI addOn = source.getAddOn(interpretedType);
				if (addOn != null)
					modifier.accept(addOn, value, tx);
				return value;
			}
		});
	}
}
