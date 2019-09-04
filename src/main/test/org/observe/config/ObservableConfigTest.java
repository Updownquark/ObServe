package org.observe.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionTester;
import org.observe.config.ObservableConfig.XmlEncoding;
import org.observe.util.TypeTokens;
import org.qommons.BreakpointHere;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;
import org.qommons.tree.BetterTreeList;
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

	@Test
	public void superTest() throws IOException, SAXException {
		TestHelper.createTester(ObservableConfigSuperTester.class)//
		.revisitKnownFailures(true).withFailurePersistence(true)//
		.withPlacemarks("modify").withRandomCases(100).withMaxFailures(1)//
		.withDebug(BreakpointHere.isDebugEnabled() != null)//
		.execute().throwErrorIfFailed();
	}

	static class ObservableConfigSuperTester implements TestHelper.Testable {
		private final ObservableConfig theConfig;
		private final ObservableValueSet<TestEntity2> testEntities1;
		private final ObservableValueSet<TestEntity2> testEntities2;

		private final List<TestEntity2> expected;
		private final ObservableCollectionTester<TestEntity2> tester1;
		private final ObservableCollectionTester<TestEntity2> tester2;

		public ObservableConfigSuperTester() {
			theConfig = ObservableConfig.createRoot("test");
			try {
				ObservableConfig.readXml(theConfig, ObservableConfigTest.class.getResourceAsStream("TestValues.xml"),
					new XmlEncoding(":x", ":xx", " ", "blah", "test"));
			} catch (IOException | SAXException e) {
				throw new IllegalStateException(e);
			}
			SimpleObservable<Void> until = new SimpleObservable<>();
			testEntities1 = theConfig.observeEntities(theConfig.createPath("test-entities2/test-entity2"),
				TypeTokens.get().of(TestEntity2.class),
				until);
			tester1 = new ObservableCollectionTester<>("testEntities1", testEntities1.getValues());
			testEntities2 = theConfig.observeEntities(theConfig.createPath("test-entities2/test-entity2"),
				TypeTokens.get().of(TestEntity2.class),
				until);
			tester2 = new ObservableCollectionTester<>("testEntities2", testEntities2.getValues());
			expected = new ArrayList<>();
			for (TestEntity2 entity : testEntities1.getValues())
				expected.add(deepCopy(entity));
			// TODO Do more with the field observable collections, making sure those collections keep up
		}

		@Override
		public void accept(TestHelper helper) {
			int modifications = helper.getInt(100, 10000);
			System.out.print(modifications + " modifications");
			System.out.flush();
			int tickSkip = modifications / 100;
			try (Transaction t = theConfig.lock(true, null)) {
				for (int i = 0; i < modifications; i++) {
					ObservableValueSet<TestEntity2> testEntities = helper.getBoolean() ? testEntities1 : testEntities2;
					if (i != 0 && i % tickSkip == 0) {
						System.out.print(".");
						System.out.flush();
					}

					helper.doAction(1, () -> { // Add element
						int index = helper.getInt(0, testEntities.getValues().size());
						ElementId after = index == 0 ? null : testEntities.getValues().getElement(index - 1).getElementId();
						String randomString = randomString(helper);
						TestEntity2 newEntity = testEntities.create(after, null, true).with(TestEntity2::getText, randomString).create()
							.get();
						Assert.assertEquals(randomString, newEntity.getText());
						expected.add(index, deepCopy(newEntity));
					}).or(1, () -> { // Remove element
						if (testEntities.getValues().isEmpty())
							return;
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						testEntities.getValues().remove(index);
						expected.remove(index);
					}).or(1, () -> {// Modify text
						if (testEntities.getValues().isEmpty())
							return;
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						String randomString = randomString(helper);
						modify.setText(randomString(helper));
						Assert.assertEquals(randomString, modify.getText());
						expected.get(index).setText(randomString);
					}).or(1, () -> {// Modify entity field.d
						if (testEntities.getValues().isEmpty())
							return;
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						int newD = helper.getAnyInt();
						modify.getEntityField().setD(newD);
						Assert.assertEquals(newD, modify.getEntityField().getD());
						expected.get(index).getEntityField().setD(newD);
					}).or(1, () -> {// Add text
						if (testEntities.getValues().isEmpty())
							return;
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						int textIndex = helper.getInt(0, modify.getTexts().size());
						String randomString = randomString(helper);
						modify.getTexts().add(textIndex, randomString);
						Assert.assertEquals(randomString, modify.getTexts().get(textIndex));
						expected.get(index).getTexts().add(textIndex, randomString);
					}).or(1, () -> {// Remove text
						if (testEntities.getValues().isEmpty())
							return;
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getTexts().isEmpty())
							return;
						int textIndex = helper.getInt(0, modify.getTexts().size() - 1);
						modify.getTexts().remove(textIndex);
						expected.get(index).getTexts().remove(textIndex);
					}).or(1, () -> {// Update text
						if (testEntities.getValues().isEmpty())
							return;
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getTexts().isEmpty())
							return;
						int textIndex = helper.getInt(0, modify.getTexts().size() - 1);
						String randomString = randomString(helper);
						modify.getTexts().set(textIndex, randomString);
						Assert.assertEquals(randomString, modify.getTexts().get(textIndex));
						expected.get(index).getTexts().set(textIndex, randomString);
					}).or(1, () -> {// Add listed entity
						if (testEntities.getValues().isEmpty())
							return;
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						int leIndex = helper.getInt(0, modify.getListedEntities().getValues().size());
						ElementId after = leIndex == 0 ? null
							: modify.getListedEntities().getValues().getElement(leIndex - 1).getElementId();
						int newE = helper.getAnyInt();
						TestEntity4 newLE = modify.getListedEntities().create(after, null, true).with(TestEntity4::getE, newE).create()
							.get();
						Assert.assertEquals(newE, newLE.getE());
						((List<TestEntity4>) expected.get(index).getListedEntities().getValues()).add(leIndex, deepCopy(newLE));
					}).or(1, () -> {// Remove listed entity
						if (testEntities.getValues().isEmpty())
							return;
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getListedEntities().getValues().isEmpty())
							return;
						int leIndex = helper.getInt(0, modify.getListedEntities().getValues().size() - 1);
						modify.getListedEntities().getValues().remove(leIndex);
						expected.get(index).getListedEntities().getValues().remove(leIndex);
					}).or(1, () -> {// Modify listed entity.e
						if (testEntities.getValues().isEmpty())
							return;
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getListedEntities().getValues().isEmpty())
							return;
						int leIndex = helper.getInt(0, modify.getListedEntities().getValues().size() - 1);
						int newE = helper.getAnyInt();
						modify.getListedEntities().getValues().get(leIndex).setE(newE);
						Assert.assertEquals(newE, modify.getListedEntities().getValues().get(leIndex).getE());
						expected.get(index).getListedEntities().getValues().get(leIndex).setE(newE);
					}).execute("modify");

					tester1.check(expected);
					tester2.check(expected);
				}
			}
		}
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

	private static TestEntity2 deepCopy(TestEntity2 entity) {
		return new TestEntity2() {
			private String theText;
			private final TestEntity3 theEntityField;
			private final List<String> theTexts;
			private final ObservableCollection<TestEntity4> theListedEntities;

			{
				theText = entity.getText();
				theEntityField = deepCopy(entity.getEntityField());
				theTexts = new ArrayList<>(entity.getTexts());
				theListedEntities = ObservableCollection.create(TypeTokens.get().of(TestEntity4.class), new BetterTreeList<>(false));
				for (TestEntity4 te4 : entity.getListedEntities().getValues())
					theListedEntities.add(deepCopy(te4));
			}

			@Override
			public String getText() {
				return theText;
			}

			@Override
			public void setText(String text) {
				theText = text;
			}

			@Override
			public TestEntity3 getEntityField() {
				return theEntityField;
			}

			@Override
			public List<String> getTexts() {
				return theTexts;
			}

			@Override
			public ObservableValueSet<TestEntity4> getListedEntities() {
				return new ObservableValueSet<ObservableConfigTest.TestEntity4>() {
					@Override
					public ObservableCollection<? extends TestEntity4> getValues() {
						return theListedEntities;
					}

					@Override
					public ConfiguredValueType<TestEntity4> getType() {
						return null;
					}

					@Override
					public ValueCreator<TestEntity4> create(ElementId after, ElementId before, boolean first) {
						return null;
					}

					@Override
					public boolean equals(Object o) {
						return o instanceof ObservableValueSet && theListedEntities.equals(((ObservableValueSet<?>) o).getValues());
					}
				};
			}

			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof TestEntity2))
					return false;
				TestEntity2 other = (TestEntity2) obj;
				return Objects.equals(theText, other.getText())//
					&& Objects.equals(theEntityField, other.getEntityField())//
					&& theTexts.equals(other.getTexts())//
					&& theListedEntities.equals(other.getListedEntities().getValues());
			}

			@Override
			public String toString() {
				return "test-entity2(text=" + theText + ")";
			}
		};
	}

	private static TestEntity3 deepCopy(TestEntity3 entity) {
		return new TestEntity3() {
			private int theD;

			{
				theD = entity.getD();
			}

			@Override
			public int getD() {
				return theD;
			}

			@Override
			public int setD(int d) {
				return theD = d;
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof TestEntity3 && theD == ((TestEntity3) obj).getD();
			}

			@Override
			public String toString() {
				return "test-entity3(d=" + theD + ")";
			}
		};
	}

	private static TestEntity4 deepCopy(TestEntity4 entity) {
		return new TestEntity4() {
			private int theE;

			{
				theE = entity.getE();
			}

			@Override
			public int getE() {
				return theE;
			}

			@Override
			public int setE(int E) {
				return theE = E;
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof TestEntity4 && theE == ((TestEntity4) obj).getE();
			}

			@Override
			public String toString() {
				return "test-entity4(e=" + theE + ")";
			}
		};
	}

	private static String randomString(TestHelper helper) {
		int len = helper.getInt(0, 100);
		char[] ch = new char[len];
		for (int i = 0; i < ch.length; i++) {
			ch[i] = (char) helper.getInt(' ', '~');
		}
		return new String(ch);
	}
}
