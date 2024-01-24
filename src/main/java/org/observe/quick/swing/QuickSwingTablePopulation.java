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

import javax.swing.*;
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
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.base.QuickTableColumn;
import org.observe.quick.base.TabularWidget;
import org.observe.quick.base.TabularWidget.TabularContext;
import org.observe.quick.base.ValueAction;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingTableAction;
import org.observe.util.TypeTokens;
import org.observe.util.swing.*;
import org.observe.util.swing.CategoryRenderStrategy.CategoryKeyListener;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseListener;
import org.observe.util.swing.ObservableCellRenderer.AbstractObservableCellRenderer;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.observe.util.swing.PanelPopulation.AbstractComponentEditor;
import org.observe.util.swing.PanelPopulation.Alert;
import org.observe.util.swing.PanelPopulation.ButtonEditor;
import org.observe.util.swing.PanelPopulation.ComboEditor;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.FieldEditor;
import org.observe.util.swing.PanelPopulation.LabelEditor;
import org.observe.util.swing.PanelPopulation.MenuBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.SliderEditor;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/** Code to populate Quick-sourced tables in Java swing */
class QuickSwingTablePopulation {
	static class InterpretedSwingTableColumn<R, C> {
		private final QuickTableColumn<R, C> theColumn;
		final CategoryRenderStrategy<R, C> theCRS;
		private ObservableCollection<InterpretedSwingTableColumn<R, ?>> theColumns;
		private ElementId theElementId;

