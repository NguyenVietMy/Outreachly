# CS Student Stats Tracker — Assessment Spec
## Axes: CS Fundamentals & System Design

---

## Overview

This document defines the taxonomy, scoring model, and question content for two assessment axes in the CS student stats tracker: **CS Fundamentals** and **System Design**. These are two of five total axes (the others — Leetcode, Resume, Projects — are out of scope here).

---

## Assessment Model

### Hybrid: Self-Reported Confidence (A) + Tiered Mastery Milestones (D)

Each subskill is assessed using **4 tiered milestone statements** of increasing depth. The student selects the deepest milestone they have genuinely achieved. This grounds the score in real capability descriptions rather than abstract self-ratings.

Additionally, after completing a section, the student provides a **confidence modifier** (Low / Medium / High) reflecting how confident they feel in their selected milestone. This modifier can be used to weight or shade the score in the UI.

### Scoring

Each milestone tier maps to a numeric value:

| Tier | Label | Points |
|------|-------|--------|
| 0 | Not familiar | 0 |
| 1 | Heard of it / surface awareness | 1 |
| 2 | Understand it / can explain it | 2 |
| 3 | Can reason through tradeoffs | 3 |
| 4 | Have implemented / debugged / applied in practice | 4 |

The axis score is the average of all subskill scores, normalized to a 0–100 scale.

```
axis_score = (sum of all subskill points / (num_subskills * 4)) * 100
```

Section scores follow the same formula scoped to their subskills.

### Confidence Modifier

After each section (not each subskill), the student selects a confidence level:

- **Low** — "I might be overestimating myself here"
- **Medium** — "I feel fairly accurate"
- **High** — "I'm confident in my self-assessment"

This modifier is stored separately and can be used in the UI (e.g., visual indicator, score shading) but should not alter the raw score. It's a signal for the student and any reviewer, not a score adjustment.

---

## Axis 1: CS Fundamentals

### Sections and Subskills

---

#### 1. Web Fundamentals

**Subskills:** HTTP/HTTPS, DNS, Cookies & sessions, REST, Auth, CORS, WebSockets, HTTP/2 vs HTTP/3, Caching headers

**Milestone statements per subskill (example: HTTP/HTTPS)**

> These milestone patterns apply to all subskills. The specific content of each tier changes per subskill — see the full list below.

| Tier | Statement |
|------|-----------|
| 1 | I know HTTP is the protocol browsers use to communicate with servers |
| 2 | I can explain the request/response lifecycle, common methods (GET, POST, PUT, DELETE), and status code ranges |
| 3 | I understand the differences between HTTP/1.1, HTTP/2, and HTTP/3, and can reason about when each matters |
| 4 | I have debugged HTTP-level issues (headers, redirects, caching behavior) in a real application |

**DNS**

| Tier | Statement |
|------|-----------|
| 1 | I know DNS maps domain names to IP addresses |
| 2 | I can explain the recursive lookup chain (resolver → root → TLD → authoritative), and what TTL means |
| 3 | I understand how DNS caching works at multiple levels and can reason about propagation delays |
| 4 | I have configured DNS records (A, CNAME, MX, TXT) and troubleshot DNS-related issues |

**Cookies & sessions**

| Tier | Statement |
|------|-----------|
| 1 | I know cookies store data in the browser |
| 2 | I can explain the difference between cookies, localStorage, and sessionStorage, and when to use each |
| 3 | I understand HttpOnly, Secure, and SameSite flags and can reason about the security tradeoffs |
| 4 | I have implemented session management and handled edge cases like cookie expiration or cross-subdomain sharing |

**REST principles**

| Tier | Statement |
|------|-----------|
| 1 | I know REST is a style for building APIs |
| 2 | I can explain statelessness, resource-based URLs, and the meaning of common status codes |
| 3 | I can reason about good vs bad REST API design and identify non-RESTful patterns |
| 4 | I have designed and iterated on REST APIs in a production or project context |

**Authentication (JWT, OAuth, sessions)**

