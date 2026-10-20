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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read save states", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("save_states").get());
});

test("Authenticated user: can create and read own save state", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const saveStateRef = aliceDb.collection("users").doc(ALICE_UID).collection("save_states").doc("slot_0");
  
  await assertSucceeds(
    saveStateRef.set({
      userId: ALICE_UID,
      gameId: "pokemon_emerald",
      gameTitle: "Pokemon Emerald",
      system: "GBA",
      slot: 0,
      stateData: "dGVzdF9zdGF0ZQ==",
      label: "Before Gym Leader",
      playTimeSeconds: 3600,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(saveStateRef.get());
});

test("Cross-user isolation: Bob cannot read Alice's save state", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("save_states").doc("slot_0").set({
      userId: ALICE_UID,
      gameId: "zelda_oot",
      gameTitle: "The Legend of Zelda",
      system: "N64",
      slot: 0,
      stateData: "dGVzdF9zdGF0ZQ==",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("save_states").doc("slot_0").get());
});

test("Schema validation: rejects invalid system type", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const saveStateRef = aliceDb.collection("users").doc(ALICE_UID).collection("save_states").doc("slot_1");

  await assertFails(
    saveStateRef.set({
      userId: ALICE_UID,
      gameId: "invalid_game",
      gameTitle: "Invalid Game",
      system: "PLAYSTATION", // Invalid enum
      slot: 1,
      stateData: "dGVzdF9zdGF0ZQ==",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Immutability: cannot alter createdAt timestamp", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const saveStateRef = aliceDb.collection("users").doc(ALICE_UID).collection("save_states").doc("slot_0");

  const originalDate = new Date("2026-01-01T00:00:00Z");
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("save_states").doc("slot_0").set({
      userId: ALICE_UID,
      gameId: "mario_64",
      gameTitle: "Super Mario 64",
      system: "N64",
      slot: 0,
      stateData: "dGVzdA==",
      createdAt: originalDate,
      updatedAt: originalDate,
    });
  });

  await assertFails(
    saveStateRef.update({
      createdAt: new Date(), // Attempting to alter immutable createdAt
      updatedAt: new Date(),
    })
  );
});
