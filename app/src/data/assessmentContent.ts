export interface SubskillDefinition {
  id: string;
  label: string;
  tiers: [string, string, string, string, string]; // tier 0-4
}

export interface SectionDefinition {
  id: string;
  label: string;
  subskills: SubskillDefinition[];
}

// ─── Axis 1: CS Fundamentals (8 sections, 30 subskills) ───

export const CS_FUNDAMENTALS_SECTIONS: SectionDefinition[] = [
  {
    id: "cs_fundamentals.web",
    label: "Web Fundamentals",
    subskills: [
      {
        id: "cs_fundamentals.web.http",
        label: "HTTP/HTTPS",
        tiers: [
          "I'm not familiar with HTTP.",
          "I know HTTP is the protocol browsers use to communicate with servers.",
          "I can explain the request/response lifecycle, common methods (GET, POST, PUT, DELETE), and status code ranges.",
          "I understand the differences between HTTP/1.1, HTTP/2, and HTTP/3, and can reason about when each matters.",
          "I have debugged HTTP-level issues (headers, redirects, caching behavior) in a real application.",
        ],
      },
      {
        id: "cs_fundamentals.web.dns",
        label: "DNS",
        tiers: [
          "I'm not familiar with DNS.",
          "I know DNS maps domain names to IP addresses.",
          "I can explain the recursive lookup chain (resolver, root, TLD, authoritative) and what TTL means.",
          "I understand how DNS caching works at multiple levels and can reason about propagation delays.",
          "I have configured DNS records (A, CNAME, MX, TXT) and troubleshot DNS-related issues.",
        ],
      },
      {
        id: "cs_fundamentals.web.cookies_sessions",
        label: "Cookies & Sessions",
        tiers: [
          "I'm not familiar with cookies or sessions.",
          "I know cookies store data in the browser.",
          "I can explain the difference between cookies, localStorage, and sessionStorage, and when to use each.",
          "I understand HttpOnly, Secure, and SameSite flags and can reason about the security tradeoffs.",
          "I have implemented session management and handled edge cases like cookie expiration or cross-subdomain sharing.",
        ],
      },
      {
        id: "cs_fundamentals.web.rest",
        label: "REST Principles",
        tiers: [
          "I'm not familiar with REST.",
          "I know REST is a style for building APIs.",
          "I can explain statelessness, resource-based URLs, and the meaning of common status codes.",
          "I can reason about good vs bad REST API design and identify non-RESTful patterns.",
          "I have designed and iterated on REST APIs in a production or project context.",
        ],
      },
      {
        id: "cs_fundamentals.web.auth",
        label: "Authentication (JWT, OAuth, Sessions)",
        tiers: [
          "I'm not familiar with authentication mechanisms.",
          "I know authentication verifies who a user is.",
          "I can explain the difference between session-based and token-based auth (JWT), and what OAuth is for.",
          "I can reason about tradeoffs between auth strategies (stateful vs stateless, refresh token rotation, OAuth flows).",
          "I have implemented authentication end-to-end, including secure token storage and logout handling.",
        ],
      },
      {
        id: "cs_fundamentals.web.cors",
        label: "CORS",
        tiers: [
          "I'm not familiar with CORS.",
          "I know CORS errors happen when a browser blocks a cross-origin request.",
          "I can explain the same-origin policy, preflight requests, and what CORS headers do.",
          "I understand when and why preflight is triggered, and can reason about credentialed requests and wildcard restrictions.",
          "I have configured CORS correctly on a server and debugged related browser errors.",
        ],
      },
      {
        id: "cs_fundamentals.web.websockets",
        label: "WebSockets",
        tiers: [
          "I'm not familiar with WebSockets.",
          "I know WebSockets allow real-time communication between browser and server.",
          "I can explain how WebSocket differs from HTTP (persistent connection, full-duplex) and when to use it.",
          "I can reason about scalability challenges (sticky sessions, connection state) and alternatives like SSE or long polling.",
          "I have built a feature using WebSockets and handled concerns like reconnection or message ordering.",
        ],
      },
      {
        id: "cs_fundamentals.web.caching_headers",
        label: "Caching Headers (ETag, Cache-Control)",
        tiers: [
          "I'm not familiar with browser caching headers.",
          "I know browsers cache responses to avoid re-fetching resources.",
          "I can explain Cache-Control directives (max-age, no-cache, no-store) and what ETags are used for.",
          "I can reason about cache invalidation strategies and the tradeoff between freshness and performance.",
          "I have tuned caching headers on a real application and observed the impact.",
        ],
      },
    ],
  },
  {
    id: "cs_fundamentals.os",
    label: "Operating Systems",
    subskills: [
      {
        id: "cs_fundamentals.os.processes_threads",
        label: "Processes vs Threads",
        tiers: [
          "I'm not familiar with processes or threads.",
          "I know processes and threads are units of execution.",
          "I can explain the difference (memory isolation, overhead) and when to prefer one over the other.",
          "I understand how the OS manages process state (running, blocked, ready) and what a context switch costs.",
          "I have written multi-threaded or multi-process code and reasoned about shared state and isolation.",
        ],
      },
      {
        id: "cs_fundamentals.os.scheduling",
        label: "Scheduling",
        tiers: [
          "I'm not familiar with CPU scheduling.",
          "I know the OS decides which process runs on the CPU.",
          "I can explain common scheduling algorithms (FIFO, round-robin, priority-based) and what preemption means.",
          "I can reason about scheduling tradeoffs (fairness vs throughput vs latency) and how modern OSes handle them.",
          "I have profiled or diagnosed CPU scheduling issues (e.g., thread starvation, high context-switch overhead).",
        ],
      },
      {
        id: "cs_fundamentals.os.memory",
        label: "Memory Management",
        tiers: [
          "I'm not familiar with OS-level memory management.",
          "I know programs use memory that is managed by the OS.",
          "I can explain the stack vs heap, virtual memory, and what a page fault is.",
          "I understand paging, segmentation, and how virtual address translation works (TLB, page tables).",
          "I have debugged memory issues (leaks, segfaults, excessive heap usage) in a real program.",
        ],
      },
      {
        id: "cs_fundamentals.os.file_systems",
        label: "File Systems",
        tiers: [
          "I'm not familiar with file system internals.",
          "I know the OS manages files on disk.",
          "I can explain inodes, file descriptors, and the difference between files and directories at the OS level.",
          "I understand how file systems handle durability (journaling, fsync) and can reason about persistence guarantees.",
          "I have worked with file system details in code (e.g., handling file descriptors, buffered vs unbuffered I/O).",
        ],
      },
      {
        id: "cs_fundamentals.os.ipc",
        label: "Inter-Process Communication",
        tiers: [
          "I'm not familiar with IPC.",
          "I know processes can communicate with each other.",
          "I can explain common IPC mechanisms: pipes, sockets, shared memory, signals.",
          "I can reason about the tradeoffs between IPC mechanisms in terms of performance and complexity.",
          "I have implemented IPC in a project (e.g., a pipe-based CLI pipeline or Unix socket communication).",
        ],
      },
      {
        id: "cs_fundamentals.os.deadlocks",
        label: "Deadlocks",
        tiers: [
          "I'm not familiar with deadlocks.",
          "I know deadlocks happen when processes get stuck waiting on each other.",
          "I can explain the four necessary conditions (mutual exclusion, hold-and-wait, no preemption, circular wait).",
          "I can reason about deadlock prevention, avoidance, and detection strategies.",
          "I have encountered and resolved a deadlock in a concurrent program.",
        ],
      },
    ],
  },
  {
    id: "cs_fundamentals.networking",
    label: "Networking",
    subskills: [
      {
        id: "cs_fundamentals.networking.osi",
        label: "OSI Model",
        tiers: [
          "I'm not familiar with the OSI model.",
          "I know the OSI model is a layered networking framework.",
          "I can name the 7 layers and give a rough description of each.",
          "I can map real protocols to their layers and explain how encapsulation works.",
          "I have used OSI layer thinking to diagnose a real networking issue.",
        ],
      },
      {
        id: "cs_fundamentals.networking.tcp_udp",
        label: "TCP vs UDP",
        tiers: [
          "I'm not familiar with TCP or UDP.",
          "I know TCP is reliable and UDP is faster but unreliable.",
          "I can explain the TCP handshake, acknowledgments, and flow control, and when UDP is preferred.",
          "I can reason about TCP's performance implications (head-of-line blocking, congestion control) and when to choose UDP.",
          "I have built or diagnosed something at the transport layer level (e.g., custom protocol over UDP, TCP socket programming).",
        ],
      },
      {
        id: "cs_fundamentals.networking.tls",
        label: "TLS/SSL Handshake",
        tiers: [
          "I'm not familiar with TLS.",
          "I know TLS encrypts traffic between client and server.",
          "I can explain the high-level handshake (cipher negotiation, certificate exchange, session key establishment).",
          "I understand symmetric vs asymmetric encryption roles in TLS, certificate chains, and what mTLS adds.",
          "I have configured TLS certificates, debugged handshake errors, or worked with certificate pinning.",
        ],
      },
      {
        id: "cs_fundamentals.networking.cdns",
        label: "CDNs",
        tiers: [
          "I'm not familiar with CDNs.",
          "I know CDNs serve static content from servers close to users.",
          "I can explain edge servers, cache hit/miss, and how CDNs reduce origin load.",
          "I can reason about cache invalidation on CDNs, origin pull vs push, and when CDNs don't help.",
          "I have configured or debugged CDN behavior in a real application.",
        ],
      },
      {
        id: "cs_fundamentals.networking.latency_bandwidth",
        label: "Latency vs Bandwidth",
        tiers: [
          "I'm not familiar with latency and bandwidth concepts.",
          "I know latency is delay and bandwidth is throughput.",
          "I can explain why latency often matters more than bandwidth for interactive applications.",
          "I can reason about the latency budget in a request (DNS + TCP + TLS + server + transfer) and where to optimize.",
          "I have measured and optimized latency in a real system.",
        ],
      },
    ],
  },
  {
    id: "cs_fundamentals.databases",
    label: "Databases",
    subskills: [
      {
        id: "cs_fundamentals.databases.acid",
        label: "ACID Properties",
        tiers: [
          "I'm not familiar with ACID.",
          "I know ACID stands for Atomicity, Consistency, Isolation, Durability.",
          "I can explain what each property means and why it matters for data integrity.",
          "I understand how isolation levels (read uncommitted, read committed, repeatable read, serializable) trade consistency for performance.",
          "I have reasoned about or debugged transaction isolation issues (dirty reads, phantom reads, lost updates) in a real app.",
        ],
      },
      {
        id: "cs_fundamentals.databases.indexing",
        label: "Indexing",
        tiers: [
          "I'm not familiar with database indexes.",
          "I know indexes speed up database queries.",
          "I can explain how a B-tree index works and the basic tradeoff (faster reads, slower writes, extra storage).",
          "I can reason about composite indexes, covering indexes, and when an index won't be used by the query planner.",
          "I have added or tuned indexes on a real database and measured the impact with EXPLAIN or equivalent.",
        ],
      },
      {
        id: "cs_fundamentals.databases.sql_nosql",
        label: "SQL vs NoSQL",
        tiers: [
          "I'm not familiar with the difference between SQL and NoSQL.",
          "I know SQL databases use tables and NoSQL databases use other structures.",
          "I can explain the main NoSQL categories (document, key-value, column-family, graph) and their primary use cases.",
          "I can reason about when to choose SQL vs NoSQL given consistency, schema flexibility, and query pattern requirements.",
          "I have made and defended a SQL vs NoSQL architectural decision in a project.",
        ],
      },
    ],
  },
  {
    id: "cs_fundamentals.dsa",
    label: "Data Structures & Algorithms",
    subskills: [
      {
        id: "cs_fundamentals.dsa.big_o",
        label: "Big-O Intuition",
        tiers: [
          "I'm not familiar with Big-O notation.",
          "I know Big-O describes how runtime grows with input size.",
          "I can identify the time and space complexity of common algorithms and data structure operations.",
          "I can reason about amortized complexity and recognize when constant factors matter in practice.",
          "I consistently analyze complexity during design and have optimized real code based on this reasoning.",
        ],
      },
      {
        id: "cs_fundamentals.dsa.graph_traversal",
        label: "Graph Traversal (BFS/DFS)",
        tiers: [
          "I'm not familiar with graph traversal algorithms.",
          "I know BFS and DFS are ways to traverse graphs.",
          "I can implement BFS and DFS and explain when each is appropriate.",
          "I can apply BFS/DFS to solve problems (shortest path, cycle detection, topological sort) and reason about complexity.",
          "I have applied graph algorithms to real problems in projects or competitive contexts.",
        ],
      },
    ],
  },
  {
    id: "cs_fundamentals.security",
    label: "Security Basics",
    subskills: [
      {
        id: "cs_fundamentals.security.common_vulns",
        label: "Common Vulnerabilities (XSS, CSRF, SQLi)",
        tiers: [
          "I'm not familiar with common web vulnerabilities.",
          "I know XSS, CSRF, and SQL injection are common web vulnerabilities.",
          "I can explain how each attack works and the standard mitigations.",
          "I can reason about defense-in-depth and identify where these vulnerabilities are likely to appear in an app's architecture.",
          "I have audited code for these vulnerabilities or implemented proper mitigations in a real project.",
        ],
      },
      {
        id: "cs_fundamentals.security.password_storage",
        label: "Password Storage",
        tiers: [
          "I'm not familiar with password storage best practices.",
          "I know passwords should not be stored in plaintext.",
          "I can explain why hashing is used, what salting does, and why bcrypt/argon2 are preferred over MD5/SHA1.",
          "I understand the role of work factors and can reason about the tradeoff between security and hash cost.",
          "I have implemented correct password hashing in a real authentication system.",
        ],
      },
    ],
  },
  {
    id: "cs_fundamentals.concurrency",
    label: "Concurrency",
    subskills: [
      {
        id: "cs_fundamentals.concurrency.race_conditions",
        label: "Race Conditions & Thread Safety",
        tiers: [
          "I'm not familiar with race conditions.",
          "I know race conditions happen when threads access shared data unsafely.",
          "I can explain what makes code thread-unsafe and how mutexes or locks prevent races.",
          "I can reason about lock granularity, lock contention, and alternatives like lock-free data structures or immutability.",
          "I have debugged a race condition or designed a thread-safe component in a real system.",
        ],
      },
      {
        id: "cs_fundamentals.concurrency.async_await",
        label: "Async/Await & Event Loops",
        tiers: [
          "I'm not familiar with async programming.",
          "I know async/await is used to write non-blocking code.",
          "I can explain how an event loop works and how async/await differs from threads.",
          "I can reason about when async I/O helps vs hurts, and understand concepts like back-pressure and the thread pool behind async runtimes.",
          "I have built an async system and debugged issues like event loop starvation or unhandled promise rejections.",
        ],
      },
    ],
  },
  {
    id: "cs_fundamentals.compilers_runtimes",
    label: "Compilers & Runtimes",
    subskills: [
      {
        id: "cs_fundamentals.compilers_runtimes.gc",
        label: "Garbage Collection",
        tiers: [
          "I'm not familiar with garbage collection.",
          "I know garbage collection automatically frees unused memory.",
          "I can explain mark-and-sweep and reference counting at a high level.",
          "I understand generational GC, GC pauses, and how to reason about GC pressure in a running application.",
          "I have tuned GC settings or diagnosed GC-related performance issues in a real application.",
        ],
      },
      {
        id: "cs_fundamentals.compilers_runtimes.jit",
        label: "JIT Compilation",
        tiers: [
          "I'm not familiar with JIT compilation.",
          "I know JIT compiles code at runtime for better performance.",
          "I can explain how JIT differs from ahead-of-time compilation and why it can outperform both interpreted and statically compiled code.",
          "I understand JIT warm-up, deoptimization, and how to reason about JIT behavior affecting latency.",
          "I have worked in a JIT-compiled environment (JVM, V8, CLR) and reasoned about JIT behavior in performance-sensitive code.",
        ],
      },
    ],
  },
];

