package org.observe.util.swing;

import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.ParseException;

import javax.swing.JTextField;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.observe.Observable;
import org.observe.SettableValue;
import org.qommons.io.Format;

public class ObservableTextField<E> extends JTextField {
	private final SettableValue<E> theValue;
	private final Format<E> theFormat;
	private boolean reformatOnCommit;
	private boolean isInternallyChanging;
	private boolean isDirty;

	private final Color normal_bg = getBackground();
	private final Color disabled_bg;
	private final Color error_bg = new Color(255, 200, 200);
	private final Color error_disabled_bg = new Color(200, 150, 150);

	private boolean revertOnFocusLoss;
	private boolean commitOnType;
	private String theError;
	private boolean isExternallyEnabled;
	private String theToolTip;

	public ObservableTextField(SettableValue<E> value, Format<E> format, Observable<?> until) {
		theValue = value;
		theFormat = format;
		if (until == null)
			until = Observable.empty;
		reformatOnCommit = true;

		isExternallyEnabled = true;
		super.setEnabled(false);
		disabled_bg = getBackground();
		super.setEnabled(true);

		revertOnFocusLoss = true;
		theValue.changes().takeUntil(until).act(evt -> {
			if (!isInternallyChanging)
				setValue(evt.getNewValue());
		});
		theValue.isEnabled().changes().takeUntil(until).act(evt -> {
			checkEnabled();
			setErrorState(theError);
		});
		getDocument().addDocumentListener(new DocumentListener() {
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

			private void checkText(Object cause) {
				isDirty = true;
				try {
					E parsed = theFormat.parse(getText());
					String err = theValue.isAcceptable(parsed);
					setErrorState(err);
					if (err == null && commitOnType)
						doCommit(parsed, cause, false);
				} catch (ParseException e) {
					setErrorState(e.getMessage() == null ? "Invalid text" : e.getMessage());
				}
			}
		});
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					revertEdits();
				else if (e.getKeyCode() == KeyEvent.VK_ENTER)
					flushEdits(e);
			}
		});
		addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				selectAll();
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (isDirty) {
					isInternallyChanging = true;
					try {
						E parsed = theFormat.parse(getText());
						theValue.set(parsed, e);
					} catch (ParseException | UnsupportedOperationException | IllegalArgumentException ex) {
						isInternallyChanging = false;
						if (revertOnFocusLoss)
							revertEdits();
					} finally {
						isInternallyChanging = false;
					}
				}
			}
		});
	}

	public SettableValue<E> getValue() {
		return theValue;
	}

	public Format<E> getFormat() {
		return theFormat;
	}

	/**
	 * @param format Whether this text field should, after successful editing, replace the user-entered text with the formatted value
	 * @return This text field
	 */
	public ObservableTextField<E> setReformatOnCommit(boolean format) {
		reformatOnCommit = format;
		return this;
	}

	/**
	 * @param revert Whether this text field should, when it loses focus while its text is either not {@link Format#parse(CharSequence)
	 *        parseable} or {@link SettableValue#isAcceptable(Object) acceptable}, revert its text to the formatted current model value. If
	 *        false, the text field will remain in an error state on focus lost.
	 * @return This text field
	 */
	public ObservableTextField<E> setRevertOnFocusLoss(boolean revert) {
		revertOnFocusLoss = revert;
		return this;
	}

	/**
	 * @param commit Whether this text field should update the model value with the parsed content of the text field each time the user
	 *        types a key (assuming the text parses correctly and the value is {@link SettableValue#isAcceptable(Object) acceptable}.
	 * @return This text field
	 */
	public ObservableTextField<E> setCommitOnType(boolean commit) {
		commitOnType = commit;
		return this;
	}

	public ObservableTextField<E> withToolTip(String tooltip) {
		setToolTipText(tooltip);
		return this;
	}

	public ObservableTextField<E> withColumns(int cols) {
		setColumns(cols);
		return this;
	}

	@Override
	public void setEnabled(boolean enabled) {
		isExternallyEnabled = enabled;
		checkEnabled();
	}

	@Override
	public void setToolTipText(String text) {
		theToolTip = text;
		setErrorState(theError);
	}

	public void revertEdits() {
		setValue(theValue.get());
	}

	public void flushEdits(Object cause) {
		if (!isDirty)
			return;
		isDirty = false;
		String text = getText();
		E parsed;
		try {
			parsed = theFormat.parse(text);
			setErrorState(null);
		} catch (ParseException e) {
			setErrorState(e.getMessage() == null ? "Parsing failed" : e.getMessage());
			return;
		}
		doCommit(parsed, cause, true);
	}

	private void doCommit(E parsed, Object cause, boolean maybeReformat) {
		isInternallyChanging = true;
		try {
			theValue.set(parsed, cause);
			if (maybeReformat && reformatOnCommit)
				setText(theFormat.format(parsed));
		} catch (IllegalArgumentException | UnsupportedOperationException e) {
			setErrorState(e.getMessage());
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

	public void redisplayErrorTooltip() {
		if (theError != null) {
			super.setToolTipText(theError);
			setTooltipVisible(true);
		}
	}

	private void setValue(E value) {
		String formatted;
		try {
			formatted = theFormat.format(value);
			setErrorState(null);
		} catch (RuntimeException e) {
			System.err.println(ObservableTextField.class.getName() + " could not format value " + value);
			e.printStackTrace();
			setErrorState("Could not format value " + value);
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
		if (isEnabled() != enabled)
			super.setEnabled(enabled);
	}

	private void setErrorState(String error) {
		boolean prevError = theError != null;
		theError = error;
		String disabled = theValue.isEnabled().get();
		if (theError != null) {
			if (disabled != null)
				setBackground(error_disabled_bg);
			else
				setBackground(error_bg);
		} else if (disabled != null)
			setBackground(disabled_bg);
		else
			setBackground(normal_bg);

		if (theError != null)
			redisplayErrorTooltip();
		else {
			if (prevError)
				setTooltipVisible(false);
			if (disabled != null)
				super.setToolTipText(disabled);
			else
				super.setToolTipText(theToolTip);
		}
	}

	private long toolTipLastDisplayed = 0;

	private void setTooltipVisible(boolean visible) {
		// Super hacky, but not sure how else to do this. Swing's tooltip system doesn't have many hooks into it.
		// This won't work right if the tooltip is already displayed or dismissed due to user interaction within the dismiss delay,
		// but will start working correctly again after the dismiss delay elapses
		// Overall, this approach may be somewhat flawed, but it's about the best I can do,
		// the potential negative consequences are small, and I think it's a very good feature
		long now = System.currentTimeMillis();
		boolean displayed = (now - toolTipLastDisplayed) < ToolTipManager.sharedInstance().getDismissDelay();
		if (visible == displayed) {
			// Tooltip is visible or invisible according to the parameter already
		} else {
			if (visible)
				toolTipLastDisplayed = now;
			else
				toolTipLastDisplayed = 0;
			KeyEvent ke = new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_MASK, KeyEvent.VK_F1,
				KeyEvent.CHAR_UNDEFINED);
			dispatchEvent(ke);
		}
	}
}
