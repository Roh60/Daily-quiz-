const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-gkswal-00669d47-2b46-46f6-b28a-6db36873372b";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      databaseId: DATABASE_ID,
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

test("Unauthenticated user: cannot read posts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("posts").get());
});

test("Authenticated user: can create a valid text post", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      postId: "post_1",
      authorId: ALICE_UID,
      authorName: "Alice Aspirant",
      type: "text",
      content: "Important tip on Indian Polity preamble amendment!",
      category: "Polity",
      tags: ["#Polity", "#UPSC"],
      likesCount: 0,
      commentsCount: 0,
      createdAt: new Date(),
    })
  );
});

test("Cross-user: Bob cannot delete Alice's post", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("posts").doc("post_alice").set({
      postId: "post_alice",
      authorId: ALICE_UID,
      authorName: "Alice",
      type: "text",
      content: "Alice notes",
      category: "History",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection("posts").doc("post_alice").delete());
});

test("Clients cannot directly delete a post; deleteMyPost callable owns recursive deletion", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("posts").doc("post_alice2").set({
      postId: "post_alice2",
      authorId: ALICE_UID,
      authorName: "Alice",
      type: "text",
      content: "Alice notes 2",
      category: "History",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("posts").doc("post_alice2").delete());
});

test("New posts cannot start with forged engagement counters", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("posts").doc("forged_1").set({
    postId: "forged_1", authorId: ALICE_UID, authorName: "Alice", type: "text",
    content: "A valid community post", category: "General", likesCount: 50,
    commentsCount: 0, createdAt: new Date(),
  }));
});

test("New polls must start with zero votes and valid option text", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("posts").doc("forged_poll").set({
    postId: "forged_poll", authorId: ALICE_UID, authorName: "Alice", type: "poll",
    content: "Choose one answer", category: "General", totalVotes: 9,
    pollOptions: [{ id: 0, text: "A", votes: 9 }, { id: 1, text: "B", votes: 0 }],
    createdAt: new Date(),
  }));
});

test("Clients cannot write poll votes directly; callable function owns vote writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(
    aliceDb.collection("posts").doc("poll_1").collection("votes").doc(ALICE_UID).set({
      userId: ALICE_UID,
      optionIndex: 1,
      votedAt: new Date(),
    })
  );
});

test("Clients cannot forge post aggregate counters", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("posts").doc("post_1").set({
      postId: "post_1", authorId: ALICE_UID, authorName: "Alice", type: "text",
      content: "A valid community post", category: "General", likesCount: 0,
      commentsCount: 0, totalVotes: 0, createdAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("posts").doc("post_1").update({ likesCount: 999 }));
  await assertFails(aliceDb.collection("posts").doc("post_1").collection("likes").doc(ALICE_UID).set({ userId: ALICE_UID, createdAt: new Date() }));
  await assertFails(aliceDb.collection("posts").doc("post_1").collection("comments").doc("c_1").set({
    commentId: "c_1", authorId: ALICE_UID, authorName: "Alice", content: "A comment", createdAt: new Date(),
  }));
});

test("User cannot grant themselves an admin role", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const profile = {
    userId: ALICE_UID,
    displayName: "Alice Aspirant",
    role: "aspirant",
    followersCount: 0,
    followingCount: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(profile));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ role: "admin" }));
});

test("Regular signed-in users cannot add questions to the shared question bank", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("library_questions").doc("q_1").set({
    questionId: "q_1",
    bookSource: "Lucent GK",
    subject: "History",
    chapter: "Ancient India",
    examCategory: "UPSC",
    question: "Which city was a major centre of the Indus Valley Civilization?",
    options: ["Harappa", "Pataliputra", "Madurai", "Kanchipuram"],
    correctOptionIndex: 0,
    explanation: "Harappa was one of the major urban centres of the Indus Valley Civilization.",
    difficulty: "Easy",
    licensingStatus: "original_question",
  }));
});

