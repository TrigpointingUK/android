#!/usr/bin/env python3
"""
OSM Tile Downloader for TrigpointingUK Leaflet Maps
===================================================

Downloads web map tiles for offline use with Leaflet maps. Multiple tile
providers are supported via `--provider` or a custom `--tile-url-template`.
Supports resumable downloads, zoom level ranges, and configurable rate limiting.
ONLY downloads tiles that intersect with the UK (including Northern Ireland).

Usage:
    python tile_downloader.py --provider osm --min-zoom 0 --max-zoom 10 --start-tile 0 --limit 1000

Features:
- Downloads tiles in Leaflet-friendly z/x/y.png directory structure, namespaced by provider
- UK-specific bounding box (-8.5°W to 2.0°E, 49.5°N to 61.0°N)
- Optimized for Leaflet WebView cache integration
- Resumable downloads (skips existing tiles)
- Configurable rate limiting with random delays
- Progress tracking and statistics
- Web server ready directory structure for ZIP distribution
- Dramatically reduced download size vs. worldwide tiles
"""

import os
import sys
import time
import random
import argparse
import requests
import math
from pathlib import Path
from urllib.parse import urlparse
from typing import Generator, Tuple, Optional, Dict
import logging

# Providers
# Note: Only Web Mercator (EPSG:3857) providers are included here.
# 27700/OSGB providers require different tiling math and are not supported by this downloader yet.
PROVIDERS: Dict[str, Dict[str, str]] = {
    "osm": {
        "name": "OpenStreetMap",
        "slug": "tile.openstreetmap.org",
        "url": "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    },
    "stamen-toner": {
        "name": "Stamen Toner",
        "slug": "StamenToner",
        "url": "https://stamen-tiles.a.ssl.fastly.net/toner/{z}/{x}/{y}.png",
    },
    "stamen-terrain": {
        "name": "Stamen Terrain",
        "slug": "StamenTerrain",
        "url": "https://stamen-tiles.a.ssl.fastly.net/terrain/{z}/{x}/{y}.png",
    },
    "carto-positron": {
        "name": "Carto Positron",
        "slug": "CartoPositron",
        "url": "https://cartodb-basemaps-a.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png",
    },
    "carto-darkmatter": {
        "name": "Carto DarkMatter",
        "slug": "CartoDarkMatter",
        "url": "https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/{z}/{x}/{y}.png",
    },
    "os-outdoor-3857": {
        "name": "OS Outdoor (3857)",
        "slug": "api.os.uk/maps/raster/v1/zxy/Outdoor_3857",
        "url": "https://api.os.uk/maps/raster/v1/zxy/Outdoor_3857/{z}/{x}/{y}.png?key={key}",
    },
    "esri-satellite": {
        "name": "ESRI World Imagery",
        "slug": "server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer",
        "url": "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
    },
}

# Configuration
USER_AGENT = "TrigpointingUK Tile Downloader (https://github.com/trigpointinguk/android)"
TILES_DIR = "map_tiles"
LOG_FILE = "download.log"

# UK bounding box (approximate, includes Northern Ireland and surrounding islands)
# Coordinates: [West, South, East, North] in degrees
UK_BBOX = [-8.5, 49.5, 2.0, 61.0]  # Generous bounds covering all UK territories

# Rate limiting
DEFAULT_MIN_DELAY = 0.5  # Minimum delay between requests (seconds)
DEFAULT_MAX_DELAY = 2.0  # Maximum delay between requests (seconds)

class OSMTileDownloader:
    def __init__(self, tiles_dir: str = TILES_DIR, min_delay: float = DEFAULT_MIN_DELAY,
                 max_delay: float = DEFAULT_MAX_DELAY, provider: str = "osm",
                 tile_url_template: Optional[str] = None, provider_slug: Optional[str] = None,
                 api_key: Optional[str] = None):
        self.tiles_dir = Path(tiles_dir)
        self.min_delay = min_delay
        self.max_delay = max_delay
        self.session = requests.Session()
        self.session.headers.update({'User-Agent': USER_AGENT})

        # Resolve provider configuration
        self.provider_key = provider
        self.provider_name = PROVIDERS.get(provider, {}).get("name", provider)
        default_template = PROVIDERS.get(provider, {}).get("url")
        self.tile_url_template = tile_url_template or default_template
        if not self.tile_url_template:
            raise ValueError(f"No tile URL template available for provider '{provider}'. "
                             f"Specify --tile-url-template explicitly.")

        # Store API key separately (don't inject into template to avoid logging secrets)
        self.api_key = None
        if "{key}" in self.tile_url_template:
            if not api_key:
                raise ValueError("This provider requires an API key. Supply --api-key.")
            self.api_key = api_key

        # Provider directory slug
        if provider_slug:
            self.provider_slug = provider_slug
        elif provider in PROVIDERS:
            self.provider_slug = PROVIDERS[provider]["slug"]
        else:
            # Derive a slug from the hostname or provider key
            try:
                host = urlparse(self.tile_url_template).hostname or provider
            except Exception:
                host = provider
            self.provider_slug = host.replace('.', '_')

        # Create base/provider directory
        (self.tiles_dir / self.provider_slug).mkdir(parents=True, exist_ok=True)

        # Setup logging
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s - %(levelname)s - %(message)s',
            handlers=[
                logging.FileHandler(LOG_FILE),
                logging.StreamHandler()
            ]
        )
        self.logger = logging.getLogger(__name__)

        self.logger.info(f"Using provider: {self.provider_name} [{self.provider_key}]")
        self.logger.info(f"Provider slug: {self.provider_slug}")
        self.logger.info(f"Tile URL template: {self.tile_url_template}")

        # Statistics
        self.downloaded_count = 0
        self.skipped_count = 0
        self.error_count = 0
        
    def deg2num(self, lat: float, lon: float, zoom: int) -> Tuple[int, int]:
        """Convert lat/lon coordinates to tile numbers."""
        lat_rad = math.radians(lat)
        n = 2.0 ** zoom
        x = int((lon + 180.0) / 360.0 * n)
        y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
        return (x, y)
    
    def num2deg(self, x: int, y: int, zoom: int) -> Tuple[float, float]:
        """Convert tile numbers to lat/lon coordinates (northwest corner)."""
        n = 2.0 ** zoom
        lon_deg = x / n * 360.0 - 180.0
        lat_rad = math.atan(math.sinh(math.pi * (1 - 2 * y / n)))
        lat_deg = math.degrees(lat_rad)
        return (lat_deg, lon_deg)
    
    def get_uk_tile_bounds(self, zoom: int) -> Tuple[int, int, int, int]:
        """Get tile coordinate bounds for UK at given zoom level."""
        west, south, east, north = UK_BBOX
        
        # Convert bounding box to tile coordinates
        x_min, y_max = self.deg2num(north, west, zoom)  # NW corner
        x_max, y_min = self.deg2num(south, east, zoom)  # SE corner
        
        # Ensure bounds are within valid tile range and properly ordered
        max_tile = 2 ** zoom - 1
        x_min = max(0, min(x_min, max_tile))
        x_max = max(0, min(x_max, max_tile))
        y_min = max(0, min(y_min, max_tile))
        y_max = max(0, min(y_max, max_tile))
        
        # Ensure min <= max (fix ordering if needed)
        if x_min > x_max:
            x_min, x_max = x_max, x_min
        if y_min > y_max:
            y_min, y_max = y_max, y_min
        
        return (x_min, y_min, x_max, y_max)
    
    def num_tiles_at_zoom(self, zoom: int) -> int:
        """Calculate total number of UK tiles at a given zoom level."""
        x_min, y_min, x_max, y_max = self.get_uk_tile_bounds(zoom)
        return (x_max - x_min + 1) * (y_max - y_min + 1)
    
    def tiles_for_zoom_range(self, min_zoom: int, max_zoom: int) -> int:
        """Calculate total number of tiles in zoom range."""
        total = 0
        for z in range(min_zoom, max_zoom + 1):
            total += self.num_tiles_at_zoom(z)
        return total
    
    def tile_coordinates_generator(self, min_zoom: int, max_zoom: int, 
                                  start_tile: int = 0) -> Generator[Tuple[int, int, int], None, None]:
        """
        Generate tile coordinates (z, x, y) for UK region only.
        
        Args:
            min_zoom: Minimum zoom level
            max_zoom: Maximum zoom level
            start_tile: Starting tile number in the sequence (for resuming)
        
        Yields:
            Tuple of (z, x, y) coordinates for tiles that intersect UK
        """
        tile_count = 0
        
        for z in range(min_zoom, max_zoom + 1):
            # Get UK-specific bounds for this zoom level
            x_min, y_min, x_max, y_max = self.get_uk_tile_bounds(z)
            
            self.logger.info(f"Zoom {z}: UK tiles x={x_min}-{x_max}, y={y_min}-{y_max} "
                           f"({(x_max-x_min+1)*(y_max-y_min+1)} tiles)")
            
            for x in range(x_min, x_max + 1):
                for y in range(y_min, y_max + 1):
                    if tile_count >= start_tile:
                        yield (z, x, y)
                    tile_count += 1
    
    def get_tile_path(self, z: int, x: int, y: int) -> Path:
        """Get the local file path for a tile (namespaced by provider)."""
        return self.tiles_dir / self.provider_slug / str(z) / str(x) / f"{y}.png"
    
    def tile_exists(self, z: int, x: int, y: int) -> bool:
        """Check if a tile already exists locally."""
        tile_path = self.get_tile_path(z, x, y)
        return tile_path.exists() and tile_path.stat().st_size > 0
    
    def download_tile(self, z: int, x: int, y: int) -> tuple[bool, bool]:
        """
        Download a single tile.
        
        Returns:
            Tuple of (success, actually_downloaded)
            - success: True if tile is available (downloaded or already exists)
            - actually_downloaded: True only if tile was downloaded from server
        """
        if self.tile_exists(z, x, y):
            self.skipped_count += 1
            return True, False  # Success but not downloaded
        
        url = self.tile_url_template.format(z=z, x=x, y=y)
        # Inject API key at request time (not stored in template to avoid logging secrets)
        if self.api_key:
            url = url.replace("{key}", self.api_key)
        tile_path = self.get_tile_path(z, x, y)
        
        try:
            # Create directory structure
            tile_path.parent.mkdir(parents=True, exist_ok=True)
            
            # Download tile
            response = self.session.get(url, timeout=30)
            response.raise_for_status()
            
            # Save tile
            with open(tile_path, 'wb') as f:
                f.write(response.content)
            
            self.downloaded_count += 1
            self.logger.debug(f"Downloaded tile {z}/{x}/{y}")
            return True, True  # Success and downloaded
            
        except requests.exceptions.RequestException as e:
            self.error_count += 1
            self.logger.error(f"Failed to download tile {z}/{x}/{y}: {e}")
            
            # Clean up partial file
            if tile_path.exists():
                tile_path.unlink()
            
            return False, False  # Failed
    
    def random_delay(self):
        """Add a random delay between requests."""
        delay = random.uniform(self.min_delay, self.max_delay)
        time.sleep(delay)
    
    def download_tiles(self, min_zoom: int, max_zoom: int, start_tile: int = 0, 
                      limit: int = None) -> None:
        """
        Download tiles in the specified range.
        
        Args:
            min_zoom: Minimum zoom level
            max_zoom: Maximum zoom level
            start_tile: Starting tile number (for resuming)
            limit: Maximum number of tiles to download (None for unlimited)
        """
        total_tiles = self.tiles_for_zoom_range(min_zoom, max_zoom)
        
        self.logger.info(f"Starting download: provider '{self.provider_name}' zoom {min_zoom}-{max_zoom} (UK region only)")
        self.logger.info(f"UK bounding box: {UK_BBOX[0]}°W to {UK_BBOX[2]}°E, {UK_BBOX[1]}°N to {UK_BBOX[3]}°N")
        self.logger.info(f"Total UK tiles in range: {total_tiles:,}")
        if start_tile > 0:
            self.logger.info(f"Starting from tile #{start_tile:,}")
        if limit:
            self.logger.info(f"Download limit: {limit:,} tiles")
        
        downloaded_this_session = 0
        start_time = time.time()
        
        try:
            for z, x, y in self.tile_coordinates_generator(min_zoom, max_zoom, start_tile):
                # Check download limit
                if limit and downloaded_this_session >= limit:
                    self.logger.info(f"Reached download limit of {limit:,} tiles")
                    break
                
                # Download tile
                success, actually_downloaded = self.download_tile(z, x, y)
                if actually_downloaded:
                    downloaded_this_session += 1
                
                # Progress reporting (every 50 tiles processed or every actual download)
                total_processed = self.downloaded_count + self.skipped_count + self.error_count
                if total_processed % 50 == 0 or actually_downloaded:
                    elapsed = time.time() - start_time
                    rate = self.downloaded_count / elapsed if elapsed > 0 else 0
                    
                    progress_msg = (
                        f"Progress: {total_processed:,} processed "
                        f"(↓{self.downloaded_count:,} ⏭{self.skipped_count:,} "
                        f"✗{self.error_count:,}) "
                        f"[{rate:.1f} downloads/sec]"
                    )
                    
                    if actually_downloaded:
                        progress_msg += f" → z{z}/{x}/{y}.png"
                    
                    self.logger.info(progress_msg)
                
                # Rate limiting - only delay after actual downloads from server
                if actually_downloaded:
                    self.random_delay()
                
        except KeyboardInterrupt:
            self.logger.info("Download interrupted by user")
        
        # Final statistics
        total_time = time.time() - start_time
        avg_rate = self.downloaded_count / total_time if total_time > 0 else 0
        
        self.logger.info("Download session complete:")
        self.logger.info(f"  Downloaded: {self.downloaded_count:,} tiles")
        self.logger.info(f"  Skipped (existing): {self.skipped_count:,} tiles")
        self.logger.info(f"  Errors: {self.error_count:,} tiles")
        self.logger.info(f"  Total processed: {self.downloaded_count + self.skipped_count + self.error_count:,} tiles")
        self.logger.info(f"  Session time: {total_time:.1f} seconds")
        self.logger.info(f"  Average download rate: {avg_rate:.2f} tiles/second")
        
        if self.downloaded_count > 0:
            estimated_size_mb = self.downloaded_count * 15 / 1024  # Estimate 15KB per tile
            self.logger.info(f"  Estimated data downloaded: {estimated_size_mb:.1f} MB")
    
    def get_stats(self) -> dict:
        """Get download statistics."""
        stats: Dict[str, Dict[int, int]] = {}

        # Look for provider subdirectories. If none, fall back to legacy structure.
        provider_dirs = [d for d in self.tiles_dir.iterdir() if d.is_dir() and not d.name.isdigit()]

        def count_zoom_levels(base_dir: Path) -> Dict[int, int]:
            by_zoom: Dict[int, int] = {}
            for zoom_dir in base_dir.iterdir():
                if zoom_dir.is_dir() and zoom_dir.name.isdigit():
                    zoom = int(zoom_dir.name)
                    tile_count = 0
                    for x_dir in zoom_dir.iterdir():
                        if x_dir.is_dir() and x_dir.name.isdigit():
                            tile_count += len([
                                f for f in x_dir.iterdir()
                                if f.suffix == '.png' and f.stat().st_size > 0
                            ])
                    by_zoom[zoom] = tile_count
            return by_zoom

        if provider_dirs:
            for pdir in provider_dirs:
                stats[pdir.name] = count_zoom_levels(pdir)
        else:
            # Legacy: tiles directly under tiles_dir
            stats[self.provider_slug] = count_zoom_levels(self.tiles_dir)

        return stats

