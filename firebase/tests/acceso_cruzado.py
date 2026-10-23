#!/usr/bin/env python3
"""
Prueba de acceso cruzado contra el Emulator Suite: una cuenta intenta leer, crear, modificar y borrar
registros de otra, hablando directamente con la API REST de Firestore (sin pasar por la interfaz de la app).

Requisitos: emuladores en marcha (npm run emuladores). Solo usa la biblioteca estándar de Python.
Uso: python3 acceso_cruzado.py
"""
import json
import urllib.error
import urllib.request

PROYECTO = "demo-recordatorios"
AUTH = f"http://localhost:9099/identitytoolkit.googleapis.com/v1/accounts"
FS = f"http://localhost:8080/v1/projects/{PROYECTO}/databases/(default)/documents"


def http(metodo, url, cuerpo=None, token=None):
    cabeceras = {"Content-Type": "application/json"}
    if token:
        cabeceras["Authorization"] = f"Bearer {token}"
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=datos, headers=cabeceras, method=metodo)) as r:
            return r.status, json.load(r) if r.length != 0 else {}
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.load(e)
        except Exception:
            return e.code, {}


def sesion(correo, clave):
    """Crea la cuenta si no existe e inicia sesión. Devuelve (uid, idToken)."""
    http("POST", f"{AUTH}:signUp?key=falsa", {"email": correo, "password": clave, "returnSecureToken": True})
    codigo, r = http("POST", f"{AUTH}:signInWithPassword?key=falsa", {"email": correo, "password": clave, "returnSecureToken": True})
    assert codigo == 200, f"no se pudo iniciar sesión con {correo}: {r}"
    return r["localId"], r["idToken"]


def campos(titulo, **extra):
    base = {"titulo": {"stringValue": titulo}, "categoria": {"stringValue": "Trabajo"}, "prioridad": {"stringValue": "ALTA"},
            "estado": {"stringValue": "PENDIENTE"}, "creadoEn": {"integerValue": "1000"}, "actualizadoEn": {"integerValue": "1000"}}
    base.update(extra)
    return {"fields": base}


uid_ana, tok_ana = sesion("ana@correo.com", "secreto123")
uid_beto, tok_beto = sesion("beto@correo.com", "secreto123")
ruta_ana = f"{FS}/users/{uid_ana}/reminders"
ruta_beto = f"{FS}/users/{uid_beto}/reminders"

# Un documento de Ana para atacar.
codigo, doc = http("POST", f"{ruta_ana}?documentId=prueba-cruzada", campos("Documento de Ana"), tok_ana)
assert codigo in (200, 409), (codigo, doc)

casos = [
    ("Ana lee sus propios registros",                      "GET", ruta_ana, None, tok_ana, 200),
    ("Ana crea un registro válido en su ruta",             "PATCH", f"{ruta_ana}/propio-ana", campos("Registro válido"), tok_ana, 200),
    ("Ana intenta guardar un título de 2 letras (inválido)", "PATCH", f"{ruta_ana}/invalido", campos("ab"), tok_ana, 403),
    ("Ana intenta guardar un campo desconocido",           "PATCH", f"{ruta_ana}/extra", campos("Registro con extra", admin={"booleanValue": True}), tok_ana, 403),
    ("Beto lee su propia colección",                       "GET", ruta_beto, None, tok_beto, 200),
    ("Beto lista los registros de Ana",                    "GET", ruta_ana, None, tok_beto, 403),
    ("Beto lee un documento concreto de Ana",              "GET", f"{ruta_ana}/prueba-cruzada", None, tok_beto, 403),
    ("Beto crea un registro en la ruta de Ana",            "PATCH", f"{ruta_ana}/de-beto", campos("Escrito por Beto"), tok_beto, 403),
    ("Beto modifica un documento de Ana",                  "PATCH", f"{ruta_ana}/prueba-cruzada?updateMask.fieldPaths=titulo", {"fields": {"titulo": {"stringValue": "Cambiado por Beto"}}}, tok_beto, 403),
    ("Beto borra un documento de Ana",                     "DELETE", f"{ruta_ana}/prueba-cruzada", None, tok_beto, 403),
    ("Sin sesión: leer los registros de Ana",              "GET", ruta_ana, None, None, 403),
    ("Sin sesión: crear un registro",                      "PATCH", f"{ruta_ana}/anonimo", campos("Anónimo"), None, 403),
]

print(f"Ana: {uid_ana[:8]}…   Beto: {uid_beto[:8]}…\n")
print(f"{'Intento':<56} {'Esperado':>8} {'Resultado':>10}")
fallos = 0
for nombre, metodo, url, cuerpo, token, esperado in casos:
    codigo, resp = http(metodo, url, cuerpo, token)
    estado = resp.get("error", {}).get("status", "") if isinstance(resp, dict) else ""
    ok = codigo == esperado
    fallos += 0 if ok else 1
    print(f"{nombre:<56} {esperado:>8} {codigo:>6} {'OK' if ok else 'FALLO'}  {estado}")

# Los datos de Ana deben estar intactos tras todos los ataques.
codigo, intacto = http("GET", f"{ruta_ana}/prueba-cruzada", None, tok_ana)
titulo = intacto["fields"]["titulo"]["stringValue"] if codigo == 200 else None
print(f"\nDocumento de Ana después de los intentos: {titulo!r}")
assert titulo == "Documento de Ana", "¡el documento de Ana cambió!"
http("DELETE", f"{ruta_ana}/prueba-cruzada", None, tok_ana)
http("DELETE", f"{ruta_ana}/propio-ana", None, tok_ana)
print("RESULTADO:", "todas las pruebas pasaron" if fallos == 0 else f"{fallos} prueba(s) fallaron")
raise SystemExit(1 if fallos else 0)
