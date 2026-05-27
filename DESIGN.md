# DESIGN.md - Digital Wallet Service

## Database Schema

```
┌──────────────────────┐       ┌──────────────────────────────┐
│       wallets        │       │        transactions           │
├──────────────────────┤       ├──────────────────────────────┤
│ id          UUID PK  │◀──────│ wallet_id     UUID FK        │
│ user_id     VARCHAR  │       │ id            UUID PK        │
│ balance     DECIMAL  │       │ idempotency_key VARCHAR UQ   │
│ status      VARCHAR  │       │ type          VARCHAR        │
│ created_at  TIMESTAMP│       │ amount        DECIMAL        │
│ updated_at  TIMESTAMP│       │ balance_before DECIMAL       │
│ version     BIGINT   │       │ balance_after  DECIMAL       │
└──────────────────────┘       │ status        VARCHAR        │
                               │ description   VARCHAR        │
                               │ reference_id  VARCHAR        │
                               │ created_at    TIMESTAMP      │
                               │ updated_at    TIMESTAMP      │
                               └──────────────────────────────┘

┌──────────────────────┐       ┌──────────────────────────────┐
│      otp_tokens      │       │        audit_logs            │
├──────────────────────┤       ├──────────────────────────────┤
│ id          UUID PK  │       │ id            UUID PK        │
│ user_id     VARCHAR  │       │ user_id       VARCHAR        │
│ code        VARCHAR  │       │ action        VARCHAR        │
│ purpose     VARCHAR  │       │ entity_type   VARCHAR        │
│ used        BOOLEAN  │       │ entity_id     VARCHAR        │
│ expires_at  TIMESTAMP│       │ details       TEXT           │
│ created_at  TIMESTAMP│       │ ip_address    VARCHAR        │
└──────────────────────┘       │ created_at    TIMESTAMP      │
                               └──────────────────────────────┘
```

### Key Design Choices
- **DECIMAL(19,4)** for monetary values: avoids floating-point precision issues
- **UUID primary keys**: no sequential ID leakage, safe for distributed systems
- **Optimistic locking (version column)** on wallets: detects concurrent modifications
- **Pessimistic locking (SELECT FOR UPDATE)** during balance mutations: prevents race conditions

---

## Concurrency & Idempotency Strategy

### Concurrency Control
1. **Pessimistic Write Lock**: `SELECT ... FOR UPDATE` on the wallet row during deposit/withdrawal ensures only one transaction modifies the balance at a time.
2. **Optimistic Locking**: The `version` column on wallets provides a secondary safety net. If two transactions somehow bypass the pessimistic lock, the second commit will fail with `OptimisticLockingFailureException`.
3. **Database-level UNIQUE constraint** on `idempotency_key` prevents duplicate inserts even under race conditions.

### Idempotency
- Every write operation requires a client-provided `idempotencyKey`.
- Before processing, the service checks if a transaction with that key already exists.
- If found, the existing result is returned (HTTP 201 with same body).
- The UNIQUE constraint on `idempotency_key` is the final safeguard against duplicates.

### Flow Diagram
```
Client Request (with idempotency_key)
    │
    ▼
Check idempotency_key in DB ──── Found? ──▶ Return existing result
    │
    │ Not found
    ▼
Acquire pessimistic lock (SELECT FOR UPDATE)
    │
    ▼
Validate limits, balance, fraud checks
    │
    ▼
Update balance + Insert transaction (single DB transaction)
    │
    ▼
Commit ──── OptimisticLockException? ──▶ Return 409 (retry)
    │
    ▼
Return success
```

---

## Error Handling & Retry Strategy

### Error Categories
| Category | HTTP Status | Retryable | Example |
|----------|-------------|-----------|---------|
| Validation | 400 | No | Invalid amount |
| Auth | 401/403 | No | Invalid JWT/OTP |
| Not Found | 404 | No | Wallet doesn't exist |
| Business Rule | 422/429 | No | Insufficient balance, limit exceeded |
| Conflict | 409 | Yes | Concurrent modification |
| Server Error | 500 | Yes | Transient DB failure |

### Retry Strategy
- **Client-side**: Clients should retry on 409 (conflict) and 5xx errors with exponential backoff.
- **Idempotency keys** ensure retries are safe and won't create duplicate transactions.
- **Compensation**: If a transaction is partially committed (e.g., external gateway timeout), the system marks it as `FAILED` and the balance remains unchanged due to atomic DB transactions.

### Transaction Atomicity
All balance operations are wrapped in a single `@Transactional` block:
1. Lock wallet row
2. Validate business rules
3. Update balance
4. Insert transaction record
5. Commit

If any step fails, the entire operation rolls back.

---

## Security Considerations

### Authentication & Authorization
- **JWT-based stateless authentication**: Tokens validated on every request via `JwtAuthenticationFilter`.
- **User isolation**: Users can only access their own wallet (enforced by extracting `userId` from JWT).
- **OTP for withdrawals**: Adds a second factor for sensitive operations.

### Input Validation
- Jakarta Bean Validation on all request DTOs.
- Amount must be positive (`@DecimalMin("0.01")`).
- Idempotency key required for all write operations.

### Fraud Detection
- Velocity checks: limits transactions per minute per user.
- High-value transaction alerts (logged for review).
- Daily/weekly configurable limits.

### Data Protection (Production Recommendations)
- **In Transit**: TLS 1.3 termination at load balancer; internal mTLS between services.
- **At Rest**: PostgreSQL Transparent Data Encryption (TDE) or AWS RDS encryption. Redis AUTH + TLS.
- **Secrets**: Use AWS Secrets Manager or HashiCorp Vault (not environment variables in production).
- **PII**: Mask sensitive data in logs. Encrypt PII columns with application-level encryption.

---

## Scalability & Future Extensibility

### Horizontal Scaling
- **Stateless service**: No server-side sessions; any instance can handle any request.
- **Database connection pooling**: HikariCP with configurable pool size.
- **Redis**: Distributed caching for wallet lookups; distributed locks for cross-instance coordination.
- **Read replicas**: Transaction history queries can be routed to read replicas.

### Future Extensions
| Feature | Approach |
|---------|----------|
| **Event sourcing** | Publish domain events (WalletCreated, DepositCompleted) to Kafka for downstream consumers |
| **Multi-currency** | Add `currency` column to wallets; use exchange rate service for conversions |
| **Scheduled payouts** | Use Spring Scheduler or external job queue (SQS) |
| **Transaction archival** | Scheduled job moves transactions older than 90 days to archive table or cold storage (S3/Glacier) |
| **Rate limiting** | Redis-based sliding window rate limiter at API gateway level |
| **Notifications** | Event-driven notifications via SNS/SQS when transactions complete |
| **Admin dashboard** | Separate admin API with role-based access for support operations |

### Performance Targets
- **Latency**: < 100ms p95 for balance operations
- **Throughput**: 1000+ TPS per instance with connection pooling
- **Availability**: 99.9% with multi-AZ deployment and health checks