| Tier | Statement |
|------|-----------|
| 1 | I know authentication verifies who a user is |
| 2 | I can explain the difference between session-based and token-based auth (JWT), and what OAuth is for |
| 3 | I can reason about tradeoffs between auth strategies (stateful vs stateless, refresh token rotation, OAuth flows) |
| 4 | I have implemented authentication end-to-end, including secure token storage and logout handling |

**CORS**

| Tier | Statement |
|------|-----------|
| 1 | I know CORS errors happen when a browser blocks a cross-origin request |
| 2 | I can explain the same-origin policy, preflight requests, and what CORS headers do |
| 3 | I understand when and why preflight is triggered, and can reason about credentialed requests and wildcard restrictions |
| 4 | I have configured CORS correctly on a server and debugged related browser errors |

**WebSockets**

| Tier | Statement |
|------|-----------|
| 1 | I know WebSockets allow real-time communication between browser and server |
| 2 | I can explain how WebSocket differs from HTTP (persistent connection, full-duplex) and when to use it |
| 3 | I can reason about scalability challenges (sticky sessions, connection state) and alternatives like SSE or long polling |
| 4 | I have built a feature using WebSockets and handled concerns like reconnection or message ordering |

**Caching headers (ETag, Cache-Control)**

| Tier | Statement |
|------|-----------|
| 1 | I know browsers cache responses to avoid re-fetching resources |
| 2 | I can explain Cache-Control directives (max-age, no-cache, no-store) and what ETags are used for |
| 3 | I can reason about cache invalidation strategies and the tradeoff between freshness and performance |
| 4 | I have tuned caching headers on a real application and observed the impact |

---

#### 2. Operating Systems

**Subskills:** Processes vs threads, Context switching, Scheduling, Memory management, File systems, System calls, IPC, Deadlocks

**Processes vs threads**

| Tier | Statement |
|------|-----------|
| 1 | I know processes and threads are units of execution |
| 2 | I can explain the difference (memory isolation, overhead) and when to prefer one over the other |
| 3 | I understand how the OS manages process state (running, blocked, ready) and what a context switch costs |
| 4 | I have written multi-threaded or multi-process code and reasoned about shared state and isolation |

**Scheduling**

| Tier | Statement |
|------|-----------|
| 1 | I know the OS decides which process runs on the CPU |
| 2 | I can explain common scheduling algorithms (FIFO, round-robin, priority-based) and what preemption means |
| 3 | I can reason about scheduling tradeoffs (fairness vs throughput vs latency) and how modern OSes handle them |
| 4 | I have profiled or diagnosed CPU scheduling issues (e.g., thread starvation, high context-switch overhead) |

**Memory management**

| Tier | Statement |
|------|-----------|
| 1 | I know programs use memory that is managed by the OS |
| 2 | I can explain the stack vs heap, virtual memory, and what a page fault is |
| 3 | I understand paging, segmentation, and how virtual address translation works (TLB, page tables) |
| 4 | I have debugged memory issues (leaks, segfaults, excessive heap usage) in a real program |

**File systems**

| Tier | Statement |
|------|-----------|
| 1 | I know the OS manages files on disk |
| 2 | I can explain inodes, file descriptors, and the difference between files and directories at the OS level |
| 3 | I understand how file systems handle durability (journaling, fsync) and can reason about persistence guarantees |
| 4 | I have worked with file system details in code (e.g., handling file descriptors, buffered vs unbuffered I/O) |

**IPC (Inter-Process Communication)**

| Tier | Statement |
|------|-----------|
| 1 | I know processes can communicate with each other |
| 2 | I can explain common IPC mechanisms: pipes, sockets, shared memory, signals |
| 3 | I can reason about the tradeoffs between IPC mechanisms in terms of performance and complexity |
| 4 | I have implemented IPC in a project (e.g., a pipe-based CLI pipeline or Unix socket communication) |

**Deadlocks**

| Tier | Statement |
|------|-----------|
| 1 | I know deadlocks happen when processes get stuck waiting on each other |
| 2 | I can explain the four necessary conditions (mutual exclusion, hold-and-wait, no preemption, circular wait) |
| 3 | I can reason about deadlock prevention, avoidance, and detection strategies |
| 4 | I have encountered and resolved a deadlock in a concurrent program |

---

