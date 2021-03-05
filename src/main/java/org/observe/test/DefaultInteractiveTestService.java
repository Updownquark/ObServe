package org.observe.test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.XmlEncoding;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.config.QommonsConfig;
import org.xml.sax.SAXException;

/** Simple default implementation of {@link InteractiveTestingService} */
public class DefaultInteractiveTestService extends DefaultInteractiveTestSuite implements InteractiveTestingService {
	private final SettableValue<DefaultTesting> theCurrentTest;
	private final Map<String, ObservableValue<?>> theValues;
	private final String theGlobalConfigLocation;
	private ObservableConfig theGlobalConfig;
	private final List<URL> theTestResourceLocations;
	private File theTestResultsDirectory;
	private File theTestResourceCache;
	private long theTestResourceCacheSizeLimit;

	/** @param globalConfig The location of the global test configuration--may be null */
	public DefaultInteractiveTestService(String globalConfig) {
		this("Interactive Tests", globalConfig);
	}

	/**
	 * @param name The name of this test service
	 * @param globalConfig The location of the global test configuration--may be null
	 */
	public DefaultInteractiveTestService(String name, String globalConfig) {
		super(null, name, false, new StampedLockingStrategy(name));
		theCurrentTest = SettableValue.build(DefaultTesting.class).safe(false).build();
		theValues = new ConcurrentHashMap<>();
		theGlobalConfigLocation = globalConfig;
		theTestResourceLocations = new ArrayList<>();
		theTestResourceCacheSizeLimit = 25 * 1024 * 1024; // 25MB

		String testRsrcLoc = System.getProperty("qtest.resources");
		if (testRsrcLoc != null) {
			for (String loc : testRsrcLoc.split(",")) {
				URL locUrl;
				try {
					locUrl = QommonsConfig.toUrl(loc);
				} catch (IOException e) {
					System.err.println(getClass().getName() + ": Could not resolve test resource location " + loc);
					e.printStackTrace();
					continue;
				}
				addTestResourceLocation(locUrl);
			}
		}
		String testResultsLoc = System.getProperty("qtest.results");
		if (testResultsLoc != null)
			withTestResultsDirectory(new File(testResultsLoc));
	}

	/**
	 * Adds a location to which resource requests from {@link #getEnv() getEnv()}.{@link InteractiveTestEnvironment#getResource(String)} may
	 * be relative to
	 *
	 * @param url The root URL for test resources
	 * @return This test service
	 */
	public DefaultInteractiveTestService addTestResourceLocation(URL url) {
		theTestResourceLocations.add(url);
		return this;
	}

	/**
	 * @param testResultsDir The directory in which to persist test results. If null, test results will be written to a file co-located with
	 *        the class file of each test (if writable).
	 * @return This test service
	 */
	public DefaultInteractiveTestService withTestResultsDirectory(File testResultsDir) {
		theTestResultsDirectory = testResultsDir;
		return this;
	}

	/**
	 * @param cacheLocation The directory in which to cache test resources for faster access
	 * @return This test service
	 */
	public DefaultInteractiveTestService withCacheLocation(File cacheLocation) {
		theTestResourceCache = cacheLocation;
		return this;
	}

	/**
	 * @param maxCacheFileSize The maximum size of files which will be cached in the resource {@link #withCacheLocation(File) cache
	 *        location}
	 * @return This test service
	 */
	public DefaultInteractiveTestService withCacheMaxFileSize(long maxCacheFileSize) {
		theTestResourceCacheSizeLimit = maxCacheFileSize;
		return this;
	}

	@Override
	public ObservableValue<TestingState> getCurrentTest() {
		return theCurrentTest.map(TestingState.class, t -> t);
	}

	@Override
	public void cancelTest() {
		DefaultTesting testing = theCurrentTest.get();
		if (testing != null)
			testing.cancel();
	}

