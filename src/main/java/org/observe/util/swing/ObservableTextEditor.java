package org.observe.util.swing;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;

/**
 * The backend logic of an observable text editor like a text field or text area
 *
 * @param <E> The type of value edited by this text component
 */
public class ObservableTextEditor<E> {
	/**
	 * A widget that is powered by an {@link ObservableTextEditor}
	 *
	 * @param <E> The type of value being edited
	 * @param <W> The type of this widget
	 */
	public interface ObservableTextEditorWidget<E, W extends ObservableTextEditorWidget<E, W>> {
		/** @return The value controlled by this text field */
		SettableValue<E> getValue();

		/** @return The format converting between value and text */
		Format<E> getFormat();

		/**
		 * Configures a warning message as a function of this text field's value. The user will be allowed to enter a value with a warning
		 * but a UI hit will be provided that there may be some problem with the value.
		 *
		 * @param warning The function to provide a warning message or null for no warning
		 * @return This text field
		 */
		W withWarning(Function<? super E, String> warning);

		/**
		 * Clears this text field's {@link #withWarning(Function) warning}
		 *
		 * @return This text field
		 */
		W clearWarning();

		/** @return Whether this text field selects all its text when it gains focus */
		boolean isSelectAllOnFocus();

		/**
		 * @param selectAll Whether this text field should select all its text when it gains focus
		 * @return This text field
		 */
		W setSelectAllOnFocus(boolean selectAll);

		/**
		 * @param format Whether this text field should, after successful editing, replace the user-entered text with the formatted value
		 * @return This text field
		 */
		W setReformatOnCommit(boolean format);

		/**
		 * @param revert Whether this text field should, when it loses focus while its text is either not {@link Format#parse(CharSequence)
		 *        parseable} or {@link SettableValue#isAcceptable(Object) acceptable}, revert its text to the formatted current model value.
		 *        If false, the text field will remain in an error state on focus lost.
		 * @return This text field
		 */
		W setRevertOnFocusLoss(boolean revert);

		/**
		 * @param commit Whether this text field should update the model value with the parsed content of the text field each time the user
		 *        types a key (assuming the text parses correctly and the value is {@link SettableValue#isAcceptable(Object) acceptable}.
		 * @return This text field
		 */
		W setCommitOnType(boolean commit);

		/**
		 * If the {@link #getFormat() format} given to this text field is an instance of {@link SpinnerFormat}, this user can adjust the
		 * text field's value incrementally using the up or down arrow keys. The nature and magnitude of the adjustment may depend on the
		 * particular {@link SpinnerFormat} implementation as well as the position of the cursor.
		 *
		 * @param commitImmediately Whether {@link SpinnerFormat#adjust(Object, String, int, boolean) adjustments} to the value should be
		 *        committed to the model value immediately. If false, the adjustments will only be made to the text and the value will be
		 *        committed when the user presses enter or when focus is lost.
		 * @return This text field
		 */
		W setCommitAdjustmentImmediately(boolean commitImmediately);

		/**
		 * <p>
		 * Sets an action to be performed when the user presses the ENTER key while focused on this text field (after a successful value
		 * commit).
		 * </p>
		 *
		 * <p>
		 * The text field only contains one such listener, so this call replaces any that may have been previously set.
		 * </p>
		 *
		 * @param action The action to perform when the user presses the ENTER key
		 * @return This text field
		 */
		W onEnter(BiConsumer<? super E, ? super KeyEvent> action);

		/**
		 * @param tooltip The tooltip for this text field (when enabled)
		 * @return This text field
		 */
		W withToolTip(String tooltip);

		/** @return The text to display (grayed) when the text field's text is empty */
		String getEmptyText();

		/**
		 * @param emptyText The text to display (grayed) when the text field's text is empty
		 * @return This text field
		 */
		W setEmptyText(String emptyText);

		/** @return Whether the user has entered text to change this field's value */
		boolean isDirty();

		/** Undoes any edits in this field's text, reverting to the formatted current value */
		void revertEdits();

		/**
		 * Causes any edits in this field's text to take effect, parsing it and setting it in the value
		 *
		 * @param cause The cause of the action (e.g. a swing event)
		 * @return Whether the edits (if any) were successfully committed to the value or were rejected. If there were no edits, this
		 *         returns true.
		 */
		boolean flushEdits(Object cause);

		/**
		 * @return The error for the current text of this field. Specifically, either:
		 *         <ol>
		 *         <li>The message in the {@link ParseException} thrown by this field's {@link #getFormat() format} when the text was parsed
		 *         (or "Invalid text" if the exception message was null) or</li>
		 *         <li>The message reported by the value ({@link SettableValue#isAcceptable(Object)}) for the parsed value</li>
		 *         <ol>
		 */
		String getEditError();

