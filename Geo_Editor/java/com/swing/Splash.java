/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.swing;

import com.util.Util;
import com.Config;
import com.util.WindowsWindowActivator;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;

import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Fullscreen startup transition displayed while the editor is initialized.
 *
 * @author Forsaiken aka Patrick
 * @author Maneco2
 * @version 1.2
 */
@SuppressWarnings("serial")
public final class Splash extends JWindow implements Runnable {
	private static final float FADE_IN_STEP = 0.04F;
	private static final float FADE_OUT_STEP = 0.01F;
	private static final long FADE_DELAY_MS = 25L;
	private static final long EDITOR_SETTLE_MS = 150L;

	public static interface CheckedRunnable {
		public boolean run();
	}

	private final long _minimumVisibleUntil;
	private final CheckedRunnable _readyCheck;
	private final Runnable _showEditor;
	private final CountDownLatch _fadeInFinished = new CountDownLatch(1);
	private final BufferedImage _image;

	private volatile float _alpha;

	public Splash(final long minimumVisibleMillis, final Runnable initializeEditor,
			final CheckedRunnable readyCheck, final Runnable showEditor) {
		_minimumVisibleUntil = System.currentTimeMillis() + minimumVisibleMillis;
		_readyCheck = readyCheck;
		_showEditor = showEditor;
		_image = Util.loadImage("./data/image/geoeditor-splash.png");

		if (_image == null) {
			initializeEditor.run();
			waitUntilReady();
			showEditor.run();
			return;
		}

		showSplash();
		final Thread animation = new Thread(this, "GeoEditor-Splash");
		animation.setDaemon(true);
		animation.start();
		try {
			_fadeInFinished.await();
			SwingUtilities.invokeAndWait(initializeEditor);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Splash initialization was interrupted", e);
		} catch (final Exception e) {
			throw new RuntimeException("Unable to initialize the editor on the Swing EDT", e);
		}
	}

	private void showSplash() {
		final Runnable show = () -> {
			setAlwaysOnTop(true);
			setFocusableWindowState(false);
			setBackground(new Color(0, 0, 0, 0));
			setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
			setVisible(true);
		};
		if (SwingUtilities.isEventDispatchThread())
			show.run();
		else {
			try {
				SwingUtilities.invokeAndWait(show);
			} catch (final Exception e) {
				throw new RuntimeException("Unable to display the startup splash", e);
			}
		}
	}

	@Override
	public void run() {
		try {
			fadeTo(1.0F, FADE_IN_STEP);
			_fadeInFinished.countDown();
			waitUntilReady();

			final long remaining = _minimumVisibleUntil - System.currentTimeMillis();
			if (remaining > 0)
				Thread.sleep(remaining);

			_showEditor.run();
			// Bring the fully rendered editor directly behind this always-on-top
			// splash before changing alpha, so the fade cannot reveal another app.
			WindowsWindowActivator.activate(Config.APP_NAME + " [Beta " + Config.APP_VERSION + "]");
			// Keep the splash fully opaque until Swing and the first OpenGL frames have
			// populated the editor. This prevents a brief blank-frame flash on Windows.
			Thread.sleep(EDITOR_SETTLE_MS);
			fadeTo(0.0F, FADE_OUT_STEP);
		} catch (final Throwable throwable) {
			_fadeInFinished.countDown();
			throwable.printStackTrace();
		} finally {
			SwingUtilities.invokeLater(() -> {
				setVisible(false);
				dispose();
				WindowsWindowActivator.activate(Config.APP_NAME + " [Beta " + Config.APP_VERSION + "]");
			});
		}
	}

	private void waitUntilReady() {
		while (!_readyCheck.run()) {
			try {
				Thread.sleep(2L);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private void fadeTo(final float target, final float step) throws InterruptedException {
		while (Math.abs(_alpha - target) > 0.0001F) {
			_alpha = target > _alpha ? Math.min(target, _alpha + step) : Math.max(target, _alpha - step);
			repaint();
			Thread.sleep(FADE_DELAY_MS);
		}
	}

	@Override
	public void paint(final Graphics g) {
		final Graphics2D graphics = (Graphics2D) g.create();
		try {
			graphics.setComposite(AlphaComposite.Src);
			graphics.setColor(new Color(0, 0, 0, 0));
			graphics.fillRect(0, 0, getWidth(), getHeight());
			graphics.setComposite(AlphaComposite.SrcOver.derive(_alpha));
			graphics.setColor(Color.BLACK);
			graphics.fillRect(0, 0, getWidth(), getHeight());
			final Rectangle bounds = calculateCoverBounds(_image.getWidth(), _image.getHeight(), getWidth(),
					getHeight());
			graphics.drawImage(_image, bounds.x, bounds.y, bounds.width, bounds.height, null);
		} finally {
			graphics.dispose();
		}
	}

	/** Returns centered cover bounds without distorting the splash aspect ratio. */
	public static Rectangle calculateCoverBounds(final int imageWidth, final int imageHeight,
			final int targetWidth, final int targetHeight) {
		if (imageWidth <= 0 || imageHeight <= 0 || targetWidth <= 0 || targetHeight <= 0)
			return new Rectangle();
		final double scale = Math.max((double) targetWidth / imageWidth, (double) targetHeight / imageHeight);
		final int width = (int) Math.ceil(imageWidth * scale);
		final int height = (int) Math.ceil(imageHeight * scale);
		return new Rectangle((targetWidth - width) / 2, (targetHeight - height) / 2, width, height);
	}

	public static long calculateFadeDurationMillis(final float step) {
		if (step <= 0F)
			return 0L;
		return (long) Math.ceil(1F / step) * FADE_DELAY_MS;
	}
}
