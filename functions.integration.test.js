// Integration tests against Firebase Auth, Firestore and Functions emulators.
// Run with: firebase emulators:exec --only auth,firestore,functions --project demo-no-project \
//   "FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 node --test functions.integration.test.js"
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, deleteApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');

const PROJECT_ID = process.env.GCLOUD_PROJECT || 'demo-no-project';
const DATABASE_ID = 'ai-studio-android-gkswal-00669d47-2b46-46f6-b28a-6db36873372b';
const FUNCTIONS_HOST = process.env.FUNCTIONS_EMULATOR_HOST || '127.0.0.1:5001';
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
let app;
let db;
let auth;
let sequence = 0;

before(() => {
  app = initializeApp({ projectId: PROJECT_ID }, 'daily-quiz-integration-tests');
  db = getFirestore(app, DATABASE_ID);
  auth = getAuth(app);
});

after(async () => {
  if (app) await deleteApp(app);
});

async function createTestUser() {
  sequence += 1;
  const email = `dq-test-${Date.now()}-${sequence}@example.test`;
  const response = await fetch(`http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password: 'TestPass123!', returnSecureToken: true }),
  });
  const body = await response.json();
  assert.equal(response.ok, true, `Auth emulator sign-up failed: ${JSON.stringify(body)}`);
  return { uid: body.localId, token: body.idToken, email };
}

async function createAdminTestUser() {
  const user = await createTestUser();
  await auth.setCustomUserClaims(user.uid, { admin: true });
  const customToken = await auth.createCustomToken(user.uid);
  const response = await fetch(`http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=fake-api-key`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ token: customToken, returnSecureToken: true }),
  });
  const body = await response.json();
  assert.equal(response.ok, true, `Admin emulator sign-in failed: ${JSON.stringify(body)}`);
  return { ...user, token: body.idToken };
}

async function callFunction(token, name, data = {}) {
  const response = await fetch(`http://${FUNCTIONS_HOST}/${PROJECT_ID}/us-central1/${name}`, {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify({ data }),
  });
  const body = await response.json();
  if (!response.ok || body.error) {
    const error = new Error(`${name} failed: ${JSON.stringify(body)}`);
    error.payload = body;
    throw error;
  }
  return body.result ?? body.data;
}

async function seedProfile(uid, displayName, extra = {}) {
  await db.collection('users').doc(uid).set({
    userId: uid, displayName, role: 'aspirant', followersCount: 0, followingCount: 0,
    createdAt: new Date(), updatedAt: new Date(), ...extra,
  });
}

test('concurrent like toggles serialize and keep likesCount accurate', { timeout: 60000 }, async () => {
  const user = await createTestUser();
  const postId = `like_race_${user.uid}`;
  await db.collection('posts').doc(postId).set({
    postId, authorId: user.uid, authorName: 'Test', type: 'text', content: 'Race test post',
    category: 'Testing', likesCount: 0, commentsCount: 0, createdAt: new Date(),
  });
  await Promise.all([
    callFunction(user.token, 'togglePostLike', { postId }),
    callFunction(user.token, 'togglePostLike', { postId }),
  ]);
  const post = await db.collection('posts').doc(postId).get();
  const like = await db.collection('posts').doc(postId).collection('likes').doc(user.uid).get();
  assert.equal(post.data().likesCount, 0);
  assert.equal(like.exists, false);
  await db.recursiveDelete(db.collection('posts').doc(postId));
  await auth.deleteUser(user.uid);
});

test('changing a poll vote adjusts option counts without increasing totalVotes twice', { timeout: 60000 }, async () => {
  const user = await createTestUser();
  const postId = `vote_change_${user.uid}`;
  await db.collection('posts').doc(postId).set({
    postId, authorId: user.uid, authorName: 'Test', type: 'poll', content: 'Vote test', category: 'Testing',
    pollOptions: [{ id: 0, text: 'A', votes: 0 }, { id: 1, text: 'B', votes: 0 }],
    totalVotes: 0, allowVoteChange: true, isClosed: false, createdAt: new Date(),
  });
  await callFunction(user.token, 'voteOnPoll', { postId, optionIndex: 0 });
  await callFunction(user.token, 'voteOnPoll', { postId, optionIndex: 1 });
  const post = await db.collection('posts').doc(postId).get();
  const vote = await db.collection('posts').doc(postId).collection('votes').doc(user.uid).get();
  assert.equal(post.data().totalVotes, 1);
  assert.deepEqual(post.data().pollOptions.map(option => option.votes), [0, 1]);
  assert.equal(vote.data().optionIndex, 1);
  await db.recursiveDelete(db.collection('posts').doc(postId));
  await auth.deleteUser(user.uid);
});

