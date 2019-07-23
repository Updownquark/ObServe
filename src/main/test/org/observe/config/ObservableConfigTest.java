package org.observe.config;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.observe.config.ObservableConfig.XmlEncoding;
import org.xml.sax.SAXException;

public class ObservableConfigTest {
	private ObservableConfig theConfig;

	@Before
	public void initConfig() {
		theConfig = ObservableConfig.createRoot("root");
	}

	@Test
	public void testXml1() throws IOException, SAXException {
		ObservableConfig.readXml(theConfig, getClass().getResourceAsStream("TestXml1.xml"),
			new XmlEncoding(":x", ":xx", " ", "blah", "test"));
		Assert.assertEquals("config", theConfig.getName());
		Assert.assertEquals("root", theConfig.get("rootAttr"));
		Assert.assertEquals("This is some\ntext", theConfig.get("element2"));
		Assert.assertEquals("\n", theConfig.get("elemenblaht3"));
		Assert.assertEquals("", theConfig.get("element4"));
	}
}
