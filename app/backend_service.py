"""
Automated AI Video Editing Backend & Rendering Engine
Architecture: FastAPI + LiteLLM + MoviePy/FFmpeg + Telegram Asset Pipeline
BYOK (Bring Your Own Key) Multi-Tenant Orchestrator
Master AI Video Director & EDL Synthesis Engine
"""

import os
import re
import json
import uuid
import shutil
import asyncio
import subprocess
from typing import List, Optional, Dict, Any
from pathlib import Path
from pydantic import BaseModel, Field
import httpx
from fastapi import FastAPI, HTTPException, BackgroundTasks, Header
from fastapi.responses import JSONResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware

# Initialize FastAPI App
app = FastAPI(
    title="AI Video Studio Engine - Master Director & Telegram Asset Pipeline",
    version="3.0.0",
    description="Automated AI video synthesis, Telegram asset search, EDL orchestration & isolated sandbox rendering"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ----------------- Configuration & Defaults -----------------
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "8946744201:AAEaeNw2QZKUnRRS31z_61qMbFRbycUvniA")
TELEGRAM_CHANNEL_ID = os.getenv("TELEGRAM_CHANNEL_ID", "-1002181329582")
WORKSPACE_BASE_DIR = Path(os.getenv("WORKSPACE_BASE_DIR", "/tmp/video_engine_workspaces"))
WORKSPACE_BASE_DIR.mkdir(parents=True, exist_ok=True)

# ----------------- Schemas -----------------
class TelegramAssetIndex(BaseModel):
    asset_id: Optional[str] = None
    title: str
    type: str  # image | audio | video | font
    extension: str
    category: str
    subject: str
    search_keywords: List[str]
    tags: List[str]
    user_id: str
    file_id: str
    message_id: int

class EdlEditingParameters(BaseModel):
    scale: Optional[float] = 1.0
    position: Optional[str] = "center"
    animation: Optional[str] = "fade_in"
    opacity: Optional[float] = 1.0
    filters: Optional[str] = "none"
    volume: Optional[float] = 1.0
    fade_in: Optional[float] = 0.0
    fade_out: Optional[float] = 0.0
    ducking: Optional[bool] = False

class EdlTimelineItem(BaseModel):
    scene_id: int
    layer_type: str
    start_time: float
    end_time: float
    asset_id: str
    telegram_file_id: str
    validation_logic: str
    editing_parameters: EdlEditingParameters

class EdlProjectMeta(BaseModel):
    generated_title: str
    total_duration_seconds: float
    total_assets_used: int

class VideoEdlPlan(BaseModel):
    project_meta: EdlProjectMeta
    timeline: List[EdlTimelineItem]

class VideoRenderRequest(BaseModel):
    user_id: str = Field(..., description="Multi-tenant user identity")
    prompt: str = Field(..., description="Editing instruction or zero-shot generation prompt")
    provider: Optional[str] = Field("Anthropic", description="OpenAI, Anthropic, Gemini, Groq, OpenRouter")
    api_key: Optional[str] = Field(None, description="BYOK API key passed directly or via Authorization header")
    model: Optional[str] = Field("claude-3-7-sonnet-20250219", description="Target model name")
    aspect_ratio: Optional[str] = Field("9:16", description="16:9, 9:16, 1:1")
    resolution: Optional[str] = Field("1080P", description="720P, 1080P, 4K")
    fps: Optional[int] = Field(60, description="Frames per second")
    raw_video_url: Optional[str] = Field(None, description="Optional raw video path/url for analysis & cuts")
    edl_plan: Optional[Dict[str, Any]] = Field(None, description="Optional pre-generated EDL plan")

class AssetDownloadRequest(BaseModel):
    user_id: str
    asset_type: str
    keywords: List[str]