test('account deletion removes interactions, repairs counters and removes follow edges', { timeout: 60000 }, async () => {
  const deletingUser = await createTestUser();
  const targetUser = await createTestUser();
  await seedProfile(deletingUser.uid, 'Deleting User', { followingCount: 1 });
  await seedProfile(targetUser.uid, 'Target User', { followersCount: 1 });
  await db.collection('wallets').doc(deletingUser.uid).set({ uid: deletingUser.uid, balancePaise: 20000, lifetimeEarnedPaise: 20000 });
  await db.collection('wallets').doc(deletingUser.uid).collection('transactions').doc('wallet_tx').set({ type: 'earning_credit', amountPaise: 20000 });
  await db.collection('withdrawal_requests').doc(`withdrawal_${deletingUser.uid}`).set({ uid: deletingUser.uid, amountPaise: 10000, upiId: 'private@bank', status: 'pending' });
  await db.collection('reward_claims').doc(`claim_${deletingUser.uid}`).set({ uid: deletingUser.uid, status: 'pending_review', quizTitle: 'Deletion test' });
  await db.collection('reward_claim_daily').doc(`${deletingUser.uid}_${new Date().toISOString().slice(0, 10)}`).set({ uid: deletingUser.uid, day: new Date().toISOString().slice(0, 10) });

  const ownedPostRef = db.collection('posts').doc(`owned_${deletingUser.uid}`);
  await ownedPostRef.set({ postId: ownedPostRef.id, authorId: deletingUser.uid, authorName: 'Deleting User', type: 'text', content: 'Owned post', category: 'Testing', likesCount: 0, commentsCount: 0, createdAt: new Date() });
  const targetPostRef = db.collection('posts').doc(`target_${targetUser.uid}`);
  await targetPostRef.set({ postId: targetPostRef.id, authorId: targetUser.uid, authorName: 'Target User', type: 'text', content: 'Target post', category: 'Testing', likesCount: 1, commentsCount: 1, createdAt: new Date() });
  await targetPostRef.collection('likes').doc(deletingUser.uid).set({ userId: deletingUser.uid, createdAt: new Date() });
  await targetPostRef.collection('comments').doc('comment_by_deleting_user').set({ commentId: 'comment_by_deleting_user', authorId: deletingUser.uid, authorName: 'Deleting User', content: 'Will be removed', createdAt: new Date() });

  const pollRef = db.collection('posts').doc(`poll_${targetUser.uid}`);
  await pollRef.set({ postId: pollRef.id, authorId: targetUser.uid, authorName: 'Target User', type: 'poll', content: 'Target poll', category: 'Testing', totalVotes: 1, pollOptions: [{ id: 0, text: 'A', votes: 1 }, { id: 1, text: 'B', votes: 0 }], createdAt: new Date() });
  await pollRef.collection('votes').doc(deletingUser.uid).set({ userId: deletingUser.uid, optionIndex: 0, votedAt: new Date() });

  await db.collection('users').doc(deletingUser.uid).collection('following').doc(targetUser.uid).set({ followingId: targetUser.uid, createdAt: new Date() });
  await db.collection('users').doc(targetUser.uid).collection('followers').doc(deletingUser.uid).set({ followerId: deletingUser.uid, createdAt: new Date() });

  const result = await callFunction(deletingUser.token, 'deleteMyAccount');
  assert.equal(result.success, true);
  assert.equal((await ownedPostRef.get()).exists, false);
  assert.equal((await targetPostRef.collection('likes').doc(deletingUser.uid).get()).exists, false);
  assert.equal((await targetPostRef.collection('comments').doc('comment_by_deleting_user').get()).exists, false);
  assert.equal((await pollRef.collection('votes').doc(deletingUser.uid).get()).exists, false);
  assert.equal((await targetPostRef.get()).data().likesCount, 0);
  assert.equal((await targetPostRef.get()).data().commentsCount, 0);
  assert.equal((await pollRef.get()).data().totalVotes, 0);
  assert.equal((await pollRef.get()).data().pollOptions[0].votes, 0);
  assert.equal((await db.collection('users').doc(targetUser.uid).get()).data().followersCount, 0);
  assert.equal((await db.collection('users').doc(targetUser.uid).collection('following').doc(deletingUser.uid).get()).exists, false);
  assert.equal((await db.collection('account_deletions').doc(deletingUser.uid).get()).data().status, 'complete');
  assert.equal((await db.collection('wallets').doc(deletingUser.uid).get()).exists, false);
  assert.equal((await db.collection('wallets').doc(deletingUser.uid).collection('transactions').doc('wallet_tx').get()).exists, false);
  assert.equal((await db.collection('withdrawal_requests').doc(`withdrawal_${deletingUser.uid}`).get()).exists, false);
  assert.equal((await db.collection('reward_claims').doc(`claim_${deletingUser.uid}`).get()).exists, false);
  assert.equal((await db.collection('reward_claim_daily').doc(`${deletingUser.uid}_${new Date().toISOString().slice(0, 10)}`).get()).exists, false);
  await db.recursiveDelete(targetPostRef);
  await db.recursiveDelete(pollRef);
  await db.recursiveDelete(db.collection('users').doc(targetUser.uid));
  await auth.deleteUser(targetUser.uid);
});

