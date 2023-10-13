package org.observe.util.swing;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.JFormattedTextField.AbstractFormatterFactory;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.SpinnerUI;
import javax.swing.plaf.basic.BasicSpinnerUI;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.util.swing.ObservableTextEditor.ObservableTextEditorWidget;
import org.qommons.ThreadConstraint;
import org.qommons.io.Format;

public class ObservableSpinner<T> extends JSpinner implements ObservableTextEditorWidget<T, ObservableSpinner<T>> {
	private final ObservableTextEditor<T> theEditor;
	private final JFormattedTextField theTextField;

	public ObservableSpinner(SettableValue<T> value, Format<T> format, Function<? super T, ? extends T> previousMaker,
		Function<? super T, ? extends T> nextMaker, Observable<?> until) {
		super(new ObservableSpinnerModel<>(value, previousMaker, nextMaker, until));
		ObservableSpinnerModel<T> model = (ObservableSpinnerModel<T>) getModel();
		SpinnerUI spinnerUI = getUI();
		setUI(new ObservableSpinnerUI<>(model, spinnerUI instanceof BasicSpinnerUI ? (BasicSpinnerUI) spinnerUI : null, until));
		JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) getEditor();
		theTextField = editor.getTextField();
		theTextField.setEditable(true);
		theTextField.setFormatterFactory(new AbstractFormatterFactory() {
			@Override
			public AbstractFormatter getFormatter(JFormattedTextField tf) {
				return new AbstractFormatter() {
					@Override
					public String valueToString(Object value2) throws ParseException {
						return format.format((T) value2);
					}

					@Override
					public Object stringToValue(String text) throws ParseException {
						return format.parse(text);
					}
				};
			}
		});
		theEditor = new ObservableTextEditor<T>(theTextField, value, format, until, //
			e -> ObservableSpinner.super.setEnabled(e), //
			tt -> ObservableSpinner.super.setToolTipText(tt)) {
			@Override
			protected void adjust(boolean up, Object cause) {
				T adjacent = up ? model.getNextValue() : model.getPreviousValue();
				if (adjacent != null)
					model.setValue(adjacent);
			}
		};
	}

	@Override
	public Format<T> getFormat() {
		if (theEditor == null)
			return null;
		return theEditor.getFormat();
	}

	@Override
	public ObservableSpinner<T> withWarning(Function<? super T, String> warning) {
		if (theEditor != null)
			theEditor.withWarning(warning);
		return this;
	}

	@Override
	public ObservableSpinner<T> clearWarning() {
		if (theEditor != null)
			theEditor.clearWarning();
		return this;
	}

	@Override
	public boolean isSelectAllOnFocus() {
		if (theEditor == null)
			return false;
		return theEditor.isSelectAllOnFocus();
	}

	@Override
	public ObservableSpinner<T> setSelectAllOnFocus(boolean selectAll) {
		if (theEditor != null)
			theEditor.setSelectAllOnFocus(selectAll);
		return this;
	}

	@Override
	public ObservableSpinner<T> setReformatOnCommit(boolean format) {
		if (theEditor != null)
			theEditor.setReformatOnCommit(format);
		return this;
	}

	@Override
	public ObservableSpinner<T> setRevertOnFocusLoss(boolean revert) {
		if (theEditor != null)
			theEditor.setRevertOnFocusLoss(revert);
		return this;
	}

	@Override
	public ObservableSpinner<T> setCommitOnType(boolean commit) {
		if (theEditor != null)
			theEditor.setCommitOnType(commit);
		return this;
	}

	@Override
	public ObservableSpinner<T> setCommitAdjustmentImmediately(boolean commitImmediately) {
		if (theEditor != null)
			theEditor.setCommitAdjustmentImmediately(commitImmediately);
		return this;
	}

	@Override
	public ObservableSpinner<T> onEnter(BiConsumer<? super T, ? super KeyEvent> action) {
		if (theEditor != null)
			theEditor.onEnter(action);
		return this;
	}

	@Override
	public ObservableSpinner<T> withToolTip(String tooltip) {
		if (theEditor != null)
			theEditor.withToolTip(tooltip);
		else
			super.setToolTipText(tooltip);
		return this;
	}

	/**
	 * @param cols The minimum number of columns of text to display
	 * @return This text field
	 */
	public ObservableSpinner<T> withColumns(int cols) {
		theTextField.setColumns(cols);
		return this;
	}

	@Override
	public String getEmptyText() {
		return null;
	}

	@Override
	public ObservableSpinner<T> setEmptyText(String emptyText) {
		System.err.println("Spinner empty text unsupported");
		return this;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (theEditor != null)
			theEditor.setEnabled(enabled);
		else
			super.setEnabled(enabled);
	}

	@Override
	public void setToolTipText(String text) {
		if (theEditor != null)
			theEditor.setToolTipText(text);
		else
			super.setToolTipText(text);
	}

	/** @return Whether the user has entered text to change this field's value */
	@Override
	public boolean isDirty() {
		if (theEditor == null)
			return false;
		return theEditor.isDirty();
	}

	/** Undoes any edits in this field's text, reverting to the formatted current value */
	@Override
	public void revertEdits() {
		if (theEditor != null)
			theEditor.revertEdits();
	}

	/**
	 * Causes any edits in this field's text to take effect, parsing it and setting it in the value
	 *
	 * @param cause The cause of the action (e.g. a swing event)
	 * @return Whether the edits (if any) were successfully committed to the value or were rejected. If there were no edits, this returns
	 *         true.
	 */
	@Override
	public boolean flushEdits(Object cause) {
		if (theEditor == null)
			return false;
		return theEditor.flushEdits(cause);
	}

	/**
	 * @return The error for the current text of this field. Specifically, either:
	 *         <ol>
	 *         <li>The message in the {@link ParseException} thrown by this field's {@link #getFormat() format} when the text was parsed (or
	 *         "Invalid text" if the exception message was null) or</li>
	 *         <li>The message reported by the value ({@link SettableValue#isAcceptable(Object)}) for the parsed value</li>
	 *         <ol>
	 */
	@Override
	public String getEditError() {
		if (theEditor == null)
			return null;
		return theEditor.getEditError();
	}

	@Override
	public String getEditWarning() {
		if (theEditor == null)
			return null;
		return theEditor.getEditWarning();
	}

	@Override
	public ObservableValue<String> getErrorState() {
		if (theEditor == null)
			return null;
		return theEditor.getErrorState();
	}

	@Override
	public ObservableValue<String> getWarningState() {
		if (theEditor == null)
			return null;
		return theEditor.getWarningState();
	}

	/** Re-displays the parsing error message from this text field as a tooltip */
	public void redisplayErrorTooltip() {
		if (theEditor != null)
			theEditor.redisplayErrorTooltip();
	}

	/** Re-displays the warning message from this text field as a tooltip */
	public void redisplayWarningTooltip() {
		if (theEditor != null)
			theEditor.redisplayWarningTooltip();
	}

	public static class ObservableSpinnerModel<T> implements SpinnerModel {
		private final SettableValue<T> theValue;
		private final Function<? super T, ? extends T> thePreviousMaker;
		private final Function<? super T, ? extends T> theNextMaker;

		private final List<ChangeListener> theChangeListeners;
		private Subscription theValueChangeSub;

		private long theCachedValueStamp;
		private T theCachedPrevious;
		private boolean isPreviousValid;
		private T theCachedNext;
		private boolean isNextValid;

		public ObservableSpinnerModel(SettableValue<T> value, Function<? super T, ? extends T> previousMaker,
			Function<? super T, ? extends T> nextMaker, Observable<?> until) {
			theValue = value;
			thePreviousMaker = previousMaker;
			theNextMaker = nextMaker;
			theChangeListeners = new ArrayList<>();
			until.take(1).act(__ -> {
				if (theValueChangeSub != null) {
					theValueChangeSub.unsubscribe();
					theValueChangeSub = null;
				}
			});
		}

		public SettableValue<T> getObservableValue() {
			return theValue;
		}

		@Override
		public T getValue() {
			return theValue.get();
		}

		@Override
		public void setValue(Object value) {
			theCachedValueStamp = -1;
			theCachedPrevious = null;
			theCachedNext = null;
			theValue.set((T) value, null);
		}

		@Override
		public T getPreviousValue() {
			updateCache();
			return isPreviousValid ? theCachedPrevious : null;
		}

		public T getWouldBePrevious() {
			updateCache();
			return theCachedPrevious;
		}

		@Override
		public T getNextValue() {
			updateCache();
			return isNextValid ? theCachedNext : null;
		}

		public T getWouldBeNext() {
			updateCache();
			return theCachedNext;
		}

		private void updateCache() {
			long stamp = theValue.getStamp();
			if (stamp == theCachedValueStamp)
				return;

			theCachedValueStamp = stamp;
			T value = theValue.get();

			theCachedPrevious = thePreviousMaker.apply(value);
			isPreviousValid = theCachedPrevious != null && theValue.isAcceptable(theCachedPrevious) == null;

			theCachedNext = theNextMaker.apply(value);
			isNextValid = theCachedNext != null && theValue.isAcceptable(theCachedNext) == null;
		}

		@Override
		public void addChangeListener(ChangeListener l) {
			if (theChangeListeners.isEmpty())
				startListening();
			theChangeListeners.add(l);
		}

		@Override
		public void removeChangeListener(ChangeListener l) {
			if (theChangeListeners.remove(l) && theChangeListeners.isEmpty() && theValueChangeSub != null) {
				theValueChangeSub.unsubscribe();
				theValueChangeSub = null;
			}
		}

		private void startListening() {
			// Combine with enabled so when enablement changes, the buttons become enabled/disabled
			SimpleObservable<Void> until = new SimpleObservable<>();
			Subscription valueSub = theValue.transform(theValue.getType(), tx -> tx//
				.combineWith(theValue.isEnabled()).combine((v, e) -> v))//
				.safe(ThreadConstraint.EDT, until)//
				.noInitChanges().act(evt -> {
					ChangeEvent swingEvt = new ChangeEvent(this);
					for (ChangeListener listener : theChangeListeners)
						listener.stateChanged(swingEvt);
				});
			theValueChangeSub = Subscription.forAll(() -> until.onNext(null), valueSub);
		}
	}

	static class ObservableSpinnerUI<T> extends BasicSpinnerUI {
		private final ObservableSpinnerModel<T> theModel;
		private final BasicSpinnerUI theBacking;
		private final Observable<?> theUntil;

		ObservableSpinnerUI(ObservableSpinnerModel<T> model, BasicSpinnerUI backing, Observable<?> until) {
			theModel = model;
			theBacking = backing;
			theUntil = until;
		}

		@Override
		protected Component createPreviousButton() {
			Component button = super.createPreviousButton();
			if (button instanceof JComponent) {
				JComponent jButton = (JComponent) button;
				theModel.getObservableValue().transform(theModel.getObservableValue().getType(), tx -> tx//
					.combineWith(theModel.getObservableValue().isEnabled()).combine((v, e) -> v))//
				.safe(ThreadConstraint.EDT, theUntil)//
				.changes().takeUntil(theUntil).act(evt -> {
					if (theModel.getPreviousValue() != null)
						jButton.setToolTipText(null);
					else {
						T previousValue = theModel.getWouldBePrevious();
						if (previousValue == null)
							jButton.setToolTipText(null);
						else
							jButton.setToolTipText(theModel.getObservableValue().isAcceptable(previousValue));
					}
				});
			}
			return button;
		}

		@Override
		protected Component createNextButton() {
			Component button = super.createNextButton();
			if (button instanceof JComponent) {
				JComponent jButton = (JComponent) button;
				theModel.getObservableValue().transform(theModel.getObservableValue().getType(), tx -> tx//
					.combineWith(theModel.getObservableValue().isEnabled()).combine((v, e) -> v))//
				.safe(ThreadConstraint.EDT, theUntil)//
				.changes().takeUntil(theUntil).act(evt -> {
					if (theModel.getNextValue() != null)
						jButton.setToolTipText(null);
					else {
						T nextValue = theModel.getWouldBeNext();
						if (nextValue == null)
							jButton.setToolTipText(null);
						else
							jButton.setToolTipText(theModel.getObservableValue().isAcceptable(nextValue));
					}
				});
			}
			return button;
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof ObservableSpinnerUI)
				return theBacking.equals(((ObservableSpinnerUI<?>) obj).theBacking);
			return theBacking.equals(obj);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}
	}
}
