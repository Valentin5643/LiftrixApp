# AI Chat Patterns

Last moved from root `AGENTS.md`: 2026-05-05. Source audit baseline: 2026-05-01.

Use this when touching chat, AI coach, generated workout programs, abuse prevention, rate limiting, chat persistence, quota accounting, or Firebase AI.

## Core Flow

```text
ChatbotViewModel
  -> SendChatMessageUseCase / ChatOperationsUseCase
  -> AIChatService
  -> AbusePreventionService
  -> RateLimitingService
  -> Firebase AI / Gemini integration
  -> chat persistence and usage accounting
```

The source-audited architecture docs identify Firebase AI usage with `gemini-2.5-flash-lite`.

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
- Only the last 10 messages are included in context per the prior guide; verify current source before relying on or changing this.

## Debug Hot Zones

- Jailbreak false positives: `AbusePreventionService`, especially fitness phrases that include roleplay-like words.
- Token overflow: `ChatHistoryEntity.tokenCount` and quota persistence.
- Language conflicts: `ChatPreferencesEntity.autoDetectLanguage`.
- Context loss: recent-message truncation before model calls.
- Missing accounting: failed parsing/validation after a model response still needs usage recorded.

## Related Docs

- Architecture and ownership: `docs/architecture.md`, `docs/module-map.md`.
- Chat modularization specs live under `docs/specs/` and `docs/specs/modularization/`; treat them as historical/planning docs unless source confirms completion.
