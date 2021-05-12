package org.observe.util.swing;

import java.time.Duration;

import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.qommons.io.Format;
import org.qommons.threading.QommonsTimer;

/**
 * <p>
 * This is a simple demo of a combo box powered by observables. It demonstrates handling of changing models, enablement, and tooltips.
 * </p>
 * <p>
 * Displays a small window with a combo box. The combo box has 100 integers, starting from 0 to 99, all of which increment every 2 seconds.
 * Only values not evenly divisible by 3 can be selected at any given moment.
 * </p>
 */
public class ObservableComboTest {
	/** @param args Command-line arguments, ignored */
	public static void main(String[] args) {
		class ValueHolder {
			int value;

			ValueHolder(int value) {
				this.value = value;
			}
		}
		final int DISABLE_MOD = 3;
		ObservableCollection<ValueHolder> comboSourceValues = ObservableCollection.build(ValueHolder.class).safe(false).build();
		for (int i = 0; i < 100; i++)
			comboSourceValues.add(new ValueHolder(i));
		SimpleObservable<Void> refresh = SimpleObservable.build().safe(false).build();
		SettableValue<ValueHolder> selected = SettableValue.build(ValueHolder.class).safe(false).withValue(comboSourceValues.getFirst())
			.build().filterAccept(vh -> vh.value % DISABLE_MOD != 0 ? null : vh.value + "%" + DISABLE_MOD + "=0");
		ObservableSwingUtils.systemLandF();
		WindowPopulation.populateWindow(null, null, true, true)//
		.withVContent(panel -> {
			panel.addComboField("Test:", selected, comboSourceValues.flow().refresh(refresh).collect(), combo -> {
				combo.renderAs(vh -> vh == null ? "" : "" + vh.value).withValueTooltip(vh -> vh == null ? "" : "" + vh.value);
			});
			panel.addLabel("Selected:", selected.refresh(refresh).map(vh -> vh == null ? -1 : vh.value), Format.INT, null);
		}).getWindow().setVisible(true);

		QommonsTimer.getCommonInstance().build(() -> {
			for (ValueHolder vh : comboSourceValues)
				vh.value++;
			refresh.onNext(null);
		}, Duration.ofSeconds(2), false).onEDT().setActive(true);
	}
}
