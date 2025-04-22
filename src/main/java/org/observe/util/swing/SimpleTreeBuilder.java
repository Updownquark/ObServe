package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.ObservableCollectionSynchronization;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseListener;
import org.observe.util.swing.Dragging.SimpleTransferAccepter;
import org.observe.util.swing.Dragging.SimpleTransferSource;
import org.observe.util.swing.Dragging.TransferAccepter;
import org.observe.util.swing.Dragging.TransferSource;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.SimpleComponentEditor;
import org.observe.util.swing.PanelPopulation.TreeEditor;
import org.observe.util.swing.PanelPopulationImpl.SimpleDataAction;
import org.observe.util.swing.PanelPopulationImpl.SimpleHPanel;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.collect.BetterList;

/**
 * Utility for building a {@link JTree}
 *
 * @param <F> The super-type of values in the tree
 * @param <P> The sub-type of this builder
 */
public class SimpleTreeBuilder<F, P extends SimpleTreeBuilder<F, P>> extends SimpleComponentEditor<JTree, P> implements TreeEditor<F, P> {
	/**
	 * Creates a tree with a function that accepts the node value
	 *
	 * @param <F> The super-type of values in the tree
	 * @param root The root value for the tree
	 * @param children The function to produce children for each tree node value
	 * @param until The observable which, when it fires, will disconnect all of the tree's listeners to the values
	 * @return The tree builder to configure
	 */
	public static <F> SimpleTreeBuilder<F, ?> createTree(ComponentEditor<?, ?> parent, ObservableValue<? extends F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeBuilder<>(parent, root, new PPTreeModel1<>(root, children), until);
	}

	/**
	 * Creates a tree with a function that accepts the node path
	 *
	 * @param <F> The super-type of values in the tree
	 * @param root The root value for the tree
	 * @param children The function to produce children for each tree node path
	 * @param until The observable which, when it fires, will disconnect all of the tree's listeners to the values
	 * @return The tree builder to configure
	 */
	public static <F> SimpleTreeBuilder<F, ?> createTree2(ComponentEditor<?, ?> parent, ObservableValue<? extends F> root,
		Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return createTree3(parent, root, (path, nodeUntil) -> children.apply(path), until);
	}

	/**
	 * Creates a tree with a function that accepts the node path and an observable that fires when the node is removed
	 *
	 * @param <F> The super-type of values in the tree
	 * @param root The root value for the tree
	 * @param children The function to produce children for each tree node path
	 * @param until The observable which, when it fires, will disconnect all of the tree's listeners to the values
	 * @return The tree builder to configure
	 */
	public static <F> SimpleTreeBuilder<F, ?> createTree3(ComponentEditor<?, ?> parent, ObservableValue<? extends F> root,
		BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeBuilder<>(parent, root, new PPTreeModel3<>(root, children), until);
	}

	private final ObservableValue<? extends F> theRoot;

	private String theItemName;

	private CategoryRenderStrategy<BetterList<F>, F> theRenderer;
	private SettableValue<F> theValueSingleSelection;
	private SettableValue<BetterList<F>> thePathSingleSelection;
	private boolean isSingleSelection;
	private ObservableCollection<F> theValueMultiSelection;
	private ObservableCollection<BetterList<F>> thePathMultiSelection;
	private Observable<?> theExpandAll;
	private Observable<?> theCollapseAll;
	private boolean isRootVisible;
	private List<Object> theActions;
	private boolean theActionsOnTop;
	private Dragging.SimpleTransferSource<BetterList<F>, F> theDragSource;
	private Dragging.SimpleTransferAccepter<BetterList<F>, F> theDragAccepter;

	private SimpleTreeBuilder(ComponentEditor<?, ?> parent, ObservableValue<? extends F> root, ObservableTreeModel<F> model,
		Observable<?> until) {
		super(parent, null, new MyTree<>(model), until);
		theRenderer = new CategoryRenderStrategy<>("Tree", (Class<F>) Object.class,
			LambdaUtils.printableFn(BetterList::getLast, "BetterList::getLast", null));
		theRoot = root;
		isRootVisible = true;
		theActions = new ArrayList<>();
		theActionsOnTop = true;
	}

	static class MyTree<T> extends JTree {
		public MyTree(ObservableTreeModel<T> newModel) {
			super(newModel);
		}

