package org.observe.util.swing;

import java.time.Duration;

import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.qommons.io.Format;
import org.qommons.threading.QommonsTimer;

public class ObservableComboTest {
	public static void main(String[] args) {
		class ValueHolder {
			int value;

			ValueHolder(int value) {
				this.value = value;
			}
		}
		final int DISABLE_MOD = 3;
		int[] modDisabled = new int[1];
		ObservableCollection<ValueHolder> comboSourceValues = ObservableCollection.build(ValueHolder.class).safe(false).build();
		for (int i = 0; i < 100; i++)
			comboSourceValues.add(new ValueHolder(i));
		SimpleObservable<Void> refresh = SimpleObservable.build().safe(false).build();
		SettableValue<ValueHolder> selected = SettableValue.build(ValueHolder.class).safe(false).withValue(comboSourceValues.getFirst())
			.build().filterAccept(vh -> vh.value % DISABLE_MOD != modDisabled[0] ? null : "Mod=" + modDisabled[0]);
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
			modDisabled[0] = (modDisabled[0] + 2) % DISABLE_MOD;
			refresh.onNext(null);
		}, Duration.ofSeconds(2), false).onEDT().setActive(true);
	}
}
