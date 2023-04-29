package org.observe.quick.swing;

import java.awt.EventQueue;
import java.awt.LayoutManager;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JFrame;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.QuickApplication;
import org.observe.quick.QuickDocument2;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWidget.Interpreted;
import org.observe.quick.QuickWindow;
import org.observe.quick.base.EditableTextWidget;
import org.observe.quick.base.InlineLayout;
import org.observe.quick.base.QuickBox;
import org.observe.quick.base.QuickField;
import org.observe.quick.base.QuickLabel;
import org.observe.quick.base.QuickLayout;
import org.observe.quick.base.QuickTextField;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.ContainerPopulator;
import org.observe.util.swing.PanelPopulation.FieldEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.observe.util.swing.WindowPopulation;
import org.qommons.Transformer;
import org.qommons.Transformer.Builder;
import org.qommons.collect.BetterList;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExTriConsumer;
import org.qommons.io.Format;

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
	ComponentEditor<?, ?> populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

	void addModifier(ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modify);

	public abstract class Abstract<W extends QuickWidget> implements QuickSwingPopulator<W> {
		private final QuickWidget.Interpreted<? extends W> theInterpreted;
		private final List<ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException>> theModifiers;

		public Abstract(QuickWidget.Interpreted<? extends W> interpreted) {
			theInterpreted = interpreted;
			theModifiers = new LinkedList<>();
		}

		public QuickWidget.Interpreted<? extends W> getInterpreted() {
			return theInterpreted;
		}

		protected abstract ComponentEditor<?, ?> doPopulate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

		@Override
		public ComponentEditor<?, ?> populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
			try {
				return modify(//
					doPopulate(panel, quick), quick);
			} catch (CheckedExceptionWrapper e) {
				if (e.getCause() instanceof ModelInstantiationException)
					throw (ModelInstantiationException) e.getCause();
				throw e;
			}
		}

		protected ComponentEditor<?, ?> modify(ComponentEditor<?, ?> component, W quick) throws ModelInstantiationException {
			for (ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modifier : theModifiers)
				modifier.accept(component, quick);
			return component;
		}

		@Override
		public void addModifier(ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modify) {
			theModifiers.add(modify);
		}
	}

	public interface QuickSwingContainerPopulator<W extends QuickWidget> extends QuickSwingPopulator<W> {
		ComponentEditor<?, ?> populateContainer(ContainerPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

		@Override
		default ComponentEditor<?, ?> populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
			return populateContainer(panel, quick);
		}

		public abstract class Abstract<W extends QuickWidget> extends QuickSwingPopulator.Abstract<W>
		implements QuickSwingContainerPopulator<W> {
			public Abstract(Interpreted<? extends W> interpreted) {
				super(interpreted);
			}

			protected abstract ComponentEditor<?, ?> doPopulateContainer(ContainerPopulator<?, ?> panel, W quick)
				throws ModelInstantiationException;

			@Override
			public ComponentEditor<?, ?> populateContainer(ContainerPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
				return modify(//
					doPopulateContainer(panel, quick), quick);
			}

			@Override
			protected ComponentEditor<?, ?> doPopulate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
				return doPopulateContainer(panel, quick);
			}
		}
	}

	/**
	 * Creates a Swing layout from a {@link QuickLayout}
	 *
	 * @param <L> The type of the Quick layout
	 */
	public interface QuickSwingLayout<L extends QuickLayout> {
		/**
		 * @param quick The Quick layout to create the layout for
		 * @return The swing layout interpretation of the Quick layout
		 * @throws ModelInstantiationException If a problem occurs instantiating the layout
		 */
		LayoutManager create(L quick) throws ModelInstantiationException;
	}

	/** Quick interpretation of the core toolkit for Swing */
	public class QuickCoreSwing implements QuickInterpretation {
		@Override
		public void configure(Builder<ExpressoInterpretationException> tx) {
			tx.with(QuickDocument2.Interpreted.class, QuickApplication.class, (interpretedDoc, tx2) -> {
				QuickSwingPopulator<QuickWidget> interpretedBody = tx2.transform(interpretedDoc.getBody(), QuickSwingPopulator.class);
				return doc -> {
					try {
						EventQueue.invokeAndWait(() -> {
							QuickWindow window = doc.getAddOn(QuickWindow.class);
							WindowBuilder<?, ?> w = WindowPopulation.populateWindow(new JFrame(), doc.getModels().getUntil(), true, true);
							if (window != null) {
								switch (window.getInterpreted().getDefinition().getCloseAction()) {
								case DoNothing:
									w.withCloseAction(JFrame.DO_NOTHING_ON_CLOSE);
									break;
								case Hide:
									w.withCloseAction(JFrame.HIDE_ON_CLOSE);
									break;
								case Dispose:
									w.withCloseAction(JFrame.DISPOSE_ON_CLOSE);
									break;
								case Exit:
									w.withCloseAction(JFrame.EXIT_ON_CLOSE);
									break;
								}
								if (window.getX() != null)
									w.withX(window.getX());
								if (window.getY() != null)
									w.withY(window.getY());
								if (window.getWidth() != null)
									w.withWidth(window.getWidth());
								if (window.getHeight() != null)
									w.withHeight(window.getHeight());
								if (window.getTitle() != null)
									w.withTitle(window.getTitle());
								if (window.getVisible() != null)
									w.withVisible(window.getVisible());
							}
							w.withHContent(new JustifiedBoxLayout(true).mainJustified().crossJustified(), content -> {
								try {
									interpretedBody.populate(content, doc.getBody());
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							});
							w.run(null);
						});
					} catch (InterruptedException e) {
					} catch (InvocationTargetException e) {
						if (e.getTargetException() instanceof CheckedExceptionWrapper
							&& e.getTargetException().getCause() instanceof ModelInstantiationException)
							throw (ModelInstantiationException) e.getTargetException().getCause();
					}
				};
			});
			modify(tx, QuickWidget.Interpreted.class, (qw, qsp, tx2) -> {
				qsp.addModifier((comp, w) -> {
					if (w.getTooltip() != null)
						comp.withTooltip(w.getTooltip());
					if (w.isVisible() != null)
						comp.visibleWhen(w.isVisible());
					// TODO Style
				});
			});
		}
	}

	/** Quick interpretation of the base toolkit for Swing */
	public class QuickBaseSwing implements QuickInterpretation {
		@Override
		public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
			QuickSwingPopulator.<QuickBox, QuickBox.Interpreted<?>> interpretContainer(tx, gen(QuickBox.Interpreted.class),
				QuickBaseSwing::interpretBox);
			tx.with(InlineLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretInlineLayout);
			QuickSwingPopulator.<QuickLabel<?>, QuickLabel.Interpreted<?, ?>> interpretWidget(tx,
				QuickBaseSwing.gen(QuickLabel.Interpreted.class), QuickBaseSwing::interpretLabel);
			QuickSwingPopulator.<QuickTextField<?>, QuickTextField.Interpreted<?>> interpretWidget(tx,
				QuickBaseSwing.gen(QuickTextField.Interpreted.class), QuickBaseSwing::interpretTextField);
		}

		static <T> Class<T> gen(Class<? super T> rawClass) {
			return (Class<T>) rawClass;
		}

		static QuickSwingContainerPopulator<QuickBox> interpretBox(QuickBox.Interpreted<?> interpreted,
			Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
			QuickSwingLayout<QuickLayout> layout = tx.transform(interpreted.getLayout(), QuickSwingLayout.class);
			BetterList<QuickSwingPopulator<QuickWidget>> contents = BetterList.<QuickWidget.Interpreted<?>, QuickSwingPopulator<QuickWidget>, ExpressoInterpretationException> of2(
				interpreted.getContents().stream(), content -> tx.transform(content, QuickSwingPopulator.class));
			return createContainer(interpreted, (panel, quick) -> {
				LayoutManager layoutInst = layout.create(quick.getLayout());
				QuickField field = quick.getAddOn(QuickField.class);
				PanelPopulator<?, ?>[] ret = new PanelPopulator[1];
				panel.addHPanel(null, layoutInst, p -> {
					ret[0] = p;
					if (field != null) {
						if (field.getName() != null)
							p.withFieldName(field.getName());
						if (field.getInterpreted().getDefinition().isFill())
							p.fill();
					}
					int c = 0;
					for (QuickWidget content : quick.getContents()) {
						try {
							contents.get(c).populate(p, content);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
						c++;
					}
				});
				return ret[0];
			});
		}

		static QuickSwingLayout<InlineLayout> interpretInlineLayout(InlineLayout.Interpreted interpreted,
			Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
			boolean vertical = interpreted.getDefinition().isVertical();
			return quick -> {
				JustifiedBoxLayout layout = new JustifiedBoxLayout(vertical)//
					.setMainAlignment(interpreted.getDefinition().getMainAlign())//
					.setCrossAlignment(interpreted.getDefinition().getCrossAlign());
				return layout;
			};
		}

		static <T> QuickSwingPopulator<QuickLabel<T>> interpretLabel(QuickLabel.Interpreted<T, ?> interpreted,
			Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
			return createWidget(interpreted, (panel, quick) -> {
				QuickField field = quick.getAddOn(QuickField.class);
				Format<T> format = quick.getFormat().get();
				FieldEditor<T, ?>[] editor = new FieldEditor[1];
				panel.addLabel(null, quick.getValue(), format, lbl -> {
					editor[0] = (FieldEditor<T, ?>) lbl;
					if (field != null) {
						if (field.getName() != null)
							lbl.withFieldName(field.getName());
						if (field.getInterpreted().getDefinition().isFill())
							lbl.fill();
					}
				});
				return editor[0];
			});
		}

		static <T> QuickSwingPopulator<QuickTextField<T>> interpretTextField(QuickTextField.Interpreted<T> interpreted,
			Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
			FieldEditor<T, ?>[] editor = new FieldEditor[1];
			return createWidget(interpreted, (panel, quick) -> {
				QuickField field = quick.getAddOn(QuickField.class);
				Format<T> format = quick.getFormat().get();
				boolean commitOnType = quick.getInterpreted().getDefinition().isCommitOnType();
				Integer columns = quick.getInterpreted().getDefinition().getColumns();
				panel.addTextField(null, quick.getValue(), format, tf -> {
					editor[0] = (FieldEditor<T, ?>) tf;
					if (field != null) {
						if (field.getName() != null)
							tf.withFieldName(field.getName());
						if (field.getInterpreted().getDefinition().isFill())
							tf.fill();
					}
					tf.modifyEditor(tf2 -> {
						try {
							quick.setContext(new EditableTextWidget.EditableWidgetContext.Default(//
								tf2.getErrorState(), tf2.getWarningState()));
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
						if (commitOnType)
							tf2.setCommitOnType(commitOnType);
						if (columns != null)
							tf2.withColumns(columns);
					});
				});
				return editor[0];
			});
		}
	}

	/**
	 * Utility for interpretation of swing widgets
	 *
	 * @param <W> The type of the Quick widget to interpret
	 * @param <I> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param interpretedType The type of the interpreted quick widget
	 * @param interpreter Produces a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> void interpretWidget(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<I> interpretedType,
		ExBiFunction<? super I, Transformer<ExpressoInterpretationException>, ? extends QuickSwingPopulator<? extends W>, ExpressoInterpretationException> interpreter) {
		transformer.with(interpretedType, QuickSwingPopulator.class, interpreter);
	}

	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> QuickSwingPopulator<W> createWidget(I interpreted,
		ExBiFunction<PanelPopulator<?, ?>, W, ComponentEditor<?, ?>, ModelInstantiationException> populator) {
		return new Abstract<W>(interpreted) {
			@Override
			protected ComponentEditor<?, ?> doPopulate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
				return populator.apply(panel, quick);
			}
		};
	}

	/**
	 * Utility for interpretation of swing widgets
	 *
	 * @param <W> The type of the Quick widget to interpret
	 * @param <I> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param interpretedType The type of the interpreted quick widget
	 * @param interpreter Produces a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> void interpretContainer(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<I> interpretedType,
		ExBiFunction<? super I, Transformer<ExpressoInterpretationException>, ? extends QuickSwingContainerPopulator<? extends W>, ExpressoInterpretationException> interpreter) {
		transformer.with(interpretedType, QuickSwingContainerPopulator.class, interpreter);
	}

	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> QuickSwingContainerPopulator<W> createContainer(
		I interpreted, ExBiFunction<ContainerPopulator<?, ?>, W, ComponentEditor<?, ?>, ModelInstantiationException> populator) {
		return new QuickSwingContainerPopulator.Abstract<W>(interpreted) {
			@Override
			protected ComponentEditor<?, ?> doPopulateContainer(ContainerPopulator<?, ?> panel, W quick)
				throws ModelInstantiationException {
				return populator.apply(panel, quick);
			}
		};
	}

	/**
	 * Utility for interpretation of swing widgets
	 *
	 * @param <W> The type of the Quick widget to interpret
	 * @param <I> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param interpretedType The type of the interpreted quick widget
	 * @param modifier Modifies a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> void modify(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<I> interpretedType,
		ExTriConsumer<? super I, QuickSwingPopulator<W>, Transformer<ExpressoInterpretationException>, ExpressoInterpretationException> modifier) {
		transformer.modifyWith(interpretedType, (Class<QuickSwingPopulator<W>>) (Class<?>) QuickSwingPopulator.class, new Transformer.Modifier<I, QuickSwingPopulator<W>, ExpressoInterpretationException>(){
			@Override
			public <T2 extends QuickSwingPopulator<W>> T2 modify(I source, T2 value, Transformer<ExpressoInterpretationException> tx)
				throws ExpressoInterpretationException {
				modifier.accept(source, value, tx);
				return value;
			}
		});
	}
}
