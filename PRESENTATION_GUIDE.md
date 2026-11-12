# Dapr Temporal POC — Presentation Guide

A step-by-step script and guide to confidently explain **Temporal (Workflow, Activity, Signal)**, **Dapr Pub/Sub**, and **YugabyteDB**.

---

## 1. Quick Executive Summary (1-Minute Pitch)

> *"In this project, we built a reliable Banking Withdrawal system using three modern distributed systems technologies:*
> 1. **Temporal** orchestrates the multi-step withdrawal workflow with automatic retries and durable state.
> 2. **Dapr Pub/Sub** decouples the Withdrawal Service and Transaction Service using event-driven topics (`withdrawal-requests` and `transaction-results`).
> 3. **YugabyteDB** provides distributed, ACID-compliant SQL storage with duplicate transaction prevention via `requestId`."

---

## 2. Core Concepts: What to Show & Explain

### Concept 1: Temporal Workflow vs. Activity
- **Where to show in code:**
  - Interface: [`WithdrawalWorkflow.java`](file:///c:/Users/Sonux/Desktop/banking-poc/withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalWorkflow.java)
  - Implementation: [`WithdrawalWorkflowImpl.java`](file:///c:/Users/Sonux/Desktop/banking-poc/withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalWorkflowImpl.java)
  - Activity: [`WithdrawalActivityImpl.java`](file:///c:/Users/Sonux/Desktop/banking-poc/withdrawal-service/src/main/java/com/bank/withdrawal/WithdrawalActivityImpl.java)
- **Key Talking Point:**
  > *"Workflows must be deterministic—they only orchestrate state and decisions. Any non-deterministic operation, such as network calls or publishing to Dapr Pub/Sub, MUST be encapsulated in a Temporal Activity. If an Activity fails, Temporal automatically retries it according to our `RetryOptions` without restarting the whole workflow."*

### Concept 2: Temporal Signal
- **Where to show in code:**
  - Signal method in `WithdrawalWorkflow`: `@SignalMethod void transactionResultSignal(TransactionResult result);`
  - Await condition in `WithdrawalWorkflowImpl`: `Workflow.await(Duration.ofSeconds(60), () -> this.transactionResult != null);`
  - Signal sender in [`DaprEventSubscriber.java`](file:///c:/Users/Sonux/Desktop/banking-poc/withdrawal-service/src/main/java/com/bank/withdrawal/DaprEventSubscriber.java):
    ```java
    WithdrawalWorkflow workflow = workflowClient.newWorkflowStub(WithdrawalWorkflow.class, workflowId);
    workflow.transactionResultSignal(result);
    ```
- **Key Talking Point:**
  > *"Instead of actively polling a database or blocking an HTTP thread, the workflow suspends itself with `Workflow.await`. When the Transaction Service finishes processing, Dapr delivers the result back to our subscriber, which fires a Signal. The Signal wakes up the workflow instantly, and the workflow returns the final result."*

### Concept 3: Dapr Pub/Sub
- **Where to show in code:**
  - Topic 1: `withdrawal-requests` in `DaprPubSubService.java`
  - Topic 2: `transaction-results` in `DaprPubSubService.java`
  - Subscriptions: `components/pubsub.yaml`, `components/subscription-*.yaml`, and `/dapr/subscribe` endpoint.
- **Key Talking Point:**
  > *"Dapr acts as a distributed sidecar. Our Java code only sends a simple HTTP POST to Dapr, and Dapr handles message delivery, retries, and broker integration (e.g. Redis, Kafka, or AWS SNS/SQS) without any broker-specific SDKs in our Java code."*

### Concept 4: YugabyteDB & Idempotency
- **Where to show in code:**
  - Entities: [`Account.java`](file:///c:/Users/Sonux/Desktop/banking-poc/transaction-service/src/main/java/com/bank/transaction/Account.java) and [`WithdrawalTransaction.java`](file:///c:/Users/Sonux/Desktop/banking-poc/transaction-service/src/main/java/com/bank/transaction/WithdrawalTransaction.java)
  - Deduplication: [`TransactionService.java`](file:///c:/Users/Sonux/Desktop/banking-poc/transaction-service/src/main/java/com/bank/transaction/TransactionService.java)
- **Key Talking Point:**
  > *"YugabyteDB is PostgreSQL-compatible and gives us distributed ACID transactions. We use `BigDecimal` for precision. To guarantee idempotency, we check `findByRequestId` before processing. If a duplicate request arrives, we return the existing result instead of deducting balance a second time."*

---

## 3. Walkthrough of the Four Main Test Cases

### Test 1: Successful Withdrawal (₹10,000 -> ₹7,000)

**Scenario:** Customer starts with ₹10,000, withdraws ₹3,000. Balance becomes ₹7,000.

#### Step 1: Seed Account
Send HTTP POST via Postman or curl:
```bash
curl -X POST http://localhost:8082/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"accountId": "ACC-101", "balance": 10000.00}'
```
*Expected Response:* `{"accountId":"ACC-101","balance":10000.00}`

#### Step 2: Execute Withdrawal
```bash
curl -X POST http://localhost:8081/api/withdrawals \
  -H "Content-Type: application/json" \
  -d '{"requestId": "req-t1-001", "accountId": "ACC-101", "amount": 3000.00}'
```
*Expected Response:*
```json
{
  "requestId": "req-t1-001",
  "accountId": "ACC-101",
  "amount": 3000.00,
  "status": "SUCCESS",
  "message": "Withdrawal successful",
  "balance": 7000.00
}
```

#### Step 3: Demonstrate in Temporal Web UI
1. Open browser: `http://localhost:8080`.
2. Look for Workflow ID `withdrawal-req-t1-001`.
3. Show:
   - State: **Completed**
   - History: `WorkflowTaskStarted` -> `ActivityTaskScheduled` (`publishWithdrawalRequest`) -> `WorkflowExecutionSignaled` (`transactionResultSignal`) -> `WorkflowExecutionCompleted`.

#### Step 4: Demonstrate in YugabyteDB
Check balance and audit log:
```sql
SELECT * FROM accounts WHERE account_id = 'ACC-101';
-- balance is 7000.00

SELECT * FROM withdrawal_transactions WHERE request_id = 'req-t1-001';
-- status is SUCCESS, amount is 3000.00
```

---

### Test 2: Insufficient Balance (₹1,000 -> Attempt ₹5,000)

**Scenario:** Customer has ₹1,000, attempts to withdraw ₹5,000. Transaction fails, balance stays ₹1,000.

#### Step 1: Seed Account
```bash
curl -X POST http://localhost:8082/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"accountId": "ACC-102", "balance": 1000.00}'
```

#### Step 2: Execute Withdrawal
```bash
curl -X POST http://localhost:8081/api/withdrawals \
  -H "Content-Type: application/json" \
  -d '{"requestId": "req-t2-002", "accountId": "ACC-102", "amount": 5000.00}'
```
*Expected Response:*
```json
{
  "requestId": "req-t2-002",
  "accountId": "ACC-102",
  "amount": 5000.00,
  "status": "FAILED",
  "message": "Insufficient balance",
  "balance": 1000.00
}
```

#### Step 3: Demonstrate Verification
- In YugabyteDB:
  ```sql
  SELECT * FROM accounts WHERE account_id = 'ACC-102';
  -- balance remains 1000.00

  SELECT * FROM withdrawal_transactions WHERE request_id = 'req-t2-002';
  -- status is FAILED, message is 'Insufficient balance'
  ```
- In Temporal UI: The workflow completed normally, returning the business failure result.

---

### Test 3: Temporal Activity Retry

**Scenario:** An Activity encounters transient network failures on Attempt 1 and Attempt 2, then automatically succeeds on Attempt 3 without any manual retry loop in Java code.

#### Option 1: Live Demo via Postman / Temporal Web UI (Recommended for Live Demo)
1. **Trigger via Postman or curl:**
   ```bash
   curl -X POST http://localhost:8081/api/withdrawals \
     -H "Content-Type: application/json" \
     -d '{"requestId": "retry-live-demo-001", "accountId": "ACC-101", "amount": 1000.00}'
   ```
2. **Open Temporal Web UI:** [http://localhost:8080](http://localhost:8080)
3. **Click on Workflow ID:** `withdrawal-retry-live-demo-001`
4. **Click on the Activity:** `PublishWithdrawalRequest` (or switch view to History/JSON)
   - **Attempts:** `3`
   - **Status:** `Completed`
   - **Last Failure:** `Simulated temporary network outage (attempt 2)` [NETWORK_ERROR]
5. **Show Container Logs:**
   ```bash
   docker logs withdrawal-service --tail 10
   ```
   *Logs will show:*
   ```text
   [WithdrawalActivityImpl] Simulating network outage on attempt: 1
   [WithdrawalActivityImpl] Simulating network outage on attempt: 2
   [WithdrawalActivityImpl] Attempt 3 succeeded! Publishing to Dapr.
   ```

#### Option 2: Automated In-Memory JUnit Test
Run the JUnit test directly from the terminal:
```bash
mvn test -pl withdrawal-service -Dtest=ActivityRetryTest
```

#### What to Show in the Output:
```
==================================================
Starting Test 3: Temporal Activity Retry
==================================================
[Test 3 ActivityRetry] Activity invocation attempt: 1
[Test 3 ActivityRetry] Attempt 1 failed! Simulating temporary network error.
[Test 3 ActivityRetry] Activity invocation attempt: 2
[Test 3 ActivityRetry] Attempt 2 failed! Simulating temporary timeout.
[Test 3 ActivityRetry] Activity invocation attempt: 3
[Test 3 ActivityRetry] Attempt 3 succeeded!
[Test 3 ActivityRetry] Workflow completed with result: SUCCESS_ON_ATTEMPT_3
[Test 3 ActivityRetry] Total activity attempts recorded: 3
==================================================
PASSED Test 3: Temporal Activity Retry Verified!
==================================================
```
*Key Point to Explain:*
> *"Notice how the workflow did not crash and no manual retry loop was written in Java. Temporal automatically managed the retry backoff and succeeded on the 3rd attempt."*

---

### Test 4: Temporal Signal

**Scenario:** The workflow starts, pauses at `Workflow.await`, verifies it is still running, receives a Signal, and completes.

#### How to Demonstrate:
Run the JUnit test:
```bash
mvn test -Dtest=WorkflowSignalTest
```

#### What to Show in the Output:
```
==================================================
Starting Test 4: Temporal Signal Verification
==================================================
[Test 4 Signal] Step 1: Starting workflow asynchronously...
[Test 4 Signal] Activity published withdrawal request for: req-signal-demo-001
[Test 4 Signal] Step 2 & 3: Current Workflow Status: WAITING_FOR_RESULT
[Test 4 Signal] Step 4: Delivering TransactionResult via Temporal Signal...
[Test 4 Signal] Step 5: Waiting for workflow completion...
[Test 4 Signal] Final Workflow Output: TransactionResult{requestId='req-signal-demo-001', accountId='ACC-101', amount=3000, status='SUCCESS', message='Withdrawal completed successfully', balance=7000}
==================================================
PASSED Test 4: Temporal Signal Successfully Handled!
==================================================
```
*Key Point to Explain:*
> *"This test proves the exact Signal lifecycle: the workflow reaches `WAITING_FOR_RESULT`, waits indefinitely without consuming CPU or thread resources, and instantly unblocks when `transactionResultSignal` is delivered."*

---

## 4. Frequent Presentation Questions & Confident Answers

**Q1: Why did you use Temporal instead of a standard Spring Boot service?**
> *"In a standard service, if a network outage or server crash occurs between publishing the event and updating the balance, the transaction enters an unknown state. Temporal provides durable execution: every step is recorded in event history. If the service restarts, Temporal resumes the workflow from the exact state without duplicate side-effects."*

**Q2: What is the difference between Temporal Activity and Workflow?**
> *"A Workflow contains pure orchestration logic and must be deterministic. An Activity contains the real-world side effects—such as database queries, HTTP calls, or message publishing—that may fail and need retries."*

**Q3: How does Dapr Pub/Sub help here?**
> *"Dapr abstracts the message broker. The code only knows about topics (`withdrawal-requests` and `transaction-results`). We can switch the underlying broker from Redis to Kafka, RabbitMQ, or AWS SQS with a single YAML change, without modifying one line of Java code."*

**Q4: How do you prevent duplicate withdrawals if the user clicks 'Withdraw' twice?**
> *"Each request carries a unique `requestId`. In `TransactionService.processWithdrawal`, we first query `transactionRepository.findByRequestId(requestId)`. If a record already exists, we immediately return the previous transaction result without deducting the account balance again."*

**Q5: Why YugabyteDB?**
> *"YugabyteDB combines the relational features and ACID guarantees of PostgreSQL with horizontal scalability and high availability. It is ideal for financial ledger records where consistency and precision (`BigDecimal`) are non-negotiable."*