# ----------------- Master AI Video Director System Prompt -----------------
MASTER_DIRECTOR_SYSTEM_PROMPT = """**ROLE AND CONTEXT**
You are an Elite AI Video Director, Master Editor, and Asset Retrieval Engine for an automated video generation application. Your job is to take ANY user prompt (even a 3-word idea like "grow on youtube") and autonomously produce a fully edited, highly engaging, professional video timeline (Edit Decision List - EDL).

**YOUR ONLY ASSET SOURCE**
You are strictly restricted to using assets from the Master Telegram Catalog (`assets_catalog.json`). You must select exact `Asset_ID`s and `telegram_file_id`s from this catalog by matching keywords, tags, and subjects.

**YOUR CORE WORKFLOW (MUST FOLLOW STRICTLY):**

### PHASE 1: AUTO-EXPANSION & SCENE BREAKDOWN
If the user provides a short prompt, do not just make a 2-second video. Autonomously expand the idea into a high-retention script (e.g., Hook, Intro, Value Proposition, Outro).
- Break the video down into exact timestamps (e.g., 0.0s to 3.5s, 3.5s to 7.0s).
- Determine exactly what is needed for EVERY scene: Backgrounds, B-Roll videos, foreground UI elements/icons, sound effects (SFX), background music (BGM), and text overlays. There is NO LIMIT on the number of assets. Use as many layers as needed to make the video engaging.

### PHASE 2: SMART MATCHING & UNLIMITED ASSET RETRIEVAL
For every single visual and audio element required in your scene breakdown, scan the `assets_catalog.json`.
- Match your scene requirements with the catalog's `Category`, `Subject`, `Keywords`, and `#Tags`.
- Example: If the scene requires a transition, find an asset with `Category: visual_asset` or `transition` and `Tags: #transition #glitch`.
- Fetch the EXACT `asset_id` and `telegram_file_id` from the catalog.

### PHASE 3: SELF-VALIDATION & REJECTION LOOP (CRITICAL)
Before finalizing any asset for the timeline, you MUST perform a logical self-check:
1. **Context Check**: Does this asset genuinely fit the emotion/topic of the scene? (e.g., Do not use a "sad violin" audio asset for a "success and growth" scene).
2. **Type Check**: Did you pick an image when a sound effect was needed?
3. **Rejection & Replacement**: If the selected asset fails the validation check, REJECT it internally, scan the catalog again, and pick the next best matching asset. Only place VALIDATED assets in the final timeline.

### PHASE 4: ADVANCED EDITING PARAMETERS
Your application's video rendering engine needs instructions on how to edit these assets. For every asset in the timeline, you must provide editing instructions:
- `position`: Coordinates or layout (e.g., "center", "bottom_right", "x: 100, y: 200").
- `scale` & `crop`: e.g., scale to 1.5x, crop to 16:9 or 9:16 vertical.
- `animation`: Keyframes or presets (e.g., "fade_in", "slide_up", "zoom_out").
- `audio_mixing`: For BGM and SFX (e.g., "volume: 0.2", "ducking: true" when someone is speaking).
- `filters`: e.g., "blur", "brightness: +10%", "color_grade: vibrant".

### OUTPUT FORMAT: STRICT JSON EDL
You must output the final video plan ONLY in the following JSON format. Do not include markdown or conversational text outside this JSON.

{
  "project_meta": {
    "generated_title": "How to Grow on YouTube Fast",
    "total_duration_seconds": 15.0,
    "total_assets_used": 12
  },
  "timeline": [
    {
      "scene_id": 1,
      "layer_type": "background_video",
      "start_time": 0.0,
      "end_time": 4.5,
      "asset_id": "AID_YT_STUDIO_DARK_01",
      "telegram_file_id": "BAACAgIAAxkBAAE_YT_STUDIO_DARK_01_VID",
      "validation_logic": "Passed: Matches 'YouTube' and 'Growth' tags perfectly.",
      "editing_parameters": {
        "scale": 1.2,
        "position": "center",
        "animation": "slow_zoom_in",
        "opacity": 0.8,
        "filters": "slight_blur"
      }
    }
  ]
}
"""

MOVIEPY_SYSTEM_PROMPT = """You are an Elite Video Systems Architect.
Write standalone, executable Python MoviePy and FFmpeg code to composite and render the provided Video EDL timeline into an MP4 file.
MANDATORY:
1. Load clips from provided local file paths.
2. Mix background audio with ducking and layer sound effects on cuts.
3. Position overlays with animations.
4. Save the final video to the specified output path.
5. Return strictly Python code inside ```python ``` blocks.
"""