#### 3. Networking

**Subskills:** OSI model, TCP vs UDP, IP & subnets, Load balancers, NAT, CDNs, TLS handshake, Latency vs bandwidth

**OSI model**

| Tier | Statement |
|------|-----------|
| 1 | I know the OSI model is a layered networking framework |
| 2 | I can name the 7 layers and give a rough description of each |
| 3 | I can map real protocols to their layers and explain how encapsulation works |
| 4 | I have used OSI layer thinking to diagnose a real networking issue |

**TCP vs UDP**

| Tier | Statement |
|------|-----------|
| 1 | I know TCP is reliable and UDP is faster but unreliable |
| 2 | I can explain the TCP handshake, acknowledgments, and flow control, and when UDP is preferred |
| 3 | I can reason about TCP's performance implications (head-of-line blocking, congestion control) and when to choose UDP |
| 4 | I have built or diagnosed something at the transport layer level (e.g., custom protocol over UDP, TCP socket programming) |

**TLS/SSL handshake**

| Tier | Statement |
|------|-----------|
| 1 | I know TLS encrypts traffic between client and server |
| 2 | I can explain the high-level handshake (cipher negotiation, certificate exchange, session key establishment) |
| 3 | I understand symmetric vs asymmetric encryption roles in TLS, certificate chains, and what mTLS adds |
| 4 | I have configured TLS certificates, debugged handshake errors, or worked with certificate pinning |

**CDNs**

| Tier | Statement |
|------|-----------|
| 1 | I know CDNs serve static content from servers close to users |
| 2 | I can explain edge servers, cache hit/miss, and how CDNs reduce origin load |
| 3 | I can reason about cache invalidation on CDNs, origin pull vs push, and when CDNs don't help |
| 4 | I have configured or debugged CDN behavior in a real application |

**Latency vs bandwidth**

| Tier | Statement |
|------|-----------|
| 1 | I know latency is delay and bandwidth is throughput |
| 2 | I can explain why latency often matters more than bandwidth for interactive applications |
| 3 | I can reason about the latency budget in a request (DNS + TCP + TLS + server + transfer) and where to optimize |
| 4 | I have measured and optimized latency in a real system |

---

#### 4. Databases

**Subskills:** SQL vs NoSQL, ACID, Transactions & isolation levels, Indexing, Normalization, Query execution, Connection pooling

**ACID properties**

| Tier | Statement |
|------|-----------|
| 1 | I know ACID stands for Atomicity, Consistency, Isolation, Durability |
| 2 | I can explain what each property means and why it matters for data integrity |
| 3 | I understand how isolation levels (read uncommitted, read committed, repeatable read, serializable) trade consistency for performance |
| 4 | I have reasoned about or debugged transaction isolation issues (dirty reads, phantom reads, lost updates) in a real app |

**Indexing**

| Tier | Statement |
|------|-----------|
| 1 | I know indexes speed up database queries |
| 2 | I can explain how a B-tree index works and the basic tradeoff (faster reads, slower writes, extra storage) |
| 3 | I can reason about composite indexes, covering indexes, and when an index won't be used by the query planner |
| 4 | I have added or tuned indexes on a real database and measured the impact with EXPLAIN or equivalent |

**SQL vs NoSQL**

| Tier | Statement |
|------|-----------|
| 1 | I know SQL databases use tables and NoSQL databases use other structures |
| 2 | I can explain the main NoSQL categories (document, key-value, column-family, graph) and their primary use cases |
| 3 | I can reason about when to choose SQL vs NoSQL given consistency, schema flexibility, and query pattern requirements |
| 4 | I have made and defended a SQL vs NoSQL architectural decision in a project |

---

#### 5. Data Structures & Algorithms

**Subskills:** Big-O, Core structures (array/list/tree/graph/heap/hashmap), Sorting, Recursion & DP, Graph traversal (BFS/DFS)

**Big-O intuition**

| Tier | Statement |
|------|-----------|
| 1 | I know Big-O describes how runtime grows with input size |
| 2 | I can identify the time and space complexity of common algorithms and data structure operations |
| 3 | I can reason about amortized complexity and recognize when constant factors matter in practice |
| 4 | I consistently analyze complexity during design and have optimized real code based on this reasoning |

