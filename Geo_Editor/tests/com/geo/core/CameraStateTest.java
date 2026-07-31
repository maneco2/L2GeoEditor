package com.geo.core;

public final class CameraStateTest {
	public static void main(final String[] args) {
		final CameraState camera = new CameraState();
		camera.set(0f, 0f, 0f, 0f, 0f);
		camera.moveForward(-10d);
		check(close(camera.x(), 0f) && close(camera.z(), -10f),
				"legacy forward movement changed");
		camera.moveSideways(10d);
		check(close(camera.x(), -10f) && close(camera.z(), -10f),
				"legacy sideways movement changed");
		camera.moveUp(5d);
		check(close(camera.y(), 5f), "legacy vertical movement changed");
		camera.rotate(40f, -20f);
		check(close(camera.rotationX(), 5f) && close(camera.rotationY(), -10f),
				"legacy mouse rotation changed");
		System.out.println("CameraStateTest passed.");
	}

	private static boolean close(final float actual, final float expected) {
		return Math.abs(actual - expected) < 0.0001f;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
