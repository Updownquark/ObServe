package org.observe.config;

import java.io.ByteArrayInputStream;
import java.io.File;
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
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionTester;
import org.observe.config.ObservableConfig.XmlEncoding;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.BreakpointHere;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.io.TextParseException;
import org.qommons.testing.QommonsTestUtils;
import org.qommons.testing.TestHelper;
import org.qommons.tree.BetterTreeList;
import org.xml.sax.SAXException;

import com.google.common.reflect.TypeToken;

/** Tests against {@link ObservableConfig} */
public class ObservableConfigTest {
	private ObservableConfig theConfig;
	private XmlEncoding theEncoding;

	/** Initializes for testing */
	@Before
	public void initConfig() {
		theConfig = ObservableConfig.createRoot("root", ThreadConstraint.ANY);
		theEncoding = XmlEncoding.DEFAULT;
	}

	/**
	 * Simple persistence test
	 *
	 * @throws IOException Should not happen
	 * @throws TextParseException Should not happen
	 */
	@Test
	public void testXml1() throws IOException, TextParseException {
		readXml(getClass().getResourceAsStream("TestXml1.xml"));
		checkXml1();
	}

	private void readXml(InputStream in) throws IOException, TextParseException {
		ObservableConfig.readXml(theConfig, in, theEncoding);
	}

	private void checkXml1() {
		Assert.assertNull(theConfig.getValue());
		Assert.assertEquals("config", theConfig.getName());
		Assert.assertEquals("root", theConfig.get("rootAttr"));
		Assert.assertEquals("This is some\ntext", theConfig.get("element2"));
		Assert.assertEquals("\n", theConfig.get("element3"));
		Assert.assertEquals("", theConfig.get("element4"));
	}

	/**
	 * Slightly larger persistence test
	 *
	 * @throws IOException Should not happen
	 * @throws TextParseException Should not happen
	 */
	@Test
	public void testXmlPersistence() throws IOException, TextParseException {
		readXml(getClass().getResourceAsStream("TestXml1.xml"));
		writeClearAndParse(null);
		testXml1();
	}

	private void writeClearAndParse(Runnable beforeParse) throws IOException, TextParseException {
		StringWriter writer = new StringWriter();
		ObservableConfig.writeXml(theConfig, writer, theEncoding, "\t");
		theConfig.getAllContent().getValues().clear();
		if (beforeParse != null)
			beforeParse.run();
		readXml(//
			new ByteArrayInputStream(writer.toString().getBytes("UTF-8")));
	}