**Graph traversal**

| Tier | Statement |
|------|-----------|
| 1 | I know BFS and DFS are ways to traverse graphs |
| 2 | I can implement BFS and DFS and explain when each is appropriate |
| 3 | I can apply BFS/DFS to solve problems (shortest path, cycle detection, topological sort) and reason about complexity |
| 4 | I have applied graph algorithms to real problems in projects or competitive contexts |

---

#### 6. Security Basics

**Subskills:** Hashing vs encryption, XSS, CSRF, SQL injection, TLS, Password storage, Least privilege

**Common vulnerabilities (XSS, CSRF, SQLi)**

| Tier | Statement |
|------|-----------|
| 1 | I know XSS, CSRF, and SQL injection are common web vulnerabilities |
| 2 | I can explain how each attack works and the standard mitigations |
| 3 | I can reason about defense-in-depth and identify where these vulnerabilities are likely to appear in an app's architecture |
| 4 | I have audited code for these vulnerabilities or implemented proper mitigations in a real project |

**Password storage**

| Tier | Statement |
|------|-----------|
| 1 | I know passwords should not be stored in plaintext |
| 2 | I can explain why hashing is used, what salting does, and why bcrypt/argon2 are preferred over MD5/SHA1 |
| 3 | I understand the role of work factors and can reason about the tradeoff between security and hash cost |
| 4 | I have implemented correct password hashing in a real authentication system |

---

#### 7. Concurrency

**Subskills:** Race conditions, Mutexes & semaphores, Deadlocks (see OS), Async/await & event loops, Thread safety

**Race conditions & thread safety**

| Tier | Statement |
|------|-----------|
| 1 | I know race conditions happen when threads access shared data unsafely |
| 2 | I can explain what makes code thread-unsafe and how mutexes or locks prevent races |
| 3 | I can reason about lock granularity, lock contention, and alternatives like lock-free data structures or immutability |
| 4 | I have debugged a race condition or designed a thread-safe component in a real system |

**Async/await & event loops**

| Tier | Statement |
|------|-----------|
| 1 | I know async/await is used to write non-blocking code |
| 2 | I can explain how an event loop works and how async/await differs from threads |
| 3 | I can reason about when async I/O helps vs hurts, and understand concepts like back-pressure and the thread pool behind async runtimes |
| 4 | I have built an async system and debugged issues like event loop starvation or unhandled promise rejections |

---

#### 8. Compilers & Runtimes

**Subskills:** Interpreted vs compiled, JIT, Garbage collection, Memory leaks, Stack overflow

**Garbage collection**

| Tier | Statement |
|------|-----------|
| 1 | I know garbage collection automatically frees unused memory |
| 2 | I can explain mark-and-sweep and reference counting at a high level |
| 3 | I understand generational GC, GC pauses, and how to reason about GC pressure in a running application |
| 4 | I have tuned GC settings or diagnosed GC-related performance issues in a real application |

**JIT compilation**

| Tier | Statement |
|------|-----------|
| 1 | I know JIT compiles code at runtime for better performance |
| 2 | I can explain how JIT differs from ahead-of-time compilation and why it can outperform both interpreted and statically compiled code |
| 3 | I understand JIT warm-up, deoptimization, and how to reason about JIT behavior affecting latency |
| 4 | I have worked in a JIT-compiled environment (JVM, V8, CLR) and reasoned about JIT behavior in performance-sensitive code |

---

## Axis 2: System Design

### Sections and Subskills

---

#### 1. Scalability

**Subskills:** Vertical vs horizontal scaling, Stateless services, Sharding, Consistent hashing, Replication

**Horizontal scaling & stateless design**

| Tier | Statement |
|------|-----------|
| 1 | I know horizontal scaling means adding more machines |
| 2 | I can explain why stateless services scale horizontally more easily and how sessions complicate this |
| 3 | I can reason about the full implications of statelessness (external session stores, idempotency, shared nothing architecture) |
| 4 | I have designed or refactored a service to be stateless and horizontally scalable |

**Sharding & consistent hashing**

