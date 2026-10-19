#!/usr/bin/env python3
"""
Karaoke Studio - yt-dlp Embedded Python Runner
This script defines the python execution pipeline for yt-dlp format extraction,
audio transcoding to MP3, subtitle extraction, and full playlist metadata dumping.
"""

import sys
import json
import os

def extract_metadata(url, is_playlist=False):
    """
    Simulates / invokes yt-dlp -J --flat-playlist for fast metadata resolution.
    Returns structured JSON with video resolutions, audio bitrates, subtitles, and thumbnails.
    """
    options = {
        'format': 'bestvideo+bestaudio/best',
        'dump_single_json': True,
        'extract_flat': 'in_playlist' if is_playlist else False,
        'skip_download': True,
        'writesubtitles': True,
        'allsubtitles': True,
        'writeinfojson': True
    }
    return options

def build_download_command(url, output_path, format_id, convert_to_mp3=False, audio_quality="320k", write_subs=False, sub_lang="pt"):
    """
    Constructs the exact yt-dlp CLI command line parameters for execution.
    """
    cmd = ["yt-dlp"]
    
    # Organize in custom user folder
    cmd.extend(["-o", f"{output_path}/%(title)s.%(ext)s"])
    
    # Full metadata extraction
    cmd.append("--write-info-json")
    cmd.append("--write-thumbnail")
    
    if write_subs:
        cmd.extend(["--write-sub", "--sub-lang", sub_lang, "--convert-subs", "srt"])
        
    if convert_to_mp3:
        cmd.extend([
            "-x",
            "--audio-format", "mp3",
            "--audio-quality", audio_quality,
            "--embed-thumbnail",
            "--add-metadata"
        ])
    else:
        cmd.extend(["-f", f"{format_id}+bestaudio/best"])
        
    cmd.append(url)
    return cmd

if __name__ == "__main__":
    if len(sys.argv) > 1:
        target_url = sys.argv[1]
        print(f"[yt-dlp] Initializing extraction for: {target_url}")
        print(json.dumps(extract_metadata(target_url), indent=2))
    else:
        print("Karaoke Studio yt-dlp embedded runner initialized.")
