package org.observe.quick.textfilter;

import java.awt.EventQueue;
import java.beans.Transient;
import java.util.regex.Pattern;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.util.Cached;
import org.qommons.Causable;
import org.qommons.DefaultCharSubSequence;
import org.qommons.io.BetterPattern;
import org.qommons.io.BetterPattern.Match;
import org.qommons.io.BetterPattern.Matcher;

public interface QuickTextPattern {
	String getPattern();
	QuickTextPattern setPattern(String pattern);

	boolean isExtended();
	QuickTextPattern setExtended(boolean extended);

	boolean isMatchCase();
	QuickTextPattern setMatchCase(boolean matchCase);

	boolean isRegex();
	QuickTextPattern setRegex(boolean regex);

	boolean isDotMatchesNewLine();
	QuickTextPattern setDotMatchesNewLine(boolean dotMatchesNewLine);

	@Transient
	String getError();
	QuickTextPattern setError(String error);

	@Transient
	String getStatus();
	QuickTextPattern setStatus(String status);

	@Transient
	BetterPattern getRealPattern();
	void setRealPattern(BetterPattern pattern);

	default BetterPattern.Match find(CharSequence str) {
		BetterPattern pattern = getRealPattern();
		if (pattern == null) {
			rebuild();
			pattern = getRealPattern();
		}
		return pattern.matcher(str).find();
	}

	@Cached
	@Transient
	default Observable<Void> getUpdate() {
		return new SimpleObservable<>();
	}

	default QuickTextPattern init() {
		setPattern("");
		rebuild();
		return this;
	}

	default QuickTextPattern rebuild() {
		String error;
		if (getPattern() == null || getPattern().isEmpty()) {
			error = "No pattern set";
		} else if (isRegex()) {
			error = null;
			try {
				Pattern pattern = Pattern.compile(getPattern(), //
					Pattern.MULTILINE//
					| (isMatchCase() ? 0 : Pattern.CASE_INSENSITIVE)//
					| (isDotMatchesNewLine() ? Pattern.DOTALL : 0)//
					);
				setRealPattern(BetterPattern.forJavaRegex(pattern));
			} catch (RuntimeException e) {
				e.printStackTrace();
				error = "Invalid pattern: " + e.getMessage();
			}
		} else if (isExtended()) {
			StringBuilder realString = new StringBuilder(getPattern());
			boolean escaped = false;
			for (int c = 0; c < realString.length(); c++) {
				if (realString.charAt(c) == '\\') {
					escaped = !escaped;
				} else {
					if (escaped) {
						char replace = 0;
						switch (realString.charAt(c)) {
						case 'n':
							replace = '\n';
							break;
						case 't':
							replace = '\t';
							break;
						}
						if (replace != 0) {
							realString.setCharAt(c, replace);
							realString.deleteCharAt(c - 1);
						}
					}
					escaped = false;
				}
			}
			setRealPattern(new BetterPattern.SimpleStringSearch(realString, !isMatchCase(), false));
			error = null;
		} else {
			setRealPattern(new BetterPattern.SimpleStringSearch(getPattern(), !isMatchCase(), false));
			error = null;
		}

		setError(error);

		EventQueue.invokeLater(this::update);
		return this;
	}

	default QuickTextPattern update() {
		((SimpleObservable<Void>) getUpdate()).onNext(null);
		return this;
	}

	static String printPosition(int pos, String text) {
		int line = 1;
		int col = 1;
		for (int i = 0; i < pos; i++) {
			switch (text.charAt(i)) {
			case '\n':
				line++;
				col = 1;
				break;
			case '\t':
				col += 4;
				break;
			case '\r':
				break;
			default:
				col++;
			}
		}
		return "Line " + line + " Col " + col;
	}

	default int searchCount(String source) {
		BetterPattern pattern = getRealPattern();
		if (pattern == null) {
			rebuild();
			pattern = getRealPattern();
		}
		int count = 0;
		Matcher matcher = pattern.matcher(source);
		Match match = matcher.find();
		if (match == null) {
			setStatus("No matches not found");
			return 0;
		} else if (match.getEnd() == 0) {
			setStatus("Empty match");
			return 0;
		}
		int firstMatch = match.getStart();
		do {
			count++;
			match = matcher.find();
		} while (match != null);

		if (count == 1) {
			setStatus("1 match found at " + printPosition(firstMatch, source));
		} else
			setStatus(count + " matches found");
		return count;
	}

	/**
	 * @param source The text to search
	 * @param forward Whether to search forward or backward from the cursor
	 * @param anchor The selection anchor position within the text
	 * @param lead The selection lead position within the text
	 */
	default void search(String source, boolean forward, SettableValue<Integer> anchor, SettableValue<Integer> lead) {
		BetterPattern pattern = getRealPattern();
		if (pattern == null) {
			rebuild();
			pattern = getRealPattern();
		}
		if (forward) {
			Match match = find(new DefaultCharSubSequence(source, lead.get(), source.length()));
			if (match != null) {
				int pos = lead.get() + match.getStart();
				try (Causable.CausableInUse cause = Causable.cause()) {
					anchor.set(pos, cause);
					lead.set(lead.get() + match.getEnd(), cause);
					setStatus("Match found at " + printPosition(pos, source));
				}
			} else {
				match = find(source);
				if (match != null) {
					int pos = match.getStart();
					try (Causable.CausableInUse cause = Causable.cause()) {
						anchor.set(pos, cause);
						lead.set(match.getEnd(), cause);
						setStatus("End reached. Starting at beginning. " + printPosition(pos, source));
					}
				} else
					setStatus("No match found in document");
			}
		} else {
			Matcher matcher = pattern.matcher(new DefaultCharSubSequence(source, 0, lead.get()));
			Match match = matcher.find();
			if (match != null) {
				Match nextMatch;
				do {
					nextMatch = matcher.find(match.getEnd());
					if (nextMatch != null)
						match = nextMatch;
				} while (nextMatch != null);
				int pos = match.getStart();
				try (Causable.CausableInUse cause = Causable.cause()) {
					anchor.set(pos, cause);
					lead.set(match.getEnd(), cause);
					setStatus("Match found at " + printPosition(pos, source));
				}
			} else {
				matcher = pattern.matcher(source);
				match = matcher.find();
				if (match != null) {
					Match nextMatch;
					do {
						nextMatch = matcher.find(match.getEnd());
						if (nextMatch != null)
							match = nextMatch;
					} while (nextMatch != null);
					int pos = lead.get() + match.getStart();
					try (Causable.CausableInUse cause = Causable.cause()) {
						anchor.set(pos, cause);
						lead.set(lead.get() + match.getEnd(), cause);
						setStatus("Beginning.  Starting at end. " + printPosition(pos, source));
					}
				} else
					setStatus("No match found in document");
			}
		}
	}
}
