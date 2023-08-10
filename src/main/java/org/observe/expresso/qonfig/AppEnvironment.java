package org.observe.expresso.qonfig;

import java.awt.Image;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;

public interface AppEnvironment {
	InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle();

	InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> getIcon();
}