test("Clients cannot forge follow edges or follower counters", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID, displayName: "Alice", role: "aspirant", followersCount: 0,
      followingCount: 0, createdAt: new Date(), updatedAt: new Date(),
    });
    await context.firestore(DATABASE_ID).collection("users").doc(BOB_UID).set({
      userId: BOB_UID, displayName: "Bob", role: "aspirant", followersCount: 0,
      followingCount: 0, createdAt: new Date(), updatedAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("followers").doc(ALICE_UID).set({
    followerId: ALICE_UID, createdAt: new Date(),
  }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("following").doc(BOB_UID).set({
    followingId: BOB_UID, createdAt: new Date(),
  }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ followersCount: 999 }));
});

test("Clients cannot directly delete their own profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID, displayName: "Alice", role: "aspirant", followersCount: 0,
      followingCount: 0, createdAt: new Date(), updatedAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).delete());
});

test("Trusted admin claim can publish a validated question", async () => {
  const adminDb = testEnv.authenticatedContext("trusted_admin", { admin: true }).firestore(DATABASE_ID);
  await assertSucceeds(adminDb.collection("library_questions").doc("q_1").set({
    questionId: "q_1",
    bookSource: "Lucent GK",
    subject: "History",
    chapter: "Ancient India",
    examCategory: "UPSC",
    question: "Which city was a major centre of the Indus Valley Civilization?",
    options: ["Harappa", "Pataliputra", "Madurai", "Kanchipuram"],
    correctOptionIndex: 0,
    explanation: "Harappa was one of the major urban centres of the Indus Valley Civilization.",
    difficulty: "Easy",
    licensingStatus: "original_question",
  }));
});


test("Like documents are private to the user who liked the post", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("posts").doc("post_privacy").set({
      postId: "post_privacy", authorId: ALICE_UID, authorName: "Alice", type: "text",
      content: "Privacy check post", category: "General", likesCount: 1, createdAt: new Date(),
    });
    await context.firestore(DATABASE_ID).collection("posts").doc("post_privacy").collection("likes").doc(ALICE_UID).set({
      userId: ALICE_UID, createdAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertSucceeds(aliceDb.collection("posts").doc("post_privacy").collection("likes").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("posts").doc("post_privacy").collection("likes").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("posts").doc("post_privacy").collection("likes").get());
});

test("Vote documents are private to the user who voted", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("posts").doc("poll_privacy").set({
      postId: "poll_privacy", authorId: ALICE_UID, authorName: "Alice", type: "poll",
      content: "Privacy check poll", category: "General", totalVotes: 1,
      pollOptions: [{ id: 0, text: "A", votes: 1 }, { id: 1, text: "B", votes: 0 }], createdAt: new Date(),
    });
    await context.firestore(DATABASE_ID).collection("posts").doc("poll_privacy").collection("votes").doc(ALICE_UID).set({
      userId: ALICE_UID, optionIndex: 0, votedAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertSucceeds(aliceDb.collection("posts").doc("poll_privacy").collection("votes").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("posts").doc("poll_privacy").collection("votes").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("posts").doc("poll_privacy").collection("votes").get());
});


test("deletion lock blocks direct client writes to profile, quiz history, posts and reports", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("account_deletions").doc(ALICE_UID).set({
      uid: ALICE_UID, status: "in_progress", stage: "mark_posts", createdAt: new Date(),
    });
    await context.firestore(DATABASE_ID).collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID, displayName: "Alice", role: "aspirant", createdAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("posts").doc("locked_post").set({
    postId: "locked_post", authorId: ALICE_UID, authorName: "Alice", type: "text",
    content: "Must not be created", category: "Testing", likesCount: 0, commentsCount: 0, createdAt: new Date(),
  }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ displayName: "Changed", updatedAt: new Date() }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("quiz_history").doc("history_1").set({ completedAt: new Date() }));
  await assertFails(aliceDb.collection("reports").doc("report_1").set({
    reportId: "report_1", reporterId: ALICE_UID, targetType: "post", targetId: "other_post",
    reason: "spam", createdAt: new Date(),
  }));
});

