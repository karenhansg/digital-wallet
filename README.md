# Digital Wallet Service

A standalone backend microservice serving as the core financial engine for an e-commerce platform.

## Architecture

```
┌─────────────┐     ┌──────────────────────────────────────────────┐
│   Client    │────▶│              API Gateway / LB                 │
└─────────────┘     └──────────────────┬───────────────────────────┘
                                       │
                    ┌──────────────────▼───────────────────────────┐
                    │         Digital Wallet Service                │
                    │  ┌─────────┐ ┌──────────┐ ┌──────────────┐  │
                    │  │ Security│ │Controller│ │  Swagger UI  │  │
                    │  │  (JWT)  │ │  Layer   │ │  /api-docs   │  │
                    │  └────┬────┘ └────┬─────┘ └──────────────┘  │
                    │       │           │                           │
                    │  ┌────▼───────────▼─────────────────────┐   │
                    │  │           Service Layer               │   │
                    │  │  WalletService | OtpService | Audit   │   │
                    │  │  FraudDetection | PaymentGateway      │   │
                    │  └────────────────┬─────────────────────┘   │
                    │                   │                           │
                    │  ┌────────────────▼─────────────────────┐   │
                    │  │         Repository Layer (JPA)         │   │
                    │  └────────────────┬─────────────────────┘   │
                    └───────────────────┼──────────────────────────┘
                                        │
                    ┌───────────────────▼──────┐  ┌──────────────┐
                    │   PostgreSQL (ACID)       │  │    Redis     │
                    │   - wallets               │  │  - Caching   │
                    │   - transactions          │  │  - Locks     │
                    │   - otp_tokens            │  └──────────────┘
                    │   - audit_logs            │
                    └──────────────────────────┘
```

## Design Decisions & Trade-offs

| Decision | Rationale |
|----------|-----------|
| **PostgreSQL** | ACID compliance for financial data; row-level locking for concurrency |
| **Redis** | Distributed caching and locking for horizontal scaling |
| **Pessimistic locking** | Prevents race conditions on balance updates under high concurrency |
| **Idempotency keys** | Prevents duplicate transactions from network retries |
| **Flyway** | Versioned, repeatable database migrations |
| **JWT stateless auth** | Enables horizontal scaling without session affinity |
| **OTP for withdrawals** | Additional security layer for sensitive operations |

## Setup & Run

### Prerequisites
- Docker & Docker Compose
- Java 21 (for local development)
- Maven 3.9+

### Quick Start (Docker)
```bash
docker-compose up --build
```
The service will be available at `http://localhost:8080`

### Local Development
```bash
# Start dependencies
docker-compose up postgres redis -d

# Run the application
mvn spring-boot:run
```

### Run Tests
```bash
mvn test
```

## API Documentation

Once running, access Swagger UI at: `http://localhost:8080/swagger-ui/index.html`

### Key Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/token?userId={id}` | Generate JWT (mock) |
| POST | `/api/wallet` | Create wallet |
| GET | `/api/wallet` | Get wallet details |
| POST | `/api/wallet/deposit` | Deposit funds |
| POST | `/api/wallet/otp` | Request withdrawal OTP |
| POST | `/api/wallet/withdraw` | Withdraw funds (requires OTP) |
| GET | `/api/wallet/transactions` | List transactions (paginated) |

### Example Flow
```bash
# 1. Get a token
TOKEN=$(curl -s -X POST "http://localhost:8080/api/auth/token?userId=user1" | jq -r .token)

# 2. Create wallet
curl -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8080/api/wallet

# 3. Deposit
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/wallet/deposit \
  -d '{"amount": 1000.00, "idempotencyKey": "dep-001", "description": "Initial deposit"}'

# 4. Request OTP for withdrawal
OTP=$(curl -s -H "Authorization: Bearer $TOKEN" -X POST http://localhost:8080/api/wallet/otp | jq -r .otp)

# 5. Withdraw
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/wallet/withdraw \
  -d "{\"amount\": 200.00, \"idempotencyKey\": \"wd-001\", \"otpCode\": \"$OTP\", \"description\": \"Payout\"}"

# 6. View transactions
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/wallet/transactions?page=0&size=10"
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/wallet_db` | Database URL |
| `DB_USERNAME` | `wallet_user` | Database username |
| `DB_PASSWORD` | `wallet_pass` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | (dev key) | Base64-encoded JWT signing key |
| `JWT_EXPIRATION_MS` | `3600000` | Token expiry (1 hour) |
| `DAILY_DEPOSIT_LIMIT` | `10000.00` | Max daily deposits (USD) |
| `DAILY_WITHDRAWAL_LIMIT` | `5000.00` | Max daily withdrawals (USD) |
| `WEEKLY_DEPOSIT_LIMIT` | `50000.00` | Max weekly deposits (USD) |
| `WEEKLY_WITHDRAWAL_LIMIT` | `25000.00` | Max weekly withdrawals (USD) |
| `MAX_BALANCE` | `100000.00` | Maximum wallet balance (USD) |
| `ARCHIVE_AFTER_DAYS` | `90` | Days before transaction archival |
| `OTP_EXPIRY_SECONDS` | `300` | OTP validity period |

## Tech Stack

- **Java 21** + **Spring Boot 3.3**
- **PostgreSQL 16** - ACID-compliant relational database
- **Redis 7** - Caching and distributed locking
- **Flyway** - Database migrations
- **JJWT** - JWT token handling
- **SpringDoc OpenAPI** - API documentation
- **JaCoCo** - Code coverage
- **Docker** - Containerization
