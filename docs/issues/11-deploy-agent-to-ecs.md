# 11 — Deploy `agent/` to ECS behind the ALB

**Track:** D · Deploy  ·  **Blocked by:** 08, 10  ·  **Blocks:** —
**Estimate:** ~6h

## Goal

The agent serves production traffic. This is what separates "built" from "deployed".

## Changes

- **Terraform** `infra/modules/ecs_agent/` — modelled on the existing `infra/modules/ecs_api/`:
  - ECR repository (extend `infra/modules/ecr/`)
  - Task definition: 0.25 vCPU / 512 MB Fargate, port 8001
  - Service in the existing cluster, private subnets
  - Secrets from AWS Secrets Manager: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `QDRANT_URL`,
    `QDRANT_API_KEY`, `INTERNAL_TOKEN`, `LANGFUSE_*`
  - Health check on `/health`
  - CloudWatch log group
- **Networking:** the agent must **not** be publicly reachable. Options, in order of preference:
  1. Internal service discovery (Cloud Map) — api → agent over the private network, no ALB listener
  2. Internal-only ALB target group on a non-public listener

  The Java `/internal/**` routes must likewise be excluded from the public ALB listener rule
  (issue 06 security requirement).
- **`.github/workflows/deploy-agent.yml`** — mirror `deploy-api.yml`: build, `pytest`, `ruff`, push
  to ECR, update service. Path-filtered on `agent/**` so API pushes don't redeploy it.
- **Cost check:** record actual monthly delta. Budgeted ~$10–15/mo for the task; Qdrant Cloud free
  tier is $0.

## Rollout

1. Deploy the agent service; confirm healthy with no traffic
2. Confirm api → agent reachability from the running API task
3. Flip the API to the agent path
4. Watch error rate and p95 for 24h; the issue-08 fallback covers agent unavailability

## Acceptance criteria

- [ ] `terraform plan` clean; `apply` succeeds
- [ ] Agent task healthy; `/health` passing
- [ ] Agent **not** reachable from the public internet (verify from outside the VPC)
- [ ] `/internal/**` on the API not reachable from the public internet (verify)
- [ ] Production chat served by the agent path, end to end
- [ ] Langfuse receives production traces spanning both services
- [ ] Deploy workflow green on a real push
- [ ] Actual cost delta recorded below

## Verify

```bash
cd infra/environments/dev && terraform plan
curl https://<public-alb>/health          # expect failure/404 for the agent
curl https://<public-alb>/internal/context/profile?userId=1   # expect 404/403
# then exercise chat in the deployed frontend
```

## Result (fill in)

| Metric | Before | After |
|---|---|---|
| Chat p95 | __ ms | __ ms |
| Monthly infra cost | $__ | $__ |
