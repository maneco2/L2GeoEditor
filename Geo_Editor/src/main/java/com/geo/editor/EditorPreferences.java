/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.editor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/** Preserves the shared XML while owning only GEOEDITOR_* preference keys. */
final class EditorPreferences {
	private static final String PREFIX = "GEOEDITOR_";
	private final File file;
	private final Properties values = new Properties();

	EditorPreferences(final File file) {
		this.file = file;
	}

	void load() {
		if (!file.isFile())
			return;
		try (FileInputStream input = new FileInputStream(file)) {
			values.loadFromXML(input);
		} catch (final IOException | IllegalArgumentException exception) {
			System.err.println("GeoEditor settings load failed: " + exception.getMessage());
		}
	}

	float getFloat(final String key, final float fallback,
			final float minimum, final float maximum) {
		final String configured = values.getProperty(PREFIX + key);
		if (configured == null)
			return fallback;
		try {
			final float value = Float.parseFloat(configured);
			return Math.max(minimum, Math.min(maximum, value));
		} catch (final NumberFormatException exception) {
			return fallback;
		}
	}

	boolean getBoolean(final String key, final boolean fallback) {
		final String value = values.getProperty(PREFIX + key);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	String get(final String key, final String fallback) {
		return values.getProperty(PREFIX + key, fallback);
	}

	String getShared(final String key, final String fallback) {
		return values.getProperty(key, fallback);
	}

	int getSharedInt(final String key, final int fallback) {
		final String configured = values.getProperty(key);
		if (configured == null)
			return fallback;
		try {
			return Integer.parseInt(configured);
		} catch (final NumberFormatException exception) {
			return fallback;
		}
	}

	boolean getSharedBoolean(final String key, final boolean fallback) {
		final String configured = values.getProperty(key);
		return configured == null ? fallback : Boolean.parseBoolean(configured);
	}

	void put(final String key, final Object value) {
		values.setProperty(PREFIX + key, String.valueOf(value));
	}

	void putShared(final String key, final Object value) {
		values.setProperty(key, String.valueOf(value));
	}

	void save() {
		final File parent = file.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs())
			throw new IllegalStateException("Unable to create settings folder: " + parent);
		try (FileOutputStream output = new FileOutputStream(file)) {
			values.storeToXML(output, "L2GeoEditor Config", "UTF-8");
		} catch (final IOException exception) {
			throw new IllegalStateException(
					"Unable to save GeoEditor settings: " + file, exception);
		}
	}
}
