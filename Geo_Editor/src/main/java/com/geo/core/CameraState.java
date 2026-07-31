/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

/** Renderer-neutral camera using the exact movement math from legacy GLCamera. */
public final class CameraState {
	private float x;
	private float y;
	private float z;
	private float rotationX;
	private float rotationY;

	public void set(final float x, final float y, final float z,
			final float rotationX, final float rotationY) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.rotationX = Math.max(-90f, Math.min(90f, rotationX));
		this.rotationY = rotationY;
	}

	public void rotate(final float mouseDeltaX, final float mouseDeltaY) {
		rotationX = Math.max(-90f, Math.min(90f,
				rotationX - mouseDeltaY * 0.25f));
		rotationY -= mouseDeltaX * 0.25f;
	}

	public void moveForward(final double move) {
		final double radians = Math.toRadians(rotationY);
		double dx = Math.sin(radians);
		double dy = Math.tan(Math.toRadians(rotationX));
		double dz = Math.cos(radians);
		double length = dx * dx + dy * dy + dz * dz;
		if (length != 1d && length != 0d) {
			length = 1d / Math.sqrt(length);
			dx *= length;
			dy *= length;
			dz *= length;
		}
		x += dx * move;
		y += dy * -move;
		z += dz * move;
	}

	public void moveSideways(final double move) {
		final double radians = Math.toRadians(rotationY - 90f);
		x += Math.sin(radians) * move;
		z += Math.cos(radians) * move;
	}

	public void moveUp(final double move) {
		y += move;
	}

	public float x() { return x; }
	public float y() { return y; }
	public float z() { return z; }
	public float rotationX() { return rotationX; }
	public float rotationY() { return rotationY; }
}
