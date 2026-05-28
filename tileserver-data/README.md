# Tile Server Data

This directory is used for building a custom `tileserver-gl` image that includes Japan OSM data.

## Important Note about MBTiles

The MBTiles data file (`osm-2020-02-10-v3.11_asia_japan.mbtiles`) is **excluded** from this repository due to:

- **License constraints**: The data distribution is limited by its original license.
- **File size**: The file is approximately 2GB, which is unsuitable for standard Git storage.

## How to build and push

1. Place the `osm-2020-02-10-v3.11_asia_japan.mbtiles` file in this directory.
2. Build the image:
   ```bash
   docker build -t tanuki-tile-server .
   ```
3. Authenticate to Google Artifact Registry:
   ```bash
   gcloud auth configure-docker asia-northeast1-docker.pkg.dev
   ```
4. Tag and push to the repository:
   ```bash
   docker tag tanuki-tile-server asia-northeast1-docker.pkg.dev/tanuki-dev/tanuki-back-repo/tanuki-tile-server:latest
   docker push asia-northeast1-docker.pkg.dev/tanuki-dev/tanuki-back-repo/tanuki-tile-server:latest
   ```

## Runtime Configuration

The image is designed to work with the `TILESERVER_GL_CONFIG_JSON` environment variable.
