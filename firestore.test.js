const { test, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
  Timestamp,
} = require('firebase/firestore');

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-kitchen3d-studio',
    firestore: {
      rules: fs.readFileSync('firestore.rules', 'utf8'),
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

function buildValidDesign(userId, overrides = {}) {
  return {
    userId,
    title: 'مطبخ فيلا الياسمين الحديثة',
    roomWidthCm: 420,
    roomDepthCm: 360,
    roomHeightCm: 280,
    elementCount: 8,
    totalCostUsd: 4850.5,
    materialsCostUsd: 3200.0,
    laborCostUsd: 950.5,
    accessoriesCostUsd: 700.0,
    primaryMaterialId: 'NATURAL_OAK',
    countertopMaterialId: 'CARRARA_MARBLE',
    projectJson: '{"name":"مطبخ فيلا الياسمين الحديثة","elements":[]}',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function buildValidComponent(userId, overrides = {}) {
  return {
    userId,
    designId: 'design_main_1',
    nameAr: 'خزانة سفلية 3 أدراج - بلوط طبيعي',
    category: 'DRAWERS',
    typeName: 'DRAWER_UNIT_3',
    widthCm: 80,
    depthCm: 60,
    heightCm: 85,
    elevationCm: 0,
    posXCm: 120,
    posYCm: 0,
    rotationDeg: 0,
    materialId: 'NATURAL_OAK',
    unitPriceUsd: 345.0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

test('unauthenticated user is denied read and write on kitchen_designs and kitchen_components', async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  const designRef = doc(unauthedDb, 'users/alice/kitchen_designs/design_1');
  const compRef = doc(unauthedDb, 'users/alice/kitchen_components/comp_1');

  await assertFails(getDoc(designRef));
  await assertFails(setDoc(designRef, buildValidDesign('alice')));
  await assertFails(getDoc(compRef));
  await assertFails(setDoc(compRef, buildValidComponent('alice')));
});

test('authenticated owner can create, read, update, and delete their own kitchen_designs', async () => {
  const aliceDb = testEnv.authenticatedContext('alice').firestore();
  const designRef = doc(aliceDb, 'users/alice/kitchen_designs/design_1');

  await assertSucceeds(setDoc(designRef, buildValidDesign('alice')));
  const snap = await assertSucceeds(getDoc(designRef));
  assert.equal(snap.exists(), true);
  assert.equal(snap.data().title, 'مطبخ فيلا الياسمين الحديثة');

  await assertSucceeds(
    updateDoc(designRef, {
      title: 'مطبخ فيلا الياسمين - معدل',
      totalCostUsd: 5120.0,
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(deleteDoc(designRef));
});

test('authenticated user cannot read or modify another users kitchen_designs or kitchen_components', async () => {
  const aliceDb = testEnv.authenticatedContext('alice').firestore();
  const bobDb = testEnv.authenticatedContext('bob').firestore();

  const aliceDesignRef = doc(aliceDb, 'users/alice/kitchen_designs/design_1');
  await assertSucceeds(setDoc(aliceDesignRef, buildValidDesign('alice')));

  const bobViewAliceDesign = doc(bobDb, 'users/alice/kitchen_designs/design_1');
  await assertFails(getDoc(bobViewAliceDesign));
  await assertFails(
    updateDoc(bobViewAliceDesign, {
      title: 'Hacked title',
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(deleteDoc(bobViewAliceDesign));
});

test('invalid schema, future timestamps, and immutable field tampering are rejected', async () => {
  const aliceDb = testEnv.authenticatedContext('alice').firestore();
  const designRef = doc(aliceDb, 'users/alice/kitchen_designs/design_bad');

  // Extra unexpected field rejected
  await assertFails(
    setDoc(designRef, buildValidDesign('alice', { extraHackField: true }))
  );

  // Future timestamp rejected
  const futureTs = Timestamp.fromMillis(Date.now() + 3600 * 1000);
  await assertFails(
    setDoc(designRef, buildValidDesign('alice', { createdAt: futureTs }))
  );

  // Create valid component then attempt to mutate userId
  const compRef = doc(aliceDb, 'users/alice/kitchen_components/comp_1');
  await assertSucceeds(setDoc(compRef, buildValidComponent('alice')));
  await assertFails(
    updateDoc(compRef, {
      userId: 'bob',
      updatedAt: serverTimestamp(),
    })
  );
});
