package org.observe.quick.swing;

import java.awt.LayoutManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JPanel;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.jdesktop.swingx.JXPanel;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.CollapsePane;
import org.observe.quick.base.QuickTableColumn;
import org.observe.quick.base.QuickTreeTable;
import org.observe.quick.base.TabularWidget;
import org.observe.quick.base.ValueAction;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingTableAction;
import org.observe.quick.swing.QuickSwingTablePopulation.InterpretedSwingTableColumn;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.CollapsePanel;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.ThreadConstraint;
import org.qommons.Transformer;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.ex.CheckedExceptionWrapper;

import com.google.common.reflect.TypeToken;

public class QuickXSwing implements QuickInterpretation {
	@Override
	public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
		QuickSwingPopulator.<CollapsePane, CollapsePane.Interpreted> interpretWidget(tx,
			QuickBaseSwing.gen(CollapsePane.Interpreted.class), QuickXSwing::interpretCollapsePane);
		QuickSwingPopulator.<QuickTreeTable<?>, QuickTreeTable.Interpreted<?>> interpretWidget(tx,
			QuickBaseSwing.gen(QuickTreeTable.Interpreted.class), QuickXSwing::interpretTreeTable);
	}

	static QuickSwingPopulator<CollapsePane> interpretCollapsePane(CollapsePane.Interpreted interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> header = interpreted.getHeader() == null ? null
			: tx.transform(interpreted.getHeader(), QuickSwingPopulator.class);
		QuickSwingPopulator<QuickWidget> content = tx.transform(interpreted.getContents().getFirst(), QuickSwingPopulator.class);
		return QuickSwingPopulator.createWidget((panel, quick) -> {
			content.populate(new CollapsePanePopulator(panel, quick, header), quick.getContents().getFirst());
		});
	}

	static <T> QuickSwingPopulator<QuickTreeTable<T>> interpretTreeTable(QuickTreeTable.Interpreted<T> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		QuickSwingPopulator<QuickWidget> renderer, editor;
		if (interpreted.getTreeColumn() == null) {
			renderer = null;
			editor = null;
		} else {
			renderer = interpreted.getTreeColumn().getRenderer() == null ? null
				: tx.transform(interpreted.getTreeColumn().getRenderer(), QuickSwingPopulator.class);
			if (interpreted.getTreeColumn().getEditing() == null || interpreted.getTreeColumn().getEditing().getEditor() == null)
				editor = null;
			else
				editor = tx.transform(interpreted.getTreeColumn().getEditing().getEditor(), QuickSwingPopulator.class);
		}

		TypeToken<BetterList<T>> rowType = interpreted.getValueType();
		Map<Object, QuickSwingPopulator<QuickWidget>> renderers = new HashMap<>();
		Map<Object, QuickSwingPopulator<QuickWidget>> editors = new HashMap<>();
		boolean[] renderersInitialized = new boolean[1];
		Subscription sub;
		try {
			sub = interpreted.getColumns().subscribe(evt -> {
				boolean colRenderer = false;
				try {
					switch (evt.getType()) {
					case add:
						colRenderer = true;
						if (evt.getNewValue().getRenderer() != null)
							renderers.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
						colRenderer = false;
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
						colRenderer = true;
						if (evt.getNewValue().getRenderer() != null)
							renderers.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
						colRenderer = false;
						if (evt.getNewValue().getEditing() != null && evt.getNewValue().getEditing().getEditor() != null)
							editors.put(evt.getNewValue().getIdentity(),
								tx.transform(evt.getNewValue().getEditing().getEditor(), QuickSwingPopulator.class));
						break;
					}
				} catch (ExpressoInterpretationException e) {
					if (renderersInitialized[0])
						(colRenderer ? evt.getNewValue().getRenderer() : evt.getNewValue().getEditing().getEditor()).reporting()
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
		renderersInitialized[0] = true;
		interpreted.destroyed().act(__ -> sub.unsubscribe());
		boolean[] tableInitialized = new boolean[1];
		// TODO Changes to actions collection?
		List<QuickSwingTableAction<BetterList<T>, ?>> interpretedActions = BetterList.<ValueAction.Interpreted<BetterList<T>, ?>, QuickSwingTableAction<BetterList<T>, ?>, ExpressoInterpretationException> of2(
			interpreted.getActions().stream(),
			a -> (QuickSwingTableAction<BetterList<T>, ?>) tx.transform(a, QuickSwingTableAction.class));

		return QuickSwingPopulator.createWidget((panel, quick) -> {
			InterpretedSwingTableColumn<BetterList<T>, T> treeColumn;
			ValueHolder<PanelPopulation.TreeTableEditor<T, ?>> treeHolder = new ValueHolder<>();
			TabularWidget.TabularContext<BetterList<T>> tableCtx = new TabularWidget.TabularContext.Default<>(rowType,
				quick.reporting().getPosition().toShortString());
			if (quick.getTreeColumn() == null)
				treeColumn = null;
			else {
				treeColumn = new InterpretedSwingTableColumn<>(quick,
					(QuickTableColumn<BetterList<T>, T>) quick.getTreeColumn().getColumns().getFirst(), tableCtx, tx, panel.getUntil(),
					treeHolder, renderer, editor);
			}
			TypeToken<BetterList<T>> pathType = TypeTokens.get().keyFor(BetterList.class)
				.<BetterList<T>> parameterized(quick.getNodeType());
			quick.setContext(tableCtx);
			ObservableCollection<InterpretedSwingTableColumn<BetterList<T>, ?>> columns = quick.getAllColumns().flow()//
				.map((Class<InterpretedSwingTableColumn<BetterList<T>, ?>>) (Class<?>) InterpretedSwingTableColumn.class, col -> {
					try {
						return new InterpretedSwingTableColumn<>(quick, col, tableCtx, tx, panel.getUntil(), treeHolder,
							renderers.get(col.getColumnSet().getIdentity()), editors.get(col.getColumnSet().getIdentity()));
					} catch (ModelInstantiationException e) {
						if (tableInitialized[0]) {
							col.getColumnSet().reporting().error(e.getMessage(), e);
							return null;
						} else
							throw new CheckedExceptionWrapper(e);
					}
				})//
				.filter(col -> col == null ? "Column failed to create" : null)//
				.catchUpdates(ThreadConstraint.ANY)//
				// TODO collectActive(onWhat?)
				.collect();
			Subscription columnsSub = columns.subscribe(evt -> {
				if (evt.getNewValue() != null)
					evt.getNewValue().init(columns, evt.getElementId());
			}, true);
			panel.getUntil().take(1).act(__ -> columnsSub.unsubscribe());
			ObservableCollection<CategoryRenderStrategy<BetterList<T>, ?>> crss = columns.flow()//
				.map((Class<CategoryRenderStrategy<BetterList<T>, ?>>) (Class<?>) CategoryRenderStrategy.class, //
					col -> col.getCRS())//
				.collect();
			panel.addTreeTable3(quick.getModel().getValue(), (parentPath, nodeUntil) -> {
				try {
					return quick.getModel().getChildren(ObservableValue.of(pathType, parentPath), nodeUntil);
				} catch (ModelInstantiationException e) {
					quick.reporting().error("Could not create children for " + parentPath, e);
					return null;
				}
			}, treeTable -> {
				treeHolder.accept(treeTable);
				if (quick.getSelection() != null)
					treeTable.withSelection(quick.getSelection(), false);
				if (quick.getMultiSelection() != null)
					treeTable.withSelection(quick.getMultiSelection());
				if (treeColumn != null)
					treeTable.withRender(treeColumn.getCRS());
				treeTable.withColumns(crss);
				treeTable.withLeafTest2(path -> {
					tableCtx.getActiveValue().set(path, null);
					return quick.getModel().isLeaf(path);
				});
				treeTable.withRootVisible(quick.isRootVisible());
				try {
					for (int a = 0; a < interpretedActions.size(); a++)
						((QuickSwingTableAction<BetterList<T>, ValueAction<BetterList<T>>>) interpretedActions.get(a))
						.addAction(treeTable, quick.getActions().get(a));
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
		});
	}

	private static class CollapsePanePopulator extends AbstractQuickContainerPopulator {
		private PanelPopulator<?, ?> thePopulator;
		private CollapsePane theCollapsePane;
		private QuickSwingPopulator<QuickWidget> theInterpretedHeader;

		public CollapsePanePopulator(PanelPopulator<?, ?> populator, CollapsePane collapsePane,
			QuickSwingPopulator<QuickWidget> interpretedHeader) {
			thePopulator = populator;
			theCollapsePane = collapsePane;
			theInterpretedHeader = interpretedHeader;
		}

		@Override
		public Observable<?> getUntil() {
			return thePopulator.getUntil();
		}

		@Override
		public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
			Consumer<PanelPopulator<JPanel, ?>> panel) {
			thePopulator.addCollapsePanel(false, layout, cp -> populateCollapsePane(modify(cp), panel));
			return this;
		}

		@Override
		public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
			thePopulator.addCollapsePanel(false, "mig", cp -> populateCollapsePane(modify(cp), panel));
			return this;
		}

		private void populateCollapsePane(CollapsePanel<JXCollapsiblePane, JXPanel, ?> cp, Consumer<PanelPopulator<JPanel, ?>> panel) {
			if (theInterpretedHeader != null) {
				cp.withHeader(hp -> {
					try {
						theInterpretedHeader.populate(hp, theCollapsePane.getHeader());
					} catch (ModelInstantiationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				});
			}
			if (theCollapsePane.isCollapsed() != null)
				cp.withCollapsed(theCollapsePane.isCollapsed());
			panel.accept((PanelPopulator<JPanel, ?>) (PanelPopulator<?, ?>) cp);
		}
	}
}