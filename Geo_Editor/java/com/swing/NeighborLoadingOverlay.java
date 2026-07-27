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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.util.Util;

/** Blocking progress overlay used while a complete neighbor window is rebuilt. @author Maneco2 @version 1.0 */
@SuppressWarnings("serial")
public final class NeighborLoadingOverlay extends JComponent {
	private static final Color CYAN = new Color(0, 205, 235);
	private static final int CARD_WIDTH = 430;
	private static final int CARD_HEIGHT = 250;
	private static final String BACKGROUND_FILE = "./data/image/neighbor-loading-background.png";
	private final LoadingCard _card = new LoadingCard();
	private final BufferedImage _background = Util.loadImage(BACKGROUND_FILE);
	private int _completed;
	private int _total;
	private String _phase = "Preparing geodata";
	private float _alpha;
	private Timer _fadeTimer;

	public NeighborLoadingOverlay() {
		setOpaque(false);
		final Font interfaceFont = UIManager.getFont("Label.font");
		setFont(interfaceFont == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 13) : interfaceFont);
		setLayout(null);
		add(_card);
		setFocusable(true);
		addMouseListener(new MouseAdapter() { });
		addMouseMotionListener(new MouseAdapter() { });
		addMouseWheelListener(event -> event.consume());
		addKeyListener(new KeyAdapter() { });
		setVisible(false);
	}

	@Override
	public void doLayout() {
		_card.setBounds(Math.max(0, (getWidth() - CARD_WIDTH) / 2),
				Math.max(0, (getHeight() - CARD_HEIGHT) / 2), CARD_WIDTH, CARD_HEIGHT);
	}

	public void start(final int total, final String phase) {
		runOnEdt(() -> {
			if (_fadeTimer != null)
				_fadeTimer.stop();
			_completed = 0;
			_total = Math.max(0, total);
			_phase = phase;
			_alpha = 1f;
			setVisible(true);
			revalidate();
			doLayout();
			requestFocusInWindow();
			repaint();
		});
	}

	public void updateProgress(final int completed, final int total, final String phase) {
		runOnEdt(() -> {
			_completed = Math.max(0, completed);
			_total = Math.max(0, total);
			_phase = phase;
			_card.repaint();
		});
	}

	public void finish(final Runnable afterFade) {
		runOnEdt(() -> {
			_completed = _total;
			_phase = "Ready";
			repaint();
			_fadeTimer = new Timer(30, null);
			_fadeTimer.setInitialDelay(250);
			_fadeTimer.addActionListener(event -> {
				_alpha -= 0.08f;
				if (_alpha <= 0f) {
					_fadeTimer.stop();
					setVisible(false);
					if (afterFade != null)
						afterFade.run();
				}
				repaint();
			});
			_fadeTimer.start();
		});
	}

	public static int percentage(final int completed, final int total) {
		if (total <= 0)
			return 100;
		return Math.max(0, Math.min(100, Math.round(completed * 100f / total)));
	}

	@Override
	protected void paintComponent(final Graphics graphics) {
		if (_alpha <= 0f)
			return;
		final Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setComposite(AlphaComposite.SrcOver.derive(_alpha));
		g.setColor(new Color(0, 7, 12, 170));
		g.fillRect(0, 0, getWidth(), getHeight());
		g.dispose();
	}

	private final class LoadingCard extends JComponent {
		private static final long serialVersionUID = 1L;

		@Override
		protected void paintComponent(final Graphics graphics) {
			final Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setComposite(AlphaComposite.SrcOver.derive(_alpha));
			g.setClip(new RoundRectangle2D.Float(0, 0, CARD_WIDTH - 1, CARD_HEIGHT - 1, 22, 22));
			if (_background != null)
				g.drawImage(_background, 0, 0, CARD_WIDTH, CARD_HEIGHT, null);
			g.setColor(new Color(5, 15, 21, _background == null ? 252 : 96));
			g.fillRoundRect(0, 0, CARD_WIDTH - 1, CARD_HEIGHT - 1, 22, 22);
			g.setClip(null);
			g.setColor(new Color(90, 138, 155));
			g.drawRoundRect(0, 0, CARD_WIDTH - 1, CARD_HEIGHT - 1, 22, 22);

			paintTopography(g, 28, 26, CARD_WIDTH - 56, 48);
			final Font baseFont = NeighborLoadingOverlay.this.getFont();
			g.setFont(baseFont.deriveFont(Font.BOLD, 18f));
			g.setColor(Color.WHITE);
			drawCentered(g, "Loading neighboring regions", 105);
			final int percent = percentage(_completed, _total);
			g.setFont(baseFont.deriveFont(Font.BOLD, 23f));
			g.setColor(CYAN);
			drawCentered(g, percent + "%", 137);

			final int barX = 48;
			final int barY = 153;
			final int barWidth = CARD_WIDTH - 96;
			g.setColor(new Color(30, 45, 53));
			g.fillRoundRect(barX, barY, barWidth, 15, 6, 6);
			g.setColor(CYAN);
			g.fillRoundRect(barX, barY, Math.round(barWidth * percent / 100f), 15, 6, 6);

			g.setFont(baseFont.deriveFont(13f));
			g.setColor(new Color(205, 216, 221));
			drawCentered(g, _completed + " / " + _total + " regions", 195);
			g.setColor(new Color(76, 216, 236));
			drawCentered(g, _phase, 220);
			g.dispose();
		}

		private void drawCentered(final Graphics2D g, final String text, final int baseline) {
			g.drawString(text, (CARD_WIDTH - g.getFontMetrics().stringWidth(text)) / 2, baseline);
		}
	}

	private static void paintTopography(final Graphics2D g, final int x, final int y, final int width,
			final int height) {
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(20, 91, 107));
		for (int row = 0; row < 4; row++) {
			int previousX = x;
			int previousY = y + height - row * 7;
			for (int column = 1; column <= 20; column++) {
				final int nextX = x + column * width / 20;
				final double wave = Math.sin(column * 0.72 + row) * (8 + row * 2);
				final int nextY = y + height - row * 7 - (int) wave;
				g.drawLine(previousX, previousY, nextX, nextY);
				previousX = nextX;
				previousY = nextY;
			}
		}
		g.setColor(CYAN);
		for (int index = 0; index < 7; index++)
			g.fillOval(x + width / 2 - 38 + index * 13, y + height - 8 + (index % 2) * 4, 4, 4);
	}

	private static void runOnEdt(final Runnable action) {
		if (SwingUtilities.isEventDispatchThread())
			action.run();
		else
			SwingUtilities.invokeLater(action);
	}
}
