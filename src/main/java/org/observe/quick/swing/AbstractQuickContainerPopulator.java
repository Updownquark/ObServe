package org.observe.quick.swing;

import java.awt.Component;
import java.awt.LayoutManager;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
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
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.jdesktop.swingx.JXPanel;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.swing.ComboButton;
import org.observe.util.swing.ComponentDecorator;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.MultiRangeSlider;
import org.observe.util.swing.MultiRangeSlider.Range;
import org.observe.util.swing.ObservableFileButton;
import org.observe.util.swing.ObservableStyledDocument;
import org.observe.util.swing.ObservableTextArea;
import org.observe.util.swing.ObservableTextField;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.*;
import org.qommons.collect.BetterList;
import org.qommons.io.Format;

public abstract class AbstractQuickContainerPopulator
implements PanelPopulation.PanelPopulator<JPanel, AbstractQuickContainerPopulator> {
	private List<Consumer<ComponentEditor<?, ?>>> theModifiers;

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
	public <R> AbstractQuickContainerPopulator addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> modify(p).addTable(rows, table));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTree(ObservableValue<? extends F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Consumer<TreeEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addTree(root, children, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTree3(ObservableValue<? extends F> root,
		BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addTree3(root, children, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTreeTable(ObservableValue<F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Consumer<TreeTableEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addTreeTable(root, children, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTreeTable3(ObservableValue<F> root,
		BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeTableEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p.withName("DEBUG")).addTreeTable3(root, children, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> modify(p).addTabs(tabs));
	}

	@Override
	public AbstractQuickContainerPopulator addSplit(boolean vertical, Consumer<SplitPane<?>> split) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> modify(p).addSplit(vertical, split));
	}

	@Override
	public AbstractQuickContainerPopulator addScroll(String fieldName, Consumer<PanelPopulation.ScrollPane<?>> scroll) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addScroll(fieldName, scroll));
	}

	@Override
	public <S> AbstractQuickContainerPopulator addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addComponent(fieldName, component, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addCollapsePanel(boolean vertical, LayoutManager layout,
		Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addCollapsePanel(vertical, layout, panel));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTextField(String fieldName, SettableValue<F> field, Format<F> format,
		Consumer<FieldEditor<ObservableTextField<F>, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addTextField(fieldName, field, format, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addTextArea(String fieldName, SettableValue<F> field, Format<F> format,
		Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addTextArea(fieldName, field, format, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addStyledTextArea(String fieldName, ObservableStyledDocument<F> doc,
		Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addStyledTextArea(fieldName, doc, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
		Consumer<LabelEditor<JLabel, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addLabel(fieldName, field, format, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addIcon(String fieldName, ObservableValue<Icon> icon,
		Consumer<FieldEditor<JLabel, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addIcon(fieldName, icon, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addLink(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
		Consumer<Object> action, Consumer<FieldEditor<JLabel, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addLink(fieldName, field, format, action, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addCheckField(String fieldName, SettableValue<Boolean> field,
		Consumer<FieldEditor<JCheckBox, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addCheckField(fieldName, field, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addToggleButton(String fieldName, SettableValue<Boolean> field, String text,
		Consumer<ButtonEditor<JToggleButton, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addToggleButton(fieldName, field, text, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addSpinnerField(String fieldName, JSpinner spinner, SettableValue<F> value,
		Function<? super F, ? extends F> purifier, Consumer<SteppedFieldEditor<JSpinner, F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addSpinnerField(fieldName, spinner, value, purifier, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addSlider(String fieldName, SettableValue<Double> value,
		Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addSlider(fieldName, value, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addMultiSlider(String fieldName, ObservableCollection<Double> values,
		Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addMultiSlider(fieldName, values, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addRangeSlider(String fieldName, SettableValue<Range> range,
		Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addRangeSlider(fieldName, range, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addMultiRangeSlider(String fieldName, ObservableCollection<Range> values,
		Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addMultiRangeSlider(fieldName, values, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addComboField(String fieldName, SettableValue<F> value,
		List<? extends F> availableValues, Consumer<ComboEditor<F, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addComboField(fieldName, value, availableValues, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addFileField(String fieldName, SettableValue<File> value, boolean open,
		Consumer<FieldEditor<ObservableFileButton, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addFileField(fieldName, value, open, modify));
	}

	@Override
	public <F, TB extends JToggleButton> AbstractQuickContainerPopulator addToggleField(String fieldName, SettableValue<F> value,
		List<? extends F> values, Class<TB> buttonType, Function<? super F, ? extends TB> buttonCreator,
		Consumer<ToggleEditor<F, TB, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addToggleField(fieldName, value, values, buttonType, buttonCreator, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addButton(String buttonText, ObservableAction action,
		Consumer<ButtonEditor<JButton, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addButton(buttonText, action, modify));
	}

	@Override
	public <F> AbstractQuickContainerPopulator addComboButton(String buttonText, ObservableCollection<F> values,
		BiConsumer<? super F, Object> action, Consumer<ComboButtonBuilder<F, ComboButton<F>, ?>> modify) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addComboButton(buttonText, values, action, modify));
	}

	@Override
	public AbstractQuickContainerPopulator addProgressBar(String fieldName, Consumer<ProgressEditor<?>> progress) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
			p -> modify(p).addProgressBar(fieldName, progress));
	}

	@Override
	public <R> AbstractQuickContainerPopulator addList(ObservableCollection<R> rows, Consumer<ListBuilder<R, ?>> list) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> modify(p).addList(rows, list));
	}

	@Override
	public AbstractQuickContainerPopulator addSettingsMenu(Consumer<SettingsMenu<JPanel, ?>> menu) {
		return addHPanel(null, new JustifiedBoxLayout(true).mainJustified().crossJustified(), p -> modify(p).addSettingsMenu(menu));
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
	public void addModifier(Consumer<ComponentEditor<?, ?>> modifier) {
		if (theModifiers == null)
			theModifiers = new ArrayList<>();
		theModifiers.add(modifier);
	}

	@Override
	public void removeModifier(Consumer<ComponentEditor<?, ?>> modifier) {
		if (theModifiers != null)
			theModifiers.remove(modifier);
	}

	protected <P extends PanelPopulator<?, ?>> P modify(P container) {
		if (theModifiers != null) {
			for (Consumer<ComponentEditor<?, ?>> modifier : theModifiers)
				container.addModifier(modifier);
		}
		return container;
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
}