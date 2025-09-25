package org.observe.quick.textfilter;

import java.awt.Color;
import java.beans.Transient;
import java.util.ArrayList;
import java.util.List;

import org.observe.SettableValue;
import org.qommons.io.BetterPattern;
import org.qommons.io.BetterPattern.Match;
import org.qommons.io.BetterPattern.Matcher;

/**
 * A regex-enabled filter structure that may be defined in the Quick Text Filtering UI. This class contains one filter string, which may or
 * may not be a regular expression, several boolean flags configuring how the filter will be evaluated, and a replacement pattern, which may
 * be used against matches of the filter string to create a modified text document.
 */
public interface QuickTextFilter extends QuickTextPattern {
	boolean isEnabled();
	QuickTextFilter setEnabled(boolean enabled);

	boolean isIncludeWholeLine();
	QuickTextFilter setIncludeWholeLine(boolean includeLine);

	boolean isPrintSourcePosition();

	QuickTextFilter setPrintSourcePosition(boolean printSourcePos);

	String getReplacement();
	QuickTextFilter setReplacement(String replacement);

	@Transient
	boolean isDirty();
	QuickTextFilter setDirty(boolean dirty);

	Color getColor();
	QuickTextFilter setColor(Color color);

	@Transient
	BetterPattern.BetterPatternReplacement getRealReplacement();
	QuickTextFilter setRealReplacement(BetterPattern.BetterPatternReplacement replacement);

	@Override
	default QuickTextFilter init() {
		QuickTextPattern.super.init();
		setEnabled(true);
		setColor(new Color(System.identityHashCode(this)));
		return this;
	}

	@Override
	default QuickTextFilter rebuild() {
		if (getColor() == null)
			setColor(new Color(System.identityHashCode(this)));
		if (isEnabled())
			setDirty(true);
		QuickTextPattern.super.rebuild();
		if (getError() != null || getReplacement() == null || getReplacement().isEmpty())
			setRealReplacement(null);
		else {
			try {
				setRealReplacement(BetterPattern.BetterPatternReplacement.parsePatternReplacement(getReplacement(), getRealPattern()));
			} catch (RuntimeException e) {
				e.printStackTrace();
				setError("Invalid pattern replacement: " + e.getMessage());
			}
		}
		return this;
	}

	default void clean() {
		setDirty(false);
		update();
	}

	default void appendReplacement(StringBuilder str, BetterPattern.Match match) {
		if (getRealReplacement() != null)
			getRealReplacement().appendReplacement(str, match);
	}

	/**
	 * @param source The source text
	 * @param filters The list of filters to apply
	 * @param multipleRounds If true, this method will produce a modified text, then re-apply the filter list over and over. This behavior
	 *        is useful for e.g. eliminating matching line pairs that may be interspersed throughout the document
	 * @param preserveUnmatched Whether to keep source text that was not matched by any filters in the first round
	 * @param result A text variable in which to put a user-readable status for the result of the replacement
	 * @return The filtered text
	 */
	public static String applyPatterns(String source, List<QuickTextFilter> filters, boolean multipleRounds, boolean preserveUnmatched,
		SettableValue<String> result) {
		if (source == null || source.isEmpty())
			return source;
		StringBuilder str = new StringBuilder(source);
		List<MatchWithOffset> matches = new ArrayList<>(filters.size());
		for (int f = 0; f < filters.size(); f++)
			matches.add(new MatchWithOffset(f));
		int anyApplied = 0;
		boolean applied;
		int round = 0;
		int index;
		int keepGoing = 100;
		while (round == 0 || (multipleRounds && keepGoing > 0)) {
			String roundSource = str.toString();
			str.setLength(0);
			applied = false;
			index = 0;
			for (int f = 0; f < filters.size(); f++) {
				MatchWithOffset match = matches.get(f);
				if (filters.get(f).isEnabled())
					match.reset(filters.get(f).getRealPattern().matcher(roundSource));
			}
			for (MatchWithOffset match = firstMatch(matches); match != null; match = firstMatch(matches)) {
				QuickTextFilter filter = filters.get(match.filterIndex);
				match.applied++;
				applied = true;
				anyApplied++;
				if (!multipleRounds && filter.isPrintSourcePosition())
					str.append(QuickTextPattern.printPosition(match.getStart(), source)).append(": ");
				if (preserveUnmatched || round > 0) {
					str.append(roundSource, index, match.getStart());
				} else if (filter.isIncludeWholeLine()) {
					int lineStart = match.getStart();
					while (lineStart > index && roundSource.charAt(lineStart - 1) != '\n')
						lineStart--;
					str.append(roundSource, lineStart, match.getStart());
				}
				index = match.getEnd();
				filter.appendReplacement(str, match.match);
				if (!preserveUnmatched && round == 0 && filter.isIncludeWholeLine()) {
					int lineEnd = index;
					while (lineEnd < roundSource.length() && roundSource.charAt(lineEnd) != '\n')
						lineEnd++;
					if (lineEnd < roundSource.length())
						lineEnd++; // Include the newline
					str.append(roundSource, index, lineEnd);
					index = lineEnd;
				}
				do {
					match.nextMatch();
				} while (match.match != null && match.getStart() < index);
				for (MatchWithOffset other : matches) {
					if (other != match) {
						while (other.match != null && other.getStart() < index) {
							other.nextMatch();
						}
					}
				}
			}
			if (preserveUnmatched || round > 0)
				str.append(roundSource, index, roundSource.length());
			if (!applied || sequencesEqual(str, roundSource))
				break;
			else if (str.length() >= roundSource.length())
				keepGoing--; // Don't keep replacing forever if there's no terminal condition
			round++;
		}
		if (anyApplied == 0)
			result.set("No matches found");
		else {
			StringBuilder resultStr = new StringBuilder("In ").append(round).append(" round");
			if (round > 1)
				resultStr.append('s');
			resultStr.append(" of searching, replaced ").append(anyApplied).append(" match");
			if (anyApplied > 1)
				resultStr.append("es");
			for (int f = 0; f < filters.size(); f++) {
				if (!filters.get(f).isEnabled())
					filters.get(f).setStatus(null).update();
				else {
					int count = matches.get(f).applied;
					filters.get(f).setStatus(count == 0 ? "Not found" : "Found " + count + " time" + (count == 1 ? "" : "s")).update();
				}
			}
		}
		return str.toString();
	}

	static class MatchWithOffset implements Comparable<MatchWithOffset> {
		final int filterIndex;
		Matcher matcher;
		Match match;
		int applied;

		MatchWithOffset(int filterIndex) {
			this.filterIndex = filterIndex;
		}

		void reset(Matcher matcher) {
			this.matcher = matcher;
			match = matcher.find();
		}

		void nextMatch() {
			match = matcher.find();
		}

		int getStart() {
			return match.getStart();
		}

		int getEnd() {
			return match.getEnd();
		}

		@Override
		public int compareTo(MatchWithOffset o) {
			int comp = Integer.compare(match.getStart(), o.match.getStart());
			if (comp == 0)
				comp = Integer.compare(filterIndex, o.filterIndex);
			return comp;
		}
	}

	static MatchWithOffset firstMatch(List<MatchWithOffset> matches) {
		return matches.stream()//
			.filter(mwo -> mwo.match != null)//
			.min(MatchWithOffset::compareTo)//
			.orElse(null);
	}

	static boolean sequencesEqual(CharSequence seq1, CharSequence seq2) {
		if (seq1.length() != seq2.length())
			return false;
		for (int i = 0; i < seq1.length(); i++) {
			if (seq1.charAt(i) != seq2.charAt(i))
				return false;
		}
		return true;
	}
}
