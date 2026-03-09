# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **real-time collaboration system** (like Google Docs or Miro) using **Spring Boot + React**. The project is currently in the design/planning phase — no code has been implemented yet.

## Architecture Reference

The complete technical architecture is documented in [docs/collab-system.md](docs/collab-system.md). This includes:
- System layering (frontend/backend/infrastructure)
- Technology stack choices
- Module breakdown
- Data models
- Security and authentication design
- Deployment configuration

## Technology Stack

Based on the architecture document:

| Layer | Technology |
|-------|------------|
| Frontend | React 18 + TypeScript + Vite, Zustand/Redux Toolkit, Yjs (CRDT), WebSocket + STOMP |
| Backend | Spring Boot 3.x (Java 17+), Spring Security + JWT, WebSocket + STOMP |
| Real-time | Redis Pub/Sub for cross-instance broadcast |
| Persistence | PostgreSQL (documents/snapshots), MongoDB (incremental events) |
| Deployment | Docker + Docker-Compose / Kubernetes |

## Getting Started

Since no code exists yet, the first implementation steps are:

1. **Set up project structure** - Create `backend/` (Spring Boot) and `frontend/` (React) directories
2. **Configure local infrastructure** - Start Redis, PostgreSQL, and MongoDB via Docker-Compose
3. **Implement basic collaboration** - Yjs + WebSocket real-time sync between frontend and backend

## Key Implementation Points

- **CRDT Conflict Resolution**: Frontend uses Yjs for automatic conflict resolution. Backend only handles message relay and persistence.
- **WebSocket Authentication**: JWT token passed via STOMP headers, validated by `JwtChannelInterceptor`
- **Snapshot Strategy**: Incremental events stored in MongoDB, full snapshots periodically saved to PostgreSQL (every 5 minutes or 100 operations)

## Commands (To Be Determined)

Common commands will be defined once the project structure is created. Expected commands:
- Backend: `./mvnw` for Maven commands (build, test, run)
- Frontend: `npm` or `pnpm` for React development
- Docker: `docker-compose up` for local infrastructure