def main():
    parser = argparse.ArgumentParser(
        description="Download OSM tiles for offline use",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Download OSM zoom levels 0-5
  python tile_downloader.py --provider osm --min-zoom 0 --max-zoom 5
  
  # Resume download starting from tile 1000, download 500 more tiles
  python tile_downloader.py --provider osm --min-zoom 0 --max-zoom 10 --start-tile 1000 --limit 500
  
  # Download with custom rate limiting
  python tile_downloader.py --provider stamen-toner --min-zoom 8 --max-zoom 12 --min-delay 1.0 --max-delay 3.0
  
  # Download from a custom template (overrides provider URL)
  python tile_downloader.py --provider mytiles --tile-url-template "https://example.com/{z}/{x}/{y}.png"
  
  # Show statistics only
  python tile_downloader.py --stats
        """
    )
    
    parser.add_argument('--provider', default='osm', choices=sorted(list(PROVIDERS.keys())) + ['custom'],
                       help='Tile provider to use (default: osm). Use custom with --tile-url-template for arbitrary servers')
    parser.add_argument('--tile-url-template', default=None,
                       help='Custom tile URL template, e.g. https://server/{z}/{x}/{y}.png')
    parser.add_argument('--provider-slug', default=None,
                       help='Directory name to namespace this provider (default: derived from provider/template)')
    parser.add_argument('--api-key', default=None,
                       help='API key, if required by provider template (use {key} placeholder in template)')
    parser.add_argument('--min-zoom', type=int, default=0,
                       help='Minimum zoom level (default: 0)')
    parser.add_argument('--max-zoom', type=int, default=10,
                       help='Maximum zoom level (default: 10)')
    parser.add_argument('--start-tile', type=int, default=0,
                       help='Starting tile number for resuming (default: 0)')
    parser.add_argument('--limit', type=int, default=None,
                       help='Maximum number of tiles to download (default: unlimited)')
    parser.add_argument('--min-delay', type=float, default=DEFAULT_MIN_DELAY,
                       help=f'Minimum delay between requests in seconds (default: {DEFAULT_MIN_DELAY})')
    parser.add_argument('--max-delay', type=float, default=DEFAULT_MAX_DELAY,
                       help=f'Maximum delay between requests in seconds (default: {DEFAULT_MAX_DELAY})')
    parser.add_argument('--tiles-dir', default=TILES_DIR,
                       help=f'Directory to store tiles (default: {TILES_DIR})')
    parser.add_argument('--stats', action='store_true',
                       help='Show download statistics and exit')
    
    args = parser.parse_args()
    
    # Create downloader
    downloader = OSMTileDownloader(
        tiles_dir=args.tiles_dir,
        min_delay=args.min_delay,
        max_delay=args.max_delay,
        provider=args.provider,
        tile_url_template=args.tile_url_template,
        provider_slug=args.provider_slug,
        api_key=args.api_key,
    )
    
    if args.stats:
        # Show statistics
        all_stats = downloader.get_stats()
        if all_stats:
            print("Current tile statistics (by provider):")
            grand_total = 0
            for provider_slug, stats in all_stats.items():
                print(f"\nProvider: {provider_slug}")
                total_tiles = 0
                for zoom in sorted(stats.keys()):
                    count = stats[zoom]
                    total_tiles += count
                    max_tiles = downloader.num_tiles_at_zoom(zoom)
                    percentage = (count / max_tiles) * 100 if max_tiles > 0 else 0
                    print(f"  Zoom {zoom:2d}: {count:8,} / {max_tiles:8,} tiles ({percentage:5.1f}%)")
                print(f"  Total:    {total_tiles:8,} tiles")
                grand_total += total_tiles
            print(f"\nGrand total across providers: {grand_total:,} tiles")
        else:
            print("No tiles found in tiles directory")
        return
    
    # Validate arguments
    if args.min_zoom < 0 or args.max_zoom < 0:
        print("Error: Zoom levels must be non-negative")
        sys.exit(1)
    
    if args.min_zoom > args.max_zoom:
        print("Error: min-zoom must be <= max-zoom")
        sys.exit(1)
    
    if args.start_tile < 0:
        print("Error: start-tile must be non-negative")
        sys.exit(1)
    
    if args.limit is not None and args.limit <= 0:
        print("Error: limit must be positive")
        sys.exit(1)
    
    # Start download
    downloader.download_tiles(
        min_zoom=args.min_zoom,
        max_zoom=args.max_zoom,
        start_tile=args.start_tile,
        limit=args.limit
    )

if __name__ == '__main__':
    main()