	/**
	 * Tests {@link ObservableConfig#asValue(TypeToken)}
	 *
	 * @throws IOException Should not happen
	 * @throws TextParseException Should not happen
	 */
	@Test
	public void testValues() throws IOException, TextParseException {
		SimpleObservable<Void> until = new SimpleObservable<>();
		readXml(getClass().getResourceAsStream("TestValues.xml"));

		ObservableCollection<Integer> testValues = theConfig.asValue(Integer.class).at("test-values/test-value").until(until)
			.buildCollection(null);
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

	/**
	 * Simple, read-only test against config-backed entities
	 *
	 * @throws IOException Should not happen
	 * @throws TextParseException Should not happen
	 */
	@Test
	public void testReadOnlySimpleEntities() throws IOException, TextParseException {
		testSimpleEntities(false);
	}

	/**
	 * Simple, test against config-backed entities, with modification
	 *
	 * @throws IOException Should not happen
	 * @throws TextParseException Should not happen
	 */
	@Test
	public void testModifySimpleEntities() throws IOException, TextParseException {
		testSimpleEntities(true);
	}

	private void testSimpleEntities(boolean withModification) throws IOException, TextParseException {
		SimpleObservable<Void> until = new SimpleObservable<>();
		readXml(getClass().getResourceAsStream("TestValues.xml"));
		SyncValueSet<TestEntity> testEntities = theConfig.asValue(TestEntity.class).at("test-entities/test-entity").until(until)
			.buildEntitySet(null);

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

	/**
	 * Test against complex config-backed entities
	 *
	 * @throws IOException Should not happen
	 * @throws TextParseException Should not happen
	 */
	@Test
	public void testComplexEntities() throws IOException, TextParseException {
		SimpleObservable<Void> until = new SimpleObservable<>();
		readXml(getClass().getResourceAsStream("TestValues.xml"));
		SyncValueSet<TestEntity2> testEntities = theConfig.asValue(TestEntity2.class).at("test-entities2/test-entity2").until(until)
			.buildEntitySet(null);

		int i = 0;
		for (TestEntity2 entity : testEntities.getValues()) {
			switch (i) {
			case 0:
				Assert.assertEquals("text1", entity.getText());
				QommonsTestUtils.assertThat(entity.getTexts(),
					QommonsTestUtils.collectionsEqual(Arrays.asList("text2", "text3", "text4"), true));
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
					.getContent("listed-entities/test-entity4").getValues().get(0).get("e"));
				break;
			case 1:
				Assert.assertEquals("text8", entity.getText());
				QommonsTestUtils.assertThat(entity.getTexts(), QommonsTestUtils.collectionsEqual(Arrays.asList("text9", "text10"), true));
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
				Assert.assertEquals("9", theConfig.getContent("test-entities2/test-entity2").getValues().get(i)
					.getContent("listed-entities/test-entity4").getValues().get(2).get("e"));

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
				QommonsTestUtils.assertThat(entity.getTexts(), QommonsTestUtils.collectionsEqual(Arrays.asList("text2", "text4"), true));
				Assert.assertEquals(10, entity.getEntityField().getD());
				Assert.assertEquals(1, entity.getListedEntities().getValues().size());
				Assert.assertEquals(60, entity.getListedEntities().getValues().getFirst().getE());
				break;
			case 1:
				Assert.assertEquals("text8", entity.getText());
				QommonsTestUtils.assertThat(entity.getTexts(),
					QommonsTestUtils.collectionsEqual(Arrays.asList("text9", "text10", "text11"), true));
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
			if (entity == null)
				throw new IllegalStateException(); // Just suppressing a warning here
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

	/**
	 * Barrage test against complex config-backed entities
	 *
	 * @throws IOException Should not happen
	 * @throws SAXException Should not happen
	 */
	@Test
	public void superTest() throws IOException, SAXException {
		TestHelper.createTester(ObservableConfigSuperTester.class)//
		.revisitKnownFailures(true).withFailurePersistence(true)//
		.withPlacemarks("modify").withRandomCases(100).withMaxFailures(1).withMaxCaseDuration(Duration.ofSeconds(10))//
		.withPersistenceDir(new File("src/test/java/org/observe/config"), false)// Where to write the failure file
		.withDebug(BreakpointHere.isDebugEnabled() != null)//
		.execute().throwErrorIfFailed();
	}

	static class ObservableConfigSuperTester implements TestHelper.Testable {
		private final XmlEncoding theEncoding;
		private final ObservableConfig theConfig;
		private final SyncValueSet<TestEntity2> testEntities1;
		private final SyncValueSet<TestEntity2> testEntities2;

		private final List<TestEntity2> expected;
		private final ObservableCollectionTester<TestEntity2> tester1;
		private final ObservableCollectionTester<TestEntity2> tester2;
		private final TestEntity2SetListener theCompleteListeners;

		public ObservableConfigSuperTester() {
			theEncoding = XmlEncoding.DEFAULT;
			// Use unsafe locking for performance--we're not doing anything thread-unsafe here
			theConfig = ObservableConfig.createRoot("test", null, __ -> new FastFailLockingStrategy(ThreadConstraint.ANY));
			try {
				ObservableConfig.readXml(theConfig, ObservableConfigTest.class.getResourceAsStream("TestValues.xml"), theEncoding);
			} catch (IOException | TextParseException e) {
				throw new IllegalStateException(e);
			}
			SimpleObservable<Void> until = new SimpleObservable<>();
			ObservableConfigFormatSet formats = new ObservableConfigFormatSet();
			formats.withFormat(TypeTokens.get().of(TestEntity4.class),
				ObservableConfigFormat.buildEntities(TypeTokens.get().of(TestEntity4.class), formats)//
				.withSubType(TypeTokens.get().of(TestEntity5.class), //
					sb -> sb.build("*", b -> b.withAttribute("type", "5").build()))//
				.build());
			// Test with 2 independent copies of the entity set. Changes to one should show up in the other via config events.
			testEntities1 = theConfig.asValue(TestEntity2.class).withFormatSet(formats).at("test-entities2/test-entity2").until(until)
				.buildEntitySet(null);
			tester1 = new ObservableCollectionTester<>("testEntities1", testEntities1.getValues());
			testEntities2 = theConfig.asValue(TestEntity2.class).withFormatSet(formats).at("test-entities2/test-entity2").until(until)
				.buildEntitySet(null);
			tester2 = new ObservableCollectionTester<>("testEntities2", testEntities2.getValues());
			// This structure is not updated by the test, but listens to all the fields of the entities to update itself.
			// Only do this on one of the test entity sets, so we can verify that entity sets don't *need* listeners installed to work.
			theCompleteListeners = new TestEntity2SetListener(testEntities1.getValues());
			expected = new ArrayList<>();
			for (int i = 0; i < testEntities1.getValues().size(); i++)
				expected.add(new TestEntity2Tester(testEntities1.getValues().get(i), testEntities2.getValues().get(i)));

			theCompleteListeners.check();
		}

		@Override
		public void accept(TestHelper helper) {
			int modifications = helper.getInt(10, 1000);
			System.out.flush();
			double ticksPerMod = 100.0 / modifications;
			int ticks = 0;
			for (int i = 0; i < modifications; i++) {
				boolean modify1 = helper.getBoolean();
				SyncValueSet<TestEntity2> testEntities = modify1 ? testEntities1 : testEntities2;
				if (helper.isReproducing()) {
					System.out.print("\tModify set " + (modify1 ? "1" : "2") + ": ");
				} else {
					int newTicks = (int) (i * ticksPerMod);
					while (ticks < newTicks) {
						System.out.print(".");
						System.out.flush();
						ticks++;
					}
				}

				try (Transaction t = theConfig.lock(true, null)) {
					helper.doAction(1, () -> { // Add element
						int index = helper.getInt(0, testEntities.getValues().size());
						ElementId after = index == 0 ? null : testEntities.getValues().getElement(index - 1).getElementId();
						String randomString = randomString(helper);
						if (helper.isReproducing())
							System.out.println("[" + index + "] Add entity text=\"" + randomString + "\"");
						TestEntity2 newEntity = testEntities.create().after(after).towardBeginning(true)
							.with(TestEntity2::getText, randomString)//
							.create()//
							.get();
						Assert.assertEquals(randomString, newEntity.getText());
						TestEntity2 entity1 = testEntities == testEntities1 ? newEntity : testEntities2.getValues().get(index);
						TestEntity2 entity2 = testEntities == testEntities1 ? testEntities1.getValues().get(index) : newEntity;
						expected.add(index, new TestEntity2Tester(entity1, entity2));
					}).or(1, () -> { // Remove element
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						if (helper.isReproducing())
							System.out.println("Remove entity [" + index + "]");
						testEntities.getValues().remove(index);
						((TestEntity2Tester) expected.remove(index)).dispose();
					}).or(1, () -> {// Modify text
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						String randomString = randomString(helper);
						if (helper.isReproducing())
							System.out.println("[" + index + "] Modify text from \"" + modify.getText() + "\" to \"" + randomString + "\"");
						modify.setText(randomString);
						Assert.assertEquals(randomString, modify.getText());
						expected.get(index).setText(randomString);
					}).or(1, () -> {// Modify entity field.d
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						int newD = helper.getAnyInt();
						if (helper.isReproducing())
							System.out.println("[" + index + "] Modify d from " + modify.getEntityField().getD() + " to " + newD);
						modify.getEntityField().setD(newD);
						Assert.assertEquals(newD, modify.getEntityField().getD());
						expected.get(index).getEntityField().setD(newD);
					}).or(1, () -> {// Add text
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						int textIndex = helper.getInt(0, modify.getTexts().size());
						String randomString = randomString(helper);
						if (helper.isReproducing())
							System.out.println("[" + index + "] Add text [" + textIndex + "] \"" + randomString + "\"");
						modify.getTexts().add(textIndex, randomString);
						Assert.assertEquals(randomString, modify.getTexts().get(textIndex));
						expected.get(index).getTexts().add(textIndex, randomString);
					}).or(1, () -> {// Remove text
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getTexts().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int textIndex = helper.getInt(0, modify.getTexts().size() - 1);
						if (helper.isReproducing())
							System.out
							.println("[" + index + "] Remove text [" + textIndex + "] \"" + modify.getTexts().get(textIndex) + "\"");
						modify.getTexts().remove(textIndex);
						expected.get(index).getTexts().remove(textIndex);
					}).or(1, () -> {// Update text
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getTexts().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int textIndex = helper.getInt(0, modify.getTexts().size() - 1);
						String randomString = randomString(helper);
						if (helper.isReproducing())
							System.out.println("[" + index + "] Update text [" + textIndex + "] from \"" + modify.getTexts().get(textIndex)
								+ "\" to \"" + randomString + "\"");
						modify.getTexts().set(textIndex, randomString);
						Assert.assertEquals(randomString, modify.getTexts().get(textIndex));
						expected.get(index).getTexts().set(textIndex, randomString);
					}).or(1, () -> {// Add listed entity
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						int leIndex = helper.getInt(0, modify.getListedEntities().getValues().size());
						ElementId after = leIndex == 0 ? null
							: modify.getListedEntities().getValues().getElement(leIndex - 1).getElementId();
						int newE = helper.getAnyInt();
						double newF = 0;
						boolean te5 = helper.getBoolean();
						SyncValueCreator<TestEntity4, ? extends TestEntity4> creator;
						if (te5) {
							newF = helper.getAnyDouble();
							if (helper.isReproducing())
								System.out.println("[" + index + "] Add listed5 [" + leIndex + "] e=" + newE + ", f=" + newF);
							creator = modify.getListedEntities().create(TypeTokens.get().of(TestEntity5.class)).with(TestEntity5::getF,
								newF);
						} else {
							if (helper.isReproducing())
								System.out.println("[" + index + "] Add listed4 [" + leIndex + "] e=" + newE);
							creator = modify.getListedEntities().create();
						}
						TestEntity4 newLE = creator.after(after).towardBeginning(true).with(TestEntity4::getE, newE).create().get();
						if (te5) {
							Assert.assertTrue(newLE instanceof TestEntity5);
							if (!checkEquals(newF, ((TestEntity5) newLE).getF()))
								Assert.assertEquals(newF, ((TestEntity5) newLE).getF(), 1E-12);
						}
						Assert.assertEquals(newE, newLE.getE());
						((List<TestEntity4>) expected.get(index).getListedEntities().getValues()).add(leIndex, deepCopy(newLE));
					}).or(1, () -> {// Remove listed entity
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getListedEntities().getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int leIndex = helper.getInt(0, modify.getListedEntities().getValues().size() - 1);
						if (helper.isReproducing())
							System.out.println("[" + index + "] Remove listed [" + leIndex + "]");
						modify.getListedEntities().getValues().remove(leIndex);
						expected.get(index).getListedEntities().getValues().remove(leIndex);
					}).or(1, () -> {// Modify listed entity.e
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getListedEntities().getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int leIndex = helper.getInt(0, modify.getListedEntities().getValues().size() - 1);
						int newE = helper.getAnyInt();
						if (helper.isReproducing())
							System.out.println("[" + index + "] Modify listed[" + leIndex + "].e from "
								+ modify.getListedEntities().getValues().get(leIndex).getE() + " to " + newE);
						TestEntity4 listedEntity = modify.getListedEntities().getValues().get(leIndex);
						listedEntity.setE(newE);
						Assert.assertEquals(newE, modify.getListedEntities().getValues().get(leIndex).getE());
						expected.get(index).getListedEntities().getValues().get(leIndex).setE(newE);
					}).or(1, () -> {// Modify listed entity.x
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getListedEntities().getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int leIndex = helper.getInt(0, modify.getListedEntities().getValues().size() - 1);
						double newX = helper.getAnyDouble();
						if (helper.isReproducing())
							System.out.println("[" + index + "] Modify listed[" + leIndex + "].x from "
								+ modify.getListedEntities().getValues().get(leIndex).getX() + " to " + newX);
						TestEntity4 listedEntity = modify.getListedEntities().getValues().get(leIndex);
						try {
							listedEntity.setX(newX);
							if (listedEntity instanceof TestEntity5)
								Assert.assertTrue("Should have thrown exception", false);
							if (!checkEquals(newX, modify.getListedEntities().getValues().get(leIndex).getX()))
								Assert.assertEquals(newX, modify.getListedEntities().getValues().get(leIndex).getX(), newX * 1E-12);
							expected.get(index).getListedEntities().getValues().get(leIndex).setX(newX);
						} catch (UnsupportedOperationException e) {
							if (!(listedEntity instanceof TestEntity5))
								Assert.assertTrue("Should not have thrown exception", false);
						}
					}).or(1, () -> {// Modify listed entity.f (for TestEntity5 instances)
						if (testEntities.getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int index = helper.getInt(0, testEntities.getValues().size() - 1);
						TestEntity2 modify = testEntities.getValues().get(index);
						if (modify.getListedEntities().getValues().isEmpty()) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						int leIndex = helper.getInt(0, modify.getListedEntities().getValues().size() - 1);
						if (!(modify.getListedEntities().getValues().get(leIndex) instanceof TestEntity5)) {
							if (helper.isReproducing())
								System.out.println("Abort");
							return;
						}
						double newF = helper.getAnyDouble();
						TestEntity5 listed = ((TestEntity5) modify.getListedEntities().getValues().get(leIndex));
						if (helper.isReproducing())
							System.out.println("[" + index + "] Modify listed[" + leIndex + "].f from " + listed.getF() + " to " + newF);
						listed.setF(newF);
						if (!checkEquals(newF, ((TestEntity5) modify.getListedEntities().getValues().get(leIndex)).getF()))
							Assert.assertEquals(newF, ((TestEntity5) modify.getListedEntities().getValues().get(leIndex)).getF(),
								newF * 1E-12);
						((TestEntity5) expected.get(index).getListedEntities().getValues().get(leIndex)).setF(newF);
					}).or(.05, () -> { // Persist/depersist
						if (helper.isReproducing())
							System.out.println("Persist/depersist");
						StringWriter writer = new StringWriter();
						try {
							ObservableConfig.writeXml(theConfig, writer, theEncoding, "\t");
							theConfig.getAllContent().getValues().clear();
							ObservableConfig.readXml(theConfig, new ByteArrayInputStream(writer.toString().getBytes("UTF-8")), theEncoding);
						} catch (IOException | TextParseException e) {
							throw new IllegalStateException(e);
						}
					}).execute("modify");
				}

				tester1.check(expected);
				tester2.check(expected);
				theCompleteListeners.check();

				for (int j = 0; j < expected.size(); j++) {
					((TestEntity2Tester) expected.get(j)).check(testEntities1.getValues().get(j), testEntities2.getValues().get(j));
				}
			}
		}
	}

	@SuppressWarnings("javadoc")
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

	@SuppressWarnings("javadoc")
	public interface TestEntity2 {
		String getText();

		void setText(String text);

		TestEntity3 getEntityField();

		ObservableCollection<String> getTexts();

		SyncValueSet<TestEntity4> getListedEntities();
	}

	@SuppressWarnings("javadoc")
	public interface TestEntity3 {
		int getD();

		int setD(int d);
	}

	@SuppressWarnings("javadoc")
	public interface TestEntity4 {
		int getE();

		int setE(int e);

		double getX();

		double setX(double x);
	}

	@SuppressWarnings("javadoc")
	public interface TestEntity5 extends TestEntity4 {
		double getF();

		TestEntity5 setF(double f);

		@Override
		default double getX() {
			return 0.0;
		}

		@Override
		default double setX(double x) {
			throw new UnsupportedOperationException();
		}
	}

	private static String print(TestEntity entity) {
		return "a=" + entity.getA() + ", b=" + entity.getB() + ", c=" + entity.getC();
	}

	private static class TestEntity2Tester implements TestEntity2 {
		private String theText;
		private final TestEntity3 theEntityField;
		private final ObservableCollection<String> theTexts;
		private final ObservableCollection<TestEntity4> theListedEntities;

		private TestEntity2 theEntity1;
		private TestEntity2 theEntity2;

		private ObservableCollectionTester<String> theTextsTester1;
		private ObservableCollectionTester<TestEntity4> theLETester1;
		private ObservableCollectionTester<String> theTextsTester2;
		private ObservableCollectionTester<TestEntity4> theLETester2;

		TestEntity2Tester(TestEntity2 entity1, TestEntity2 entity2) {
			theText = entity1.getText();
			theEntityField = deepCopy(entity1.getEntityField());
			theTexts = ObservableCollection.build(String.class).build()//
				.withAll(entity1.getTexts());
			theListedEntities = ObservableCollection.create(TypeTokens.get().of(TestEntity4.class),
				BetterTreeList.<TestEntity4> build().build());
			for (TestEntity4 te4 : entity1.getListedEntities().getValues())
				theListedEntities.add(deepCopy(te4));

			install(entity1, entity2);
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
		public ObservableCollection<String> getTexts() {
			return theTexts;
		}

		@Override
		public SyncValueSet<TestEntity4> getListedEntities() {
			return new SyncValueSet<ObservableConfigTest.TestEntity4>() {
				@Override
				public ObservableCollection<TestEntity4> getValues() {
					return theListedEntities;
				}

				@Override
				public ConfiguredValueType<TestEntity4> getType() {
					return null;
				}

				@Override
				public <T extends TestEntity4> SyncValueCreator<TestEntity4, T> create(TypeToken<T> subType) {
					return null;
				}

				@Override
				public boolean equals(Object o) {
					return o instanceof SyncValueSet && theListedEntities.equals(((SyncValueSet<?>) o).getValues());
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
			StringBuilder str = new StringBuilder("TestEntity2{");
			str.append("entityField=").append(theEntityField).append(", ");
			str.append("listedEntities=").append(theListedEntities).append(", ");
			str.append("text=").append(theText).append(", ");
			str.append("texts=").append(theTexts);
			str.append('}');
			return str.toString();
		}

		void install(TestEntity2 entity1, TestEntity2 entity2) {
			if (theEntity1 != entity1) {
				theEntity1 = entity1;
				theTextsTester1 = new ObservableCollectionTester<>("texts", entity1.getTexts());
				theLETester1 = new ObservableCollectionTester<>("listed-entities", entity1.getListedEntities().getValues());
			}
			if (theEntity2 != entity2) {
				theEntity2 = entity2;
				theTextsTester2 = new ObservableCollectionTester<>("texts", entity2.getTexts());
				theLETester2 = new ObservableCollectionTester<>("listed-entities", entity2.getListedEntities().getValues());
			}
		}

		void check(TestEntity2 entity1, TestEntity2 entity2) {
			if (theEntity1 != entity1) {
				theEntity1 = entity1;
				theTextsTester1 = new ObservableCollectionTester<>("texts", entity1.getTexts());
				theLETester1 = new ObservableCollectionTester<>("listed-entities", entity1.getListedEntities().getValues());
			} else {
				theTextsTester1.check(theTexts);
				theLETester1.check(theListedEntities);
			}
			if (theEntity2 != entity2) {
				theEntity2 = entity2;
				theTextsTester2 = new ObservableCollectionTester<>("texts", entity2.getTexts());
				theLETester2 = new ObservableCollectionTester<>("listed-entities", entity2.getListedEntities().getValues());
			} else {
				theTextsTester2.check(theTexts);
				theLETester2.check(theListedEntities);
			}
			install(entity1, entity2);
		}

		void dispose() {
			theTextsTester1.setSynced(false);
			theLETester1.setSynced(false);
			theTextsTester2.setSynced(false);
			theLETester2.setSynced(false);
		}
	}

	static class TestEntity2SetListener implements Subscription {
		private final ObservableCollection<TestEntity2> theEntities;
		private final List<TestEntity2Listener> theListeners;

		private Subscription theSubscription;

		TestEntity2SetListener(ObservableCollection<TestEntity2> entities) {
			theEntities = entities;
			theListeners = new ArrayList<>(QommonsUtils.map(entities, TestEntity2Listener::new, false));
			theSubscription = entities.onChange(evt -> {
				switch (evt.getType()) {
				case add:
					theListeners.add(evt.getIndex(), new TestEntity2Listener(evt.getNewValue()));
					break;
				case remove:
					theListeners.remove(evt.getIndex()).unsubscribe();
					break;
				case set:
					break;
				}
			});
		}

		void check() {
			Assert.assertEquals(theEntities.size(), theListeners.size());
			for (int i = 0; i < theListeners.size(); i++) {
				Assert.assertTrue(theEntities.get(i) == theListeners.get(i).getEntity());
				theListeners.get(i).check();
			}
		}

		@Override
		public void unsubscribe() {
			theSubscription.unsubscribe();
			theSubscription = null;
		}
	}

	static class TestEntity2Listener implements Subscription {
		private final TestEntity2 theEntity;
		private String theText;
		private TestEntity3Listener theEntityField;
		private List<String> theTexts;
		private List<TestEntity4Listener> theListedEntities;

		private List<Subscription> theSubscriptions;

		TestEntity2Listener(TestEntity2 entity) {
			theEntity = entity;
			theSubscriptions = new ArrayList<>();
			theText = entity.getText();
			theSubscriptions.add(EntityReflector.observeField(entity, TestEntity2::getText).noInitChanges().act(evt -> {
				Assert.assertEquals(theText, evt.getOldValue());
				theText = evt.getNewValue();
			}));
			theEntityField = new TestEntity3Listener(entity.getEntityField());
			theTexts = new ArrayList<>(entity.getTexts());
			theSubscriptions.add(entity.getTexts().onChange(evt -> {
				switch (evt.getType()) {
				case add:
					theTexts.add(evt.getIndex(), evt.getNewValue());
					break;
				case remove:
					Assert.assertEquals(theTexts.remove(evt.getIndex()), evt.getOldValue());
					break;
				case set:
					Assert.assertEquals(theTexts.set(evt.getIndex(), evt.getNewValue()), evt.getOldValue());
					break;
				}
			}));
			theListedEntities = new ArrayList<>(
				QommonsUtils.map(entity.getListedEntities().getValues(), TestEntity4Listener::create, false));
			theSubscriptions.add(entity.getListedEntities().getValues().onChange(evt -> {
				switch (evt.getType()) {
				case add:
					theListedEntities.add(evt.getIndex(), TestEntity4Listener.create(evt.getNewValue()));
					break;
				case remove:
					theListedEntities.remove(evt.getIndex()).unsubscribe();
					break;
				case set:
					break;
				}
			}));
		}

		TestEntity2 getEntity() {
			return theEntity;
		}

		void check() {
			Assert.assertEquals(theEntity.getText(), theText);
			theEntityField.check();
			QommonsTestUtils.assertThat(theTexts, QommonsTestUtils.collectionsEqual(theEntity.getTexts(), true));
			Assert.assertEquals(theEntity.getListedEntities().getValues().size(), theListedEntities.size());
			for (int i = 0; i < theListedEntities.size(); i++) {
				Assert.assertTrue(theListedEntities.get(i).getEntity() == theEntity.getListedEntities().getValues().get(i));
				theListedEntities.get(i).check();
			}
		}

		@Override
		public void unsubscribe() {
			Subscription.forAll(theSubscriptions).unsubscribe();
			theSubscriptions.clear();
			theEntityField.unsubscribe();
			for (TestEntity4Listener te4 : theListedEntities)
				te4.unsubscribe();
			theListedEntities.clear();
		}
	}

	static class TestEntity3Listener implements Subscription {
		private final TestEntity3 theEntity;
		private int theD;

		private Subscription theSubscription;

		TestEntity3Listener(TestEntity3 entity) {
			theEntity = entity;
			theD = entity.getD();
			theSubscription = EntityReflector.observeField(entity, TestEntity3::getD).noInitChanges().act(evt -> {
				Assert.assertEquals(theD, evt.getOldValue().intValue());
				theD = evt.getNewValue();
			});
		}

		void check() {
			Assert.assertEquals(theEntity.getD(), theD);
		}

		@Override
		public void unsubscribe() {
			theSubscription.unsubscribe();
			theSubscription = null;
		}
	}

	static class TestEntity4Listener implements Subscription {
		private TestEntity4 theEntity;
		private int theE;
		private double theX;

		private final List<Subscription> theSubscriptions;

		TestEntity4Listener(TestEntity4 entity) {
			theEntity = entity;
			theSubscriptions = new ArrayList<>();
			theE = entity.getE();
			addSubscription(EntityReflector.observeField(entity, TestEntity4::getE).noInitChanges().act(evt -> {
				Assert.assertEquals(theE, evt.getOldValue().intValue());
				theE = evt.getNewValue();
			}));
			theX = entity.getX();
			addSubscription(EntityReflector.observeField(entity, TestEntity4::getX).noInitChanges().act(evt -> {
				Assert.assertEquals(theX, evt.getOldValue().doubleValue(), 1E-14);
				theX = evt.getNewValue();
			}));
		}

		TestEntity4 getEntity() {
			return theEntity;
		}

		protected void addSubscription(Subscription sub) {
			theSubscriptions.add(sub);
		}

		void check() {
			Assert.assertEquals(theEntity.getE(), theE);
			Assert.assertEquals(theEntity.getX(), theX, 1E-14);
		}

		@Override
		public void unsubscribe() {
			Subscription.forAll(theSubscriptions).unsubscribe();
			theSubscriptions.clear();
		}

		static TestEntity4Listener create(TestEntity4 entity) {
			if (entity instanceof TestEntity5)
				return new TestEntity5Listener((TestEntity5) entity);
			else
				return new TestEntity4Listener(entity);
		}
	}

	static class TestEntity5Listener extends TestEntity4Listener {
		private double theF;

		TestEntity5Listener(TestEntity5 entity) {
			super(entity);

			theF = entity.getF();
			addSubscription(EntityReflector.observeField(entity, TestEntity5::getF).noInitChanges().act(evt -> {
				Assert.assertEquals(theF, evt.getOldValue().doubleValue(), 1E-14);
				theF = evt.getNewValue();
			}));
		}

		@Override
		TestEntity5 getEntity() {
			return (TestEntity5) super.getEntity();
		}

		@Override
		void check() {
			super.check();
			Assert.assertEquals(getEntity().getF(), theF, 1E-14);
		}
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
				int oldD = theD;
				theD = d;
				return oldD;
			}

			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof TestEntity3))
					return false;
				TestEntity3 other = (TestEntity3) obj;
				if (theD != other.getD())
					return false;
				return true;
			}

			@Override
			public String toString() {
				return "TestEntity3{d=" + theD + "}";
			}
		};
	}

	private static TestEntity4 deepCopy(TestEntity4 entity) {
		class TestEntity4Tester implements TestEntity4 {
			private int theE;
			private double theX;

			TestEntity4Tester() {
				theE = entity.getE();
				theX = entity.getX();
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
			public double getX() {
				return theX;
			}

			@Override
			public double setX(double x) {
				double oldX = theX;
				theX = x;
				return oldX;
			}

			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof TestEntity4))
					return false;
				else if ((this instanceof TestEntity5) != (obj instanceof TestEntity5))
					return false;
				TestEntity4 other = (TestEntity4) obj;
				if (theE != other.getE())
					return false;
				else if (!checkEquals(theX, other.getX()))
					return false;
				return true;
			}

			@Override
			public String toString() {
				return "TestEntity4{e=" + theE + ", x=" + theX + "}";
			}
		};
		class TestEntity5Tester extends TestEntity4Tester implements TestEntity5 {
			private double theF;

			TestEntity5Tester() {
				theF = ((TestEntity5) entity).getF();
			}

			@Override
			public double getF() {
				return theF;
			}

			@Override
			public TestEntity5 setF(double f) {
				theF = f;
				return this;
			}

			@Override
			public boolean equals(Object obj) {
				if (!super.equals(obj))
					return false;
				TestEntity5 other = (TestEntity5) obj;
				return checkEquals(theF, other.getF());
			}

			@Override
			public String toString() {
				return "TestEntity5{e=" + getE() + ", f=" + theF + "}";
			}
		}
		return entity instanceof TestEntity5 ? new TestEntity5Tester() : new TestEntity4Tester();
	}

	private static boolean checkEquals(double d1, double d2) {
		if (d1 == d2) {//
		} else if (Double.isNaN(d1)) {
			if (!Double.isNaN(d2))
				return false;
		} else if (Double.isNaN(d2))
			return false;
		else {
			double diff = Math.abs(d1 - d2);
			if (diff < 1E-200) { // Differences between tiny numbers can't be treated the same way
				if (diff > Math.abs(d1))
					return false;
			} else if (diff > Math.abs(d1) * 1E-10)
				return false;
		}
		return true;
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