test('social callables reject actions for deleting actors and deleting post authors', { timeout: 60000 }, async () => {
  const actor = await createTestUser();
  const author = await createTestUser();
  await seedProfile(actor.uid, 'Actor');
  await seedProfile(author.uid, 'Author');
  const postId = `locked_post_${author.uid}`;
  const postRef = db.collection('posts').doc(postId);
  await postRef.set({ postId, authorId: author.uid, authorName: 'Author', type: 'text', content: 'Locked post', category: 'Testing', likesCount: 0, commentsCount: 0, createdAt: new Date() });
  await db.collection('account_deletions').doc(author.uid).set({ uid: author.uid, status: 'in_progress', stage: 'mark_posts' });
  await assert.rejects(callFunction(actor.token, 'togglePostLike', { postId }));
  await assert.rejects(callFunction(actor.token, 'addPostComment', { postId, content: 'Should be rejected' }));

  const pollId = `locked_poll_${author.uid}`;
  await db.collection('posts').doc(pollId).set({
    postId: pollId, authorId: author.uid, authorName: 'Author', type: 'poll', content: 'Locked poll', category: 'Testing',
    pollOptions: [{ id: 0, text: 'A', votes: 0 }, { id: 1, text: 'B', votes: 0 }], totalVotes: 0, createdAt: new Date(),
  });
  await assert.rejects(callFunction(actor.token, 'voteOnPoll', { postId: pollId, optionIndex: 0 }));

  await db.collection('account_deletions').doc(actor.uid).set({ uid: actor.uid, status: 'in_progress', stage: 'lock_created' });
  await assert.rejects(callFunction(actor.token, 'togglePostLike', { postId: `other_${actor.uid}` }));

  await db.recursiveDelete(postRef);
  await db.recursiveDelete(db.collection('posts').doc(pollId));
  await db.recursiveDelete(db.collection('account_deletions').doc(actor.uid));
  await db.recursiveDelete(db.collection('account_deletions').doc(author.uid));
  await db.recursiveDelete(db.collection('users').doc(actor.uid));
  await db.recursiveDelete(db.collection('users').doc(author.uid));
  await auth.deleteUser(actor.uid);
  await auth.deleteUser(author.uid);
});


test('wallet withdrawal rejects insufficient balance and malformed UPI IDs', { timeout: 60000 }, async () => {
  const user = await createTestUser();
  await seedProfile(user.uid, 'Wallet Test');
  await assert.rejects(callFunction(user.token, 'requestWalletWithdrawal', { amountPaise: 10000, upiId: 'test@bank' }));
  await assert.rejects(callFunction(user.token, 'requestWalletWithdrawal', { amountPaise: 10000, upiId: 'not-a-upi-id' }));
  await assert.rejects(callFunction(user.token, 'adminCreditWallet', { uid: user.uid, amountPaise: 10000, reason: 'fake reward', referenceId: `test_${Date.now()}` }));
  await db.recursiveDelete(db.collection('users').doc(user.uid));
  await auth.deleteUser(user.uid);
});


test('wallet withdrawal reserves balance and records a pending request atomically', { timeout: 60000 }, async () => {
  const user = await createTestUser();
  await seedProfile(user.uid, 'Wallet Request Test');
  await db.collection('wallets').doc(user.uid).set({ uid: user.uid, balancePaise: 25000, lifetimeEarnedPaise: 25000, lifetimeWithdrawnPaise: 0 });
  const result = await callFunction(user.token, 'requestWalletWithdrawal', { amountPaise: 10000, upiId: 'test.user@bank' });
  assert.equal(result.success, true);
  assert.equal(result.status, 'pending');
  const wallet = await db.collection('wallets').doc(user.uid).get();
  assert.equal(wallet.data().balancePaise, 15000);
  const request = await db.collection('withdrawal_requests').doc(result.requestId).get();
  assert.equal(request.data().status, 'pending');
  assert.equal(request.data().amountPaise, 10000);
  assert.equal(request.data().upiId, 'test.user@bank');
  await db.recursiveDelete(db.collection('wallets').doc(user.uid));
  await db.collection('withdrawal_requests').doc(result.requestId).delete();
  await db.recursiveDelete(db.collection('users').doc(user.uid));
  await auth.deleteUser(user.uid);
});