		@Override
		public ObservableTreeModel<T> getModel() {
			return (ObservableTreeModel<T>) super.getModel();
		}

		@Override
		public void setModel(TreeModel newModel) {
			if (getModel() != null)
				throw new IllegalStateException("Models can't be switched out");
			super.setModel(newModel);
		}

		@Override
		public boolean isPathEditable(TreePath path) {
			return super.isPathEditable(path) && getModel().isPathEditable(path);
		}
	}

	static abstract class PPTreeModel<F> extends ObservableTreeModel<F> {
		private Predicate<? super F> theLeafTest;
		private Predicate<? super BetterList<F>> theLeafTest2;

		PPTreeModel(ObservableValue<? extends F> root) {
			super(root);
		}

		@Override
		public boolean isLeaf(Object node) {
			ObservableTreeModel<F>.TreeNode treeNode = (ObservableTreeModel<F>.TreeNode) node;
			Predicate<? super F> leafTest = theLeafTest;
			if (leafTest != null)
				return leafTest.test(treeNode.get());
			Predicate<? super BetterList<F>> leafTest2 = theLeafTest2;
			if (leafTest2 != null) {
				return leafTest2.test(treeNode.getValuePath());
			}
			return false;
		}

		public void setLeafTest(Predicate<? super F> leafTest) {
			theLeafTest = leafTest;
		}

		public void setLeafTest2(Predicate<? super BetterList<F>> leafTest) {
			theLeafTest2 = leafTest;
		}
	}

	static class PPTreeModel1<F> extends PPTreeModel<F> {
		private final Function<? super F, ? extends ObservableCollection<? extends F>> theChildren;

		PPTreeModel1(ObservableValue<? extends F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children) {
			super(root);
			theChildren = children;
		}

		@Override
		protected ObservableCollection<? extends F> getChildren(BetterList<F> parentPath, Observable<?> until) {
			return theChildren.apply(parentPath.getLast());
		}
	}

	static class PPTreeModel3<F> extends PPTreeModel<F> {
		private final BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> theChildren;

		PPTreeModel3(ObservableValue<? extends F> root,
			BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> children) {
			super(root);
			theChildren = children;
		}

		@Override
		protected ObservableCollection<? extends F> getChildren(BetterList<F> parentPath, Observable<?> nodeUntil) {
			return theChildren.apply(parentPath, nodeUntil);
		}
	}

	@Override
	public List<BetterList<F>> getSelection() {
		TreePath[] selection = getEditor().getSelectionPaths();
		if (selection == null || selection.length == 0)
			return BetterList.empty();
		return BetterList.of(Arrays.stream(selection)//
			.map(ObservableTreeModel::betterPath));
	}

	@Override
	public P withRemove(Consumer<? super List<? extends BetterList<F>>> deletion, Consumer<DataAction<BetterList<F>, ?>> actionMod) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public P withMultiAction(String actionName, Consumer<? super List<? extends BetterList<F>>> action,
		Consumer<DataAction<BetterList<F>, ?>> actionMod) {
		SimpleDataAction<BetterList<F>, ?> ta = new SimpleDataAction<>(actionName, this, action, false, getUntil());
		actionMod.accept(ta);
		theActions.add(ta);
		return (P) this;
	}

	@Override
	public P withActionsOnTop(boolean actionsOnTop) {
		theActionsOnTop = actionsOnTop;
		return (P) this;
	}

	@Override
	public P withTreeOption(Consumer<? super PanelPopulator<?, ?>> panel) {
		theActions.add(panel);
		return (P) this;
	}

	@Override
	public P dragSourcePath(Consumer<? super TransferSource<BetterList<F>, F>> source) {
		if (theDragSource == null)
			theDragSource = new SimpleTransferSource<>();
		// if (source == null)
		// throw new IllegalArgumentException("Drag sourcing must be configured");
		if (source != null)
			source.accept(theDragSource);
		return (P) this;
	}

	@Override
	public P dragAcceptPath(Consumer<? super TransferAccepter<BetterList<F>, F>> accept) {
		if (theDragAccepter == null)
			theDragAccepter = new SimpleTransferAccepter<>();
		// if (accept == null)
		// throw new IllegalArgumentException("Drag accepting must be configured");
		if (accept != null)
			accept.accept(theDragAccepter);
		return (P) this;
	}