| Tier | Statement |
|------|-----------|
| 1 | I know sharding splits data across multiple databases |
| 2 | I can explain range-based vs hash-based sharding and what consistent hashing solves |
| 3 | I can reason about hot spots, rebalancing, and cross-shard queries as failure modes of sharding |
| 4 | I have designed or worked with a sharded data layer and reasoned about its failure modes |

---

#### 2. Caching

**Subskills:** Cache-aside, Write-through, Write-behind, Eviction policies, Cache invalidation, CDN caching, Redis vs Memcached

**Cache patterns**

| Tier | Statement |
|------|-----------|
| 1 | I know caching stores frequently accessed data in fast memory |
| 2 | I can explain cache-aside, write-through, and write-behind patterns and their basic tradeoffs |
| 3 | I can reason about cache invalidation strategies, consistency guarantees, and the scenarios where each pattern breaks down |
| 4 | I have implemented caching in a production-like system and dealt with invalidation bugs or cache stampedes |

**Eviction policies**

| Tier | Statement |
|------|-----------|
| 1 | I know caches have limited space and must evict entries |
| 2 | I can explain LRU and LFU and when each is appropriate |
| 3 | I can reason about eviction policy choice given access patterns (bursty vs uniform, recency vs frequency-dominated) |
| 4 | I have configured or tuned eviction policies in Redis or a similar system |

---

#### 3. Distributed Systems

**Subskills:** CAP theorem, Consistency models, Clock skew, Consensus (Raft/Paxos conceptual), Idempotency, Distributed transactions

**CAP theorem**

| Tier | Statement |
|------|-----------|
| 1 | I know CAP stands for Consistency, Availability, Partition tolerance |
| 2 | I can explain the CAP tradeoff and why you must choose between CP and AP during a partition |
| 3 | I understand why CAP is often misapplied, what PACELC adds, and how real systems (Cassandra, Zookeeper, etc.) position themselves |
| 4 | I have made architectural decisions informed by CAP/PACELC tradeoffs in a real or design context |

**Consistency models**

| Tier | Statement |
|------|-----------|
| 1 | I know systems can have different levels of data consistency |
| 2 | I can explain eventual consistency vs strong consistency and give examples of each |
| 3 | I understand the full spectrum (eventual, monotonic read, read-your-writes, causal, linearizable) and can reason about what each guarantees |
| 4 | I have debugged a consistency issue in a distributed system or explicitly designed for a specific consistency model |

**Idempotency**

| Tier | Statement |
|------|-----------|
| 1 | I know idempotent operations can be repeated safely |
| 2 | I can explain why idempotency matters in distributed systems (retries, at-least-once delivery) |
| 3 | I can reason about how to design idempotent APIs and operations (idempotency keys, conditional writes) |
| 4 | I have implemented idempotency in an API or message consumer in a real system |

---

#### 4. Databases at Scale

**Subskills:** Read replicas, Sharding (see Scalability), Hot spots, CQRS, Schema migrations at scale, SQL vs NoSQL for scale

**Read replicas**

| Tier | Statement |
|------|-----------|
| 1 | I know read replicas are copies of a database used for read traffic |
| 2 | I can explain replication lag and why reads from replicas may be stale |
| 3 | I can reason about when replication lag is acceptable, how to route reads intelligently, and what happens during failover |
| 4 | I have worked with a system using read replicas and handled replication lag in application logic |

**Schema migrations at scale**

| Tier | Statement |
|------|-----------|
| 1 | I know database schemas need to be updated as applications evolve |
| 2 | I can explain why naive migrations (ADD COLUMN, DROP COLUMN) can cause downtime at scale |
| 3 | I understand techniques like expand/contract migrations, online schema change tools, and backward-compatible deploys |
| 4 | I have performed a zero-downtime schema migration on a live database |

---

#### 5. Messaging & Async

**Subskills:** Message queues vs pub/sub, Kafka vs RabbitMQ, Fan-out, Backpressure, Delivery semantics, Dead letter queues

**Delivery semantics**

