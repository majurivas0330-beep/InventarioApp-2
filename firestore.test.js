const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write products", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod-1")
      .set({
        name: "Teclado mecanico",
        description: "Switches tactiles",
        priceCop: 220000,
        quantity: 12,
        deleted: false,
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Authenticated owner: valid create, list, partial update, tombstone and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("products");

  await assertSucceeds(
    col.doc("seed-teclado-mecanico").set({
      name: "Teclado mecanico",
      description: "Switches tactiles",
      priceCop: 220000,
      quantity: 12,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(col.get());

  await assertSucceeds(
    col.doc("seed-teclado-mecanico").update({
      name: "Teclado mecanico Pro",
      description: "Switches tactiles RGB",
      priceCop: 240000,
      quantity: 15,
      deleted: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    col.doc("seed-teclado-mecanico").update({
      deleted: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(col.doc("seed-teclado-mecanico").delete());
});

test("Authenticated owner: invalid payloads, extra fields, future timestamps, and immutable createdAt mutations fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("products");

  // Extra @DocumentId 'id' field rejected
  await assertFails(
    col.doc("prod-extra-id").set({
      id: "prod-extra-id",
      name: "Mouse",
      description: "Inalambrico",
      priceCop: 79000,
      quantity: 20,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Blank name rejected
  await assertFails(
    col.doc("prod-blank").set({
      name: "",
      description: "Desc",
      priceCop: 10000,
      quantity: 1,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Non-positive price rejected
  await assertFails(
    col.doc("prod-zero-price").set({
      name: "Monitor",
      description: "27 pulgadas",
      priceCop: 0,
      quantity: 5,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Negative quantity rejected
  await assertFails(
    col.doc("prod-neg-qty").set({
      name: "Monitor",
      description: "27 pulgadas",
      priceCop: 990000,
      quantity: -1,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Future timestamp rejected
  await assertFails(
    col.doc("prod-future").set({
      name: "Monitor",
      description: "27 pulgadas",
      priceCop: 990000,
      quantity: 5,
      deleted: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Create valid doc then attempt mutating createdAt
  await assertSucceeds(
    col.doc("prod-valid").set({
      name: "Monitor 27",
      description: "QHD",
      priceCop: 990000,
      quantity: 5,
      deleted: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    col.doc("prod-valid").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Cross-user isolation: Alice cannot read, list, update, or delete Bob's products", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("products")
      .doc("bob-prod-1")
      .set({
        name: "Bob Monitor",
        description: "Privado",
        priceCop: 800000,
        quantity: 3,
        deleted: false,
        createdAt: new Date(Date.now() - 10000),
        updatedAt: new Date(Date.now() - 10000),
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobCol = aliceDb.collection("users").doc(BOB_UID).collection("products");

  await assertFails(bobCol.get());
  await assertFails(bobCol.doc("bob-prod-1").get());
  await assertFails(bobCol.doc("bob-prod-1").update({ quantity: 99 }));
  await assertFails(bobCol.doc("bob-prod-1").delete());
});
