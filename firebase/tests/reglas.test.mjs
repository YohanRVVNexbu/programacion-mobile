// Pruebas de las reglas de seguridad de Firestore contra el emulador.
import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, getDocs, collection, deleteDoc, updateDoc } from 'firebase/firestore';

const valido = () => ({
  titulo: 'Llamar al banco', descripcion: 'Pedir estado de cuenta', categoria: 'Trabajo', prioridad: 'ALTA',
  estado: 'PENDIENTE', creadoEn: 1000, actualizadoEn: 1000,
});

let entorno;
before(async () => {
  entorno = await initializeTestEnvironment({
    projectId: 'demo-recordatorios',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
after(async () => { await entorno.cleanup(); });
beforeEach(async () => { await entorno.clearFirestore(); });

const ana = () => entorno.authenticatedContext('ana').firestore();
const beto = () => entorno.authenticatedContext('beto').firestore();
const anonimo = () => entorno.unauthenticatedContext().firestore();
const deAna = (db, id = 'r1') => doc(db, 'users/ana/reminders', id);
async function sembrarDeAna() {
  await entorno.withSecurityRulesDisabled(async (ctx) => { await setDoc(deAna(ctx.firestore()), valido()); });
}

test('la dueña puede crear, leer, actualizar y borrar sus recordatorios', async () => {
  const db = ana();
  await assertSucceeds(setDoc(deAna(db), valido()));
  const leido = await assertSucceeds(getDoc(deAna(db)));
  assert.equal(leido.data().titulo, 'Llamar al banco');
  await assertSucceeds(updateDoc(deAna(db), { titulo: 'Llamar al banco central', actualizadoEn: 2000 }));
  await assertSucceeds(getDocs(collection(db, 'users/ana/reminders')));
  await assertSucceeds(deleteDoc(deAna(db)));
});

test('otra cuenta no puede leer, listar, crear, modificar ni borrar registros ajenos', async () => {
  await sembrarDeAna();
  const db = beto();
  await assertFails(getDoc(deAna(db)));
  await assertFails(getDocs(collection(db, 'users/ana/reminders')));
  await assertFails(setDoc(deAna(db, 'nuevo'), valido()));
  await assertFails(updateDoc(deAna(db), { titulo: 'Cambiado por Beto', actualizadoEn: 5 }));
  await assertFails(deleteDoc(deAna(db)));
});

test('sin sesión no se puede leer ni escribir', async () => {
  await sembrarDeAna();
  const db = anonimo();
  await assertFails(getDoc(deAna(db)));
  await assertFails(getDocs(collection(db, 'users/ana/reminders')));
  await assertFails(setDoc(deAna(db, 'x'), valido()));
  await assertFails(deleteDoc(deAna(db)));
});

test('los datos inválidos se rechazan aunque la cuenta sea la dueña', async () => {
  const db = ana();
  const casos = {
    'título corto': { titulo: 'ab' },
    'título demasiado largo': { titulo: 'x'.repeat(61) },
    'descripción de más de 200 caracteres': { descripcion: 'x'.repeat(201) },
    'categoría inexistente': { categoria: 'Otra' },
    'prioridad inexistente': { prioridad: 'URGENTE' },
    'estado inexistente': { estado: 'QUIZAS' },
    'fecha que no es número': { actualizadoEn: 'ayer' },
    'campo desconocido': { admin: true },
  };
  for (const [nombre, cambio] of Object.entries(casos)) {
    await assertFails(setDoc(deAna(db, 'malo'), { ...valido(), ...cambio }), nombre);
  }
  const incompleto = valido(); delete incompleto.categoria;
  await assertFails(setDoc(deAna(db, 'malo'), incompleto));
});

test('los campos opcionales pueden ir ausentes o nulos', async () => {
  const db = ana();
  const sin = valido(); delete sin.descripcion;
  await assertSucceeds(setDoc(deAna(db, 'a'), sin));
  await assertSucceeds(setDoc(deAna(db, 'b'), { ...valido(), descripcion: null, fechaLimite: null, completadoEn: null, ubicacion: null }));
});

test('cualquier otra ruta está denegada, incluso para una cuenta autenticada', async () => {
  const db = ana();
  await assertFails(setDoc(doc(db, 'otra/cosa'), { a: 1 }));
  await assertFails(getDoc(doc(db, 'users/ana')));
  await assertFails(setDoc(doc(db, 'users/ana/otros/x'), valido()));
});
