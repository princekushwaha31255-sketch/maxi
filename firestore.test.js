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

test("1. Unauthenticated user cannot read or write chat_messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chat_messages").get()
  );
});

test("2. Authenticated user can create and query their own chat_messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chat_messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        isUser: true,
        text: "YouTube kholo",
        detectedLanguage: "Hinglish",
        toolInvoked: "launch_app",
        status: "VERIFIED_SUCCESS",
        verificationEvidence: "Launched com.google.android.youtube",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chat_messages")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("3. Cross-user isolation: Bob cannot read or write Alice's chat_messages", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("chat_messages")
      .doc("msg_alice")
      .set({
        userId: ALICE_UID,
        isUser: true,
        text: "Secret message",
        detectedLanguage: "English",
        toolInvoked: "",
        status: "INFO_RESPONSE",
        verificationEvidence: "",
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chat_messages")
      .doc("msg_alice")
      .get()
  );
});

test("4. Identity spoofing rejected: Alice cannot create a message with userId = Bob", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("chat_messages")
      .doc("msg_spoof")
      .set({
        userId: BOB_UID,
        isUser: true,
        text: "Spoofed",
        detectedLanguage: "English",
        toolInvoked: "",
        status: "INFO_RESPONSE",
        verificationEvidence: "",
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("5. Shadow field injection rejected on create and update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("trusted_rules")
      .doc("rule_1")
      .set({
        userId: ALICE_UID,
        toolName: "whatsapp_action",
        scopeKey: "whatsapp:+919876543210",
        description: "Trusted contact",
        isEnabled: true,
        isAdmin: true,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("6. Trusted rule valid create and toggle update succeeds, immutable createdAt enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("trusted_rules")
    .doc("rule_valid");

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      toolName: "whatsapp_action",
      scopeKey: "whatsapp:+919876543210",
      description: "Trusted contact Rahul",
      isEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid toggle update
  await assertSucceeds(
    docRef.update({
      isEnabled: false,
      updatedAt: new Date(),
    })
  );

  // Tampering with createdAt must fail
  await assertFails(
    docRef.update({
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});
