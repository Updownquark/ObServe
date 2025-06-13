package org.observe.quick.style;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.LocatedExpression;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.BreakpointHere;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.ErrorReporting;
import org.qommons.io.ResourceLocator;

/** General utilities for Quick Styles */
public class QuickStyleUtils {
	/** A runtime cache that can be stashed in an expresso environment to avoid loading resources multiple times */
	public static class RuntimeCache {
		/** The session key by which to store the cache in the expresso environment */
		public static final String ENV_KEY = "Quick.Runtime.Cache";

		private final Map<Object, Object> theCache = new ConcurrentHashMap<>();

		/**
		 * @param key The key to get the resource for
		 * @return The resource in this cache with the given key, or null
		 */
		public Object getCacheItem(Object key) {
			return theCache.get(key);
		}

		/**
		 * @param key The key to store the resource under
		 * @param value The resource to cache with the given key
		 */
		public void setCacheItem(Object key, Object value) {
			theCache.put(key, value);
		}
	}

	private static class ResourceLocatorKey {
		private final String theDocument;

		ResourceLocatorKey(String document) {
			theDocument = document;
		}

		@Override
		public int hashCode() {
			return theDocument.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ResourceLocatorKey && theDocument.equals(((ResourceLocatorKey) obj).theDocument);
		}

		@Override
		public String toString() {
			return theDocument;
		}
	}

	private static class IconKey {
		private final String theLocation;

		IconKey(String location) {
			theLocation = location;
		}

		@Override
		public int hashCode() {
			return theLocation.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof IconKey && theLocation.equals(((IconKey) obj).theLocation);
		}
	}

