package org.observe.quick.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.Iconized;
import org.observe.quick.MouseCursor;
import org.observe.quick.QuickAbstractWindow;
import org.observe.quick.QuickDialog;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickTextWidget;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.DynamicStyledDocument;
import org.observe.quick.base.GeneralDialog;
import org.observe.quick.base.MultiValueRenderable;
import org.observe.quick.base.MultiValueRenderable.MultiValueRenderContext;
import org.observe.quick.base.Positionable;
import org.observe.quick.base.QuickAbstractMenuItem;
import org.observe.quick.base.QuickBorderLayout;
import org.observe.quick.base.QuickBox;
import org.observe.quick.base.QuickButton;
import org.observe.quick.base.QuickCheckBox;
import org.observe.quick.base.QuickCheckBoxMenuItem;
import org.observe.quick.base.QuickColorChooser;
import org.observe.quick.base.QuickComboBox;
import org.observe.quick.base.QuickConfirm;
import org.observe.quick.base.QuickCustomComponent;
import org.observe.quick.base.QuickEditableTextWidget;
import org.observe.quick.base.QuickField;
import org.observe.quick.base.QuickFieldPanel;
import org.observe.quick.base.QuickFileButton;
import org.observe.quick.base.QuickFileChooser;
import org.observe.quick.base.QuickGridFlowLayout;
import org.observe.quick.base.QuickInfoDialog;
import org.observe.quick.base.QuickInlineLayout;
import org.observe.quick.base.QuickLabel;
import org.observe.quick.base.QuickLayerLayout;
import org.observe.quick.base.QuickLayout;
import org.observe.quick.base.QuickMenu;
import org.observe.quick.base.QuickMenuBar;
import org.observe.quick.base.QuickMenuContainer;
import org.observe.quick.base.QuickMenuItem;
import org.observe.quick.base.QuickProgressBar;
import org.observe.quick.base.QuickRadioButton;
import org.observe.quick.base.QuickRadioButtons;
import org.observe.quick.base.QuickScrollPane;
import org.observe.quick.base.QuickSeparator;
import org.observe.quick.base.QuickSimpleLayout;
import org.observe.quick.base.QuickSize;
import org.observe.quick.base.QuickSlider;
import org.observe.quick.base.QuickSpacer;
import org.observe.quick.base.QuickSpinner;
import org.observe.quick.base.QuickSplit;
import org.observe.quick.base.QuickTable;
import org.observe.quick.base.QuickTable.Interpreted;
import org.observe.quick.base.QuickTableColumn;
import org.observe.quick.base.QuickTabs;
import org.observe.quick.base.QuickTextArea;
import org.observe.quick.base.QuickTextField;
import org.observe.quick.base.QuickToggleButton;
import org.observe.quick.base.QuickToggleButtons;
import org.observe.quick.base.QuickTransfer;
import org.observe.quick.base.QuickTree;
import org.observe.quick.base.Sizeable;
import org.observe.quick.base.StyledDocument;
import org.observe.quick.base.TabularWidget;
import org.observe.quick.base.TabularWidget.TableSelectionMode;
import org.observe.quick.base.ValueAction;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingContainerPopulator;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingDialog;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingDocument;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingLayout;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingTableAction;
import org.observe.quick.swing.QuickSwingPopulator.WindowModifier;
import org.observe.quick.swing.QuickSwingTablePopulation.InterpretedSwingTableColumn;
import org.observe.util.TypeTokens;
import org.observe.util.swing.BgFontAdjuster;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.LayerLayout;
import org.observe.util.swing.ModelCell;
import org.observe.util.swing.ObservableColorEditor;
import org.observe.util.swing.ObservableFileButton;
import org.observe.util.swing.ObservableStyledDocument;
import org.observe.util.swing.ObservableTextArea;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.ContainerPopulator;
import org.observe.util.swing.PanelPopulation.MenuBarBuilder;
import org.observe.util.swing.PanelPopulation.MenuBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.observe.util.swing.Shading;
import org.observe.util.swing.WindowPopulation;
import org.qommons.BiTuple;
import org.qommons.BreakpointHere;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.TriConsumer;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiFunction;
import org.qommons.io.Format;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

/** Quick interpretation of the base toolkit for Swing */
public class QuickBaseSwing implements QuickInterpretation {
	/**
	 * Support for menu bars in Quick swing
	 *
	 * @param <E> The type of element this menu bar support is for
	 */
	public interface QuickSwingMenuBarPopulator<E extends ExElement> {
		/**
		 * @param menuBar The menu bar builder to populate
		 * @param quick The quick element to use to populate the menu bar
		 * @throws ModelInstantiationException If an error occurs populating the menu bar
		 */
		void populateMenuBar(PanelPopulation.MenuBarBuilder<?> menuBar, E quick) throws ModelInstantiationException;
	}

	/**
	 * Support for menus in Quick swing
	 *
	 * @param <E> The type of element this menu support is for
	 */
	public interface QuickSwingMenuPopulator<E extends ExElement> {
		/**
		 * @param menu The menu builder to populate
		 * @param quick The quick element to use to populate the menu
		 * @throws ModelInstantiationException If an error occurs populating the menu
		 */
		void populateMenu(PanelPopulation.MenuBuilder<?, ?> menu, E quick) throws ModelInstantiationException;
	}

	/**
	 * Quick DataFlavor support in Swing
	 *
	 * @param <T> The type of data for the flavor
	 * @param <F> The type of the QuickDataFlavor
	 */
	public interface QuickSwingDataFlavor<T, F extends QuickTransfer.QuickDataFlavor<T>> {
		/**
		 * @param quick The instantiated QuickDataFlavor
		 * @return The Swing DataFlavor represented by the Quick data flavor
		 * @throws ModelInstantiationException If the data flavor cannot be interpreted
		 */
		DataFlavor getFlavor(F quick) throws ModelInstantiationException;
	}

	@Override
	public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
		// Simple widgets
		tx.with(QuickLabel.Interpreted.class, QuickSwingPopulator.class, widget(SwingLabel::new));
		tx.with(QuickSpacer.Interpreted.class, QuickSwingPopulator.class, (i, tx2) -> new SwingSpacer());
		tx.with(QuickSeparator.Interpreted.class, QuickSwingPopulator.class, (i, tx2) -> new SwingSeparator());
		tx.with(QuickProgressBar.Interpreted.class, QuickSwingPopulator.class, (i, tx2) -> new SwingProgressBar());
		tx.with(QuickTextField.Interpreted.class, QuickSwingPopulator.class, widget(SwingTextField::new));
		tx.with(QuickCheckBox.Interpreted.class, QuickSwingPopulator.class, widget(SwingCheckBox::new));
		tx.with(QuickRadioButton.Interpreted.class, QuickSwingPopulator.class, widget(SwingRadioButton::new));
		tx.with(QuickToggleButton.Interpreted.class, QuickSwingPopulator.class, widget(SwingToggleButton::new));
		tx.with(QuickButton.Interpreted.class, QuickSwingPopulator.class, widget(SwingButton::new));
		tx.with(QuickFileButton.Interpreted.class, QuickSwingPopulator.class, widget(SwingFileButton::new));
		tx.with(QuickComboBox.Interpreted.class, QuickSwingPopulator.class, SwingComboBox::new);
		tx.with(QuickSlider.Interpreted.class, QuickSwingPopulator.class, widget(SwingSlider::new));
		tx.with(QuickSpinner.Interpreted.class, QuickSwingPopulator.class, (i, tx2) -> new SwingSpinner<>(i));
		tx.with(QuickColorChooser.Interpreted.class, QuickSwingPopulator.class, (i, tx2) -> new SwingColorChooser());
		tx.with(QuickRadioButtons.Interpreted.class, QuickSwingPopulator.class, widget(SwingRadioButtons::new));
		tx.with(QuickToggleButtons.Interpreted.class, QuickSwingPopulator.class, widget(SwingToggleButtons::new));
		tx.with(QuickTextArea.Interpreted.class, QuickSwingPopulator.class, SwingTextArea::new);
		tx.with(DynamicStyledDocument.Interpreted.class, QuickSwingDocument.class,
			(qd, tx2) -> QuickBaseSwing.interpretDynamicStyledDoc(qd, tx2));
		tx.with(QuickCustomComponent.Interpreted.class, QuickSwingPopulator.class, (quick, tx2) -> new SwingCustomComponentPopulator());

