package org.observe.util;

import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.reflect.TypeToken;

/** Tests {@link TypeTokens} */
public class TypeTokensTest {
	private TypeToken<Map<String, Map<Integer, ? extends List<char[][]>>>[]> theExpected;

	/** Sets up the test */
	@Before
	public void setup() {
		theExpected = new TypeToken<Map<String, Map<Integer, ? extends List<char[][]>>>[]>() {
		};
	}

	/**
	 * Tests the ability to build {@link TypeToken}s from scratch
	 *
	 * @see TypeTokens#keyFor(Class)
	 * @see TypeTokens.TypeKey#parameterized(java.lang.reflect.Type...)
	 * @see TypeTokens.TypeKey#parameterized(TypeToken...)
	 * @see TypeTokens#getExtendsWildcard(TypeToken)
	 * @see TypeTokens#getSuperWildcard(TypeToken)
	 * @see TypeTokens#getArrayType(TypeToken, int)
	 */
	@Test
	public void testTypeBuilding() {
		TypeToken<?> built = TypeTokens.get().getArrayType(//
			TypeTokens.get().keyFor(Map.class)//
			.parameterized(TypeTokens.get().STRING, TypeTokens.get().keyFor(Map.class)//
				.parameterized(TypeTokens.get().INT, TypeTokens.get().getExtendsWildcard(TypeTokens.get().keyFor(List.class)//
					.parameterized(TypeTokens.get().getArrayType(TypeTokens.get().of(char.class), 2))))),
			1);
		Assert.assertEquals(theExpected, built);
	}

	/**
	 * Tests the ability to parse {@link TypeToken}s from strings
	 *
	 * @see TypeTokens#parseType(String)
	 */
	@Test
	public void testTypeParsing() {
		String typeString = "java.util.Map<java.lang.String, java.util.Map<java.lang.Integer, ? extends java.util.List<char [ ][ ]>>>[]";
		TypeToken<?> parsed;
		try {
			parsed = TypeTokens.get()//
				.parseType(typeString);
		} catch (ParseException e) {
			System.err.println(typeString);
			for (int i = 0; i < e.getErrorOffset(); i++)
				System.err.print(' ');
			System.err.print("^");
			throw new AssertionError("Type parsing failed", e);
		}

		Assert.assertEquals(theExpected, parsed);
	}

	/** Tests {@link TypeTokens#getCommonType(TypeToken [])} with simple types */
	@Test
	public void testSimpleCommonType() {
		Assert.assertEquals(TypeTokens.get().of(Number.class),
			TypeTokens.get().getCommonType(TypeTokens.get().INT, TypeTokens.get().DOUBLE, TypeTokens.get().BYTE));
	}

	/** Tests {@link TypeTokens#getCommonType(TypeToken [])} with complex types */
	@Test
	public void testComplexCommonType() {
		Assert.assertEquals(new TypeToken<Collection<? extends Map<? extends Number, ? extends CharSequence>>>() {
		}, //
			TypeTokens.get().getCommonType(//
				new TypeToken<List<HashMap<Integer, String>>>() {
				}, //
				new TypeToken<Set<TreeMap<Double, StringBuilder>>>() {
				}));
	}
}