		public InterpretedSwingTableColumn(QuickWidget quickParent, QuickTableColumn<R, C> column, TabularContext<R> context,
			Observable<?> until, Supplier<? extends ComponentEditor<?, ?>> parent, QuickSwingPopulator<QuickWidget> swingRenderer,
				QuickSwingPopulator<QuickWidget> swingEditor) throws ModelInstantiationException {
			theColumn = column;
			theCRS = new CategoryRenderStrategy<>(column.getName().get(), column.getType(), row -> {
				try (Transaction t = QuickCoreSwing.rendering()) {
					context.getActiveValue().set(row, null);
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
			QuickSwingTableColumn<R, C> renderer = new QuickSwingTableColumn<>(quickParent, column, context, parent, swingRenderer,
				swingEditor);
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
			theCRS.withValueTooltip(renderer::getTooltip);
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
		}

		public void init(ObservableCollection<InterpretedSwingTableColumn<R, ?>> columns, ElementId id) {
			theColumns = columns;
			theElementId = id;
		}

		public CategoryRenderStrategy<R, C> getCRS() {
			return theCRS;
		}

		void refresh() {
			if (theElementId != null && !QuickCoreSwing.isRendering())
				theColumns.mutableElement(theElementId).set(this);
		}
	}

	static class QuickSwingRenderer<R, C> extends AbstractObservableCellRenderer<R, C> {
		private final QuickWidget theQuickParent;
		private final Supplier<? extends ComponentEditor<?, ?>> theParent;
		private final QuickWidget theRenderer;
		private final SimpleObservable<Void> theRenderUntil;
		private final QuickWithBackground.BackgroundContext theRendererContext;
		protected final TabularWidget.TabularContext<R> theRenderTableContext;
		private ObservableCellRenderer<R, C> theDelegate;
		private AbstractComponentEditor<?, ?> theComponent;
		private Runnable thePreRender;
		private final TypeToken<C> theValueType;
		private final Supplier<C> theValue;

		protected JComponent theOwner;
		private ObservableValue<String> theTooltip;
		private Function<ModelCell<? extends R, ? extends C>, String> isEnabled;

		QuickSwingRenderer(QuickWidget quickParent, TypeToken<C> valueType, Supplier<C> value, QuickWidget renderer,
			TabularWidget.TabularContext<R> ctx, Supplier<? extends ComponentEditor<?, ?>> parent,
				QuickSwingPopulator<QuickWidget> swingRenderer) throws ModelInstantiationException {
			theQuickParent = quickParent;
			theParent = parent;
			theValueType = valueType;
			theValue = value;
			theRenderer = renderer;
			theRenderTableContext = ctx;
			theRenderUntil = new SimpleObservable<>();

			SwingCellPopulator<R, C> renderPopulator;
			if (swingRenderer != null) {
				renderPopulator = new SwingCellPopulator<>(this, true);
				// This is in the modifier because we don't have the component yet
				swingRenderer.addModifier((comp, w) -> {
					comp.modifyComponent(c -> {
						theOwner = getOwner(parent.get());
					});
				});

				theRendererContext = new QuickWithBackground.BackgroundContext.Default();
				theRenderer.setContext(theRendererContext);
			} else {
				renderPopulator = null;
				theRendererContext = null;
			}

			if (renderPopulator != null)
				swingRenderer.populate(renderPopulator, theRenderer);
		}

		protected JComponent getOwner(ComponentEditor<?, ?> parentEditor) {
			if (parentEditor.getEditor() instanceof JComponent)
				return (JComponent) parentEditor.getEditor();
			else if (parentEditor.getComponent() instanceof JComponent)
				return (JComponent) parentEditor.getComponent();
			else
				return null;
		}

		public QuickWidget getRenderer() {
			return theRenderer;
		}

		public ComponentEditor<?, ?> getParent() {
			return theParent.get();
		}

		public TypeToken<C> getValueType() {
			return theValueType;
		}

		public TabularWidget.TabularContext<R> getContext() {
			return theRenderTableContext;
		}

		void delegateTo(ObservableCellRenderer<R, C> delegate) {
			theDelegate = delegate;
		}

		void renderWith(AbstractComponentEditor<?, ?> component, Runnable preRender) {
			theComponent = component;
			thePreRender = preRender;
		}

		public void setTooltip(ObservableValue<String> tooltip) {
			theTooltip = tooltip;
		}

		public void setEnabled(Function<ModelCell<? extends R, ? extends C>, String> enabled) {
			isEnabled = enabled;
		}

		@Override
		public String renderAsText(ModelCell<? extends R, ? extends C> cell) {
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
		protected Component renderCell(Component parent, ModelCell<? extends R, ? extends C> cell, CellRenderContext ctx) {
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
		}

		void setCellContext(ModelCell<? extends R, ? extends C> cell, TabularWidget.TabularContext<R> tableCtx,
			boolean withValue) {
			try (Transaction t = QuickCoreSwing.rendering(); Causable.CausableInUse cause = Causable.cause()) {
				if (withValue || tableCtx.getActiveValue().get() != cell.getModelValue())
					tableCtx.getActiveValue().set(cell.getModelValue(), null);
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

		String getTooltip(R modelValue, C columnValue) {
			if (theTooltip == null)
				return null;
			try (Transaction t = QuickCoreSwing.rendering()) {
				theRenderTableContext.getActiveValue().set(modelValue, null);
				return theTooltip.get();
			}
		}

		String getTooltip() {
			return theTooltip == null ? null : theTooltip.get();
		}

		ObservableValue<String> getTooltipValue() {
			return theTooltip == null ? ObservableValue.of(String.class, null) : theTooltip;
		}
	}

	static class QuickSwingTableColumn<R, C> extends QuickSwingRenderer<R, C>
	implements CategoryMouseListener<R, C>, CategoryKeyListener<R, C> {
		private final QuickTableColumn<R, C> theColumn;

		private final QuickTableColumn.ColumnEditContext<R, C> theEditContext;
		private ObservableCellEditor<R, C> theCellEditor;

		private final QuickMouseListener.MouseButtonListenerContext theMouseContext;
		private final QuickKeyListener.KeyTypedContext theKeyTypeContext;
		private final QuickKeyListener.KeyCodeContext theKeyCodeContext;

		QuickSwingTableColumn(QuickWidget quickParent, QuickTableColumn<R, C> column, TabularWidget.TabularContext<R> ctx,
			Supplier<? extends ComponentEditor<?, ?>> parent, QuickSwingPopulator<QuickWidget> swingRenderer,
				QuickSwingPopulator<QuickWidget> swingEditor) throws ModelInstantiationException {
			super(quickParent, column.getType(), column.getValue(), column.getRenderer(), ctx, parent, swingRenderer);
			theColumn = column;

			if (theColumn.getEditing() != null) {
				if (swingEditor != null) {
					swingEditor.populate(new SwingCellPopulator<>(this, false), theColumn.getEditing().getEditor());
				}
				theEditContext = new QuickTableColumn.ColumnEditContext.Default<>(theColumn.getColumnSet().getRowType(),
					theColumn.getType(), theColumn.getEditing().reporting().getPosition().toShortString());
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

		void withEditor(ObservableCellEditor<R, C> editor) {
			theCellEditor = editor;
		}

		@Override
		protected Component renderCell(Component parent, ModelCell<? extends R, ? extends C> cell, CellRenderContext ctx) {
			Component rendered = super.renderCell(parent, cell, ctx);
			if (theOwner != null && cell.isCellHovered())
				theOwner.setCursor(rendered.getCursor());
			return rendered;
		}

		void mutation(CategoryRenderStrategy<R, C>.CategoryMutationStrategy mutation) {
			if (theColumn.getEditing() != null) {
				if (theColumn.getEditing().isEditable() != null) {
					mutation.editableIf((rowValue, colValue) -> {
						try (Transaction t = QuickCoreSwing.rendering()) {
							theRenderTableContext.getActiveValue().set(rowValue, null);
							theRenderTableContext.getRowIndex().set(0, null);
							theRenderTableContext.getColumnIndex().set(0, null);
							theRenderTableContext.isSelected().set(false, null);
							return theColumn.getEditing().isEditable().get() == null;
						}
					});
				} else {
					mutation.editableIf((rowValue, colValue) -> {
						try (Transaction t = QuickCoreSwing.rendering()) {
							theRenderTableContext.getActiveValue().set(rowValue, null);
							theRenderTableContext.getRowIndex().set(0, null);
							theRenderTableContext.getColumnIndex().set(0, null);
							theRenderTableContext.isSelected().set(false, null);
							return theColumn.getEditing().getFilteredColumnEditValue().isEnabled().get() == null;
						}
					});
				}
				if (theColumn.getEditing().isAcceptable() != null) {
					mutation.filterAccept((rowEl, colValue) -> {
						try (Transaction t = QuickCoreSwing.rendering()) {
							theEditContext.getActiveValue().set(rowEl.get(), null);
							theEditContext.getEditColumnValue().set(colValue, null);
							theEditContext.getRowIndex().set(0, null);
							theEditContext.getColumnIndex().set(0, null);
							return theColumn.getEditing().isAcceptable().get();
						}
					});
				} else {
					mutation.filterAccept((rowEl, colValue) -> {
						try (Transaction t = QuickCoreSwing.rendering()) {
							theRenderTableContext.getActiveValue().set(rowEl.get(), null);
							theRenderTableContext.getRowIndex().set(0, null);
							theRenderTableContext.getColumnIndex().set(0, null);
							theRenderTableContext.isSelected().set(false, null);
							return theColumn.getEditing().getFilteredColumnEditValue().isAcceptable(colValue);
						}
					});
				}
				if (theColumn.getEditing().getType() instanceof QuickTableColumn.ColumnEditType.RowModifyEditType) {
					QuickTableColumn.ColumnEditType.RowModifyEditType<R, C> editType = (QuickTableColumn.ColumnEditType.RowModifyEditType<R, C>) theColumn
						.getEditing().getType();
					mutation.mutateAttribute((rowValue, colValue) -> {
						try (Transaction t = QuickCoreSwing.rendering()) {
							theEditContext.getActiveValue().set(rowValue, null);
							theEditContext.getEditColumnValue().set(colValue, null);
							editType.getCommit().act(null);
						}
					});
					mutation.withRowUpdate(editType.isRowUpdate());
				} else if (theColumn.getEditing().getType() instanceof QuickTableColumn.ColumnEditType.RowReplaceEditType) {
					QuickTableColumn.ColumnEditType.RowReplaceEditType<R, C> editType = (QuickTableColumn.ColumnEditType.RowReplaceEditType<R, C>) theColumn
						.getEditing().getType();
					mutation.withRowValueSwitch((rowValue, colValue) -> {
						try (Transaction t = QuickCoreSwing.rendering()) {
							theEditContext.getActiveValue().set(rowValue, null);
							theEditContext.getEditColumnValue().set(colValue, null);
							return editType.getReplacement().get();
						}
					});
				} else
					theColumn.getEditing().reporting().error("Unhandled column edit type: " + theColumn.getEditing().getType());
				if (theCellEditor != null)
					mutation.withEditor(theCellEditor);
				Integer clicks = theColumn.getEditing().getClicks();
				if (clicks != null)
					mutation.clicks(clicks);
			}
		}

		String isEditAcceptable(ModelCell<R, C> cell, C editValue) {
			if (cell == null)
				return "Nothing being edited";
			setCellContext(cell, theRenderTableContext, false);
			theEditContext.getActiveValue().set(cell.getModelValue(), null);
			theEditContext.getEditColumnValue().set(editValue, null);
			return theColumn.getEditing().getFilteredColumnEditValue().isAcceptable(editValue);
		}

		@Override
		public void keyPressed(ModelCell<? extends R, ? extends C> cell, KeyEvent e) {
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
					theOwner.setToolTipText(newTT);
			}
		}

		@Override
		public void keyReleased(ModelCell<? extends R, ? extends C> cell, KeyEvent e) {
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
					theOwner.setToolTipText(newTT);
			}
		}

		@Override
		public void keyTyped(ModelCell<? extends R, ? extends C> cell, KeyEvent e) {
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
					theOwner.setToolTipText(newTT);
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
		public void mouseClicked(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
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
					theOwner.setToolTipText(newTT);
			}
		}

		@Override
		public void mousePressed(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
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
					theOwner.setToolTipText(newTT);
			}
		}

		@Override
		public void mouseReleased(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
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
					theOwner.setToolTipText(newTT);
			}
		}

		@Override
		public void mouseEntered(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
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
					theOwner.setToolTipText(newTT);
			}
		}

		@Override
		public void mouseExited(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
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
					theOwner.setToolTipText(newTT);
			}
		}

		@Override
		public void mouseMoved(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
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
					theOwner.setToolTipText(newTT);
			}
		}
	}

