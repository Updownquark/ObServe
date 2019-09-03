package org.observe.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig.XmlEncoding;
import org.observe.util.TypeTokens;
import org.qommons.QommonsTestUtils;
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

	@Test
	public void testComplexEntities() throws IOException, SAXException {
		SimpleObservable<Void> until = new SimpleObservable<>();
		readXml(getClass().getResourceAsStream("TestValues.xml"));
		ObservableValueSet<TestEntity2> testEntities = theConfig.observeEntities(theConfig.createPath("test-entities2/test-entity2"),
			TypeTokens.get().of(TestEntity2.class), until);

		int i = 0;
		for (TestEntity2 entity : testEntities.getValues()) {
			switch (i) {
			case 0:
				Assert.assertEquals("text1", entity.getText());
				Assert.assertThat(entity.getTexts(), QommonsTestUtils.collectionsEqual(Arrays.asList("text2", "text3", "text4"), true));
				entity.getTexts().remove(1);
				Assert.assertEquals(1, entity.getEntityField().getD());
				Assert.assertEquals(2, entity.getListedEntities().getValues().size());
				int j = 0;
				for (TestEntity4 child : entity.getListedEntities().getValues()) {
					switch (j) {
					case 0:
						Assert.assertEquals(5, child.getE());
						break;
					case 1:
						Assert.assertEquals(6, child.getE());
						break;
					}
					j++;
				}

				entity.getEntityField().setD(10);
				Assert.assertEquals("10", theConfig.getContent("test-entities2/test-entity2").getValues().get(i).get("entity-field/d"));

				entity.getListedEntities().getValues().get(1).setE(60);
				entity.getListedEntities().getValues().remove(0);
				Assert.assertEquals(1, theConfig.getContent("test-entities2/test-entity2").getValues().get(i).getContent("listed-entities")
					.getValues().size());
				Assert.assertEquals("60", theConfig.getContent("test-entities2/test-entity2").getValues().get(i)
					.getContent("listed-entities/listed-entity").getValues().get(1).get("e"));
				break;
			case 1:
				Assert.assertEquals("text8", entity.getText());
				Assert.assertThat(entity.getTexts(), QommonsTestUtils.collectionsEqual(Arrays.asList("text9", "text10"), true));
				entity.getTexts().add("text11");
				Assert.assertEquals(1, entity.getEntityField().getD());
				Assert.assertEquals(2, entity.getListedEntities().getValues().size());
				j = 0;
				for (TestEntity4 child : entity.getListedEntities().getValues()) {
					switch (j) {
					case 0:
						Assert.assertEquals(7, child.getE());
						break;
					case 1:
						Assert.assertEquals(8, child.getE());
						break;
					}
					j++;
				}

				theConfig.getContent("test-entities2/test-entity2").getValues().get(i).set("entity-field/d", "20");
				Assert.assertEquals(20, entity.getEntityField().getD());
				entity.getListedEntities().create().with(TestEntity4::getE, 9).create();
				Assert.assertEquals(3, entity.getListedEntities().getValues().size());
				Assert.assertEquals("9", theConfig.getContent("test-entities2/test-entity2").getValues().get(i).getContent("listed-entities/listed-entity")
					.getValues().get(2).get("e"));

				break;
			default:
				Assert.assertTrue("Too many entities", false);
			}
			i++;
			entity.toString(); // Just making sure this doesn't throw an exception
		}
		Assert.assertEquals(2, i);

		TestEntity2 entity2 = testEntities.create()//
			.with(TestEntity2::getText, "new text")//
			// .with("entity-field.d", 100)
			.create().get();
		Assert.assertEquals("new text", entity2.getText());

		i = 0;
		for (TestEntity2 entity : testEntities.getValues()) {
			switch (i) {
			case 0:
				Assert.assertEquals("text1", entity.getText());
				Assert.assertThat(entity.getTexts(), QommonsTestUtils.collectionsEqual(Arrays.asList("text2", "text4"), true));
				Assert.assertEquals(10, entity.getEntityField().getD());
				Assert.assertEquals(1, entity.getListedEntities().getValues().size());
				Assert.assertEquals(60, entity.getListedEntities().getValues().getFirst().getE());
				break;
			case 1:
				Assert.assertEquals("text8", entity.getText());
				Assert.assertThat(entity.getTexts(), QommonsTestUtils.collectionsEqual(Arrays.asList("text9", "text10", "text11"), true));
				Assert.assertEquals(20, entity.getEntityField().getD());
				Assert.assertEquals(3, entity.getListedEntities().getValues().size());
				int j = 0;
				for (TestEntity4 child : entity.getListedEntities().getValues()) {
					switch (j) {
					case 0:
						Assert.assertEquals(7, child.getE());
						break;
					case 1:
						Assert.assertEquals(8, child.getE());
						break;
					case 3:
						Assert.assertEquals(9, child.getE());
						break;
					}
					j++;
				}
				break;
			case 2:
				// TODO
				break;
			}
			i++;
		}
		Assert.assertEquals(3, i);

		writeClearAndParse(//
			() -> Assert.assertEquals(0, testEntities.getValues().size()));

		i = 0;
		for (TestEntity2 entity : testEntities.getValues()) {
			switch (i) {
			case 0:
				// TODO
				break;
			case 1:
				// TODO
				break;
			case 2:
				// TODO
				break;
			default:
				Assert.assertTrue("Too many entities", false);
			}
			i++;
		}
		Assert.assertEquals(3, i);
	}

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

		ObservableValueSet<TestEntity4> getListedEntities();
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