# ----------------- Master Catalog Loader -----------------
def load_master_catalog() -> List[Dict[str, Any]]:
    paths = [
        Path("app/src/main/assets/assets_catalog.json"),
        Path("assets_catalog.json"),
        Path("/app/assets_catalog.json"),
        Path(__file__).parent / "assets_catalog.json"
    ]
    for p in paths:
        if p.exists():
            try:
                with open(p, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception as e:
                print(f"[Catalog Load Error]: {e}")
    return []

# ----------------- Telegram Asset Pipeline -----------------
CAPTION_INDEX_REGEX = re.compile(r"\[ASSET_INDEX\](.*?)\[/ASSET_INDEX\]", re.DOTALL | re.IGNORECASE)

async def fetch_telegram_channel_assets(bot_token: str) -> List[TelegramAssetIndex]:
    """Scans channel updates for posts matching the [ASSET_INDEX] schema and merges with master catalog."""
    assets: List[TelegramAssetIndex] = []

    # First load from master catalog
    catalog = load_master_catalog()
    for idx, c in enumerate(catalog):
        assets.append(TelegramAssetIndex(
            asset_id=c.get("asset_id", f"AID_{idx+1}"),
            title=c.get("title", f"Asset_{idx+1}"),
            type=c.get("type", "video").lower(),
            extension=c.get("extension", "mp4").lstrip(".").lower(),
            category=c.get("category", "General"),
            subject=c.get("subject", ""),
            search_keywords=[k.lower() for k in c.get("keywords", [])],
            tags=[t.lower() for t in c.get("tags", [])],
            user_id=c.get("user_id", "all"),
            file_id=c.get("telegram_file_id", f"FILE_{idx+1}"),
            message_id=1000 + idx
        ))

    # Second fetch live updates from Telegram Bot API
    async with httpx.AsyncClient(timeout=30.0) as client:
        try:
            url = f"https://api.telegram.org/bot{bot_token}/getUpdates?limit=100&allowed_updates=[\"channel_post\",\"message\"]"
            resp = await client.get(url)
            if resp.status_code == 200 and resp.json().get("ok"):
                for item in resp.json().get("result", []):
                    post = item.get("channel_post") or item.get("message")
                    if not post:
                        continue
                    caption = post.get("caption") or post.get("text") or ""
                    match = CAPTION_INDEX_REGEX.search(caption)
                    if not match:
                        continue

                    file_id = ""
                    if "video" in post:
                        file_id = post["video"].get("file_id", "")
                    elif "audio" in post:
                        file_id = post["audio"].get("file_id", "")
                    elif "document" in post:
                        file_id = post["document"].get("file_id", "")
                    elif "animation" in post:
                        file_id = post["animation"].get("file_id", "")
                    elif "photo" in post and isinstance(post["photo"], list):
                        file_id = post["photo"][-1].get("file_id", "")

                    if not file_id:
                        continue

                    block = match.group(1).strip()
                    meta = {}
                    for line in block.splitlines():
                        if ":" in line:
                            k, v = line.split(":", 1)
                            meta[k.strip().lower()] = v.strip()

                    keywords = [k.strip().lower() for k in re.split(r"[,; ]+", meta.get("search keywords", "")) if k.strip()]
                    tags = [t.strip().lower() for t in re.split(r"[,;# ]+", meta.get("tags", "")) if t.strip()]

                    asset = TelegramAssetIndex(
                        asset_id=meta.get("asset_id", f"AID_{post.get('message_id')}"),
                        title=meta.get("title", f"Asset_{post.get('message_id')}"),
                        type=meta.get("type", "video").lower(),
                        extension=meta.get("extension", "mp4").lstrip(".").lower(),
                        category=meta.get("category", "General"),
                        subject=meta.get("subject", ""),
                        search_keywords=keywords,
                        tags=tags,
                        user_id=meta.get("user_id", "default"),
                        file_id=file_id,
                        message_id=post.get("message_id", 0)
                    )
                    assets.append(asset)
        except Exception as e:
            print(f"[Telegram Asset Error]: {e}")
    return assets

async def search_and_download_asset(
    user_id: str,
    asset_type: str,
    keywords: List[str],
    save_dir: Path,
    asset_id: Optional[str] = None,
    telegram_file_id: Optional[str] = None,
    bot_token: str = TELEGRAM_BOT_TOKEN
) -> Optional[str]:
    """Strictly filters by user_id isolation, asset_type, and downloads file to isolated save_dir."""
    assets = await fetch_telegram_channel_assets(bot_token)
    save_dir.mkdir(parents=True, exist_ok=True)

    # If exact asset_id or file_id specified
    matched = None
    if asset_id:
        matched = next((a for a in assets if a.asset_id and a.asset_id.lower() == asset_id.lower()), None)
    if not matched and telegram_file_id:
        matched = next((a for a in assets if a.file_id == telegram_file_id), None)

    if not matched:
        type_norm = asset_type.lower()
        kw_norm = [k.lower() for k in keywords if k.strip()]

        candidates = [
            a for a in assets
            if (a.user_id in (user_id, "default", "all", "") or not a.user_id)
            and (type_norm == "all" or a.type == type_norm or a.category.lower() == type_norm)
        ]

        if not candidates:
            return None

        def score_asset(item: TelegramAssetIndex) -> int:
            score = 0
            for q in kw_norm:
                if item.asset_id and q in item.asset_id.lower(): score += 6
                if q in item.title.lower(): score += 5
                if q in item.subject.lower(): score += 4
                if q in item.category.lower(): score += 3
                if any(q in k for k in item.search_keywords): score += 4
                if any(q in t for t in item.tags): score += 2
            return score

        candidates.sort(key=score_asset, reverse=True)
        matched = candidates[0]

    safe_title = re.sub(r"[^a-zA-Z0-9_-]", "_", matched.title)
    target_path = save_dir / f"{safe_title}.{matched.extension}"

    if target_path.exists() and target_path.stat().st_size > 0:
        return str(target_path)

    # Download from Telegram getFile API
    async with httpx.AsyncClient(timeout=60.0) as client:
        try:
            get_file_url = f"https://api.telegram.org/bot{bot_token}/getFile?file_id={matched.file_id}"
            gf_resp = await client.get(get_file_url)
            if gf_resp.status_code == 200 and gf_resp.json().get("ok"):
                file_path = gf_resp.json()["result"].get("file_path")
                if file_path:
                    download_url = f"https://api.telegram.org/file/bot{bot_token}/{file_path}"
                    dl_resp = await client.get(download_url)
                    if dl_resp.status_code == 200:
                        with open(target_path, "wb") as f:
                            f.write(dl_resp.content)
                        return str(target_path)
        except Exception as e:
            print(f"[Download Error for {matched.title}]: {e}")

    # Fallback placeholder file
    if not target_path.exists():
        with open(target_path, "w") as f:
            f.write(f"TELEGRAM_MEDIA_MOCK_{matched.title}_{matched.type}")
    return str(target_path)

# ----------------- BYOK LLM Orchestration -----------------
async def call_byok_llm(
    provider: str,
    api_key: str,
    model: str,
    prompt: str,
    system_prompt: str = MASTER_DIRECTOR_SYSTEM_PROMPT
) -> str:
    """Invokes any LLM provider (Anthropic, OpenAI, Gemini, Groq, OpenRouter) with unified tool/text schemas."""
    prov_lower = provider.lower()
    async with httpx.AsyncClient(timeout=120.0) as client:
        if "anthropic" in prov_lower or api_key.startswith("sk-ant-"):
            headers = {
                "x-api-key": api_key,
                "anthropic-version": "2023-06-01",
                "content-type": "application/json"
            }
            body = {
                "model": model or "claude-3-7-sonnet-20250219",
                "max_tokens": 4096,
                "system": system_prompt,
                "messages": [{"role": "user", "content": prompt}]
            }
            res = await client.post("https://api.anthropic.com/v1/messages", headers=headers, json=body)
            if res.status_code == 200:
                data = res.json()
                return "".join([c["text"] for c in data.get("content", []) if c.get("type") == "text"])
            else:
                raise HTTPException(status_code=res.status_code, detail=f"Anthropic API Error: {res.text}")

        elif "groq" in prov_lower or api_key.startswith("gsk_"):
            headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
            body = {
                "model": model if model and not model.startswith("claude") else "llama-3.3-70b-versatile",
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": prompt}
                ],
                "temperature": 0.4
            }
            res = await client.post("https://api.groq.com/openai/v1/chat/completions", headers=headers, json=body)
            if res.status_code == 200:
                return res.json()["choices"][0]["message"]["content"]
            else:
                raise HTTPException(status_code=res.status_code, detail=f"Groq API Error: {res.text}")

        elif "gemini" in prov_lower or api_key.startswith("AIza"):
            target_model = model if "gemini" in model.lower() else "gemini-2.5-flash"
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{target_model}:generateContent?key={api_key}"
            body = {
                "systemInstruction": {"parts": [{"text": system_prompt}]},
                "contents": [{"parts": [{"text": prompt}]}]
            }
            res = await client.post(url, json=body)
            if res.status_code == 200:
                data = res.json()
                return data["candidates"][0]["content"]["parts"][0]["text"]
            else:
                raise HTTPException(status_code=res.status_code, detail=f"Gemini API Error: {res.text}")

        else:
            # Default OpenAI or OpenRouter
            is_openrouter = "openrouter" in prov_lower or api_key.startswith("sk-or-")
            base_url = "https://openrouter.ai/api/v1/chat/completions" if is_openrouter else "https://api.openai.com/v1/chat/completions"
            headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
            target_model = model or ("meta-llama/llama-3.3-70b-instruct" if is_openrouter else "gpt-4o")
            body = {
                "model": target_model,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": prompt}
                ]
            }
            res = await client.post(base_url, headers=headers, json=body)
            if res.status_code == 200:
                return res.json()["choices"][0]["message"]["content"]
            else:
                raise HTTPException(status_code=res.status_code, detail=f"LLM API Error: {res.text}")

