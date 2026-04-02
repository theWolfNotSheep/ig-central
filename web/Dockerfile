FROM node:20-alpine AS deps
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

ARG BACKEND_BASE_URL
ARG NEXT_PUBLIC_APP_ENV=PROD
ENV BACKEND_BASE_URL=$BACKEND_BASE_URL
ENV NEXT_PUBLIC_APP_ENV=$NEXT_PUBLIC_APP_ENV

RUN echo "BACKEND_BASE_URL=$BACKEND_BASE_URL NEXT_PUBLIC_APP_ENV=$NEXT_PUBLIC_APP_ENV" \
  && test -n "$BACKEND_BASE_URL"

RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000

COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public
COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/node_modules ./node_modules

EXPOSE 3000
CMD ["npx", "next", "start", "-p", "3000"]
