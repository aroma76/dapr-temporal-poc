# Banking POC — Full Architecture & Technical Reference

> A Proof of Concept for a distributed, event-driven banking withdrawal system using Spring Boot, Temporal, Dapr, YugabyteDB, and Redis.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Infrastructure: Docker Containers](#3-infrastructure-docker-containers)
4. [System Architecture Diagram](#4-system-architecture-diagram)
5. [End-to-End Request Lifecycle](#5-end-to-end-request-lifecycle)
6. [Data Model: YugabyteDB Schema](#6-data-model-yugabytedb-schema)
7. [Service Code Reference](#7-service-code-reference)
8. [Dapr Pub/Sub Internals](#8-dapr-pubsub-internals)
9. [Temporal Workflow State Machine](#9-temporal-workflow-state-machine)
10. [Test Suite](#10-test-suite)
11. [API Reference](#11-api-reference)
12. [Design Principles & Problem Solving](#12-design-principles--problem-solving)
13. [How to Run](#13-how-to-run)
14. [Troubleshooting Guide](#14-troubleshooting-guide)

---

## 1. Project Overview

This project is a **Banking Proof of Concept** demonstrating a production-grade microservices architecture for handling bank withdrawal requests in a reliable, durable, and idempotent way.

The core problem being solved is: **What happens if anything goes wrong during a money transfer?**

- What if the network drops mid-request?
- What if the server crashes while processing a payment?
- What if a client accidentally submits the same withdrawal twice?
- What if the message broker is temporarily unavailable?

This POC demonstrates how four modern distributed systems technologies — **Temporal**, **Dapr**, **YugabyteDB**, and **Redis** — work together to make financial transactions reliable, auditable, and fault-tolerant.

### Four Core Demonstration Scenarios

| # | Scenario | What It Proves |
|---|---|---|
| **1** | Successful Withdrawal (₹10,000 → ₹7,000) | Full happy-path flow across all 7 components |
| **2** | Insufficient Balance | Graceful business failure with audit log, no data corruption |
| **3** | Temporal Activity Retry | Automatic retry with exponential backoff on network errors |
| **4** | Temporal Signal Handling | Asynchronous event-driven workflow resumption via Dapr events |

---

## 2. Technology Stack

| Layer | Technology | Version | Why It Was Chosen |
|---|---|---|---|
| **Language & Runtime** | Java | 17 LTS | Long-term supported, modern Java features (records, sealed classes), required by Spring Boot 3 |
| **Application Framework** | Spring Boot | 3.3.4 | Auto-configuration, embedded Tomcat, Spring Data JPA, beginner-friendly |
| **Workflow Orchestration** | Temporal | 1.24.1 (Server) | Durable execution engine — survives crashes, retries failures automatically, maintains exact state |
| **Messaging Abstraction** | Dapr (Distributed Application Runtime) | 1.13.0 | Abstracts the message broker from business code — swap Redis for Kafka with zero Java changes |
| **Message Broker** | Redis | 7-alpine | Lightweight Redis Streams for Pub/Sub — no Kafka/Zookeeper complexity needed for 2 services |
| **Application Database** | YugabyteDB | 2.21.1.0-b271 | Distributed SQL, PostgreSQL-compatible API (YSQL), ACID transactions, horizontally scalable |
| **Workflow Persistence** | PostgreSQL | 15-alpine | Dedicated database for Temporal Server's internal state (workflow histories, activity checkpoints) |
| **Build Tool** | Apache Maven | 3.9.x | Dependency management, multi-module parent POM, test running |
| **ORM / Persistence** | Spring Data JPA + Hibernate | 6.x | Automatic schema management (`ddl-auto=update`), type-safe repository abstraction |
| **Containerization** | Docker + Docker Compose | 3.8 | 100% containerized: all 9 containers (infrastructure + both Spring Boot microservices) launched via a single `docker-compose up -d` command |

---

## 3. Infrastructure: Docker Containers

The entire system runs as **9 Docker containers** defined in [`docker-compose.yml`](docker-compose.yml). All containers are built and launched with a single command: `docker-compose up -d`.

### Container Reference Table

| # | Container Name | Docker Image / Source | Port(s) | Role |
|---|---|---|---|---|
| 1 | `banking-yugabyte` | `yugabytedb/yugabyte:latest` | `5433`, `7000`, `9000` | Primary application database |
| 2 | `banking-redis` | `redis:7-alpine` | `6379` | Dapr Pub/Sub message broker backing store |
| 3 | `temporal-db` | `postgres:15-alpine` | `5432` | Temporal Server's internal persistence database |
| 4 | `temporal-server` | `temporalio/auto-setup:1.24.1` | `7233` | Workflow orchestration engine |
| 5 | `temporal-ui` | `temporalio/ui:2.26.0` | `8080` | Workflow visual dashboard |
| 6 | `withdrawal-dapr` | `daprio/daprd:1.13.0` | `3500`, `50001` | Dapr sidecar for the Withdrawal Service |
| 7 | `transaction-dapr` | `daprio/daprd:1.13.0` | `3501`, `50002` | Dapr sidecar for the Transaction Service |
| 8 | `transaction-service` | `withdrawal-service/Dockerfile` (`eclipse-temurin:17-jre-alpine`) | `8082` | Transaction Spring Boot Microservice |
| 9 | `withdrawal-service` | `transaction-service/Dockerfile` (`eclipse-temurin:17-jre-alpine`) | `8081` | Withdrawal Spring Boot Microservice |

### Detailed Container Descriptions

#### `banking-yugabyte` — Application Database
- **Started with**: `bin/yugabyted start --daemon=false`
- **Hostname**: `banking-yugabyte` (the hostname is set explicitly so that `ysqlsh` can resolve it correctly, since YugabyteDB binds to the container's hostname IP rather than `127.0.0.1`)
- **Data persistence**: Stored in Docker volume `yb_data` — data survives container restarts
- **Accessed by**: `transaction-service` via JDBC at `localhost:5433` using the standard PostgreSQL JDBC driver (`org.postgresql.Driver`)
- **DBeaver connection**: Host `localhost`, Port `5433`, Database `yugabyte`, User `yugabyte`
- **Admin UIs**: Master UI at [http://localhost:7000](http://localhost:7000), TServer UI at [http://localhost:9000](http://localhost:9000)

#### `banking-redis` — Message Broker
- **Image**: `redis:7-alpine` (lightweight Alpine-based image, ~15 MB)
- **Role**: The physical message bus. Dapr uses Redis Streams internally to persist and route messages on topics `withdrawal-requests` and `transaction-results`
- **Why not Kafka?**: For two services and two topics, Kafka requires Zookeeper + Kafka Broker + Schema Registry — over 3 extra containers and significant setup. Redis Streams handle this with zero configuration
- **Accessed by**: Both `withdrawal-dapr` and `transaction-dapr` sidecars only. The Spring Boot services never talk to Redis directly

#### `temporal-db` — Temporal's Database
- **Image**: `postgres:15-alpine`
- **Credentials**: User `temporal`, Password `temporal`, DB `temporal`
- **Port**: `5432` (kept internal; only `temporal-server` connects to it)
- **Why separate from YugabyteDB?**: Temporal's internal schema (workflow executions, activity results, timers, signals) is Temporal-specific infrastructure. Keeping it in its own PostgreSQL instance isolates it from banking application data
- **Data persistence**: Stored in Docker volume `temporal_pg_data`

> [!IMPORTANT]
> `temporalio/auto-setup` requires the env var `DB=postgres12` exactly. Using `postgresql` or `postgres` will cause Temporal Server to exit with `Unsupported DB type` on startup.

#### `temporal-server` — Workflow Engine
- **Image**: `temporalio/auto-setup:1.24.1` — the `auto-setup` variant automatically runs all database migrations against `temporal-db` before starting the Temporal frontend, history, matching, and worker services
- **Connected to**: `temporal-db` on port `5432`
- **Used by**: `withdrawal-service` via Temporal Java SDK connecting to `127.0.0.1:7233`
- **What it stores**: Every workflow execution's complete history — every activity invocation, every signal received, every timer set. This is what makes workflows durable across crashes

#### `temporal-ui` — Visual Workflow Dashboard
- **URL**: [http://localhost:8080](http://localhost:8080)
- **Connected to**: `temporal-server` at `temporal:7233` (internal Docker network DNS)
- **What you can see**: Live list of running workflows, completed workflow histories, activity retry attempts with timestamps, signal events, input/output payloads for every step

#### `withdrawal-dapr` — Withdrawal Service Sidecar
- **App ID**: `withdrawal-service`
- **App Port**: `8081` (points at the Withdrawal Spring Boot service on the host via `host.docker.internal`)
- **Dapr HTTP Port**: `3500` (Withdrawal Service publishes messages here)
- **Subscribed to**: Topic `transaction-results` → routes incoming events to `http://host.docker.internal:8081/events/transaction-results`
- **Components loaded from**: `./components/pubsub.yaml`

#### `transaction-dapr` — Transaction Service Sidecar
- **App ID**: `transaction-service`
- **App Port**: `8082` (points at the Transaction Spring Boot service on the host)
- **Dapr HTTP Port**: `3501` (Transaction Service publishes results here)
- **Subscribed to**: Topic `withdrawal-requests` → routes incoming events to `http://host.docker.internal:8082/events/withdrawal-requests`

---

## 4. System Architecture Diagram

```mermaid
flowchart TD
    Client["🖥️ Client\n(Postman / curl / Browser)"]

    subgraph WithdrawalSvc ["Withdrawal Microservice — Port :8081"]
        WCtrl["WithdrawalController\nREST API Entry Point"]
        WWorkflow["WithdrawalWorkflow\nTemporal Durable Workflow"]
        WActivity["WithdrawalActivityImpl\nActivity: Publishes to Dapr"]
        WSubscriber["DaprEventSubscriber\nReceives results, signals Temporal"]
        WSidecar["withdrawal-dapr Sidecar\nPort :3500"]

        WCtrl -->|"Start Workflow"| WWorkflow
        WWorkflow -->|"Execute Activity (with retry)"| WActivity
        WActivity -->|"HTTP POST publish"| WSidecar
        WSubscriber -->|"Temporal Signal"| WWorkflow
    end

    subgraph Temporal ["Temporal Orchestration Layer"]
        TServer["Temporal Server\nPort :7233 (gRPC)"]
        TDB[("temporal-db\nPostgreSQL :5432")]
        TUI["Temporal Web UI\nhttp://localhost:8080"]
        TServer <-->|"Workflow history & state"| TDB
        TUI <-->|"Query & inspect"| TServer
    end

    subgraph Messaging ["Dapr Pub/Sub Broker Layer"]
        Redis[("banking-redis\nRedis :6379\nRedis Streams")]
        TopicReq["Stream: withdrawal-requests"]
        TopicRes["Stream: transaction-results"]
    end

    subgraph TransactionSvc ["Transaction Microservice — Port :8082"]
        TSidecar["transaction-dapr Sidecar\nPort :3501"]
        TSubscriber["DaprEventSubscriber\nReceives withdrawal requests"]
        TService["TransactionService\nIdempotency + Balance Check + Debit"]
        TDaprSvc["DaprPubSubService\nPublishes results"]
        TCtrl["TransactionController\nAccount seeding REST API"]

        TSidecar -->|"HTTP POST forward"| TSubscriber
        TSubscriber -->|"processWithdrawal()"| TService
        TService -->|"publishTransactionResult()"| TDaprSvc
        TDaprSvc -->|"HTTP POST publish"| TSidecar
        TCtrl --> TService
    end

    subgraph DB ["Data Persistence"]
        YB[("YugabyteDB\nYSQL Port :5433")]
        Accounts["Table: accounts\naccount_id | balance"]
        Txns["Table: withdrawal_transactions\nid | request_id | account_id | amount | status | message | created_at"]
        YB --- Accounts
        YB --- Txns
    end

    Client -->|"POST /api/withdrawals"| WCtrl
    Client -->|"POST /api/accounts"| TCtrl
    WWorkflow <-->|"gRPC"| TServer
    WSidecar -->|"Publish"| TopicReq
    TopicReq --> Redis
    Redis --> TopicRes
    TSidecar -->|"Subscribe"| Redis
    TopicRes -->|"Deliver"| WSidecar
    WSidecar -->|"HTTP POST forward"| WSubscriber
    TService -->|"JDBC/PostgreSQL driver"| YB
```

---

## 5. End-to-End Request Lifecycle

A single withdrawal request travels through **7 phases** and **10 component handoffs**:

### Phase 1 — HTTP Request Ingestion

**Entry point**: [`WithdrawalController.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalController.java)

A client sends:
```http
POST http://localhost:8081/api/withdrawals
Content-Type: application/json

{
  "requestId": "req-live-001",
  "accountId": "ACC-001",
  "amount": 3000.00
}
```

The controller constructs a **deterministic Workflow ID** from the `requestId`:
```java
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setWorkflowId("withdrawal-" + request.getRequestId())
    .setTaskQueue("WITHDRAWAL_TASK_QUEUE")
    .build();
```

> [!NOTE]
> The Workflow ID is derived from `requestId` for a reason. If the same client submits the same `requestId` a second time, Temporal will detect that `withdrawal-req-live-001` is already running or completed and will not launch a duplicate. This is the **first idempotency layer** of the system.

The controller then makes a **synchronous, blocking call** that waits until the entire distributed workflow completes:
```java
TransactionResult result = workflow.executeWithdrawal(request);
return ResponseEntity.ok(result);
```

---

### Phase 2 — Temporal Workflow Begins

**File**: [`WithdrawalWorkflowImpl.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalWorkflowImpl.java)

Temporal registers a new workflow execution. Inside the workflow:

1. Status is updated to `"PUBLISHING_REQUEST"`
2. The Activity stub is called: `activity.publishWithdrawalRequest(request)`

**Why an Activity and not a direct HTTP call?**

In Temporal, Workflows must be **purely deterministic** — no network I/O, no timestamps, no randomness. All external side effects (network calls, file writes, etc.) must be performed inside Activities. The Activity has retry semantics:

```java
ActivityOptions options = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofSeconds(10))
    .setRetryOptions(RetryOptions.newBuilder()
        .setInitialInterval(Duration.ofSeconds(1))
        .setMaximumAttempts(3)
        .build())
    .build();
```

If the Dapr sidecar is temporarily unreachable, Temporal will retry the Activity automatically up to 3 times with 1 second initial delay (exponentially growing). This is what **Test 3 (Activity Retry)** demonstrates.

After publishing, the workflow enters a **non-blocking wait state**:
```java
boolean received = Workflow.await(Duration.ofSeconds(60), () -> this.transactionResult != null);
```

Temporal persists the full workflow state to `temporal-db` and suspends the workflow thread. It consumes **zero CPU while waiting**. The gRPC connection to `temporal-server:7233` is released.

---

### Phase 3 — Activity Publishes to Dapr

**Files**: [`WithdrawalActivityImpl.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalActivityImpl.java), [`DaprPubSubService.java`](withdrawal-service/src/main/java/com/bank/withdrawal/DaprPubSubService.java)

The Activity calls `DaprPubSubService`, which sends a plain HTTP POST to the Dapr sidecar:
```http
POST http://localhost:3500/v1.0/publish/pubsub/withdrawal-requests
Content-Type: application/json

{
  "requestId": "req-live-001",
  "accountId": "ACC-001",
  "amount": 3000.00
}
```

**Key Design Choice**: The Withdrawal Service never imports a Redis SDK, never manages a Redis connection pool, and never knows what broker is under Dapr. It calls `localhost:3500` via HTTP. The Dapr sidecar (`withdrawal-dapr`) receives this and appends it as an event to the Redis Stream `withdrawal-requests` in `banking-redis:6379`.

The Dapr component configuration that wires Redis is in [`components/pubsub.yaml`](components/pubsub.yaml):
```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: pubsub
spec:
  type: pubsub.redis
  version: v1
  metadata:
  - name: redisHost
    value: redis:6379
```

> [!TIP]
> To swap Redis for Kafka, you only need to change `pubsub.yaml` to `type: pubsub.kafka` and add Kafka broker metadata. Zero Java code changes are needed in either microservice. This is the power of Dapr's abstraction.

---

### Phase 4 — Message Delivery to Transaction Service

`transaction-dapr` (port `3501`) is subscribed to topic `withdrawal-requests`. It continuously polls the Redis Stream and receives the new message.

Dapr then makes an internal HTTP POST to the Transaction Service:
```http
POST http://host.docker.internal:8082/events/withdrawal-requests
Content-Type: application/cloudevents+json

{
  "specversion": "1.0",
  "type": "com.dapr.event.sent",
  "data": {
    "requestId": "req-live-001",
    "accountId": "ACC-001",
    "amount": 3000.00
  }
}
```

Dapr wraps the message in the **CloudEvents 1.0** format. The [`DaprEventSubscriber.java`](transaction-service/src/main/java/com/bank/transaction/DaprEventSubscriber.java) in Transaction Service unwraps it:
```java
JsonNode rootNode = objectMapper.readTree(payload);
JsonNode dataNode = rootNode.has("data") ? rootNode.get("data") : rootNode;
WithdrawalRequest request = objectMapper.treeToValue(dataNode, WithdrawalRequest.class);
```

**How does Dapr know to deliver to this route?** Dapr calls the discovery endpoint at startup:
```
GET http://host.docker.internal:8082/dapr/subscribe
```
The service responds with:
```json
[{ "pubsubname": "pubsub", "topic": "withdrawal-requests", "route": "/events/withdrawal-requests" }]
```

---

### Phase 5 — ACID Business Processing in YugabyteDB

**File**: [`TransactionService.java`](transaction-service/src/main/java/com/bank/transaction/TransactionService.java)

The entire `processWithdrawal()` method runs inside a single `@Transactional` database transaction against YugabyteDB. If anything fails midway, the whole thing rolls back atomically.

#### Step 5A: Idempotency Check (Second Layer)
```java
Optional<WithdrawalTransaction> existing = transactionRepository.findByRequestId(request.getRequestId());
if (existing.isPresent()) {
    // Return the previous result WITHOUT re-processing
    return new TransactionResult(..., "Duplicate request - returned existing result", ...);
}
```
The `withdrawal_transactions` table has a `UNIQUE` constraint on `request_id`. Even if the same message was delivered twice by the broker (at-least-once delivery guarantee), the application handles it gracefully.

#### Step 5B: Account Verification
```java
Account account = accountRepository.findById(request.getAccountId()).orElse(null);
if (account == null) {
    // Save a FAILED audit record and return error
}
```

#### Step 5C: Balance Check
```java
if (account.getBalance().compareTo(request.getAmount()) < 0) {
    // Save FAILED audit record: "Insufficient balance"
    // Balance is NOT changed
}
```

#### Step 5D: Atomic Debit + Audit
```java
account.setBalance(account.getBalance().subtract(request.getAmount()));
accountRepository.save(account);               // UPDATE accounts SET balance = ? WHERE account_id = ?
transactionRepository.save(successTx);         // INSERT INTO withdrawal_transactions ...
```

Both SQL statements execute inside the same ACID transaction. YugabyteDB commits them atomically — either both succeed or neither does.

---

### Phase 6 — Result Published Back via Dapr

**File**: [`DaprPubSubService.java`](transaction-service/src/main/java/com/bank/transaction/DaprPubSubService.java)

After the database transaction commits, `DaprPubSubService` publishes the result:
```http
POST http://localhost:3501/v1.0/publish/pubsub/transaction-results
Content-Type: application/json

{
  "requestId": "req-live-001",
  "accountId": "ACC-001",
  "amount": 3000.0,
  "status": "SUCCESS",
  "message": "Withdrawal successful",
  "balance": 7000.0
}
```

The service includes a **direct fallback** for local development without Dapr:
```java
try {
    restTemplate.postForEntity(daprPublishUrl, entity, Void.class);
} catch (Exception daprException) {
    // Dapr not available? Deliver directly to Withdrawal Service
    restTemplate.postForEntity(directFallbackUrl, entity, Void.class);
}
```

---

### Phase 7 — Temporal Signal Delivery & Workflow Completion

**Files**: [`DaprEventSubscriber.java`](withdrawal-service/src/main/java/com/bank/withdrawal/DaprEventSubscriber.java) (Withdrawal Service), [`WithdrawalWorkflowImpl.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalWorkflowImpl.java)

`withdrawal-dapr` receives the `transaction-results` event from Redis and forwards it to Withdrawal Service's subscriber:
```
POST http://host.docker.internal:8081/events/transaction-results
```

`DaprEventSubscriber` parses the result and fires a **Temporal Signal** into the waiting workflow:
```java
String workflowId = "withdrawal-" + result.getRequestId();
WithdrawalWorkflow workflow = workflowClient.newWorkflowStub(WithdrawalWorkflow.class, workflowId);
workflow.transactionResultSignal(result);
```

Inside the workflow, the signal method is invoked on the workflow thread:
```java
@Override
public void transactionResultSignal(TransactionResult result) {
    this.transactionResult = result;
    this.status = "SIGNAL_RECEIVED";
}
```

The `Workflow.await()` predicate `() -> this.transactionResult != null` becomes `true`. The workflow thread unblocks, sets status to `"COMPLETED"`, and returns the result.

This unblocks the original HTTP request in `WithdrawalController`, which returns `HTTP 200 OK` with the full `TransactionResult` JSON to the client.

---

## 6. Data Model: YugabyteDB Schema

Hibernate auto-generates the schema on `transaction-service` startup via `spring.jpa.hibernate.ddl-auto=update`.

### Table: `accounts`

Defined in [`Account.java`](transaction-service/src/main/java/com/bank/transaction/Account.java)

```sql
CREATE TABLE accounts (
    account_id VARCHAR(50)     NOT NULL PRIMARY KEY,
    balance    NUMERIC(15, 2)  NOT NULL
);
```

| Column | Type | Constraint | Description |
|---|---|---|---|
| `account_id` | `VARCHAR(50)` | `PRIMARY KEY` | Business account identifier (e.g., `ACC-001`) |
| `balance` | `NUMERIC(15, 2)` | `NOT NULL` | Current account balance with 2 decimal places |

### Table: `withdrawal_transactions`

Defined in [`WithdrawalTransaction.java`](transaction-service/src/main/java/com/bank/transaction/WithdrawalTransaction.java)

```sql
CREATE TABLE withdrawal_transactions (
    id         BIGSERIAL        NOT NULL PRIMARY KEY,
    request_id VARCHAR(64)      NOT NULL UNIQUE,
    account_id VARCHAR(50)      NOT NULL,
    amount     NUMERIC(15, 2)   NOT NULL,
    status     VARCHAR(20)      NOT NULL,
    message    VARCHAR(255),
    created_at TIMESTAMP        NOT NULL
);
```

| Column | Type | Constraint | Description |
|---|---|---|---|
| `id` | `BIGSERIAL` | `PRIMARY KEY` | Auto-incremented surrogate key |
| `request_id` | `VARCHAR(64)` | `UNIQUE NOT NULL` | Client-provided idempotency key — prevents duplicate processing |
| `account_id` | `VARCHAR(50)` | `NOT NULL` | The account that was debited |
| `amount` | `NUMERIC(15, 2)` | `NOT NULL` | Amount requested for withdrawal |
| `status` | `VARCHAR(20)` | `NOT NULL` | Either `"SUCCESS"` or `"FAILED"` |
| `message` | `VARCHAR(255)` | | Human-readable outcome message |
| `created_at` | `TIMESTAMP` | `NOT NULL` | Server-side timestamp at time of processing |

> [!NOTE]
> Both successful and failed withdrawals are recorded in `withdrawal_transactions`. An "Insufficient balance" failure writes a `FAILED` audit row without modifying the `accounts` table, providing a complete, immutable audit trail.

---

## 7. Service Code Reference

### Withdrawal Service (`withdrawal-service` — Port 8081)

| File | Purpose |
|---|---|
| [`WithdrawalController.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalController.java) | REST endpoint. Receives HTTP POST, starts Temporal workflow, blocks until result, returns response |
| [`WithdrawalWorkflow.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalWorkflow.java) | Temporal `@WorkflowInterface`. Declares `executeWithdrawal()`, `transactionResultSignal()`, `getStatus()` |
| [`WithdrawalWorkflowImpl.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalWorkflowImpl.java) | Temporal workflow implementation. Calls Activity, waits for signal with 60-second timeout |
| [`WithdrawalActivity.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalActivity.java) | Temporal `@ActivityInterface`. Declares `publishWithdrawalRequest()` |
| [`WithdrawalActivityImpl.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalActivityImpl.java) | Activity implementation. Delegates to `DaprPubSubService` |
| [`DaprPubSubService.java`](withdrawal-service/src/main/java/com/bank/withdrawal/DaprPubSubService.java) | Sends HTTP POST to Dapr sidecar port `3500`. Falls back to direct HTTP if Dapr is unavailable |
| [`DaprEventSubscriber.java`](withdrawal-service/src/main/java/com/bank/withdrawal/DaprEventSubscriber.java) | Dapr subscriber webhook. Exposes `GET /dapr/subscribe` and `POST /events/transaction-results`. Sends Temporal signal |
| [`TemporalConfig.java`](withdrawal-service/src/main/java/com/bank/withdrawal/TemporalConfig.java) | Spring `@Configuration`. Registers Temporal `WorkflowClient`, `WorkflowWorker`, Activity and Workflow implementations |
| [`WithdrawalRequest.java`](withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalRequest.java) | POJO DTO: `requestId`, `accountId`, `amount` |
| [`TransactionResult.java`](withdrawal-service/src/main/java/com/bank/withdrawal/TransactionResult.java) | POJO DTO: `requestId`, `accountId`, `amount`, `status`, `message`, `balance` |

### Transaction Service (`transaction-service` — Port 8082)

| File | Purpose |
|---|---|
| [`TransactionController.java`](transaction-service/src/main/java/com/bank/transaction/TransactionController.java) | REST endpoints: `POST /api/accounts` (seed/create), `GET /api/accounts/{id}` (query), `GET /api/transactions` (list all) |
| [`DaprEventSubscriber.java`](transaction-service/src/main/java/com/bank/transaction/DaprEventSubscriber.java) | Dapr subscriber webhook. Exposes `GET /dapr/subscribe` and `POST /events/withdrawal-requests`. Calls `TransactionService` and publishes result |
| [`TransactionService.java`](transaction-service/src/main/java/com/bank/transaction/TransactionService.java) | Core banking logic: idempotency check → account lookup → balance check → atomic debit + audit |
| [`DaprPubSubService.java`](transaction-service/src/main/java/com/bank/transaction/DaprPubSubService.java) | Publishes `TransactionResult` to Dapr sidecar port `3501` topic `transaction-results` |
| [`Account.java`](transaction-service/src/main/java/com/bank/transaction/Account.java) | JPA `@Entity` mapped to `accounts` table in YugabyteDB |
| [`WithdrawalTransaction.java`](transaction-service/src/main/java/com/bank/transaction/WithdrawalTransaction.java) | JPA `@Entity` mapped to `withdrawal_transactions` table |
| [`AccountRepository.java`](transaction-service/src/main/java/com/bank/transaction/AccountRepository.java) | Spring Data JPA `JpaRepository<Account, String>` |
| [`TransactionRepository.java`](transaction-service/src/main/java/com/bank/transaction/TransactionRepository.java) | Spring Data JPA `JpaRepository<WithdrawalTransaction, Long>` with `findByRequestId()` |

---

## 8. Dapr Pub/Sub Internals

### 8.1 What Pub/Sub Means (The YouTube Channel Analogy)

**Pub/Sub** stands for **Publish / Subscribe**. It is an asynchronous messaging pattern where services talk to each other **without knowing who or where the other service is**.

#### The Real-World Analogy:
Think of a YouTuber and their audience:
- **The Publisher (YouTuber):** Uploads a video to a channel topic (e.g. `withdrawal-requests`). They don't personally call or email every viewer; they just post it.
- **The Topic / Channel:** The designated stream category managed by the broker.
- **The Subscribers (Viewers):** Anyone who clicked "Subscribe" automatically receives the notification and consumes the video whenever they are online.

#### Direct HTTP (Phone Call) vs. Pub/Sub (Notice Board):

| Feature | Direct HTTP REST | Pub/Sub Pattern (Dapr + Redis) |
|---|---|---|
| **Analogy** | **A direct phone call** | **Pinning a notice to a shared board** |
| **Coupling** | Service A must hardcode Service B's exact IP address and port | Neither service knows the other exists. They only know the **topic name** |
| **If Service B is down?** | Call fails immediately (`500 Error`), transaction is lost | Message waits safely in Redis Streams until Service B boots back up |
| **Adding new services** | Must modify Service A's code to call the new service | Simply add a new subscriber to the topic. Service A's code never changes |

### 8.2 The Relationship Between Dapr and Redis
- **Redis** is the **actual message broker** (the storage engine holding the streams in memory).
- **Dapr** is the **universal adapter** sitting between Java and Redis.
- Spring Boot never imports Jedis or Redis client libraries. It just sends standard HTTP POST requests to `http://localhost:3500`. Dapr translates those calls into Redis Stream commands (`XADD`, `XREAD`).
- If tomorrow the team switches to **Apache Kafka** or **RabbitMQ**, zero Java code changes are needed — only `pubsub.yaml` is updated.

### 8.3 Component Configuration

**File**: [`components/pubsub.yaml`](components/pubsub.yaml)

```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: pubsub
spec:
  type: pubsub.redis
  version: v1
  metadata:
  - name: redisHost
    value: redis:6379
  - name: redisPassword
    value: ""
```

The `name: pubsub` is what microservices reference in their Dapr publish URLs: `/v1.0/publish/pubsub/<topic>`.

### 8.4 Subscription Flow Diagram

```mermaid
sequenceDiagram
    participant WS as Withdrawal Service (:8081)
    participant WD as withdrawal-dapr (:3500)
    participant Redis as Redis Streams (:6379)
    participant TD as transaction-dapr (:3501)
    participant TS as Transaction Service (:8082)

    Note over WD,TS: Startup: Dapr discovers subscriptions
    WD->>WS: GET /dapr/subscribe
    WS-->>WD: [{ topic: "transaction-results", route: "/events/transaction-results" }]
    TD->>TS: GET /dapr/subscribe
    TS-->>TD: [{ topic: "withdrawal-requests", route: "/events/withdrawal-requests" }]

    Note over WD,TD: Runtime: Message flow
    WS->>WD: POST /v1.0/publish/pubsub/withdrawal-requests
    WD->>Redis: XADD withdrawal-requests { data }
    Redis-->>TD: XREAD withdrawal-requests
    TD->>TS: POST /events/withdrawal-requests (CloudEvent)
    TS->>TD: POST /v1.0/publish/pubsub/transaction-results
    TD->>Redis: XADD transaction-results { data }
    Redis-->>WD: XREAD transaction-results
    WD->>WS: POST /events/transaction-results (CloudEvent)
```

---

## 9. Temporal Workflow State Machine & Error Recovery

### 9.1 Temporal Java SDK Reference & Architecture

The project uses the official **Temporal Java SDK** (`io.temporal:temporal-sdk` version `1.24.1`). The SDK architecture is structured as follows:

| SDK Class / Annotation | Location | Role in Architecture |
|---|---|---|
| `WorkflowServiceStubs` | `TemporalConfig.java` | Manages the gRPC communication channel to `temporal-server:7233` |
| `WorkflowClient` | `TemporalConfig.java` | High-level client used to start workflows and dispatch asynchronous signals |
| `WorkerFactory` & `Worker` | `TemporalConfig.java` | Background worker that polls `WITHDRAWAL_TASK_QUEUE` for tasks |
| `@WorkflowInterface` | `WithdrawalWorkflow.java` | Marks the Java interface as a Temporal Workflow definition |
| `@WorkflowMethod` | `WithdrawalWorkflow.java` | Marks the main entry method (`executeWithdrawal`) |
| `@SignalMethod` | `WithdrawalWorkflow.java` | Marks the asynchronous signal callback (`transactionResultSignal`) |
| `@QueryMethod` | `WithdrawalWorkflow.java` | Marks synchronous state query inspection (`getStatus`) |
| `@ActivityInterface` | `WithdrawalActivity.java` | Marks interface for side-effect operations executed by workers |
| `Workflow.await()` | `WithdrawalWorkflowImpl.java` | Non-blocking durable wait until a predicate is satisfied |
| `RetryOptions` | `WithdrawalWorkflowImpl.java` | Defines initial interval, backoff coefficient, and maximum attempts |
| `TestWorkflowEnvironment` | `ActivityRetryTest.java` | In-memory Temporal test server for zero-dependency unit tests |

### 9.2 Temporal Workflow State Machine

The `WithdrawalWorkflow` execution passes through these states:

```mermaid
stateDiagram-v2
    [*] --> INITIALIZED : Workflow started by WithdrawalController

    INITIALIZED --> PUBLISHING_REQUEST : activity.publishWithdrawalRequest() called

    PUBLISHING_REQUEST --> PUBLISHING_REQUEST : Activity fails (network error)\nTemporal auto-retries (up to 3 attempts)

    PUBLISHING_REQUEST --> WAITING_FOR_RESULT : Activity succeeds\nEvent published to Dapr

    WAITING_FOR_RESULT --> SIGNAL_RECEIVED : transactionResultSignal() called\nby DaprEventSubscriber

    WAITING_FOR_RESULT --> FAILED_TIMEOUT : Workflow.await() 60-second timeout\nNo signal received

    SIGNAL_RECEIVED --> COMPLETED : transactionResult != null\nWorkflow.await() unblocks

    COMPLETED --> [*] : TransactionResult returned to\nWithdrawalController HTTP response

    FAILED_TIMEOUT --> [*] : Timeout result returned to\nWithdrawalController HTTP response
```

### 9.3 How Temporal Self-Heals and Recovers from 4 Critical Failure Scenarios

#### Scenario 1: Network Glitch / Temporary Dapr Outage (Automatic Activity Retry)
* **Problem**: The Withdrawal Service attempts to publish an event to Dapr, but the network drops or the sidecar is temporarily restarting.
* **Standard REST Failure**: The HTTP call throws a `500 Internal Server Error`, the transaction aborts, and the user receives a failed payment screen.
* **How Temporal Fixes It**:
  ```java
  ActivityOptions.newBuilder()
      .setStartToCloseTimeout(Duration.ofSeconds(10))
      .setRetryOptions(RetryOptions.newBuilder()
          .setInitialInterval(Duration.ofSeconds(1))
          .setMaximumAttempts(3)
          .build())
      .build();
  ```
  Temporal intercepts the activity exception, pauses for 1 second, and attempts again. If attempt 2 or 3 succeeds, the workflow proceeds normally. The user never experiences an error.

#### Scenario 2: Server Crash Mid-Workflow (Event Sourcing & State Recovery)
* **Problem**: While the workflow is paused at `Workflow.await(60s)` waiting for the transaction result, the server crashes or loses power.
* **Standard Application Failure**: In-memory state is erased. The server has no memory of the in-flight transaction.
* **How Temporal Fixes It**:
  Every workflow step is recorded as an immutable event in `temporal-db` (PostgreSQL). When `withdrawal-service` restarts, its Worker reconnects to `WITHDRAWAL_TASK_QUEUE`. Temporal replays the event history from the database, reinstates local variables, and resumes execution right at `Workflow.await()` without re-executing already completed activities.

#### Scenario 3: Downstream Service Hangs or Never Responds (Deterministic Timeout)
* **Problem**: Transaction Service crashes and never publishes a result back to Dapr.
* **Standard Application Failure**: Thread hangs indefinitely, leaking resources until memory exhausts.
* **How Temporal Fixes It**:
  ```java
  boolean received = Workflow.await(Duration.ofSeconds(60), () -> this.transactionResult != null);
  if (!received || this.transactionResult == null) {
      this.status = "FAILED_TIMEOUT";
      return new TransactionResult(..., "FAILED", "Transaction timed out while waiting for result signal", null);
  }
  ```
  When the 60-second timer expires, `Workflow.await()` safely unblocks, updates state to `FAILED_TIMEOUT`, and returns a structured failure response to the client.

#### Scenario 4: User Double-Clicks "Submit" (Deterministic Workflow ID Deduplication)
* **Problem**: A customer accidentally double-clicks "Submit", sending two identical requests within 50ms.
* **Standard Application Failure**: Two threads run simultaneously, potentially debiting the customer twice.
* **How Temporal Fixes It**:
  ```java
  WorkflowOptions options = WorkflowOptions.newBuilder()
      .setWorkflowId("withdrawal-" + request.getRequestId())
      .build();
  ```
  Temporal enforces that only one active execution can exist per `WorkflowId`. The second request is automatically deduplicated, completely preventing double debiting.

---

## 10. Test Suite

The project includes **5 automated tests** across two test classes, covering all 4 demonstration scenarios. All pass with `BUILD SUCCESS`.

### 10.1 In-Memory H2 Testing Architecture (Fast & Clean Testing)

When running automated tests with `mvn test`, the test suite uses [`src/test/resources/application.properties`](transaction-service/src/test/resources/application.properties) to point to an in-memory **H2 Database** instead of a live YugabyteDB instance:

```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
spring.jpa.hibernate.ddl-auto=create-drop
```

#### Why In-Memory Testing is Used:
1. **Speed**: Tables are created directly in RAM in under 50ms, allowing all 5 tests to run in seconds without disk or network latency.
2. **PostgreSQL Compatibility (`MODE=PostgreSQL`)**: H2 mimics PostgreSQL's SQL dialect, ensuring identical behavior to YugabyteDB's YSQL.
3. **Total Test Isolation (`create-drop`)**: Each test run creates a fresh database in memory and drops it upon completion. Tests can never pollute or delete live data in YugabyteDB.
4. **Zero-Dependency Testing**: Developers or CI/CD pipelines can run `mvn test` on any laptop without needing Docker, Redis, or YugabyteDB installed.

### 10.2 `ActivityRetryTest.java` (Withdrawal Service)

**Tests Temporal Activity retry mechanism using an in-memory Temporal test server.**

- **Test 3A**: Activity fails on attempt 1 (simulates `NETWORK_ERROR`)
- **Test 3B**: Activity fails on attempt 2 (simulates `TIMEOUT_ERROR`)  
- **Test 3C**: Activity succeeds on attempt 3 — asserts result equals `"Action completed on attempt 3"`

The test uses `TestWorkflowEnvironment.newInstance()` — a full in-process Temporal server that runs with accelerated timers (no real `sleep()` required).

### `TransactionServiceTest.java` (Transaction Service)

**Integration tests run against a real in-memory H2 database via Spring Boot test context.**

| Test Method | Scenario | Assertions |
|---|---|---|
| `testSuccessfulWithdrawal()` | ₹10,000 account, ₹3,000 withdrawal | Status = `SUCCESS`, result balance = ₹7,000, DB balance = ₹7,000, audit row saved |
| `testInsufficientBalance()` | ₹1,000 account, ₹5,000 attempt | Status = `FAILED`, message = `"Insufficient balance"`, DB balance unchanged at ₹1,000, `FAILED` audit row saved |
| `testDuplicateWithdrawalPrevention()` | ₹10,000 account, same `requestId` sent twice | First call succeeds (₹8,000), second call returns `"Duplicate..."`, DB balance still ₹8,000 (not ₹6,000) |

### Running Tests
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
& "E:\apache-maven-3.9.11-bin\apache-maven-3.9.11\bin\mvn.cmd" test
```

Expected output:
```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 (withdrawal-service)
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 (transaction-service)
BUILD SUCCESS
```

---

## 11. API Reference

### Withdrawal Service — `http://localhost:8081`

#### `POST /api/withdrawals` — Synchronous Withdrawal
Starts a Temporal workflow and waits for the full result before responding.

**Request Body:**
```json
{
  "requestId": "req-001",
  "accountId": "ACC-001",
  "amount": 3000.00
}
```
**Success Response (`200 OK`):**
```json
{
  "requestId": "req-001",
  "accountId": "ACC-001",
  "amount": 3000.0,
  "status": "SUCCESS",
  "message": "Withdrawal successful",
  "balance": 7000.0
}
```
**Failure Response (`200 OK`):**
```json
{
  "requestId": "req-001",
  "accountId": "ACC-001",
  "amount": 15000.0,
  "status": "FAILED",
  "message": "Insufficient balance",
  "balance": 7000.0
}
```

#### `POST /api/withdrawals/async` — Asynchronous Withdrawal
Starts workflow and returns immediately without waiting.

**Response (`202 Accepted`):**
```json
{ "workflowId": "withdrawal-req-001", "requestId": "req-001", "status": "RUNNING" }
```

#### `GET /api/withdrawals/{requestId}/status` — Query Workflow Status
```
GET /api/withdrawals/req-001/status
```
**Response:**
```json
{ "workflowId": "withdrawal-req-001", "status": "COMPLETED" }
```

---

### Transaction Service — `http://localhost:8082`

#### `POST /api/accounts` — Create or Update Account
```json
{ "accountId": "ACC-001", "balance": 10000.00 }
```
**Response (`200 OK`):**
```json
{ "accountId": "ACC-001", "balance": 10000 }
```

#### `GET /api/accounts/{id}` — Get Account Balance
```
GET /api/accounts/ACC-001
```
**Response (`200 OK`):**
```json
{ "accountId": "ACC-001", "balance": 7000.00 }
```

#### `GET /api/transactions` — List All Transactions
Returns all rows from `withdrawal_transactions` ordered by `created_at`.

---

### Internal Dapr Webhook Endpoints (Called by Dapr Sidecars Only)

| Service | Method | Path | Purpose |
|---|---|---|---|
| Withdrawal (8081) | `GET` | `/dapr/subscribe` | Returns subscription to `transaction-results` |
| Withdrawal (8081) | `POST` | `/events/transaction-results` | Receives result, signals Temporal |
| Transaction (8082) | `GET` | `/dapr/subscribe` | Returns subscription to `withdrawal-requests` |
| Transaction (8082) | `POST` | `/events/withdrawal-requests` | Receives request, processes withdrawal |

---

## 12. Design Principles & Problem Solving

| Distributed Systems Problem | Solution Used in This POC |
|---|---|
| **Double payment / duplicate request** | Two-tier idempotency: Temporal deduplicates workflow IDs; YugabyteDB has `UNIQUE` constraint on `request_id` |
| **Network failure mid-transaction** | Temporal Activity retries (up to 3 attempts, exponential backoff) |
| **Server crash during processing** | Temporal persists all workflow state to `temporal-db` — workflow resumes exactly where it left off after restart |
| **Broker unavailability** | Dapr at-least-once delivery with Redis Stream durability; `DaprPubSubService` has a direct HTTP fallback |
| **Insufficient funds** | Pre-debit balance check inside `@Transactional` block; writes `FAILED` audit row; balance unchanged |
| **Audit & compliance** | Every withdrawal attempt (success or failure) is recorded in `withdrawal_transactions` with timestamp |
| **Broker vendor lock-in** | Dapr Pub/Sub abstraction — swap Redis for Kafka/RabbitMQ/Azure Service Bus by changing only `pubsub.yaml` |
| **Service coupling** | Services communicate only through Dapr topics and Temporal signals — no direct HTTP calls between services |

---

## 13. How to Run

### Prerequisites
- Docker Desktop (running)
- Java 17 JDK
- Apache Maven 3.9+

### Step 1: Start Infrastructure
```bash
cd c:\Users\Sonux\Desktop\banking-poc
docker-compose up -d
```
Wait 20–30 seconds for all 7 containers to initialize.

### Step 2: Start Microservices

**Terminal 1 — Transaction Service:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
cd c:\Users\Sonux\Desktop\banking-poc\transaction-service
mvn spring-boot:run
```

**Terminal 2 — Withdrawal Service:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
cd c:\Users\Sonux\Desktop\banking-poc\withdrawal-service
mvn spring-boot:run
```

### Step 3: Seed an Account
```bash
curl -X POST http://localhost:8082/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"accountId": "ACC-001", "balance": 10000}'
```

### Step 4: Run the Demonstrations

**Test 1 — Successful Withdrawal:**
```bash
curl -X POST http://localhost:8081/api/withdrawals \
  -H "Content-Type: application/json" \
  -d '{"requestId": "req-001", "accountId": "ACC-001", "amount": 3000}'
```

**Test 2 — Insufficient Balance:**
```bash
curl -X POST http://localhost:8081/api/withdrawals \
  -H "Content-Type: application/json" \
  -d '{"requestId": "req-002", "accountId": "ACC-001", "amount": 50000}'
```

**Test 3 — Activity Retry (automated, runs in `mvn test`)**

**Test 4 — View Temporal Signals in Workflow History:**
Open [http://localhost:8080](http://localhost:8080), select namespace `default`, and click on any completed `WithdrawalWorkflow`. You will see the full event timeline including the Signal event.

### Step 5: Verify Database State
```sql
-- Connect via DBeaver: localhost:5433, DB: yugabyte, User: yugabyte
SELECT * FROM accounts;
SELECT * FROM withdrawal_transactions ORDER BY created_at;
```

---

## 14. Troubleshooting Guide

| Symptom | Root Cause | Fix |
|---|---|---|
| `temporal-server` exits immediately | `DB=postgresql` or `DB=postgres` in docker-compose | Change to `DB=postgres12` |
| `ysqlsh: connection refused at localhost:5433` | YugabyteDB binds to hostname IP, not `127.0.0.1` | Ensure `hostname: banking-yugabyte` and `PGHOST=banking-yugabyte` are set in docker-compose |
| Spring Boot `@PathVariable` not found | Spring Boot 3.2+ needs `-parameters` compiler flag | Add `<parameters>true</parameters>` to `maven-compiler-plugin` in parent `pom.xml` |
| Dapr sidecar shows `waiting on port 8081` for a long time | Spring Boot hasn't started yet | Normal — sidecar waits until app starts; it discovers the app when Spring Boot is ready |
| Duplicate workflow ID error | Same `requestId` submitted while a workflow is still running | Wait for the previous workflow to complete, or use a new `requestId` |
| Docker named pipe error | Docker Desktop daemon not running | Start Docker Desktop and wait for the whale icon to become stable |
| Temporal workflow times out (60s) | Transaction Service is not running or Dapr is not routing | Ensure both services are running and check `transaction-dapr` logs |

---

*Document generated from live code and verified end-to-end. All 5 tests pass with `BUILD SUCCESS`. All 7 Docker containers verified healthy.*
