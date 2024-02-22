package org.observe.quick.style;

import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import org.qommons.config.QommonsConfig;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.ErrorReporting;

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
				.holder2();
			InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> imgV = expression.interpret(ModelTypes.Value.forType(Image.class),
				env, tce);
			if (imgV != null)
				return imgV;
			InterpretedValueSynth<SettableValue<?>, SettableValue<URL>> urlV = expression.interpret(ModelTypes.Value.forType(URL.class),
				env, tce.clear());
			if (urlV != null)
				return urlV.map(ModelTypes.Value.forType(Image.class), mvi -> mvi.map(
					sv -> SettableValue.asSettable(sv.map(url -> url == null ? null : new ImageIcon(url).getImage()), __ -> "unsettable")));
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> stringV = expression
				.interpret(ModelTypes.Value.forType(String.class), env, tce.clear());
			ErrorReporting reporting = env.reporting().at(expression.getFilePosition());
			String sourceDocument = expression.getFilePosition().getFileLocation();
			if (stringV != null) {
				RuntimeCache cache = env.get(RuntimeCache.ENV_KEY, RuntimeCache.class);
				if (cache == null)
					cache = new RuntimeCache();
				env.putGlobal(RuntimeCache.ENV_KEY, cache);
				RuntimeCache fCache = cache;
				return stringV.map(ModelTypes.Value.forType(Image.class), mvi -> mvi.map(sv -> SettableValue.asSettable(sv.map(loc -> {
					if (loc == null)
						return null;
					IconKey key = new IconKey(loc);
					Object found = fCache.getCacheItem(key);
					if (found instanceof Image)
						return (Image) found;
					else if (found != null)
						return null; // Error. Don't report again, just return null
					String relLoc;
					try {
						relLoc = QommonsConfig.resolve(loc, QuickStyleUtils.class, sourceDocument);
					} catch (IOException e) {
						reporting.at(expression.getFilePosition())
						.error("Could not resolve icon location '" + loc + "' relative to document " + sourceDocument);
						e.printStackTrace();
						return null;
					}
					Image img = null;
					Icon icon = ObservableSwingUtils.getFixedIcon(null, relLoc, 16, 16);
					if (icon == null)
						icon = ObservableSwingUtils.getFixedIcon(null, loc, 16, 16);
					if (icon == null)
						reporting.at(expression.getFilePosition()).error("Icon file not found: '" + loc);
					else if (icon instanceof ImageIcon) {
						if (((ImageIcon) icon).getImageLoadStatus() == MediaTracker.ERRORED) {
							fCache.setCacheItem(key, "Image load error");
							reporting.at(expression.getFilePosition()).error("Icon file could not be loaded: '" + loc);
						} else
							img = ((ImageIcon) icon).getImage();
					} else {
						BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_4BYTE_ABGR);
						// Hopefully the icon doesn't need the component argument
						icon.paintIcon(null, image.getGraphics(), 0, 0);
						img = image;
					}
					if (img != null)
						fCache.setCacheItem(key, img);
					return img;
				}), __ -> "unsettable")));
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
		if (loc == null)
			return null;
		RuntimeCache cache = env.get(RuntimeCache.ENV_KEY, RuntimeCache.class);
		if (cache == null)
			cache = new RuntimeCache();
		env.putGlobal(RuntimeCache.ENV_KEY, cache);

		IconKey key = new IconKey(loc);
		Object found = cache.getCacheItem(key);
		if (found instanceof Image)
			return (Image) found;
		else if (found != null)
			throw new ParseException((String) found, 0);
		String sourceDocument = env.reporting().getFileLocation().getFileLocation();
		String relLoc;
		try {
			relLoc = QommonsConfig.resolve(loc, QuickStyleUtils.class, sourceDocument);
		} catch (IOException e) {
			throw new ParseException("Could not resolve icon location '" + loc + "' relative to document " + sourceDocument, 0);
		}
		Image img = null;
		Icon icon = ObservableSwingUtils.getFixedIcon(null, relLoc, 16, 16);
		if (icon == null)
			icon = ObservableSwingUtils.getFixedIcon(null, loc, 16, 16);
		if (icon == null) {
			cache.setCacheItem(key, "Icon file not found: '" + loc + "'");
			throw new ParseException("Icon file not found: '" + loc + "'", 0);
		} else if (icon instanceof ImageIcon) {
			if (((ImageIcon) icon).getImageLoadStatus() == MediaTracker.ERRORED) {
				cache.setCacheItem(key, "Image load error");
				throw new ParseException("Icon file could not be loaded: '" + loc, 0);
			} else
				img = ((ImageIcon) icon).getImage();
		} else {
			BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_4BYTE_ABGR);
			// Hopefully the icon doesn't need the component argument
			icon.paintIcon(null, image.getGraphics(), 0, 0);
			img = image;
		}
		if (img != null)
			cache.setCacheItem(key, img);
		return img;
	}
}