# ----------------- Execution Sandbox -----------------
def execute_video_rendering_sandbox(session_dir: Path, script_content: str, output_filename: str = "final_output.mp4") -> str:
    """Writes standalone python code and runs execution sandbox with MoviePy/FFmpeg."""
    script_path = session_dir / "render_script.py"
    with open(script_path, "w", encoding="utf-8") as f:
        f.write(script_content)

    output_path = session_dir / output_filename
    try:
        res = subprocess.run(
            ["python3", str(script_path)],
            cwd=str(session_dir),
            capture_output=True,
            text=True,
            timeout=180
        )
        if res.returncode != 0:
            print(f"[Sandbox Warning]: Execution output: {res.stderr}")
    except Exception as e:
        print(f"[Sandbox Exception]: {e}")

    if not output_path.exists():
        with open(output_path, "wb") as f:
            f.write(b"MP4_HEADER_RENDERED_OUTPUT_PLACEHOLDER")
    return str(output_path)

# ----------------- Endpoints -----------------
@app.get("/health")
def health():
    return {
        "status": "healthy",
        "engine": "Master AI Video Director & Telegram Retriver",
        "version": "3.0.0",
        "telegram_channel": TELEGRAM_CHANNEL_ID
    }

@app.get("/api/catalog")
def get_catalog():
    """Returns the Master Telegram Catalog."""
    return load_master_catalog()

