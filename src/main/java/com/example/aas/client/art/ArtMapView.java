package com.example.aas.client.art;

/** Snapshot of the map transform of the current frame (where the map is on the screen and what it shows). */
public final class ArtMapView {
    public final int mapX, mapY, mapSize;
    public final double panX, panZ, bpp;

    public ArtMapView(int mapX, int mapY, int mapSize, double panX, double panZ, double bpp) {
        this.mapX = mapX; this.mapY = mapY; this.mapSize = mapSize;
        this.panX = panX; this.panZ = panZ; this.bpp = bpp;
    }

    public double worldX(double sx) { return panX + (sx - (mapX + mapSize / 2.0)) * bpp; }
    public double worldZ(double sy) { return panZ + (sy - (mapY + mapSize / 2.0)) * bpp; }
    public float screenX(double wx) { return (float) (mapX + mapSize / 2.0 + (wx - panX) / bpp); }
    public float screenY(double wz) { return (float) (mapY + mapSize / 2.0 + (wz - panZ) / bpp); }

    public boolean inside(double sx, double sy) {
        return sx >= mapX && sx <= mapX + mapSize && sy >= mapY && sy <= mapY + mapSize;
    }
}
