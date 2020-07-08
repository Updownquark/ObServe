package org.observe.util;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.observe.util.ObjectMethodOverride.ObjectMethod;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

/** Tests {@link EntityReflector} */
public class EntityReflectorTest {
	/** ID-tag for getter */
	public static @interface Id {}

	@SuppressWarnings("javadoc")
	public static interface A {
		int getId();

		void setId(int id);

		int getA();

		void setA(int a);

		default String print() {
			return "id=" + getId() + ", a=" + getA();
		}
	}

	@SuppressWarnings("javadoc")
	public static interface B {
		int getId();

		void setId(int id);

		@Id
		int getB();

		void setB(int b);

		default String print() {
			return "id: " + getId() + ", a=" + getB();
		}
	}

	@SuppressWarnings("javadoc")
	public static interface C extends A, B {
		@Override
		default int getB() {
			return 0;
		}

		@Override
		default void setB(int b) {
			throw new UnsupportedOperationException();
		}

		int getC();

		void setC(int c);

		@Override
		default String print() {
			return A.super.print() + ", c=" + getC();
		}
	}

	/** A quick, simple test against reflectively-synthesized entities, with complex inheritance */
	@Test
	public void testInheritance() {
		Map<TypeToken<?>, EntityReflector<?>> supers = new HashMap<>();
		EntityReflector<C> cRef = EntityReflector.build(TypeTokens.get().of(C.class), true).withSupers(supers)
			.<String> withCustomMethod(C::toString, (c, args) -> c.print()).build();
		QuickMap<String, Object> c1Fields = cRef.getFields().keySet().createMap();
		C c1 = cRef.newInstance(new EntityReflector.EntityInstanceBacking() {
			@Override
			public Object get(int fieldIndex) {
				return c1Fields.get(fieldIndex);
			}

			@Override
			public void set(int fieldIndex, Object newValue) {
				c1Fields.put(fieldIndex, newValue);
			}

		});
		c1.setId(10);
		c1.setA(20);
		try {
			c1.setB(5);
			Assert.assertFalse("Should have thrown an exception", true);
		} catch (UnsupportedOperationException e) {}
		c1.setC(30);
		Assert.assertEquals(0, c1.getB());
		Assert.assertEquals("id=10, a=20, c=30", c1.print());
		Assert.assertEquals(c1.print(), //
			c1.toString());
		// TODO
	}

	@SuppressWarnings("javadoc")
	public static interface D {
		@ObjectMethodOverride(ObjectMethod.toString)
		String getString();

		void setString(String s);
	}

	/**
	 * Tests the ability to override Object methods such as {@link Object#equals(Object) equals()} and {@link Object#toString() toString()}
	 */
	@Test
	public void testObjectOverride() {
		EntityReflector<D> dRef = EntityReflector.build(TypeTokens.get().of(D.class), true).build();

		QuickMap<String, Object> d1Fields = dRef.getFields().keySet().createMap();
		D d1 = dRef.newInstance(new EntityReflector.EntityInstanceBacking() {
			@Override
			public Object get(int fieldIndex) {
				return d1Fields.get(fieldIndex);
			}

			@Override
			public void set(int fieldIndex, Object newValue) {
				d1Fields.put(fieldIndex, newValue);
			}
		});
		d1.setString("blah");
		Assert.assertEquals("blah", d1.toString());
	}
}
