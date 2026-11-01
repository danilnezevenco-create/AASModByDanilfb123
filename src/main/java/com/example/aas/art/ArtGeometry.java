package com.example.aas.art;

/**
 * Pure geometry shared by the server (validation / clamping) and the client (selection preview).
 * Everything is in world blocks on the XZ plane. No Minecraft classes, so it is safe on both sides.
 */
public final class ArtGeometry {
    private ArtGeometry() {}

    /** Moves (x, z) into the circle (cx, cz, r) if it lies outside, returns {x, z}. */
    public static double[] clampToCircle(double x, double z, double cx, double cz, double r) {
        double dx = x - cx, dz = z - cz;
        double d = Math.hypot(dx, dz);
        if (d <= r || d < 1e-9) return new double[]{x, z};
        double k = r / d;
        return new double[]{cx + dx * k, cz + dz * k};
    }

    /** True if the whole circle (x, z, r) lies inside the circle (cx, cz, R). */
    public static boolean circleInside(double x, double z, double r, double cx, double cz, double R) {
        return Math.hypot(x - cx, z - cz) + r <= R + 1e-6;
    }

    /** Largest width such that the whole rectangle A->B (given width) stays inside the circle (cx, cz, R). */
    public static double maxWidthInside(double ax, double az, double bx, double bz, double cx, double cz, double R) {
        double dx = bx - ax, dz = bz - az;
        double len = Math.hypot(dx, dz);
        if (len < 1e-9) return 0;
        double nx = -dz / len, nz = dx / len;
        double lo = 0, hi = 2 * R;
        for (int i = 0; i < 32; i++) {
            double mid = (lo + hi) / 2, s = mid / 2;
            if (cornersInside(ax, az, bx, bz, nx, nz, s, cx, cz, R)) lo = mid; else hi = mid;
        }
        return lo;
    }

    public static boolean rectInside(double ax, double az, double bx, double bz, double width, double cx, double cz, double R) {
        double dx = bx - ax, dz = bz - az;
        double len = Math.hypot(dx, dz);
        if (len < 1e-9) return false;
        double nx = -dz / len, nz = dx / len;
        return cornersInside(ax, az, bx, bz, nx, nz, width / 2, cx, cz, R + 1e-6);
    }

    private static boolean cornersInside(double ax, double az, double bx, double bz, double nx, double nz, double s,
                                         double cx, double cz, double R) {
        return Math.hypot(ax + nx * s - cx, az + nz * s - cz) <= R
                && Math.hypot(ax - nx * s - cx, az - nz * s - cz) <= R
                && Math.hypot(bx + nx * s - cx, bz + nz * s - cz) <= R
                && Math.hypot(bx - nx * s - cx, bz - nz * s - cz) <= R;
    }

    /** The four corners of rectangle A->B with the given width: {x0,z0, x1,z1, x2,z2, x3,z3}. */
    public static double[] rectCorners(double ax, double az, double bx, double bz, double width) {
        double dx = bx - ax, dz = bz - az;
        double len = Math.hypot(dx, dz);
        if (len < 1e-9) return new double[]{ax, az, ax, az, ax, az, ax, az};
        double nx = -dz / len * width / 2, nz = dx / len * width / 2;
        return new double[]{ax + nx, az + nz, bx + nx, bz + nz, bx - nx, bz - nz, ax - nx, az - nz};
    }
}