		/**
		 * @return The warning for the current text of this field--the string produced by the {@link #withWarning(Function) warning}
		 *         function set on this editor. Will be null if no warning function is set, or if there is an {@link #getEditError() error}.
		 */
		String getEditWarning();

		/**
		 * @return The error message displayed to the user
		 * @see #getEditError()
		 */
		ObservableValue<String> getErrorState();

		/**
		 * @return The warning message displayed to the user
		 * @see #getEditWarning()
		 */
		ObservableValue<String> getWarningState();
	}

	private final JTextComponent theComponent;
	private final Consumer<Boolean> theEnabledSetter;
	private final Consumer<String> theTooltipSetter;

	private final SettableValue<E> theValue;
	private final Format<E> theFormat;
	private Function<? super E, String> theWarning;
	private boolean reformatOnCommit;
	private boolean isInternallyChanging;
	private boolean isDirty;

	private final Color normal_bg;
	private final Color disabled_bg;
	private final Color error_bg = new Color(255, 200, 200);
	private final Color error_disabled_bg = new Color(200, 150, 150);
	private final Color warning_bg = new Color(250, 200, 90);
	private final Color warning_disabled_bg = new Color(235, 220, 150);

	private boolean selectAllOnFocus;
	private boolean revertOnFocusLoss;
	private boolean commitOnType;
	private String theError;
	private String theWarningMsg;
	private boolean isExternallyEnabled;
	private String theToolTip;

	private boolean commitAdjustmentImmediately;

	private BiConsumer<? super E, ? super KeyEvent> theEnterAction;

	private String theCachedText;
	private long theStateStamp;
	private SimpleObservable<Void> theStatusChange;

