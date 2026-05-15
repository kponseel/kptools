import { useEffect, useRef, useState } from "react";
import { fileToCompressedDataUrl, readFileAsDataUrl } from "../lib/image";

interface Props {
  onPhoto: (dataUrl: string) => void;
}

export function PhotoCapture({ onPhoto }: Props) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    return () => stopCamera();
  }, []);

  function stopCamera() {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
  }

  async function openCamera() {
    setError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 1280 } },
        audio: false,
      });
      streamRef.current = stream;
      setCameraOpen(true);
      // Wait for state to mount the video element
      requestAnimationFrame(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play().catch(() => undefined);
        }
      });
    } catch (e) {
      setError(
        e instanceof Error
          ? `Caméra inaccessible : ${e.message}`
          : "Impossible d'accéder à la caméra.",
      );
    }
  }

  function closeCamera() {
    stopCamera();
    setCameraOpen(false);
  }

  function snapshot() {
    const video = videoRef.current;
    if (!video) return;
    const w = video.videoWidth;
    const h = video.videoHeight;
    if (!w || !h) return;
    const canvas = document.createElement("canvas");
    const size = Math.min(w, h, 1024);
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    // Crop center square + mirror so preview matches result
    const sx = (w - Math.min(w, h)) / 2;
    const sy = (h - Math.min(w, h)) / 2;
    ctx.translate(canvas.width, 0);
    ctx.scale(-1, 1);
    ctx.drawImage(video, sx, sy, Math.min(w, h), Math.min(w, h), 0, 0, size, size);
    const dataUrl = canvas.toDataURL("image/jpeg", 0.92);
    closeCamera();
    onPhoto(dataUrl);
  }

  async function onFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setError(null);
    try {
      const dataUrl = file.size > 1_500_000
        ? await fileToCompressedDataUrl(file)
        : await readFileAsDataUrl(file);
      onPhoto(dataUrl);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Erreur lors de la lecture du fichier.");
    }
  }

  return (
    <div className="capture">
      {!cameraOpen && (
        <div className="capture-buttons">
          <button className="btn btn-secondary" onClick={() => fileInputRef.current?.click()}>
            <span aria-hidden>📁</span> Choisir une photo
          </button>
          <button className="btn btn-secondary" onClick={openCamera}>
            <span aria-hidden>📷</span> Prendre une photo
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            hidden
            onChange={onFileChange}
          />
        </div>
      )}

      {cameraOpen && (
        <div className="camera-stage">
          <video ref={videoRef} playsInline muted className="camera-video" />
          <div className="camera-controls">
            <button className="btn btn-ghost" onClick={closeCamera}>
              Annuler
            </button>
            <button className="btn btn-primary" onClick={snapshot}>
              📸 Capturer
            </button>
          </div>
        </div>
      )}

      {error && <p className="error">{error}</p>}
    </div>
  );
}
