# kpocrm

- `/` : **On regarde quoi ?** : le meilleur de Netflix, Prime Video et Disney+.
- `/vocal/` : **Vocaux** : transcription directement sur le téléphone, avec Whisper (transformers.js), des vocaux WhatsApp et de presque tous les fichiers audio ou vidéo : m4a (Enregistreur Google), mp3, wav, flac, ogg/opus, webm, amr, 3gp, wma, aiff, mp4, mov… Le navigateur lit les formats courants. Les formats rares passent par ffmpeg.wasm, téléchargé seulement si besoin. Une fois installée sur Android, l'app apparaît dans « Partager » de WhatsApp. Modèles au choix : Whisper tiny, base, small ou large-v3 turbo, et Cohere Transcribe (GPU requis). La correction et le résumé par Claude Haiku 5.5 sont optionnels : il faut ajouter sa clé API dans les réglages.
