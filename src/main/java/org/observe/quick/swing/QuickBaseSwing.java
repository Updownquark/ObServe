package org.observe.quick.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.EventQueue;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickAbstractWindow;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickTextWidget;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWindow;
import org.observe.quick.base.*;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingContainerPopulator;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingDialog;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingDocument;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingLayout;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingTableAction;
import org.observe.quick.swing.QuickSwingTablePopulation.InterpretedSwingTableColumn;
import org.observe.util.TypeTokens;
import org.observe.util.swing.BgFontAdjuster;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.ObservableStyledDocument;
import org.observe.util.swing.ObservableTextArea;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.ContainerPopulator;
import org.observe.util.swing.PanelPopulation.FieldEditor;
import org.observe.util.swing.PanelPopulation.MenuBarBuilder;
import org.observe.util.swing.PanelPopulation.MenuBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.observe.util.swing.WindowPopulation;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiFunction;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/** Quick interpretation of the base toolkit for Swing */
public class QuickBaseSwing implements QuickInterpretation {
	public interface QuickSwingMenuBarPopulator<E extends ExElement> {
		void populateMenuBar(PanelPopulation.MenuBarBuilder<?> menuBar, E quick) throws ModelInstantiationException;
	}

	public interface QuickSwingMenuPopulator<E extends ExElement> {
		void populateMenu(PanelPopulation.MenuBuilder<?, ?> menu, E quick) throws ModelInstantiationException;
	}

	@Override
	public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
		// Simple widgets
		tx.with(QuickLabel.Interpreted.class, QuickSwingPopulator.class, widget(SwingLabel::new));
		tx.with(QuickSpacer.Interpreted.class, QuickSwingPopulator.class, (i, tx2) -> new SwingSpacer());
		tx.with(QuickProgressBar.Interpreted.class, QuickSwingPopulator.class, (i, tx2) -> new SwingProgressBar());
		tx.with(QuickTextField.Interpreted.class, QuickSwingPopulator.class, widget(SwingTextField::new));
		tx.with(QuickCheckBox.Interpreted.class, QuickSwingPopulator.class, widget(SwingCheckBox::new));
		tx.with(QuickRadioButton.Interpreted.class, QuickSwingPopulator.class, widget(SwingRadioButton::new));
		tx.with(QuickToggleButton.Interpreted.class, QuickSwingPopulator.class, widget(SwingToggleButton::new));
		tx.with(QuickButton.Interpreted.class, QuickSwingPopulator.class, widget(SwingButton::new));
		tx.with(QuickFileButton.Interpreted.class, QuickSwingPopulator.class, widget(SwingFileButton::new));
		tx.with(QuickComboBox.Interpreted.class, QuickSwingPopulator.class, SwingComboBox::new);
		tx.with(QuickRadioButtons.Interpreted.class, QuickSwingPopulator.class, widget(SwingRadioButtons::new));
		tx.with(QuickTextArea.Interpreted.class, QuickSwingPopulator.class, SwingTextArea::new);
		tx.with(DynamicStyledDocument.Interpreted.class, QuickSwingDocument.class,
			(qd, tx2) -> QuickBaseSwing.interpretDynamicStyledDoc(qd, tx2));

		// Containers
		tx.with(QuickBox.Interpreted.class, QuickSwingContainerPopulator.class, SwingBox::new);
		tx.with(QuickFieldPanel.Interpreted.class, QuickSwingContainerPopulator.class, SwingFieldPanel::new);
		QuickSwingPopulator.<QuickWidget, QuickField, QuickField.Interpreted> modifyForAddOn(tx, QuickField.Interpreted.class,
			(Class<QuickWidget.Interpreted<QuickWidget>>) (Class<?>) QuickWidget.Interpreted.class, (ao, qsp, tx2) -> {
				qsp.addModifier((comp, w) -> {
					if (w.getAddOn(QuickField.class).getFieldLabel() != null)
						comp.withFieldName(w.getAddOn(QuickField.class).getFieldLabel());
					if (ao.getDefinition().isFill())
						comp.fill();
				});
			});
		tx.with(QuickSplit.Interpreted.class, QuickSwingContainerPopulator.class, SwingSplit::new);
		tx.with(QuickScrollPane.Interpreted.class, QuickSwingContainerPopulator.class, SwingScroll::new);

		// Box layouts
		tx.with(QuickInlineLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretInlineLayout);
		tx.with(QuickSimpleLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretSimpleLayout);
		tx.with(QuickBorderLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretBorderLayout);

		// Table
		tx.with(QuickTable.Interpreted.class, QuickSwingPopulator.class, SwingTable::new);
		tx.with(ValueAction.Single.Interpreted.class, QuickSwingTableAction.class, QuickSwingTablePopulation::interpretValueAction);
		tx.with(ValueAction.Multi.Interpreted.class, QuickSwingTableAction.class, QuickSwingTablePopulation::interpretMultiValueAction);

		// Tabs
		tx.with(QuickTabs.Interpreted.class, QuickSwingContainerPopulator.class, SwingTabs::new);
		// Tree
		tx.with(QuickTree.Interpreted.class, QuickSwingPopulator.class, SwingTree::new);

		// Dialogs
		tx.with(QuickInfoDialog.Interpreted.class, QuickSwingDialog.class, QuickBaseSwing::interpretInfoDialog);
		tx.with(QuickConfirm.Interpreted.class, QuickSwingDialog.class, QuickBaseSwing::interpretConfirm);
		tx.with(QuickFileChooser.Interpreted.class, QuickSwingDialog.class, QuickBaseSwing::interpretFileChooser);
		tx.with(GeneralDialog.Interpreted.class, QuickSwingDialog.class, QuickBaseSwing::interpretGeneralDialog);