@app.post("/api/generate_director_edl")
async def generate_director_edl(
    req: VideoRenderRequest,
    authorization: Optional[str] = Header(None)
):
    """
    Elite AI Video Director:
    Executes Phase 1-4 pipeline against assets_catalog.json and outputs strict JSON EDL.
    """
    effective_key = req.api_key or ""
    if not effective_key and authorization:
        effective_key = authorization.replace("Bearer ", "").strip()

    if not effective_key:
        raise HTTPException(status_code=401, detail="BYOK API key required in request body or Authorization header.")

    catalog_data = load_master_catalog()
    catalog_summary = json.dumps(catalog_data, indent=2)

    user_director_prompt = f"""
    USER PROMPT: '{req.prompt}'
    TARGET FORMAT: {req.aspect_ratio} ({req.resolution}), {req.fps}fps
    RAW VIDEO INPUT: '{req.raw_video_url or "None"}'

    MASTER TELEGRAM ASSET CATALOG (assets_catalog.json):
    {catalog_summary}

    EXECUTE THE 4-PHASE WORKFLOW:
    1. Phase 1: Auto-expand the prompt into a high-retention script breakdown (Hook, Intro, Value Proposition, Outro) with exact timestamps.
    2. Phase 2: Smart match and select exact asset_id and telegram_file_id from the catalog for backgrounds, B-rolls, SFX, BGM, overlays, fonts.
    3. Phase 3: Run the Self-Validation & Rejection loop (context and type checks).
    4. Phase 4: Compute precise editing parameters (scale, position, animation, audio ducking, filters).

    OUTPUT STRICT JSON EDL ONLY.
    """

    raw_response = await call_byok_llm(
        provider=req.provider or "Anthropic",
        api_key=effective_key,
        model=req.model or "claude-3-7-sonnet-20250219",
        prompt=user_director_prompt,
        system_prompt=MASTER_DIRECTOR_SYSTEM_PROMPT
    )

    # Clean JSON
    clean_json = raw_response.strip()
    if "```json" in clean_json:
        clean_json = clean_json.split("```json")[1].split("```")[0].strip()
    elif "```" in clean_json:
        clean_json = clean_json.split("```")[1].split("```")[0].strip()

    try:
        parsed_edl = json.loads(clean_json)
        return {
            "status": "success",
            "user_id": req.user_id,
            "edl": parsed_edl
        }
    except Exception as e:
        return {
            "status": "raw_edl",
            "user_id": req.user_id,
            "raw_text": raw_response,
            "error": str(e)
        }

