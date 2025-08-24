import os
import sys
import queue
import json
import sounddevice as sd
from vosk import Model, KaldiRecognizer

# === 1. Путь к модели ===
MODEL_PATH = r"vosk-model-small-ru-0.22"

# === 2. Проверка папки модели ===
if not os.path.exists(MODEL_PATH):
    print(f"❌ Папка модели не найдена: {MODEL_PATH}")
    sys.exit(1)

required_dirs = ["am", "conf", "graph", "ivector"]
missing = [d for d in required_dirs if not os.path.exists(os.path.join(MODEL_PATH, d))]
if missing:
    print(f"❌ В папке {MODEL_PATH} не хватает директорий: {missing}")
    sys.exit(1)

print("✅ Модель найдена, загружаем...")

# === 3. Загружаем модель ===
model = Model(MODEL_PATH)
rec = KaldiRecognizer(model, 16000)

# Очередь для звука
q = queue.Queue()

# === 4. Функция для записи текста в файл ===
def save_text(text, filename="recognized.txt"):
    """Сохраняет распознанный текст в файл"""
    with open(filename, "a", encoding="utf-8") as f:
        f.write(text + "\n")   # добавляем новую строку


# === 5. Callback для микрофона ===
def callback(indata, frames, time, status):
    if status:
        print(status, file=sys.stderr)
    q.put(bytes(indata))


# === 6. Основной цикл ===
with sd.RawInputStream(samplerate=16000, blocksize=8000, dtype='int16',
                       channels=1, callback=callback):
    print("🎤 Говорите что-нибудь... (Ctrl+C для выхода)")
    while True:
        data = q.get()
        if rec.AcceptWaveform(data):
            result = json.loads(rec.Result())
            text = result.get("text", "")
            if text:
                print("➡️ Итог:", text)
                save_text(text)   # сохраняем в файл
        else:
            partial = json.loads(rec.PartialResult())
            if partial.get("partial"):
                print("...промежуточно:", partial["partial"])
