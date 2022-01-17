package org.observe.util.swing;

import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

import org.observe.SettableValue;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.util.TypeTokens;
import org.qommons.collect.CollectionElement;
import org.qommons.io.SpinnerFormat;

/** A simple GUI that demonstrates the power and ease of observables */
public class ObservableDemoGui extends JPanel {
	static class ValueCategory {
		final String name;

		final ObservableSet<Integer> values;

		ValueCategory(String nm) {
			name = nm;
			values = ObservableCollection.build(TypeTokens.get().INT).distinct().build();
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private ObservableSet<String> theCategoryNames;
	private ObservableCollection<ValueCategory> theCategories;

	/** Creates the GUI panel */
	public ObservableDemoGui() {
		initObservables();
		initComponents();
	}

	private void initObservables() {
		theCategoryNames = ObservableCollection.build(TypeTokens.get().STRING).distinct().build();
		theCategories = theCategoryNames.flow()
			.transform(TypeTokens.get().of(ValueCategory.class), tx -> tx.cache(true).reEvalOnUpdate(false).map(ValueCategory::new))
			.collect();
	}

	private void initComponents() {
		SettableValue<String> newCategory = SettableValue.build(String.class).withValue("Cat 1").build();
		SettableValue<String> selectedCategory = SettableValue.build(String.class).build();
		ObservableSet<Integer> valuesOfSelectedCategory = ObservableSet.flattenValue(selectedCategory.map(cat -> {
			CollectionElement<String> found = theCategoryNames.getElement(cat, true);
			if (found == null)
				return ObservableSet.of(TypeTokens.get().INT);
			return theCategories.getElementsBySource(found.getElementId(), theCategoryNames).getFirst().get().values;
		}));
		ObservableCollection<Integer> selectedValues = ObservableCollection.build(int.class).build();
		SettableValue<Integer> newValue = SettableValue.build(Integer.class).withValue(0).build();

		JTree valueTree = new JTree(new ObservableTreeModel<Object>("Values") {
			@Override
			public void valueForPathChanged(TreePath path, Object newVal) {}

			@Override
			public boolean isLeaf(Object node) {
				return node instanceof Integer;
			}

			@Override
			protected ObservableCollection<?> getChildren(Object parent) {
				if (parent instanceof String)
					return theCategories;
				else if (parent instanceof ValueCategory)
					return ((ValueCategory) parent).values;
				else
					return ObservableCollection.of(TypeTokens.get().VOID);
			}
		});
		valueTree.setEditable(false);

		ObservableMultiMap<Long, Integer> mapByMod5 = valuesOfSelectedCategory.flow()
			.groupBy(TypeTokens.get().LONG, v -> Long.valueOf(v % 5), (k, v) -> v).gather();
		JTree groupedValueTree = new JTree(new ObservableTreeModel<Object>("Grouped Values") {
			@Override
			public boolean isLeaf(Object node) {
				return node instanceof Integer;
			}

			@Override
			public void valueForPathChanged(TreePath path, Object newVal) {}

			@Override
			protected ObservableCollection<?> getChildren(Object parent) {
				if (parent instanceof String)
					return mapByMod5.keySet();
				else if (parent instanceof Long)
					return mapByMod5.get((Long) parent);
				else
					return ObservableCollection.of(TypeTokens.get().VOID);
			}
		});

		PanelPopulation.populateVPanel(this, null)//
		.addSplit(false,
			mainSplit -> mainSplit.fill()//
			.firstV(
				left -> left//
				.addTextField("New Category:", newCategory.filterAccept(theCategoryNames::canAdd), SpinnerFormat.NUMERICAL_TEXT, //
					f -> f.fill()
					.modifyEditor(
						tf -> tf.withColumns(12).setCommitOnType(true).onEnter((cat, evt) -> theCategoryNames.add(cat)))
					.withPostButton("Add", cause -> theCategoryNames.add(newCategory.get()), //
						b -> b.withTooltip("Add a new category").disableWith(
							newCategory.refresh(theCategoryNames.simpleChanges()).map(theCategoryNames::canAdd))))//
				.addComboField("Selected Category:", selectedCategory, theCategoryNames, //
					f -> f.fill().withPostButton("Remove", cause -> theCategoryNames.remove(selectedCategory.get()),
						b -> b.withTooltip("Remove the selected category")))//
				.addTextField("New Value:", newValue.refresh(valuesOfSelectedCategory.simpleChanges()).filterAccept(v -> {
					return valuesOfSelectedCategory.canAdd(v);
				}).disableWith(selectedCategory.map(cat -> cat == null ? "No category selected" : null)), SpinnerFormat.INT,
					f -> f.fill()
					.modifyEditor(tf -> tf.setCommitAdjustmentImmediately(true).setCommitOnType(true)
						.onEnter((val, evt) -> valuesOfSelectedCategory.add(val)))
					.withPostButton("Add", cause -> valuesOfSelectedCategory.add(newValue.get()), //
						b -> b.withTooltip("Add the value to the selected category")
						.disableWith(newValue.refresh(selectedCategory.noInitChanges())
							.refresh(valuesOfSelectedCategory.simpleChanges()).map(valuesOfSelectedCategory::canAdd))))//
				.addList(valuesOfSelectedCategory,
					list -> list.fill().withFieldName("Values:").withSelection(selectedValues)
					.render(rs -> rs.withAddRow(() -> getNextAdd(valuesOfSelectedCategory), v -> v, addRow -> {
						addRow.withMutation(arm -> arm.asText(SpinnerFormat.INT, tf -> tf.withColumns(5)).clicks(1));
					}))//
					.withRemove(values -> valuesOfSelectedCategory.removeAll(values), null)//
					.withPostButton("Remove", cause -> valuesOfSelectedCategory.removeAll(selectedValues), //
						b -> b.withTooltip("Remove the selected values from the selected category")
						.disableWith(selectedValues.observeSize().map(sz -> sz == 0 ? "No values selected" : null)))))//
			.lastV(right -> right//
				.addSplit(true,
					rightSplit -> rightSplit.fill()//
					.first(new JScrollPane(valueTree))//
					.last(new JScrollPane(groupedValueTree)))//
				));
	}

	private static Integer getNextAdd(List<Integer> values) {
		return values.stream().max(Integer::compareTo).orElse(-1) + 1;
	}

	/**
	 * Starts the GUI
	 *
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String... args) {
		ObservableSwingUtils.systemLandF();
		JFrame frame = new JFrame("Observable Demo");
		frame.setContentPane(new ObservableDemoGui());
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}
}
