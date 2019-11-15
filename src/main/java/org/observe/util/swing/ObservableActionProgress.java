package org.observe.util.swing;

import java.util.function.Consumer;

public interface ObservableActionProgress {
	ObservableActionProgress setMessage(String progressMessage);
	ObservableActionProgress setLength(double length);
	ObservableActionProgress setProgress(double progress);

	ObservableActionProgress setStageMessage();
	ObservableActionProgress setStageLength(double length);
	ObservableActionProgress setStageProgress(double progress);

	ObservableActionProgress failed(String message);
	ObservableActionProgress done(String message);

	ObservableActionProgress onFailure(Consumer<String> action);
	ObservableActionProgress onSuccess(Consumer<String> action);
}