	/**
	 * @param component The text component this editor manages
	 * @param value The value linked to this editor's text
	 * @param format The format to use to translate between value and text
	 * @param until An observable to release this editor's resources and listeners
	 * @param enabled Called when the component's enablement should change
	 * @param tooltip Called when the component's tooltip should change
	 */
	public ObservableTextEditor(JTextComponent component, SettableValue<E> value, Format<E> format, Observable<?> until, //
		Consumer<Boolean> enabled, Consumer<String> tooltip) {
		theComponent = component;
		theEnabledSetter = enabled;
		theTooltipSetter = tooltip;
		theValue = value.safe(ThreadConstraint.EDT, until);
		theFormat = format;
		if (until == null)
			until = Observable.empty;
		reformatOnCommit = true;

		normal_bg = component.getBackground();
		isExternallyEnabled = true;
		enabled.accept(false);
		disabled_bg = component.getBackground();
		enabled.accept(true);

		selectAllOnFocus = true;
		revertOnFocusLoss = true;
		theValue.changes().takeUntil(until).act(evt -> {
			if (!isInternallyChanging && (!component.hasFocus() || evt.getOldValue() != evt.getNewValue()))
				setValue(evt.getNewValue());
		});
		theValue.isEnabled().changes().takeUntil(until).act(evt -> {
			if (evt.getOldValue() == evt.getNewValue())
				return;
			checkEnabled();
			setErrorState(theError, theWarningMsg);
		});
		theComponent.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				if (!isInternallyChanging) {
					checkText(e);
				}
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				if (!isInternallyChanging) {
					checkText(e);
				}
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				if (!isInternallyChanging) {
					checkText(e);
				}
			}
		});
		component.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					revertEdits();
				else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					if (flushEdits(e) && theEnterAction != null)
						theEnterAction.accept(theValue.get(), e);
				}
			}
		});
		component.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				if (selectAllOnFocus)
					theComponent.selectAll();
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (isDirty) {
					isInternallyChanging = true;
					try {
						if (!flushEdits(e) && revertOnFocusLoss) {
							isInternallyChanging = false;
							revertEdits();
						}
					} finally {
						isInternallyChanging = false;
					}
				}
			}
		});
		if (theFormat instanceof SpinnerFormat) {
			component.getInputMap().put(KeyStroke.getKeyStroke("UP"), "increment");
			component.getActionMap().put("increment", new AbstractAction("increment") {
				@Override
				public void actionPerformed(ActionEvent e) {
					adjust(true, e);
				}
			});
			component.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "decrement");
			component.getActionMap().put("decrement", new AbstractAction("decrement") {
				@Override
				public void actionPerformed(ActionEvent e) {
					adjust(false, e);
				}
			});
		}
	}

	/** @return The value controlled by this text field */
	public SettableValue<E> getValue() {
		return theValue;
	}

	/** @return The format converting between value and text */
	public Format<E> getFormat() {
		return theFormat;
	}

	/**
	 * Configures a warning message as a function of this text field's value. The user will be allowed to enter a value with a warning but a
	 * UI hit will be provided that there may be some problem with the value.
	 *
	 * @param warning The function to provide a warning message or null for no warning
	 * @return This text field
	 */
	public ObservableTextEditor<E> withWarning(Function<? super E, String> warning) {
		if (warning == null)
			return clearWarning();
		if (theWarning == null)
			theWarning = warning;
		else {
			Function<? super E, String> oldWarning = theWarning;
			theWarning = v -> {
				String msg = oldWarning.apply(v);
				if (msg == null)
					msg = warning.apply(v);
				return msg;
			};
		}
		return this;
	}

	/**
	 * Clears this text field's {@link #withWarning(Function) warning}
	 *
	 * @return This text field
	 */
	public ObservableTextEditor<E> clearWarning() {
		theWarning = null;
		return this;
	}

	/** @return Whether this text field selects all its text when it gains focus */
	public boolean isSelectAllOnFocus() {
		return selectAllOnFocus;
	}

	/**
	 * @param selectAll Whether this text field should select all its text when it gains focus
	 * @return This text field
	 */
	public ObservableTextEditor<E> setSelectAllOnFocus(boolean selectAll) {
		selectAllOnFocus = selectAll;
		return this;
	}

	/**
	 * @param format Whether this text field should, after successful editing, replace the user-entered text with the formatted value
	 * @return This text field
	 */
	public ObservableTextEditor<E> setReformatOnCommit(boolean format) {
		reformatOnCommit = format;
		return this;
	}

	/**
	 * @param revert Whether this text field should, when it loses focus while its text is either not {@link Format#parse(CharSequence)
	 *        parseable} or {@link SettableValue#isAcceptable(Object) acceptable}, revert its text to the formatted current model value. If
	 *        false, the text field will remain in an error state on focus lost.
	 * @return This text field
	 */
	public ObservableTextEditor<E> setRevertOnFocusLoss(boolean revert) {
		revertOnFocusLoss = revert;
		return this;
	}

	/**
	 * @param commit Whether this text field should update the model value with the parsed content of the text field each time the user
	 *        types a key (assuming the text parses correctly and the value is {@link SettableValue#isAcceptable(Object) acceptable}.
	 * @return This text field
	 */
	public ObservableTextEditor<E> setCommitOnType(boolean commit) {
		commitOnType = commit;
		return this;
	}

	/**
	 * If the {@link #getFormat() format} given to this text field is an instance of {@link SpinnerFormat}, this user can adjust the text
	 * field's value incrementally using the up or down arrow keys. The nature and magnitude of the adjustment may depend on the particular
	 * {@link SpinnerFormat} implementation as well as the position of the cursor.
	 *
	 * @param commitImmediately Whether {@link SpinnerFormat#adjust(Object, String, int, boolean) adjustments} to the value should be
	 *        committed to the model value immediately. If false, the adjustments will only be made to the text and the value will be
	 *        committed when the user presses enter or when focus is lost.
	 * @return This text field
	 */
	public ObservableTextEditor<E> setCommitAdjustmentImmediately(boolean commitImmediately) {
		commitAdjustmentImmediately = commitImmediately;
		return this;
	}

	/**
	 * <p>
	 * Sets an action to be performed when the user presses the ENTER key while focused on this text field (after a successful value
	 * commit).
	 * </p>
	 *
	 * <p>
	 * The text field only contains one such listener, so this call replaces any that may have been previously set.
	 * </p>
	 *
	 * @param action The action to perform when the user presses the ENTER key
	 * @return This text field
	 */
	public ObservableTextEditor<E> onEnter(BiConsumer<? super E, ? super KeyEvent> action) {
		theEnterAction = action;
		return this;
	}

	/**
	 * @param tooltip The tooltip for this text field (when enabled)
	 * @return This text field
	 */
	public ObservableTextEditor<E> withToolTip(String tooltip) {
		setToolTipText(tooltip);
		return this;
	}

	/**
	 * @return The error message displayed to the user
	 * @see #getEditError()
	 */
	public ObservableValue<String> getErrorState() {
		if (theStatusChange == null) {
			synchronized (this) {
				if (theStatusChange == null)
					theStatusChange = SimpleObservable.build().build();
			}
		}
		return ObservableValue.of(TypeTokens.get().STRING, () -> theError, () -> theStateStamp, theStatusChange);
	}

	/**
	 * @return The warning message displayed to the user
	 * @see #getEditWarning()
	 */
	public ObservableValue<String> getWarningState() {
		if (theStatusChange == null) {
			synchronized (this) {
				if (theStatusChange == null)
					theStatusChange = SimpleObservable.build().build();
			}
		}
		return ObservableValue.of(TypeTokens.get().STRING, () -> theWarningMsg, () -> theStateStamp, theStatusChange);
	}

	/** @param enabled Whether to allow the user to modify this editor when the value is enabled */
	public void setEnabled(boolean enabled) {
		isExternallyEnabled = enabled;
		checkEnabled();
	}

	/** @param text The tooltip text to display to the user when there is no error or warning */
	public void setToolTipText(String text) {
		theToolTip = text;
		setErrorState(theError, theWarningMsg);
	}

	/** @return Whether the user has entered text to change this field's value */
	public boolean isDirty() {
		return isDirty;
	}

	/** Undoes any edits in this field's text, reverting to the formatted current value */
	public void revertEdits() {
		setValue(theValue.get());
	}

	/** @return This editor's text */
	public String getText() {
		try {
			return theComponent.getDocument().getText(0, theComponent.getDocument().getLength());
		} catch (BadLocationException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Sets the text internally, not modifying the value
	 *
	 * @param text The text to display
	 * @return This editor
	 */
	protected ObservableTextEditor<E> setText(String text) {
		try {
			Document doc = theComponent.getDocument();
			if (doc instanceof AbstractDocument) {
				((AbstractDocument) doc).replace(0, doc.getLength(), text, null);
			} else {
				doc.remove(0, doc.getLength());
				doc.insertString(0, text, null);
			}
			theCachedText = text;
		} catch (BadLocationException e) {
			throw new IllegalStateException(e);
		}
		return this;
	}

	/**
	 * Causes any edits in this field's text to take effect, parsing it and setting it in the value
	 *
	 * @param cause The cause of the action (e.g. a swing event)
	 * @return Whether the edits (if any) were successfully committed to the value or were rejected. If there were no edits, this returns
	 *         true.
	 */
	public boolean flushEdits(Object cause) {
		if (!isDirty)
			return true;
		isDirty = false;
		String text = getText();
		E parsed;
		try {
			parsed = theFormat.parse(text);
			setErrorState(null, parsed);
		} catch (ParseException e) {
			setErrorState(e.getMessage() == null ? "Parsing failed" : e.getMessage(), (String) null);
			return false;
		}
		return doCommit(parsed, cause, true);
	}

	private boolean doCommit(E parsed, Object cause, boolean maybeReformat) {
		isInternallyChanging = true;
		try (Transaction t = theValue.lock(true, cause)) {
			theValue.set(parsed, cause);
			isDirty = false;
			if (maybeReformat && reformatOnCommit) {
				int selStart = theComponent.getSelectionStart();
				int selEnd = theComponent.getSelectionStart();
				int length = theComponent.getText().length();
				String newText = theFormat.format(parsed);
				setText(newText);
				if (newText.length() == length) {
					// Preserve selection
					theComponent.setSelectionStart(selStart);
					theComponent.setSelectionEnd(selEnd);
				}
			}
			return true;
		} catch (IllegalArgumentException | UnsupportedOperationException e) {
			setErrorState(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), (String) null);
			return false;
		} finally {
			isInternallyChanging = false;
		}
	}

	/**
	 * @return The error for the current text of this field. Specifically, either:
	 *         <ol>
	 *         <li>The message in the {@link ParseException} thrown by this field's {@link #getFormat() format} when the text was parsed (or
	 *         "Invalid text" if the exception message was null) or</li>
	 *         <li>The message reported by the value ({@link SettableValue#isAcceptable(Object)}) for the parsed value</li>
	 *         <ol>
	 */
	public String getEditError() {
		return theError;
	}

	/**
	 * @return The warning for the current text of this field--the string produced by the {@link #withWarning(Function) warning} function
	 *         set on this editor. Will be null if no warning function is set, or if there is an {@link #getEditError() error}.
	 */
	public String getEditWarning() {
		return theError;
	}

	/** Re-displays the parsing error message from this text field as a tooltip */
	public void redisplayErrorTooltip() {
		if (theError != null) {
			theTooltipSetter.accept(theError);
			ObservableSwingUtils.setTooltipVisible(theComponent, true);
		}
	}

	/** Re-displays the warning message from this text field as a tooltip */
	public void redisplayWarningTooltip() {
		if (theWarningMsg != null) {
			theTooltipSetter.accept(theWarningMsg);
			ObservableSwingUtils.setTooltipVisible(theComponent, true);
		}
	}

	private void checkText(Object cause) {
		String text = getText();
		if (Objects.equals(theCachedText, text))
			return;
		theCachedText = text;
		isDirty = true;
		try {
			E parsed = theFormat.parse(getText());
			String err = theValue.isAcceptable(parsed);
			setErrorState(err, parsed);
			if (err == null && commitOnType)
				doCommit(parsed, cause, false);
		} catch (ParseException e) {
			setErrorState(e.getMessage() == null ? "Invalid text" : e.getMessage(), (String) null);
		}
	}

	private void setValue(E value) {
		String formatted;
		try {
			formatted = theFormat.format(value);
			setErrorState(null, value);
		} catch (RuntimeException e) {
			System.err.println(ObservableTextField.class.getName() + " could not format value " + value);
			e.printStackTrace();
			setErrorState("Could not format value " + value, value);
			return;
		}
		isInternallyChanging = true;
		try {
			setText(formatted);
		} finally {
			isInternallyChanging = false;
		}
	}

	private void checkEnabled() {
		boolean enabled = isExternallyEnabled && theValue.isEnabled().get() == null;
		if (theComponent.isEnabled() != enabled)
			theEnabledSetter.accept(enabled);
	}

	private void setErrorState(String error, E parsedValue) {
		String warningMsg;
		if (error != null)
			warningMsg = null;
		else if (theWarning == null)
			warningMsg = null;
		else
			warningMsg = theWarning.apply(parsedValue);
		setErrorState(error, warningMsg);
	}

	private void setErrorState(String error, String warningMsg) {
		boolean prevError = theError != null;
		theError = error;
		theWarningMsg = warningMsg;
		String disabled = theValue.isEnabled().get();
		if (theError != null) {
			if (disabled != null)
				theComponent.setBackground(error_disabled_bg);
			else
				theComponent.setBackground(error_bg);
		} else if (warningMsg != null) {
			if (disabled != null)
				theComponent.setBackground(warning_disabled_bg);
			else
				theComponent.setBackground(warning_bg);
		} else {
			if (disabled != null)
				theComponent.setBackground(disabled_bg);
			else
				theComponent.setBackground(normal_bg);
		}

		if (theError != null)
			redisplayErrorTooltip();
		else if (theWarningMsg != null)
			redisplayWarningTooltip();
		else {
			if (prevError)
				ObservableSwingUtils.setTooltipVisible(theComponent, false);
			if (disabled != null)
				theTooltipSetter.accept(disabled);
			else
				theTooltipSetter.accept(theToolTip);
		}
		if (theStatusChange != null) {
			theStateStamp++;
			theStatusChange.onNext(null);
		}
	}

	/**
	 * @param up Whether to adjust the value up or down
	 * @param cause The cause of the modification (e.g. a key event)
	 */
	protected void adjust(boolean up, Object cause) {
		SpinnerFormat<E> spinnerFormat = (SpinnerFormat<E>) theFormat;
		String text = getText();
		E toAdjust;
		if (isDirty) {
			try {
				toAdjust = theFormat.parse(text);
			} catch (ParseException ex) {
				return;
			}
		} else
			toAdjust = theValue.get();
		int selectionStart = theComponent.getSelectionStart();
		int selectionEnd = theComponent.getSelectionEnd();
		boolean withContext = selectionStart == selectionEnd;
		if (withContext || spinnerFormat.supportsAdjustment(withContext)) {
			BiTuple<E, String> adjusted = spinnerFormat.adjust(toAdjust, text, withContext ? selectionStart : -1, up);
			if (adjusted == null) {//
			} else {
				isInternallyChanging = true;
				try {
					if (commitAdjustmentImmediately && theValue.isAcceptable(adjusted.getValue1()) == null) {
						try {
							theValue.set(adjusted.getValue1(), cause);
						} catch (RuntimeException ex) {
							return; // We did due diligence checking above, but whatever.
						}
					} else
						isDirty = true;
					String newText = adjusted.getValue2();
					setText(newText);
					selectionStart += newText.length() - text.length();
					selectionEnd += newText.length() - text.length();
					if (selectionEnd < 0) {
						selectionStart = selectionEnd = 0;
					} else if (selectionStart < 0)
						selectionStart = 0;
					if (selectionStart > newText.length())
						selectionStart = selectionEnd = newText.length();
					else if (selectionEnd > newText.length())
						selectionEnd = newText.length();
					theComponent.setSelectionStart(selectionStart);
					theComponent.setSelectionEnd(selectionEnd);
					checkText(cause);
				} finally {
					isInternallyChanging = false;
				}
			}
		}
	}
}
