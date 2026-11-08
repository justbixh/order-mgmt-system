#!/usr/bin/env bash
# End-to-end demo: runs every saga scenario against the locally running system and checks the result.
# Usage: scripts/demo.sh            (services must be up, see README)
# Requires: curl, jq
set -euo pipefail

ORDER=${ORDER_URL:-http://localhost:8081}
INVENTORY=${INVENTORY_URL:-http://localhost:8082}
PAYMENT=${PAYMENT_URL:-http://localhost:8083}
NOTIFY=${NOTIFICATION_URL:-http://localhost:8084}
FAILURES=0

bold() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
stock() { curl -fs "$INVENTORY/api/inventory/products/$1" | jq -c '{available:.availableQuantity,reserved:.reservedQuantity}'; }

# place_order <product> <qty> <price> <payment-token> [idempotency-key]  -> prints order id
place_order() {
  local key_header=()
  [[ -n "${5:-}" ]] && key_header=(-H "Idempotency-Key: $5")
  curl -fs -X POST "$ORDER/api/orders" -H 'Content-Type: application/json' ${key_header[@]+"${key_header[@]}"} -d "{
    \"customerId\": \"cust-1\", \"customerEmail\": \"alice@example.com\", \"currency\": \"USD\",
    \"paymentMethod\": \"$4\",
    \"items\": [{\"productId\": \"$1\", \"quantity\": $2, \"unitPrice\": $3}]}" | jq -r .id
}

# wait_for_status <order-id> <status> [timeout-seconds]
wait_for_status() {
  local id=$1 want=$2 tries=${3:-30} status=""
  for ((i = 0; i < tries; i++)); do
    status=$(curl -fs "$ORDER/api/orders/$id" | jq -r .status)
    [[ "$status" == "$want" ]] && return 0
    sleep 1
  done
  echo "   last status: $status"; return 1
}

check() { # check <description> <command...>
  local desc=$1; shift
  if "$@"; then echo "   PASS  $desc"; else echo "   FAIL  $desc"; FAILURES=$((FAILURES + 1)); fi
}

show_history() { curl -fs "$ORDER/api/orders/$1" | jq -r '.history[] | "   \(.changedAt)  \(.status)  \(.reason // "")"'; }

bold "1. Happy path: stock reserved, payment succeeds, order confirmed, customer notified"
echo "   stock before: $(stock SKU-HEADPHONES)"
ID=$(place_order SKU-HEADPHONES 2 49.90 tok_success)
echo "   order $ID placed"
check "order reaches CONFIRMED" wait_for_status "$ID" CONFIRMED
show_history "$ID"
echo "   stock after:  $(stock SKU-HEADPHONES)  (2 sold: available -2, reserved back to 0)"
check "payment is COMPLETED" test "$(curl -fs $PAYMENT/api/payments/orders/$ID | jq -r .status)" = COMPLETED
check "confirmation notification stored" test "$(curl -fs "$NOTIFY/api/notifications?orderId=$ID" | jq -r '.[0].type')" = ORDER_CONFIRMED

bold "2. Payment declined: compensation releases the reserved stock"
echo "   stock before: $(stock SKU-KEYBOARD)"
ID=$(place_order SKU-KEYBOARD 3 89.00 tok_declined)
check "order reaches CANCELLED" wait_for_status "$ID" CANCELLED
show_history "$ID"
sleep 2
echo "   stock after:  $(stock SKU-KEYBOARD)  (must equal 'before': the reservation was released)"
check "payment is FAILED" test "$(curl -fs $PAYMENT/api/payments/orders/$ID | jq -r .status)" = FAILED
check "cancellation notification stored" test "$(curl -fs "$NOTIFY/api/notifications?orderId=$ID" | jq -r '.[0].type')" = ORDER_CANCELLED

bold "3. Out of stock: nothing reserved, order cancelled"
ID=$(place_order SKU-LIMITED 5 999.00 tok_success)
check "order reaches CANCELLED" wait_for_status "$ID" CANCELLED
show_history "$ID"
check "no payment was attempted for a cancelled order (placeholder only)" \
  test "$(curl -fs $PAYMENT/api/payments/orders/$ID | jq -r .status)" = CANCELLED

bold "4. Customer cancels before confirmation (tok_gateway_error keeps the payment stuck, so the order stays open)"
ID=$(place_order SKU-PHONE 1 599.00 tok_gateway_error)
sleep 1
curl -fs -X POST "$ORDER/api/orders/$ID/cancel" | jq -c '{status, failureReason}'
check "order is CANCELLED" wait_for_status "$ID" CANCELLED
sleep 2
echo "   stock: $(stock SKU-PHONE)"

bold "5. Idempotent order creation: same Idempotency-Key twice creates ONE order"
KEY="demo-$(date +%s)"
A=$(place_order SKU-HEADPHONES 1 49.90 tok_success "$KEY")
B=$(place_order SKU-HEADPHONES 1 49.90 tok_success "$KEY")
check "both calls returned the same order id" test "$A" = "$B"

bold "6. Gateway outage: consumer retries with backoff, then the event lands in the dead-letter topic"
ID=$(place_order SKU-PHONE 1 599.00 tok_gateway_error)
echo "   order $ID stays PENDING/INVENTORY_RESERVED while payment retries (~15s), then the inventory-events record is dead-lettered"
sleep 22
echo "   status: $(curl -fs $ORDER/api/orders/$ID | jq -r .status)  (inspect topic inventory-events.DLT in Kafka UI: http://localhost:8090)"
echo "   cancelling it to free the reserved stock:"
curl -fs -X POST "$ORDER/api/orders/$ID/cancel" | jq -c '{status}'

echo
if [[ $FAILURES -eq 0 ]]; then echo "ALL CHECKS PASSED"; else echo "$FAILURES CHECK(S) FAILED"; exit 1; fi
