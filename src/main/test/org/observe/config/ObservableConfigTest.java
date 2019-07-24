package org.observe.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Duration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.observe.Observable;
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
		StringWriter writer = new StringWriter();
		ObservableConfig.writeXml(theConfig, writer, theEncoding, "\t");
		theConfig.getAllContent().getValues().clear();
		readXml(new ByteArrayInputStream(writer.toString().getBytes("UTF-8")));
		testXml1();
	}

	@Test
	public void testEntities() throws IOException, SAXException {
		readXml(getClass().getResourceAsStream("TestEntities.xml"));
		ObservableValueSet<TestEntity> testEntities = theConfig.observeEntities(theConfig.createPath("test-entities/test-entity"),
			TypeTokens.get().of(TestEntity.class), Observable.empty());
		int i = 0;
		for (TestEntity entity : testEntities.getValues()) {
			switch (i) {
			case 0:
				Assert.assertEquals(5, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ofMinutes(10), entity.getC());
				break;
			case 1:
				Assert.assertEquals(10, entity.getA());
				Assert.assertEquals(false, entity.getB());
				Assert.assertEquals(Duration.ofSeconds(10), entity.getC());
				break;
			case 2:
				Assert.assertEquals(4, entity.getA());
				Assert.assertEquals(true, entity.getB());
				Assert.assertEquals(Duration.ofHours(1), entity.getC());
				break;
			case 3:
				Assert.assertEquals(42, entity.getA());
				Assert.assertEquals(false, entity.getB());
				Assert.assertEquals(Duration.ofDays(10 * 7), entity.getC());
				break;
			default:
				Assert.assertTrue("Too many entities", false);
			}
			i++;
		}
	}

	public interface TestEntity {
		int getA();
		TestEntity setA(int a);

		boolean getB();
		TestEntity setB(boolean b);

		Duration getC();
		void setC(Duration c);
	}
}