	static class SwingCellPopulator<R, C> implements PanelPopulation.PartialPanelPopulatorImpl<Container, SwingCellPopulator<R, C>> {
		private final QuickSwingRenderer<R, C> theRenderer;
		private final QuickSwingTableColumn<R, C> theEditor;
		private final boolean isRenderer;

		Color nonSelectionBG;
		Color nonSelectionFG;
		Color selectionBG;
		Color selectionFG;

		public SwingCellPopulator(QuickSwingRenderer<R, C> cell, boolean renderer) {
			theRenderer = cell;
			isRenderer = renderer;
			if (!renderer) {
				if (!(cell instanceof QuickSwingTableColumn))
					throw new IllegalStateException("Editing unsupported for this type");
				theEditor = (QuickSwingTableColumn<R, C>) cell;
			} else
				theEditor = null;

			UIDefaults uiValues = UIManager.getDefaults();
			nonSelectionBG = uiValues.getColor("Table.background");
			nonSelectionFG = uiValues.getColor("Table.foreground");
			selectionBG = uiValues.getColor("Table.selectionBackground");
			selectionFG = uiValues.getColor("Table.selectionForeground");
		}

		SwingCellPopulator<R, C> unsupported(String message) {
			theRenderer.getRenderer().reporting()
			.warn(message + " unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
			return this;
		}

		@Override
		public SwingCellPopulator<R, C> withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
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
		public SwingCellPopulator<R, C> withShading(Shading shading) {
			return unsupported("Shading");
		}

		@Override
		public boolean isSyntheticRenderer() {
			return true;
		}

		@Override
		public SwingCellPopulator<R, C> withFieldName(ObservableValue<String> fieldName) {
			return unsupported("Field name");
		}

		@Override
		public SwingCellPopulator<R, C> modifyFieldLabel(Consumer<FontAdjuster> font) {
			return unsupported("Field label");
		}

		@Override
		public SwingCellPopulator<R, C> withFont(Consumer<FontAdjuster> font) {
			return unsupported("Font");
		}

		@Override
		public Container getEditor() {
			throw new IllegalStateException("Container retrieval unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
		}

		@Override
		public SwingCellPopulator<R, C> visibleWhen(ObservableValue<Boolean> visible) {
			return unsupported("Visibility");
		}

		@Override
		public SwingCellPopulator<R, C> fill() {
			return unsupported("Fill");
		}

		@Override
		public SwingCellPopulator<R, C> fillV() {
			return unsupported("Fill");
		}

		@Override
		public SwingCellPopulator<R, C> decorate(Consumer<ComponentDecorator> decoration) {
			return unsupported("Decorate");
		}

		@Override
		public SwingCellPopulator<R, C> repaintOn(Observable<?> repaint) {
			return unsupported("Repaint");
		}

		@Override
		public SwingCellPopulator<R, C> modifyEditor(Consumer<? super Container> modify) {
			return unsupported("General editor modifier");
		}

		@Override
		public SwingCellPopulator<R, C> modifyComponent(Consumer<Component> component) {
			return unsupported("General component modifier");
		}

		@Override
		public SwingCellPopulator<R, C> modifyAssociatedComponents(Consumer<Component> component) {
			return unsupported("General component modifier");
		}

		@Override
		public void modifyAssociatedComponent(Component component) {}

		@Override
		public Component getComponent() {
			throw new IllegalStateException("Container retrieval unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
		}

		@Override
		public SwingCellPopulator<R, C> withLayoutConstraints(Object constraints) {
			return unsupported("Layout constraints for cell renderer holder");
		}

		@Override
		public SwingCellPopulator<R, C> withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
			return unsupported("Popup menu");
		}

		@Override
		public SwingCellPopulator<R, C> onMouse(Consumer<MouseEvent> onMouse) {
			return unsupported("Mouse events");
		}

		@Override
		public SwingCellPopulator<R, C> withName(String name) {
			return unsupported("Name");
		}

		@Override
		public SwingCellPopulator<R, C> withTooltip(ObservableValue<String> tooltip) {
			return unsupported("Tooltip");
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return ObservableValue.of(String.class, null);
		}

		@Override
		public Observable<?> getUntil() {
			return theRenderer.getRenderer().onDestroy();
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
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
		public <F> SwingCellPopulator<R, C> addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
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

				ObservableCellRenderer<R, C> delegate = new AbstractObservableCellRenderer<R, C>() {
					@Override
					public String renderAsText(ModelCell<? extends R, ? extends C> cell) {
						theRenderer.getContext().getActiveValue().set(cell.getModelValue(), null);
						return format.apply(field.get());
					}

					@Override
					protected Component renderCell(Component parent, ModelCell<? extends R, ? extends C> cell, CellRenderContext ctx) {
						if (!isManaged(label[0], "background")) {
							if (cell.getRowIndex() >= 0) {
								label[0].setOpaque(true);
								label[0].setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
							} else // For combo boxes, the row index is -1 and the label should not be opaque
								label[0].setOpaque(false);
						}
						if (!isManaged(label[0], "foreground"))
							label[0].setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
						label[0].setEnabled(cell.isEnabled() == null);
						theRenderer.getContext().getActiveValue().set(cell.getModelValue(), null);
						F fieldV = field.get();
						label[0].setText(format.apply(fieldV));
						editor.decorate(label[0]);
						cell.setEnabled(null); // Don't let the super class muck with our style
						return label[0];
					}
				};
				theRenderer.delegateTo(delegate);
			} else
				unsupported("Label");
			return this;
		}

		@Override
		public SwingCellPopulator<R, C> addIcon(String fieldName, ObservableValue<Icon> icon, Consumer<FieldEditor<JLabel, ?>> modify) {
			if (isRenderer) {
				ObservableCellRenderer<R, C> delegate = ObservableCellRenderer.<R, C> formatted(c -> "").setIcon(cell -> {
					theRenderer.getContext().getActiveValue().set(cell.getModelValue(), null);
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
		public <F> SwingCellPopulator<R, C> addLink(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<Object> action, Consumer<FieldEditor<JLabel, ?>> modify) {
			if (isRenderer) {
				JLabel[] label = new JLabel[1];
				ObservableCellRenderer<R, C> delegate = ObservableCellRenderer.linkRenderer(cell -> {
					label[0].setOpaque(true);
					if (!isManaged(label[0], "background"))
						label[0].setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
					if (!isManaged(label[0], "foreground"))
						label[0].setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
					label[0].setEnabled(cell.isEnabled() == null);
					theRenderer.getContext().getActiveValue().set(cell.getModelValue(), null);
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
		public SwingCellPopulator<R, C> addCheckField(String fieldName, SettableValue<Boolean> field,
			Consumer<ButtonEditor<JCheckBox, ?>> modify) {
			if (isRenderer) {
				JCheckBox check = new JCheckBox();
				ObservableCellRenderer<R, C> delegate = ObservableCellRenderer.checkRenderer(check, cell -> {
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
		public SwingCellPopulator<R, C> addButton(String buttonText, ObservableAction action, Consumer<ButtonEditor<JButton, ?>> modify) {
			ButtonRenderEditor<JButton, ?>[] editor = new SwingCellPopulator.ButtonRenderEditor[1];
			if (isRenderer) {
				JButton button = new JButton();
				ObservableCellRenderer<R, C> delegate = ObservableCellRenderer.buttonRenderer(button, cell -> {
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
		public <F> SwingCellPopulator<R, C> addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify) {
			if (isRenderer)
				PanelPopulation.PartialPanelPopulatorImpl.super.addTextField(fieldName, field, format, modify);
			else {
				ObservableTextField<C>[] textField = new ObservableTextField[1];
				ObservableCellEditor<R, C> cellEditor = ObservableCellEditor.createTextEditor((Format<C>) format, tf -> textField[0] = tf);
				FieldRenderEditor<ObservableTextField<C>> fieldEditor = new FieldRenderEditor<>(cellEditor, textField[0]);
				if (modify != null)
					modify.accept((FieldEditor<ObservableTextField<F>, ?>) (FieldEditor<?, ?>) fieldEditor);
				theEditor.withEditor(fieldEditor.getCellEditor());
			}
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, C> addStyledTextArea(String fieldName, ObservableStyledDocument<F> doc,
			Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify) {
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

				ObservableCellRenderer<R, C> delegate = new AbstractObservableCellRenderer<R, C>() {
					@Override
					public String renderAsText(ModelCell<? extends R, ? extends C> cell) {
						theRenderer.getContext().getActiveValue().set(cell.getModelValue(), null);
						return doc.toString();
					}

					@Override
					protected Component renderCell(Component parent, ModelCell<? extends R, ? extends C> cell, CellRenderContext ctx) {
						theRenderer.getContext().getActiveValue().set(cell.getModelValue(), null);
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
		public SwingCellPopulator<R, C> addSlider(String fieldName, SettableValue<Double> value,
			Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
			if (isRenderer)
				PanelPopulation.PartialPanelPopulatorImpl.super.addSlider(fieldName, value, modify);
			else { // TODO
				theEditor.getColumn().getEditing().getEditor().reporting().error("Slider cell editing is not implemented");
			}
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, C> addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<ComboEditor<F, ?>> modify) {
			ObservableCollection<C> values;
			if (availableValues instanceof ObservableCollection)
				values = (ObservableCollection<C>) availableValues;
			else
				values = ObservableCollection.of(theRenderer.getValueType(), (List<C>) availableValues);
			if (isRenderer)
				PanelPopulation.PartialPanelPopulatorImpl.super.addComboField(fieldName, value, availableValues, modify);
			else {
				JComboBox<C> combo = new JComboBox<>();
				ComboRenderEditor editor = new ComboRenderEditor(
					ObservableCellEditor.createComboEditor(String::valueOf, combo, (v, until) -> values), combo);
				if (modify != null)
					modify.accept((ComboEditor<F, ?>) editor);
				theEditor.withEditor(editor.getCellEditor());
			}
			return this;
		}

		abstract class AbstractFieldRenderEditor<COMP extends Component, E extends AbstractFieldRenderEditor<COMP, E>>
		implements FieldEditor<COMP, E> {
			private final ObservableCellRenderer<R, C> theCellRenderer;
			private final ObservableCellEditor<R, C> theCellEditor;
			private final COMP theEditorComponent;

			protected AbstractFieldRenderEditor(ObservableCellRenderer<R, C> cellRenderer) {
				theCellRenderer = cellRenderer;
				theCellEditor = null;
				theEditorComponent = null;
			}

			protected AbstractFieldRenderEditor(ObservableCellEditor<R, C> cellEditor, COMP component) {
				theCellRenderer = null;
				theCellEditor = cellEditor;
				theEditorComponent = component;
			}

			public ObservableCellRenderer<R, C> getCellRenderer() {
				return theCellRenderer;
			}

			public ObservableCellEditor<R, C> getCellEditor() {
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
				theCellEditor.decorate((cell, deco) -> font.accept(deco));
				return (E) this;
			}

			@Override
			public COMP getEditor() {
				return theEditorComponent;
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
		}

		class FieldRenderEditor<COMP extends Component> extends AbstractFieldRenderEditor<COMP, FieldRenderEditor<COMP>> {
			FieldRenderEditor(ObservableCellRenderer<R, C> cellRenderer) {
				super(cellRenderer);
			}

			FieldRenderEditor(ObservableCellEditor<R, C> cellEditor, COMP editorComponent) {
				super(cellEditor, editorComponent);
			}
		}

		class LabelRenderEditor extends AbstractFieldRenderEditor<JLabel, LabelRenderEditor>
		implements LabelEditor<JLabel, LabelRenderEditor> {
			private ObservableValue<? extends Icon> theIcon;

			LabelRenderEditor(ObservableCellEditor<R, C> cellEditor, JLabel editorComponent) {
				super(cellEditor, editorComponent);
			}

			LabelRenderEditor(ObservableCellRenderer<R, C> cellRenderer) {
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
				((JLabel) c).setIcon(theIcon == null ? null : theIcon.get());
				return c;
			}
		}

		class ButtonRenderEditor<B extends AbstractButton, E extends ButtonRenderEditor<B, E>> extends AbstractFieldRenderEditor<B, E>
		implements ButtonEditor<B, E> {
			ObservableValue<String> theButtonText;
			private ObservableValue<? extends Icon> theIcon;
			private ObservableValue<String> theDisabled;

			ButtonRenderEditor(String buttonText, ObservableCellRenderer<R, C> cellRenderer) {
				super(cellRenderer);
				theButtonText = ObservableValue.of(String.class, buttonText);
			}

			ButtonRenderEditor(String buttonText, ObservableCellEditor<R, C> cellEditor, B editorComponent) {
				super(cellEditor, editorComponent);
				theButtonText = ObservableValue.of(String.class, buttonText);
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
					theDisabled = ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, old, disabled);
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

			public ComboRenderEditor(ObservableCellEditor<R, C> cellEditor, JComboBox<C> editorComponent) {
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
		return (table, action) -> {
			ValueAction.SingleValueActionContext<R> ctx = new ValueAction.SingleValueActionContext.Default<>(action.getValueType());
			action.setActionContext(ctx);
			long[] lastUpdate = new long[1];
			table.withAction(null, LambdaUtils.printableConsumer(v -> {
				if (!Objects.equals(v, ctx.getActionValue().get()))
					ctx.getActionValue().set(v, null);
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
						ctx.getActionValue().set(v, null);
					}
					return action.getAction().isEnabled().get();
				}, null);
				ta.disableWith(action.getAction().isEnabled());
				ta.modifyButton(btn -> {
					btn.withText(action.getName());
					btn.withIcon(action.getAddOn(Iconized.class).getIcon());
					btn.withTooltip(action.getTooltip());
				});
			});
		};
	}

	static <R> QuickSwingTableAction<R, ValueAction.Multi<R>> interpretMultiValueAction(ValueAction.Multi.Interpreted<R, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		return (table, action) -> {
			ValueAction.MultiValueActionContext<R> ctx = new ValueAction.MultiValueActionContext.Default<>(action.getValueType());
			action.setActionContext(ctx);
			Supplier<List<R>>[] actionValues = new Supplier[1];
			long[] lastUpdate = new long[1];
			table.withMultiAction(null, LambdaUtils.<List<? extends R>> printableConsumer(values -> {
				if (!ctx.getActionValues().equals(values)) {
					try (Transaction t = ctx.getActionValues().lock(true, null)) {
						CollectionUtils.synchronize(ctx.getActionValues(), values)//
						.simple(v -> v)//
						.rightOrder()//
						.adjust();
					}
				}
				action.getAction().act(null);
				CollectionUtils.synchronize(ctx.getActionValues(), actionValues[0].get()).simple(r -> r).adjust();
			}, () -> action.getAction().toString(), null), ta -> {
				actionValues[0] = ta::getActionItems;
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
							CollectionUtils.synchronize(ctx.getActionValues(), values)//
							.simple(v -> v)//
							.rightOrder()//
							.adjust();
						}
					}
					return action.isEnabled().get();
				}, null);
				ta.disableWith(action.getAction().isEnabled());
				ta.modifyButton(btn -> {
					btn.withText(action.getName());
					btn.withIcon(action.getAddOn(Iconized.class).getIcon());
					btn.withTooltip(action.getTooltip());
				});
			});
		};
	}
}
