#!/usr/bin/env bash
# Télécharge le moteur de transcription sherpa-onnx (Android AAR, ~50 Mo, non versionné dans git).
set -euo pipefail
V="${SHERPA_VERSION:-2.0.1}"
DIR="$(cd "$(dirname "$0")/.." && pwd)/app/libs"
mkdir -p "$DIR"
F="$DIR/sherpa-onnx-$V.aar"
[ -s "$F" ] || curl -fSL -o "$F" "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$V/sherpa-onnx-$V.aar"
echo "OK: $F"