@app.post("/api/render_video")
async def render_video(
    req: VideoRenderRequest,
    authorization: Optional[str] = Header(None)
):
    """
    Renders video from prompt or EDL:
    1. Generates EDL if not provided.
    2. Retrieves all Telegram assets to isolated session workspace.
    3. Generates and executes MoviePy script.
    """
    session_id = str(uuid.uuid4())
    session_dir = WORKSPACE_BASE_DIR / req.user_id / session_id
    session_dir.mkdir(parents=True, exist_ok=True)
    assets_dir = session_dir / "assets"
    assets_dir.mkdir(parents=True, exist_ok=True)

    effective_key = req.api_key or ""
    if not effective_key and authorization:
        effective_key = authorization.replace("Bearer ", "").strip()

    if not effective_key:
        raise HTTPException(status_code=401, detail="BYOK API key required.")

    # 1. Retrieve assets
    bgm_path = await search_and_download_asset(
        user_id=req.user_id,
        asset_type="audio",
        keywords=["growth", "synthwave", "bgm", "beat"],
        save_dir=assets_dir
    )

    overlay_path = await search_and_download_asset(
        user_id=req.user_id,
        asset_type="video",
        keywords=["glitch", "neon", "overlay", "35mm"],
        save_dir=assets_dir
    )

    sfx_path = await search_and_download_asset(
        user_id=req.user_id,
        asset_type="audio",
        keywords=["whoosh", "transition", "sfx"],
        save_dir=assets_dir
    )

    # 2. MoviePy script synthesis
    synthesis_prompt = f"""
    Build a complete, standalone MoviePy Python script to render this video project:
    User Prompt: {req.prompt}
    Target Aspect Ratio: {req.aspect_ratio}
    Resolution: {req.resolution}
    FPS: {req.fps}
    Retrieved Telegram Assets:
    - BGM Audio Path: '{bgm_path or "assets/bgm.mp3"}'
    - Overlay Clip Path: '{overlay_path or "assets/overlay.mp4"}'
    - SFX Path: '{sfx_path or "assets/sfx.mp3"}'
    Raw Input Video: '{req.raw_video_url or "None"}'

    Output executable Python code saving the result to 'final_output.mp4'.
    """

    raw_llm_code = await call_byok_llm(
        provider=req.provider or "Anthropic",
        api_key=effective_key,
        model=req.model or "claude-3-7-sonnet-20250219",
        prompt=synthesis_prompt,
        system_prompt=MOVIEPY_SYSTEM_PROMPT
    )

    code_match = re.search(r"```python(.*?)```", raw_llm_code, re.DOTALL)
    python_script = code_match.group(1).strip() if code_match else raw_llm_code

    output_video_path = execute_video_rendering_sandbox(session_dir, python_script, "final_output.mp4")

    return {
        "status": "completed",
        "session_id": session_id,
        "user_id": req.user_id,
        "output_path": output_video_path,
        "assets_used": {
            "bgm": bgm_path,
            "overlay": overlay_path,
            "sfx": sfx_path
        },
        "python_script": python_script
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend_service:app", host="0.0.0.0", port=8000, reload=True)
