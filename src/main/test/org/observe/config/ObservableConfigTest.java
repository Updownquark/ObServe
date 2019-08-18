package org.observe.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig.XmlEncoding;
import org.observe.util.TypeTokens;
import org.xml.sax.SAXException;

public class ObservableConfigTest {
	private ObservableConfig theConfig;
	private XmlEncoding theEncoding;

	@Before
	public void initConfig() {
		theConfig = ObservableConfig.createRoot("root");
		theEncoding = new XmlEncoding(":x", ":xx", " ", "blah", "test");
	}

	@Test
	public void testXml1() throws IOException, SAXException {
		readXml(getClass().getResourceAsStream("TestXml1.xml"));
		checkXml1();
	}

	private void readXml(InputStream in) throws IOException, SAXException {
		ObservableConfig.readXml(theConfig, in, theEncoding);
	}

	private void checkXml1() {
		Assert.assertNull(theConfig.getValue());
		Assert.assertEquals("config", theConfig.getName());
		Assert.assertEquals("root", theConfig.get("rootAttr"));
		Assert.assertEquals("This is some\ntext", theConfig.get("element2"));
		Assert.assertEquals("\n", theConfig.get("elemenblaht3"));
		Assert.assertEquals("", theConfig.get("element4"));
	}

	@Test
	public void testXmlPersistence() throws IOException, SAXException {
		readXml(getClass().getResourceAsStream("TestXml1.xml"));
		writeClearAndParse(null);
		testXml1();
	}

	private void writeClearAndParse(Runnable beforeParse) throws IOException, SAXException {
		StringWriter writer = new StringWriter();
		ObservableConfig.writeXml(theConfig, writer, theEncoding, "\t");
		theConfig.getAllContent().getValues().clear();
		if (beforeParse != null)
			beforeParse.run();
		readXml(//
			new ByteArrayInputStream(writer.toString().getBytes("UTF-8")));
	}

	@Test
	public void testValues() throws IOException, SAXException {
		SimpleObservable<Void> until = new SimpleObservable<>();
		readXml(getClass().getResourceAsStream("TestValues.xml"));

		ObservableCollection<Integer> testValues = theConfig.observeValues("test-values/test-value", TypeTokens.get().INT,
			ObservableConfigFormat.INT, until);
		int i = 0;
		for (Integer value : testValues) {
			Assert.assertEquals(i, value.intValue());

			switch (i) {
			case 0:
				testValues.set(i, 100);
				break;
			case 5:
				testValues.remove(i);
				break;
			case 9:
				testValues.remove(i - 1);
				break;
			}
			i++;
		}
		Assert.assertEquals(10, i);

		Assert.assertEquals(8, testValues.size());
		Assert.assertEquals(100, testValues.get(0).intValue());
		theConfig.getChild("test-values").getAllContent().getValues().get(3).setValue("30");
		Assert.assertEquals(30, testValues.get(3).intValue());

		testValues.add(90);
		Assert.assertEquals(9, testValues.size());

		writeClearAndParse(//
			() -> Assert.assertEquals(0, testValues.size()));
		i = 0;
		for (Integer value : testValues) {
			switch (i) {
			case 0:
				Assert.assertEquals(100, value.intValue());
				break;
			case 1:
			case 2:
				Assert.assertEquals(i, value.intValue());
				break;
			case 3:
				Assert.assertEquals(30, value.intValue());
				break;
			case 4:
				Assert.assertEquals(i, value.intValue());
				break;
			case 5:
			case 6:
			case 7:
				Assert.assertEquals(i + 1, value.intValue());
				break;
			case 8:
				Assert.assertEquals(90, value.intValue());
				break;
			}
			i++;
		}
		Assert.assertEquals(9, testValues.size());
	}

	@Test
	public void testReadOnlySimpleEntities() throws IOException, SAXException {
		testSimpleEntities(false);
	}

	@Test
	public void testModifySimpleEntities() throws IOException, SAXException {
		testSimpleEntities(true);
	}