		// Containers
		tx.with(QuickBox.Interpreted.class, QuickSwingContainerPopulator.class, SwingBox::new);
		tx.with(QuickFieldPanel.Interpreted.class, QuickSwingContainerPopulator.class, SwingFieldPanel::new);
		QuickSwingPopulator.<QuickWidget, QuickField, QuickField.Interpreted> modifyForAddOn(tx, QuickField.Interpreted.class,
			(Class<QuickWidget.Interpreted<QuickWidget>>) (Class<?>) QuickWidget.Interpreted.class, (ao, qsp, tx2) -> {
				QuickSwingPopulator<QuickWidget> post = ao.getPost() == null ? null
					: tx2.transform(ao.getPost().getContent(), QuickSwingPopulator.class);
				qsp.addModifier((comp, w) -> {
					QuickField aoi = w.getAddOn(QuickField.class);
					if (aoi.getFieldLabel() != null)
						comp.withFieldName(aoi.getFieldLabel());
					if (post != null) {
						comp.withPostContent(p -> {
							try {
								post.populate(p, aoi.getPost().getContent());
							} catch (ModelInstantiationException e) {
								throw new CheckedExceptionWrapper(e);
							}
						});
					}
					if (ao.getDefinition().isFill())
						comp.fill();
					if (ao.getDefinition().isVFill())
						comp.fillV();
				});
			});
		tx.with(QuickSplit.Interpreted.class, QuickSwingContainerPopulator.class, SwingSplit::new);
		tx.with(QuickScrollPane.Interpreted.class, QuickSwingContainerPopulator.class, SwingScroll::new);

		// Box layouts
		tx.with(QuickInlineLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretInlineLayout);
		tx.with(QuickSimpleLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretSimpleLayout);
		tx.with(QuickBorderLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretBorderLayout);
		tx.with(QuickGridFlowLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretGridFlowLayout);
		tx.with(QuickLayerLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretLayerLayout);

		// Table
		tx.with(QuickTable.Interpreted.class, QuickSwingPopulator.class, SwingTable::new);
		tx.with(ValueAction.Single.Interpreted.class, QuickSwingTableAction.class, QuickSwingTablePopulation::interpretValueAction);
		tx.with(ValueAction.Multi.Interpreted.class, QuickSwingTableAction.class, QuickSwingTablePopulation::interpretMultiValueAction);
		tx.with(MappedTableConfig.class, SwingTable.class, SwingTable::new);

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

