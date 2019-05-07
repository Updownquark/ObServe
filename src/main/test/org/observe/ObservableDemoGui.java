package org.observe;

import java.awt.BorderLayout;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.tree.TreePath;

import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableComboBoxModel;
import org.observe.util.swing.ObservableListModel;
import org.observe.util.swing.ObservableListSelectionModel;
import org.observe.util.swing.ObservableTreeModel;

import com.google.common.reflect.TypeToken;

/** A simple GUI that demonstrates the power and ease of observables */
public class ObservableDemoGui extends JPanel {
	static class ValueCategory {
		final String name;

		final ObservableCollection<Integer> values;

		ValueCategory(String nm) {
			name = nm;
			values = ObservableCollection.create(TypeToken.of(Integer.class));
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private ObservableCollection<ValueCategory> theCategories;
	private SimpleSettableValue<ValueCategory> theSelectedCategory;
	private ObservableCollection<Integer> theSelectedValues;

	private SimpleObservable<String> theCategoryAddObservable;
	private SimpleObservable<ValueCategory> theCategoryRemoveObservable;
	private SimpleObservable<Integer> theValueAddObservable;
	private SimpleObservable<Void> theValueRemoveObservable;

	private JTextField theNewCategoryText;
	private JButton theCategoryAddButton;
	private JButton theCategoryRemoveButton;
	private JComboBox<ValueCategory> theCategoryCombo;
	private JList<Integer> theValueList;
	private JSpinner theValueSpinner;
	private JButton theValueAddButton;
	private JButton theValueRemoveButton;
	private JTree theValueTree;
	private JTree theGroupedValueTree;

	/** Creates the GUI panel */
	public ObservableDemoGui() {
		initObservables();
		initComponents();
		layoutComponents();

		wireUp();
	}

	private void initObservables() {
		theCategories = ObservableCollection.create(TypeTokens.get().of(ValueCategory.class));
		theSelectedCategory = new SimpleSettableValue<>(TypeTokens.get().of(ValueCategory.class), true);
		theCategoryAddObservable = new SimpleObservable<>();
		theCategoryRemoveObservable = new SimpleObservable<>();
		theValueAddObservable = new SimpleObservable<>();
		theValueRemoveObservable = new SimpleObservable<>();
	}

	private void initComponents() {
		theNewCategoryText = new JTextField();
		theCategoryCombo = new JComboBox<>();
		ObservableComboBoxModel.comboFor(theCategoryCombo, "Select a category", cat -> cat.toString(), theCategories, theSelectedCategory);
		theCategoryAddButton = new JButton("Add CategoryRenderStrategy");
		theCategoryAddButton.addActionListener(evt -> {
			String text = theNewCategoryText.getText();
			if (text == null || text.length() == 0)
				return;
			theCategoryAddObservable.onNext(text);
		});
		theCategoryRemoveButton = new JButton("Remove Selected CategoryRenderStrategy");
		theCategoryRemoveButton.addActionListener(evt -> theCategoryRemoveObservable.onNext(theSelectedCategory.get()));
		ObservableCollection<Integer> selectedValues = ObservableCollection.flattenValue(//
			theSelectedCategory.map(cat -> cat == null ? ObservableCollection.of(TypeTokens.get().INT) : cat.values));
		theValueList = new JList<>(new ObservableListModel<>(selectedValues));
		ObservableListSelectionModel<Integer> selectionModel = new ObservableListSelectionModel<>(selectedValues, null);
		theValueList.setSelectionModel(selectionModel);
		theSelectedValues = selectionModel.getSelectedValues();
		theValueSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 1));
		theValueAddButton = new JButton("Add Value");
		theValueAddButton.addActionListener(evt -> theValueAddObservable.onNext((Integer) theValueSpinner.getValue()));
		theValueRemoveButton = new JButton("Remove Selected Values");
		theValueRemoveButton.addActionListener(evt -> theValueRemoveObservable.onNext(null));
		theValueTree = new JTree(new ObservableTreeModel("Values") {
			@Override
			public void valueForPathChanged(TreePath path, Object newValue) {}

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
					return ObservableCollection.of(TypeToken.of(Void.class));
			}
		});
		theValueTree.setEditable(false);

