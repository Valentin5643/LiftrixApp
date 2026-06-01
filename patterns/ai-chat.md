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
  -> chat persistence and usage accounting
```

The source refresh identified Firebase AI usage with `gemini-2.5-flash-lite` in `AIChatServiceImpl` and `WorkoutProgramGenerationServiceImpl`.

## Core Rules

- Chat history is user-owned data and must be scoped by `user_id`.
- Do not call Firebase AI directly from UI.
- Every paid model response must record token usage in the same quota source used by `RateLimitingService`.
- Usage accounting must include repair attempts and responses that later fail parsing, validation, or UI persistence.
- Enforce daily message, monthly token, and hourly cost limits before model calls.
- Abuse prevention is fitness-aware; fitness context can reduce jailbreak score.
- Preserve English/Romanian language behavior unless source is updated.

## Required User Scoping

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
- Token overflow: `ChatHistoryEntity.tokenCount` and quota persistence.
- Language conflicts: `ChatPreferencesEntity.autoDetectLanguage`.
- Context loss: recent-message truncation before model calls.
- Missing accounting: failed parsing/validation after a model response still needs usage recorded.

## Related Docs

- Architecture and ownership: `docs/architecture.md`, `docs/module-map.md`.
- `:feature:chat` is registered and source-backed. Chat modularization specs under `docs/specs/` and `docs/specs/modularization/` remain useful for intent/history, but source is authoritative for current ownership.
