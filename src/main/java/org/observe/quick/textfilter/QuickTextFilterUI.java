package org.observe.quick.textfilter;

/** UI code for the Quick Text Filtering application */
public class QuickTextFilterUI {
	/**
	 * @param text The text to count the lines of
	 * @return The number of lines in the text
	 */
	public static int getLineCount(String text) {
		if (text == null)
			return 0;
		int lines = 1;
		for (int c = 0; c < text.length(); c++) {
			if (text.charAt(c) == '\n')
				lines++;
		}
		return lines;
	}

	/**
	 * @param text The text content
	 * @return A string containing line numbers for each line of the text
	 */
	public static String genLineNumbers(String text) {
		int lines = getLineCount(text);
		StringBuilder lineNumbers = new StringBuilder();
		int length = (int) Math.log10(lines) + 1;
		int spaces = length - 1;
		int nextSpaceDec = 10;
		for (int i = 0; i < lines; i++) {
			if (i > 0)
				lineNumbers.append('\n');
			int num = i + 1;
			if (num == nextSpaceDec) {
				spaces--;
				nextSpaceDec *= 10;
			}
			for (int s = 0; s < spaces; s++)
				lineNumbers.append(' ');
			lineNumbers.append(num);
		}
		return lineNumbers.toString();
	}
}
