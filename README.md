# Dapr Temporal POC: Temporal, Dapr Pub/Sub, and YugabyteDB

[![GitHub Repo](https://img.shields.io/badge/GitHub-aroma76%2Fdapr--temporal--poc-blue?logo=github)](https://github.com/aroma76/dapr-temporal-poc)

A simple, beginner-friendly Banking Proof of Concept (POC) demonstrating **Temporal Orchestration**, **Dapr Event-Driven Pub/Sub**, and **YugabyteDB Distributed SQL**.

---

## 1. Core Architecture & Workflow

```
[Client / Postman]
       │  POST /api/withdrawals
       ▼
┌─────────────────────────────────────────────────────────────┐
│                     WITHDRAWAL SERVICE                      │
│                                                             │
│  WithdrawalController                                       │
│       │ starts workflow                                     │
│       ▼                                                     │
│  WithdrawalWorkflow (Temporal)                              │
│       │ 1. Executes Activity                                │
│       ▼                                                     │
│  WithdrawalActivity ──► DaprPubSubService                   │
│                                │                            │
│                                │ publishes to topic:        │
│                                │ "withdrawal-requests"      │
└────────────────────────────────┼────────────────────────────┘
                                 ▼
                     ┌───────────────────────┐
                     │     DAPR PUB/SUB      │
                     └───────────────────────┘
                                 │
                                 │ delivers event
                                 ▼
┌─────────────────────────────────────────────────────────────┐
│                    TRANSACTION SERVICE                      │
│                                                             │
│  DaprEventSubscriber (POST /events/withdrawal-requests)     │
│       │                                                     │
│       ▼                                                     │
│  TransactionService (@Transactional)                        │
│       │                                                     │
│       ├─► 1. Check duplicate requestId (Idempotency)        │
│       ├─► 2. Check account balance                          │
│       ├─► 3. Deduct balance & record audit log              │
│       │                                                     │
│       ▼                                                     │
│  YugabyteDB (tables: 'accounts', 'withdrawal_transactions') │
│       │                                                     │
│       ▼                                                     │
│  DaprPubSubService                                          │
│       │ publishes to topic: "transaction-results"           │
└───────┼─────────────────────────────────────────────────────┘
        ▼
┌───────────────────────┐
│     DAPR PUB/SUB      │
└───────────────────────┘
        │
        │ delivers result
        ▼
┌─────────────────────────────────────────────────────────────┐
│                     WITHDRAWAL SERVICE                      │
│                                                             │
│  DaprEventSubscriber (POST /events/transaction-results)     │
│       │                                                     │
│       ▼ sends Temporal Signal                               │
│  WithdrawalWorkflow                                         │
│       │ 4. Unblocks from Workflow.await(...)                │
│       │ 5. Returns final TransactionResult                  │
│       ▼                                                     │
│  [Client receives HTTP 200 with TransactionResult]          │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Core Concepts Explained Simply

### Temporal
- **Workflow (`WithdrawalWorkflow`)**: A fault-tolerant, stateful coordinator that runs the withdrawal sequence deterministically. If the server crashes, Temporal resumes the workflow from the exact line of code.
- **Activity (`WithdrawalActivity`)**: Executes non-deterministic tasks and side effects (such as making network calls to Dapr Pub/Sub). Activities have built-in timeout and retry guarantees.
- **Signal (`transactionResultSignal`)**: An asynchronous message sent into a running workflow. In this POC, the workflow pauses at `Workflow.await(...)` until a Signal arrives containing the `TransactionResult`.

### Dapr Pub/Sub
- **Two Topics**:
  1. `withdrawal-requests`: Withdrawal Service publishes requests; Transaction Service consumes them.
  2. `transaction-results`: Transaction Service publishes outcomes; Withdrawal Service consumes them.
- **Decoupled**: Neither service calls the other's database or internal APIs directly. They communicate exclusively via event messages.

### YugabyteDB
- **Distributed SQL**: Fully compatible with PostgreSQL wire protocol (YSQL) on port `5433`.
- **Only 2 Tables**:
  - `accounts`: Stores `account_id` and `balance` (`BigDecimal`).
  - `withdrawal_transactions`: Audit log storing `request_id`, `account_id`, `amount`, `status`, `message`, and timestamp.
- **Idempotency**: Uses `requestId` to prevent duplicate deductions if the same request is sent multiple times.

---

## 3. Minimal Project Structure

```
dapr-temporal-poc/
├── pom.xml                               # Parent Maven aggregator
├── docker-compose.yml                    # Temporal, YugabyteDB, Redis, Temporal UI
├── Banking_POC.postman_collection.json   # Ready-to-use Postman test collection
├── components/                           # Dapr Pub/Sub and Subscription YAMLs
│   ├── pubsub.yaml
│   ├── subscription-withdrawal.yaml
│   └── subscription-results.yaml
│
├── withdrawal-service/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/bank/withdrawal/
│       │   ├── WithdrawalServiceApplication.java
│       │   ├── WithdrawalController.java     # REST API to initiate withdrawals
│       │   ├── WithdrawalWorkflow.java       # Temporal Workflow interface
│       │   ├── WithdrawalWorkflowImpl.java   # Workflow implementation
│       │   ├── WithdrawalActivity.java       # Temporal Activity interface
│       │   ├── WithdrawalActivityImpl.java   # Activity implementation
│       │   ├── DaprEventSubscriber.java      # Receives 'transaction-results'
│       │   ├── DaprPubSubService.java        # Publishes to 'withdrawal-requests'
│       │   ├── TemporalConfig.java           # Temporal worker & client setup
│       │   ├── WithdrawalRequest.java        # Input DTO
│       │   └── TransactionResult.java        # Result DTO
│       └── test/java/com/bank/withdrawal/
│           ├── ActivityRetryTest.java        # Test 3: Temporal Activity Retry
│           └── WorkflowSignalTest.java       # Test 4: Temporal Signal
│
└── transaction-service/
    ├── pom.xml
    └── src/
        ├── main/java/com/bank/transaction/
        │   ├── TransactionServiceApplication.java
        │   ├── TransactionController.java    # REST API to inspect accounts/txs
        │   ├── TransactionService.java       # Banking business logic & DB operations
        │   ├── AccountRepository.java        # Spring Data JPA for 'accounts'
        │   ├── TransactionRepository.java    # Spring Data JPA for 'withdrawal_transactions'
        │   ├── Account.java                  # JPA Entity: accounts
        │   ├── WithdrawalTransaction.java    # JPA Entity: withdrawal_transactions
        │   ├── DaprEventSubscriber.java      # Receives 'withdrawal-requests'
        │   ├── DaprPubSubService.java        # Publishes to 'transaction-results'
        │   ├── WithdrawalRequest.java        # Input DTO
        │   └── TransactionResult.java        # Result DTO
        └── test/java/com/bank/transaction/
            └── TransactionServiceTest.java   # Test 1 & 2: Success, Insufficient balance & Duplicate check
```

---

## 4. The 4 Main Test Cases

| # | Test Case | Scenario | Expected Outcome | Execution Tool |
|---|---|---|---|---|
| **Test 1** | **Successful Withdrawal** | Initial balance ₹10,000, Withdraw ₹3,000 | Balance becomes **₹7,000**, status `SUCCESS` | Postman / JUnit / YugabyteDB |
| **Test 2** | **Insufficient Balance** | Initial balance ₹1,000, Withdraw ₹5,000 | Balance remains **₹1,000**, status `FAILED` | Postman / JUnit / YugabyteDB |
| **Test 3** | **Temporal Activity Retry** | Attempt 1 fails, Attempt 2 fails, Attempt 3 succeeds | Temporal automatically retries until success | JUnit (`TestWorkflowEnvironment`) |
| **Test 4** | **Temporal Signal** | Workflow awaits result, receives `TransactionResult` signal | Workflow completes with signaled result | JUnit (`TestWorkflowEnvironment`) |

---

## 5. How to Run the Tests

### Quick Verification (Automated JUnit Tests)
Run from the root directory:
```bash
mvn clean test
```
All four tests execute automatically in ~1 minute using in-memory Temporal (`TestWorkflowEnvironment`) and an embedded database.

### Running Live Services for Presentation (100% Containerized)
1. Start the entire system (all 9 containers: YugabyteDB, Temporal Server, Temporal UI, Redis, 2 Dapr sidecars, Withdrawal Service, and Transaction Service):
   ```bash
   docker compose up -d
   ```
2. Verify all containers are up and running:
   ```bash
   docker ps
   ```
3. Access UIs and APIs:
   - Temporal Web UI: `http://localhost:8080`
   - Withdrawal Service API: `http://localhost:8081`
   - Transaction Service API: `http://localhost:8082`

*(Optional: If developing locally without containers, run `mvn spring-boot:run` in `withdrawal-service` and `transaction-service` after running `docker compose up -d yugabyte redis temporal-db temporal temporal-ui`).*

For step-by-step presentation instructions and demo scripts, see [PRESENTATION_GUIDE.md](file:///c:/Users/Sonux/Desktop/banking-poc/PRESENTATION_GUIDE.md).