		// This tree uses the groupBy functionality of collection flow, which is currently quite broken
		// theGroupedValueTree = new JTree(new ObservableTreeModel("Grouped Values") {
		// private ObservableMultiMap<Long, Integer> theMap;
		//
		// {
		// ObservableCollection<Integer> values = ObservableCollection.flattenValue(theSelectedCategory.map(cat -> cat.values));
		// theMap = values.flow()
		// .groupBy(TypeToken.of(Long.class), //
		// v -> Long.valueOf(v % 5))
		// .gather(options -> options.useFirst(true));
		// }
		//
		// @Override
		// public boolean isLeaf(Object node) {
		// return node instanceof Integer;
		// }
		//
		// @Override
		// public void valueForPathChanged(TreePath path, Object newValue) {}
		//
		// @Override
		// protected ObservableCollection<?> getChildren(Object parent) {
		// if (parent instanceof String)
		// return theMap.keySet();
		// else if (parent instanceof Long)
		// return theMap.get(parent);
		// else
		// return ObservableCollection.of(TypeToken.of(Void.class));
		// }
		// });
	}

	private void layoutComponents() {
		setLayout(new BorderLayout());
		JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		add(mainSplit, BorderLayout.CENTER);
		JSplitPane treeSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		mainSplit.setRightComponent(treeSplit);
		JScrollPane treeScroll = new JScrollPane(theValueTree);
		treeSplit.setTopComponent(treeScroll);
		treeScroll = new JScrollPane(/*theGroupedValueTree*/);
		treeSplit.setBottomComponent(treeScroll);
		JPanel controlPanel = new JPanel();
		GroupLayout controlLayout = new GroupLayout(controlPanel);
		controlPanel.setLayout(controlLayout);

		controlLayout
		.setVerticalGroup(
			controlLayout.createSequentialGroup()//
			.addGroup(controlLayout.createParallelGroup().addComponent(theNewCategoryText, 30, 30, 30)
				.addComponent(theCategoryAddButton))
			.addGroup(controlLayout.createParallelGroup().addComponent(theCategoryCombo, 30, 30, 30)
				.addComponent(theCategoryRemoveButton))
			.addGroup(controlLayout.createParallelGroup().addComponent(theValueSpinner, 30, 30, 30).addComponent(theValueAddButton))
			.addComponent(theValueList)//
			.addComponent(theValueRemoveButton));
		controlLayout
		.setHorizontalGroup(
			controlLayout.createParallelGroup()//
			.addGroup(controlLayout.createSequentialGroup()
				.addGroup(controlLayout.createParallelGroup().addComponent(theNewCategoryText).addComponent(theCategoryCombo)
					.addComponent(theValueSpinner))
				.addGroup(controlLayout.createParallelGroup().addComponent(theCategoryAddButton)
					.addComponent(theCategoryRemoveButton).addComponent(theValueAddButton)))
			.addComponent(theValueList)//
			.addComponent(theValueRemoveButton));
		mainSplit.setLeftComponent(controlPanel);
	}

	private void wireUp() {
		theCategoryAddObservable.act(text -> theCategories.add(new ValueCategory(text)));
		theCategoryRemoveObservable.act(cat -> theCategories.remove(cat));
		theValueAddObservable.act(value -> {
			ValueCategory cat = theSelectedCategory.get();
			if (cat == null)
				return;
			if (cat.values.contains(value))
				return;
			theSelectedCategory.get().values.add(value);
		});
		theValueRemoveObservable.act(v -> {
			if (theSelectedCategory.get() != null)
				theSelectedCategory.get().values.removeAll(theSelectedValues);
		});
	}

	/**
	 * Starts the GUI
	 *
	 * @param args Command-line arguments, ignored
	 */
	public static void main(String... args) {
		JFrame frame = new JFrame("Observable Demo");
		frame.setContentPane(new ObservableDemoGui());
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}
}
