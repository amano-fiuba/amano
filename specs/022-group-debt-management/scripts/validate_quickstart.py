#!/usr/bin/env python3
"""API-level validation of specs/022-group-debt-management/quickstart.md (T056/T084).

Usage:
    python3 specs/022-group-debt-management/scripts/validate_quickstart.py <base_url> <db_container> [main|nobcra]

Runs against a throwaway PostgreSQL container and a real backend process without
touching any development database. Prints one line per check (PASS / FAIL / NOT-RUN)
and exits non-zero if any check fails.
"""

from __future__ import annotations

import datetime as dt
import http.cookiejar
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from decimal import Decimal

TZ = "America/Argentina/Buenos_Aires"
AWAITING_MSG = "Esta deuda ya tiene un pago esperando validación: esperá a que un administrador lo decida"


class Runner:
    def __init__(self, base_url: str, db_container: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.db_container = db_container
        self.passed = 0
        self.failed = 0
        self.not_run = 0

    def check(self, label: str, condition: bool, detail: str = "") -> None:
        status = "PASS" if condition else "FAIL"
        if condition:
            self.passed += 1
        else:
            self.failed += 1
        suffix = f" ({detail})" if detail else ""
        print(f"[{status}] {label}{suffix}", flush=True)

    def skip(self, label: str, reason: str) -> None:
        self.not_run += 1
        print(f"[NOT-RUN] {label} ({reason})", flush=True)

    def sql(self, query: str) -> str:
        cmd = [
            "docker", "exec", self.db_container,
            "psql", "-U", "amano", "-d", "amano", "-t", "-A", "-c", query,
        ]
        res = subprocess.run(cmd, check=True, capture_output=True, text=True)
        return res.stdout.strip()


class Session:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.user_id: int | None = None
        self.email: str | None = None

    def request(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        headers: dict | None = None,
        form: dict | None = None,
    ) -> tuple[int, dict | list | str | None]:
        url = f"{self.base_url}{path}"
        req_headers = dict(headers or {})
        data: bytes | None = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            req_headers.setdefault("Content-Type", "application/json")
        elif form is not None:
            data = urllib.parse.urlencode(form, doseq=True).encode("utf-8")
            req_headers.setdefault("Content-Type", "application/x-www-form-urlencoded")
        req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
        try:
            with self.opener.open(req, timeout=15) as resp:
                raw = resp.read().decode("utf-8")
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as err:
            raw = err.read().decode("utf-8")
            try:
                parsed = json.loads(raw) if raw else None
            except json.JSONDecodeError:
                parsed = raw
            return err.code, parsed


def register_and_login(runner: Runner, tag: str, first: str, last: str) -> Session:
    sess = Session(runner.base_url)
    email = f"{tag}.{uuid.uuid4().hex[:8]}@example.com"
    status, _ = sess.request(
        "POST",
        "/api/auth/register",
        form={
            "firstName": first,
            "lastName": last,
            "email": email,
            "password": "Password1!",
            "confirmPassword": "Password1!",
            "acceptTerms": "true",
            "currencies": ["ARS", "USD", "EUR"],
        },
    )
    if status != 201:
        raise RuntimeError(f"register failed for {email}: {status}")
    runner.sql(f"UPDATE users SET email_verified_at = now() WHERE email = '{email}';")
    status, data = sess.request("POST", "/api/auth/login", body={"email": email, "password": "Password1!"})
    if status != 200 or not isinstance(data, dict):
        raise RuntimeError(f"login failed for {email}: {status} {data}")
    sess.user_id = int(data["id"])
    sess.email = email
    return sess


def befriend(a: Session, b: Session) -> None:
    st, req = a.request("POST", "/api/users/me/friendship-requests", body={"recipientId": b.user_id})
    if st != 201 or not isinstance(req, dict):
        raise RuntimeError(f"friendship request failed: {st} {req}")
    st, _ = b.request("PUT", f"/api/users/me/friendship-requests/{req['id']}", body={"action": "ACCEPT"})
    if st != 200:
        raise RuntimeError(f"friendship accept failed: {st}")


def d(val: object) -> Decimal:
    return Decimal(str(val))


def sum_balances(balances_resp: dict, currency: str = "ARS") -> Decimal:
    for cur in balances_resp.get("currencies", []):
        if cur["currency"] == currency:
            return sum((d(b["netBalance"]) for b in cur["balances"]), Decimal("0.00"))
    return Decimal("0.00")


def member_balance(balances_resp: dict, member_id: int, member_type: str = "REGISTERED", currency: str = "ARS") -> Decimal:
    for cur in balances_resp.get("currencies", []):
        if cur["currency"] == currency:
            for b in cur["balances"]:
                if b["memberId"] == member_id and b["memberType"] == member_type:
                    return d(b["netBalance"])
    return Decimal("0.00")


def run_main(runner: Runner) -> None:
    today = dt.date.today().isoformat()
    yesterday = (dt.date.today() - dt.timedelta(days=1)).isoformat()

    # --- Setup ---
    ana = register_and_login(runner, "ana", "Ana", "López")
    juan = register_and_login(runner, "juan", "Juan", "Pérez")
    carla = register_and_login(runner, "carla", "Carla", "Gómez")
    outsider = register_and_login(runner, "out", "Luis", "Fuera")
    runner.check("Setup: registro, verificación y login de 4 usuarios", all(u.user_id for u in [ana, juan, carla, outsider]))

    befriend(ana, juan)
    befriend(ana, carla)
    runner.check("Setup: amistades Ana↔Juan y Ana↔Carla aceptadas", True)

    st, grp = ana.request(
        "POST",
        "/api/groups",
        body={
            "name": "Viaje Costa",
            "description": "Grupo quickstart",
            "primaryCurrency": "ARS",
            "members": [
                {"memberType": "REGISTERED", "identifier": juan.email},
                {"memberType": "REGISTERED", "identifier": carla.email},
            ],
        },
    )
    gid = grp["id"]
    runner.check("Setup: crear grupo ARS con 3 miembros registrados", st == 201 and grp.get("primaryCurrency") == "ARS", f"id={gid}")

    st, sim = ana.request(
        "POST",
        f"/api/groups/{gid}/members",
        body={"memberType": "SIMULATED", "name": "Abuela Rosa", "identifier": f"abuela.{uuid.uuid4().hex[:6]}@example.com"},
    )
    _, members = ana.request("GET", f"/api/groups/{gid}/members")
    sim_id = next(m["simulatedProfileId"] for m in members if m.get("simulatedProfileId") is not None)
    runner.check("Setup: agregar persona simulada al grupo", st == 201 and sim.get("member", {}).get("type") == "SIMULATED", f"simId={sim_id}")

    # --- Scenario 1: Shared Expense & Split Methods ---
    idem1 = str(uuid.uuid4())
    exp1_payload = {
        "amount": "100.00",
        "currency": "ARS",
        "date": today,
        "description": "Cena de grupo",
        "deviceTimeZone": TZ,
        "payerUserId": ana.user_id,
        "splitMethod": "EQUAL",
        "participants": [
            {"type": "REGISTERED", "userId": ana.user_id},
            {"type": "REGISTERED", "userId": juan.user_id},
            {"type": "REGISTERED", "userId": carla.user_id},
        ],
    }
    st, exp1 = ana.request("POST", f"/api/groups/{gid}/expenses", body=exp1_payload, headers={"Idempotency-Key": idem1})
    shares1 = sorted(d(s["amount"]) for s in exp1["shares"])
    debts1 = exp1["debts"]
    runner.check(
        "Escenario 1.1: gasto compartido 100 ARS EQUAL -> 201 y partes 33.33 / 33.33 / 33.34",
        st == 201 and shares1 == [Decimal("33.33"), Decimal("33.33"), Decimal("33.34")],
        f"shares={shares1}",
    )
    runner.check(
        "Escenario 1.2: 2 deudas creadas en estado PENDING por 33.33 cada una",
        len(debts1) == 2 and all(x["status"] == "PENDING" and d(x["amount"]) == Decimal("33.33") for x in debts1),
    )

    st_rep, exp1_rep = ana.request("POST", f"/api/groups/{gid}/expenses", body=exp1_payload, headers={"Idempotency-Key": idem1})
    runner.check("Escenario 1.3: idempotencia del gasto (misma clave -> 200 y mismo id)", st_rep == 200 and exp1_rep["id"] == exp1["id"])

    st_bal, bal1 = ana.request("GET", f"/api/groups/{gid}/balances")
    runner.check(
        "Escenario 1.4: suma de saldos del grupo == 0.00 (SC-001)",
        st_bal == 200 and sum_balances(bal1) == Decimal("0.00") and member_balance(bal1, ana.user_id) == Decimal("66.66"),
    )

    st_act, act1 = ana.request("GET", f"/api/groups/{gid}/activity?size=50")
    types1 = [it["eventType"] for it in act1.get("content", [])]
    runner.check("Escenario 1.5: historial registra EXPENSE_CREATED", st_act == 200 and "EXPENSE_CREATED" in types1)

    st_cust, exp_cust = juan.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "90.00",
            "currency": "ARS",
            "date": today,
            "description": "Bebidas",
            "deviceTimeZone": TZ,
            "payerUserId": juan.user_id,
            "splitMethod": "CUSTOM",
            "participants": [
                {"type": "REGISTERED", "userId": ana.user_id, "amount": "40.00"},
                {"type": "REGISTERED", "userId": juan.user_id, "amount": "30.00"},
                {"type": "REGISTERED", "userId": carla.user_id, "amount": "20.00"},
            ],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 1.6: reparto CUSTOM persiste partes exactas (40 / 30 / 20)",
        st_cust == 201 and sorted(d(s["amount"]) for s in exp_cust["shares"]) == [Decimal("20.00"), Decimal("30.00"), Decimal("40.00")],
    )

    st_pct, exp_pct = carla.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "50.00",
            "currency": "ARS",
            "date": today,
            "description": "Postre",
            "deviceTimeZone": TZ,
            "payerUserId": carla.user_id,
            "splitMethod": "PERCENTAGE",
            "participants": [
                {"type": "REGISTERED", "userId": ana.user_id, "percentage": "50"},
                {"type": "REGISTERED", "userId": juan.user_id, "percentage": "30"},
                {"type": "REGISTERED", "userId": carla.user_id, "percentage": "20"},
            ],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 1.7: reparto PERCENTAGE (50/30/20 de 50 ARS -> 25 / 15 / 10)",
        st_pct == 201 and sorted(d(s["amount"]) for s in exp_pct["shares"]) == [Decimal("10.00"), Decimal("15.00"), Decimal("25.00")],
    )

    st_bad_pct, body_bad_pct = carla.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "50.00",
            "currency": "ARS",
            "date": today,
            "description": "Mal porcentaje",
            "deviceTimeZone": TZ,
            "payerUserId": carla.user_id,
            "splitMethod": "PERCENTAGE",
            "participants": [
                {"type": "REGISTERED", "userId": ana.user_id, "percentage": "50"},
                {"type": "REGISTERED", "userId": carla.user_id, "percentage": "40"},
            ],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 1.8: porcentajes que no suman 100 -> 400 con mensaje en voseo",
        st_bad_pct == 400 and "Los porcentajes deben sumar exactamente 100" in json.dumps(body_bad_pct, ensure_ascii=False),
    )

    # --- Scenario 3: Liquidation Proposal (cross-debts from Scenario 1) ---
    st_prop, prop = ana.request("GET", f"/api/groups/{gid}/settlement-proposal")
    ars_prop = next(p for p in prop["proposals"] if p["currency"] == "ARS")
    summary = ars_prop["summary"]
    transfers = ars_prop["transfers"]
    runner.check(
        "Escenario 3.1: propuesta de liquidación cumple transferCount <= M - 1 (SC-002)",
        st_prop == 200 and summary["transferCount"] <= summary["membersSettled"] - 1,
        f"transfers={summary['transferCount']}, members={summary['membersSettled']}",
    )
    runner.check(
        "Escenario 3.2: totalTransferred == totalNetBalances",
        d(summary["totalTransferred"]) == d(summary["totalNetBalances"]),
        f"total={summary['totalTransferred']}",
    )

    _, bal_before = ana.request("GET", f"/api/groups/{gid}/balances")
    net_map = {b["memberId"]: d(b["netBalance"]) for b in bal_before["currencies"][0]["balances"] if b["memberType"] == "REGISTERED"}
    for tr in transfers:
        net_map[tr["fromMemberId"]] += d(tr["amount"])
        net_map[tr["toMemberId"]] -= d(tr["amount"])
    runner.check(
        "Escenario 3.3: aplicar las transferencias propuestas deja todos los saldos en 0.00",
        all(v == Decimal("0.00") for v in net_map.values()),
        f"after={net_map}",
    )

    _, bal_after = ana.request("GET", f"/api/groups/{gid}/balances")
    runner.check("Escenario 3.4: la propuesta es de solo lectura (no altera saldos)", bal_before == bal_after)

    st_prop_out, _ = outsider.request("GET", f"/api/groups/{gid}/settlement-proposal")
    runner.check("Escenario 3.5: no miembro recibe 404 neutral en propuesta", st_prop_out == 404)

    # --- Scenario 2: Partial and Total Payments (with and without validation) ---
    juan_debt = next(x for x in debts1 if x["debtor"]["userId"] == juan.user_id)
    carla_debt = next(x for x in debts1 if x["debtor"]["userId"] == carla.user_id)
    juan_debt_id = juan_debt["id"]
    carla_debt_id = carla_debt["id"]

    # 2.1 Excessive and non-positive amounts -> 400
    st_exc, body_exc = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{juan_debt_id}/payments",
        body={"amount": "33.34", "idempotencyKey": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 2.1: pago mayor al saldo pendiente (33.34 > 33.33) -> 400 'El monto del pago excede la deuda pendiente'",
        st_exc == 400 and "El monto del pago excede la deuda pendiente" in json.dumps(body_exc, ensure_ascii=False),
    )

    st_zero, _ = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{juan_debt_id}/payments",
        body={"amount": "0.00", "idempotencyKey": str(uuid.uuid4())},
    )
    runner.check("Escenario 2.2: pago con monto 0.00 -> 400", st_zero == 400)

    # 2.3 Partial payment without validation -> CONFIRMED, debt PARTIALLY_PAID
    pay1_key = str(uuid.uuid4())
    st_p1, pay1 = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{juan_debt_id}/payments",
        body={"amount": "10.00", "idempotencyKey": pay1_key},
    )
    runner.check(
        "Escenario 2.3: pago parcial (10.00 de 33.33) sin validación -> 201 CONFIRMED y deuda PARTIALLY_PAID",
        st_p1 == 201 and pay1["status"] == "CONFIRMED" and pay1["debtStatusAfter"] == "PARTIALLY_PAID",
    )

    # 2.4 Idempotent replay of partial payment -> 200
    st_p1_rep, pay1_rep = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{juan_debt_id}/payments",
        body={"amount": "10.00", "idempotencyKey": pay1_key},
    )
    runner.check(
        "Escenario 2.4: misma idempotencyKey repite 200 sin duplicar el pago parcial",
        st_p1_rep == 200 and pay1_rep["paymentId"] == pay1["paymentId"],
    )

    # Same key with different amount -> 422
    st_p1_422, _ = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{juan_debt_id}/payments",
        body={"amount": "5.00", "idempotencyKey": pay1_key},
    )
    runner.check("Escenario 2.5: misma idempotencyKey con otro monto -> 422", st_p1_422 == 422)

    # Pending debts & balances reflect paidAmount=10.00, outstanding=23.33
    _, pend_after_p1 = juan.request("GET", f"/api/groups/{gid}/debts/pending")
    juan_d_row = next(x for x in pend_after_p1 if x["debtId"] == juan_debt_id)
    _, bal_after_p1 = ana.request("GET", f"/api/groups/{gid}/balances")
    runner.check(
        "Escenario 2.6: tras pago parcial, deuda muestra paidAmount=10.00, outstanding=23.33 y saldos suman 0.00",
        d(juan_d_row["paidAmount"]) == Decimal("10.00")
        and d(juan_d_row["outstanding"]) == Decimal("23.33")
        and juan_d_row["status"] == "PARTIALLY_PAID"
        and sum_balances(bal_after_p1) == Decimal("0.00"),
    )

    # Paying more than remaining balance (23.34 > 23.33) -> 400
    st_exc2, _ = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{juan_debt_id}/payments",
        body={"amount": "23.34", "idempotencyKey": str(uuid.uuid4())},
    )
    runner.check("Escenario 2.7: segundo pago que supera el saldo restante (23.34 > 23.33) -> 400", st_exc2 == 400)

    # Completing the remaining balance (23.33) -> PAID
    st_p2, pay2 = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{juan_debt_id}/payments",
        body={"amount": "23.33", "idempotencyKey": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 2.8: segundo pago por el saldo restante (23.33) -> 201 CONFIRMED y deuda PAID",
        st_p2 == 201 and pay2["status"] == "CONFIRMED" and pay2["debtStatusAfter"] == "PAID",
    )

    # Paying an already settled debt -> 409
    st_settled_409, _ = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{juan_debt_id}/payments",
        body={"amount": "1.00", "idempotencyKey": str(uuid.uuid4())},
    )
    runner.check("Escenario 2.9: pagar una deuda ya saldada (PAID) -> 409", st_settled_409 == 409)

    # Enable "Pagos requieren validación"
    st_perm, _ = ana.request("PUT", f"/api/groups/{gid}", body={"permissions": {"paymentsRequireValidation": True}})
    runner.check("Escenario 2.10: activar 'Pagos requieren validación' en el grupo", st_perm == 200)

    # Partial payment with validation -> REPORTED, debt PAYMENT_REPORTED, paidAmount still 0
    _, bal_before_rep = ana.request("GET", f"/api/groups/{gid}/balances")
    st_rep1, rep1 = carla.request(
        "POST",
        f"/api/groups/{gid}/debts/{carla_debt_id}/payments",
        body={"amount": "15.00", "idempotencyKey": str(uuid.uuid4())},
    )
    _, pend_rep1 = carla.request("GET", f"/api/groups/{gid}/debts/pending")
    carla_d_row = next(x for x in pend_rep1 if x["debtId"] == carla_debt_id)
    _, bal_during_rep = ana.request("GET", f"/api/groups/{gid}/balances")
    runner.check(
        "Escenario 2.11: pago parcial con validación -> REPORTED, deuda PAYMENT_REPORTED y paidAmount=0.00 sin alterar saldos",
        st_rep1 == 201
        and rep1["status"] == "REPORTED"
        and rep1["debtStatusAfter"] == "PAYMENT_REPORTED"
        and d(carla_d_row["paidAmount"]) == Decimal("0.00")
        and d(carla_d_row["outstanding"]) == Decimal("33.33")
        and bal_before_rep == bal_during_rep,
    )

    # Another payment while REPORTED -> 409 with exact voseo message
    st_wait_409, body_wait_409 = carla.request(
        "POST",
        f"/api/groups/{gid}/debts/{carla_debt_id}/payments",
        body={"amount": "5.00", "idempotencyKey": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 2.12: otro pago mientras hay uno esperando validación -> 409 con mensaje en voseo",
        st_wait_409 == 409 and body_wait_409.get("detail") == AWAITING_MSG,
        f"detail={body_wait_409.get('detail')}",
    )

    # Non-admin cannot list, confirm or reject
    st_q_403, _ = juan.request("GET", f"/api/groups/{gid}/payments/pending-validation")
    st_c_403, _ = juan.request("POST", f"/api/groups/{gid}/payments/{rep1['paymentId']}/confirm")
    st_r_403, _ = juan.request("POST", f"/api/groups/{gid}/payments/{rep1['paymentId']}/reject")
    runner.check("Escenario 2.13: miembro no admin recibe 403 al listar, confirmar o rechazar pagos", (st_q_403, st_c_403, st_r_403) == (403, 403, 403))

    # Admin lists queue and confirms partial payment -> debt PARTIALLY_PAID, paidAmount=15.00
    st_q, queue = ana.request("GET", f"/api/groups/{gid}/payments/pending-validation")
    runner.check(
        "Escenario 2.14: admin lista pagos por validar y ve el pago parcial de 15.00",
        st_q == 200 and any(p["paymentId"] == rep1["paymentId"] and d(p["amount"]) == Decimal("15.00") for p in queue),
    )

    st_conf1, conf1 = ana.request("POST", f"/api/groups/{gid}/payments/{rep1['paymentId']}/confirm")
    _, pend_after_conf1 = carla.request("GET", f"/api/groups/{gid}/debts/pending")
    carla_d_row2 = next(x for x in pend_after_conf1 if x["debtId"] == carla_debt_id)
    runner.check(
        "Escenario 2.15: admin confirma pago parcial -> CONFIRMED, deuda PARTIALLY_PAID y saldo pendiente 18.33",
        st_conf1 == 200
        and conf1["status"] == "CONFIRMED"
        and conf1["debtStatusAfter"] == "PARTIALLY_PAID"
        and d(carla_d_row2["paidAmount"]) == Decimal("15.00")
        and d(carla_d_row2["outstanding"]) == Decimal("18.33"),
    )

    # Confirming an already decided payment -> 409
    st_conf_again, _ = ana.request("POST", f"/api/groups/{gid}/payments/{rep1['paymentId']}/confirm")
    runner.check("Escenario 2.16: confirmar un pago ya decidido -> 409 PAYMENT_NOT_DECIDABLE", st_conf_again == 409)

    # Second reported payment on the PARTIALLY_PAID debt, then rejected -> returns to PARTIALLY_PAID (18.33)
    st_rep2, rep2 = carla.request(
        "POST",
        f"/api/groups/{gid}/debts/{carla_debt_id}/payments",
        body={"amount": "18.33", "idempotencyKey": str(uuid.uuid4())},
    )
    st_rej2, rej2 = ana.request("POST", f"/api/groups/{gid}/payments/{rep2['paymentId']}/reject")
    _, pend_after_rej2 = carla.request("GET", f"/api/groups/{gid}/debts/pending")
    carla_d_row3 = next(x for x in pend_after_rej2 if x["debtId"] == carla_debt_id)
    runner.check(
        "Escenario 2.17: rechazar pago sobre deuda con pago parcial previo la devuelve a PARTIALLY_PAID con saldo 18.33",
        st_rep2 == 201
        and st_rej2 == 200
        and rej2["status"] == "REJECTED"
        and rej2["debtStatusAfter"] == "PARTIALLY_PAID"
        and d(carla_d_row3["outstanding"]) == Decimal("18.33"),
    )

    # Inherited 018 endpoint POST /api/payments also respects validation and completes the debt -> VERIFIED
    st_leg, leg_pay = carla.request(
        "POST",
        "/api/payments",
        body={"debtId": carla_debt_id, "amount": "18.33", "date": today, "deviceTimeZone": TZ},
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    st_conf_leg, conf_leg = ana.request("POST", f"/api/groups/{gid}/payments/{leg_pay['id']}/confirm")
    runner.check(
        "Escenario 2.18: endpoint heredado POST /api/payments respeta validación (REPORTED) y al confirmar cierra en VERIFIED",
        st_leg == 201
        and leg_pay.get("status") == "REPORTED"
        and st_conf_leg == 200
        and conf_leg["debtStatusAfter"] == "VERIFIED",
    )

    _, act_pay = ana.request("GET", f"/api/groups/{gid}/activity?size=50")
    pay_events = {it["eventType"] for it in act_pay.get("content", [])}
    runner.check(
        "Escenario 2.19: historial incluye PAYMENT_REGISTERED, PAYMENT_CONFIRMED y PAYMENT_REJECTED",
        {"PAYMENT_REGISTERED", "PAYMENT_CONFIRMED", "PAYMENT_REJECTED"}.issubset(pay_events),
    )

    # Disable validation for remaining scenarios
    ana.request("PUT", f"/api/groups/{gid}", body={"permissions": {"paymentsRequireValidation": False}})

    # --- Scenario 4: Multi-Currency Expense (real BCRA API) ---
    st_q_usd, quote_usd = ana.request("GET", f"/api/groups/{gid}/exchange-quote?currency=USD&date=2026-01-15")
    runner.check(
        "Escenario 4.1: vista previa de cotización USD->ARS del 2026-01-15 (BCRA real)",
        st_q_usd == 200 and quote_usd.get("rateSource") == "BCRA" and d(quote_usd.get("rate", 0)) > 0,
        f"rate={quote_usd.get('rate')}, rateDate={quote_usd.get('rateDate')}",
    )

    st_usd, exp_usd = ana.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "50.00",
            "currency": "USD",
            "date": "2026-01-15",
            "description": "Hotel USD",
            "deviceTimeZone": TZ,
            "payerUserId": ana.user_id,
            "splitMethod": "EQUAL",
            "participants": [
                {"type": "REGISTERED", "userId": ana.user_id},
                {"type": "REGISTERED", "userId": juan.user_id},
            ],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 4.2: gasto de 50 USD guarda monto original, convertedAmount en ARS y tasa BCRA",
        st_usd == 201
        and exp_usd["currency"] == "USD"
        and d(exp_usd["amount"]) == Decimal("50.00")
        and exp_usd["convertedCurrency"] == "ARS"
        and d(exp_usd["convertedAmount"]) > Decimal("50.00")
        and exp_usd["rateSource"] == "BCRA",
        f"converted={exp_usd.get('convertedAmount')} ARS @ {exp_usd.get('exchangeRate')}",
    )

    # Sunday date -> previous business day
    st_sun, exp_sun = ana.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "20.00",
            "currency": "EUR",
            "date": "2026-01-18",
            "description": "Taxi domingo EUR",
            "deviceTimeZone": TZ,
            "payerUserId": ana.user_id,
            "splitMethod": "EQUAL",
            "participants": [
                {"type": "REGISTERED", "userId": ana.user_id},
                {"type": "REGISTERED", "userId": juan.user_id},
            ],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 4.3: gasto en domingo (2026-01-18, EUR) usa cotización del día hábil previo",
        st_sun == 201 and exp_sun["rateDate"] < "2026-01-18" and exp_sun["rateSource"] == "BCRA",
        f"rateDate={exp_sun.get('rateDate')}",
    )

    # Same currency -> rate 1, SAME_CURRENCY
    st_same_q, quote_same = ana.request("GET", f"/api/groups/{gid}/exchange-quote?currency=ARS&date=2026-01-15")
    runner.check(
        "Escenario 4.4: misma moneda del grupo devuelve tasa 1 y fuente SAME_CURRENCY",
        st_same_q == 200 and d(quote_same["rate"]) == Decimal("1") and quote_same["rateSource"] == "SAME_CURRENCY",
    )

    # Future date -> 400
    tomorrow = (dt.date.today() + dt.timedelta(days=2)).isoformat()
    st_fut, _ = ana.request("GET", f"/api/groups/{gid}/exchange-quote?currency=USD&date={tomorrow}")
    runner.check("Escenario 4.5: cotización con fecha futura -> 400", st_fut == 400)

    # Group creation with USD and invalid currency
    st_g_usd, grp_usd = ana.request(
        "POST",
        "/api/groups",
        body={
            "name": "Grupo USD",
            "primaryCurrency": "USD",
            "members": [{"memberType": "REGISTERED", "identifier": juan.email}],
        },
    )
    st_g_bad, body_g_bad = ana.request(
        "POST",
        "/api/groups",
        body={
            "name": "Grupo BRL",
            "primaryCurrency": "BRL",
            "members": [{"memberType": "REGISTERED", "identifier": juan.email}],
        },
    )
    runner.check(
        "Escenario 4.6: crear grupo en USD devuelve 201 y moneda inválida (BRL) responde 400 'La moneda no es válida'",
        st_g_usd == 201
        and grp_usd.get("primaryCurrency") == "USD"
        and st_g_bad == 400
        and "La moneda no es válida" in json.dumps(body_g_bad, ensure_ascii=False),
    )

    runner.skip(
        "Escenario 4.7: observación directa de hits en caché en memoria del BCRA",
        "no observable por HTTP; cubierto por GroupExpenseControllerIT.t052SecondExpenseWithSameCurrencyAndDateUsesCachedQuoteWithoutSecondApiCall",
    )

    # --- Scenario 5: Simulated Person in Debt Calculation ---
    st_sim_exp, exp_sim = ana.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "60.00",
            "currency": "ARS",
            "date": today,
            "description": "Helado con la abuela",
            "deviceTimeZone": TZ,
            "payerUserId": ana.user_id,
            "splitMethod": "EQUAL",
            "participants": [
                {"type": "REGISTERED", "userId": ana.user_id},
                {"type": "SIMULATED", "simulatedProfileId": sim_id},
            ],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    sim_debt_id = exp_sim["debts"][0]["id"]
    _, bal_sim = ana.request("GET", f"/api/groups/{gid}/balances")
    _, prop_sim = ana.request("GET", f"/api/groups/{gid}/settlement-proposal")
    ars_transfers_sim = next(p for p in prop_sim["proposals"] if p["currency"] == "ARS")["transfers"]
    sim_transfers = [t for t in ars_transfers_sim if t["fromMemberType"] == "SIMULATED" or t["toMemberType"] == "SIMULATED"]
    runner.check(
        "Escenario 5.1: persona simulada aparece en deudas, saldos (suma 0.00) y transferencias marcadas informative=true",
        st_sim_exp == 201
        and member_balance(bal_sim, sim_id, "SIMULATED") == Decimal("-30.00")
        and sum_balances(bal_sim) == Decimal("0.00")
        and len(sim_transfers) >= 1
        and all(t["informative"] is True for t in sim_transfers),
    )

    # Uninvolved member (Carla) cannot pay simulated person's debt to Ana -> 403
    st_sim_403, _ = carla.request(
        "POST",
        f"/api/groups/{gid}/debts/{sim_debt_id}/payments",
        body={"amount": "10.00", "idempotencyKey": str(uuid.uuid4())},
    )
    # Creditor (Ana) registers a partial payment then the rest
    st_sim_p1, sim_p1 = ana.request(
        "POST",
        f"/api/groups/{gid}/debts/{sim_debt_id}/payments",
        body={"amount": "10.00", "idempotencyKey": str(uuid.uuid4())},
    )
    st_sim_p2, sim_p2 = ana.request(
        "POST",
        f"/api/groups/{gid}/debts/{sim_debt_id}/payments",
        body={"amount": "20.00", "idempotencyKey": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 5.2: tercero recibe 403 en deuda de persona simulada; el acreedor registra pago parcial y luego el saldo",
        st_sim_403 == 403
        and st_sim_p1 == 201
        and sim_p1["debtStatusAfter"] == "PARTIALLY_PAID"
        and st_sim_p2 == 201
        and sim_p2["debtStatusAfter"] == "PAID",
    )

    # --- Scenario 6: Edit and Delete Restrictions ---
    st_ed_409, body_ed_409 = ana.request("PATCH", f"/api/groups/{gid}/expenses/{exp1['id']}", body={"description": "Intento"})
    st_del_409, body_del_409 = ana.request("DELETE", f"/api/groups/{gid}/expenses/{exp1['id']}")
    runner.check(
        "Escenario 6.1: gasto compartido rechaza edición (409) y borrado (409)",
        st_ed_409 == 409
        and "No se permite editar un gasto que ya tiene participantes" in json.dumps(body_ed_409, ensure_ascii=False)
        and st_del_409 == 409
        and "No se puede eliminar un gasto con deudas asociadas" in json.dumps(body_del_409, ensure_ascii=False),
    )

    st_pers, exp_pers = ana.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "45.00",
            "currency": "ARS",
            "date": today,
            "description": "Café personal",
            "deviceTimeZone": TZ,
            "payerUserId": ana.user_id,
            "participants": [{"type": "REGISTERED", "userId": ana.user_id}],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    pers_id = exp_pers["id"]
    runner.check("Escenario 6.2: crear gasto personal (solo el pagador) sin generar deudas", st_pers == 201 and exp_pers["debts"] == [])

    st_ed_403, _ = juan.request("PATCH", f"/api/groups/{gid}/expenses/{pers_id}", body={"amount": "50.00"})
    st_del_403, _ = juan.request("DELETE", f"/api/groups/{gid}/expenses/{pers_id}")
    st_ed_ok, exp_pers_ed = ana.request("PATCH", f"/api/groups/{gid}/expenses/{pers_id}", body={"amount": "55.00", "description": "Café grande"})
    _, bal_before_del = ana.request("GET", f"/api/groups/{gid}/balances")
    st_del_ok, _ = ana.request("DELETE", f"/api/groups/{gid}/expenses/{pers_id}")
    st_get_del, _ = ana.request("GET", f"/api/groups/{gid}/expenses/{pers_id}")
    _, bal_after_del = ana.request("GET", f"/api/groups/{gid}/balances")
    _, act_ed = ana.request("GET", f"/api/groups/{gid}/activity?size=50")
    ed_types = {it["eventType"] for it in act_ed.get("content", [])}
    runner.check(
        "Escenario 6.3: gasto personal admite PATCH (200) y DELETE (204) por su autor, 403 a terceros, no altera saldos y registra eventos",
        st_ed_403 == 403
        and st_del_403 == 403
        and st_ed_ok == 200
        and d(exp_pers_ed["amount"]) == Decimal("55.00")
        and st_del_ok == 204
        and st_get_del == 404
        and bal_before_del == bal_after_del
        and {"EXPENSE_EDITED", "EXPENSE_DELETED"}.issubset(ed_types),
    )

    # --- Scenario 7: Overdue Debts ---
    st_due_403, _ = juan.request("PUT", f"/api/groups/{gid}", body={"debtDueDays": 0})
    st_due_400, _ = ana.request("PUT", f"/api/groups/{gid}", body={"debtDueDays": -1})
    st_due_ok, grp_due = ana.request("PUT", f"/api/groups/{gid}", body={"debtDueDays": 0})
    runner.check(
        "Escenario 7.1: configurar debtDueDays=0 (403 no admin, 400 fuera de rango, 200 admin)",
        (st_due_403, st_due_400, st_due_ok) == (403, 400, 200) and grp_due.get("debtDueDays") == 0,
    )

    st_old, exp_old = ana.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "40.00",
            "currency": "ARS",
            "date": yesterday,
            "description": "Gasto de ayer",
            "deviceTimeZone": TZ,
            "payerUserId": ana.user_id,
            "splitMethod": "EQUAL",
            "participants": [
                {"type": "REGISTERED", "userId": ana.user_id},
                {"type": "REGISTERED", "userId": juan.user_id},
            ],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    overdue_debt_id = exp_old["debts"][0]["id"]

    # Wait up to 65s for the per-minute overdue cron to run
    overdue_seen = False
    for _ in range(65):
        _, pend_list = juan.request("GET", f"/api/groups/{gid}/debts/pending")
        row = next((x for x in pend_list if x["debtId"] == overdue_debt_id), None)
        if row and row["status"] == "OVERDUE":
            overdue_seen = True
            break
        time.sleep(1)

    _, act_ov = ana.request("GET", f"/api/groups/{gid}/activity?size=50")
    ov_events = [it for it in act_ov.get("content", []) if it["eventType"] == "DEBT_OVERDUE"]
    runner.check(
        "Escenario 7.2: job programado pasa la deuda PENDING a OVERDUE y registra DEBT_OVERDUE sin actor",
        st_old == 201 and overdue_seen and len(ov_events) >= 1 and ov_events[0].get("actor") is None,
    )

    # Partial payment on an OVERDUE debt -> PARTIALLY_PAID, then rest -> PAID
    st_ov_p1, ov_p1 = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{overdue_debt_id}/payments",
        body={"amount": "5.00", "idempotencyKey": str(uuid.uuid4())},
    )
    st_ov_p2, ov_p2 = juan.request(
        "POST",
        f"/api/groups/{gid}/debts/{overdue_debt_id}/payments",
        body={"amount": "15.00", "idempotencyKey": str(uuid.uuid4())},
    )
    runner.check(
        "Escenario 7.3: deuda OVERDUE acepta pago parcial (-> PARTIALLY_PAID) y luego pago del saldo restante (-> PAID)",
        st_ov_p1 == 201
        and ov_p1["debtStatusAfter"] == "PARTIALLY_PAID"
        and st_ov_p2 == 201
        and ov_p2["debtStatusAfter"] == "PAID",
    )

    # --- Scenario 8: Leaving a Group With Open Debts (020) ---
    st_leave, body_leave = carla.request("POST", f"/api/groups/{gid}/leave")
    runner.check(
        "Escenario 8.1: miembro con deuda abierta no puede abandonar el grupo (403 PENDING_DEBTS)",
        st_leave == 403
        and body_leave.get("properties", {}).get("code") == "PENDING_DEBTS"
        and "No podés abandonar el grupo: tenés saldos pendientes con otros miembros" in body_leave.get("detail", ""),
    )

    # --- Auth check ---
    anon = Session(runner.base_url)
    st_401, _ = anon.request("GET", f"/api/groups/{gid}/balances")
    runner.check("Seguridad: petición sin sesión devuelve 401", st_401 == 401)

    runner.skip(
        "Recorrido visual en navegador real y entrega de correo por SMTP",
        "no ejecutado en navegador; cubierto por la suite Vitest (GroupDebtsView.test.jsx, RegisterPaymentView.test.jsx, GroupExpenseFormView.test.jsx, ...)",
    )