| Tier | Statement |
|------|-----------|
| 1 | I know messages can be delivered at-least-once or at-most-once |
| 2 | I can explain the three delivery semantics (at-most-once, at-least-once, exactly-once) and their implications |
| 3 | I can reason about why exactly-once is hard to achieve and how idempotent consumers approximate it |
| 4 | I have designed or debugged a messaging system around specific delivery guarantees |

**Backpressure**

| Tier | Statement |
|------|-----------|
| 1 | I know systems can get overwhelmed if producers send data faster than consumers process it |
| 2 | I can explain what backpressure is and common strategies (bounded queues, rate limiting, consumer scaling) |
| 3 | I can reason about backpressure propagation through a pipeline and how to design systems that degrade gracefully |
| 4 | I have implemented or tuned backpressure handling in a real data pipeline or messaging system |

---

#### 6. API Design

**Subskills:** REST vs GraphQL vs gRPC, Versioning, Rate limiting, Pagination, API gateways, Idempotent endpoints, Contract-first design

**REST vs GraphQL vs gRPC**

| Tier | Statement |
|------|-----------|
| 1 | I know REST, GraphQL, and gRPC are different ways to build APIs |
| 2 | I can explain the core model of each and their primary use cases |
| 3 | I can reason about the tradeoffs (overfetching, type safety, streaming, client coupling) and make a contextual recommendation |
| 4 | I have built APIs in at least two of these styles and experienced their tradeoffs in practice |

**Rate limiting**

| Tier | Statement |
|------|-----------|
| 1 | I know rate limiting restricts how often clients can call an API |
| 2 | I can explain common algorithms (token bucket, sliding window, fixed window) |
| 3 | I can reason about distributed rate limiting challenges (shared state, race conditions across nodes) |
| 4 | I have implemented rate limiting in a service and handled edge cases like burst allowance and client feedback (429 headers) |

---

#### 7. Infrastructure & Deployment

**Subskills:** Containers (Docker), Kubernetes basics, CI/CD pipelines, Blue-green & canary deployments, Infrastructure as code

**Containers & orchestration**

| Tier | Statement |
|------|-----------|
| 1 | I know Docker packages applications into containers |
| 2 | I can explain what a container is, how images work, and what Kubernetes does at a high level |
| 3 | I can reason about container orchestration concepts (pods, services, ingress, rolling updates, resource limits) |
| 4 | I have deployed and operated a containerized application on Kubernetes or equivalent |

**Deployment strategies**

| Tier | Statement |
|------|-----------|
| 1 | I know applications need to be deployed without taking down the site |
| 2 | I can explain blue-green and canary deployments and how they reduce deployment risk |
| 3 | I can reason about the infrastructure requirements for each strategy and how to roll back safely |
| 4 | I have executed or automated a blue-green or canary deployment in a real pipeline |

---

#### 8. Storage Systems

**Subskills:** Object vs block vs file storage, Blob storage patterns, Data lakes vs warehouses, Columnar vs row storage

**Object vs block vs file storage**

| Tier | Statement |
|------|-----------|
| 1 | I know there are different types of storage (like S3, hard drives, network file systems) |
| 2 | I can explain the conceptual difference between object, block, and file storage and give examples of each |
| 3 | I can reason about which storage type to use given access patterns, latency requirements, and cost |
| 4 | I have designed a storage layer for a real application and selected storage type deliberately |

---

#### 9. Observability

**Subskills:** Structured logging, Distributed tracing, Metrics & alerting, SLAs/SLOs/SLIs, Incident response

**Distributed tracing**

| Tier | Statement |
|------|-----------|
| 1 | I know distributed tracing tracks a request across multiple services |
| 2 | I can explain trace IDs, spans, and how tools like Jaeger or Zipkin stitch together a full trace |
| 3 | I can reason about instrumentation overhead, sampling strategies, and how to use traces to diagnose latency |
| 4 | I have instrumented a service for distributed tracing and used it to diagnose a real performance issue |

**SLAs, SLOs, SLIs**

| Tier | Statement |
|------|-----------|
| 1 | I know SLA stands for Service Level Agreement |
| 2 | I can explain the difference between SLA (external contract), SLO (internal target), and SLI (the metric being measured) |
| 3 | I can reason about error budgets, how to set meaningful SLOs, and how SLOs drive engineering priorities |
| 4 | I have defined SLOs for a service and used error budgets to inform release decisions |