	@Override
	void setTesting(DefaultTesting testing) {
		theCurrentTest.set(testing, null);
	}

	@Override
	public InteractiveTestEnvironment getEnv() {
		return new TestEnv();
	}

	@Override
	public <T> void addValue(String name, ObservableValue<T> value) {
		if (theValues.putIfAbsent(name, value) != null)
			throw new IllegalArgumentException("A value is already associated with the name " + name);
	}

	@Override
	ObservableConfig getSuiteConfig() throws IOException {
		if (theGlobalConfig != null)
			return theGlobalConfig;
		ObservableConfig config = ObservableConfig.createRoot(getName(), null, __ -> new FastFailLockingStrategy());
		if (theGlobalConfigLocation != null) {
			URL url = QommonsConfig.toUrl(theGlobalConfigLocation);
			try {
				ObservableConfig.readXml(config, url.openStream(), XmlEncoding.DEFAULT);
			} catch (SAXException e) {
				throw new IOException("Could not parse " + url.getFile(), e);
			}
		}
		theGlobalConfig = config.unmodifiable();
		return config;
	}

	private File getCacheFile(URL resource) {
		if (theTestResourceCache == null) {
			String prop = System.getProperty("qtest.cache");
			if (prop != null)
				theTestResourceCache = new File(prop);
		}
		if (theTestResourceCache == null)
			theTestResourceCache = new File("~/.qtest");
		if (!theTestResourceCache.exists() && !theTestResourceCache.mkdirs()) {
			System.err.println("Could not create test resource cache directory: " + theTestResourceCache.getAbsolutePath());
			return null;
		}
		return new File(theTestResourceCache, resource.getHost() + "/" + resource.getFile());
	}

	@Override
	File getTestResultsDirectory() {
		return theTestResultsDirectory;
	}

	private class TestEnv implements InteractiveTestEnvironment {
		@Override
		public ObservableValue<?> getValueIfExists(String name) {
			return theValues.get(name);
		}

		@Override
		public InputStream getResource(String location) throws IOException {
			URL resource;
			long lastMod = 0;
			if (location.contains("://"))
				resource = new URL(location);
			else if (new File(location).exists())
				return new BufferedInputStream(new FileInputStream(location));
			else if (location.startsWith("/"))
				throw new IOException("Unrecognized resource: " + location);
			else {
				resource = null;
				for (URL trl : theTestResourceLocations) {
					try {
						URL testResource = new URL(trl + "/" + location);
						lastMod = testResource.openConnection().getLastModified();
						resource = testResource;
						break;
					} catch (IOException e) {
					}
				}
				if (resource == null)
					throw new IOException("Could not locate test resource " + location);
			}

			URLConnection conn = resource.openConnection();
			long size = conn.getContentLengthLong();
			if (size >= theTestResourceCacheSizeLimit)
				return new BufferedInputStream(conn.getInputStream());

			if (lastMod == 0)
				lastMod = conn.getLastModified();

			File cacheFile = getCacheFile(resource);
			if (cacheFile == null) // No available cache
				return new BufferedInputStream(conn.getInputStream());
			if (!cacheFile.exists() || cacheFile.lastModified() != lastMod || size != cacheFile.length()) {
				if (!cacheFile.getParentFile().exists() && !cacheFile.getParentFile().mkdirs()) {
					System.err.println("Could not create parent directory for cache file " + cacheFile.getAbsolutePath());
					return new BufferedInputStream(conn.getInputStream());
				}
				try (InputStream in = conn.getInputStream(); //
					OutputStream out = new FileOutputStream(cacheFile)) {
					byte[] buffer = new byte[64 * 1024 * 1024];
					int read = in.read(buffer);
					while (read >= 0) {
						out.write(buffer, 0, read);
						read = in.read(buffer);
					}
				}
				cacheFile.setLastModified(lastMod);
			}
			return new BufferedInputStream(new FileInputStream(cacheFile));
		}
	}
}
