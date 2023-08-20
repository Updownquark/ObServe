package org.observe.expresso.qonfig;

import java.awt.Image;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;

public interface AppEnvironment {
	InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle();

	InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> getIcon();

	default Instantiator instantiate() {
		return new Instantiator(getTitle() == null ? null : getTitle().instantiate(), getIcon() == null ? null : getIcon().instantiate());
	}

	public static class Instantiator {
		public final ModelValueInstantiator<SettableValue<String>> title;
		public final ModelValueInstantiator<SettableValue<Image>> icon;

		public Instantiator(ModelValueInstantiator<SettableValue<String>> title, ModelValueInstantiator<SettableValue<Image>> icon) {
			this.title = title;
			this.icon = icon;
		}
	}
}
