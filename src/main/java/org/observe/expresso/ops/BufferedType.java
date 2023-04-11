package org.observe.expresso.ops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.qommons.Named;
import org.qommons.StringUtils;

/** A type in which white space may be embedded */
public class BufferedType {
	private final List<BufferedName> theNames;

	/** @param names The names making up this type */
	public BufferedType(List<BufferedName> names) {
		if (names.isEmpty())
			throw new IllegalArgumentException("Empty type");
		theNames = names;
	}

	/** @return The names making up this type */
	public List<BufferedName> getNames() {
		return theNames;
	}

	/** @return This type name, with no white space */
	public String getName() {
		return StringUtils.print(new StringBuilder(), "", theNames, (str, name) -> str.append(name.getName())).toString();
	}

	/** @return The length of this type without white space */
	public int length() {
		int length = 0;
		if (theNames.size() > 1)
			length += theNames.size() - 1;
		for (Named name : theNames)
			length += name.getName().length();
		return length;
	}

	@Override
	public String toString() {
		return StringUtils.print(new StringBuilder(), "", theNames, StringBuilder::append).toString();
	}

	/**
	 * @param text The text to parse
	 * @return The buffered type parsed from the text
	 */
	public static BufferedType parse(String text) {
		List<BufferedName> names = new ArrayList<>();
		// First figure out the leading white space
		int c;
		for (c = 0; Character.isWhitespace(text.charAt(c)); c++) {
		}
		int initWS = c;
		int nameStart = c, nameEnd = -1;
		for (c++; c < text.length(); c++) {
			if (Character.isWhitespace(text.charAt(c))) {
				if (nameStart >= 0 && nameEnd < 0)
					nameEnd = c;
			} else if (nameEnd >= 0) {
				names.add(BufferedName.buffer(initWS, text.substring(nameStart, nameEnd), c - nameEnd));
				initWS = 0;
				nameStart = c;
				nameEnd = -1;
			}
		}
		if (nameEnd < 0)
			names.add(BufferedName.buffer(initWS, text.substring(nameStart), 0));
		else
			names.add(BufferedName.buffer(initWS, text.substring(nameStart, nameEnd), c - nameEnd));
		return new BufferedType(Collections.unmodifiableList(names));
	}
}