		// Menus
		tx.with(QuickMenuContainer.Interpreted.class, QuickSwingPopulator.WindowModifier.class,
			(quick, tx2) -> new SwingMenuContainer(quick, tx2));
		tx.with(QuickMenu.Interpreted.class, QuickSwingMenuBarPopulator.class, (quick, tx2) -> new SwingMenu<>(quick, tx2));
		tx.with(QuickMenu.Interpreted.class, QuickSwingMenuPopulator.class, (quick, tx2) -> new SwingSubMenu<>(quick, tx2));
		tx.with(QuickMenuItem.Interpreted.class, QuickSwingMenuPopulator.class, (quick, tx2) -> new SwingMenuItem<>());
		tx.with(QuickCheckBoxMenuItem.Interpreted.class, QuickSwingMenuPopulator.class, (quick, tx2) -> new SwingCheckBoxMenuItem<>());
	}

	public static <W extends QuickWidget, GW extends W, I extends QuickWidget.Interpreted<W>, GI extends QuickWidget.Interpreted<GW>> //
	ExBiFunction<I, Transformer<ExpressoInterpretationException>, QuickSwingPopulator<W>, ExpressoInterpretationException> widget(
		Supplier<QuickSwingPopulator<GW>> ctor) {
		return (i, tx) -> (QuickSwingPopulator<W>) ctor.get();
	}

	static class SwingMenuContainer implements QuickSwingPopulator.WindowModifier<QuickMenuContainer> {
		private final List<QuickSwingMenuBarPopulator<?>> theMenuPopulators;

		SwingMenuContainer(QuickMenuContainer.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theMenuPopulators = new ArrayList<>();
			QuickMenuBar.Interpreted menuBar = interpreted.getMenuBar();
			if (menuBar != null) {
				for (QuickMenu.Interpreted<?, ?> menu : menuBar.getMenus())
					theMenuPopulators.add(tx.transform(menu, QuickSwingMenuBarPopulator.class));
			}
		}

		@Override
		public void modifyWindow(WindowBuilder<?, ?> window, QuickMenuContainer quick) throws ModelInstantiationException {
			if (!theMenuPopulators.isEmpty()) {
				try {
					window.withMenuBar(menuBar -> {
						try {
							for (int m = 0; m < theMenuPopulators.size(); m++)
								((QuickSwingMenuBarPopulator<ExElement>) theMenuPopulators.get(m)).populateMenuBar(menuBar,
									quick.getMenuBar().getMenus().get(m));
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					});
				} catch (CheckedExceptionWrapper e) {
					throw CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
				}
			}
		}
	}

	static class SwingMenu<T> implements QuickSwingMenuBarPopulator<QuickMenu<T>> {
		private final List<QuickSwingMenuPopulator<?>> theMenuItemPopulators;

		SwingMenu(QuickMenu.Interpreted<T, ?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theMenuItemPopulators = new ArrayList<>();
			for (QuickAbstractMenuItem.Interpreted<?, ?> menu : interpreted.getMenuItems())
				theMenuItemPopulators.add(tx.transform(menu, QuickSwingMenuPopulator.class));
		}

		@Override
		public void populateMenuBar(MenuBarBuilder<?> menuBar, QuickMenu<T> quick) throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			if (format == null)
				format = (Format<T>) QuickTextWidget.TO_STRING_FORMAT;
			Format<T> fFormat = format;
			try {
				menuBar.withMenu(null, menu -> {
					menu.withText(quick.getValue().map(fFormat::format));
					if (quick.getIcon() != null)
						menu.withIcon(quick.getIcon());

					try {
						for (int m = 0; m < theMenuItemPopulators.size(); m++)
							((QuickSwingMenuPopulator<ExElement>) theMenuItemPopulators.get(m)).populateMenu(menu,
								quick.getMenuItems().get(m));
					} catch (ModelInstantiationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				});
			} catch (CheckedExceptionWrapper e) {
				throw CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
			}
		}
	}

	static class SwingMenuItem<T> implements QuickSwingMenuPopulator<QuickMenuItem<T>> {
		@Override
		public void populateMenu(MenuBuilder<?, ?> menu, QuickMenuItem<T> quick) throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			if (format == null)
				format = (Format<T>) QuickTextWidget.TO_STRING_FORMAT;
			Format<T> fFormat = format;
			menu.withAction(null, quick.getAction(), menuItem -> {
				menuItem.withText(quick.getValue().map(fFormat::format));
				if (quick.getIcon() != null)
					menuItem.withIcon(quick.getIcon());
			});
		}
	}

	static class SwingSubMenu<T> implements QuickSwingMenuPopulator<QuickMenu<T>> {
		private final List<QuickSwingMenuPopulator<?>> theMenuItemPopulators;

		SwingSubMenu(QuickMenu.Interpreted<T, ?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theMenuItemPopulators = new ArrayList<>();
			for (QuickAbstractMenuItem.Interpreted<?, ?> menu : interpreted.getMenuItems())
				theMenuItemPopulators.add(tx.transform(menu, QuickSwingMenuPopulator.class));
		}

		@Override
		public void populateMenu(MenuBuilder<?, ?> menu, QuickMenu<T> quick) throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			if (format == null)
				format = (Format<T>) QuickTextWidget.TO_STRING_FORMAT;
			Format<T> fFormat = format;
			try {
				menu.withSubMenu(null, subMenu -> {
					subMenu.withText(quick.getValue().map(fFormat::format));
					if (quick.getIcon() != null)
						subMenu.withIcon(quick.getIcon());

					try {
						for (int m = 0; m < theMenuItemPopulators.size(); m++)
							((QuickSwingMenuPopulator<ExElement>) theMenuItemPopulators.get(m)).populateMenu(subMenu,
								quick.getMenuItems().get(m));
					} catch (ModelInstantiationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				});
			} catch (CheckedExceptionWrapper e) {
				throw CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
			}
		}
	}

	static class SwingCheckBoxMenuItem<T> implements QuickSwingMenuPopulator<QuickCheckBoxMenuItem<T>> {
		@Override
		public void populateMenu(MenuBuilder<?, ?> menu, QuickCheckBoxMenuItem<T> quick) throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			if (format == null)
				format = (Format<T>) QuickTextWidget.TO_STRING_FORMAT;
			Format<T> fFormat = format;
			menu.withCheckBoxMenuItem(null, quick.isSelected(), menuItem -> {
				menuItem.withText(quick.getValue().map(fFormat::format));
				if (quick.getIcon() != null)
					menuItem.withIcon(quick.getIcon());
			});
		}
	}

	static class SwingBox extends QuickSwingPopulator.QuickSwingContainerPopulator.Abstract<QuickBox> {
		private final QuickSwingLayout<QuickLayout> theLayout;
		private final List<QuickSwingPopulator<QuickWidget>> theContents;

		SwingBox(QuickBox.Interpreted<?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theLayout = tx.transform(interpreted.getLayout(), QuickSwingLayout.class);
			theContents = BetterList.<QuickWidget.Interpreted<?>, QuickSwingPopulator<QuickWidget>, ExpressoInterpretationException> of2(
				interpreted.getContents().stream(), content -> tx.transform(content, QuickSwingPopulator.class));
			for (QuickSwingPopulator<QuickWidget> content : theContents)
				theLayout.modifyChild(content);
		}

		@Override
		protected void doPopulateContainer(ContainerPopulator<?, ?> panel, QuickBox quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			LayoutManager layoutInst = theLayout.create(quick.getLayout());
			panel.addHPanel(null, layoutInst, p -> {
				component.accept(p);
				int c = 0;
				for (QuickWidget content : quick.getContents()) {
					try {
						theContents.get(c).populate(p, content);
					} catch (ModelInstantiationException e) {
						content.reporting().error(e.getMessage(), e);
					}
					c++;
				}
			});
		}
	}

	static QuickSwingLayout<QuickInlineLayout> interpretInlineLayout(QuickInlineLayout.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return new QuickSwingLayout<QuickInlineLayout>() {
			@Override
			public LayoutManager create(QuickInlineLayout quick) throws ModelInstantiationException {
				return new JustifiedBoxLayout(quick.isVertical())//
					.setMainAlignment(quick.getMainAlign())//
					.setCrossAlignment(quick.getCrossAlign())//
					.setPadding(quick.getPadding());
			}

			@Override
			public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
			}
		};
	}

	static QuickSwingLayout<QuickSimpleLayout> interpretSimpleLayout(QuickSimpleLayout.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return new QuickSwingLayout<QuickSimpleLayout>() {
			@Override
			public LayoutManager create(QuickSimpleLayout quick) throws ModelInstantiationException {
				return new SimpleLayout();
			}

			@Override
			public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
				child.addModifier((comp, w) -> {
					Component[] component = new Component[1];
					comp.modifyComponent(c -> component[0] = c);
					Positionable h = w.getAddOn(Positionable.Horizontal.class);
					Positionable v = w.getAddOn(Positionable.Vertical.class);
					Sizeable width = w.getAddOn(Sizeable.Horizontal.class);
					Sizeable height = w.getAddOn(Sizeable.Vertical.class);
					SimpleLayout.SimpleConstraints childConstraint = simpleConstraints(h, v, width, height);
					comp.withLayoutConstraints(childConstraint);
					Observable.or(h.changes(), v.changes(), width.changes(), height.changes()).act(evt -> {
						if (component[0].getParent() != null)
							component[0].getParent().invalidate();
					});
				});
			}
		};
	}

	static SimpleLayout.SimpleConstraints simpleConstraints(Positionable h, Positionable v, Sizeable width, Sizeable height) {
		return new SimpleLayout.SimpleConstraints(//
			h.getLeading(), h.getCenter(), h.getTrailing(), //
			v.getLeading(), v.getCenter(), v.getTrailing(), //
			width.getSize(), enforceAbsolute(width.getMinimum()), enforceAbsolute(width.getPreferred()),
			enforceAbsolute(width.getMaximum()), //
			height.getSize(), enforceAbsolute(height.getMinimum()), enforceAbsolute(height.getPreferred()),
			enforceAbsolute(height.getMaximum())//
			);
	}

	static Supplier<Integer> enforceAbsolute(Supplier<QuickSize> size) {
		if (size == null)
			return LambdaUtils.constantSupplier(null, "null", null);
		return LambdaUtils.printableSupplier(() -> {
			QuickSize sz = size.get();
			if (sz == null)
				return null;
			else if (sz.percent == 0.0f)
				return sz.pixels;
			else {
				System.err.println("min/pref/max size constraints must be absolute: " + sz);
				return null;
			}
		}, size::toString, null);
	}

	static QuickSwingLayout<QuickBorderLayout> interpretBorderLayout(QuickBorderLayout.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return new QuickSwingLayout<QuickBorderLayout>() {
			@Override
			public LayoutManager create(QuickBorderLayout quick) throws ModelInstantiationException {
				return new BorderLayout();
			}

			@Override
			public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
				child.addModifier((comp, w) -> {
					QuickBorderLayout.Region region = w.getAddOn(QuickBorderLayout.Child.class).getRegion();
					Component[] component = new Component[1];
					comp.modifyComponent(c -> component[0] = c);
					Sizeable size = w.getAddOn(Sizeable.class);
					BorderLayout.Constraints childConstraint = borderConstraints(region, size);
					comp.withLayoutConstraints(childConstraint);
					if (size != null) {
						size.changes().act(evt -> {
							if (component[0].getParent() != null)
								component[0].getParent().invalidate();
						});
					}
				});
			}
		};
	}

	static BorderLayout.Constraints borderConstraints(QuickBorderLayout.Region region, Sizeable size) {
		if (size == null)
			return new BorderLayout.Constraints(region, null, null, null, null);
		return new BorderLayout.Constraints(region, //
			size.getSize(), enforceAbsolute(size.getMinimum()), enforceAbsolute(size.getPreferred()), enforceAbsolute(size.getMaximum()));
	}

	static class SwingSpacer extends QuickSwingPopulator.Abstract<QuickSpacer> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickSpacer quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.spacer(quick.getLength(), sp -> component.accept(sp));
		}
	}

	static class SwingLabel<T> extends QuickSwingPopulator.Abstract<QuickLabel<T>> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickLabel<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			if (format == null)
				format = (Format<T>) QuickTextWidget.TO_STRING_FORMAT;
			panel.addLabel(null, quick.getValue(), format, lbl -> {
				component.accept(lbl);
				if (quick.getIcon() != null)
					lbl.withIcon(quick.getIcon());
			});
		}
	}

	static class SwingProgressBar extends QuickSwingPopulator.Abstract<QuickProgressBar> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickProgressBar quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addProgressBar(null, progress -> {
				component.accept(progress);
				progress.withTaskLength(quick.getMaximum());
				progress.withProgress(quick.getValue());
				progress.withProgressText(quick.getText());
			});
		}
	}

	static class SwingTextField<T> extends QuickSwingPopulator.Abstract<QuickTextField<T>> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTextField<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			boolean commitOnType = quick.isCommitOnType();
			Integer columns = quick.getColumns();
			panel.addTextField(null, quick.getValue(), format, tf -> {
				component.accept(tf);
				tf.modifyEditor(tf2 -> {
					try {
						quick.setContext(new QuickEditableTextWidget.EditableTextWidgetContext.Default(//
							tf2.getErrorState(), tf2.getWarningState()));
					} catch (ModelInstantiationException e) {
						quick.reporting().error(e.getMessage(), e);
						return;
					}
					if (commitOnType)
						tf2.setCommitOnType(commitOnType);
					if (columns != null)
						tf2.withColumns(columns);
					quick.isPassword().changes().takeUntil(tf.getUntil()).act(evt -> tf2.asPassword(evt.getNewValue() ? '*' : (char) 0));
					quick.getEmptyText().changes().takeUntil(tf.getUntil()).act(evt -> tf2.setEmptyText(evt.getNewValue()));
					quick.isEditable().changes().takeUntil(tf.getUntil())
					.act(evt -> tf2.setEditable(!Boolean.FALSE.equals(evt.getNewValue())));
				});
			});
		}
	}

	static class SwingTextArea<T> extends QuickSwingPopulator.Abstract<QuickTextArea<T>> {
		private final QuickSwingDocument<T> theDocument;

		SwingTextArea(QuickTextArea.Interpreted<T> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theDocument = tx.transform(interpreted.getDocument(), QuickSwingDocument.class);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTextArea<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			Format<T> format = quick.getFormat().get();
			boolean commitOnType = quick.isCommitOnType();
			SettableValue<Integer> rows = quick.getRows();
			Consumer<FieldEditor<ObservableTextArea<T>, ?>> modifier = tf -> {
				component.accept(tf);
				tf.modifyEditor(tf2 -> {
					if (tf2.getErrorState() != null) {
						try {
							quick.setContext(new QuickEditableTextWidget.EditableTextWidgetContext.Default(//
								tf2.getErrorState(), tf2.getWarningState()));
						} catch (ModelInstantiationException e) {
							quick.reporting().error(e.getMessage(), e);
							return;
						}
					}
					if (commitOnType)
						tf2.setCommitOnType(commitOnType);
					rows.changes().takeUntil(tf.getUntil()).act(evt -> tf2.withRows(evt.getNewValue()));
					QuickTextArea.QuickTextAreaContext ctx = new QuickTextArea.QuickTextAreaContext.Default();
					tf2.addMouseListener(pos -> ctx.getMousePosition().set(pos, null));
					quick.setTextAreaContext(ctx);
					quick.isEditable().changes().takeUntil(tf.getUntil())
					.act(evt -> tf2.setEditable(!Boolean.FALSE.equals(evt.getNewValue())));
				});
			};
			if (theDocument != null) {
				ObservableStyledDocument<T> docInst = theDocument.interpret(quick.getDocument(), panel.getUntil());
				panel.addStyledTextArea(null, docInst, tf -> {
					modifier.accept(tf);
					tf.modifyEditor(tf2 -> {
						tf2.addMouseMotionListener(theDocument.mouseListener(quick.getDocument(), docInst, tf2, tf.getUntil()));
						tf2.addCaretListener(theDocument.caretListener(quick.getDocument(), docInst, tf2, tf.getUntil()));
					});
				});
			} else {
				panel.addTextArea(null, quick.getValue(), format, tf -> {
					modifier.accept(tf);
					tf.modifyEditor(tf2 -> {
						try {
							quick.setContext(new QuickEditableTextWidget.EditableTextWidgetContext.Default(//
								tf2.getErrorState(), tf2.getWarningState()));
						} catch (ModelInstantiationException e) {
							quick.reporting().error(e.getMessage(), e);
							return;
						}
						if (commitOnType)
							tf2.setCommitOnType(commitOnType);
						rows.changes().takeUntil(tf.getUntil()).act(evt -> tf2.withRows(evt.getNewValue()));
						QuickTextArea.QuickTextAreaContext ctx = new QuickTextArea.QuickTextAreaContext.Default();
						tf2.addMouseListener(pos -> ctx.getMousePosition().set(pos, null));
						quick.setTextAreaContext(ctx);
						quick.isEditable().changes().takeUntil(tf.getUntil())
						.act(evt -> tf2.setEditable(!Boolean.FALSE.equals(evt.getNewValue())));
					});
				});
			}
		}
	}

	static <T> QuickSwingDocument<T> interpretDynamicStyledDoc(DynamicStyledDocument.Interpreted<T, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		TypeToken<T> valueType = interpreted.getValueType();
		return new QuickSwingDocument<T>() {
			@Override
			public ObservableStyledDocument<T> interpret(StyledDocument<T> quickDoc, Observable<?> until)
				throws ModelInstantiationException {
				DynamicStyledDocument<T> doc = (DynamicStyledDocument<T>) quickDoc;
				Format<T> format = doc.getFormat();
				ObservableStyledDocument<T> swingDoc = new ObservableStyledDocument<T>(doc.getRoot(), format, ThreadConstraint.EDT, until) {
					@Override
					protected ObservableCollection<? extends T> getChildren(T value) {
						try {
							return doc.getChildren(staCtx(valueType, value));
						} catch (ModelInstantiationException e) {
							doc.reporting().error(e.getMessage(), e);
							return ObservableCollection.of(valueType);
						}
					}

					@Override
					protected void adjustStyle(T value, BgFontAdjuster style) {
						StyledDocument.TextStyle textStyle;
						try {
							textStyle = doc.getStyle(staCtx(valueType, value));
						} catch (ModelInstantiationException e) {
							doc.reporting().error(e.getMessage(), e);
							return;
						}
						if (textStyle != null) {
							QuickCoreSwing.adjustFont(style, textStyle);
							Color bg = textStyle.getBackground().get();
							if (bg != null)
								style.withBackground(bg);
						}
					}
				};
				if (doc.hasPostText()) {
					swingDoc.withPostNodeText(node -> {
						try {
							return doc.getPostText(staCtx(valueType, node)).get();
						} catch (ModelInstantiationException e) {
							doc.reporting().error(e.getMessage(), e);
							return null;
						}
					});
				}
				return swingDoc;
			}

			@Override
			public MouseMotionListener mouseListener(StyledDocument<T> quickDoc, ObservableStyledDocument<T> doc, JTextComponent widget,
				Observable<?> until) {
				return new MouseAdapter() {
					@Override
					public void mouseMoved(MouseEvent e) {
						int docPos = widget.viewToModel(e.getPoint());
						ObservableStyledDocument<T>.DocumentNode node = doc.getNodeAt(docPos);
						((DynamicStyledDocument<T>) quickDoc).getNodeValue().set(node == null ? null : node.getValue(), e);
					}
				};
			}

			@Override
			public CaretListener caretListener(StyledDocument<T> quickDoc, ObservableStyledDocument<T> doc, JTextComponent widget,
				Observable<?> until) {
				boolean[] selectionCallbackLock = new boolean[1];
				SettableValue<T> selectionStartValue = quickDoc.getSelectionStartValue();
				SettableValue<Integer> selectionStartOffset = quickDoc.getSelectionStartOffset();
				SettableValue<T> selectionEndValue = quickDoc.getSelectionEndValue();
				SettableValue<Integer> selectionEndOffset = quickDoc.getSelectionEndOffset();
				Observable.onRootFinish(Observable.or(selectionStartValue.noInitChanges(), selectionStartOffset.noInitChanges(),
					selectionEndValue.noInitChanges(), selectionEndOffset.noInitChanges())).act(__ -> {
						if (selectionCallbackLock[0])
							return;
						selectionCallbackLock[0] = true;
						try {
							T sv = selectionStartValue.get();
							T ev = selectionEndValue.get();
							ObservableStyledDocument<T>.DocumentNode startNode = sv == null ? null : doc.getNodeFor(sv);
							if (sv == null) {
								widget.setCaretPosition(0);
								return;
							}
							ObservableStyledDocument<T>.DocumentNode endNode = sv == null ? null : doc.getNodeFor(ev);
							int startIndex = startNode.getStart() + selectionStartOffset.get();
							if (startIndex < 0)
								startIndex = 0;
							else if (startIndex > widget.getDocument().getLength())
								startIndex = widget.getDocument().getLength();
							widget.setCaretPosition(startIndex);
							Rectangle selectionBounds;
							try {
								selectionBounds = widget.modelToView(startIndex);
							} catch (BadLocationException e) {
								quickDoc.reporting().error(e.getMessage(), e);
								selectionBounds = null;
							}
							if (endNode != null) {
								int endIndex = endNode.getStart() + selectionEndOffset.get();
								if (endIndex >= 0 && endIndex <= widget.getDocument().getLength())
									widget.select(Math.min(startIndex, endIndex), Math.max(startIndex, endIndex));
								Rectangle end;
								try {
									end = widget.modelToView(endIndex);
									if (selectionBounds == null)
										selectionBounds = end;
									else
										selectionBounds = selectionBounds.union(end);
								} catch (BadLocationException e) {
									quickDoc.reporting().error(e.getMessage(), e);
								}
							}
							if (selectionBounds != null) {
								QuickCoreSwing.scrollTo(widget, selectionBounds);
							}
						} finally {
							selectionCallbackLock[0] = false;
						}
					});
				return e -> {
					if (selectionCallbackLock[0])
						return;
					int selStart = Math.min(e.getDot(), e.getMark());
					int selEnd = Math.max(e.getDot(), e.getMark());
					ObservableStyledDocument<T>.DocumentNode startNode = doc.getNodeAt(selStart);
					ObservableStyledDocument<T>.DocumentNode endNode = doc.getNodeAt(selEnd);
					if (selectionStartValue.isAcceptable(startNode == null ? null : startNode.getValue()) == null) {
						int startOffset = startNode == null ? 0 : selStart - startNode.getStart();
						int endOffset = endNode == null ? 0 : selEnd - endNode.getStart();
						selectionCallbackLock[0] = true;
						try (Causable.CausableInUse cause = Causable.cause(e);
							Transaction svt = selectionStartValue.lock(true, cause);
							Transaction sot = selectionStartOffset.lock(true, cause);
							Transaction evt = selectionEndValue.lock(true, cause);
							Transaction eot = selectionEndOffset.lock(true, cause)) {
							selectionStartValue.set(startNode == null ? null : startNode.getValue(), cause);
							if (selectionStartOffset.isAcceptable(startOffset) == null)
								selectionStartOffset.set(startOffset, cause);
							if (selectionEndValue.isAcceptable(endNode == null ? null : endNode.getValue()) == null) {
								selectionEndValue.set(endNode == null ? null : endNode.getValue(), cause);
								if (selectionEndOffset.isAcceptable(endOffset) == null)
									selectionEndOffset.set(endOffset, cause);
							}
						} finally {
							selectionCallbackLock[0] = false;
						}
					}
				};
			}
		};
	}

	static <T> DynamicStyledDocument.StyledTextAreaContext<T> staCtx(TypeToken<T> type, T value) {
		return new DynamicStyledDocument.StyledTextAreaContext.Default<>(SettableValue.of(type, value, "Unmodifiable"));
	}

	static class SwingCheckBox extends QuickSwingPopulator.Abstract<QuickCheckBox> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickCheckBox quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addCheckField(null, quick.getValue(), cb -> {
				cb.withText(quick.getText());
				component.accept(cb);
			});
		}
	}

	static class SwingRadioButton extends QuickSwingPopulator.Abstract<QuickRadioButton> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickRadioButton quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addRadioButton(null, quick.getValue(), cb -> {
				cb.withText(quick.getText());
				component.accept(cb);
			});
		}
	}

	static class SwingToggleButton extends QuickSwingPopulator.Abstract<QuickToggleButton> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickToggleButton quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addToggleButton(null, quick.getValue(), null, cb -> {
				component.accept(cb);
				cb.withText(quick.getText());
				cb.withIcon(quick.getIcon());
			});
		}
	}

	static class SwingFieldPanel extends QuickSwingContainerPopulator.Abstract<QuickFieldPanel> {
		private BetterList<QuickSwingPopulator<QuickWidget>> theContents;

		SwingFieldPanel(QuickFieldPanel.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theContents = BetterList.<QuickWidget.Interpreted<?>, QuickSwingPopulator<QuickWidget>, ExpressoInterpretationException> of2(
				interpreted.getContents().stream(), content -> tx.transform(content, QuickSwingPopulator.class));
		}

		@Override
		protected void doPopulateContainer(ContainerPopulator<?, ?> panel, QuickFieldPanel quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addVPanel(p -> {
				component.accept(p);
				int c = 0;
				for (QuickWidget content : quick.getContents()) {
					try {
						theContents.get(c).populate(p, content);
					} catch (ModelInstantiationException e) {
						content.reporting().error(e.getMessage(), e);
					}
					c++;
				}
			});
		}
	}

	static class SwingButton extends QuickSwingPopulator.Abstract<QuickButton> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickButton quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addButton(null, quick.getAction(), btn -> {
				component.accept(btn);
				if (quick.getText() != null)
					btn.withText(quick.getText());
				if (quick.getIcon() != null)
					btn.withIcon(quick.getIcon());
			});
		}
	}

	static class SwingFileButton extends QuickSwingPopulator.Abstract<QuickFileButton> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickFileButton quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addFileField(null, quick.getValue(), quick.isOpen(), fb -> component.accept(fb));
		}
	}

	static class SwingComboBox<T> extends QuickSwingPopulator.Abstract<QuickComboBox<T>> {
		private QuickSwingPopulator<QuickWidget> theRenderer;

		SwingComboBox(QuickComboBox.Interpreted<T> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			if (interpreted.getRenderer() != null)
				theRenderer = tx.transform(interpreted.getRenderer(), QuickSwingPopulator.class);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickComboBox<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			ComponentEditor<?, ?>[] combo = new ComponentEditor[1];
			TabularWidget.TabularContext<T> tableCtx = new TabularWidget.TabularContext.Default<>(quick.getValue().getType(),
				quick.toString());
			quick.setContext(tableCtx);
			ObservableCellRenderer<T, T> renderer = theRenderer == null ? null : new QuickSwingTablePopulation.QuickSwingRenderer<>(quick,
				quick.getValue().getType(), quick.getValue(), quick.getRenderer(), tableCtx, () -> combo[0], theRenderer);
			panel.addComboField(null, quick.getValue(), quick.getValues(), cf -> {
				combo[0] = cf;
				component.accept(cf);
				if (theRenderer != null)
					cf.renderWith(renderer);
			});
		}
	}

	static class SwingRadioButtons<T> extends QuickSwingPopulator.Abstract<QuickRadioButtons<T>> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickRadioButtons<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addRadioField(null, quick.getValue(), quick.getValues(), rf -> component.accept(rf));
		}
	}

	static class SwingTable<R> extends QuickSwingPopulator.Abstract<QuickTable<R>> {
		private final TypeToken<R> rowType;
		private final Map<Object, QuickSwingPopulator<QuickWidget>> renderers = new HashMap<>();
		private final Map<Object, QuickSwingPopulator<QuickWidget>> editors = new HashMap<>();
		private boolean renderersInitialized;
		private boolean tableInitialized;
		private final List<QuickSwingTableAction<R, ?>> interpretedActions;

		SwingTable(QuickTable.Interpreted<R> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			rowType = interpreted.getValueType();
			Subscription sub;
			try {
				sub = interpreted.getColumns().subscribe(evt -> {
					boolean renderer = false;
					try {
						switch (evt.getType()) {
						case add:
							renderer = true;
							if (evt.getNewValue().getRenderer() != null)
								renderers.put(evt.getNewValue().getIdentity(),
									tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
							renderer = false;
							if (evt.getNewValue().getEditing() != null && evt.getNewValue().getEditing().getEditor() != null)
								editors.put(evt.getNewValue().getIdentity(),
									tx.transform(evt.getNewValue().getEditing().getEditor(), QuickSwingPopulator.class));
							break;
						case remove:
							renderers.remove(evt.getOldValue().getIdentity());
							editors.remove(evt.getOldValue().getIdentity());
							break;
						case set:
							if (evt.getOldValue().getIdentity() != evt.getNewValue().getIdentity()) {
								renderers.remove(evt.getOldValue().getIdentity());
								editors.remove(evt.getOldValue().getIdentity());
							}
							renderer = true;
							if (evt.getNewValue().getRenderer() != null)
								renderers.put(evt.getNewValue().getIdentity(),
									tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
							renderer = false;
							if (evt.getNewValue().getEditing() != null && evt.getNewValue().getEditing().getEditor() != null)
								editors.put(evt.getNewValue().getIdentity(),
									tx.transform(evt.getNewValue().getEditing().getEditor(), QuickSwingPopulator.class));
							break;
						}
					} catch (ExpressoInterpretationException e) {
						if (renderersInitialized)
							(renderer ? evt.getNewValue().getRenderer() : evt.getNewValue().getEditing().getEditor()).reporting()
							.at(e.getErrorOffset()).error(e.getMessage(), e);
						else
							throw new CheckedExceptionWrapper(e);
					}
				}, true);
			} catch (CheckedExceptionWrapper e) {
				if (e.getCause() instanceof ExpressoInterpretationException)
					throw (ExpressoInterpretationException) e.getCause();
				else
					throw new ExpressoInterpretationException(e.getMessage(), interpreted.reporting().getPosition(), 0, e.getCause());
			}
			renderersInitialized = true;
			interpreted.destroyed().act(__ -> sub.unsubscribe());
			// TODO Changes to actions collection?
			interpretedActions = BetterList.<ValueAction.Interpreted<R, ?>, QuickSwingTableAction<R, ?>, ExpressoInterpretationException> of2(
				interpreted.getActions().stream(), a -> (QuickSwingTableAction<R, ?>) tx.transform(a, QuickSwingTableAction.class));
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTable<R> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			TabularWidget.TabularContext<R> ctx = new TabularWidget.TabularContext.Default<>(rowType,
				quick.reporting().getPosition().toShortString());
			quick.setContext(ctx);
			TableBuilder<R, ?, ?>[] parent = new TableBuilder[1];
			ObservableCollection<InterpretedSwingTableColumn<R, ?>> columns = quick.getAllColumns().flow()//
				.map((Class<InterpretedSwingTableColumn<R, ?>>) (Class<?>) InterpretedSwingTableColumn.class, column -> {
					try {
						return new InterpretedSwingTableColumn<>(quick, column, ctx, panel.getUntil(), () -> parent[0],
							renderers.get(column.getColumnSet().getIdentity()), editors.get(column.getColumnSet().getIdentity()));
					} catch (ModelInstantiationException e) {
						if (tableInitialized) {
							column.getColumnSet().reporting().error(e.getMessage(), e);
							return null;
						} else
							throw new CheckedExceptionWrapper(e);
					}
				})//
				.filter(column -> column == null ? "Column failed to create" : null)//
				.catchUpdates(ThreadConstraint.ANY)//
				// TODO collectActive(onWhat?)
				.collect();
			Subscription columnsSub = columns.subscribe(evt -> {
				if (evt.getNewValue() != null)
					evt.getNewValue().init(columns, evt.getElementId());
			}, true);
			panel.getUntil().take(1).act(__ -> columnsSub.unsubscribe());
			ObservableCollection<CategoryRenderStrategy<R, ?>> crss = columns.flow()//
				.map((Class<CategoryRenderStrategy<R, ?>>) (Class<?>) CategoryRenderStrategy.class, //
					column -> column.getCRS())//
				.collect();
			panel.addTable(quick.getRows(), table -> {
				component.accept(table);
				parent[0] = table;
				table.withColumns(crss);
				if (quick.getSelection() != null)
					table.withSelection(quick.getSelection(), false);
				if (quick.getMultiSelection() != null)
					table.withSelection(quick.getMultiSelection());
				try {
					for (int a = 0; a < interpretedActions.size(); a++)
						((QuickSwingTableAction<R, ValueAction<R>>) interpretedActions.get(a)).addAction(table, quick.getActions().get(a));
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
			tableInitialized = true;
		}
	}

	static class SwingTree<T> extends QuickSwingPopulator.Abstract<QuickTree<T>> {
		private QuickSwingPopulator<QuickWidget> theRenderer;
		private QuickSwingPopulator<QuickWidget> theEditor;

		SwingTree(QuickTree.Interpreted<T, ?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			if (interpreted.getTreeColumn() == null) {
				theRenderer = null;
				theEditor = null;
			} else {
				theRenderer = interpreted.getTreeColumn().getRenderer() == null ? null
					: tx.transform(interpreted.getTreeColumn().getRenderer(), QuickSwingPopulator.class);
				if (interpreted.getTreeColumn().getEditing() == null || interpreted.getTreeColumn().getEditing().getEditor() == null)
					theEditor = null;
				else
					theEditor = tx.transform(interpreted.getTreeColumn().getEditing().getEditor(), QuickSwingPopulator.class);
			}
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTree<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			MultiValueRenderable.MultiValueRenderContext<BetterList<T>> ctx = new MultiValueRenderable.MultiValueRenderContext.Default<>(
				TypeTokens.get().keyFor(BetterList.class).<BetterList<T>> parameterized(quick.getNodeType()));
			quick.setContext(ctx);
			InterpretedSwingTableColumn<BetterList<T>, T> treeColumn;
			ValueHolder<PanelPopulation.TreeEditor<T, ?>> treeHolder = new ValueHolder<>();
			if (quick.getTreeColumn() == null)
				treeColumn = null;
			else {
				TabularWidget.TabularContext<BetterList<T>> tableCtx = new TabularWidget.TabularContext<BetterList<T>>() {
					private final SettableValue<Integer> theRow = SettableValue.build(int.class).withDescription("row").withValue(0)
						.build();
					private final SettableValue<Integer> theColumn = SettableValue.build(int.class).withDescription("column").withValue(0)
						.build();

					@Override
					public SettableValue<BetterList<T>> getActiveValue() {
						return ctx.getActiveValue();
					}

					@Override
					public SettableValue<Boolean> isSelected() {
						return ctx.isSelected();
					}

					@Override
					public SettableValue<Integer> getRowIndex() {
						return theRow;
					}

					@Override
					public SettableValue<Integer> getColumnIndex() {
						return theColumn;
					}
				};
				treeColumn = new InterpretedSwingTableColumn<>(quick,
					(QuickTableColumn<BetterList<T>, T>) quick.getTreeColumn().getColumns().getFirst(), tableCtx, panel.getUntil(),
					treeHolder, theRenderer, theEditor);
			}
			TypeToken<BetterList<T>> pathType = TypeTokens.get().keyFor(BetterList.class)
				.<BetterList<T>> parameterized(quick.getNodeType());
			panel.addTree3(quick.getModel().getValue(), (parentPath, nodeUntil) -> {
				try {
					return quick.getModel().getChildren(ObservableValue.of(pathType, parentPath), nodeUntil);
				} catch (ModelInstantiationException e) {
					quick.reporting().error("Could not create children for " + parentPath, e);
					return null;
				}
			}, tree -> {
				component.accept(tree);
				treeHolder.accept(tree);
				if (quick.getSelection() != null)
					tree.withSelection(quick.getSelection(), false);
				if (quick.getMultiSelection() != null)
					tree.withSelection(quick.getMultiSelection());
				if (quick.getNodeSelection() != null)
					tree.withValueSelection(quick.getNodeSelection(), false);
				if (quick.getNodeMultiSelection() != null)
					tree.withValueSelection(quick.getNodeMultiSelection());
				if (treeColumn != null)
					tree.withRender(treeColumn.getCRS());
				tree.withLeafTest2(path -> {
					ctx.getActiveValue().set(path, null);
					return quick.getModel().isLeaf(path);
				});
				tree.withRootVisible(quick.isRootVisible());
			});
		}
	}

	static class SwingSplit extends QuickSwingPopulator.QuickSwingContainerPopulator.Abstract<QuickSplit> {
		private final QuickSwingPopulator<QuickWidget> theFirst;
		private final QuickSwingPopulator<QuickWidget> theLast;

		SwingSplit(QuickSplit.Interpreted<?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			if (interpreted.getContents().size() > 0)
				theFirst = tx.transform(interpreted.getContents().getFirst(), QuickSwingPopulator.class);
			else
				theFirst = null;
			if (interpreted.getContents().size() > 1)
				theLast = tx.transform(interpreted.getContents().getLast(), QuickSwingPopulator.class);
			else
				theLast = null;
		}

		@Override
		protected void doPopulateContainer(ContainerPopulator<?, ?> panel, QuickSplit quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addSplit(quick.isVertical(), s -> {
				component.accept(s);
				SettableValue<QuickSize> splitPos = quick.getSplitPosition();
				s.withSplit(size -> {
					QuickSize sz = splitPos.get();
					return sz == null ? null : sz.evaluate(size);
				}, (newSplit, size) -> {
					if (!quick.isSplitPositionSet())
						return true;
					QuickSize sz = splitPos.get();
					QuickSize newSz;
					if (sz == null || sz.percent != 0.0f) // Proportion
						newSz = new QuickSize(newSplit * 100.0f / size, 0);
					else
						newSz = new QuickSize(0.0f, newSplit);
					if (splitPos.isAcceptable(newSz) == null) {
						splitPos.set(newSz, null);
						return true;
					} else
						return false;
				}, splitPos.noInitChanges());
				AbstractQuickContainerPopulator populator = new AbstractQuickContainerPopulator() {
					private boolean isFirst = true;

					@Override
					public Observable<?> getUntil() {
						return s.getUntil();
					}

					@Override
					public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
						Consumer<PanelPopulator<JPanel, ?>> hPanel) {
						if (isFirst) {
							isFirst = false;
							s.firstH(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) p));
						} else
							s.lastH(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) p));
						return this;
					}

					@Override
					public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> vPanel) {
						if (isFirst) {
							isFirst = false;
							s.firstV((Consumer<PanelPopulator<?, ?>>) (Consumer<?>) vPanel);
						} else
							s.lastV(p -> vPanel.accept((PanelPopulator<JPanel, ?>) p));
						return this;
					}
				};
				try {
					if (theFirst != null)
						theFirst.populate(populator, quick.getContents().getFirst());
					if (theLast != null)
						theLast.populate(populator, quick.getContents().getLast());
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
		}
	}

	static class SwingScroll extends QuickSwingPopulator.QuickSwingContainerPopulator.Abstract<QuickScrollPane> {
		private QuickSwingPopulator<QuickWidget> theContent;
		private QuickSwingPopulator<QuickWidget> theRowHeader;
		private QuickSwingPopulator<QuickWidget> theColumnHeader;

		SwingScroll(QuickScrollPane.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theContent = tx.transform(interpreted.getContents().getFirst(), QuickSwingPopulator.class);
			theRowHeader = interpreted.getRowHeader() == null ? null : tx.transform(interpreted.getRowHeader(), QuickSwingPopulator.class);
			theColumnHeader = interpreted.getColumnHeader() == null ? null
				: tx.transform(interpreted.getColumnHeader(), QuickSwingPopulator.class);
		}

		@Override
		protected void doPopulateContainer(ContainerPopulator<?, ?> panel, QuickScrollPane quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addScroll(null, s -> {
				component.accept(s);
				try {
					theContent.populate(new ScrollPopulator(s), quick.getContents().getFirst());
					if (theRowHeader != null)
						theRowHeader.populate(new ScrollRowHeaderPopulator(s), quick.getRowHeader());
					if (theColumnHeader != null)
						theColumnHeader.populate(new ScrollColumnHeaderPopulator(s), quick.getColumnHeader());
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
		}

		private static class ScrollPopulator extends AbstractQuickContainerPopulator {
			private final PanelPopulation.ScrollPane<?> theScroll;

			ScrollPopulator(PanelPopulation.ScrollPane<?> scroll) {
				theScroll = scroll;
			}

			@Override
			public Observable<?> getUntil() {
				return theScroll.getUntil();
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> hPanel) {
				theScroll.withHContent(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) p));
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> vPanel) {
				theScroll.withVContent(p -> vPanel.accept((PanelPopulator<JPanel, ?>) p));
				return this;
			}
		}

		private static class ScrollRowHeaderPopulator extends AbstractQuickContainerPopulator {
			private final PanelPopulation.ScrollPane<?> theScroll;

			ScrollRowHeaderPopulator(PanelPopulation.ScrollPane<?> scroll) {
				theScroll = scroll;
			}

			@Override
			public Observable<?> getUntil() {
				return theScroll.getUntil();
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> hPanel) {
				theScroll.withHRowHeader(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) p));
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> vPanel) {
				theScroll.withVRowHeader(p -> vPanel.accept((PanelPopulator<JPanel, ?>) p));
				return this;
			}
		}

		private static class ScrollColumnHeaderPopulator extends AbstractQuickContainerPopulator {
			private final PanelPopulation.ScrollPane<?> theScroll;

			ScrollColumnHeaderPopulator(PanelPopulation.ScrollPane<?> scroll) {
				theScroll = scroll;
			}

			@Override
			public Observable<?> getUntil() {
				return theScroll.getUntil();
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> hPanel) {
				theScroll.withHColumnHeader(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) p));
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> vPanel) {
				throw new IllegalArgumentException("Vertical panel makes no sense for a column header");
			}
		}
	}

	static QuickSwingDialog<QuickInfoDialog> interpretInfoDialog(QuickInfoDialog.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContent(), QuickSwingPopulator.class);
		return new QuickSwingDialog<QuickInfoDialog>() {
			private SettableValue<String> theType;
			private SettableValue<String> theTitle;
			private SettableValue<Icon> theIcon;
			private Component theContent;
			private ObservableAction theOnClose;

			@Override
			public void initialize(QuickInfoDialog dialog, Component parent, Observable<?> until) throws ModelInstantiationException {
				QuickAbstractWindow window = dialog.getAddOn(QuickAbstractWindow.class);
				theType = dialog.getType();
				theTitle = window.getTitle();
				theIcon = dialog.getIcon();
				theOnClose = dialog.getOnClose();
				QuickBaseSwing.ComponentExtractor ce = new ComponentExtractor(until);
				content.populate(ce, dialog.getContent());
				theContent = ce.getExtractedComponent();

				window.isVisible().value().takeUntil(until).filter(v -> v).act(__ -> {
					Icon icon = theIcon == null ? null : theIcon.get();
					int swingType;
					String type = theType.get();
					if (type == null)
						swingType = JOptionPane.INFORMATION_MESSAGE;
					else {
						switch (type.toLowerCase()) {
						case "info":
						case "information":
							swingType = JOptionPane.INFORMATION_MESSAGE;
							break;
						case "err":
						case "error":
							swingType = JOptionPane.ERROR_MESSAGE;
							break;
						case "warn":
						case "warning":
							swingType = JOptionPane.WARNING_MESSAGE;
							break;
						case "plain":
							swingType = JOptionPane.PLAIN_MESSAGE;
							break;
						case "q":
						case "question":
						case "?":
							swingType = JOptionPane.QUESTION_MESSAGE;
							break;
						default:
							dialog.reporting().warn("Unrecognized message type: " + type);
							swingType = JOptionPane.INFORMATION_MESSAGE;
							break;
						}
					}
					if (icon != null)
						JOptionPane.showMessageDialog(parent, theContent, theTitle.get(), swingType, icon);
					else
						JOptionPane.showMessageDialog(parent, theContent, theTitle.get(), swingType);
					EventQueue.invokeLater(() -> {
						if (window.isVisible().isAcceptable(false) == null)
							window.isVisible().set(false, null);
					});
					theOnClose.act(null);
				});
			}
		};
	}

	static QuickSwingDialog<QuickConfirm> interpretConfirm(QuickConfirm.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContent(), QuickSwingPopulator.class);
		return new QuickSwingDialog<QuickConfirm>() {
			private SettableValue<String> theTitle;
			private SettableValue<Icon> theIcon;
			private Component theContent;
			private ObservableAction theOnConfirm;
			private ObservableAction theOnCancel;

			@Override
			public void initialize(QuickConfirm dialog, Component parent, Observable<?> until) throws ModelInstantiationException {
				QuickAbstractWindow window = dialog.getAddOn(QuickAbstractWindow.class);
				theTitle = window.getTitle();
				theIcon = dialog.getIcon();
				theOnConfirm = dialog.getOnConfirm();
				theOnCancel = dialog.getOnCancel();
				QuickBaseSwing.ComponentExtractor ce = new ComponentExtractor(until);
				content.populate(ce, dialog.getContent());
				theContent = ce.getExtractedComponent();

				window.isVisible().value().takeUntil(until).filter(v -> v).act(__ -> {
					int result;
					Icon icon = theIcon == null ? null : theIcon.get();
					if (icon != null)
						result = JOptionPane.showConfirmDialog(parent, theContent, theTitle.get(), JOptionPane.OK_CANCEL_OPTION,
							JOptionPane.QUESTION_MESSAGE, icon);
					else
						result = JOptionPane.showConfirmDialog(parent, theContent, theTitle.get(), JOptionPane.OK_CANCEL_OPTION);
					EventQueue.invokeLater(() -> {
						if (window.isVisible().isAcceptable(false) == null)
							window.isVisible().set(false, null);
					});
					if (result == JOptionPane.OK_OPTION)
						theOnConfirm.act(null);
					else if (theOnCancel.isEnabled() == null)
						theOnCancel.act(null);
				});
			}
		};
	}

	static QuickSwingDialog<QuickFileChooser> interpretFileChooser(QuickFileChooser.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		return new QuickSwingDialog<QuickFileChooser>() {
			private QuickFileChooser theQuickChooser;
			private SettableValue<String> theTitle;
			private JFileChooser theSwingChooser = new JFileChooser();

			@Override
			public void initialize(QuickFileChooser dialog, Component parent, Observable<?> until) throws ModelInstantiationException {
				QuickAbstractWindow window = dialog.getAddOn(QuickAbstractWindow.class);
				theTitle = window.getTitle();
				theQuickChooser = dialog;
				int mode;
				if (theQuickChooser.isFilesSelectable()) {
					if (theQuickChooser.isDirectoriesSelectable())
						mode = JFileChooser.FILES_AND_DIRECTORIES;
					else
						mode = JFileChooser.FILES_ONLY;
				} else
					mode = JFileChooser.DIRECTORIES_ONLY;
				theSwingChooser.setFileSelectionMode(mode);
				theSwingChooser.setMultiSelectionEnabled(theQuickChooser.isMultiSelectable());

				window.isVisible().value().takeUntil(until).filter(v -> v).act(__ -> display(window, parent));
			}

			void display(QuickAbstractWindow window, Component parent) {
				boolean satisfied = false;
				while (!satisfied) {
					File dir = theQuickChooser.getDirectory().get();
					if (dir != null)
						theSwingChooser.setCurrentDirectory(dir);
					theSwingChooser.setDialogTitle(theTitle.get());

					int result;
					if (theQuickChooser.isOpen())
						result = theSwingChooser.showOpenDialog(parent);
					else
						result = theSwingChooser.showSaveDialog(parent);

					String enabled, title = null;
					if (result == JFileChooser.APPROVE_OPTION) {
						List<File> files;
						if (theSwingChooser.isMultiSelectionEnabled())
							files = Arrays.asList(theSwingChooser.getSelectedFiles());
						else
							files = Arrays.asList(theSwingChooser.getSelectedFile());
						enabled = theQuickChooser.filesChosen(files);
						if (enabled == null) {
							if (theQuickChooser.getDirectory().isAcceptable(theSwingChooser.getCurrentDirectory()) == null) {
								satisfied = true;
								theQuickChooser.getDirectory().set(theSwingChooser.getCurrentDirectory(), null);
							}
						} else
							title = "Selected file" + (files.size() == 1 ? "" : "s") + " not allowed";
					} else {
						enabled = theQuickChooser.getOnCancel().isEnabled().get();
						if (enabled == null) {
							satisfied = true;
							theQuickChooser.getOnCancel().act(null);
						} else
							title = theQuickChooser.isOpen() ? "A file must be chosen" : "The file must be saved";
					}
					if (!satisfied)
						JOptionPane.showMessageDialog(parent, enabled, title, JOptionPane.ERROR_MESSAGE);
				}
				EventQueue.invokeLater(() -> {
					if (window.isVisible().isAcceptable(false) == null)
						window.isVisible().set(false, null);
				});
			}
		};
	}

	static QuickSwingDialog<GeneralDialog> interpretGeneralDialog(GeneralDialog.Interpreted<?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContent(), QuickSwingPopulator.class);
		return new QuickSwingDialog<GeneralDialog>() {
			@Override
			public void initialize(GeneralDialog dialog, Component parent, Observable<?> until) throws ModelInstantiationException {
				QuickWindow window = dialog.getAddOn(QuickWindow.class);
				SettableValue<String> title = window.getTitle();
				JDialog jDialog = new JDialog(SwingUtilities.getWindowAncestor(parent), //
					dialog.isModal() ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
				if (!dialog.isModal())
					jDialog.setAlwaysOnTop(dialog.isAlwaysOnTop());
				PanelPopulation.WindowBuilder<JDialog, ?> swingDialog = WindowPopulation.populateDialog(jDialog, until, false);
				content.populate(new WindowContentPopulator(swingDialog, until), dialog.getContent());
				swingDialog.withTitle(title);
				if (window.getX() != null)
					swingDialog.withX(window.getX());
				if (window.getY() != null)
					swingDialog.withY(window.getY());
				if (window.getWidth() != null)
					swingDialog.withWidth(window.getWidth());
				if (window.getHeight() != null)
					swingDialog.withHeight(window.getHeight());
				swingDialog.withVisible(window.isVisible());
				swingDialog.disposeOnClose(false);
				EventQueue.invokeLater(() -> { // Do in an invoke later to allow the UI to come up before opening the dialog
					swingDialog.run(parent);
				});
			}
		};
	}

	static class WindowContentPopulator extends AbstractQuickContainerPopulator {
		private final PanelPopulation.WindowBuilder<?, ?> theWindow;
		private final Observable<?> theUntil;

		WindowContentPopulator(WindowBuilder<?, ?> window, Observable<?> until) {
			theWindow = window;
			theUntil = until;
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> panel) {
			theWindow.withHContent(layout, p -> panel.accept((PanelPopulator<JPanel, ?>) p));
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
			theWindow.withVContent(p -> panel.accept((PanelPopulator<JPanel, ?>) p));
			return this;
		}
	}

	static class SwingTabs<T> extends QuickSwingContainerPopulator.Abstract<QuickTabs<T>> {
		Map<Object, QuickSwingPopulator<QuickWidget>> renderers = new HashMap<>();

		SwingTabs(QuickTabs.Interpreted<T> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			for (QuickWidget.Interpreted<?> content : interpreted.getContents())
				renderers.put(content.getIdentity(), tx.transform(content, QuickSwingPopulator.class));
			for (QuickTabs.TabSet.Interpreted<? extends T> tabSet : interpreted.getTabSets())
				renderers.put(tabSet.getRenderer().getIdentity(), tx.transform(tabSet.getRenderer(), QuickSwingPopulator.class));
		}

		@Override
		protected void doPopulateContainer(ContainerPopulator<?, ?> panel, QuickTabs<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			PanelPopulation.TabPaneEditor<?, ?>[] tabs = new PanelPopulation.TabPaneEditor[1];
			panel.addTabs(t -> {
				component.accept(t);
				tabs[0] = t;
			});
			quick.getTabs().subscribe(evt -> {
				switch (evt.getType()) {
				case add:
					QuickSwingPopulator<QuickWidget> renderer = renderers.get(evt.getNewValue().getRenderer().getIdentity());
					try {
						renderer.populate(new TabsPopulator<>(tabs[0], evt.getNewValue(), evt.getIndex()),
							evt.getNewValue().getRenderer());
					} catch (ModelInstantiationException e) {
						evt.getNewValue().getRenderer().reporting().error("Failed to populate tab", e);
					}
					break;
				case remove:
				case set:
				}
			}, true);
			tabs[0].withSelectedTab(quick.getSelectedTab());
		}

		private static class TabsPopulator<T> extends AbstractQuickContainerPopulator {
			private final PanelPopulation.TabPaneEditor<?, ?> theTabEditor;
			private final QuickTabs.TabInstance<? extends T> theTab;
			private final int theTabIndex;

			TabsPopulator(PanelPopulation.TabPaneEditor<?, ?> tabEditor, QuickTabs.TabInstance<? extends T> tab,
				int index) {
				theTabEditor = tabEditor;
				theTab = tab;
				theTabIndex = index;
			}

			@Override
			public Observable<?> getUntil() {
				return theTabEditor.getUntil();
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> panel) {
				theTabEditor.withHTab(theTab.getTabValue(), theTabIndex, layout,
					tab -> panel.accept((PanelPopulator<JPanel, ?>) tab), this::configureTab);
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
				theTabEditor.withVTab(theTab.getTabValue(), theTabIndex, tab -> panel.accept((PanelPopulator<JPanel, ?>) tab),
					this::configureTab);
				return this;
			}

			void configureTab(PanelPopulation.TabEditor<?> tab) {
				tab.setName(theTab.getTabName());
				tab.setIcon(theTab.getTabIcon());
				tab.setRemovable(theTab.isRemovable().get());
				tab.onRemove(__ -> theTab.onRemove());
				tab.onSelect(onSelect -> {
					onSelect.changes().takeUntil(theTab.getRenderer().isDestroyed().noInitChanges().take(1)).act(evt -> {
						if (evt.getNewValue())
							theTab.onSelect();
					});
				});
			}
		}
	}

	static class ComponentExtractor extends AbstractQuickContainerPopulator {
		private Observable<?> theUntil;
		private Component theExtractedComponent;

		public ComponentExtractor(Observable<?> until) {
			theUntil = until;
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> panel) {
			PanelPopulation.PanelPopulator<JPanel, ?> populator = PanelPopulation.populateHPanel(null, layout, theUntil);
			panel.accept(populator);
			theExtractedComponent = populator.getContainer();
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
			PanelPopulation.PanelPopulator<JPanel, ?> populator = PanelPopulation.populateVPanel(null, theUntil);
			panel.accept(populator);
			theExtractedComponent = populator.getContainer();
			return this;
		}

		public Component getExtractedComponent() {
			return theExtractedComponent;
		}
	}
}