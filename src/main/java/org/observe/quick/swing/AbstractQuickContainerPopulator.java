package org.observe.quick.swing;

import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.swing.*;
import org.observe.util.swing.MultiRangeSlider.Range;
import org.observe.util.swing.PanelPopulation.*;
import org.qommons.collect.BetterList;
import org.qommons.io.Format;

public abstract class AbstractQuickContainerPopulator
implements PanelPopulation.PanelPopulator<JPanel, AbstractQuickContainerPopulator> {
	@Override
	public abstract AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
		Consumer<PanelPopulator<JPanel, ?>> panel);

	@Override
	public abstract AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel);

	@Override
	public Component decorate(Component c) {
		return c;
	}

	@Override
	public <R> AbstractQuickContainerPopulator addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?, ?>> table) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> p.addTable(rows, table));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTree(ObservableValue<? extends F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Consumer<TreeEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addTree(root, children, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTree3(ObservableValue<? extends F> root,
		BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addTree3(root, children, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTreeTable(ObservableValue<F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Consumer<TreeTableEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addTreeTable(root, children, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTreeTable3(ObservableValue<F> root,
		BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeTableEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addTreeTable3(root, children, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> p.addTabs(tabs));
	}

	@Override
	public AbstractQuickContainerPopulator addSplit(boolean vertical, Consumer<SplitPane<?>> split) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> p.addSplit(vertical, split));
	}

	@Override
	public AbstractQuickContainerPopulator addScroll(String fieldName, Consumer<PanelPopulation.ScrollPane<?>> scroll) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addScroll(fieldName, scroll));
	}

	@Override
	public <S> AbstractQuickContainerPopulator addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addComponent(fieldName, component, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addCollapsePanel(boolean vertical, LayoutManager layout,
		Consumer<CollapsePanel<JXCollapsiblePane, JPanel, ?>> panel) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addCollapsePanel(vertical, layout, panel));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTextField(String fieldName, SettableValue<F> field, Format<F> format,
		Consumer<FieldEditor<ObservableTextField<F>, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addTextField(fieldName, field, format, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTextArea(String fieldName, SettableValue<F> field, Format<F> format,
		Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addTextArea(fieldName, field, format, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addStyledTextArea(String fieldName, ObservableStyledDocument<F> doc,
		Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addStyledTextArea(fieldName, doc, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
		Consumer<LabelEditor<JLabel, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addLabel(fieldName, field, format, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addIcon(String fieldName, ObservableValue<Icon> icon,
		Consumer<FieldEditor<JLabel, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addIcon(fieldName, icon, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addLink(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
		Consumer<Object> action, Consumer<FieldEditor<JLabel, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addLink(fieldName, field, format, action, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addCheckField(String fieldName, SettableValue<Boolean> field,
		Consumer<ButtonEditor<JCheckBox, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addCheckField(fieldName, field, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addRadioButton(String fieldName, SettableValue<Boolean> field,
		Consumer<ButtonEditor<JRadioButton, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addRadioButton(fieldName, field, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addToggleButton(String fieldName, SettableValue<Boolean> field, String text,
		Consumer<ButtonEditor<JToggleButton, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addToggleButton(fieldName, field, text, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addSpinnerField(String fieldName, JSpinner spinner, SettableValue<F> value,
		Function<? super F, ? extends F> purifier, Consumer<SteppedFieldEditor<JSpinner, F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addSpinnerField(fieldName, spinner, value, purifier, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addSpinnerField(String fieldName, SettableValue<F> value, Format<F> format,
		Function<? super F, ? extends F> previousValue, Function<? super F, ? extends F> nextValue,
		Consumer<FieldEditor<ObservableSpinner<F>, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addSpinnerField(fieldName, value, format, previousValue, nextValue, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addSlider(String fieldName, SettableValue<Double> value,
		Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addSlider(fieldName, value, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addMultiSlider(String fieldName, ObservableCollection<Double> values,
		Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addMultiSlider(fieldName, values, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addRangeSlider(String fieldName, SettableValue<Range> range,
		Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addRangeSlider(fieldName, range, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addMultiRangeSlider(String fieldName, ObservableCollection<Range> values,
		Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addMultiRangeSlider(fieldName, values, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addComboField(String fieldName, SettableValue<F> value,
		List<? extends F> availableValues, Consumer<ComboEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addComboField(fieldName, value, availableValues, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addFileField(String fieldName, SettableValue<File> value, boolean open,
		Consumer<FieldEditor<ObservableFileButton, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addFileField(fieldName, value, open, modify));
	}

	@Override
	public <F, TB extends JToggleButton> AbstractQuickContainerPopulator addToggleField(String fieldName, SettableValue<F> value,
		List<? extends F> values, Class<TB> buttonType, Function<? super F, ? extends TB> buttonCreator,
		Consumer<ToggleEditor<F, TB, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addToggleField(fieldName, value, values, buttonType, buttonCreator, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addButton(String buttonText, ObservableAction action,
		Consumer<ButtonEditor<JButton, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addButton(buttonText, action, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addComboButton(String buttonText, ObservableCollection<F> values,
		BiConsumer<? super F, Object> action, Consumer<ComboButtonBuilder<F, ComboButton<F>, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addComboButton(buttonText, values, action, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addProgressBar(String fieldName, Consumer<ProgressEditor<?>> progress) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> p.addProgressBar(fieldName, progress));
	}

	@Override
	public <R> AbstractQuickContainerPopulator addList(ObservableCollection<R> rows, Consumer<ListBuilder<R, ?>> list) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> p.addList(rows, list));
	}

	@Override
	public AbstractQuickContainerPopulator addSettingsMenu(Consumer<SettingsMenu<JPanel, ?>> menu) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> p.addSettingsMenu(menu));
	}

	@Override
	public AbstractQuickContainerPopulator withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public JPanel getContainer() {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator withFieldName(ObservableValue<String> fieldName) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator modifyFieldLabel(Consumer<FontAdjuster> font) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator withFont(Consumer<FontAdjuster> font) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public JPanel getEditor() {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator visibleWhen(ObservableValue<Boolean> visible) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator fill() {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator fillV() {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator decorate(Consumer<ComponentDecorator> decoration) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator repaintOn(Observable<?> repaint) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator modifyEditor(Consumer<? super JPanel> modify) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator modifyComponent(Consumer<Component> component) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator modifyAssociatedComponents(Consumer<Component> component) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public void modifyAssociatedComponent(Component component) {}

	@Override
	public Component getComponent() {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator withLayoutConstraints(Object constraints) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator onMouse(Consumer<MouseEvent> onMouse) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator withName(String name) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public AbstractQuickContainerPopulator withTooltip(ObservableValue<String> tooltip) {
		throw new UnsupportedOperationException("Should not call this here");
	}

	@Override
	public ObservableValue<String> getTooltip() {
		return ObservableValue.of(String.class, null);
	}
}