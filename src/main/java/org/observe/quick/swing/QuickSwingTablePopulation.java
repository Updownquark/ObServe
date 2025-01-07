package org.observe.quick.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.text.StyledDocument;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.Iconized;
import org.observe.quick.KeyCode;
import org.observe.quick.QuickEventListener;
import org.observe.quick.QuickKeyListener;
import org.observe.quick.QuickMouseListener;
import org.observe.quick.QuickTextWidget;
import org.observe.quick.QuickValueWidget;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.base.QuickTableColumn;
import org.observe.quick.base.TabularWidget;
import org.observe.quick.base.TabularWidget.TabularContext;
import org.observe.quick.base.ValueAction;
import org.observe.quick.base.ValueAction.Multi;
import org.observe.quick.base.ValueAction.Single;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingTableAction;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.CategoryRenderStrategy.CategoryKeyListener;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseListener;
import org.observe.util.swing.ComponentDecorator;
import org.observe.util.swing.ComponentPropertyManager;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.ModelCell;
import org.observe.util.swing.MultiRangeSlider;
import org.observe.util.swing.ObservableCellEditor;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.ObservableCellRenderer.AbstractObservableCellRenderer;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.observe.util.swing.ObservableStyledDocument;
import org.observe.util.swing.ObservableTextArea;
import org.observe.util.swing.ObservableTextField;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.Alert;
import org.observe.util.swing.PanelPopulation.ButtonEditor;
import org.observe.util.swing.PanelPopulation.CollectionWidgetBuilder;
import org.observe.util.swing.PanelPopulation.ComboEditor;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.LabelEditor;
import org.observe.util.swing.PanelPopulation.MenuBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.SimpleComponentEditor;
import org.observe.util.swing.PanelPopulation.SliderEditor;
import org.observe.util.swing.Shading;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.TriConsumer;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;
import org.qommons.io.Format;

/** Code to populate Quick-sourced tables in Java swing */
class QuickSwingTablePopulation {
	static class InterpretedSwingTableColumn<R, R2, C> {
		private final QuickTableColumn<R, C> theColumn;
		private final Function<R2, R> theReverse;
		final CategoryRenderStrategy<R2, C> theCRS;
		private ObservableCollection<InterpretedSwingTableColumn<R, R2, ?>> theColumns;
		private ElementId theElementId;

		public InterpretedSwingTableColumn(QuickWidget quickParent, QuickTableColumn<R, C> column,
			TriConsumer<R2, R, QuickWidget> update, Function<R2, R> reverse, TabularContext<R> context, Observable<?> until,
			Supplier<? extends ComponentEditor<?, ?>> parent, QuickSwingPopulator<QuickWidget> swingRenderer,
				QuickSwingPopulator<QuickWidget> swingEditor) throws ModelInstantiationException {
			theColumn = column;
			theReverse = reverse;
			theCRS = new CategoryRenderStrategy<>(column.getName().get(), TypeTokens.getRawType(column.getType()), row -> {
				try (Transaction t = QuickCoreSwing.rendering()) {
					context.getActiveValue().set(theReverse.apply(row), null);
					return column.getValue().get();
				}
			});

			theColumn.getName().noInitChanges().takeUntil(until).act(evt -> {
				theCRS.setName(evt.getNewValue());
				refresh();
			});
			column.getHeaderTooltip().changes().takeUntil(until).act(evt -> {
				theCRS.withHeaderTooltip(evt.getNewValue());
				refresh();
			});
			QuickSwingTableColumn<R, R2, C> renderer = new QuickSwingTableColumn<>(update, theReverse, quickParent, column, context, parent,
				swingRenderer, swingEditor);
			Observable.onRootFinish(theColumn.getRenderStyleChanges()).takeUntil(until).act(evt -> {
				refresh();
			});
			Integer width = column.getWidth();
			if (column.getMinWidth() != null)
				theCRS.withWidth("min", column.getMinWidth());
			else if (width != null)
				theCRS.withWidth("min", width);
			if (column.getPrefWidth() != null)
				theCRS.withWidth("pref", column.getPrefWidth());
			else if (width != null)
				theCRS.withWidth("pref", width);
			if (column.getMaxWidth() != null)
				theCRS.withWidth("max", column.getMaxWidth());
			else if (width != null)
				theCRS.withWidth("max", width);

			theCRS.withRenderer(renderer);
			theCRS.withValueTooltip((r, c) -> renderer.getTooltip(r, c));
			// The listeners may take a performance hit, so only add listening if they're there
			boolean[] mouseKey = new boolean[2];
			if (column.getRenderer() != null) {
				for (QuickEventListener listener : column.getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener)
						mouseKey[0] = true;
					else if (listener instanceof QuickKeyListener)
						mouseKey[1] = true;
				}
			}
			if (mouseKey[0])
				theCRS.addMouseListener(renderer);
			else
				theCRS.removeMouseListener(renderer);
			if (mouseKey[1])
				theCRS.withKeyListener(renderer);
			else
				theCRS.withKeyListener(null);
			if (column.getEditing() != null)
				theCRS.withMutation(renderer::mutation);
			if (!column.getTransferSources().isEmpty())
				column.getTransferSources().get(0).reporting().warn("Not implemented");
		}

		public void init(ObservableCollection<InterpretedSwingTableColumn<R, R2, ?>> columns, ElementId id) {
			theColumns = columns;
			theElementId = id;
		}

		public QuickTableColumn<R, C> getColumn() {
			return theColumn;
		}

		public CategoryRenderStrategy<R2, C> getCRS() {
			return theCRS;
		}