test('practice reward claims are review-only and limited to one per UTC day', { timeout: 60000 }, async () => {
  const user = await createTestUser();
  await seedProfile(user.uid, 'Reward Claim Test');
  const result = await callFunction(user.token, 'submitPracticeRewardClaim', {
    quizTitle: 'Daily GK Practice', totalQuestions: 10, correctAnswers: 8, durationSeconds: 120,
  });
  assert.equal(result.success, true);
  assert.equal(result.status, 'pending_review');
  const claim = await db.collection('reward_claims').doc(result.claimId).get();
  assert.equal(claim.data().uid, user.uid);
  assert.equal(claim.data().status, 'pending_review');
  assert.equal((await db.collection('wallets').doc(user.uid).get()).exists, false, 'submitting a claim must not credit money');
  await assert.rejects(callFunction(user.token, 'submitPracticeRewardClaim', {
    quizTitle: 'Second claim', totalQuestions: 10, correctAnswers: 9, durationSeconds: 120,
  }));
  await assert.rejects(callFunction(user.token, 'submitPracticeRewardClaim', {
    quizTitle: 'Bad score', totalQuestions: 5, correctAnswers: 6, durationSeconds: 120,
  }));
  await db.collection('reward_claims').doc(result.claimId).delete();
  await db.collection('reward_claim_daily').doc(`${user.uid}_${new Date().toISOString().slice(0, 10)}`).delete();
  await db.recursiveDelete(db.collection('users').doc(user.uid));
  await auth.deleteUser(user.uid);
});


test('practice reward approval is admin-only, credits once, and is idempotent', { timeout: 60000 }, async () => {
  const user = await createTestUser();
  const admin = await createAdminTestUser();
  await seedProfile(user.uid, 'Reward Approval Test');
  const claimResult = await callFunction(user.token, 'submitPracticeRewardClaim', {
    quizTitle: 'Verified practice', totalQuestions: 10, correctAnswers: 9, durationSeconds: 180,
  });

  await assert.rejects(callFunction(user.token, 'adminReviewPracticeRewardClaim', {
    claimId: claimResult.claimId, action: 'approve', amountPaise: 250,
  }));

  const approved = await callFunction(admin.token, 'adminReviewPracticeRewardClaim', {
    claimId: claimResult.claimId, action: 'approve', amountPaise: 250, note: 'Emulator test approval',
  });
  assert.equal(approved.status, 'approved');
  assert.equal(approved.balancePaise, 250);

  const duplicate = await callFunction(admin.token, 'adminReviewPracticeRewardClaim', {
    claimId: claimResult.claimId, action: 'approve', amountPaise: 250,
  });
  assert.equal(duplicate.duplicate, true);
  const wallet = await db.collection('wallets').doc(user.uid).get();
  assert.equal(wallet.data().balancePaise, 250);
  const ledger = await db.collection('wallets').doc(user.uid).collection('transactions').get();
  assert.equal(ledger.size, 1);
  assert.equal((await db.collection('reward_claims').doc(claimResult.claimId).get()).data().status, 'approved');

  await db.recursiveDelete(db.collection('wallets').doc(user.uid));
  await db.collection('reward_claims').doc(claimResult.claimId).delete();
  await db.collection('reward_claim_daily').doc(`${user.uid}_${new Date().toISOString().slice(0, 10)}`).delete();
  await db.recursiveDelete(db.collection('users').doc(user.uid));
  await auth.deleteUser(user.uid);
  await db.recursiveDelete(db.collection('users').doc(admin.uid));
  await auth.deleteUser(admin.uid);
});