test("users without a deletion lock can create quiz history but cannot directly create reports", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("quiz_history").doc("history_1").set({ completedAt: new Date() }));
  await assertFails(bobDb.collection("reports").doc("report_bob").set({
    reportId: "report_bob", reporterId: BOB_UID, targetType: "post", targetId: "post_1",
    reason: "spam", createdAt: new Date(), status: "pending",
  }));
});

test("Wallet is owner-readable but clients cannot change balance or ledger", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore(DATABASE_ID);
    await adminDb.collection("wallets").doc(ALICE_UID).set({ uid: ALICE_UID, balancePaise: 12345, lifetimeEarnedPaise: 12345 });
    await adminDb.collection("wallets").doc(ALICE_UID).collection("transactions").doc("tx_1").set({ amountPaise: 12345, type: "earning_credit" });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertSucceeds(aliceDb.collection("wallets").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("wallets").doc(ALICE_UID).collection("transactions").get());
  await assertFails(bobDb.collection("wallets").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("wallets").doc(ALICE_UID).update({ balancePaise: 99999999 }));
  await assertFails(aliceDb.collection("wallets").doc(ALICE_UID).collection("transactions").doc("fake").set({ amountPaise: 999999 }));
});

test("Withdrawal requests are private and cannot be client-created or approved", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("withdrawal_requests").doc("withdraw_1").set({ uid: ALICE_UID, amountPaise: 10000, upiId: "alice@bank", status: "pending" });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertSucceeds(aliceDb.collection("withdrawal_requests").where("uid", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("withdrawal_requests").doc("withdraw_1").get());
  await assertFails(aliceDb.collection("withdrawal_requests").doc("forged").set({ uid: ALICE_UID, amountPaise: 10000, upiId: "alice@bank", status: "paid" }));
  await assertFails(aliceDb.collection("withdrawal_requests").doc("withdraw_1").update({ status: "paid" }));
});

test("Reward claims are private and clients cannot create or approve them", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("reward_claims").doc("claim_1").set({ uid: ALICE_UID, status: "pending_review", quizTitle: "Daily GK" });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertSucceeds(aliceDb.collection("reward_claims").doc("claim_1").get());
  await assertFails(bobDb.collection("reward_claims").doc("claim_1").get());
  await assertFails(aliceDb.collection("reward_claims").doc("fake").set({ uid: ALICE_UID, status: "approved", approvedAmountPaise: 10000 }));
  await assertFails(aliceDb.collection("reward_claims").doc("claim_1").update({ status: "approved" }));
});


test("content reports are private to reporter and admin; clients cannot moderate them", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore(DATABASE_ID).collection("reports").doc("report_private").set({
      reportId: "report_private", reporterId: ALICE_UID, targetType: "post", targetId: "post_1",
      reason: "spam", status: "pending", createdAt: new Date(),
    });
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const adminDb = testEnv.authenticatedContext("admin_123", { admin: true }).firestore(DATABASE_ID);
  await assertSucceeds(aliceDb.collection("reports").doc("report_private").get());
  await assertFails(bobDb.collection("reports").doc("report_private").get());
  await assertFails(bobDb.collection("reports").get());
  await assertSucceeds(adminDb.collection("reports").get());
  await assertFails(adminDb.collection("reports").doc("report_private").update({ status: "dismissed" }));
});

test("Server quiz answer keys and referral accounting are inaccessible to clients", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertFails(aliceDb.collection("quiz_questions").doc("q1").get());
  await assertFails(aliceDb.collection("quiz_questions").doc("q1").set({ question: "forged", options: ["A", "B"], correctIndex: 0 }));
  await assertFails(aliceDb.collection("published_quizzes").doc("daily-general").get());
  await assertFails(aliceDb.collection("quiz_sessions").doc("fake-session").set({ uid: ALICE_UID, correctAnswers: 100 }));
  await assertFails(aliceDb.collection("referral_code_lookup").doc("ABCD1234").get());
  await assertFails(aliceDb.collection("referrals").doc(ALICE_UID).set({ referredUid: ALICE_UID, referrerUid: BOB_UID, status: "approved" }));
});