// ─── Axis 2: System Design (11 sections, 22 subskills) ───

export const SYSTEM_DESIGN_SECTIONS: SectionDefinition[] = [
  {
    id: "system_design.scalability",
    label: "Scalability",
    subskills: [
      {
        id: "system_design.scalability.horizontal_scaling",
        label: "Horizontal Scaling & Stateless Design",
        tiers: [
          "I'm not familiar with horizontal scaling.",
          "I know horizontal scaling means adding more machines.",
          "I can explain why stateless services scale horizontally more easily and how sessions complicate this.",
          "I can reason about the full implications of statelessness (external session stores, idempotency, shared nothing architecture).",
          "I have designed or refactored a service to be stateless and horizontally scalable.",
        ],
      },
      {
        id: "system_design.scalability.sharding",
        label: "Sharding & Consistent Hashing",
        tiers: [
          "I'm not familiar with sharding.",
          "I know sharding splits data across multiple databases.",
          "I can explain range-based vs hash-based sharding and what consistent hashing solves.",
          "I can reason about hot spots, rebalancing, and cross-shard queries as failure modes of sharding.",
          "I have designed or worked with a sharded data layer and reasoned about its failure modes.",
        ],
      },
    ],
  },
  {
    id: "system_design.caching",
    label: "Caching",
    subskills: [
      {
        id: "system_design.caching.patterns",
        label: "Cache Patterns",
        tiers: [
          "I'm not familiar with caching patterns.",
          "I know caching stores frequently accessed data in fast memory.",
          "I can explain cache-aside, write-through, and write-behind patterns and their basic tradeoffs.",
          "I can reason about cache invalidation strategies, consistency guarantees, and the scenarios where each pattern breaks down.",
          "I have implemented caching in a production-like system and dealt with invalidation bugs or cache stampedes.",
        ],
      },
      {
        id: "system_design.caching.eviction",
        label: "Eviction Policies",
        tiers: [
          "I'm not familiar with cache eviction policies.",
          "I know caches have limited space and must evict entries.",
          "I can explain LRU and LFU and when each is appropriate.",
          "I can reason about eviction policy choice given access patterns (bursty vs uniform, recency vs frequency-dominated).",
          "I have configured or tuned eviction policies in Redis or a similar system.",
        ],
      },
    ],
  },
  {
    id: "system_design.distributed",
    label: "Distributed Systems",
    subskills: [
      {
        id: "system_design.distributed.cap",
        label: "CAP Theorem",
        tiers: [
          "I'm not familiar with the CAP theorem.",
          "I know CAP stands for Consistency, Availability, Partition tolerance.",
          "I can explain the CAP tradeoff and why you must choose between CP and AP during a partition.",
          "I understand why CAP is often misapplied, what PACELC adds, and how real systems (Cassandra, Zookeeper, etc.) position themselves.",
          "I have made architectural decisions informed by CAP/PACELC tradeoffs in a real or design context.",
        ],
      },
      {
        id: "system_design.distributed.consistency",
        label: "Consistency Models",
        tiers: [
          "I'm not familiar with consistency models.",
          "I know systems can have different levels of data consistency.",
          "I can explain eventual consistency vs strong consistency and give examples of each.",
          "I understand the full spectrum (eventual, monotonic read, read-your-writes, causal, linearizable) and can reason about what each guarantees.",
          "I have debugged a consistency issue in a distributed system or explicitly designed for a specific consistency model.",
        ],
      },
      {
        id: "system_design.distributed.idempotency",
        label: "Idempotency",
        tiers: [
          "I'm not familiar with idempotency.",
          "I know idempotent operations can be repeated safely.",
          "I can explain why idempotency matters in distributed systems (retries, at-least-once delivery).",
          "I can reason about how to design idempotent APIs and operations (idempotency keys, conditional writes).",
          "I have implemented idempotency in an API or message consumer in a real system.",
        ],
      },
    ],
  },
  {
    id: "system_design.databases_at_scale",
    label: "Databases at Scale",
    subskills: [
      {
        id: "system_design.databases_at_scale.read_replicas",
        label: "Read Replicas",
        tiers: [
          "I'm not familiar with read replicas.",
          "I know read replicas are copies of a database used for read traffic.",
          "I can explain replication lag and why reads from replicas may be stale.",
          "I can reason about when replication lag is acceptable, how to route reads intelligently, and what happens during failover.",
          "I have worked with a system using read replicas and handled replication lag in application logic.",
        ],
      },
      {
        id: "system_design.databases_at_scale.schema_migrations",
        label: "Schema Migrations at Scale",
        tiers: [
          "I'm not familiar with schema migrations at scale.",
          "I know database schemas need to be updated as applications evolve.",
          "I can explain why naive migrations (ADD COLUMN, DROP COLUMN) can cause downtime at scale.",
          "I understand techniques like expand/contract migrations, online schema change tools, and backward-compatible deploys.",
          "I have performed a zero-downtime schema migration on a live database.",
        ],
      },
    ],
  },
  {
    id: "system_design.messaging",
    label: "Messaging & Async",
    subskills: [
      {
        id: "system_design.messaging.delivery_semantics",
        label: "Delivery Semantics",
        tiers: [
          "I'm not familiar with message delivery semantics.",
          "I know messages can be delivered at-least-once or at-most-once.",
          "I can explain the three delivery semantics (at-most-once, at-least-once, exactly-once) and their implications.",
          "I can reason about why exactly-once is hard to achieve and how idempotent consumers approximate it.",
          "I have designed or debugged a messaging system around specific delivery guarantees.",
        ],
      },
      {
        id: "system_design.messaging.backpressure",
        label: "Backpressure",
        tiers: [
          "I'm not familiar with backpressure.",
          "I know systems can get overwhelmed if producers send data faster than consumers process it.",
          "I can explain what backpressure is and common strategies (bounded queues, rate limiting, consumer scaling).",
          "I can reason about backpressure propagation through a pipeline and how to design systems that degrade gracefully.",
          "I have implemented or tuned backpressure handling in a real data pipeline or messaging system.",
        ],
      },
    ],
  },
  {
    id: "system_design.api_design",
    label: "API Design",
    subskills: [
      {
        id: "system_design.api_design.rest_graphql_grpc",
        label: "REST vs GraphQL vs gRPC",
        tiers: [
          "I'm not familiar with API paradigm differences.",
          "I know REST, GraphQL, and gRPC are different ways to build APIs.",
          "I can explain the core model of each and their primary use cases.",
          "I can reason about the tradeoffs (overfetching, type safety, streaming, client coupling) and make a contextual recommendation.",
          "I have built APIs in at least two of these styles and experienced their tradeoffs in practice.",
        ],
      },
      {
        id: "system_design.api_design.rate_limiting",
        label: "Rate Limiting",
        tiers: [
          "I'm not familiar with rate limiting.",
          "I know rate limiting restricts how often clients can call an API.",
          "I can explain common algorithms (token bucket, sliding window, fixed window).",
          "I can reason about distributed rate limiting challenges (shared state, race conditions across nodes).",
          "I have implemented rate limiting in a service and handled edge cases like burst allowance and client feedback (429 headers).",
        ],
      },
    ],
  },
  {
    id: "system_design.infra",
    label: "Infrastructure & Deployment",
    subskills: [
      {
        id: "system_design.infra.containers",
        label: "Containers & Orchestration",
        tiers: [
          "I'm not familiar with containers.",
          "I know Docker packages applications into containers.",
          "I can explain what a container is, how images work, and what Kubernetes does at a high level.",
          "I can reason about container orchestration concepts (pods, services, ingress, rolling updates, resource limits).",
          "I have deployed and operated a containerized application on Kubernetes or equivalent.",
        ],
      },
      {
        id: "system_design.infra.deployment_strategies",
        label: "Deployment Strategies",
        tiers: [
          "I'm not familiar with deployment strategies.",
          "I know applications need to be deployed without taking down the site.",
          "I can explain blue-green and canary deployments and how they reduce deployment risk.",
          "I can reason about the infrastructure requirements for each strategy and how to roll back safely.",
          "I have executed or automated a blue-green or canary deployment in a real pipeline.",
        ],
      },
    ],
  },
  {
    id: "system_design.storage",
    label: "Storage Systems",
    subskills: [
      {
        id: "system_design.storage.object_block_file",
        label: "Object vs Block vs File Storage",
        tiers: [
          "I'm not familiar with storage types.",
          "I know there are different types of storage (like S3, hard drives, network file systems).",
          "I can explain the conceptual difference between object, block, and file storage and give examples of each.",
          "I can reason about which storage type to use given access patterns, latency requirements, and cost.",
          "I have designed a storage layer for a real application and selected storage type deliberately.",
        ],
      },
    ],
  },
  {
    id: "system_design.observability",
    label: "Observability",
    subskills: [
      {
        id: "system_design.observability.tracing",
        label: "Distributed Tracing",
        tiers: [
          "I'm not familiar with distributed tracing.",
          "I know distributed tracing tracks a request across multiple services.",
          "I can explain trace IDs, spans, and how tools like Jaeger or Zipkin stitch together a full trace.",
          "I can reason about instrumentation overhead, sampling strategies, and how to use traces to diagnose latency.",
          "I have instrumented a service for distributed tracing and used it to diagnose a real performance issue.",
        ],
      },
      {
        id: "system_design.observability.sla_slo_sli",
        label: "SLAs, SLOs, SLIs",
        tiers: [
          "I'm not familiar with SLAs, SLOs, or SLIs.",
          "I know SLA stands for Service Level Agreement.",
          "I can explain the difference between SLA (external contract), SLO (internal target), and SLI (the metric being measured).",
          "I can reason about error budgets, how to set meaningful SLOs, and how SLOs drive engineering priorities.",
          "I have defined SLOs for a service and used error budgets to inform release decisions.",
        ],
      },
    ],
  },
  {
    id: "system_design.reliability",
    label: "Reliability & Availability",
    subskills: [
      {
        id: "system_design.reliability.circuit_breakers",
        label: "Circuit Breakers",
        tiers: [
          "I'm not familiar with circuit breakers.",
          "I know circuit breakers prevent cascading failures.",
          "I can explain the three states (closed, open, half-open) and how a circuit breaker protects downstream services.",
          "I can reason about threshold tuning, fallback strategies, and the risk of a circuit breaker being too sensitive or too slow to open.",
          "I have implemented or configured a circuit breaker in a real service.",
        ],
      },
      {
        id: "system_design.reliability.retries_backoff",
        label: "Retries & Backoff",
        tiers: [
          "I'm not familiar with retry strategies.",
          "I know retrying failed requests can help recover from transient errors.",
          "I can explain exponential backoff and jitter and why naive retries can make failures worse.",
          "I can reason about retry budgets, idempotency requirements, and the interaction between retries and circuit breakers.",
          "I have implemented retry logic with backoff and reasoned about its effect on system stability.",
        ],
      },
    ],
  },
  {
    id: "system_design.security_at_scale",
    label: "Security at Scale",
    subskills: [
      {
        id: "system_design.security_at_scale.secrets",
        label: "Secrets Management",
        tiers: [
          "I'm not familiar with secrets management.",
          "I know secrets like API keys should not be hardcoded.",
          "I can explain common secrets management approaches (environment variables, vaults like HashiCorp Vault, cloud-native secret stores).",
          "I can reason about secret rotation, least privilege access, and audit logging for secret access.",
          "I have set up and operated secrets management infrastructure in a real deployment.",
        ],
      },
      {
        id: "system_design.security_at_scale.zero_trust",
        label: "Zero Trust & Network Segmentation",
        tiers: [
          "I'm not familiar with zero trust.",
          "I know zero trust means not automatically trusting internal network traffic.",
          "I can explain the zero trust model, network segmentation, and why perimeter security alone is insufficient.",
          "I can reason about implementing zero trust (identity-based access, mTLS between services, policy enforcement).",
          "I have designed or operated a system with zero trust principles applied.",
        ],
      },
    ],
  },
];

export const SECTION_COUNTS = {
  coreCs: CS_FUNDAMENTALS_SECTIONS.length,
  systemDesign: SYSTEM_DESIGN_SECTIONS.length,
} as const;
