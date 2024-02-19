package org.observe.quick.swing;

import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

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
	 * @throws ModelInstantiationException If an problem occurs instantiating any components
	 */
	void populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

	/**
	 * Adds a modifier to this populator to be called when the component is added
	 *
	 * @param modify The modifier
	 */
	void addModifier(ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modify);

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

		protected abstract void doPopulate(PanelPopulator<?, ?> panel, W quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException;

		@Override
		public void populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
			populate((ContainerPopulator<?, ?>) panel, quick);
		}

		protected <P extends ContainerPopulator<?, ?>> void populate(P panel, W quick) throws ModelInstantiationException {
			boolean[] modified = new boolean[1];
			try {
				doPopulate((PanelPopulator<?, ?>) panel, quick, comp -> {
					modified[0] = true;
					if (comp != null) {
						try {
							for (ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modifier : theModifiers)
								modifier.accept(comp, quick);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
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
		public void addModifier(ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modify) {
			theModifiers.add(modify);
		}
	}

	public interface QuickSwingContainerPopulator<W extends QuickWidget> extends QuickSwingPopulator<W> {
		void populateContainer(ContainerPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

		@Override
		default void populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
			populateContainer(panel, quick);
		}

		public abstract class Abstract<W extends QuickWidget> extends QuickSwingPopulator.Abstract<W>
		implements QuickSwingContainerPopulator<W> {
			protected abstract void doPopulateContainer(ContainerPopulator<?, ?> panel, W quick,
				Consumer<ComponentEditor<?, ?>> component)
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

	public interface WindowModifier<AO extends ExAddOn<?>> {
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

		void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException;
	}

	public interface QuickSwingBorder {
		void decorate(ComponentDecorator deco, QuickBorder border, Component[] component) throws ModelInstantiationException;
	}

	public interface QuickSwingEventListener<L extends QuickEventListener> {
		void addListener(Component c, L listener) throws ModelInstantiationException;
	}

	public interface QuickSwingDialog<D extends QuickDialog> {
		void initialize(D dialog, Component parent, Observable<?> until) throws ModelInstantiationException;
	}

	public interface QuickSwingDocument<T> {
		ObservableStyledDocument<T> interpret(StyledDocument<T> quickDoc, Observable<?> until) throws ModelInstantiationException;

		MouseAdapter mouseListener(StyledDocument<T> quickDoc, ObservableStyledDocument<T> doc, JTextComponent widget,
			Observable<?> until);

		CaretListener caretListener(StyledDocument<T> quickDoc, ObservableStyledDocument<T> doc, JTextComponent widget,
			Observable<?> until);
	}

	public interface QuickSwingTableAction<R, A extends ValueAction<R>> {
		void addAction(PanelPopulation.CollectionWidgetBuilder<R, ?, ?> table, A action) throws ModelInstantiationException;
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