		void refresh() {
			if (theElementId != null && !QuickCoreSwing.isRendering())
				theColumns.mutableElement(theElementId).set(this);
		}
	}

	static class QuickSwingRenderer<R, R2, C> extends AbstractObservableCellRenderer<R2, C> {
		final TriConsumer<R2, R, QuickWidget> theUpdate;
		final Function<R2, R> theReverse;
		private final QuickWidget theQuickParent;
		private final Supplier<? extends ComponentEditor<?, ?>> theParent;
		private final QuickWidget theRenderer;
		private final SimpleObservable<Void> theRenderUntil;
		private final QuickWithBackground.BackgroundContext theRendererContext;
		protected final TabularWidget.TabularContext<R> theRenderTableContext;
		private ObservableCellRenderer<R2, C> theDelegate;
		private SimpleComponentEditor<?, ?> theComponent;
		private Runnable thePreRender;
		private final Supplier<C> theValue;

		private ObservableValue<String> theTooltip;
		private Function<ModelCell<? extends R2, ? extends C>, String> isEnabled;

		private boolean isUpdating;

		QuickSwingRenderer(TriConsumer<R2, R, QuickWidget> update, Function<R2, R> reverse, QuickWidget quickParent,
			Supplier<C> value, QuickWidget renderer, TabularWidget.TabularContext<R> ctx, Supplier<? extends ComponentEditor<?, ?>> parent,
				QuickSwingPopulator<QuickWidget> swingRenderer) throws ModelInstantiationException {
			theUpdate = update;
			theReverse = reverse;
			theQuickParent = quickParent;
			theParent = parent;
			theValue = value;
			theRenderer = renderer;
			theRenderTableContext = ctx;
			theRenderUntil = new SimpleObservable<>();

			SwingCellPopulator<R, R2, C> renderPopulator;
			if (swingRenderer != null) {
				renderPopulator = new SwingCellPopulator<>(this, true);
				theRendererContext = new QuickWithBackground.BackgroundContext.Default();
				theRenderer.setContext(theRendererContext);
			} else {
				renderPopulator = null;
				theRendererContext = null;
			}

			if (renderPopulator != null)
				swingRenderer.populate(renderPopulator, theRenderer);
		}

		protected JComponent getOwner() {
			ComponentEditor<?, ?> parentEditor = theParent.get();
			if (parentEditor == null)
				return null;
			else if (parentEditor.getEditor() instanceof JComponent)
				return (JComponent) parentEditor.getEditor();
			else if (parentEditor.getComponent() instanceof JComponent)
				return (JComponent) parentEditor.getComponent();
			else
				return null;
		}

		protected void onOwner(Consumer<JComponent> action) {
			JComponent owner = getOwner();
			if (owner != null)
				action.accept(owner);
		}

		public QuickWidget getRenderer() {
			return theRenderer;
		}

		public ComponentEditor<?, ?> getParent() {
			return theParent.get();
		}

		public QuickWidget getQuickParent() {
			return theQuickParent;
		}

		public TabularWidget.TabularContext<R> getContext() {
			return theRenderTableContext;
		}

		void delegateTo(ObservableCellRenderer<R2, C> delegate) {
			theDelegate = delegate;
		}

		void renderWith(SimpleComponentEditor<?, ?> component, Runnable preRender) {
			theComponent = component;
			thePreRender = preRender;
		}

		public boolean isUpdating() {
			return isUpdating;
		}

		public void setTooltip(ObservableValue<String> tooltip) {
			theTooltip = tooltip;
		}

		public void setEnabled(Function<ModelCell<? extends R2, ? extends C>, String> enabled) {
			isEnabled = enabled;
		}

		@Override
		public String renderAsText(ModelCell<? extends R2, ? extends C> cell) {
			setCellContext(cell, theRenderTableContext, false);
			if (theRenderer instanceof QuickTextWidget) {
				if (thePreRender != null)
					thePreRender.run();
				String text = ((QuickTextWidget<C>) theRenderer).getCurrentText();
				theRenderUntil.onNext(null);
				return text;
			} else {
				C colValue = theValue.get();
				return colValue == null ? "" : colValue.toString();
			}
		}

		@Override
		protected Component renderCell(Component parent, ModelCell<? extends R2, ? extends C> cell, CellRenderContext ctx) {
			isUpdating = true;
			try {
				setCellContext(cell, theRenderTableContext, false);
				if (thePreRender != null)
					thePreRender.run();
				Component render;
				try (Transaction t = QuickCoreSwing.rendering()) {
					if (theDelegate != null)
						render = theDelegate.getCellRendererComponent(parent, cell, ctx);
					else if (theComponent != null)
						render = theComponent.getComponent();
					else { // No renderer specified, use default
						theDelegate = ObservableCellRenderer.formatted(String::valueOf);
						render = theDelegate.getCellRendererComponent(parent, cell, ctx);
					}
				}
				theRenderUntil.onNext(null);
				return render;
			} finally {
				isUpdating = false;
			}
		}

		void setCellContext(ModelCell<? extends R2, ? extends C> cell, TabularWidget.TabularContext<R> tableCtx,
			boolean withValue) {
			try (Transaction t = QuickCoreSwing.rendering(); Causable.CausableInUse cause = Causable.cause()) {
				R reversed = theReverse.apply(cell.getModelValue());
				if (withValue || tableCtx.getActiveValue().get() != reversed) {
					// Had an issue with trees where the path was actually the same, but not identical.
					// If the active value is eventing, that almost certainly means it's being populated with the same value currently.
					if (tableCtx.getActiveValue().isEventing()) {
						if (!Objects.equals(tableCtx.getActiveValue().get(), reversed))
							theQuickParent.reporting().error("Got some mixed up observables");
					} else
						tableCtx.getActiveValue().set(reversed, null);
				}
				tableCtx.isSelected().set(cell.isSelected(), cause);
				tableCtx.getRowIndex().set(cell.getRowIndex(), cause);
				tableCtx.getColumnIndex().set(cell.getColumnIndex(), cause);
				if (tableCtx == theRenderTableContext && theRendererContext != null) {
					theRendererContext.isHovered().set(cell.isCellHovered(), cause);
					theRendererContext.isFocused().set(cell.isCellFocused(), cause);
					if (cell.isCellHovered()) {
						theRendererContext.isPressed().set(theQuickParent.isPressed().get(), cause);
						theRendererContext.isRightPressed().set(theQuickParent.isRightPressed().get(), cause);
					} else {
						theRendererContext.isPressed().set(false, cause);
						theRendererContext.isRightPressed().set(false, cause);
					}
				}
				String enabled = isEnabled == null ? null : isEnabled.apply(cell);
				if (enabled != null)
					cell.setEnabled(enabled);
			}
		}

		String getTooltip(R2 modelValue, C columnValue) {
			if (theTooltip == null)
				return null;
			try (Transaction t = QuickCoreSwing.rendering()) {
				if (theRenderTableContext.getActiveValue().get() != modelValue) {
					// Had an issue with trees where the path was actually the same, but not identical.
					// If the active value is eventing, that almost certainly means it's being populated with the same value currently.
					if (theRenderTableContext.getActiveValue().isEventing()) {
						if (!Objects.equals(theRenderTableContext.getActiveValue().get(), modelValue))
							theQuickParent.reporting().error("Got some mixed up observables");
					} else
						theRenderTableContext.getActiveValue().set(theReverse.apply(modelValue), null);
				}
				String enabled = null;
				if (isEnabled != null) {
					enabled = isEnabled.apply(new ModelCell.Default<>(() -> modelValue, columnValue, 0, 0, //
						false, false, true, true, false, false));
				}
				if (enabled == null && theRenderer instanceof QuickValueWidget)
					enabled = ((QuickValueWidget<?>) theRenderer).getDisabled().get();
				if (enabled != null)
					return enabled;
				return theTooltip.get();
			}
		}

		String getTooltip() {
			return theTooltip == null ? null : theTooltip.get();
		}

		ObservableValue<String> getTooltipValue() {
			return theTooltip == null ? ObservableValue.of(null) : theTooltip;
		}
	}

	static class QuickSwingTableColumn<R, R2, C> extends QuickSwingRenderer<R, R2, C>
	implements CategoryMouseListener<R2, C>, CategoryKeyListener<R2, C> {
		private final QuickTableColumn<R, C> theColumn;

		private final QuickTableColumn.ColumnEditContext<R, C> theEditContext;
		private ObservableCellEditor<R2, C> theCellEditor;

		private final QuickMouseListener.MouseButtonListenerContext theMouseContext;
		private final QuickKeyListener.KeyTypedContext theKeyTypeContext;
		private final QuickKeyListener.KeyCodeContext theKeyCodeContext;

		QuickSwingTableColumn(TriConsumer<R2, R, QuickWidget> update, Function<R2, R> reverse, QuickWidget quickParent,
			QuickTableColumn<R, C> column,
			TabularWidget.TabularContext<R> ctx,
			Supplier<? extends ComponentEditor<?, ?>> parent, QuickSwingPopulator<QuickWidget> swingRenderer,
				QuickSwingPopulator<QuickWidget> swingEditor) throws ModelInstantiationException {
			super(update, reverse, quickParent, column.getValue(), column.getRenderer(), ctx, parent, swingRenderer);
			theColumn = column;

			if (theColumn.getEditing() != null) {
				if (swingEditor != null) {
					swingEditor.populate(new SwingCellPopulator<>(this, false), theColumn.getEditing().getEditor());
				}
				theEditContext = new QuickTableColumn.ColumnEditContext.Default<>(
					theColumn.getEditing().reporting().getPosition().toShortString());
				theColumn.getEditing().setEditorContext(theEditContext);
			} else
				theEditContext = null;

			theMouseContext = new QuickMouseListener.MouseButtonListenerContext.Default();
			theKeyTypeContext = new QuickKeyListener.KeyTypedContext.Default();
			theKeyCodeContext = new QuickKeyListener.KeyCodeContext.Default();

			if (getRenderer() != null) {
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseButtonListener)
						((QuickMouseListener.QuickMouseButtonListener) listener).setListenerContext(theMouseContext);
					else if (listener instanceof QuickMouseListener)
						((QuickMouseListener) listener).setListenerContext(theMouseContext);
					else if (listener instanceof QuickKeyListener.QuickKeyTypedListener)
						((QuickKeyListener.QuickKeyTypedListener) listener).setListenerContext(theKeyTypeContext);
					else if (listener instanceof QuickKeyListener.QuickKeyCodeListener)
						((QuickKeyListener.QuickKeyCodeListener) listener).setListenerContext(theKeyCodeContext);
					else
						listener.reporting().error("Unhandled cell renderer listener type: " + listener.getClass().getName());
				}
			}
			if (theColumn.getEditing() != null)
				setEnabled(cell -> theColumn.getEditing().isEditable().get());
		}

		public QuickTableColumn<R, C> getColumn() {
			return theColumn;
		}

		void withEditor(ObservableCellEditor<R2, C> editor) {
			theCellEditor = editor;
		}

		@Override
		protected Component renderCell(Component parent, ModelCell<? extends R2, ? extends C> cell, CellRenderContext ctx) {
			Component rendered = super.renderCell(parent, cell, ctx);
			onOwner(o -> o.setCursor(rendered.getCursor()));
			return rendered;
		}

		void mutation(CategoryRenderStrategy<R2, C>.CategoryMutationStrategy mutation) {
			if (theColumn.getEditing() != null) {
				if (theColumn.getEditing().isEditable() != null) {
					mutation.editableIf((rowValue, colValue) -> {
						try (Transaction t = render(rowValue, colValue, theRenderTableContext)) {
							return theColumn.getEditing().isEditable().get() == null;
						}
					});
				} else {
					mutation.editableIf((rowValue, colValue) -> {
						try (Transaction t = render(rowValue, colValue, theRenderTableContext)) {
							return theColumn.getEditing().getFilteredColumnEditValue().isEnabled().get() == null;
						}
					});
				}
				if (theColumn.getEditing().isAcceptable() != null) {
					mutation.filterAccept((rowEl, colValue) -> {
						try (Transaction t = render(rowEl.get(), colValue, theEditContext)) {
							theEditContext.getEditColumnValue().set(colValue, null);
							return theColumn.getEditing().isAcceptable().get();
						}
					});
				} else {
					mutation.filterAccept((rowEl, colValue) -> {
						try (Transaction t = render(rowEl.get(), colValue, theRenderTableContext)) {
							return theColumn.getEditing().getFilteredColumnEditValue().isAcceptable(colValue);
						}
					});
				}
				if (theColumn.getEditing().getType() instanceof QuickTableColumn.ColumnEditType.RowModifyEditType) {
					QuickTableColumn.ColumnEditType.RowModifyEditType<R, C> editType = (QuickTableColumn.ColumnEditType.RowModifyEditType<R, C>) theColumn
						.getEditing().getType();
					mutation.mutateAttribute((rowValue, colValue) -> {
						try (Transaction t = render(rowValue, colValue, theEditContext)) {
							theEditContext.getEditColumnValue().set(colValue, null);
							editType.getCommit().act(null);
						}
					});
					mutation.withRowUpdate(editType.isRowUpdate());
				} else if (theColumn.getEditing().getType() instanceof QuickTableColumn.ColumnEditType.RowReplaceEditType) {
					QuickTableColumn.ColumnEditType.RowReplaceEditType<R, C> editType = (QuickTableColumn.ColumnEditType.RowReplaceEditType<R, C>) theColumn
						.getEditing().getType();
					if (LambdaUtils.isTrivial(theReverse)) {
						mutation.withRowValueSwitch((rowValue, colValue) -> {
							try (Transaction t = render(rowValue, colValue, theEditContext)) {
								theEditContext.getEditColumnValue().set(colValue, null);
								return (R2) editType.getReplacement().get();
							}
						});
					} else if (theUpdate != null) {
						mutation.mutateAttribute((rowValue, colValue) -> {
							try (Transaction t = render(rowValue, colValue, theEditContext)) {
								theEditContext.getEditColumnValue().set(colValue, null);
								theUpdate.accept(rowValue, editType.getReplacement().get(), getQuickParent());
							}
						});
					} else {
						theColumn.getEditing().reporting().error("Cannot support edit type " + theColumn.getEditing().getType()
							+ " for a mapped table without an update scheme");
					}
				} else
					theColumn.getEditing().reporting().error("Unhandled column edit type: " + theColumn.getEditing().getType());
				if (theCellEditor != null)
					mutation.withEditor(theCellEditor);
				Integer clicks = theColumn.getEditing().getClicks();
				if (clicks != null)
					mutation.clicks(clicks);
			}
		}

		Transaction render(R2 rowValue, C colValue, TabularWidget.TabularContext<R> ctx) {
			Transaction t = QuickCoreSwing.rendering();
			boolean success = false;
			try {
				R rv = theReverse.apply(rowValue);
				if (ctx.getActiveValue().get() != rv)
					ctx.getActiveValue().set(rv, null);
				if (ctx.getRowIndex().get().intValue() != 0)
					ctx.getRowIndex().set(0, null);
				if (ctx.getColumnIndex().get().intValue() != 0)
					ctx.getColumnIndex().set(0, null);
				if (ctx.isSelected().get())
					ctx.isSelected().set(false, null);
				success = true;
				return t;
			} finally {
				if (!success)
					t.close();
			}
		}

		void setEditCell(ModelCell<? extends R2, ? extends C> cell) {
			setCellContext(cell, theRenderTableContext, false);
			theEditContext.getActiveValue().set(theReverse.apply(cell.getModelValue()), null);
		}

		String isEditAcceptable(ModelCell<R2, C> cell, C editValue) {
			if (cell == null)
				return "Nothing being edited";
			setEditCell(cell);
			theEditContext.getEditColumnValue().set(editValue, null);
			return theColumn.getEditing().getFilteredColumnEditValue().isAcceptable(editValue);
		}

		@Override
		public void keyPressed(ModelCell<? extends R2, ? extends C> cell, KeyEvent e) {
			if (cell == null)
				return;
			try (Transaction t = QuickCoreSwing.rendering()) {
				setCellContext(cell, theRenderTableContext, true);
				KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
				if (code == null)
					return;
				theKeyCodeContext.getKeyCode().set(code, e);
				String tt = getTooltip();
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickKeyListener.QuickKeyCodeListener) {
						QuickKeyListener.QuickKeyCodeListener keyL = (QuickKeyListener.QuickKeyCodeListener) listener;
						if (!keyL.isPressed() || (keyL.getKeyCode() != null && keyL.getKeyCode() != code))
							continue;
						else if (!keyL.testFilter())
							continue;
						keyL.getAction().act(e);
					}
				}
				String newTT = getTooltip();
				if (!Objects.equals(tt, newTT))
					onOwner(o -> o.setToolTipText(newTT));
			}
		}

		@Override
		public void keyReleased(ModelCell<? extends R2, ? extends C> cell, KeyEvent e) {
			if (cell == null)
				return;
			try (Transaction t = QuickCoreSwing.rendering()) {
				setCellContext(cell, theRenderTableContext, true);
				KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
				if (code == null)
					return;
				theKeyCodeContext.getKeyCode().set(code, e);
				String tt = getTooltip();
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickKeyListener.QuickKeyCodeListener) {
						QuickKeyListener.QuickKeyCodeListener keyL = (QuickKeyListener.QuickKeyCodeListener) listener;
						if (!keyL.isPressed() || (keyL.getKeyCode() != null && keyL.getKeyCode() != code))
							continue;
						else if (!keyL.testFilter())
							continue;
						keyL.getAction().act(e);
					}
				}
				String newTT = getTooltip();
				if (!Objects.equals(tt, newTT))
					onOwner(o -> o.setToolTipText(newTT));
			}
		}

		@Override
		public void keyTyped(ModelCell<? extends R2, ? extends C> cell, KeyEvent e) {
			if (cell == null)
				return;
			try (Transaction t = QuickCoreSwing.rendering()) {
				setCellContext(cell, theRenderTableContext, true);
				char ch = e.getKeyChar();
				theKeyTypeContext.getTypedChar().set(ch, e);
				String tt = getTooltip();
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickKeyListener.QuickKeyTypedListener) {
						QuickKeyListener.QuickKeyTypedListener keyL = (QuickKeyListener.QuickKeyTypedListener) listener;
						if (keyL.getCharFilter() != 0 && keyL.getCharFilter() != ch)
							continue;
						else if (!keyL.testFilter())
							continue;
						keyL.getAction().act(e);
					}
				}
				String newTT = getTooltip();
				if (!Objects.equals(tt, newTT))
					onOwner(o -> o.setToolTipText(newTT));
			}
		}

		@Override
		public boolean isMovementListener() {
			for (QuickEventListener listener : getRenderer().getEventListeners()) {
				if (listener instanceof QuickMouseListener.QuickMouseMoveListener)
					return true;
			}
			return false;
		}

		@Override
		public void mouseClicked(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			try (Transaction t = QuickCoreSwing.rendering()) {
				QuickMouseListener.MouseButton eventButton = QuickCoreSwing.checkMouseEventType(e, null);
				if (eventButton == null)
					return;
				setCellContext(cell, theRenderTableContext, true);
				theMouseContext.getMouseButton().set(eventButton, e);
				theMouseContext.getX().set(e.getX(), e);
				theMouseContext.getY().set(e.getY(), e);
				String tt = getTooltip();
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseClickListener) {
						QuickMouseListener.QuickMouseClickListener mouseL = (QuickMouseListener.QuickMouseClickListener) listener;
						if (mouseL.getButton() != null && mouseL.getButton() != eventButton)
							continue;
						else if (mouseL.getClickCount() > 0 && e.getClickCount() != mouseL.getClickCount())
							continue;
						else if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
				String newTT = getTooltip();
				if (!Objects.equals(tt, newTT))
					onOwner(o -> o.setToolTipText(newTT));
			}
		}

		@Override
		public void mousePressed(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			try (Transaction t = QuickCoreSwing.rendering()) {
				QuickMouseListener.MouseButton eventButton = QuickCoreSwing.checkMouseEventType(e, null);
				if (eventButton == null)
					return;
				setCellContext(cell, theRenderTableContext, true);
				theMouseContext.getMouseButton().set(eventButton, e);
				theMouseContext.getX().set(e.getX(), e);
				theMouseContext.getY().set(e.getY(), e);
				String tt = getTooltip();
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMousePressedListener) {
						QuickMouseListener.QuickMousePressedListener mouseL = (QuickMouseListener.QuickMousePressedListener) listener;
						if (mouseL.getButton() != null && mouseL.getButton() != eventButton)
							continue;
						else if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
				String newTT = getTooltip();
				if (!Objects.equals(tt, newTT))
					onOwner(o -> o.setToolTipText(newTT));
			}
		}

		@Override
		public void mouseReleased(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			try (Transaction t = QuickCoreSwing.rendering()) {
				QuickMouseListener.MouseButton eventButton = QuickCoreSwing.checkMouseEventType(e, null);
				if (eventButton == null)
					return;
				setCellContext(cell, theRenderTableContext, true);
				theMouseContext.getMouseButton().set(eventButton, e);
				theMouseContext.getX().set(e.getX(), e);
				theMouseContext.getY().set(e.getY(), e);
				String tt = getTooltip();
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseReleasedListener) {
						QuickMouseListener.QuickMouseReleasedListener mouseL = (QuickMouseListener.QuickMouseReleasedListener) listener;
						if (mouseL.getButton() != null && mouseL.getButton() != eventButton)
							continue;
						else if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
				String newTT = getTooltip();
				if (!Objects.equals(tt, newTT))
					onOwner(o -> o.setToolTipText(newTT));
			}
		}

		@Override
		public void mouseEntered(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			try (Transaction t = QuickCoreSwing.rendering()) {
				setCellContext(cell, theRenderTableContext, true);
				theMouseContext.getX().set(e.getX(), e);
				theMouseContext.getY().set(e.getY(), e);
				String tt = getTooltip();
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseMoveListener) {
						QuickMouseListener.QuickMouseMoveListener mouseL = (QuickMouseListener.QuickMouseMoveListener) listener;
						if (mouseL.getEventType() != QuickMouseListener.MouseMoveEventType.Enter)
							continue;
						else if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
				String newTT = getTooltip();
				if (!Objects.equals(tt, newTT))
					onOwner(o -> o.setToolTipText(newTT));
			}
		}

		@Override
		public void mouseExited(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			try (Transaction t = QuickCoreSwing.rendering()) {
				setCellContext(cell, theRenderTableContext, true);
				theMouseContext.getX().set(e.getX(), e);
				theMouseContext.getY().set(e.getY(), e);
				String tt = getTooltip();
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseMoveListener) {
						QuickMouseListener.QuickMouseMoveListener mouseL = (QuickMouseListener.QuickMouseMoveListener) listener;
						if (mouseL.getEventType() != QuickMouseListener.MouseMoveEventType.Exit)
							continue;
						else if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
				String newTT = getTooltip();
				if (!Objects.equals(tt, newTT))
					onOwner(o -> o.setToolTipText(newTT));
			}
		}

		@Override
		public void mouseMoved(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			try (Transaction t = QuickCoreSwing.rendering()) {
				setCellContext(cell, theRenderTableContext, true);
				theMouseContext.getX().set(e.getX(), e);
				theMouseContext.getY().set(e.getY(), e);
				String tt = getTooltip();
				for (QuickEventListener listener : getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseMoveListener) {
						QuickMouseListener.QuickMouseMoveListener mouseL = (QuickMouseListener.QuickMouseMoveListener) listener;
						if (mouseL.getEventType() != QuickMouseListener.MouseMoveEventType.Move)
							continue;
						else if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
				String newTT = getTooltip();
				if (!Objects.equals(tt, newTT))
					onOwner(o -> o.setToolTipText(newTT));
			}
		}
	}

	static class SwingCellPopulator<R, R2, C>
	implements PanelPopulation.PartialPanelPopulatorImpl<Container, SwingCellPopulator<R, R2, C>> {
		private final QuickSwingRenderer<R, R2, C> theRenderer;
		private final QuickSwingTableColumn<R, R2, C> theEditor;
		private final boolean isRenderer;

		Color nonSelectionBG;
		Color nonSelectionFG;
		Color selectionBG;
		Color selectionFG;

		public SwingCellPopulator(QuickSwingRenderer<R, R2, C> cell, boolean renderer) {
			theRenderer = cell;
			isRenderer = renderer;
			if (!renderer) {
				if (!(cell instanceof QuickSwingTableColumn))
					throw new IllegalStateException("Editing unsupported for this type");
				theEditor = (QuickSwingTableColumn<R, R2, C>) cell;
			} else
				theEditor = null;

			UIDefaults uiValues = UIManager.getDefaults();
			nonSelectionBG = uiValues.getColor("Table.background");
			nonSelectionFG = uiValues.getColor("Table.foreground");
			selectionBG = uiValues.getColor("Table.selectionBackground");
			selectionFG = uiValues.getColor("Table.selectionForeground");
		}

		SwingCellPopulator<R, R2, C> unsupported(String message) {
			theRenderer.getRenderer().reporting()
			.warn(message + " unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
			return this;
		}

		@Override
		public SwingCellPopulator<R, R2, C> withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			return unsupported("Glass pane");
		}

		@Override
		public Container getContainer() {
			throw new IllegalStateException("Container retrieval unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
		}

		@Override
		public Component decorate(Component c) {
			return c;
		}

		@Override
		public boolean supportsShading() {
			return false;
		}

		@Override
		public SwingCellPopulator<R, R2, C> withShading(Shading shading) {
			return unsupported("Shading");
		}

		@Override
		public boolean isSyntheticRenderer() {
			return true;
		}

		@Override
		public SwingCellPopulator<R, R2, C> withFieldName(ObservableValue<String> fieldName) {
			return unsupported("Field name");
		}

		@Override
		public SwingCellPopulator<R, R2, C> modifyFieldLabel(Consumer<FontAdjuster> font) {
			return unsupported("Field label");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withPostLabel(ObservableValue<String> postLabel) {
			return unsupported("Post label");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withPostButton(String buttonText, ObservableAction action,
			Consumer<ButtonEditor<JButton, ?>> modify) {
			return unsupported("Post button");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withPostContent(Consumer<PanelPopulator<JPanel, ?>> content) {
			return unsupported("Post content");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withFont(Consumer<FontAdjuster> font) {
			return unsupported("Font");
		}

		@Override
		public Container getEditor() {
			throw new IllegalStateException("Container retrieval unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
		}

		@Override
		public SwingCellPopulator<R, R2, C> disableWith(ObservableValue<String> disabled) {
			return unsupported("Visibility");
		}

		@Override
		public SwingCellPopulator<R, R2, C> visibleWhen(ObservableValue<Boolean> visible) {
			return unsupported("Visibility");
		}

		@Override
		public SwingCellPopulator<R, R2, C> fill() {
			return unsupported("Fill");
		}

		@Override
		public SwingCellPopulator<R, R2, C> fillV() {
			return unsupported("Fill");
		}

		@Override
		public SwingCellPopulator<R, R2, C> decorate(Consumer<ComponentDecorator> decoration) {
			return unsupported("Decorate");
		}

		@Override
		public SwingCellPopulator<R, R2, C> repaintOn(Observable<?> repaint) {
			return unsupported("Repaint");
		}

		@Override
		public SwingCellPopulator<R, R2, C> modifyEditor(Consumer<? super Container> modify) {
			return unsupported("General editor modifier");
		}

		@Override
		public SwingCellPopulator<R, R2, C> modifyComponent(Consumer<Component> component) {
			return unsupported("General component modifier");
		}

		@Override
		public SwingCellPopulator<R, R2, C> modifyAssociatedComponents(Consumer<Component> component) {
			return unsupported("General component modifier");
		}

		@Override
		public void modifyAssociatedComponent(Component component) {}

		@Override
		public Component getComponent() {
			throw new IllegalStateException("Container retrieval unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withLayoutConstraints(Object constraints) {
			return unsupported("Layout constraints for cell renderer holder");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
			return unsupported("Popup menu");
		}

		@Override
		public SwingCellPopulator<R, R2, C> onMouse(Consumer<MouseEvent> onMouse) {
			return unsupported("Mouse events");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withName(String name) {
			return unsupported("Name");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withTooltip(ObservableValue<String> tooltip) {
			return unsupported("Tooltip");
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return ObservableValue.of(null);
		}

		@Override
		public Observable<?> getUntil() {
			if (theRenderer.getRenderer() != null)
				return theRenderer.getRenderer().onDestroy();
			else
				return Observable.empty();
		}

		@Override
		public void doAdd(SimpleComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			if (isRenderer)
				theRenderer.renderWith(field, field::reset);
			else
				unsupported("This editor type");
		}

		boolean isManaged(Component c, String property) {
			for (PropertyChangeListener listener : c.getPropertyChangeListeners(property)) {
				if (listener instanceof ComponentPropertyManager)
					return true;
			}
			return false;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<LabelEditor<JLabel, ?>> modify) {
			if (isRenderer) {
				LabelRenderEditor editor = new LabelRenderEditor(theRenderer);
				if (modify != null)
					modify.accept(editor);
				JLabel[] label = new JLabel[1];
				PanelPopulation.PartialPanelPopulatorImpl.super.addLabel(fieldName, field, format, tf -> {
					if (modify != null)
						modify.accept(tf);
					label[0] = tf.getEditor();
				});

				ObservableCellRenderer<R2, C> delegate = new AbstractObservableCellRenderer<R2, C>() {
					@Override
					public String renderAsText(ModelCell<? extends R2, ? extends C> cell) {
						theRenderer.getContext().getActiveValue().set(theRenderer.theReverse.apply(cell.getModelValue()), null);
						return format.apply(field.get());
					}

					@Override
					protected Component renderCell(Component parent, ModelCell<? extends R2, ? extends C> cell, CellRenderContext ctx) {
						if (!isManaged(label[0], "background")) {
							if (cell.getRowIndex() >= 0) {
								label[0].setOpaque(true);
								label[0].setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
							} else // For combo boxes, the row index is -1 and the label should not be opaque
								label[0].setOpaque(false);
						}
						if (!isManaged(label[0], "foreground"))
							label[0].setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
						theRenderer.getContext().getActiveValue().set(theRenderer.theReverse.apply(cell.getModelValue()), null);
						F fieldV = field.get();
						label[0].setText(format.apply(fieldV));
						String enabled = cell.isEnabled();
						if (enabled == null && theRenderer.getRenderer() instanceof QuickValueWidget)
							enabled = ((QuickValueWidget<?>) theRenderer.getRenderer()).getDisabled().get();
						label[0].setEnabled(enabled == null);
						cell.setEnabled(null); // Don't let the super class muck with our style
						editor.decorate(label[0]);
						return label[0];
					}
				};
				theRenderer.delegateTo(delegate);
			} else
				unsupported("Label");
			return this;
		}

		@Override
		public SwingCellPopulator<R, R2, C> addIcon(String fieldName, ObservableValue<Icon> icon,
			Consumer<ComponentEditor<JLabel, ?>> modify) {
			if (isRenderer) {
				ObservableCellRenderer<R2, C> delegate = ObservableCellRenderer.<R2, C> formatted(c -> "").setIcon(cell -> {
					theRenderer.getContext().getActiveValue().set(theRenderer.theReverse.apply(cell.getModelValue()), null);
					return icon.get();
				});
				FieldRenderEditor<JLabel> editor = new FieldRenderEditor<>(theRenderer);
				if (modify != null)
					modify.accept(editor);
				theRenderer.delegateTo(delegate);
			} else
				unsupported("Icon");
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addLink(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<Object> action, Consumer<ComponentEditor<JLabel, ?>> modify) {
			if (isRenderer) {
				JLabel[] label = new JLabel[1];
				ObservableCellRenderer<R2, C> delegate = ObservableCellRenderer.linkRenderer(cell -> {
					label[0].setOpaque(true);
					if (!isManaged(label[0], "background"))
						label[0].setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
					if (!isManaged(label[0], "foreground"))
						label[0].setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
					label[0].setEnabled(cell.isEnabled() == null);
					theRenderer.getContext().getActiveValue().set(theRenderer.theReverse.apply(cell.getModelValue()), null);
					F fieldValue = field.get();
					cell.setEnabled(null); // Don't let the super class muck with our style
					return format.apply(fieldValue);
				});
				FieldRenderEditor<JLabel> editor = new FieldRenderEditor<>(theRenderer);
				if (modify != null)
					modify.accept(editor);
				label[0] = editor.getEditor();
				theRenderer.delegateTo(delegate);
			} else
				unsupported("Link");
			return this;
		}

		@Override
		public SwingCellPopulator<R, R2, C> addCheckField(String fieldName, SettableValue<Boolean> field,
			Consumer<ButtonEditor<JCheckBox, ?>> modify) {
			if (isRenderer) {
				JCheckBox check = new JCheckBox();
				ObservableCellRenderer<R2, C> delegate = ObservableCellRenderer.checkRenderer(check, cell -> {
					check.setOpaque(true);
					if (!isManaged(check, "background"))
						check.setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
					if (!isManaged(check, "foreground"))
						check.setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
					return Boolean.TRUE.equals(field.get());
				});
				ButtonRenderEditor<JCheckBox, ?> editor = new ButtonRenderEditor<>(null, theRenderer);
				if (modify != null)
					modify.accept(editor);
				theRenderer.delegateTo(delegate);
			} else {
				if (TypeTokens.getRawType(TypeTokens.get().unwrap(theEditor.getColumn().getType())) != boolean.class)
					theEditor.getColumn().getEditing().getEditor().reporting()
					.error("Check box editor can only be used for boolean-type columns, not " + theEditor.getColumn().getType());
				else {
					JCheckBox check = new JCheckBox();
					ButtonRenderEditor<JCheckBox, ?> fieldEditor = new ButtonRenderEditor<>(null,
						ObservableCellEditor.createCheckBoxEditor(check, cell -> {
							check.setOpaque(true);
							if (!isManaged(check, "background"))
								check.setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
							if (!isManaged(check, "foreground"))
								check.setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
						}), check);
					if (modify != null)
						modify.accept(fieldEditor);
					theEditor.withEditor(fieldEditor.getCellEditor());
				}
			}
			return this;
		}

		@Override
		public SwingCellPopulator<R, R2, C> addButton(String buttonText, ObservableAction action,
			Consumer<ButtonEditor<JButton, ?>> modify) {
			ButtonRenderEditor<JButton, ?>[] editor = new SwingCellPopulator.ButtonRenderEditor[1];
			if (isRenderer) {
				JButton button = new JButton();
				ObservableCellRenderer<R2, C> delegate = ObservableCellRenderer.buttonRenderer(button, cell -> {
					if (!isManaged(button, "background"))
						button.setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
					if (!isManaged(button, "foreground"))
						button.setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
					return editor[0].theButtonText == null ? null : editor[0].theButtonText.get();
				});
				editor[0] = new ButtonRenderEditor<>(buttonText, delegate);
				delegate.modify(comp -> editor[0].decorateButton((JButton) comp));
				if (modify != null)
					modify.accept(editor[0]);
				theRenderer.delegateTo(delegate);
			} else {
				JButton button = new JButton();
				editor[0] = new ButtonRenderEditor<>(buttonText, ObservableCellEditor.createButtonCellEditor(colValue -> {
					editor[0].decorateButton(button);
					return editor[0].getButtonText().get();
				}, button, cell -> {
					if (!isManaged(button, "background"))
						button.setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
					if (!isManaged(button, "foreground"))
						button.setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
				}, cell -> {
					action.act(null);
					return cell.getCellValue();
				}), button);
				if (modify != null)
					modify.accept(editor[0]);
				theEditor.withEditor(editor[0].getCellEditor());
			}
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<ComponentEditor<ObservableTextField<F>, ?>> modify) {
			if (isRenderer)
				PanelPopulation.PartialPanelPopulatorImpl.super.addTextField(fieldName, field, format, modify);
			else {
				ObservableTextField<C>[] textField = new ObservableTextField[1];
				ObservableCellEditor<R, C> cellEditor = ObservableCellEditor.createTextEditor((Format<C>) format, tf -> textField[0] = tf);
				FieldRenderEditor<ObservableTextField<C>> fieldEditor = new FieldRenderEditor<>(cellEditor, textField[0]);
				if (modify != null)
					modify.accept((ComponentEditor<ObservableTextField<F>, ?>) (ComponentEditor<?, ?>) fieldEditor);
				theEditor.withEditor(fieldEditor.getCellEditor());
			}
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addStyledTextArea(String fieldName, ObservableStyledDocument<F> doc,
			Consumer<ComponentEditor<ObservableTextArea<F>, ?>> modify) {
			if (isRenderer) {
				FieldRenderEditor<ObservableTextArea<F>> editor = new FieldRenderEditor<>(theRenderer);
				if (modify != null)
					modify.accept(editor);
				ObservableTextArea<F>[] textArea = new ObservableTextArea[1];
				SimpleObservable<Void> renderUntil = new SimpleObservable<>();
				PanelPopulation.PartialPanelPopulatorImpl.super.addStyledTextArea(fieldName, doc, tf -> {
					if (modify != null)
						modify.accept(tf);
					textArea[0] = tf.getEditor();
				});
				textArea[0].setMargin(new Insets(0, 0, 0, 0));

				ObservableCellRenderer<R2, C> delegate = new AbstractObservableCellRenderer<R2, C>() {
					@Override
					public String renderAsText(ModelCell<? extends R2, ? extends C> cell) {
						theRenderer.getContext().getActiveValue().set(theRenderer.theReverse.apply(cell.getModelValue()), null);
						return doc.toString();
					}

					@Override
					protected Component renderCell(Component parent, ModelCell<? extends R2, ? extends C> cell, CellRenderContext ctx) {
						theRenderer.getContext().getActiveValue().set(theRenderer.theReverse.apply(cell.getModelValue()), null);
						editor.decorate(textArea[0]);
						doc.refresh(null);
						ObservableStyledDocument.synchronize(doc, ((StyledDocument) textArea[0].getDocument()), renderUntil);
						textArea[0].setDocument(textArea[0].getDocument());
						renderUntil.onNext(null);
						return textArea[0];
					}
				};
				theRenderer.delegateTo(delegate);
			} else
				unsupported("Text Area");
			return this;
		}

		@Override
		public SwingCellPopulator<R, R2, C> addSlider(String fieldName, SettableValue<Double> value,
			Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
			if (isRenderer)
				PanelPopulation.PartialPanelPopulatorImpl.super.addSlider(fieldName, value, modify);
			else { // TODO
				theEditor.getColumn().getEditing().getEditor().reporting().error("Slider cell editing is not implemented");
			}
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<ComboEditor<F, ?>> modify) {
			ObservableCollection<C> values;
			if (availableValues instanceof ObservableCollection)
				values = (ObservableCollection<C>) availableValues;
			else
				values = ObservableCollection.of((List<C>) availableValues);
			if (isRenderer)
				PanelPopulation.PartialPanelPopulatorImpl.super.addComboField(fieldName, value, availableValues, modify);
			else {
				JComboBox<C> combo = new JComboBox<>();
				ComboRenderEditor editor = new ComboRenderEditor(
					ObservableCellEditor.createComboEditor(String::valueOf, combo, (editCell, until) -> {
						theEditor.setEditCell(editCell);
						return values;
					}), combo);
				if (modify != null)
					modify.accept((ComboEditor<F, ?>) editor);
				theEditor.withEditor(editor.getCellEditor());
			}
			return this;
		}

		abstract class AbstractFieldRenderEditor<COMP extends Component, E extends AbstractFieldRenderEditor<COMP, E>>
		implements ComponentEditor<COMP, E> {
			private final ObservableCellRenderer<R2, C> theCellRenderer;
			private final ObservableCellEditor<R2, C> theCellEditor;
			private final COMP theEditorComponent;

			protected AbstractFieldRenderEditor(ObservableCellRenderer<R2, C> cellRenderer) {
				theCellRenderer = cellRenderer;
				theCellEditor = null;
				theEditorComponent = null;
			}

			protected AbstractFieldRenderEditor(ObservableCellEditor<R2, C> cellEditor, COMP component) {
				theCellRenderer = null;
				theCellEditor = cellEditor;
				theEditorComponent = component;
			}

			public ObservableCellRenderer<R2, C> getCellRenderer() {
				return theCellRenderer;
			}

			public ObservableCellEditor<R2, C> getCellEditor() {
				return theCellEditor;
			}

			E unsupported(String message) {
				theRenderer.getRenderer().reporting()
				.warn(message + " unsupported for cell " + (theCellRenderer == null ? "editor" : "renderer"));
				return (E) this;
			}

			@Override
			public Component decorate(Component c) {
				return c;
			}

			@Override
			public E withTooltip(ObservableValue<String> tooltip) {
				if (theCellRenderer != null)
					theRenderer.setTooltip(tooltip);
				return (E) this;
			}

			@Override
			public ObservableValue<String> getTooltip() {
				return theRenderer.getTooltipValue();
			}

			@Override
			public Observable<?> getUntil() {
				return SwingCellPopulator.this.getUntil();
			}

			@Override
			public E withFieldName(ObservableValue<String> fieldName) {
				return unsupported("Field name");
			}

			@Override
			public E modifyFieldLabel(Consumer<FontAdjuster> font) {
				return unsupported("Field label");
			}

			@Override
			public E withFont(Consumer<FontAdjuster> font) {
				if (theCellRenderer != null)
					theCellRenderer.decorate((cell, deco) -> font.accept(deco));
				else
					theCellEditor.decorate((cell, deco) -> font.accept(deco));
				return (E) this;
			}

			@Override
			public COMP getEditor() {
				return theEditorComponent;
			}

			@Override
			public E disableWith(ObservableValue<String> disabled) {
				// Disablement is unsupported, but don't throw a fit
				return (E) this;
			}

			@Override
			public E visibleWhen(ObservableValue<Boolean> visible) {
				// Visibility is unsupported, but don't throw a fit
				return (E) this;
			}

			@Override
			public E fill() {
				return unsupported("Fill");
			}

			@Override
			public E fillV() {
				return unsupported("Fill");
			}

			@Override
			public E decorate(Consumer<ComponentDecorator> decoration) {
				if (theCellRenderer != null)
					theCellRenderer.decorate((cell, deco) -> decoration.accept(deco));
				else
					theCellEditor.decorate((cell, deco) -> decoration.accept(deco));
				return (E) this;
			}

			@Override
			public E repaintOn(Observable<?> repaint) {
				unsupported("Repaint");
				return (E) this;
			}

			@Override
			public E modifyEditor(Consumer<? super COMP> modify) {
				if (theCellRenderer != null) {
					theCellRenderer.modify(comp -> {
						modify.accept((COMP) comp);
						return null;
					});
				} else {
					theCellEditor.modify(comp -> {
						modify.accept((COMP) comp);
						return null;
					});
				}
				return (E) this;
			}

			@Override
			public E modifyComponent(Consumer<Component> component) {
				if (theCellRenderer != null) {
					theCellRenderer.modify(comp -> {
						component.accept(comp);
						return null;
					});
				} else {
					theCellEditor.modify(comp -> {
						component.accept(comp);
						return null;
					});
				}
				return (E) this;
			}

			@Override
			public E modifyAssociatedComponents(Consumer<Component> component) {
				if (theCellRenderer != null)
					theCellRenderer.modifyAssociated(component);
				return (E) this;
			}

			@Override
			public void modifyAssociatedComponent(Component component) {}

			@Override
			public Component getComponent() {
				unsupported("Component retrieval");
				return null;
			}

			@Override
			public Alert alert(String title, String message) {
				return theRenderer.getParent().alert(title, message);
			}

			@Override
			public E withLayoutConstraints(Object constraints) {
				return unsupported("Layout constraints");
			}

			@Override
			public E withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
				theRenderer.getParent().withPopupMenu(menu);
				return (E) this;
			}

			@Override
			public E onMouse(Consumer<MouseEvent> onMouse) {
				return unsupported("Mouse events");
			}

			@Override
			public E withName(String name) {
				return (E) this;
			}

			@Override
			public E withPostLabel(ObservableValue<String> postLabel) {
				return unsupported("Post label");
			}

			@Override
			public E withPostButton(String buttonText, ObservableAction action, Consumer<ButtonEditor<JButton, ?>> modify) {
				return unsupported("Post button");
			}

			@Override
			public E withPostContent(Consumer<PanelPopulator<JPanel, ?>> content) {
				return unsupported("Post content");
			}
		}

		class FieldRenderEditor<COMP extends Component> extends AbstractFieldRenderEditor<COMP, FieldRenderEditor<COMP>> {
			FieldRenderEditor(ObservableCellRenderer<R2, C> cellRenderer) {
				super(cellRenderer);
			}

			FieldRenderEditor(ObservableCellEditor<R2, C> cellEditor, COMP editorComponent) {
				super(cellEditor, editorComponent);
			}
		}

		class LabelRenderEditor extends AbstractFieldRenderEditor<JLabel, LabelRenderEditor>
		implements LabelEditor<JLabel, LabelRenderEditor> {
			private ObservableValue<? extends Icon> theIcon;

			LabelRenderEditor(ObservableCellEditor<R2, C> cellEditor, JLabel editorComponent) {
				super(cellEditor, editorComponent);
			}

			LabelRenderEditor(ObservableCellRenderer<R2, C> cellRenderer) {
				super(cellRenderer);
			}

			@Override
			public LabelRenderEditor withIcon(ObservableValue<? extends Icon> icon) {
				theIcon = icon;
				return this;
			}

			@Override
			public Component decorate(Component c) {
				super.decorate(c);
				Icon icon;
				try {
					icon = theIcon == null ? null : theIcon.get();
				} catch (RuntimeException e) {
					theRenderer.getRenderer().reporting().error(e.toString(), e);
					icon = null;
				}
				if (icon != null && !c.isEnabled()) {
					icon = UIManager.getLookAndFeel().getDisabledIcon((JLabel) c, icon);
				}
				((JLabel) c).setIcon(icon);
				return c;
			}
		}

		class ButtonRenderEditor<B extends AbstractButton, E extends ButtonRenderEditor<B, E>> extends AbstractFieldRenderEditor<B, E>
		implements ButtonEditor<B, E> {
			ObservableValue<String> theButtonText;
			private ObservableValue<? extends Icon> theIcon;
			private ObservableValue<String> theDisabled;

			ButtonRenderEditor(String buttonText, ObservableCellRenderer<R2, C> cellRenderer) {
				super(cellRenderer);
				theButtonText = ObservableValue.of(buttonText);
			}

			ButtonRenderEditor(String buttonText, ObservableCellEditor<R2, C> cellEditor, B editorComponent) {
				super(cellEditor, editorComponent);
				theButtonText = ObservableValue.of(buttonText);
			}

			@Override
			public E withIcon(ObservableValue<? extends Icon> icon) {
				theIcon = icon;
				return (E) this;
			}

			@Override
			public E withText(ObservableValue<String> text) {
				theButtonText = text;
				return (E) this;
			}

			public ObservableValue<String> getButtonText() {
				return theButtonText;
			}

			@Override
			public E disableWith(ObservableValue<String> disabled) {
				if (theDisabled == null)
					theDisabled = disabled;
				else {
					ObservableValue<String> old = theDisabled;
					theDisabled = ObservableValue.firstValue(msg -> msg != null, () -> null, old, disabled);
				}
				return (E) this;
			}

			public Runnable decorateButton(JButton button) {
				button.setIcon(theIcon == null ? null : theIcon.get());
				String disabled = theDisabled == null ? null : theDisabled.get();
				button.setEnabled(disabled == null);
				if (disabled != null)
					button.setToolTipText(disabled);
				return null;
			}
		}

		class ComboRenderEditor extends AbstractFieldRenderEditor<JComboBox<C>, ComboRenderEditor>
		implements ComboEditor<C, ComboRenderEditor> {
			private Function<? super C, String> theValueTooltip;
			private IntSupplier theHoveredItem;

			public ComboRenderEditor(ObservableCellEditor<R2, C> cellEditor, JComboBox<C> editorComponent) {
				super(cellEditor, editorComponent);
			}

			void setHoveredItem(IntSupplier hoveredItem) {
				theHoveredItem = hoveredItem;
			}

			@Override
			public ComboRenderEditor renderWith(ObservableCellRenderer<C, C> renderer) {
				getCellEditor().modify(combo -> {
					((JComboBox<C>) combo).setRenderer(new ListCellRenderer<C>() {
						@Override
						public Component getListCellRendererComponent(JList<? extends C> list, C value, int index, boolean isSelected,
							boolean cellHasFocus) {
							boolean hovered = theHoveredItem != null && theHoveredItem.getAsInt() == index;
							ModelCell<C, C> cell = new ModelCell.Default<>(() -> value, value, index, 0, isSelected, cellHasFocus, hovered,
								hovered, true, true);
							cell.setEnabled(theEditor.isEditAcceptable(getCellEditor().getEditingCell(), value));
							return renderer.getCellRendererComponent(list, cell, CellRenderContext.DEFAULT);
						}
					});
					return null;
				});
				return this;
			}

			@Override
			public ComboRenderEditor withValueTooltip(Function<? super C, String> tooltip) {
				theValueTooltip = tooltip;
				return this;
			}

			@Override
			public String getTooltip(C value) {
				return theValueTooltip == null ? null : theValueTooltip.apply(value);
			}
		}
	}

	static <R> QuickSwingTableAction<R, ValueAction.Single<R>> interpretValueAction(ValueAction.Single.Interpreted<R, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		return new QuickSwingTableAction<R, ValueAction.Single<R>>() {
			@Override
			public <R2> void addAction(CollectionWidgetBuilder<R2, ?, ?> table, Function<R2, R> reverse, Single<R> action)
				throws ModelInstantiationException {
				ValueAction.SingleValueActionContext<R> ctx = new ValueAction.SingleValueActionContext.Default<>();
				action.setActionContext(ctx);
				long[] lastUpdate = new long[1];
				table.withAction(null, LambdaUtils.printableConsumer(v -> {
					if (!Objects.equals(v, ctx.getActionValue().get()))
						ctx.getActionValue().set(reverse.apply(v), null);
					action.getAction().act(null);
				}, () -> action.getAction().toString(), null), ta -> {
					ta.allowForEmpty(false);
					ta.allowForMultiple(action.allowForMultiple());
					ta.displayAsButton(action.isButton());
					ta.displayAsPopup(action.isPopup());
					ta.allowWhen(v -> {
						/* Had a problem here where updates to selection, e.g. via a model change, weren't updating action enablement.
						 * This was because the equals call in the if below didn't trigger, so the action value isn't updated,
						 * so the stamp isn't changed, so the out-of-date cached enablement was used.
						 *
						 * However, the if here serves the purpose that setting this value many times can be costly.
						 * So here's my solution.
						 */
						long now = System.currentTimeMillis();
						if (now - lastUpdate[0] > 3 || !Objects.equals(v, ctx.getActionValue().get())) {
							lastUpdate[0] = now;
							ctx.getActionValue().set(reverse.apply(v), null);
						}
						return action.getAction().isEnabled().get();
					}, null);
					ta.disableWith(action.getAction().isEnabled());
					ta.modifyButton(btn -> {
						btn.withText(action.getName());
						btn.withIcon(action.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
						btn.withTooltip(action.getTooltip());
					});
				});
			}
		};
	}

	static <R> QuickSwingTableAction<R, ValueAction.Multi<R>> interpretMultiValueAction(ValueAction.Multi.Interpreted<R, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		return new QuickSwingTableAction<R, ValueAction.Multi<R>>() {
			@Override
			public <R2> void addAction(CollectionWidgetBuilder<R2, ?, ?> table, Function<R2, R> reverse, Multi<R> action)
				throws ModelInstantiationException {
				ValueAction.MultiValueActionContext<R> ctx = new ValueAction.MultiValueActionContext.Default<>();
				action.setActionContext(ctx);
				Supplier<List<R>>[] actionValues = new Supplier[1];
				long[] lastUpdate = new long[1];
				table.withMultiAction(null, LambdaUtils.<List<? extends R2>> printableConsumer(values -> {
					if (!ctx.getActionValues().equals(values)) {
						try (Transaction t = ctx.getActionValues().lock(true, null)) {
							CollectionUtils.synchronize(ctx.getActionValues(), values, (av, v) -> Objects.equals(av, reverse.apply(v)))//
							.simple(reverse)//
							.rightOrder()//
							.adjust();
						}
					}
					action.getAction().act(null);
					CollectionUtils.synchronize(ctx.getActionValues(), actionValues[0].get()).simple(r -> r).adjust();
				}, () -> action.getAction().toString(), null), ta -> {
					actionValues[0] = () -> QommonsUtils.map(ta.getActionItems(), reverse, true);
					ta.allowForEmpty(action.allowForEmpty());
					ta.allowForMultiple(true);
					ta.displayAsButton(action.isButton());
					ta.displayAsPopup(action.isPopup());
					ta.allowWhenMulti(values -> {
						/* Had a problem here where updates to selection, e.g. via a model change, weren't updating action enablement.
						 * This was because the equals call in the if below didn't trigger, so the action value isn't updated,
						 * so the stamp isn't changed, so the out-of-date cached enablement was used.
						 *
						 * However, the if here serves the purpose that setting this value many times can be costly.
						 * So here's my solution.
						 */
						long now = System.currentTimeMillis();
						if (now - lastUpdate[0] > 3 || !ctx.getActionValues().equals(values)) {
							lastUpdate[0] = now;
							try (Transaction t = ctx.getActionValues().lock(true, null)) {
								CollectionUtils.synchronize(ctx.getActionValues(), values, (av, v) -> Objects.equals(av, reverse.apply(v)))//
								.simple(reverse)//
								.rightOrder()//
								.adjust();
							}
						}
						return action.isEnabled().get();
					}, null);
					ta.disableWith(action.getAction().isEnabled());
					ta.modifyButton(btn -> {
						btn.withText(action.getName());
						btn.withIcon(action.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
						btn.withTooltip(action.getTooltip());
					});
				});
			}
		};
	}
}