def run_nobcra(runner: Runner) -> None:
    today = dt.date.today().isoformat()
    ana = register_and_login(runner, "ana.nb", "Ana", "López")
    juan = register_and_login(runner, "juan.nb", "Juan", "Pérez")
    befriend(ana, juan)

    st, grp = ana.request(
        "POST",
        "/api/groups",
        body={
            "name": "Grupo Sin BCRA",
            "primaryCurrency": "ARS",
            "members": [{"memberType": "REGISTERED", "identifier": juan.email}],
        },
    )
    gid = grp["id"]
    runner.check("Sin BCRA 1: grupo creado en ARS", st == 201)

    st_q, body_q = ana.request("GET", f"/api/groups/{gid}/exchange-quote?currency=USD&date=2026-01-15")
    runner.check(
        "Sin BCRA 2: vista previa de cotización USD->ARS responde 503 con mensaje en voseo",
        st_q == 503
        and "No se pudo obtener la cotización; reintente después o registre en moneda del grupo"
        in json.dumps(body_q, ensure_ascii=False),
    )

    count_before = int(runner.sql(f"SELECT count(*) FROM group_expenses WHERE group_id = {gid};"))
    st_exp, body_exp = ana.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "50.00",
            "currency": "USD",
            "date": "2026-01-15",
            "description": "Gasto USD sin tasa",
            "deviceTimeZone": TZ,
            "payerUserId": ana.user_id,
            "splitMethod": "EQUAL",
            "participants": [
                {"type": "REGISTERED", "userId": ana.user_id},
                {"type": "REGISTERED", "userId": juan.user_id},
            ],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    count_after = int(runner.sql(f"SELECT count(*) FROM group_expenses WHERE group_id = {gid};"))
    runner.check(
        "Sin BCRA 3: gasto en otra moneda responde 503 EXCHANGE_RATE_UNAVAILABLE y NO se guarda",
        st_exp == 503
        and body_exp.get("properties", {}).get("code") == "EXCHANGE_RATE_UNAVAILABLE"
        and count_before == count_after == 0,
    )

    st_ars, exp_ars = ana.request(
        "POST",
        f"/api/groups/{gid}/expenses",
        body={
            "amount": "100.00",
            "currency": "ARS",
            "date": today,
            "description": "Gasto en moneda del grupo",
            "deviceTimeZone": TZ,
            "payerUserId": ana.user_id,
            "splitMethod": "EQUAL",
            "participants": [
                {"type": "REGISTERED", "userId": ana.user_id},
                {"type": "REGISTERED", "userId": juan.user_id},
            ],
        },
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    runner.check(
        "Sin BCRA 4: gasto en la moneda del grupo (ARS) funciona sin llamar al BCRA (201, SAME_CURRENCY)",
        st_ars == 201 and exp_ars.get("rateSource") == "SAME_CURRENCY",
    )


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    base_url = sys.argv[1]
    db_container = sys.argv[2]
    mode = sys.argv[3] if len(sys.argv) > 3 else "main"

    runner = Runner(base_url, db_container)
    if mode == "nobcra":
        run_nobcra(runner)
    else:
        run_main(runner)

    print(
        f"\nSummary ({mode}): {runner.passed} PASS / {runner.failed} FAIL / {runner.not_run} NOT-RUN",
        flush=True,
    )
    return 1 if runner.failed else 0


if __name__ == "__main__":
    sys.exit(main())