	@Override
	public P withItemName(String itemName) {
		theItemName = itemName;
		return (P) this;
	}

	@Override
	public String getItemName() {
		return theItemName;
	}

	@Override
	public ObservableValue<? extends F> getRoot() {
		return theRoot;
	}

	@Override
	public CategoryRenderStrategy<BetterList<F>, F> getRender() {
		return theRenderer;
	}

	@Override
	public P withRender(CategoryRenderStrategy<BetterList<F>, F> render) {
		theRenderer = render;
		return (P) this;
	}

	@Override
	public P withSelection(SettableValue<BetterList<F>> selection, boolean enforceSingleSelection) {
		thePathSingleSelection = selection;
		isSingleSelection = enforceSingleSelection;
		return (P) this;
	}

	@Override
	public P withSelection(ObservableCollection<BetterList<F>> selection) {
		thePathMultiSelection = selection;
		return (P) this;
	}

	@Override
	public P withSelectionMode(int mode) {
		getEditor().getSelectionModel().setSelectionMode(mode);
		return (P) this;
	}

	@Override
	public P withValueSelection(SettableValue<F> selection, boolean enforceSingleSelection) {
		theValueSingleSelection = selection;
		isSingleSelection = enforceSingleSelection;
		return (P) this;
	}

	@Override
	public P withValueSelection(ObservableCollection<F> selection) {
		theValueMultiSelection = selection;
		return (P) this;
	}

	@Override
	public P withLeafTest(Predicate<? super F> leafTest) {
		((SimpleTreeBuilder.PPTreeModel<F>) getEditor().getModel()).setLeafTest(leafTest);
		return (P) this;
	}

	@Override
	public P withLeafTest2(Predicate<? super BetterList<F>> leafTest) {
		((SimpleTreeBuilder.PPTreeModel<F>) getEditor().getModel()).setLeafTest2(leafTest);
		return (P) this;
	}

	@Override
	public P withRootVisible(boolean rootVisible) {
		isRootVisible = rootVisible;
		return (P) this;
	}

	@Override
	public P withExpandAll(Observable<?> expandAll) {
		if (theExpandAll == null)
			theExpandAll = expandAll;
		else
			theExpandAll = Observable.or(theExpandAll, expandAll);
		return (P) this;
	}

	@Override
	public P withCollapseAll(Observable<?> expandAll) {
		if (theCollapseAll == null)
			theCollapseAll = expandAll;
		else
			theCollapseAll = Observable.or(theCollapseAll, expandAll);
		return (P) this;
	}

	@Override
	public boolean isVisible(List<? extends F> path) {
		return getEditor().isVisible(new TreePath(path.toArray()));
	}

	@Override
	public boolean isExpanded(List<? extends F> path) {
		return getEditor().isExpanded(new TreePath(path.toArray()));
	}

