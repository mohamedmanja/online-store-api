# Harmony Store — API

Spring Boot REST API for the Harmony online store. Handles authentication, product catalogue, orders, payments, shipping, and file storage.

## Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17, Spring Boot 3.2 |
| Database | PostgreSQL + Spring Data JPA / Hibernate |
| Auth | JWT (HttpOnly cookie) + Spring Security + OAuth2 (Google, Facebook) |
| Payments | Stripe Checkout |
| Shipping | Shippo |
| File storage | MinIO (S3-compatible) |
| 2FA | TOTP (Google Authenticator) + Email OTP + SMS OTP |
| Email | Spring Mail (SMTP) |

## Project Structure

```
src/main/java/com/harmony/store/
├── controller/       REST controllers (one per domain)
├── service/          Business logic
├── repository/       Spring Data JPA repositories
├── model/            JPA entities
├── dto/              Request/response DTOs
└── config/           Security, JWT, OAuth2, exception handling
```

## API Endpoints

All routes are prefixed with `/api` (configured in `application.yml`).

### Auth — `/api/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/register` | Public | Register with email + password |
| POST | `/login` | Public | Login; returns user or 2FA pending token |
| POST | `/logout` | Public | Clears auth cookie |
| GET | `/me` | Required | Returns authenticated user |
| POST | `/2fa/verify` | Public | Complete 2FA login with OTP |
| POST | `/2fa/resend` | Public | Resend OTP for pending login |
| POST | `/forgot-password` | Public | Send password reset email |
| POST | `/reset-password` | Public | Reset password with token |
| GET | `/google` | Public | Initiate Google OAuth2 flow |
| GET | `/facebook` | Public | Initiate Facebook OAuth2 flow |

### Products — `/api/products`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/` | Public | List products (paginated, filterable) |
| GET | `/categories` | Public | List all categories |
| GET | `/{id}` | Public | Get single product |
| GET | `/admin/all` | Admin | List all products including inactive |
| POST | `/` | Admin | Create product (multipart with optional image) |
| PUT | `/{id}` | Admin | Update product |
| DELETE | `/{id}` | Admin | Delete product |
| POST | `/categories` | Admin | Create category |

Query params for `GET /`: `search`, `category` (slug), `sort` (`newest`/`price_asc`/`price_desc`), `page`, `limit`.

### Orders — `/api/orders`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/` | Required | List current user's orders |
| POST | `/from-session` | Required | Create order from completed Stripe session |
| GET | `/admin` | Admin | List all orders (paginated, filterable by status) |
| PUT | `/admin/{id}/status` | Admin | Update order status |

### Payments — `/api/payments`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/checkout` | Required | Create Stripe Checkout session |
| POST | `/webhook` | Public | Stripe webhook receiver |

### Shipping — `/api/shipping`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/rates` | Admin | Get shipping rate quotes (Shippo) |
| POST | `/shipments` | Admin | Create shipment + label |
| GET | `/orders/{orderId}` | Required | Get shipments for an order |
| POST | `/webhook` | Public | Shippo webhook receiver |

### Users — `/api/users`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/` | Admin | List all users (paginated, searchable) |
| PUT | `/{id}/role` | Admin | Change user role |
| GET | `/me` | Required | Get current user profile |
| PUT | `/me` | Required | Update profile (name, email) |
| POST | `/me/change-password` | Required | Change password |

### Addresses — `/api/addresses`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/` | Required | List user's saved addresses |
| POST | `/` | Required | Add address |
| PUT | `/{id}` | Required | Update address |
| DELETE | `/{id}` | Required | Delete address |

### Two-Factor Auth — `/api/2fa`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/status` | Required | Get 2FA status (enabled methods, default) |
| POST | `/totp/setup` | Required | Generate TOTP secret + QR code |
| POST | `/totp/enable` | Required | Confirm TOTP setup with OTP |
| POST | `/totp/disable` | Required | Disable TOTP |
| POST | `/email/enable` | Required | Enable email OTP |
| POST | `/email/disable` | Required | Disable email OTP |
| POST | `/sms/enable` | Required | Enable SMS OTP |
| POST | `/sms/disable` | Required | Disable SMS OTP |
| POST | `/verify` | Required | Verify OTP (for management actions) |
| PUT | `/default` | Required | Set default 2FA method |

### User Preferences — `/api/preferences`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/` | Required | Get preferences |
| PUT | `/` | Required | Update preferences |

## Authentication

The API uses **JWT stored in an HttpOnly cookie** (`auth_token`). On login, the cookie is set automatically and sent with every subsequent request.

**2FA login flow:**
1. `POST /auth/login` — if 2FA is enabled, returns `{ requires2FA: true, pendingToken, method }`
2. `POST /auth/2fa/verify` — submit the OTP + pendingToken to complete login

**OAuth2 flow:** redirect the browser to `/api/auth/google` or `/api/auth/facebook`. On success the callback sets the auth cookie and redirects to the frontend.

## Local Development

### Prerequisites

- Java 17+
- Maven
- PostgreSQL
- MinIO (or any S3-compatible storage)

### Environment

Copy the example config and fill in your values:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

Key environment variables (can be set in `application-local.yml` or as system env vars):

```
DATABASE_HOST          PostgreSQL host (default: localhost)
DATABASE_PORT          PostgreSQL port (default: 5432)
DATABASE_NAME          Database name (default: online_store)
DATABASE_USERNAME      (default: postgres)
DATABASE_PASSWORD

JWT_SECRET             At least 256-bit secret
FRONTEND_URL           e.g. http://localhost:3000

GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
FACEBOOK_APP_ID
FACEBOOK_APP_SECRET

SMTP_HOST
SMTP_PORT
SMTP_USER
SMTP_PASS
SMTP_FROM

STRIPE_SECRET_KEY
STRIPE_WEBHOOK_SECRET

SHIPPO_API_KEY
SHIPPO_WEBHOOK_SECRET

MINIO_ENDPOINT
MINIO_PORT
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
MINIO_URL              Public URL prefix for served files
```

### Run

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The API starts on `http://localhost:8080/api`.

### Build

```bash
mvn clean package -DskipTests
```

## Docker

```bash
# Build JAR first
mvn clean package -DskipTests

# Build image
docker build -t harmony-api .

# Run (UAT profile)
docker run -p 8080:8080 \
  -e DATABASE_HOST=... \
  -e DATABASE_PASSWORD=... \
  -e JWT_SECRET=... \
  -e STRIPE_SECRET_KEY=... \
  harmony-api
```

The Dockerfile uses the `uat` Spring profile by default.

## Database

PostgreSQL schema is managed by Hibernate (`ddl-auto: update`). The initial schema SQL is at `../db.sql`.

## Role Model

| Role | Permissions |
|---|---|
| `CUSTOMER` | Own profile, orders, addresses, preferences, 2FA |
| `ADMIN` | All of the above + product management, all orders, all users |