test('content reports are submitted through callable and only admin can review them', { timeout: 60000 }, async () => {
  const reporter = await createTestUser();
  const ordinaryUser = await createTestUser();
  const admin = await createAdminTestUser();
  await seedProfile(reporter.uid, 'Reporter');
  const targetPostId = `report_target_${reporter.uid}`;
  await db.collection('posts').doc(targetPostId).set({
    postId: targetPostId, authorId: ordinaryUser.uid, authorName: 'Target', type: 'text',
    content: 'Target post', category: 'Testing', likesCount: 0, commentsCount: 0, createdAt: new Date(),
  });
  const reportId = `report_${reporter.uid}`;
  const submitted = await callFunction(reporter.token, 'submitContentReport', {
    reportId, targetType: 'post', targetId: targetPostId, reason: 'spam', notes: 'Repeated spam',
  });
  assert.equal(submitted.status, 'pending');
  const stored = await db.collection('reports').doc(reportId).get();
  assert.equal(stored.data().reporterId, reporter.uid);
  assert.equal(stored.data().status, 'pending');
  await assert.rejects(
    callFunction(reporter.token, 'adminReviewContentReport', { reportId, decision: 'dismissed' }),
    /permission-denied|Admin|admin/i,
  );
  const reviewed = await callFunction(admin.token, 'adminReviewContentReport', {
    reportId, decision: 'reviewed', moderatorNotes: 'Checked by moderation team',
  });
  assert.equal(reviewed.status, 'reviewed');
  const after = await db.collection('reports').doc(reportId).get();
  assert.equal(after.data().status, 'reviewed');
  assert.equal(after.data().moderatorUid, admin.uid);
  await db.recursiveDelete(db.collection('posts').doc(targetPostId));
  await db.collection('reports').doc(reportId).delete();
  await auth.deleteUser(reporter.uid);
  await auth.deleteUser(ordinaryUser.uid);
  await auth.deleteUser(admin.uid);
});

test('verified quiz session scores on the server and credits a qualifying reward once', { timeout: 60000 }, async () => {
  const user = await createTestUser();
  await seedProfile(user.uid, 'Verified Quiz Tester');
  const quizId = `verified_${user.uid}`;
  const questionIds = [];
  for (let i = 0; i < 5; i++) {
    const id = `q_${user.uid}_${i}`; questionIds.push(id);
    await db.collection('quiz_questions').doc(id).set({ question: `Question ${i}?`, options: ['A', 'B', 'C', 'D'], correctIndex: 1, explanation: 'Test explanation.' });
  }
  await db.collection('published_quizzes').doc(quizId).set({ title: 'Test Quiz', questionIds, questionCount: 5, rewardPaise: 100, rewardMinPercent: 80, published: true });
  const started = await callFunction(user.token, 'startVerifiedQuizSession', { quizId });
  const answers = Object.fromEntries(started.questions.map(q => [q.id, 1]));
  await new Promise(resolve => setTimeout(resolve, 5100));
  const result = await callFunction(user.token, 'submitVerifiedQuizSession', { sessionId: started.sessionId, answers });
  assert.equal(result.correct, 5);
  assert.equal(result.rewardPaise, 100);
  const wallet = await db.collection('wallets').doc(user.uid).get();
  assert.equal(wallet.data().balancePaise, 100);
  await assert.rejects(() => callFunction(user.token, 'submitVerifiedQuizSession', { sessionId: started.sessionId, answers }));
  await db.collection('quiz_sessions').doc(started.sessionId).delete();
  for (const id of questionIds) await db.collection('quiz_questions').doc(id).delete();
  await db.collection('published_quizzes').doc(quizId).delete();
  await db.recursiveDelete(db.collection('wallets').doc(user.uid));
  await db.collection('verified_quiz_reward_daily').doc(`${user.uid}_${new Date().toISOString().slice(0, 10)}`).delete().catch(() => {});
  await auth.deleteUser(user.uid);
});

test('referral code redemption prevents self-referral and duplicate redemption', { timeout: 60000 }, async () => {
  const referrer = await createTestUser();
  const referred = await createTestUser();
  await seedProfile(referrer.uid, 'Referrer');
  await seedProfile(referred.uid, 'Referred');
  const { code } = await callFunction(referrer.token, 'getMyReferralCode', {});
  assert.ok(code);
  await assert.rejects(() => callFunction(referrer.token, 'redeemReferralCode', { code }));
  const redeemed = await callFunction(referred.token, 'redeemReferralCode', { code });
  assert.equal(redeemed.status, 'pending_review');
  await assert.rejects(() => callFunction(referred.token, 'redeemReferralCode', { code }));
  await db.collection('referrals').doc(referred.uid).delete();
  const codeDoc = await db.collection('referral_codes').doc(referrer.uid).get();
  if (codeDoc.exists) { await db.collection('referral_code_lookup').doc(codeDoc.data().code).delete(); await codeDoc.ref.delete(); }
  await auth.deleteUser(referrer.uid); await auth.deleteUser(referred.uid);
});
