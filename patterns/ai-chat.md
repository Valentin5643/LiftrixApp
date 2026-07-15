# AI Chat Patterns

Last moved from root `AGENTS.md`: 2026-05-05. Source refresh: 2026-05-31.

Use this when touching chat, AI coach, generated workout programs, abuse prevention, rate limiting, chat persistence, quota accounting, or Firebase AI.

## Core Flow

```text
ChatbotViewModel (:feature:chat)
  -> SendChatMessageUseCase / ChatOperationsUseCase
  -> AIChatService (:core:data)
  -> AbusePreventionService (:core:data)
  -> RateLimitingService (:core:data)
  -> Firebase AI / Gemini integration
  -> append-only ai_usage accounting
```

The source refresh identified Firebase AI usage with `gemini-2.5-flash-lite` in `AIChatServiceImpl` and `WorkoutProgramGenerationServiceImpl`.

## Core Rules

- Chat history is user-owned data and must be scoped by `user_id`.
- Do not call Firebase AI directly from UI.
- Every paid model response must append one idempotent, user-scoped `ai_usage` event before parsing or persistence can expose success. `RateLimitingService` reads only that ledger; chat retention never owns quota usage.
- `RateLimitingService` is the sole quota/status owner: limits come only from Remote Config and consumption comes only from `AiUsageDao`. Chat preferences, chat history, and subscriptions must not alter enforcement.
- Usage accounting must include repair attempts and responses that later fail parsing, validation, or UI persistence.
- Enforce daily message, monthly token, and hourly cost limits before model calls.
- Abuse prevention is fitness-aware; fitness context can reduce jailbreak score.
- Preserve English/Romanian language behavior unless source is updated.
- Default-enabled chat history persists messages in `chat_history` and local conversation metadata/tombstones in `chat_conversations`; explicit preference opt-out remains ephemeral.
- Submission retries reuse `chat-{requestId}-user` and `chat-{requestId}-assistant` row IDs. Conversation rename/delete never reads or mutates `ai_usage`.
- Every Firebase AI dispatch must run through `PaidAiCallExecutor`. A paid-operation change cannot merge unless CHAT_RESPONSE, WORKOUT_GENERATE, WORKOUT_REPAIR, and WORKOUT_MODIFY all demonstrate admin, kill-switch, abuse, quota, App Check, post-dispatch accounting, and metadata-only telemetry coverage with no direct bypass.
- Pre-dispatch rejection writes no usage event. Every post-dispatch outcome writes exactly one event, including empty output, max-token stops, and provider failures.

## Required User Scoping

## Guided Workout Builder

- `WorkoutProgramGateway` remains the presentation facade for generation, modification, validation, and per-day template persistence.
- Generation consumes explicit reviewed goal, level, equipment, ordered training days, duration, limitations, and additional preferences.
- Stages are milestone-driven: analyzing goals, choosing exercises, building the schedule, balancing, optional repair, and finalization. UI timers never advance them.
- Replace-exercise and regenerate-day results are rejected if any field outside the requested scope changes.
- Partial saves retain ordered day-index mappings; retry skips already saved indices.
- Cache hits, UI stages, local edits, saves, and navigation do not write `ai_usage`; actual generate/repair/modify responses continue through the existing service ledger path.

```kotlin
@Query("SELECT * FROM chat_history WHERE user_id = :userId")
suspend fun getChatHistory(userId: String): List<ChatHistoryEntity>
```

## Rate Limits Preserved From Root Guide

```kotlin
if (limits.dailyMessagesRemaining <= 0) throw QuotaExceededException()
if (limits.monthlyTokensRemaining <= 0) throw QuotaExceededException()
if (estimatedHourlyCost > 1.0) throw CostThresholdException()
recordAiUsage(userId, tokensUsed)
```

Prior root guidance listed limits as 100 messages/day, 10k tokens/month, and $1/hour. Verify source before changing these values.

## Language and Safety Notes

- Romanian auto-detection was previously based on Romanian diacritics.
- Fitness keywords reduce jailbreak score in the existing guidance.
- Recent-message context is intentionally bounded. `SendChatMessageUseCase` uses a last-10-message window; service-level context truncation also exists in AI service code. Verify both layers before changing prompt context size.

## Debug Hot Zones

- Jailbreak false positives: `AbusePreventionService`, especially fitness phrases that include roleplay-like words.
- Token overflow: `AiUsageEntity.totalTokens` and quota persistence.
- Language conflicts: `ChatPreferencesEntity.autoDetectLanguage`.
- Context loss: recent-message truncation before model calls.
- Missing accounting: failed parsing/validation after a model response still needs usage recorded.

## Related Docs

- Architecture and ownership: `docs/architecture.md`, `docs/module-map.md`.
- `:feature:chat` is registered and source-backed. Chat modularization specs under `docs/specs/` and `docs/specs/modularization/` remain useful for intent/history, but source is authoritative for current ownership.
