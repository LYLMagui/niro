"""
EvoMap Proxy Service - Lightweight FastAPI wrapper for EvoMap GEP-A2A Protocol
No API key required - just register via /a2a/hello
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
import httpx
import os
import json
import hashlib
import uuid
from datetime import datetime, timezone

app = FastAPI(title="EvoMap Proxy", version="1.0.0")

# Configuration
EVOMAP_HUB_URL = os.getenv("EVOMAP_HUB_URL", "https://evomap.ai")
NODE_ID_FILE = os.getenv("NODE_ID_FILE", "/tmp/evomap_node_id")

# In-memory node state
_node_id: Optional[str] = None
_heartbeat_interval: Optional[int] = None


def _load_node_id() -> str:
    """Load or generate node ID"""
    global _node_id
    if _node_id:
        return _node_id
    try:
        with open(NODE_ID_FILE, "r") as f:
            _node_id = f.read().strip()
    except FileNotFoundError:
        _node_id = f"node_{uuid.uuid4().hex[:16]}"
        try:
            with open(NODE_ID_FILE, "w") as f:
                f.write(_node_id)
        except Exception:
            pass  # Non-writable environment, use memory
    return _node_id


def _generate_envelope(message_type: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    """Generate GEP-A2A protocol envelope"""
    node_id = _load_node_id()
    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    return {
        "protocol": "gep-a2a",
        "protocol_version": "1.0.0",
        "message_type": message_type,
        "message_id": f"msg_{int(datetime.now(timezone.utc).timestamp())}_{uuid.uuid4().hex[:8]}",
        "sender_id": node_id,
        "timestamp": now,
        "payload": payload,
    }


def _canonical_json(obj: Dict) -> str:
    """Generate canonical JSON for SHA256 hashing"""
    return json.dumps(obj, sort_keys=True, separators=(",", ":"))


def _compute_asset_id(asset: Dict) -> str:
    """Compute SHA256 content-addressable asset ID"""
    asset_copy = {k: v for k, v in asset.items() if k != "asset_id"}
    content_hash = hashlib.sha256(_canonical_json(asset_copy).encode()).hexdigest()
    return f"sha256:{content_hash}"


# === Models ===


class HelloRequest(BaseModel):
    capabilities: Optional[Dict] = {}
    env_fingerprint: Optional[Dict] = {"platform": "linux", "arch": "x64"}


class FetchRequest(BaseModel):
    asset_type: Optional[str] = "Capsule"
    signals: Optional[List[str]] = None
    keywords: Optional[List[str]] = None
    include_tasks: Optional[bool] = False
    limit: Optional[int] = 10


class PublishAsset(BaseModel):
    type: str
    schema_version: str = "1.5.0"
    category: Optional[str] = None
    signals_match: Optional[List[str]] = None
    summary: str
    trigger: Optional[List[str]] = None
    gene: Optional[str] = None
    confidence: Optional[float] = None
    blast_radius: Optional[Dict] = None
    outcome: Optional[Dict] = None
    env_fingerprint: Optional[Dict] = None
    success_streak: Optional[int] = None
    intent: Optional[str] = None
    capsule_id: Optional[str] = None
    genes_used: Optional[List[str]] = None
    mutations_tried: Optional[int] = None
    total_cycles: Optional[int] = None


class PublishRequest(BaseModel):
    assets: List[PublishAsset]


# === Health ===


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "healthy", "service": "evomap-proxy", "node_id": _load_node_id()}


# === A2A Protocol Endpoints ===


@app.post("/a2a/hello")
async def hello(request: HelloRequest = HelloRequest()):
    """
    Register/refresh node registration. Returns 500 starter credits on first hello.
    """
    global _heartbeat_interval

    envelope = _generate_envelope(
        "hello",
        {
            "capabilities": request.capabilities,
            "gene_count": 0,
            "capsule_count": 0,
            "env_fingerprint": request.env_fingerprint,
        },
    )

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                f"{EVOMAP_HUB_URL}/a2a/hello",
                json=envelope,
                headers={"Content-Type": "application/json"},
            )
            response.raise_for_status()
            data = response.json()

            # Save node_id from response if different
            if "your_node_id" in data:
                global _node_id
                _node_id = data["your_node_id"]

            if "heartbeat_interval_ms" in data:
                _heartbeat_interval = data["heartbeat_interval_ms"]

            return data
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"EvoMap error: {e.response.text}",
        )
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


@app.post("/a2a/heartbeat")
async def heartbeat():
    """Send heartbeat to stay online (required every 15 min)"""
    envelope = _generate_envelope("heartbeat", {})

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                f"{EVOMAP_HUB_URL}/a2a/heartbeat", json=envelope
            )
            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"EvoMap error: {e.response.text}",
        )
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


@app.post("/a2a/fetch")
async def fetch(request: FetchRequest = FetchRequest()):
    """
    Fetch promoted assets (Capsules) from EvoMap marketplace.
    Supports filtering by signals or keywords.
    """
    envelope = _generate_envelope(
        "fetch",
        {
            "asset_type": request.asset_type,
            "signals": request.signals,
            "keywords": request.keywords,
            "include_tasks": request.include_tasks,
            "limit": request.limit,
        },
    )

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(f"{EVOMAP_HUB_URL}/a2a/fetch", json=envelope)
            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"EvoMap error: {e.response.text}",
        )
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


@app.post("/a2a/publish")
async def publish(request: PublishRequest):
    """
    Publish Gene + Capsule + EvolutionEvent bundle to EvoMap.
    """
    # Compute asset_ids for each asset
    assets_with_ids = []
    for asset in request.assets:
        asset_dict = asset.model_dump(exclude_none=True)
        asset_dict["asset_id"] = _compute_asset_id(asset_dict)
        assets_with_ids.append(asset_dict)

    envelope = _generate_envelope("publish", {"assets": assets_with_ids})

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(f"{EVOMAP_HUB_URL}/a2a/publish", json=envelope)
            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"EvoMap error: {e.response.text}",
        )
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


# === REST Endpoints (No envelope required) ===


@app.get("/a2a/assets/search")
async def search_assets(
    signals: Optional[str] = None,
    status: str = "promoted",
    asset_type: str = "Capsule",
    limit: int = 10,
):
    """
    Search assets by signals (REST endpoint, no envelope)
    """
    params = {"status": status, "type": asset_type, "limit": limit}
    if signals:
        params["signals"] = signals

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get(
                f"{EVOMAP_HUB_URL}/a2a/assets/search", params=params
            )
            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"EvoMap error: {e.response.text}",
        )
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


@app.get("/a2a/assets/ranked")
async def ranked_assets(asset_type: str = "Capsule", limit: int = 10):
    """Get assets ranked by GDI score"""
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get(
                f"{EVOMAP_HUB_URL}/a2a/assets/ranked",
                params={"type": asset_type, "limit": limit},
            )
            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"EvoMap error: {e.response.text}",
        )
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


@app.get("/a2a/assets/{asset_id}")
async def get_asset(asset_id: str):
    """Get single asset detail"""
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get(f"{EVOMAP_HUB_URL}/a2a/assets/{asset_id}")
            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"EvoMap error: {e.response.text}",
        )
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


@app.get("/a2a/stats")
async def stats():
    """Hub-wide statistics (also serves as health check)"""
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get(f"{EVOMAP_HUB_URL}/a2a/stats")
            response.raise_for_status()
            return response.json()
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


@app.get("/a2a/trending")
async def trending(limit: int = 10):
    """Get trending assets"""
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get(
                f"{EVOMAP_HUB_URL}/a2a/trending", params={"limit": limit}
            )
            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"EvoMap error: {e.response.text}",
        )
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


# === OpenCode Skill Integration ===


class OpenCodeSearchRequest(BaseModel):
    signals: Optional[List[str]] = None
    keywords: Optional[List[str]] = None


@app.post("/api/opencode/evomap/solutions/search")
async def opencode_search(request: OpenCodeSearchRequest):
    """
    OpenCode Skill integration endpoint.
    Search for solutions by error signals or keywords.
    """
    # Use REST search endpoint for simplicity
    signals_param = ",".join(request.signals) if request.signals else None

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            # Try REST search first
            if signals_param:
                response = await client.get(
                    f"{EVOMAP_HUB_URL}/a2a/assets/search",
                    params={"signals": signals_param, "limit": 10},
                )
            elif request.keywords:
                # Fall back to A2A fetch with keywords
                envelope = _generate_envelope(
                    "fetch",
                    {
                        "asset_type": "Capsule",
                        "keywords": request.keywords,
                        "limit": 10,
                    },
                )
                response = await client.post(
                    f"{EVOMAP_HUB_URL}/a2a/fetch", json=envelope
                )
            else:
                # Default: fetch recent promoted capsules
                response = await client.get(
                    f"{EVOMAP_HUB_URL}/a2a/assets/ranked",
                    params={"type": "Capsule", "limit": 10},
                )

            response.raise_for_status()
            return response.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=e.response.status_code,
            detail=f"EvoMap error: {e.response.text}",
        )
    except httpx.RequestError as e:
        raise HTTPException(
            status_code=502, detail=f"Failed to connect to EvoMap: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
