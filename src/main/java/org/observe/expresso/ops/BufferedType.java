package org.observe.expresso.ops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;

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
		for (BufferedName name : theNames)
			length += name.getName().length();
		return length;
	}

	/** @return The length of this type with white space included */
	public int getFullLength() {
		int length = 0;
		if (theNames.size() > 1)
			length += theNames.size() - 1;
		for (BufferedName name : theNames)
			length += name.length();
		return length;
	}

	/**
	 * @param before The number of white space characters to add before this type
	 * @param after The number of white space characters to add after this type
	 * @return The type name that is the same as this, but with the given additional buffer
	 */
	public BufferedType buffer(int before, int after) {
		if (before <= 0 && after <= 0)
			return this;
		if (theNames.size() == 1)
			return new BufferedType(QommonsUtils.unmodifiableCopy(BufferedName.buffer(//
				before + theNames.get(0).getBefore(), theNames.get(0).getName(), after + theNames.get(0).getAfter())));
		BufferedName[] names = theNames.toArray(new BufferedName[theNames.size()]);
		if (before > 0)
			names[0] = BufferedName.buffer(before + names[0].getBefore(), names[0].getName(), names[0].getAfter());
		if (after > 0) {
			BufferedName last = names[names.length - 1];
			names[names.length - 1] = BufferedName.buffer(last.getBefore(), last.getName(), after + last.getAfter());
		}
		return new BufferedType(BetterList.of(names));
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
