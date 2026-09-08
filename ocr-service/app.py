from functools import lru_cache
import os
import tempfile

import fitz
from fastapi import FastAPI, File, HTTPException, UploadFile
from paddleocr import PaddleOCR

app = FastAPI(title="HR Agent PaddleOCR")


@lru_cache(maxsize=1)
def engine() -> PaddleOCR:
    return PaddleOCR(use_doc_orientation_classify=False, use_doc_unwarping=False,
                     use_textline_orientation=False, lang="ch")


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/ocr")
async def ocr(file: UploadFile = File(...)):
    suffix = os.path.splitext(file.filename or "upload.pdf")[1] or ".pdf"
    path = ""
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as target:
            path = target.name
            target.write(await file.read())
        images = []
        if suffix.lower() == ".pdf":
            pdf = fitz.open(path)
            for page in pdf:
                pix = page.get_pixmap(matrix=fitz.Matrix(2, 2))
                image_path = f"{path}-{page.number}.png"
                pix.save(image_path)
                images.append(image_path)
        else:
            images.append(path)
        lines = []
        for image_path in images:
            for result in engine().predict(image_path):
                data = result.json.get("res", result.json)
                lines.extend(data.get("rec_texts", []))
        return {"text": "\n".join(lines)}
    except Exception as exc:
        raise HTTPException(status_code=422, detail="OCR_FAILED") from exc
    finally:
        if path and os.path.exists(path):
            os.remove(path)
        for candidate in list(locals().get("images", [])):
            if candidate != path and os.path.exists(candidate):
                os.remove(candidate)