		// Data flavors
		tx.with(QuickTransfer.AsObject.Interpreted.class, QuickSwingDataFlavor.class, (quick, tx2) -> {
			TypeToken<?> dataType = quick.getDataType();
			DataFlavor flavor = new DataFlavor(TypeTokens.getRawType(dataType), dataType.toString());
			return __ -> flavor;
		});
		@SuppressWarnings("deprecation")
		final DataFlavor plainText = DataFlavor.plainTextFlavor;
		tx.with(QuickTransfer.AsText.Interpreted.class, QuickSwingDataFlavor.class, (quick, tx2) -> {
			String mimeType = quick.getDefinition().getMimeType();
			DataFlavor flavor;
			switch (mimeType) {
			case "text/plain":
				flavor = plainText;
				break;
			case "text/html":
				flavor = DataFlavor.allHtmlFlavor;
				break;
			default:
				flavor = new DataFlavor(mimeType, mimeType);
				break;
			}
			return __ -> flavor;
		});
	}

	/**
	 * Utility method for populating a {@link org.qommons.Transformer.Builder} for a Quick widget populator for a generic widget type
	 *
	 * @param <W> The wildcard Quick widget element type
	 * @param <GW> The parameterized Quick widget element type
	 * @param <I> The wildcard Quick widget interpreted type
	 * @param <GI> The parameterized Quick widget interpreted type
	 * @param ctor The constructor for the widget populator
	 * @return The population creator for the transformer
	 */
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
					menu.withIcon(quick.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));

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
				menu.withIcon(quick.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
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
					menu.withIcon(quick.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));

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
				menu.withIcon(quick.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
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
			LayoutManager layoutInst = theLayout.create(panel, quick.getLayout());
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
			public LayoutManager create(ContainerPopulator<?, ?> panel, QuickInlineLayout quick) throws ModelInstantiationException {
				return new JustifiedBoxLayout(quick.isVertical())//
					.setMainAlignment(quick.getMainAlign())//
					.setCrossAlignment(quick.getCrossAlign())//
					.setPadding(quick.getPadding())//
					.setShowingInvisible(quick.isShowInvisible());
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
			public LayoutManager create(ContainerPopulator<?, ?> panel, QuickSimpleLayout quick) throws ModelInstantiationException {
				SimpleLayout layout = new SimpleLayout();
				layout.setContainerConstraints(simpleConstraints(null, null, quick.getElement().getAddOn(Sizeable.Horizontal.class),
					quick.getElement().getAddOn(Sizeable.Vertical.class)));
				return layout;
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
			new SimpleLayout.DimensionConstraints(//
				h == null ? null : h.getLeading(), h == null ? null : h.getCenter(), h == null ? null : h.getTrailing(), //
					width.getSize(), enforceAbsolute(width.getMinimum()), enforceAbsolute(width.getPreferred()),
					enforceAbsolute(width.getMaximum())), //
			new SimpleLayout.DimensionConstraints(//
				v == null ? null : v.getLeading(), v == null ? null : v.getCenter(), v == null ? null : v.getTrailing(), //
					height.getSize(), enforceAbsolute(height.getMinimum()), enforceAbsolute(height.getPreferred()),
					enforceAbsolute(height.getMaximum()))//
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
			public LayoutManager create(ContainerPopulator<?, ?> panel, QuickBorderLayout quick) throws ModelInstantiationException {
				return new BetterBorderLayout();
			}

			@Override
			public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
				child.addModifier((comp, w) -> {
					QuickBorderLayout.Region region = w.getAddOn(QuickBorderLayout.Child.class).getRegion();
					Component[] component = new Component[1];
					comp.modifyComponent(c -> component[0] = c);
					Sizeable size = w.getAddOn(Sizeable.class);
					BetterBorderLayout.Constraints childConstraint = borderConstraints(region, size);
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

	static BetterBorderLayout.Constraints borderConstraints(QuickBorderLayout.Region region, Sizeable size) {
		if (size == null)
			return new BetterBorderLayout.Constraints(region, null, null, null, null);
		return new BetterBorderLayout.Constraints(region, //
			size.getSize(), enforceAbsolute(size.getMinimum()), enforceAbsolute(size.getPreferred()), enforceAbsolute(size.getMaximum()));
	}

	static QuickSwingLayout<QuickGridFlowLayout> interpretGridFlowLayout(QuickGridFlowLayout.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return new QuickSwingLayout<QuickGridFlowLayout>() {
			@Override
			public LayoutManager create(ContainerPopulator<?, ?> panel, QuickGridFlowLayout quick) throws ModelInstantiationException {
				GridFlowLayout layout = new GridFlowLayout()//
					.setPrimaryStart(quick.getPrimaryStart()).setSecondaryStart(quick.getSecondaryStart())//
					.setMainAlign(quick.getMainAlign()).setCrossAlign(quick.getCrossAlign()).setRowAlign(quick.getRowAlign())//
					.setPadding(quick.getPadding());
				quick.getMaxRowCount().changes().takeUntil(quick.getElement().onDestroy()).act(evt -> {
					layout.setMaxRowCount(evt.getNewValue());
					if (!evt.isInitial() && panel.getContainer() != null)
						panel.getContainer().invalidate();
				});
				return layout;
			}

			@Override
			public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
			}
		};
	}

	static QuickSwingLayout<QuickLayerLayout> interpretLayerLayout(QuickLayerLayout.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) {
		return new QuickSwingLayout<QuickLayerLayout>() {
			@Override
			public LayoutManager create(ContainerPopulator<?, ?> panel, QuickLayerLayout quick) throws ModelInstantiationException {
				return new LayerLayout();
			}

			@Override
			public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
			}
		};
	}

	static class SwingSpacer extends QuickSwingPopulator.Abstract<QuickSpacer> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickSpacer quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addComponent(null, new SpacerComponent(quick, Observable.or(panel.getUntil(), quick.onDestroy())),
				sp -> component.accept(sp));
		}
	}

	static class SpacerComponent extends JComponent {
		SpacerComponent(QuickSpacer spacer, Observable<?> until) {
			Sizeable.Horizontal horizontalSize = spacer.getAddOn(Sizeable.Horizontal.class);
			Sizeable.Vertical verticalSize = spacer.getAddOn(Sizeable.Vertical.class);

			SimpleObservable<Void> parentChange = new SimpleObservable<>();
			addHierarchyListener(e -> parentChange.onNext(null));

			applySize(horizontalSize, verticalSize, parentChange, -1, until);
			applySize(horizontalSize, verticalSize, parentChange, 0, until);
			applySize(horizontalSize, verticalSize, parentChange, 1, until);
		}

		private void applySize(Sizeable.Horizontal horizontalSize, Sizeable.Vertical verticalSize, SimpleObservable<Void> parentChange,
			int type, Observable<?> until) {
			ObservableValue<QuickSize> typeWidth, typeHeight;
			Consumer<Dimension> setSize;
			QuickSize defaultSize;
			if (type < 0) {
				typeWidth = horizontalSize.getMinimum();
				typeHeight = verticalSize.getMinimum();
				setSize = this::setMinimumSize;
				defaultSize = QuickSize.ZERO;
			} else if (type == 0) {
				typeWidth = horizontalSize.getPreferred();
				typeHeight = verticalSize.getPreferred();
				setSize = this::setPreferredSize;
				defaultSize = QuickSize.ZERO;
			} else {
				typeWidth = horizontalSize.getMaximum();
				typeHeight = verticalSize.getMaximum();
				setSize = this::setMaximumSize;
				defaultSize = QuickSize.ofPixels(Integer.MAX_VALUE);
			}
			ObservableValue<QuickSize> width = ObservableValue.firstValue(v -> v != null, () -> defaultSize, //
				horizontalSize.getSize(), typeWidth);
			ObservableValue<QuickSize> minHeight = ObservableValue.firstValue(v -> v != null, () -> defaultSize, //
				verticalSize.getSize(), typeHeight);
			width.<BiTuple<QuickSize, QuickSize>> transform(tx -> tx//
				.combineWith(minHeight)//
				.combine(BiTuple::new))//
			.refresh(parentChange)//
			.changes()//
			.takeUntil(until)//
			.act(evt -> {
				BiTuple<QuickSize, QuickSize> min = evt.getNewValue();
				Dimension parentSize = getParent() == null ? new Dimension(0, 0) : getParent().getSize();
				setSize.accept(new Dimension(min.getValue1().evaluate(parentSize.width), min.getValue2().evaluate(parentSize.height)));
			});
		}
	}

	static class SwingSeparator extends QuickSwingPopulator.Abstract<QuickSeparator> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickSeparator quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addComponent(null, new JSeparator(quick.isVertical() ? JSeparator.VERTICAL : JSeparator.HORIZONTAL),
				c -> component.accept(c));
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
				lbl.disableWith(quick.getDisabled());
				lbl.withIcon(quick.getAddOn(Iconized.class).getIcon().map(//
					LambdaUtils.printableFn(img -> img == null ? null : new ImageIcon(img), "ImageIcon(Image)", null)));
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
			if (format == null)
				quick.reporting().error("Format cannot be null");
			boolean commitOnType = quick.isCommitOnType();
			Integer columns = quick.getColumns();
			panel.addTextField(null, quick.getValue(), format, tf -> {
				component.accept(tf);
				boolean[] watching = new boolean[1];
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
					if (!watching[0]) {
						watching[0] = true;
						quick.getFormat().changes().takeUntil(tf.getUntil()).act(evt -> tf2.getEditor().setFormat(evt.getNewValue()));
						quick.isPassword().changes().takeUntil(tf.getUntil())
						.act(evt -> tf2.asPassword(Boolean.TRUE.equals(evt.getNewValue()) ? '*' : (char) 0));
						quick.getEmptyText().changes().takeUntil(tf.getUntil()).act(evt -> tf2.setEmptyText(evt.getNewValue()));
						quick.isEditable().changes().takeUntil(tf.getUntil())
						.act(evt -> tf2.setEditable(!Boolean.FALSE.equals(evt.getNewValue())));
					}
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
			// TODO This currently doesn't handle mouse cursor for the <text-style> element. Not quite sure how to do that.
			Format<T> format = quick.getFormat().get();
			boolean commitOnType = quick.isCommitOnType();
			SettableValue<Integer> rows = quick.getRows();
			Consumer<ComponentEditor<ObservableTextArea<T>, ?>> modifier = tf -> {
				component.accept(tf);
				boolean[] watching = new boolean[1];
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
					if (!watching[0]) {
						watching[0] = true;
						rows.changes().takeUntil(tf.getUntil()).act(evt -> tf2.withRows(evt.getNewValue()));
						QuickTextArea.QuickTextAreaContext ctx = new QuickTextArea.QuickTextAreaContext.Default();
						tf2.addMouseListener(pos -> ctx.getMousePosition().set(pos, null));
						quick.setTextAreaContext(ctx);
						if (tf2.getEditor() != null)
							quick.getFormat().changes().takeUntil(tf.getUntil()).act(evt -> tf2.getEditor().setFormat(evt.getNewValue()));
						quick.isEditable().changes().takeUntil(tf.getUntil())
						.act(evt -> tf2.setEditable(!Boolean.FALSE.equals(evt.getNewValue())));
					}
				});
			};
			if (theDocument != null) {
				ObservableStyledDocument<T> docInst = theDocument.interpret(quick.getDocument(), panel.getUntil());
				panel.addStyledTextArea(null, docInst, tf -> {
					modifier.accept(tf);
					tf.modifyEditor(tf2 -> {
						MouseAdapter mouse = theDocument.mouseListener(quick.getDocument(), docInst, tf2, tf.getUntil());
						tf2.addMouseListener(mouse);
						tf2.addMouseMotionListener(mouse);
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
						tf2.asHtml(quick.isHtml());
						tf2.setCommitOnType(commitOnType);
						rows.changes().takeUntil(tf.getUntil()).act(evt -> tf2.withRows(evt.getNewValue()));
					});
				});
			}
		}
	}

	static <T> QuickSwingDocument<T> interpretDynamicStyledDoc(DynamicStyledDocument.Interpreted<T, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		return new QuickSwingDocument<T>() {
			private JTextComponent theWidget;

			@Override
			public ObservableStyledDocument<T> interpret(StyledDocument<T> quickDoc, Observable<?> until)
				throws ModelInstantiationException {
				DynamicStyledDocument<T> doc = (DynamicStyledDocument<T>) quickDoc;
				Format<T> format = doc.getFormat();
				ObservableStyledDocument<T> swingDoc = new ObservableStyledDocument<T>(doc.getRoot(), format, ThreadConstraint.EDT, until) {
					private boolean didSetCursor;

					@Override
					protected ObservableCollection<? extends T> getChildren(T value) {
						try {
							return doc.getChildren(staCtx(value, false, false, false, false));
						} catch (ModelInstantiationException e) {
							doc.reporting().error(e.getMessage(), e);
							return ObservableCollection.of();
						}
					}

					@Override
					protected void adjustStyle(T value, BgFontAdjuster style) {
						boolean hovered = doc.getNodeValue().get() == value;
						boolean focused = doc.getSelectionStartValue().get() == value;
						boolean pressed = hovered && QuickCoreSwing.isLeftPressed();
						boolean rightPressed = hovered && QuickCoreSwing.isRightPressed();
						StyledDocument.TextStyle textStyle;
						try {
							textStyle = doc.getStyle(staCtx(value, hovered, focused, pressed, rightPressed));
						} catch (ModelInstantiationException e) {
							doc.reporting().error(e.getMessage(), e);
							return;
						}
						if (textStyle != null) {
							QuickCoreSwing.adjustFont(style, textStyle);
							Color bgColor = textStyle.getColor().get();
							if (bgColor != null)
								style.withBackground(bgColor);
							if (hovered) {
								MouseCursor quickCursor = textStyle.getMouseCursor().get();
								Cursor cursor = null;
								if (quickCursor != null) {
									try {
										cursor = tx.transform(quickCursor, Cursor.class);
									} catch (ExpressoInterpretationException e) {
										doc.reporting().error("Unsupported cursor: " + quickCursor, e);
									}
								}
								if (cursor != null) {
									didSetCursor = true;
									theWidget.setCursor(cursor);
								} else if (didSetCursor) {
									didSetCursor = false;
									theWidget.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
								}
							}
						}
					}
				};
				if (doc.hasPostText()) {
					swingDoc.withPostNodeText(node -> {
						try {
							return doc.getPostText(staCtx(node, false, false, false, false)).get();
						} catch (ModelInstantiationException e) {
							doc.reporting().error(e.getMessage(), e);
							return null;
						}
					});
				}
				return swingDoc;
			}

			@Override
			public MouseAdapter mouseListener(StyledDocument<T> quickDoc, ObservableStyledDocument<T> doc, JTextComponent widget,
				Observable<?> until) {
				theWidget = widget;
				return new MouseAdapter() {
					@Override
					public void mousePressed(MouseEvent e) {
						T value = ((DynamicStyledDocument<T>) quickDoc).getNodeValue().get();
						if (value != null)
							doc.refresh(value, e);
						widget.setCursor(null);
					}

					@Override
					public void mouseReleased(MouseEvent e) {
						T value = ((DynamicStyledDocument<T>) quickDoc).getNodeValue().get();
						if (value != null)
							doc.refresh(value, e);
					}

					@Override
					public void mouseExited(MouseEvent e) {
						T value = ((DynamicStyledDocument<T>) quickDoc).getNodeValue().get();
						if (value != null)
							doc.refresh(value, e);
					}

					@Override
					public void mouseMoved(MouseEvent e) {
						int docPos = widget.viewToModel(e.getPoint());
						ObservableStyledDocument<T>.DocumentNode node = doc.getNodeAt(docPos);
						((DynamicStyledDocument<T>) quickDoc).getNodeValue().set(node == null ? null : node.getValue(), e);
						if (node != null)
							doc.refresh(node.getValue(), e);
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

	private static final SettableValue<Boolean> TRUE = SettableValue.of(true, "Unmodifiable");
	private static final SettableValue<Boolean> FALSE = SettableValue.of(false, "Unmodifiable");

	static <T> DynamicStyledDocument.StyledTextAreaContext<T> staCtx(T value, boolean hovered, boolean focused, boolean pressed,
		boolean rightPressed) {
		return new DynamicStyledDocument.StyledTextAreaContext.Default<>(//
			hovered ? TRUE : FALSE, focused ? TRUE : FALSE, pressed ? TRUE : FALSE, rightPressed ? TRUE : FALSE, //
				SettableValue.of(value, "Unmodifiable"));
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
				cb.withIcon(quick.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
			});
		}
	}

	static class SwingFieldPanel extends QuickSwingContainerPopulator.Abstract<QuickFieldPanel> {
		private BetterList<QuickSwingPopulator<QuickWidget>> theContents;

		SwingFieldPanel(QuickFieldPanel.Interpreted<?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theContents = BetterList.<QuickWidget.Interpreted<?>, QuickSwingPopulator<QuickWidget>, ExpressoInterpretationException> of2(
				interpreted.getContents().stream(), content -> tx.transform(content, QuickSwingPopulator.class));
		}

		@Override
		protected void doPopulateContainer(ContainerPopulator<?, ?> panel, QuickFieldPanel quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addVPanel(quick.isShowInvisible(), p -> {
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
				btn.withIcon(quick.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
			});
		}
	}

	static class SwingFileButton extends QuickSwingPopulator.Abstract<QuickFileButton> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickFileButton quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addFileField(null, quick.getValue(), quick.isOpen(), fb -> {
				ObservableFileButton ofb = fb.getEditor();
				quick.getFileDescrip().changes().takeUntil(fb.getUntil()).act(evt -> {
					ofb.withFileFilterDescrip(evt.getNewValue());
				});
				if (quick.getDefaultDir().get() != null)
					ofb.startAt(quick.getDefaultDir().get());
				SettableValue<File> defaultDir = quick.getDefaultDir();
				quick.getValue().noInitChanges().takeUntil(fb.getUntil()).act(evt -> {
					if (evt.getNewValue() != null && evt.getNewValue().getParentFile() != null//
						&& defaultDir.isAcceptable(evt.getNewValue().getParentFile()) == null) {
						defaultDir.set(evt.getNewValue().getParentFile(), evt);
						ofb.startAt(evt.getNewValue().getParentFile());
					}
				});
				component.accept(fb);
			});
		}
	}

	static class SwingComboBox<T> extends QuickSwingPopulator.Abstract<QuickComboBox<T>> {
		private final Map<Object, QuickSwingPopulator<QuickWidget>> theRenderers;

		SwingComboBox(QuickComboBox.Interpreted<T> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theRenderers=new HashMap<>();
			for(QuickWidget.Interpreted<?> renderer : interpreted.getRenderers())
				theRenderers.put(renderer.getIdentity(), tx.transform(renderer, QuickSwingPopulator.class));
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickComboBox<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			ComponentEditor<?, ?>[] combo = new ComponentEditor[1];
			TabularWidget.TabularContext<T> tableCtx = new TabularWidget.TabularContext.Default<>(quick.toString());
			quick.setContext(tableCtx);
			QuickSwingTablePopulation.QuickSwingRenderer<T, T, T> renderer = theRenderers == null ? null
				: new QuickSwingTablePopulation.QuickSwingRenderer<>(null, LambdaUtils.identity(), quick,
					quick.getValue(), quick.getRenderers(), tableCtx, () -> combo[0], theRenderers);
			panel.addComboField(null, quick.getValue(), quick.getValues(), cf -> {
				combo[0] = cf;
				component.accept(cf);
				if (theRenderers != null) {
					cf.renderWith(renderer);
					cf.withValueTooltip(v -> {
						ModelCell<T, T> cell = new ModelCell.Default<>(() -> v, v, 0, 0, false, false, false, false, false, true);
						return renderer.getTooltip(cell);
					});
				}
			});
		}
	}

	static class SwingSlider extends QuickSwingPopulator.Abstract<QuickSlider> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickSlider quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addSlider(null, quick.getValue(), slider -> {
				component.accept(slider);
				slider.withBounds(quick.getMin(), quick.getMax());
			});
		}
	}

	static class SwingSpinner<T> extends QuickSwingPopulator.Abstract<QuickSpinner<T>> {
		private final Class<T> theType;
		private Function<? super T, ? extends T> theDefaultPrevious;
		private Function<? super T, ? extends T> theDefaultNext;

		SwingSpinner(QuickSpinner.Interpreted<T> interpreted) throws ExpressoInterpretationException {
			theType = TypeTokens.get().unwrap(TypeTokens.getRawType(interpreted.getValueType()));
			// For integer types, can just use 1 as the increment
			if (interpreted.getNext() != null && interpreted.getPrevious() != null) {
				// They specified next and previous, so we don't have to worry about defaults
			} else if (theType == byte.class) {
				theDefaultPrevious = v -> {
					if (v == null)
						return (T) Byte.valueOf((byte) 0);
					byte b = ((Number) v).byteValue();
					if (b == Byte.MIN_VALUE)
						return null;
					return (T) Byte.valueOf((byte) (b - 1));
				};
				theDefaultNext = v -> {
					if (v == null)
						return (T) Byte.valueOf((byte) 1);
					byte b = ((Number) v).byteValue();
					if (b == Byte.MAX_VALUE)
						return null;
					return (T) Byte.valueOf((byte) (b + 1));
				};
			} else if (theType == short.class) {
				theDefaultPrevious = v -> {
					if (v == null)
						return (T) Short.valueOf((short) 0);
					short s = ((Number) v).shortValue();
					if (s == Short.MIN_VALUE)
						return null;
					return (T) Short.valueOf((short) (s - 1));
				};
				theDefaultNext = v -> {
					if (v == null)
						return (T) Short.valueOf((short) 1);
					short s = ((Number) v).shortValue();
					if (s == Short.MIN_VALUE)
						return null;
					return (T) Short.valueOf((short) (s + 1));
				};
			} else if (theType == int.class) {
				theDefaultPrevious = v -> {
					if (v == null)
						return (T) Integer.valueOf(0);
					int i = ((Number) v).intValue();
					if (i == Integer.MIN_VALUE)
						return null;
					return (T) Integer.valueOf(i - 1);
				};
				theDefaultNext = v -> {
					if (v == null)
						return (T) Integer.valueOf(1);
					int i = ((Number) v).intValue();
					if (i == Integer.MAX_VALUE)
						return null;
					return (T) Integer.valueOf(i + 1);
				};
			} else if (theType == long.class) {
				theDefaultPrevious = v -> {
					if (v == null)
						return (T) Long.valueOf(0);
					long i = ((Number) v).longValue();
					if (i == Long.MIN_VALUE)
						return null;
					return (T) Long.valueOf(i - 1);
				};
				theDefaultNext = v -> {
					if (v == null)
						return (T) Long.valueOf(1);
					long i = ((Number) v).longValue();
					if (i == Long.MAX_VALUE)
						return null;
					return (T) Long.valueOf(i + 1);
				};
			} else if (theType == boolean.class) {
				// Kinda weird, but alright
				theDefaultPrevious = v -> {
					if (v == null)
						return (T) Boolean.FALSE;
					else
						return (T) Boolean.valueOf(!((Boolean) v).booleanValue());
				};
				theDefaultNext = v -> {
					if (v == null)
						return (T) Boolean.TRUE;
					else
						return (T) Boolean.valueOf(!((Boolean) v).booleanValue());
				};
			} else if (Enum.class.isAssignableFrom(theType)) {
				// Logical enough as well
				theDefaultPrevious = v -> {
					if (v == null)
						return theType.getEnumConstants()[0];
					int ordinal = ((Enum<?>) v).ordinal();
					return ordinal == 0 ? null : theType.getEnumConstants()[ordinal - 1];
				};
				theDefaultNext = v -> {
					if (v == null)
						return theType.getEnumConstants()[0];
					int ordinal = ((Enum<?>) v).ordinal();
					return ordinal == theType.getEnumConstants().length - 1 ? null : theType.getEnumConstants()[ordinal + 1];
				};
			} else
				throw new ExpressoInterpretationException("Unable to determine logical increment for spinner of type " + theType
					+ ": 'previous' and 'next' attributes must be specified", interpreted.reporting().getPosition(), 0);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickSpinner<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			Function<? super T, ? extends T> previous, next;
			if (quick.getPrevious() != null) {
				previous = __ -> quick.getPrevious().get();
			} else
				previous = theDefaultPrevious;
			if (quick.getNext() != null) {
				next = __ -> quick.getNext().get();
			} else
				next = theDefaultNext;
			Format<T> format = quick.getFormat().get();
			boolean commitOnType = quick.isCommitOnType();
			Integer columns = quick.getColumns();
			panel.addSpinnerField(null, quick.getValue(), format, previous, next, tf -> {
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
					quick.isEditable().changes().takeUntil(tf.getUntil())
					.act(evt -> tf2.setTextEditable(!Boolean.FALSE.equals(evt.getNewValue())));
				});
			});
		}
	}

	static class SwingColorChooser extends QuickSwingPopulator.Abstract<QuickColorChooser> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickColorChooser quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			ObservableColorEditor editor = new ObservableColorEditor(quick.getValue(), true, quick.isWithAlpha(),
				Observable.or(panel.getUntil(), quick.onDestroy()));
			panel.addComponent(null, editor, c -> {
				component.accept(c);
			});
		}
	}

	static class SwingRadioButtons<T> extends QuickSwingPopulator.Abstract<QuickRadioButtons<T>> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickRadioButtons<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			MultiValueRenderContext<T> ctx = new MultiValueRenderContext.Default<>();
			quick.setContext(ctx);
			panel.addRadioField(null, quick.getValue(), quick.getValues(), rf -> {
				rf.withValueTooltip(v -> {
					ctx.getActiveValue().set(v, null);
					return quick.getValueTooltip().get();
				});
				component.accept(rf);
			});
		}
	}

	static class SwingToggleButtons<T> extends QuickSwingPopulator.Abstract<QuickToggleButtons<T>> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickToggleButtons<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addToggleField(null, quick.getValue(), quick.getValues(), __ -> new JToggleButton(), rf -> component.accept(rf));
		}
	}

	static class SwingCustomComponentPopulator extends QuickSwingPopulator.Abstract<QuickCustomComponent> {
		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickCustomComponent quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			Object componentV = quick.getComponent().get();
			if (componentV instanceof Component) {
				panel.addComponent(null, componentV, comp -> {
					component.accept(comp);
				});
			} else
				throw new ModelInstantiationException("Expected an instance of " + Component.class.getName() + ", not "
					+ (componentV == null ? "null" : componentV.getClass().getName()), quick.reporting().getPosition(), 0);
		}
	}

	/**
	 * Facilitates table extensions
	 *
	 * @param <R> The type of the row collection in the QuickTable widget
	 * @param <R2> The type of the row collection handled by PanelPopulation
	 * @param <C> The super-type of column IDs in the table
	 */
	public static class MappedTableConfig<R, R2, C> {
		final QuickTable.Interpreted<R, C, ?> theTable;
		final TriConsumer<R2, R, QuickWidget> theUpdate;
		final Function<R2, R> theReverse;

		/**
		 * @param table The interpreted table widget
		 * @param update The function to update a PanelPopulation value when a row changes in the Quick widget's row collection
		 * @param reverse The function to produce a Quick widget's row value from a PanelPopulation row
		 */
		public MappedTableConfig(Interpreted<R, C, ?> table, TriConsumer<R2, R, QuickWidget> update, Function<R2, R> reverse) {
			theTable = table;
			theUpdate = update;
			theReverse = reverse;
		}
	}

	static class SwingTable<R, R2, C> extends QuickSwingPopulator.Abstract<QuickTable<R, C>> {
		private final QuickSwingColumnSet<R, R2> theColumns;
		private final Function<R2, R> theReverse;
		private final Map<Object, QuickSwingTableAction<R, ValueAction<R>>> interpretedActions;
		private final Map<Object, QuickSwingPopulator<QuickWidget>> interpretedOptions;
		private final QuickSwingTransfer theDragging;

		SwingTable(QuickTable.Interpreted<R, C, ?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			this(interpreted, tx, null, (Function<R2, R>) LambdaUtils.identity());
		}

		SwingTable(MappedTableConfig<R, R2, C> config, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			this(config.theTable, tx, config.theUpdate, config.theReverse);
		}

		SwingTable(QuickTable.Interpreted<R, C, ?> table, Transformer<ExpressoInterpretationException> tx,
			TriConsumer<R2, R, QuickWidget> update, Function<R2, R> reverse) throws ExpressoInterpretationException {
			theReverse = reverse;
			theColumns = new QuickSwingColumnSet<>(table, table.getColumns(), tx, update, reverse);
			// TODO Changes to actions collection?
			interpretedActions = new HashMap<>();
			interpretedOptions = new HashMap<>();
			for (ValueAction.Interpreted<R, ?> action : table.getActions())
				interpretedActions.put(action.getIdentity(), tx.transform(action, QuickSwingTableAction.class));
			for (QuickWidget.Interpreted<?> option : table.getOptions())
				interpretedOptions.put(option.getIdentity(), tx.transform(option, QuickSwingPopulator.class));
			theDragging = new QuickSwingTransfer(table.getTransferSources(), table.getTransferAccepters(), tx);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTable<R, C> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			TabularWidget.TabularContext<R> ctx = new TabularWidget.TabularContext.Default<>(
				quick.reporting().getPosition().toShortString());
			quick.setContext(ctx);
			QuickSwingColumnSet<R, R2>.Populator columnPopulator = theColumns.createPopulator(quick, quick.getAllColumns(), ctx,
				panel.getUntil());
			panel.addTable((ObservableCollection<R2>) quick.getRows(), table -> {
				component.accept(table);
				populateTable(table, ctx, quick, columnPopulator);
			});
		}

		protected void populateTable(TableBuilder<R2, ?, ?> table, TabularWidget.TabularContext<R> ctx, QuickTable<R, C> quick,
			QuickSwingColumnSet<R, R2>.Populator columnPopulator) {
			columnPopulator.populate(table);
			switch (quick.getSelectionType()) {
			case column:
				table.rowSelection(false).columnSelection(true);
				break;
			case cell:
				table.rowSelection(true).columnSelection(true);
				break;
			case row:
			default:
				table.rowSelection(true).columnSelection(false);
				break;
			}
			table.withSelectionMode(swingSelectionMode(quick.getRowSelectionMode()));
			table.withColumnSelectionMode(swingSelectionMode(quick.getColumnSelectionMode()));
			if (LambdaUtils.isTrivial(theReverse)) {
				if (quick.getSelection() != null)
					table.withSelection((SettableValue<R2>) quick.getSelection(), false);
				if (quick.getMultiSelection() != null)
					table.withSelection((ObservableCollection<R2>) quick.getMultiSelection());
			}
			if (quick.getColumnSelection() != null)
				table.withColumnSelection((SettableValue<Object>) quick.getColumnSelection(), false);
			if (quick.getColumnMultiSelection() != null)
				table.withColumnSelection((ObservableCollection<Object>) quick.getColumnMultiSelection());
			table.withActionsOnTop(quick.isOptionsOnTop());
			for (ExElement aao : quick.getActionsAndOptions()) {
				if (aao instanceof ValueAction) {
					QuickSwingTableAction<R, ValueAction<R>> interp = interpretedActions.get(aao.getIdentity());
					if (interp == null)
						aao.reporting().warn("Could not find interpretation");
					else {
						try {
							interp.addAction(table, theReverse, (ValueAction<R>) aao);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}
				} else if (aao instanceof QuickWidget) {
					QuickSwingPopulator<QuickWidget> interp = interpretedOptions.get(aao.getIdentity());
					if (interp == null)
						aao.reporting().warn("Could not find interpretation");
					else {
						table.withTableOption(optionPopulator -> {
							try {
								interp.populate(optionPopulator, (QuickWidget) aao);
							} catch (ModelInstantiationException e) {
								throw new CheckedExceptionWrapper(e);
							}
						});
					}
				} else
					aao.reporting().warn("Unrecognized action/option " + aao.getClass().getName());
			}
			theDragging.configureTransferSources(cell -> {
				ctx.getActiveValue().set(theReverse.apply(cell.getModelValue()));
				ctx.isSelected().set(cell.isSelected());
				ctx.getRowIndex().set(cell.getRowIndex());
				ctx.getColumnIndex().set(cell.getColumnIndex());
			}, quick.getTransferSources(), table::dragSourceRow);
			theDragging.configureTransferAccepters(cell -> {
				if (cell != null) {
					ctx.getActiveValue().set(theReverse.apply(cell.getModelValue()));
					ctx.isSelected().set(cell.isSelected());
					ctx.getRowIndex().set(cell.getRowIndex());
					ctx.getColumnIndex().set(cell.getColumnIndex());
				} else {
					ctx.getActiveValue().set(null);
					ctx.isSelected().set(false);
					ctx.getRowIndex().set(0);
					ctx.getColumnIndex().set(0);
				}
			}, quick.getTransferAccepters(), table::dragAcceptRow);
			modifyTable(table, quick);
		}

		private static int swingSelectionMode(TableSelectionMode selectionMode) {
			switch (selectionMode) {
			case single:
				return ListSelectionModel.SINGLE_SELECTION;
			case contiguous:
				return ListSelectionModel.SINGLE_INTERVAL_SELECTION;
			case general:
			default:
				return ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;
			}
		}

		protected void modifyTable(TableBuilder<R2, ?, ?> table, QuickTable<R, C> quick) {
		}
	}

	static class SwingTree<N, T extends QuickTree<N>> extends QuickSwingPopulator.Abstract<T> {
		private final Map<Object, QuickSwingPopulator<QuickWidget>> theRenderers;
		private final Map<Object, QuickSwingPopulator<QuickWidget>> theEditors;
		private Map<Object, QuickSwingTableAction<BetterList<N>, ?>> interpretedActions;
		private final Map<Object, QuickSwingPopulator<QuickWidget>> interpretedOptions;
		private final QuickSwingTransfer theDragging;

		SwingTree(QuickTree.Interpreted<N, ?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			if (interpreted.getTreeColumn() == null) {
				theRenderers = theEditors = Collections.emptyMap();
			} else {
				theRenderers = new HashMap<>();
				theEditors = new HashMap<>();
				for (QuickWidget.Interpreted<?> renderer : interpreted.getTreeColumn().getRenderers())
					theRenderers.put(renderer.getIdentity(), tx.transform(renderer, QuickSwingPopulator.class));
				if (interpreted.getTreeColumn().getEditing() != null)
					for (QuickWidget.Interpreted<?> editor : interpreted.getTreeColumn().getEditing().getEditors())
						theEditors.put(editor.getIdentity(), tx.transform(editor, QuickSwingPopulator.class));
			}

			interpretedActions = new HashMap<>();
			interpretedOptions = new HashMap<>();
			for (ValueAction.Interpreted<BetterList<N>, ?> action : interpreted.getActions())
				interpretedActions.put(action.getIdentity(), tx.transform(action, QuickSwingTableAction.class));
			for (QuickWidget.Interpreted<?> option : interpreted.getOptions())
				interpretedOptions.put(option.getIdentity(), tx.transform(option, QuickSwingPopulator.class));
			theDragging = new QuickSwingTransfer(interpreted.getTransferSources(), interpreted.getTransferAccepters(), tx);
			if (interpreted.getTreeColumn() != null) {
				theDragging.withSources(interpreted.getTreeColumn().getTransferSources(), tx);
				theDragging.withAccepters(interpreted.getTreeColumn().getTransferAccepters(), tx);
			}
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, T quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			TabularWidget.TabularContext<BetterList<N>> ctx = new TabularWidget.TabularContext.Default<>(quick.reporting().toString());
			quick.setContext(ctx);
			ValueHolder<PanelPopulation.TreeEditor<N, ?>> treeHolder = new ValueHolder<>();
			InterpretedSwingTableColumn<BetterList<N>, BetterList<N>, N> treeColumn = getTreeColumn(quick, treeHolder, ctx,
				panel.getUntil());
			panel.addTree3(quick.getModel().getValue(), childrenProducer(quick), tree -> {
				component.accept(tree);
				treeHolder.accept(tree);
				populateTree(tree, quick, ctx);
				if (treeColumn != null)
					tree.withRender(treeColumn.getCRS());
			});
		}

		protected static <N> BiFunction<? super BetterList<N>, Observable<?>, ObservableCollection<? extends N>> childrenProducer(
			QuickTree<N> quick) {
			Map<BetterList<N>, CachedPathChildren<N>> childrenCache = new HashMap<>();
			return (parentPath, nodeUntil) -> {
				CachedPathChildren<N> children = childrenCache.get(parentPath);
				if (children != null) {
					children.refresh.onNext(null);
					return children.children;
				}
				try {
					SimpleObservable<Void> refresh = new SimpleObservable<>();
					children = new CachedPathChildren<>(//
						quick.getModel().getChildren(ObservableValue.of(parentPath).refresh(refresh), nodeUntil), refresh);
					childrenCache.put(parentPath, children);
					nodeUntil.take(1).act(__ -> childrenCache.remove(parentPath));
					return children.children;
				} catch (ModelInstantiationException e) {
					quick.reporting().error("Could not create children for " + parentPath, e);
					return null;
				}
			};
		}

		static class CachedPathChildren<N> {
			final ObservableCollection<? extends N> children;
			final SimpleObservable<Void> refresh;

			CachedPathChildren(ObservableCollection<? extends N> children, SimpleObservable<Void> refresh) {
				this.children = children;
				this.refresh = refresh;
			}
		}

		protected InterpretedSwingTableColumn<BetterList<N>, BetterList<N>, N> getTreeColumn(QuickTree<N> quick,
			ValueHolder<? extends PanelPopulation.AbstractTreeEditor<N, ?, ?>> treeHolder,
				TabularWidget.TabularContext<BetterList<N>> ctx, Observable<?> until) throws ModelInstantiationException {
			if (quick.getTreeColumn() == null)
				return null;
			return new InterpretedSwingTableColumn<>(quick,
				(QuickTableColumn<BetterList<N>, N>) quick.getTreeColumn().getColumns().getFirst(), null, LambdaUtils.identity(), ctx,
				until, treeHolder, theRenderers, theEditors, theDragging);
		}

		protected void populateTree(PanelPopulation.AbstractTreeEditor<N, ?, ?> tree, QuickTree<N> quick,
			MultiValueRenderable.MultiValueRenderContext<BetterList<N>> ctx) {
			if (quick.getSelection() != null)
				tree.withSelection(quick.getSelection(), false);
			if (quick.getMultiSelection() != null)
				tree.withSelection(quick.getMultiSelection());
			if (quick.getNodeSelection() != null)
				tree.withValueSelection(quick.getNodeSelection(), false);
			if (quick.getNodeMultiSelection() != null)
				tree.withValueSelection(quick.getNodeMultiSelection());
			tree.withLeafTest2(path -> {
				ctx.getActiveValue().set(path, null);
				return quick.getModel().isLeaf(path);
			});
			tree.withRootVisible(quick.isRootVisible());
			for (ExElement aao : quick.getActionsAndOptions()) {
				if (aao instanceof ValueAction) {
					QuickSwingTableAction<BetterList<N>, ?> interp = interpretedActions.get(aao.getIdentity());
					if (interp == null)
						aao.reporting().warn("Could not find interpretation");
					else {
						try {
							((QuickSwingTableAction<BetterList<N>, ValueAction<BetterList<N>>>) interp).addAction(tree,
								LambdaUtils.<BetterList<N>> identity(), (ValueAction<BetterList<N>>) aao);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					}
				} else if (aao instanceof QuickWidget) {
					QuickSwingPopulator<QuickWidget> interp = interpretedOptions.get(aao.getIdentity());
					if (interp == null)
						aao.reporting().warn("Could not find interpretation");
					else {
						tree.withTreeOption(optionPopulator -> {
							try {
								interp.populate(optionPopulator, (QuickWidget) aao);
							} catch (ModelInstantiationException e) {
								throw new CheckedExceptionWrapper(e);
							}
						});
					}
				} else
					aao.reporting().warn("Unrecognized action/option " + aao.getClass().getName());
			}
			theDragging.configureTransferSources(cell -> {
				ctx.getActiveValue().set(cell.getModelValue());
				ctx.isSelected().set(cell.isSelected());
			}, quick.getTransferSources(), tree::dragSourcePath);
			theDragging.configureTransferAccepters(cell -> {
				if (cell != null) {
					ctx.getActiveValue().set(cell.getModelValue());
					ctx.isSelected().set(cell.isSelected());
				} else {
					ctx.getActiveValue().set(null);
					ctx.isSelected().set(false);
				}
			}, quick.getTransferAccepters(), tree::dragAcceptPath);
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
					public boolean supportsShading() {
						return false;
					}

					@Override
					public AbstractQuickContainerPopulator withShading(Shading shading) {
						quick.reporting().warn("Shading not supported");
						return this;
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
					public AbstractQuickContainerPopulator addVPanel(boolean showInvisible, Consumer<PanelPopulator<JPanel, ?>> vPanel) {
						if (isFirst) {
							isFirst = false;
							s.firstV(showInvisible, (Consumer<PanelPopulator<?, ?>>) (Consumer<?>) vPanel);
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
					s.scrollable(quick.isScrollingVertically(), quick.isScrollingHorizontally());
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
			public boolean supportsShading() {
				return false;
			}

			@Override
			public AbstractQuickContainerPopulator withShading(Shading shading) {
				System.err.println("Shading not supported for scroll pane");
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> hPanel) {
				theScroll.withHContent(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) p));
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(boolean showInvisible, Consumer<PanelPopulator<JPanel, ?>> vPanel) {
				theScroll.withVContent(showInvisible, p -> vPanel.accept((PanelPopulator<JPanel, ?>) p));
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
			public boolean supportsShading() {
				return false;
			}

			@Override
			public AbstractQuickContainerPopulator withShading(Shading shading) {
				System.err.println("Shading not supported for scroll pane row header");
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> hPanel) {
				theScroll.withHRowHeader(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) p));
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(boolean showInvisible, Consumer<PanelPopulator<JPanel, ?>> vPanel) {
				theScroll.withVRowHeader(showInvisible, p -> vPanel.accept((PanelPopulator<JPanel, ?>) p));
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
			public boolean supportsShading() {
				return false;
			}

			@Override
			public AbstractQuickContainerPopulator withShading(Shading shading) {
				System.err.println("Shading not supported for scroll pane column header");
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> hPanel) {
				theScroll.withHColumnHeader(layout, p -> hPanel.accept((PanelPopulator<JPanel, ?>) p));
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(boolean showInvisible, Consumer<PanelPopulator<JPanel, ?>> vPanel) {
				throw new IllegalArgumentException("Vertical panel makes no sense for a column header");
			}
		}
	}

	static abstract class AbstractQuickSwingDialog<D extends QuickDialog> implements QuickSwingDialog<D> {
		private final Map<Class<? extends ExAddOn<?>>, WindowModifier<?>> theWindowModifiers;

		AbstractQuickSwingDialog(QuickDialog.Interpreted<? super D> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theWindowModifiers = new HashMap<>();
			for (ExAddOn.Interpreted<?, ?> addOn : interpreted.getAddOns()) {
				if (tx.supportsTransform(addOn, QuickSwingPopulator.WindowModifier.class))
					theWindowModifiers.put(addOn.getInstanceType(), tx.transform(addOn, QuickSwingPopulator.WindowModifier.class));
			}
		}

		@Override
		public void initialize(D dialog, Component parent, Observable<?> until) throws ModelInstantiationException {
			QuickAbstractWindow window = dialog.getAddOn(QuickAbstractWindow.class);
			Object preInit = preInitialize(dialog, window, until);
			// We allow for lazy initialization, because when we're doing this interpretation,
			// the root component may not have been added to the widget hierarchy yet.
			Window swingWindow = SwingUtilities.getWindowAncestor(parent);
			if (swingWindow != null)
				doInitialize(dialog, window, parent, swingWindow, preInit, until);
			else {
				int[] tryNumber = new int[] { 2 };
				QommonsTimer.TaskHandle[] taskHandle = new QommonsTimer.TaskHandle[1];
				boolean[] canceled = new boolean[1];
				until.take(1).act(__ -> canceled[0] = true);
				Runnable[] task = new Runnable[1];
				task[0] = () -> {
					if (canceled[0])
						return;
					Window swingWindow2 = SwingUtilities.getWindowAncestor(parent);
					if (swingWindow == null && tryNumber[0] < 5) {
						tryNumber[0]++;
						if (taskHandle[0] == null) {
							taskHandle[0] = QommonsTimer.getCommonInstance().build(task[0], null, false)//
								.onEDT();
						}
						taskHandle[0].runNextIn(Duration.ofMillis(100));
					} else
						doInitialize(dialog, window, parent, swingWindow2, preInit, until);
				};
				window.isVisible().value().filter(LambdaUtils.identity()).take(1).takeUntil(until).act(__ -> task[0].run());
			}
		}

		protected abstract Object preInitialize(D dialog, QuickAbstractWindow window, Observable<?> until)
			throws ModelInstantiationException;

		protected abstract void doInitialize(D dialog, QuickAbstractWindow window, Component parent, Window windowAncestor, Object preInit,
			Observable<?> until);

		protected void modifyWindow(D dialog, PanelPopulation.WindowBuilder<?, ?> window) {
			for (Map.Entry<Class<? extends ExAddOn<?>>, WindowModifier<?>> modifier : theWindowModifiers.entrySet()) {
				try {
					((WindowModifier<ExAddOn<?>>) modifier.getValue()).modifyWindow(window, dialog.getAddOn(modifier.getKey()));
				} catch (ModelInstantiationException e) {
					dialog.reporting().at(e.getPosition().getPosition()).error(e.getMessage(), e);
				}
			}
		}

		protected void windowClosed(QuickAbstractWindow window) {
			if (window.isVisible().isAcceptable(false) == null) {
				if (window.isVisible().isEventing())
					EventQueue.invokeLater(() -> windowClosed(window));
				else
					window.isVisible().set(false, null);
			}
		}
	}

	static QuickSwingDialog<QuickInfoDialog> interpretInfoDialog(QuickInfoDialog.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContent(), QuickSwingPopulator.class);
		return new QuickSwingDialog<QuickInfoDialog>() {
			private SettableValue<String> theType;
			private SettableValue<String> theTitle;
			private SettableValue<Image> theIcon;
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
					ThreadConstraint.EDT.invoke(() -> {
						Image icon = theIcon == null ? null : theIcon.get();
						int swingType;
						String type = theType.get();
						if (type == null)
							swingType = JOptionPane.INFORMATION_MESSAGE;
						else {
							switch (type.toLowerCase()) {
							case "info":
							case "information":
							case ".":
								swingType = JOptionPane.INFORMATION_MESSAGE;
								break;
							case "err":
							case "error":
							case "!":
								swingType = JOptionPane.ERROR_MESSAGE;
								break;
							case "warn":
							case "warning":
								swingType = JOptionPane.WARNING_MESSAGE;
								break;
							case "plain":
							case "":
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
							JOptionPane.showMessageDialog(parent, theContent, theTitle.get(), swingType, new ImageIcon(icon));
						else
							JOptionPane.showMessageDialog(parent, theContent, theTitle.get(), swingType);
						Runnable[] resetVisible = new Runnable[1];
						resetVisible[0] = () -> {
							if (window.isVisible().isAcceptable(false) == null) {
								if (window.isVisible().isEventing())
									EventQueue.invokeLater(resetVisible[0]);
								else
									window.isVisible().set(false, null);
							}
						};
						theOnClose.act(null);
					});
				});
			}
		};
	}

	static QuickSwingDialog<QuickConfirm> interpretConfirm(QuickConfirm.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContent(), QuickSwingPopulator.class);
		return new QuickSwingDialog<QuickConfirm>() {
			private SettableValue<String> theTitle;
			private SettableValue<Image> theIcon;
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
					ThreadConstraint.EDT.invoke(() -> {
						int result;
						Image icon = theIcon == null ? null : theIcon.get();
						if (icon != null)
							result = JOptionPane.showConfirmDialog(parent, theContent, theTitle.get(), JOptionPane.OK_CANCEL_OPTION,
								JOptionPane.QUESTION_MESSAGE, new ImageIcon(icon));
						else
							result = JOptionPane.showConfirmDialog(parent, theContent, theTitle.get(), JOptionPane.OK_CANCEL_OPTION);
						Runnable[] resetVisible = new Runnable[1];
						resetVisible[0] = () -> {
							if (window.isVisible().isAcceptable(false) == null) {
								if (window.isVisible().isEventing())
									EventQueue.invokeLater(resetVisible[0]);
								else
									window.isVisible().set(false, null);
							}
						};
						EventQueue.invokeLater(resetVisible[0]);
						if (result == JOptionPane.OK_OPTION)
							theOnConfirm.act(null);
						else if (theOnCancel.isEnabled() == null)
							theOnCancel.act(null);
					});
				});
			}
		};
	}

	static QuickSwingDialog<QuickFileChooser> interpretFileChooser(QuickFileChooser.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		return new AbstractQuickSwingDialog<QuickFileChooser>(interpreted, tx) {
			private SettableValue<String> theTitle;

			@Override
			protected Object preInitialize(QuickFileChooser dialog, QuickAbstractWindow window, Observable<?> until)
				throws ModelInstantiationException {
				return null;
			}

			@Override
			protected void doInitialize(QuickFileChooser dialog, QuickAbstractWindow window, Component parent, Window windowAncestor,
				Object preInit,
				Observable<?> until) {
				JFileChooser swingChooser = new JFileChooser();
				theTitle = window.getTitle();
				int mode;
				if (dialog.isFilesSelectable()) {
					if (dialog.isDirectoriesSelectable())
						mode = JFileChooser.FILES_AND_DIRECTORIES;
					else
						mode = JFileChooser.FILES_ONLY;
				} else
					mode = JFileChooser.DIRECTORIES_ONLY;
				swingChooser.setFileSelectionMode(mode);
				swingChooser.setMultiSelectionEnabled(dialog.isMultiSelectable());
				window.isVisible().value().takeUntil(until).safe(ThreadConstraint.EDT).filter(LambdaUtils.identity()).act(__ -> {
					display(swingChooser, parent, dialog, window);
				});
			}

			private void display(JFileChooser swingWindow, Component parent, QuickFileChooser dialog, QuickAbstractWindow window) {
				boolean satisfied = false;
				while (!satisfied) {
					File dir = dialog.getDirectory().get();
					if (dir != null)
						swingWindow.setCurrentDirectory(dir);
					swingWindow.setDialogTitle(theTitle.get());

					swingWindow.setFileFilter(new QuickFileFilter(dialog));

					int result;
					if (dialog.isOpen())
						result = swingWindow.showOpenDialog(parent);
					else
						result = swingWindow.showSaveDialog(parent);

					String enabled, title = null;
					if (result == JFileChooser.APPROVE_OPTION) {
						List<File> files;
						if (swingWindow.isMultiSelectionEnabled())
							files = Arrays.asList(swingWindow.getSelectedFiles());
						else
							files = Arrays.asList(swingWindow.getSelectedFile());
						enabled = dialog.filesChosen(files);
						if (enabled == null) {
							if (dialog.getDirectory().isAcceptable(swingWindow.getCurrentDirectory()) == null) {
								satisfied = true;
								dialog.getDirectory().set(swingWindow.getCurrentDirectory(), null);
							}
						} else
							title = "Selected file" + (files.size() == 1 ? "" : "s") + " not allowed";
					} else {
						enabled = dialog.getOnCancel().isEnabled().get();
						if (enabled == null) {
							satisfied = true;
							dialog.getOnCancel().act(null);
						} else
							title = dialog.isOpen() ? "A file must be chosen" : "The file must be saved";
					}
					if (!satisfied)
						JOptionPane.showMessageDialog(parent, enabled, title, JOptionPane.ERROR_MESSAGE);
				}
				windowClosed(window);
			}
		};
	}

	static class QuickFileFilter extends javax.swing.filechooser.FileFilter {
		private final QuickFileChooser theFileChooser;

		QuickFileFilter(QuickFileChooser fileChooser) {
			theFileChooser = fileChooser;
		}

		@Override
		public boolean accept(File f) {
			if (f == null)
				return false;
			else if (f.isDirectory())
				return true;
			else
				return theFileChooser.isFileAllowed(f) == null;
		}

		@Override
		public String getDescription() {
			return theFileChooser.getFileDescrip().get();
		}
	}

	static <D extends GeneralDialog> QuickSwingDialog<D> interpretGeneralDialog(GeneralDialog.Interpreted<D> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContent(), QuickSwingPopulator.class);
		return new AbstractQuickSwingDialog<D>(interpreted, tx) {
			@Override
			protected Object preInitialize(D dialog, QuickAbstractWindow window, Observable<?> until)
				throws ModelInstantiationException {
				QuickBaseSwing.ComponentExtractor ce = new ComponentExtractor(until);
				content.populate(ce, dialog.getContent());
				return ce.getExtractedComponent();
			}

			@Override
			protected void doInitialize(D dialog, QuickAbstractWindow window, Component parent, Window windowAncestor,
				Object preInit, Observable<?> until) {
				SettableValue<String> title = window.getTitle();
				JDialog jDialog = new JDialog(windowAncestor, //
					dialog.isModal() ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
				if (PanelPopulation.isDebugging(((Component) preInit).getName(), "general-dialog"))
					BreakpointHere.breakpoint();
				jDialog.setAlwaysOnTop(dialog.isAlwaysOnTop());
				PanelPopulation.WindowBuilder<JDialog, ?> swingDialog = WindowPopulation.populateDialog(jDialog, until, false);
				if (preInit instanceof Container)
					jDialog.setContentPane((Container) preInit);
				else
					jDialog.getContentPane().add((Component) preInit);
				swingDialog.withTitle(title);
				swingDialog.disposeOnClose(false);
				modifyWindow(dialog, swingDialog);
				swingDialog.run(parent);
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
		public boolean supportsShading() {
			return false;
		}

		@Override
		public AbstractQuickContainerPopulator withShading(Shading shading) {
			System.err.println("Shading not supported for window content");
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> panel) {
			theWindow.withHContent(layout, p -> panel.accept((PanelPopulator<JPanel, ?>) p));
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(boolean showInvisible, Consumer<PanelPopulator<JPanel, ?>> panel) {
			theWindow.withVContent(showInvisible, p -> panel.accept((PanelPopulator<JPanel, ?>) p));
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
			Map<T, TabsPopulator<T>> tabs = new HashMap<>();
			panel.addTabs(t -> {
				component.accept(t);
				Subscription sub = quick.getTabs().subscribe(evt -> {
					switch (evt.getType()) {
					case add:
						QuickSwingPopulator<QuickWidget> renderer = renderers.get(evt.getNewValue().getRenderer().getIdentity());
						TabsPopulator<T> tabPopulator = new TabsPopulator<>(t, evt.getNewValue(), evt.getIndex());
						tabs.put(evt.getNewValue().getTabValue(), tabPopulator);
						try {
							renderer.populate(tabPopulator, evt.getNewValue().getRenderer());
						} catch (ModelInstantiationException e) {
							evt.getNewValue().getRenderer().reporting().error("Failed to populate tab", e);
						}
						break;
					case remove:
						tabPopulator = tabs.remove(evt.getOldValue().getTabValue());
						if (tabPopulator != null)
							tabPopulator.remove();
						break;
					case set:
						break;
					}
				}, true);
				Observable.or(t.getUntil(), quick.onDestroy()).take(1).act(__ -> sub.unsubscribe());
				t.withSelectedTab(quick.getSelectedTab());
			});
		}

		private static class TabsPopulator<T> extends AbstractQuickContainerPopulator {
			private final PanelPopulation.TabPaneEditor<?, ?> theTabsEditor;
			private final QuickTabs.TabInstance<? extends T> theTab;
			private PanelPopulation.TabEditor<?> theTabEditor;
			private final int theTabIndex;

			TabsPopulator(PanelPopulation.TabPaneEditor<?, ?> tabEditor, QuickTabs.TabInstance<? extends T> tab, int index) {
				theTabsEditor = tabEditor;
				theTab = tab;
				theTabIndex = index;
			}

			void remove() {
				theTabEditor.remove();
			}

			@Override
			public Observable<?> getUntil() {
				return theTabsEditor.getUntil();
			}

			@Override
			public boolean supportsShading() {
				return false;
			}

			@Override
			public AbstractQuickContainerPopulator withShading(Shading shading) {
				System.err.println("Shading not supported for tab content");
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> panel) {
				theTabsEditor.withHTab(theTab.getTabValue(), theTabIndex, layout, tab -> panel.accept((PanelPopulator<JPanel, ?>) tab),
					this::configureTab);
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(boolean showInvisible, Consumer<PanelPopulator<JPanel, ?>> panel) {
				theTabsEditor.withVTab(theTab.getTabValue(), theTabIndex, showInvisible,
					tab -> panel.accept((PanelPopulator<JPanel, ?>) tab),
					this::configureTab);
				return this;
			}

			void configureTab(PanelPopulation.TabEditor<?> tab) {
				Observable<?> onRemove = Observable.or(theTab.isAvailable().value().filter(Boolean.FALSE::equals),
					theTab.getRenderer().onDestroy());
				theTabEditor = tab;
				ObservableAction removeTab = theTab.isAvailable().assignmentTo(ObservableValue.of(false));
				tab.setName(theTab.getTabName());
				tab.setIcon(theTab.getTabIcon());
				removeTab.isEnabled().changes().takeUntil(onRemove).act(e -> tab.setRemovable(e == null));
				tab.onRemove(cause -> {
					if (Boolean.TRUE.equals(theTab.isAvailable().get()) && theTab.isAvailable().isAcceptable(false) == null
						&& !theTab.isAvailable().isEventing())
						theTab.isAvailable().set(false, cause);
				});
				SettableValue<Boolean> visible = theTab.getRenderer().isVisible();
				tab.onSelect(onSelect -> {
					onSelect.changes().takeUntil(onRemove).act(evt -> {
						if (Boolean.TRUE.equals(evt.getNewValue())) {
							if (!Boolean.TRUE.equals(visible.get()) && visible.isAcceptable(true) == null && !visible.isEventing())
								visible.set(true);
						} else {
							if (Boolean.TRUE.equals(visible.get()) && visible.isAcceptable(false) == null && !visible.isEventing())
								visible.set(false);
						}
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
		public boolean supportsShading() {
			return false;
		}

		@Override
		public AbstractQuickContainerPopulator withShading(Shading shading) {
			System.err.println("Shading not supported");
			return this;
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
		public AbstractQuickContainerPopulator addVPanel(boolean showInvisible, Consumer<PanelPopulator<JPanel, ?>> panel) {
			PanelPopulation.PanelPopulator<JPanel, ?> populator = PanelPopulation.populateVPanel(null, theUntil);
			panel.accept(populator);
			theExtractedComponent = populator.getComponent();
			return this;
		}

		public Component getExtractedComponent() {
			return theExtractedComponent;
		}
	}
}