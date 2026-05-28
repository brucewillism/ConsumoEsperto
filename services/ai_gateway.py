"""
Referência Python do AI Gateway (ConsumoEsperto).

O backend em produção usa AiGatewayService.java (Spring). Este módulo espelha
a mesma lógica AUTO para integrações FastAPI/Celery/LiteLLM sidecar.

Fluxo: prompt → select_strategy() → POST ATS /api/optimize → provider LLM
"""

from __future__ import annotations

import hashlib
import logging
import time
from dataclasses import dataclass
from typing import Any, Optional

import httpx

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class PromptContext:
    system_prompt: str = ""
    user_prompt: str = ""
    estimated_tokens: int = 0
    contains_code: bool = False
    history_message_count: int = 0
    complexity_score: float = 0.0
    document_import: bool = False
    json_output: bool = False


def estimate_tokens(system: str, user: str) -> int:
    return max(1, (len(system) + len(user)) // 4)


def detect_code(text: str) -> bool:
    t = text.lower()
    return "```" in t or "public class " in t or "retorne apenas json" in t or '{"action"' in t


def select_strategy(ctx: PromptContext) -> str:
    """AUTO MODE — não usar ultra para tudo."""
    if ctx.estimated_tokens < 1000:
        return "fast"
    if ctx.contains_code or detect_code(ctx.system_prompt + "\n" + ctx.user_prompt):
        return "code-focused"
    if ctx.estimated_tokens > 4000:
        return "ultra"
    if ctx.history_message_count > 12:
        return "ultra"
    if ctx.complexity_score > 0.8:
        return "balanced"
    return "fast"


class AiGateway:
  def __init__(
      self,
      ats_base_url: str,
      ats_api_key: str,
      *,
      timeout_s: float = 120.0,
      max_retries: int = 2,
      cache_ttl_s: float = 300.0,
      auto_strategy: bool = True,
  ) -> None:
      self.ats_base_url = ats_base_url.rstrip("/")
      self.ats_api_key = ats_api_key
      self.timeout_s = timeout_s
      self.max_retries = max_retries
      self.cache_ttl_s = cache_ttl_s
      self.auto_strategy = auto_strategy
      self._cache: dict[str, tuple[float, dict[str, Any]]] = {}

  def optimize(
      self,
      user_id: str | int,
      system_prompt: str,
      user_prompt: str,
      *,
      ctx: Optional[PromptContext] = None,
      target_model: str = "gemini-2.5-flash",
  ) -> dict[str, Any]:
      sys = system_prompt or ""
      usr = user_prompt or ""
      if ctx is None:
          tokens = estimate_tokens(sys, usr)
          ctx = PromptContext(
              system_prompt=sys,
              user_prompt=usr,
              estimated_tokens=tokens,
              contains_code=detect_code(sys + usr),
          )
      strategy = select_strategy(ctx) if self.auto_strategy else "balanced"

      cache_key = hashlib.sha256(
          f"{user_id}|{strategy}|{target_model}|{sys}|{usr}".encode()
      ).hexdigest()[:32]
      now = time.time()
      hit = self._cache.get(cache_key)
      if hit and hit[0] > now:
          return hit[1]

      body = {
          "messages": [
              *([{"role": "system", "content": sys}] if sys.strip() else []),
              {"role": "user", "content": usr or "."},
          ],
          "strategy": strategy,
          "user_id": f"consumoesperto-{user_id}",
          "target_model": target_model,
          "use_memory": False,
          "use_rag": False,
          "use_hierarchical_memory": False,
          "use_semantic_cache": True,
          "check_semantic_loss": False,
          "use_ollama": strategy in ("ultra", "balanced", "aggressive"),
      }

      last_err: Optional[Exception] = None
      for attempt in range(self.max_retries + 1):
          try:
              with httpx.Client(timeout=self.timeout_s) as client:
                  r = client.post(
                      f"{self.ats_base_url}/api/optimize",
                      json=body,
                      headers={"X-API-Key": self.ats_api_key},
                  )
                  r.raise_for_status()
                  data = r.json()
              out_sys, out_usr = _parse_messages(data.get("messages"))
              result = {
                  "system_prompt": out_sys or sys,
                  "user_prompt": out_usr or usr,
                  "tokens_saved": int(data.get("tokens_saved") or 0),
                  "strategy": strategy,
                  "optimized": True,
              }
              self._cache[cache_key] = (now + self.cache_ttl_s, result)
              return result
          except Exception as e:
              last_err = e
              logger.warning("ATS attempt %s failed: %s", attempt + 1, e)
              time.sleep(0.2 * (attempt + 1))

      logger.warning("ATS fallback: %s", last_err)
      return {
          "system_prompt": sys,
          "user_prompt": usr,
          "tokens_saved": 0,
          "strategy": strategy,
          "optimized": False,
      }


def _parse_messages(messages: Any) -> tuple[str, str]:
    systems: list[str] = []
    users: list[str] = []
    if not isinstance(messages, list):
        return "", ""
    for m in messages:
        if not isinstance(m, dict):
            continue
        role = str(m.get("role", "")).lower()
        content = str(m.get("content", "")).strip()
        if not content:
            continue
        if role == "system":
            systems.append(content)
        elif role == "user":
            users.append(content)
    return "\n\n".join(systems), "\n\n".join(users)
