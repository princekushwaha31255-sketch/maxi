# MAX AI — Firestore Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **Strict Multi-Tenant Isolation**: Every document under `/users/{userId}/chat_messages/{messageId}`, `/users/{userId}/action_logs/{logId}`, and `/users/{userId}/trusted_rules/{ruleId}` belongs exclusively to `userId` (`request.auth != null && request.auth.uid == userId`).
2. **Identity Integrity**: The payload's `userId` field must equal `request.auth.uid` and the path `{userId}` on creation, and must remain immutable on update (`incoming().userId == existing().userId`).
3. **Temporal Integrity**: `createdAt` and `updatedAt` must be valid Firestore `timestamp` objects `<= request.time`. On updates, `createdAt` is strictly immutable (`incoming().createdAt == existing().createdAt`).
4. **Path Variable Hardening**: Every document ID (`userId`, `messageId`, `logId`, `ruleId`) must match `^[a-zA-Z0-9_\-]+$` and be `<= 128` characters.
5. **Bounded String Sizes**: All string fields have strict `.size()` upper bounds to prevent Denial-of-Wallet storage exhaustion.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read/Write**: `request.auth == null` attempting `get`, `list`, `create`, `update`, or `delete` on `/users/alice_123/chat_messages`.
2. **Cross-User Read (PII / History Scraping)**: Authenticated user `bob_456` attempting `get` or `list` on `/users/alice_123/chat_messages`.
3. **Identity Spoofing on Create**: User `alice_123` writing to `/users/alice_123/chat_messages/msg_1` with `{"userId": "bob_456", ...}`.
4. **Shadow Field Injection on Create**: Payload containing an undeclared field `{"isAdmin": true}` not in `hasOnly(...)`.
5. **Shadow Field Injection on Update**: Updating `trusted_rules` with an extra key `{"scopeKey": "all"}` when only `['isEnabled', 'updatedAt']` are allowed in `affectedKeys().hasOnly(...)`.
6. **Immutable `createdAt` Tampering**: Updating a `trusted_rules` document while modifying `createdAt` to a newer timestamp.
7. **Immutable `userId` Tampering**: Updating a `trusted_rules` document to transfer ownership to another UID.
8. **Future Timestamp Spoofing**: Creating a document where `createdAt > request.time`.
9. **Resource Exhaustion (1MB String)**: Creating a `chat_messages` document with `text` length > 8000 characters.
10. **Invalid Enum Value**: Creating a `chat_messages` document with `detectedLanguage: "Klingon"` instead of `['Hindi', 'English', 'Hinglish']`.
11. **Path ID Poisoning**: Creating a document with an invalid ID containing spaces or special characters or length > 128.
12. **Value Poisoning on Update**: Updating `trusted_rules.isEnabled` with a string `"true"` instead of a `bool`.