	@Override
	protected Component createComponent() {
		int[] hoveredRow = new int[] { -1 };
		JTree tree = getEditor();
		ObservableTreeModel<F> model = (ObservableTreeModel<F>) tree.getModel();
		model.withRenderer(theRenderer).withStatus(tree::getRowForPath, tree::isExpanded, tree.getSelectionModel()::isPathSelected);

		if (theRenderer.getRenderer() != null)
			getEditor().setCellRenderer(new ObservableTreeCellRenderer<>(theRenderer.getRenderer(), hoveredRow));
		if (theRenderer.getMutator().getEditor() != null) {
			getEditor().setEditable(true);
			getEditor().setCellEditor(theRenderer.getMutator().getEditor());
		}
		CategoryMouseListener<? super BetterList<F>, ? super F> mouseListener = theRenderer.getMouseListener();
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (mouseListener != null) {
					ModelCell<BetterList<F>, F> cell = cellAt(e);
					if (cell != null)
						mouseListener.mouseClicked(cell, e);
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				if (mouseListener != null) {
					ModelCell<BetterList<F>, F> cell = cellAt(e);
					if (cell != null)
						mouseListener.mousePressed(cell, e);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (mouseListener != null) {
					ModelCell<BetterList<F>, F> cell = cellAt(e);
					if (cell != null)
						mouseListener.mouseReleased(cell, e);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				updateTooltip(e);
				if (mouseListener != null) {
					ModelCell<BetterList<F>, F> cell = cellAt(e);
					if (cell != null)
						mouseListener.mouseEntered(cell, e);
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hoveredRow[0] = -1;
				if (mouseListener != null) {
					ModelCell<BetterList<F>, F> cell = cellAt(e);
					if (cell != null)
						mouseListener.mouseExited(cell, e);
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				updateTooltip(e);
				if (mouseListener != null) {
					ModelCell<BetterList<F>, F> cell = cellAt(e);
					if (cell != null)
						mouseListener.mouseMoved(cell, e);
				}
			}

			private void updateTooltip(MouseEvent e) {
				TreePath path = getEditor().getPathForLocation(e.getX(), e.getY());
				int row = getEditor().getRowForLocation(e.getX(), e.getY());
				if (row != hoveredRow[0]) {
					Rectangle bounds;
					if (hoveredRow[0] >= 0) {
						bounds = getEditor().getRowBounds(hoveredRow[0]);
						if (bounds != null)
							getEditor().repaint(bounds);
					}
					if (row >= 0) {
						bounds = getEditor().getRowBounds(row);
						if (bounds != null)
							getEditor().repaint(bounds);
					}
					hoveredRow[0] = row;
				}
				if (path == null || theRenderer.getTooltipFn() == null) {
					getEditor().setToolTipText(null);
				} else {
					F value = (F) path.getLastPathComponent();
					ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(() -> ObservableTreeModel.betterPath(path), value, row, 0,
						getEditor().getSelectionModel().isRowSelected(row), false, true, true, !getEditor().isCollapsed(row),
						getEditor().getModel().isLeaf(value));
					String tooltip = theRenderer.getTooltip(cell);
					getEditor().setToolTipText(tooltip);
				}
			}

			private ModelCell<BetterList<F>, F> cellAt(MouseEvent e) {
				TreePath path = getEditor().getPathForLocation(e.getX(), e.getY());
				if (path == null)
					return null;
				int row = getEditor().getRowForLocation(e.getX(), e.getY());
				F value = (F) path.getLastPathComponent();
				return new ModelCell.Default<>(() -> ObservableTreeModel.betterPath(path), value, row, 0,
					getEditor().getSelectionModel().isRowSelected(row), false, true, true, !getEditor().isCollapsed(row),
					getEditor().getModel().isLeaf(value));
			}
		};
		getEditor().addMouseListener(mouse);
		getEditor().addMouseMotionListener(mouse);
		getUntil().take(1).act(__ -> {
			getEditor().removeMouseListener(mouse);
			getEditor().removeMouseMotionListener(mouse);
		});
		// Create our own multi-selection collection free of constraints.
		// We don't support preventing the user from selecting things.
		// We also need to control the actions if nothing else
		ObservableCollection<BetterList<F>> multiSelection = ObservableCollection.<BetterList<F>> build().build().safe(ThreadConstraint.EDT,
			getUntil());
		ObservableTreeModel.syncSelection(getEditor(), multiSelection, Equivalence.DEFAULT, getUntil());
		if (PanelPopulation.isDebugging(getEditor().getName(), "multiSelect"))
			multiSelection.simpleChanges().takeUntil(getUntil()).act(__ -> System.out.println("Selection=" + multiSelection));
		if (theValueMultiSelection != null) {
			ObservableCollection<F> modelValueSel = multiSelection.flow()//
				.<F> transform(tx -> tx//
					.cache(false).reEvalOnUpdate(false).fireIfUnchanged(true)//
					.map(path -> path == null ? null : path.getLast())//
					.replaceSource(value -> model.getValuePath(value, true), null))//
				.collect();
			Subscription sub = ObservableCollectionSynchronization.synchronize(modelValueSel, theValueMultiSelection)//
				.synchronize();
			getUntil().take(1).act(__ -> sub.unsubscribe());
		}
		if (thePathMultiSelection != null) {
			Subscription sub = ObservableCollectionSynchronization.synchronize(multiSelection, thePathMultiSelection)//
				.synchronize();
			getUntil().take(1).act(__ -> sub.unsubscribe());
		}
		if (thePathSingleSelection != null)
			ObservableTreeModel.syncSelection(getEditor(), thePathSingleSelection, false, Equivalence.DEFAULT, getUntil());
		if (theValueSingleSelection != null)
			ObservableTreeModel.syncSelection(getEditor(), theValueSingleSelection//
				.safe(ThreadConstraint.EDT).//
				<BetterList<F>> transformReversible(tx -> tx//
					.map(v -> model.getValuePath(v, true))//
					.withReverse(path -> path == null ? null : path.getLast())),
				false, Equivalence.DEFAULT, getUntil());
		if (isSingleSelection)
			getEditor().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		if (theExpandAll != null) {
			theExpandAll.takeUntil(getUntil()).act(__ -> {
				for (int r = 0; r < tree.getRowCount(); r++)
					tree.expandRow(r);
			});
		}
		if (theCollapseAll != null) {
			theCollapseAll.takeUntil(getUntil()).act(__ -> {
				int rowCount = tree.getRowCount();
				boolean collapsed = false;
				do {
					for (int r = rowCount - 1; r >= 0 && rowCount == tree.getRowCount(); r--)
						tree.collapseRow(r);
					collapsed = rowCount != tree.getRowCount();
				} while (collapsed);
			});
		}

		// Tooltip control could mess up cell tooltips and stuff
		withTooltipControl(false);

		getEditor().setExpandsSelectedPaths(true);
		getEditor().setRootVisible(isRootVisible);
		JScrollPane scroll = new JScrollPane(getEditor());
		Component comp = scroll;
		if (!theActions.isEmpty()) {
			boolean hasPopups = false, hasButtons = false;
			for (Object action : theActions) {
				if (!(action instanceof SimpleDataAction))
					hasButtons = true;
				else {
					if (((SimpleDataAction<BetterList<F>, ?>) action).isPopup())
						hasPopups = true;
					if (((SimpleDataAction<BetterList<F>, ?>) action).isButton())
						hasButtons = true;
				}
			}
			for (Object action : theActions) {
				if (action instanceof SimpleDataAction)
					((SimpleDataAction<BetterList<F>, ?>) action).init(multiSelection);
			}

			if (hasPopups) {
				withPopupMenu(popupMenu -> {
					for (Object action : theActions) {
						if (action instanceof SimpleDataAction && ((SimpleDataAction<BetterList<F>, ?>) action).isPopup()) {
							SimpleDataAction<BetterList<F>, ?> dataAction = (SimpleDataAction<BetterList<F>, ?>) action;
							popupMenu.withAction("Action", dataAction.theObservableAction, dataAction::modifyButtonEditor);
						}
					}
				});
			}
			if (hasButtons) {
				SimpleHPanel<JPanel, ?> buttonPanel = new SimpleHPanel<>(this, null,
					new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING)), getUntil());
				for (Object action : theActions) {
					if (action instanceof SimpleDataAction) {
						if (((SimpleDataAction<?, ?>) action).isButton())
							((SimpleDataAction<BetterList<F>, ?>) action).addButton(buttonPanel);
					} else if (action instanceof Consumer)
						buttonPanel.addHPanel(null, "box", (Consumer<PanelPopulator<JPanel, ?>>) action);
				}
				JPanel tablePanel = new JPanel(new BorderLayout());
				tablePanel.add(buttonPanel.getComponent(), theActionsOnTop ? BorderLayout.NORTH : BorderLayout.SOUTH);
				tablePanel.add(scroll, BorderLayout.CENTER);
				comp = tablePanel;
			} else
				comp = scroll;
		} else
			comp = scroll;

		// Set up transfer handling (DnD, copy/paste)
		Dragging.TransferSource<BetterList<F>, F> valueDragSource = null;
		Dragging.TransferAccepter<BetterList<F>, F> valueDragAccept = null;
		if (theRenderer != null) {
			valueDragSource = theRenderer.getDragSource();
			if (theRenderer.getMutator() != null)
				valueDragAccept = theRenderer.getMutator().getDragAccepter();
		}

		if (theDragSource != null || theDragAccepter != null || valueDragSource != null || valueDragAccept != null) {
			getEditor().setDragEnabled(true);
			getEditor().setDropMode(DropMode.ON);
			TransferHandler handler = new TreeBuilderTransferHandler(getEditor(), theDragSource, theDragAccepter, valueDragSource,
				valueDragAccept);
			getEditor().setTransferHandler(handler);
		}

		decorate(comp);
		return comp;
	}

	@Override
	public ObservableValue<String> getTooltip() {
		return null;
	}

	@Override
	protected Component createFieldNameLabel(Observable<?> until) {
		return null;
	}

	@Override
	protected Component createPostLabel(Observable<?> until) {
		return null;
	}

	static class ObservableTreeCellRenderer<F> implements TreeCellRenderer {
		private final ObservableCellRenderer<? super BetterList<F>, ? super F> theRenderer;
		private final int[] theHoveredRowColumn;

		ObservableTreeCellRenderer(ObservableCellRenderer<? super BetterList<F>, ? super F> renderer, int[] hoveredRowColumn) {
			theRenderer = renderer;
			theHoveredRowColumn = hoveredRowColumn;
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
			boolean hasFocus) {
			ObservableTreeModel<F>.TreeNode node = (ObservableTreeModel<F>.TreeNode) value;
			Supplier<BetterList<F>> modelValue = node::getValuePath;
			boolean hovered = theHoveredRowColumn[0] == row;
			ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(modelValue, (F) node.getRenderValue(), row, 0, selected, hasFocus,
				hovered, hovered, expanded, leaf);
			return theRenderer.getCellRendererComponent(tree, cell, null);
		}
	}

	class TreeBuilderTransferHandler extends TransferHandler {
		private final JTree theTree;
		private final Dragging.TransferSource<BetterList<F>, F> thePathSource;
		private final Dragging.TransferAccepter<BetterList<F>, F> thePathAccepter;
		private final Dragging.TransferSource<BetterList<F>, F> theNodeSource;
		private final Dragging.TransferAccepter<BetterList<F>, F> theNodeAccepter;
		private Icon theDragAppearance;

		TreeBuilderTransferHandler(JTree tree, TransferSource<BetterList<F>, F> pathSource, TransferAccepter<BetterList<F>, F> pathAccepter,
			Dragging.TransferSource<BetterList<F>, F> nodeSource, Dragging.TransferAccepter<BetterList<F>, F> nodeAccepter) {
			theTree = tree;
			thePathSource = pathSource;
			thePathAccepter = pathAccepter;
			theNodeSource = nodeSource;
			theNodeAccepter = nodeAccepter;
		}

		@Override
		public int getSourceActions(JComponent c) {
			int actions = 0;
			if (thePathSource != null)
				actions |= thePathSource.getSourceActions();
			if (theNodeSource != null)
				actions |= theNodeSource.getSourceActions();
			return actions;
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			if ((thePathSource == null && theNodeSource == null) || theTree.getSelectionCount() == 0)
				return null;

			List<Transferable> transferables = new ArrayList<>(theTree.getSelectionCount());
			for (TreePath path : theTree.getSelectionPaths()) {
				BetterList<F> betterPath = ObservableTreeModel.betterPath(path);
				ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(() -> betterPath, betterPath.getLast(),
					theTree.getRowForPath(path), 0, theTree.isPathSelected(path), false, false, false, theTree.isExpanded(path),
					theTree.getModel().isLeaf(path.getLastPathComponent()));
				if (thePathSource != null) {
					Transferable pathT = thePathSource.createTransferable(cell);
					if (pathT != null)
						transferables.add(pathT);
				}
				if (theNodeSource != null) {
					Transferable nodeT = theNodeSource.createTransferable(cell);
					if (nodeT != null)
						transferables.add(nodeT);
				}
			}
			if (transferables.isEmpty())
				return null;
			else if (transferables.size() == 1)
				return transferables.get(0);
			else
				return new Dragging.AndTransferable(transferables.toArray(new Transferable[transferables.size()]));
		}

		@Override
		public Icon getVisualRepresentation(Transferable t) {
			if (theDragAppearance != null)
				return theDragAppearance;
			return super.getVisualRepresentation(t);
		}

		@Override
		protected void exportDone(JComponent source, Transferable data, int action) {
			// TODO If removed, scroll
			super.exportDone(source, data, action);
		}

		@Override
		public boolean canImport(TransferSupport support) {
			if ((thePathAccepter == null && theNodeAccepter == null) || theTree.getRowCount() == 0)
				return false;
			int rowIndex;
			boolean beforeRow;
			if (support.isDrop()) {
				Point dropPoint = support.getDropLocation().getDropPoint();
				rowIndex = theTree.getRowForLocation(dropPoint.x, dropPoint.y);
				if (rowIndex < 0) {
					rowIndex = theTree.getRowCount() - 1;
					beforeRow = false;
				} else {
					Rectangle bounds = theTree.getRowBounds(rowIndex);
					beforeRow = (support.getDropLocation().getDropPoint().y - bounds.y) <= bounds.height / 2;
				}
			} else {
				rowIndex = theTree.getLeadSelectionRow();
				if (rowIndex < 0)
					return false;
				beforeRow = false;
			}

			Dragging.TransferWrapper wrapper = Dragging.wrap(support);
			ObservableTreeModel<F>.TreeNode node = (ObservableTreeModel<F>.TreeNode) theTree.getPathForRow(rowIndex).getLastPathComponent();
			BetterList<F> targetPath = node.getValuePath();
			boolean selected = theTree.isRowSelected(rowIndex);
			ModelRow<BetterList<F>> modelRow = new ModelRow.Default<>(() -> targetPath, rowIndex, selected, selected, false,
				theTree.isExpanded(rowIndex), theTree.getModel().isLeaf(node));
			Object renderValue = getRender() == null ? targetPath.getLast() : getRender().getCategoryValue(modelRow);
			ModelCell<BetterList<F>, F> cell = new ModelCell.RowWrapper<>(modelRow, (F) renderValue, 0, false, selected);
			if (thePathAccepter != null && thePathAccepter.canAccept(cell, false, beforeRow, wrapper, true)) {
				theDragAppearance = thePathAccepter.getDragAppearance();
				return true;
			}
			if (theNodeAccepter != null && theNodeAccepter.canAccept(cell, false, beforeRow, wrapper, true)) {
				theDragAppearance = theNodeAccepter.getDragAppearance();
				return true;
			}
			return false;
		}

		@Override
		public boolean importData(TransferSupport support) {
			if ((thePathAccepter == null && theNodeAccepter == null) || theTree.getRowCount() == 0)
				return false;
			int rowIndex;
			boolean beforeRow;
			if (support.isDrop()) {
				Point dropPoint = support.getDropLocation().getDropPoint();
				rowIndex = theTree.getRowForLocation(dropPoint.x, dropPoint.y);
				if (rowIndex < 0) {
					rowIndex = theTree.getRowCount() - 1;
					beforeRow = false;
				} else {
					Rectangle bounds = theTree.getRowBounds(rowIndex);
					beforeRow = (support.getDropLocation().getDropPoint().y - bounds.y) <= bounds.height / 2;
				}
			} else {
				rowIndex = theTree.getLeadSelectionRow();
				if (rowIndex < 0)
					return false;
				beforeRow = false;
			}

			Dragging.TransferWrapper wrapper = Dragging.wrap(support);
			ObservableTreeModel<F>.TreeNode node = (ObservableTreeModel<F>.TreeNode) theTree.getPathForRow(rowIndex).getLastPathComponent();
			BetterList<F> targetPath = node.getValuePath();
			boolean selected = theTree.isRowSelected(rowIndex);
			ModelRow<BetterList<F>> modelRow = new ModelRow.Default<>(() -> targetPath, rowIndex, selected, selected, false,
				theTree.isExpanded(rowIndex), theTree.getModel().isLeaf(node));
			Object renderValue = getRender() == null ? targetPath.getLast() : getRender().getCategoryValue(modelRow);
			ModelCell<BetterList<F>, F> cell = new ModelCell.RowWrapper<>(modelRow, (F) renderValue, 0, false, selected);
			if (thePathAccepter != null && thePathAccepter.canAccept(cell, selected, beforeRow, wrapper, true)) {
				try {
					thePathAccepter.accept(cell, false, beforeRow, wrapper, true, false);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (theNodeAccepter != null && theNodeAccepter.canAccept(cell, false, beforeRow, wrapper, true)) {
				try {
					theNodeAccepter.accept(cell, false, beforeRow, wrapper, true, false);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return false;
		}
	}
}