---

#### 10. Reliability & Availability

**Subskills:** Load balancing, Health checks, Circuit breakers, Retries & exponential backoff, Chaos engineering, Failover & disaster recovery

**Circuit breakers**

| Tier | Statement |
|------|-----------|
| 1 | I know circuit breakers prevent cascading failures |
| 2 | I can explain the three states (closed, open, half-open) and how a circuit breaker protects downstream services |
| 3 | I can reason about threshold tuning, fallback strategies, and the risk of a circuit breaker being too sensitive or too slow to open |
| 4 | I have implemented or configured a circuit breaker in a real service |

**Retries & backoff**

| Tier | Statement |
|------|-----------|
| 1 | I know retrying failed requests can help recover from transient errors |
| 2 | I can explain exponential backoff and jitter and why naive retries can make failures worse |
| 3 | I can reason about retry budgets, idempotency requirements, and the interaction between retries and circuit breakers |
| 4 | I have implemented retry logic with backoff and reasoned about its effect on system stability |

---

#### 11. Security at Scale

**Subskills:** Service-to-service auth (mTLS, API keys), Secrets management, Network segmentation, DDoS mitigation, Zero trust

**Secrets management**

| Tier | Statement |
|------|-----------|
| 1 | I know secrets like API keys should not be hardcoded |
| 2 | I can explain common secrets management approaches (environment variables, vaults like HashiCorp Vault, cloud-native secret stores) |
| 3 | I can reason about secret rotation, least privilege access, and audit logging for secret access |
| 4 | I have set up and operated secrets management infrastructure in a real deployment |

**Zero trust & network segmentation**

| Tier | Statement |
|------|-----------|
| 1 | I know zero trust means not automatically trusting internal network traffic |
| 2 | I can explain the zero trust model, network segmentation, and why perimeter security alone is insufficient |
| 3 | I can reason about implementing zero trust (identity-based access, mTLS between services, policy enforcement) |
| 4 | I have designed or operated a system with zero trust principles applied |

---

## Data Schema (Suggested)

```ts
type Tier = 0 | 1 | 2 | 3 | 4;
type Confidence = 'low' | 'medium' | 'high';

interface SubskillAssessment {
  subskillId: string;       // e.g. "cs.web.http"
  tier: Tier;               // 0–4, student selected milestone
}

interface SectionAssessment {
  sectionId: string;        // e.g. "cs.web"
  confidence: Confidence;   // student confidence modifier
  subskills: SubskillAssessment[];
}

interface AxisAssessment {
  axisId: 'cs_fundamentals' | 'system_design';
  completedAt: string;      // ISO timestamp
  sections: SectionAssessment[];
}

// Derived scores (computed, not stored raw)
interface AxisScore {
  axisId: string;
  rawScore: number;         // 0–100
  sectionScores: { sectionId: string; score: number }[];
}
```

### Score computation

```ts
function computeScore(sections: SectionAssessment[]): number {
  const allSubskills = sections.flatMap(s => s.subskills);
  const total = allSubskills.reduce((sum, s) => sum + s.tier, 0);
  const max = allSubskills.length * 4;
  return Math.round((total / max) * 100);
}
```

---

## Implementation Notes for Coding Agents

- **Do not** use the confidence modifier to alter the raw score. Store it separately; surface it visually only.
- Milestone tiers are **ordinal** — the student selects the single deepest tier they qualify for, not multiple.
- Subskill IDs should follow the pattern `{axis}.{section}.{subskill}` for namespacing across axes.
- The full taxonomy above intentionally includes **representative subskills** with complete milestone statements. Remaining subskills (marked by section but without full milestones) should follow the same 4-tier pattern — generate their milestone statements using the axis section context as a guide.
- Section groupings are the natural unit for **UI pagination** — show one section at a time, collect confidence at the end of each.
- The assessment should be **resumable** — save state after each section so incomplete sessions aren't lost.
- **Do not** show the numeric score to the student during assessment — only reveal after completion to avoid anchoring bias on earlier answers.