	/**
	 * Evaluates an icon in Quick
	 *
	 * @param expression The expression to parse
	 * @param env The expresso environment in which to parse the expression
	 * @return The ModelValueSynth to produce the icon value
	 * @throws ExpressoInterpretationException If the icon could not be evaluated
	 */
	public static InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> evaluateIcon(LocatedExpression expression,
		InterpretedExpressoEnv env) throws ExpressoInterpretationException {
		if (expression != null) {
			ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, NeverThrown, NeverThrown> tce = ExceptionHandler
				.placeHolder2();
			InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> imgV = expression.interpret(ModelTypes.Value.forType(Image.class),
				env, tce);
			if (imgV != null)
				return imgV;
			InterpretedValueSynth<SettableValue<?>, SettableValue<URL>> urlV = expression.interpret(ModelTypes.Value.forType(URL.class),
				env, tce.clear());
			ErrorReporting reporting = env.reporting().at(expression.getFilePosition());
			if (urlV != null)
				return urlV.map(ModelTypes.Value.forType(Image.class), mvi -> mvi.map(
					sv -> SettableValue.asSettable(sv.map(url -> url == null ? null : new ImageIcon(url).getImage()),
						__ -> reporting.getFileLocation().getPosition(0).toShortString() + "url->image is not reversible")));
			InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> iconV = expression.interpret(ModelTypes.Value.forType(Icon.class),
				env, tce.clear());
			if (iconV != null) {
				return iconV.map(ModelTypes.Value.forType(Image.class), mvi -> mvi.map(sv -> SettableValue.asSettable(sv.map(icon -> {
					if (icon == null)
						return null;
					else if (icon instanceof ImageIcon)
						return ((ImageIcon) icon).getImage();
					BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_4BYTE_ABGR);
					icon.paintIcon(null, image.getGraphics(), 0, 0);
					return image;
				}), __ -> reporting.getFileLocation().getPosition(0).toShortString() + "icon->image is not reversible")));
			}
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> stringV = expression
				.interpret(ModelTypes.Value.forType(String.class), env, tce.clear());
			String sourceDocument = expression.getFilePosition().getFileLocation();
			ClassLoader ccl = Thread.currentThread().getContextClassLoader();
			if (stringV != null) {
				RuntimeCache cache = env.get(RuntimeCache.ENV_KEY, RuntimeCache.class);
				if (cache == null)
					cache = new RuntimeCache();
				env.putGlobal(RuntimeCache.ENV_KEY, cache);
				RuntimeCache fCache = cache;
				return stringV.map(ModelTypes.Value.forType(Image.class), mvi -> mvi.map(sv -> SettableValue.asSettable(sv.map(loc -> {
					if (loc == null || loc.isEmpty())
						return null;
					Image img;
					try {
						img = parseImage(loc, sourceDocument, ccl, fCache);
					} catch (ParseException e) {
						reporting.warn(e.getMessage());
						return null;
					}
					return img;
				}), __ -> reporting.getFileLocation().getPosition(0).toShortString() + "string->image is not reversible")));
			}
			reporting.warn("Cannot evaluate '" + expression + "' as an icon");
			return InterpretedValueSynth.literalValue(TypeTokens.get().of(Image.class), null, "Icon not provided");
		} else
			return InterpretedValueSynth.literalValue(TypeTokens.get().of(Image.class), null, "None provided");
	}

	/**
	 * @param loc The location text for the icon
	 * @param env The expresso environment containing resources to help find the icon
	 * @return The icon
	 * @throws ParseException If the icon could not be found or parsed
	 */
	public static Image parseIcon(String loc, InterpretedExpressoEnv env) throws ParseException {
		if (loc == null || loc.isEmpty())
			return null;
		RuntimeCache cache = env.get(RuntimeCache.ENV_KEY, RuntimeCache.class);
		if (cache == null)
			cache = new RuntimeCache();
		env.putGlobal(RuntimeCache.ENV_KEY, cache);
		return parseImage(loc, env.reporting().getFileLocation().getFileLocation(), Thread.currentThread().getContextClassLoader(), cache);
	}

	private static final Pattern ICON_SIZE_POSTFIX = Pattern.compile("(?<loc>.*)\\$(?<w>\\d{1,6})x(?<h>\\d{1,6})");

	private static Image parseImage(String iconLocation, String sourceDocument, ClassLoader contextClassLoader, RuntimeCache cache)
		throws ParseException {
		Matcher m = ICON_SIZE_POSTFIX.matcher(iconLocation);
		Dimension size = null;
		if (m.matches()) {
			iconLocation = m.group("loc");
			size = new Dimension(Integer.parseInt(m.group("w")), Integer.parseInt(m.group("h")));
		}
		IconKey key = new IconKey(iconLocation);
		Object found = cache.getCacheItem(key);
		if (found instanceof ImageIcon)
			return resize((ImageIcon) found, size);
		else if (found instanceof String)
			throw new ParseException((String) found, 0);
		ResourceLocatorKey rlk = new ResourceLocatorKey(sourceDocument);
		ResourceLocator locator = (ResourceLocator) cache.getCacheItem(rlk);
		if (locator == null) {
			locator = createLocator(sourceDocument, contextClassLoader);
			cache.setCacheItem(rlk, locator);
		}
		ImageIcon img;
		try {
			img = parseIcon(iconLocation, locator);
			if (img == null) {
				String msg = "Icon file not found@ '" + iconLocation + "'";
				cache.setCacheItem(key, msg);
				throw new ParseException(msg, 0);
			}
		} catch (IOException e) {
			e.printStackTrace();
			String msg = "Icon could not be loaded@ '" + iconLocation + "': " + e.getMessage();
			cache.setCacheItem(key, msg);
			throw new ParseException(msg, 0);
		} catch (ParseException | RuntimeException e) {
			cache.setCacheItem(key, e.getMessage());
			throw e;
		}
		cache.setCacheItem(key, img);
		return resize(img, size);
	}

	private static ResourceLocator createLocator(String sourceDocument, ClassLoader contextClassLoader) {
		// Try a whole bunch of different ways to resolve the icon resource
		ResourceLocator locator = new ResourceLocator();
		locator.relativeTo(ObservableSwingUtils.class);
		locator.relativeTo(contextClassLoader);
		locator.relativeTo(sourceDocument);
		return locator;
	}

	private static ImageIcon parseIcon(String iconLocation, ResourceLocator locator) throws IOException, ParseException {
		URL url = locator.findResource(iconLocation);
		if (url == null) {
			BreakpointHere.breakpoint(); // TODO Remove
			locator.findResource(iconLocation);
			return null;
		}
		ImageIcon icon = new ImageIcon(url);
		if (icon.getImageLoadStatus() == MediaTracker.ERRORED) {
			throw new ParseException("Icon file could not be loaded: '" + iconLocation + "'", 0);
		}
		return icon;
	}

	private static Image resize(ImageIcon icon, Dimension size) {
		if (size == null || (size.width == icon.getIconWidth() && size.height == icon.getIconHeight()))
			return icon.getImage();
		return icon.getImage().getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH);
	}
}