	private void testSimpleEntities(boolean withModification) throws IOException, SAXException {
		SimpleObservable<Void> until = new SimpleObservable<>();
		readXml(getClass().getResourceAsStream("TestValues.xml"));
		ObservableValueSet<TestEntity> testEntities = theConfig.observeEntities(theConfig.createPath("test-entities/test-entity"),
			TypeTokens.get().of(TestEntity.class), until);

		int i = 0;
		for (TestEntity entity : testEntities.getValues()) {
			switch (i) {
			case 0:
				Assert.assertEquals(5, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ofMinutes(10), entity.getC());

				if (withModification) {
					entity.setA(25);
					Assert.assertEquals(25, entity.getA());
				}
				break;
			case 1:
				Assert.assertEquals(10, entity.getA());
				Assert.assertEquals(false, entity.getB());
				Assert.assertEquals(Duration.ofSeconds(10), entity.getC());

				if (withModification) {
					entity.setB(true);
					Assert.assertEquals(true, entity.getB());
				}
				break;
			case 2:
				Assert.assertEquals(4, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ofHours(1), entity.getC());

				if (withModification) {
					entity.setC(Duration.ZERO);
					Assert.assertEquals(Duration.ZERO, entity.getC());
				}
				break;
			case 3:
				Assert.assertEquals(42, entity.getA());
				Assert.assertEquals(false, entity.getB());
				Assert.assertEquals(Duration.ofDays(10 * 7), entity.getC());

				if (withModification)
					testEntities.getValues().remove(i);
				break;
			default:
				Assert.assertTrue("Too many entities", false);
			}
			i++;
			Assert.assertEquals(print(entity), entity.print());
			entity.toString(); // Just making sure this doesn't throw an exception
		}
		Assert.assertEquals(4, i);
		if (!withModification)
			return;

		i = 0;
		for (TestEntity entity : testEntities.getValues()) {
			switch (i) {
			case 0:
				Assert.assertEquals(25, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ofMinutes(10), entity.getC());
				break;
			case 1:
				Assert.assertEquals(10, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ofSeconds(10), entity.getC());
				break;
			case 2:
				Assert.assertEquals(4, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ZERO, entity.getC());
				break;
			default:
				Assert.assertTrue("Too many entities", false);
			}
			i++;
		}
		Assert.assertEquals(3, i);

		TestEntity entity2 = testEntities.create()//
			// .with(TestEntity::getA, 50)//Leave A default
			.with(TestEntity::getB, false)//
			.with(TestEntity::getC, Duration.ofDays(1))//
			.create().get();
		Assert.assertEquals(0, entity2.getA()); // Zero is the default for an integer--check for it
		Assert.assertEquals(false, entity2.getB());
		Assert.assertEquals(Duration.ofDays(1), entity2.getC());
		entity2.setA(50);

		writeClearAndParse(//
			() -> Assert.assertEquals(0, testEntities.getValues().size()));
		i = 0;
		for (TestEntity entity : testEntities.getValues()) {
			switch (i) {
			case 0:
				Assert.assertEquals(25, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ofMinutes(10), entity.getC());
				break;
			case 1:
				Assert.assertEquals(10, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ofSeconds(10), entity.getC());
				break;
			case 2:
				Assert.assertEquals(4, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ZERO, entity.getC());
				break;
			case 3:
				Assert.assertEquals(50, entity.getA());
				Assert.assertEquals(false, entity.getB());
				Assert.assertEquals(Duration.ofDays(1), entity.getC());
				break;
			default:
				Assert.assertTrue("Too many entities", false);
			}
			i++;
		}
		Assert.assertEquals(4, i);

		theConfig.set("test-entities/test-entity{a=10,b=true}/a", "20");
		Assert.assertEquals(20, testEntities.getValues().get(1).getA());
	}

	// @Test
	public void testComplexEntities() {}

	public interface TestEntity {
		int getA();

		TestEntity setA(int a);

		boolean getB();

		TestEntity setB(boolean b);

		Duration getC();

		void setC(Duration c);

		default String print() {
			return ObservableConfigTest.print(this);
		}
	}

	public interface TestEntity2 {
		String getText();

		void setText(String text);

		TestEntity3 getEntityField();

		List<String> getTexts();

		List<TestEntity4> getListedEntities();
	}

	public interface TestEntity3 {
		int getD();

		int setD(int d);
	}

	public interface TestEntity4 {
		int getE();

		int setE(int e);
	}

	private static String print(TestEntity entity) {
		return "a=" + entity.getA() + ", b=" + entity.getB() + ", c=" + entity.getC();
	}
}
