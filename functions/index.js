const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore, FieldPath } = require('firebase-admin/firestore');
const { defineSecret } = require('firebase-functions/params');

const app = initializeApp();
const DATABASE_ID = 'ai-studio-android-gkswal-00669d47-2b46-46f6-b28a-6db36873372b';
const db = getFirestore(app, DATABASE_ID);
const DELETION_BATCH_SIZE = 100;
const DELETION_JOB_COLLECTION = 'account_deletions';
// The Functions emulator deliberately bypasses App Check so integration tests can use
// emulator-issued auth tokens. Deployed production functions enforce App Check.
const enforceAppCheck = process.env.FUNCTIONS_EMULATOR !== 'true';

function accountDeletionRef(uid) {
  return db.collection(DELETION_JOB_COLLECTION).doc(uid);
}


function requireString(value, field, maxLength) {
  if (typeof value !== 'string' || value.trim().length === 0 || value.trim().length > maxLength) {
    throw new HttpsError('invalid-argument', `Invalid ${field}.`);
  }
  return value.trim();
}

function requirePostId(value) {
  const postId = requireString(value, 'postId', 128);
  if (!/^[a-zA-Z0-9_-]+$/.test(postId)) {
    throw new HttpsError('invalid-argument', 'Invalid postId.');
  }
  return postId;
}

// Aggregate counters are never writable directly from Android clients. Each callable
// performs the document mutation and counter update in one Firestore transaction.
exports.togglePostLike = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const postId = requirePostId(request.data?.postId);
  const postRef = db.collection('posts').doc(postId);
  const likeRef = postRef.collection('likes').doc(uid);

  return db.runTransaction(async (tx) => {
    const [postSnap, likeSnap, lockSnap] = await Promise.all([tx.get(postRef), tx.get(likeRef), tx.get(accountDeletionRef(uid))]);
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (!postSnap.exists) throw new HttpsError('not-found', 'Post not found.');
    const post = postSnap.data();
    const authorLock = await tx.get(accountDeletionRef(post.authorId));
    if (authorLock.exists || post.deleting === true) throw new HttpsError('failed-precondition', 'This post or its author is being deleted.');
    if (likeSnap.exists) {
      tx.delete(likeRef);
      tx.update(postRef, {
        likesCount: Math.max(0, Number(post.likesCount || 0) - 1),
        updatedAt: new Date(),
      });
      return { liked: false };
    }
    tx.create(likeRef, { userId: uid, createdAt: new Date() });
    tx.update(postRef, {
      likesCount: Math.max(0, Number(post.likesCount || 0)) + 1,
      updatedAt: new Date(),
    });
    return { liked: true };
  });
});

exports.voteOnPoll = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const postId = requirePostId(request.data?.postId);
  const optionIndex = request.data?.optionIndex;
  if (!Number.isInteger(optionIndex) || optionIndex < 0 || optionIndex > 3) {
    throw new HttpsError('invalid-argument', 'Invalid poll option.');
  }
  const postRef = db.collection('posts').doc(postId);
  const voteRef = postRef.collection('votes').doc(uid);

  return db.runTransaction(async (tx) => {
    const [postSnap, voteSnap, lockSnap] = await Promise.all([tx.get(postRef), tx.get(voteRef), tx.get(accountDeletionRef(uid))]);
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (!postSnap.exists) throw new HttpsError('not-found', 'Poll not found.');
    const post = postSnap.data();
    const authorLock = await tx.get(accountDeletionRef(post.authorId));
    if (authorLock.exists || post.deleting === true) throw new HttpsError('failed-precondition', 'This poll or its author is being deleted.');
    if (post.type !== 'poll' || !Array.isArray(post.pollOptions) || post.pollOptions.length < 2 || post.pollOptions.length > 4) {
      throw new HttpsError('failed-precondition', 'This post is not a valid poll.');
    }
    if (post.isClosed === true || (post.expiresAt && post.expiresAt.toMillis && post.expiresAt.toMillis() <= Date.now())) {
      throw new HttpsError('failed-precondition', 'This poll is closed.');
    }
    if (optionIndex >= post.pollOptions.length) throw new HttpsError('invalid-argument', 'Option does not exist.');
    if (voteSnap.exists && post.allowVoteChange === false) {
      throw new HttpsError('failed-precondition', 'Vote changes are disabled.');
    }
    const previousIndex = voteSnap.exists ? Number(voteSnap.data().optionIndex) : -1;
    if (previousIndex === optionIndex) return { changed: false, optionIndex };

    const options = post.pollOptions.map((option, index) => ({
      id: Number.isInteger(option.id) ? option.id : index,
      text: typeof option.text === 'string' ? option.text : '',
      votes: Math.max(0, Number(option.votes || 0)),
    }));
    let totalVotes = Math.max(0, Number(post.totalVotes || 0));
    if (previousIndex >= 0 && previousIndex < options.length) {
      options[previousIndex].votes = Math.max(0, options[previousIndex].votes - 1);
    } else {
      totalVotes += 1;
    }
    options[optionIndex].votes += 1;
    tx.set(voteRef, { userId: uid, optionIndex, votedAt: new Date() });
    tx.update(postRef, { pollOptions: options, totalVotes, updatedAt: new Date() });
    return { changed: true, optionIndex };
  });
});

exports.addPostComment = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const postId = requirePostId(request.data?.postId);
  const content = requireString(request.data?.content, 'comment', 500);
  const parentId = request.data?.parentId == null ? null : requireString(request.data.parentId, 'parentId', 128);
  const postRef = db.collection('posts').doc(postId);
  const commentRef = postRef.collection('comments').doc();
  const profileSnap = await db.collection('users').doc(uid).get();
  const displayName = String(profileSnap.data()?.displayName || request.auth.token.name || 'GK Aspirant').trim().slice(0, 60) || 'GK Aspirant';

  await db.runTransaction(async (tx) => {
    const [postSnap, lockSnap] = await Promise.all([tx.get(postRef), tx.get(accountDeletionRef(uid))]);
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (!postSnap.exists) throw new HttpsError('not-found', 'Post not found.');
    const postData = postSnap.data();
    const authorLock = await tx.get(accountDeletionRef(postData.authorId));
    if (authorLock.exists || postData.deleting === true) throw new HttpsError('failed-precondition', 'This post or its author is being deleted.');
    if (parentId) {
      const parentRef = postRef.collection('comments').doc(parentId);
      const parentSnap = await tx.get(parentRef);
      if (!parentSnap.exists) throw new HttpsError('invalid-argument', 'Reply target does not exist.');
    }
    tx.create(commentRef, {
      commentId: commentRef.id,
      authorId: uid,
      authorName: displayName,
      content,
      ...(parentId ? { parentId } : {}),
      createdAt: new Date(),
    });
    tx.update(postRef, {
      commentsCount: Math.max(0, Number(postData.commentsCount || 0)) + 1,
      updatedAt: new Date(),
    });
  });
  return { success: true, commentId: commentRef.id };
});


// Follow edges and profile counters are changed together on the trusted server.
exports.toggleFollowAuthor = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const targetUid = requireString(request.data?.targetAuthorId, 'targetAuthorId', 128);
  if (!/^[a-zA-Z0-9_-]+$/.test(targetUid)) throw new HttpsError('invalid-argument', 'Invalid target user.');
  if (uid === targetUid) throw new HttpsError('failed-precondition', 'You cannot follow yourself.');

  const sourceRef = db.collection('users').doc(uid);
  const targetRef = db.collection('users').doc(targetUid);
  const followingRef = sourceRef.collection('following').doc(targetUid);
  const followerRef = targetRef.collection('followers').doc(uid);
  return db.runTransaction(async (tx) => {
    const [sourceSnap, targetSnap, followingSnap, followerSnap, sourceLock, targetLock] = await Promise.all([
      tx.get(sourceRef), tx.get(targetRef), tx.get(followingRef), tx.get(followerRef),
      tx.get(accountDeletionRef(uid)), tx.get(accountDeletionRef(targetUid)),
    ]);
    if (sourceLock.exists || targetLock.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (!sourceSnap.exists || !targetSnap.exists) throw new HttpsError('not-found', 'User profile not found.');
    if (followingSnap.exists || followerSnap.exists) {
      // Repair one-sided legacy edges by deleting both and only decrementing if both existed.
      tx.delete(followingRef);
      tx.delete(followerRef);
      const sourceCount = Math.max(0, Number(sourceSnap.data().followingCount || 0) - (followingSnap.exists ? 1 : 0));
      const targetCount = Math.max(0, Number(targetSnap.data().followersCount || 0) - (followerSnap.exists ? 1 : 0));
      tx.update(sourceRef, { followingCount: sourceCount, updatedAt: new Date() });
      tx.update(targetRef, { followersCount: targetCount, updatedAt: new Date() });
      return { following: false };
    }
    tx.create(followingRef, { followingId: targetUid, createdAt: new Date() });
    tx.create(followerRef, { followerId: uid, createdAt: new Date() });
    tx.update(sourceRef, { followingCount: Math.max(0, Number(sourceSnap.data().followingCount || 0)) + 1, updatedAt: new Date() });
    tx.update(targetRef, { followersCount: Math.max(0, Number(targetSnap.data().followersCount || 0)) + 1, updatedAt: new Date() });
    return { following: true };
  });
});

// A post and all nested interaction documents are removed by the server, not a client.
exports.submitContentReport = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const targetType = requireString(request.data?.targetType, 'targetType', 20);
  if (!['post', 'comment', 'user'].includes(targetType)) {
    throw new HttpsError('invalid-argument', 'Unsupported report target type.');
  }
  const targetId = requireString(request.data?.targetId, 'targetId', 128);
  if (!/^[a-zA-Z0-9_-]+$/.test(targetId)) throw new HttpsError('invalid-argument', 'Invalid targetId.');
  const reason = requireString(request.data?.reason, 'reason', 100);
  const notes = typeof request.data?.notes === 'string' ? request.data.notes.trim() : '';
  if (notes.length > 500) throw new HttpsError('invalid-argument', 'Report notes are too long.');
  if (targetType === 'user' && targetId === uid) {
    throw new HttpsError('invalid-argument', 'You cannot report your own account.');
  }
  const lock = await accountDeletionRef(uid).get();
  if (lock.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
  let reportId = request.data?.reportId;
  if (reportId == null || reportId === '') reportId = `${uid}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
  reportId = requireString(reportId, 'reportId', 128);
  if (!/^[a-zA-Z0-9_-]+$/.test(reportId)) throw new HttpsError('invalid-argument', 'Invalid reportId.');
  if (targetType === 'post' && !(await db.collection('posts').doc(targetId).get()).exists) {
    throw new HttpsError('not-found', 'Post not found.');
  }
  if (targetType === 'user' && !(await db.collection('users').doc(targetId).get()).exists) {
    throw new HttpsError('not-found', 'User not found.');
  }
  if (targetType === 'comment') {
    const comment = await db.collectionGroup('comments').where('commentId', '==', targetId).limit(1).get();
    if (comment.empty) throw new HttpsError('not-found', 'Comment not found.');
  }
  const reportRef = db.collection('reports').doc(reportId);
  const existing = await reportRef.get();
  if (existing.exists) throw new HttpsError('already-exists', 'Report already submitted.');
  const report = {
    reportId, reporterId: uid, targetType, targetId, reason,
    notes: notes || null, status: 'pending', createdAt: new Date(), updatedAt: new Date(),
  };
  await reportRef.create(report);
  return { reportId, status: 'pending' };
});

exports.adminReviewContentReport = onCall({ enforceAppCheck }, async (request) => {
  requireAdmin(request);
  const reportId = requireString(request.data?.reportId, 'reportId', 128);
  const decision = requireString(request.data?.decision, 'decision', 20);
  if (!['reviewed', 'dismissed'].includes(decision)) {
    throw new HttpsError('invalid-argument', 'Decision must be reviewed or dismissed.');
  }
  const moderatorNotes = typeof request.data?.moderatorNotes === 'string' ? request.data.moderatorNotes.trim() : '';
  if (moderatorNotes.length > 500) throw new HttpsError('invalid-argument', 'Moderator notes are too long.');
  const ref = db.collection('reports').doc(reportId);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError('not-found', 'Report not found.');
    const current = snap.data();
    if (!['pending', 'reviewed', 'dismissed'].includes(current.status)) {
      throw new HttpsError('failed-precondition', 'Report has an invalid status.');
    }
    tx.update(ref, {
      status: decision,
      moderatorUid: request.auth.uid,
      moderatorNotes: moderatorNotes || null,
      reviewedAt: new Date(),
      updatedAt: new Date(),
    });
  });
  return { reportId, status: decision };
});

exports.deleteMyPost = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const postId = requirePostId(request.data?.postId);
  const postRef = db.collection('posts').doc(postId);
  const canDelete = await db.runTransaction(async (tx) => {
    const [postSnap, lockSnap] = await Promise.all([tx.get(postRef), tx.get(accountDeletionRef(request.auth.uid))]);
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (!postSnap.exists) return false;
    if (postSnap.data().authorId !== request.auth.uid) throw new HttpsError('permission-denied', 'You can delete only your own post.');
    if (postSnap.data().deleting !== true) tx.update(postRef, { deleting: true, updatedAt: new Date() });
    return true;
  });
  if (!canDelete) return { success: true };
  await db.recursiveDelete(postRef);
  return { success: true };
});



// Wallet money is stored as integer paise. Clients can only read their own wallet;
// all credits, reservations and refunds are performed by trusted callable functions.
const MIN_WITHDRAWAL_PAISE = 10000; // ₹100 minimum
const MAX_WITHDRAWAL_PAISE = 1000000; // ₹10,000 per request
function requireAdmin(request) {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  if (request.auth.token.admin !== true) throw new HttpsError('permission-denied', 'Administrator access is required.');
}
function requireUpiId(value) {
  const upiId = requireString(value, 'UPI ID', 255);
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{1,127}@[A-Za-z][A-Za-z0-9.-]{1,63}$/.test(upiId)) {
    throw new HttpsError('invalid-argument', 'Enter a valid UPI ID, for example name@bank.');
  }
  return upiId;
}
function walletRef(uid) { return db.collection('wallets').doc(uid); }
function walletLedgerRef(uid, transactionId) { return walletRef(uid).collection('transactions').doc(transactionId); }

exports.requestWalletWithdrawal = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const amountPaise = request.data?.amountPaise;
  const upiId = requireUpiId(request.data?.upiId);
  if (!Number.isSafeInteger(amountPaise) || amountPaise < MIN_WITHDRAWAL_PAISE || amountPaise > MAX_WITHDRAWAL_PAISE) {
    throw new HttpsError('invalid-argument', 'Withdrawal must be between ₹100 and ₹10,000.');
  }
  const id = require('node:crypto').randomUUID();
  const wallet = walletRef(uid);
  const withdrawal = db.collection('withdrawal_requests').doc(id);
  const ledger = walletLedgerRef(uid, id);
  await db.runTransaction(async (tx) => {
    const [walletSnap, lockSnap] = await Promise.all([tx.get(wallet), tx.get(accountDeletionRef(uid))]);
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    const currentBalance = walletSnap.exists ? Number(walletSnap.data().balancePaise || 0) : 0;
    if (!Number.isSafeInteger(currentBalance) || currentBalance < amountPaise) {
      throw new HttpsError('failed-precondition', 'Insufficient available wallet balance.');
    }
    tx.set(wallet, {
      uid, balancePaise: currentBalance - amountPaise,
      lifetimeWithdrawnPaise: Number(walletSnap.data()?.lifetimeWithdrawnPaise || 0),
      updatedAt: new Date(),
    }, { merge: true });
    tx.create(withdrawal, {
      requestId: id, uid, amountPaise, upiId, status: 'pending',
      createdAt: new Date(), updatedAt: new Date(),
    });
    tx.create(ledger, {
      transactionId: id, type: 'withdrawal_reserved', amountPaise: -amountPaise,
      balanceAfterPaise: currentBalance - amountPaise, status: 'pending',
      withdrawalRequestId: id, createdAt: new Date(),
    });
  });
  return { success: true, requestId: id, status: 'pending' };
});

// Only trusted admins can grant earnings. Never call this from a user-facing reward button.
exports.adminCreditWallet = onCall({ enforceAppCheck }, async (request) => {
  requireAdmin(request);
  const uid = requireString(request.data?.uid, 'uid', 128);
  const amountPaise = request.data?.amountPaise;
  const reason = requireString(request.data?.reason, 'reason', 160);
  const referenceId = requireString(request.data?.referenceId, 'referenceId', 128);
  if (!/^[A-Za-z0-9_-]+$/.test(uid) || !/^[A-Za-z0-9_-]+$/.test(referenceId)) {
    throw new HttpsError('invalid-argument', 'Invalid UID or reference ID.');
  }
  if (!Number.isSafeInteger(amountPaise) || amountPaise <= 0 || amountPaise > 10000000) {
    throw new HttpsError('invalid-argument', 'Credit amount must be between 1 paise and ₹100,000.');
  }
  const wallet = walletRef(uid);
  const ledger = walletLedgerRef(uid, `credit_${referenceId}`);
  return db.runTransaction(async (tx) => {
    const [walletSnap, ledgerSnap, profileSnap, lockSnap] = await Promise.all([
      tx.get(wallet), tx.get(ledger), tx.get(db.collection('users').doc(uid)), tx.get(accountDeletionRef(uid)),
    ]);
    if (!profileSnap.exists) throw new HttpsError('not-found', 'User profile not found.');
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (ledgerSnap.exists) return { success: true, duplicate: true, balancePaise: Number(walletSnap.data()?.balancePaise || 0) };
    const currentBalance = Number(walletSnap.data()?.balancePaise || 0);
    const newBalance = currentBalance + amountPaise;
    tx.set(wallet, {
      uid, balancePaise: newBalance,
      lifetimeEarnedPaise: Number(walletSnap.data()?.lifetimeEarnedPaise || 0) + amountPaise,
      lifetimeWithdrawnPaise: Number(walletSnap.data()?.lifetimeWithdrawnPaise || 0),
      updatedAt: new Date(),
    }, { merge: true });
    tx.create(ledger, {
      transactionId: `credit_${referenceId}`, type: 'earning_credit', amountPaise,
      balanceAfterPaise: newBalance, status: 'completed', reason, referenceId,
      createdAt: new Date(), createdBy: request.auth.uid,
    });
    return { success: true, balancePaise: newBalance };
  });
});

// User-submitted practice reward claims are review-only. Client-reported quiz scores are NOT trusted
// for automatic cash credit because the current quiz bank/answer checking runs locally on the device.
exports.submitPracticeRewardClaim = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const title = requireString(request.data?.quizTitle, 'quiz title', 100);
  const total = request.data?.totalQuestions;
  const correct = request.data?.correctAnswers;
  const durationSeconds = request.data?.durationSeconds;
  if (!Number.isInteger(total) || total < 5 || total > 100 || !Number.isInteger(correct) || correct < 0 || correct > total ||
      !Number.isInteger(durationSeconds) || durationSeconds < 10 || durationSeconds > 7200) {
    throw new HttpsError('invalid-argument', 'Invalid practice-session details.');
  }
  const crypto = require('node:crypto');
  const claimId = crypto.randomUUID();
  const day = new Date().toISOString().slice(0, 10);
  const dailyRef = db.collection('reward_claim_daily').doc(`${uid}_${day}`);
  const claimRef = db.collection('reward_claims').doc(claimId);
  await db.runTransaction(async (tx) => {
    const [dailySnap, lockSnap, profileSnap] = await Promise.all([
      tx.get(dailyRef), tx.get(accountDeletionRef(uid)), tx.get(db.collection('users').doc(uid)),
    ]);
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (!profileSnap.exists) throw new HttpsError('failed-precondition', 'Complete your profile before submitting a claim.');
    if (dailySnap.exists) throw new HttpsError('resource-exhausted', 'Only one practice reward review can be submitted per UTC day.');
    tx.create(dailyRef, { uid, claimId, day, createdAt: new Date() });
    tx.create(claimRef, {
      claimId, uid, type: 'practice_quiz', quizTitle: title,
      totalQuestions: total, correctAnswers: correct, durationSeconds,
      status: 'pending_review', createdAt: new Date(), updatedAt: new Date(),
    });
  });
  return { success: true, claimId, status: 'pending_review', message: 'Submitted for manual review; no money is credited automatically.' };
});

// Only an admin may approve/reject a claim. Approval credits at most ₹100 and is idempotent.
exports.adminReviewPracticeRewardClaim = onCall({ enforceAppCheck }, async (request) => {
  requireAdmin(request);
  const claimId = requireString(request.data?.claimId, 'claimId', 128);
  const action = request.data?.action;
  const note = typeof request.data?.note === 'string' ? request.data.note.trim().slice(0, 300) : '';
  const amountPaise = request.data?.amountPaise;
  if (!/^[A-Za-z0-9_-]+$/.test(claimId) || !['approve', 'reject'].includes(action)) {
    throw new HttpsError('invalid-argument', 'Invalid claim review action.');
  }
  if (action === 'approve' && (!Number.isSafeInteger(amountPaise) || amountPaise < 1 || amountPaise > 10000)) {
    throw new HttpsError('invalid-argument', 'Approved reward must be between 1 paise and ₹100.');
  }
  const claimRef = db.collection('reward_claims').doc(claimId);
  return db.runTransaction(async (tx) => {
    const claimSnap = await tx.get(claimRef);
    if (!claimSnap.exists) throw new HttpsError('not-found', 'Reward claim not found.');
    const claim = claimSnap.data();
    if (claim.status === (action === 'approve' ? 'approved' : 'rejected')) return { success: true, duplicate: true, status: claim.status };
    if (claim.status !== 'pending_review') throw new HttpsError('failed-precondition', 'This claim was already reviewed.');
    const wallet = walletRef(claim.uid);
    const ledgerId = `reward_${claimId.replace(/-/g, '')}`;
    const ledger = walletLedgerRef(claim.uid, ledgerId);
    const [walletSnap, ledgerSnap, lockSnap] = await Promise.all([
      tx.get(wallet), tx.get(ledger), tx.get(accountDeletionRef(claim.uid)),
    ]);
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (action === 'approve') {
      if (ledgerSnap.exists) throw new HttpsError('already-exists', 'This reward has already been credited.');
      const balance = Number(walletSnap.data()?.balancePaise || 0);
      const nextBalance = balance + amountPaise;
      tx.set(wallet, {
        uid: claim.uid, balancePaise: nextBalance,
        lifetimeEarnedPaise: Number(walletSnap.data()?.lifetimeEarnedPaise || 0) + amountPaise,
        lifetimeWithdrawnPaise: Number(walletSnap.data()?.lifetimeWithdrawnPaise || 0), updatedAt: new Date(),
      }, { merge: true });
      tx.create(ledger, {
        transactionId: ledgerId, type: 'practice_reward', amountPaise,
        balanceAfterPaise: nextBalance, status: 'completed', reason: 'Approved practice reward claim',
        referenceId: claimId, createdAt: new Date(), createdBy: request.auth.uid,
      });
    }
    tx.update(claimRef, {
      status: action === 'approve' ? 'approved' : 'rejected',
      ...(action === 'approve' ? { approvedAmountPaise: amountPaise } : {}),
      reviewNote: note, reviewedAt: new Date(), reviewedBy: request.auth.uid, updatedAt: new Date(),
    });
    return { success: true, status: action === 'approve' ? 'approved' : 'rejected', ...(action === 'approve' ? { balancePaise: Number(walletSnap.data()?.balancePaise || 0) + amountPaise } : {}) };
  });
});

// Admin marks a request paid only after the UPI transfer is completed outside the app.
// Rejected requests are refunded atomically; duplicate review actions are idempotent.
exports.adminReviewWithdrawal = onCall({ enforceAppCheck }, async (request) => {
  requireAdmin(request);
  const requestId = requireString(request.data?.requestId, 'requestId', 128);
  const action = request.data?.action;
  const adminNote = typeof request.data?.adminNote === 'string' ? request.data.adminNote.trim().slice(0, 300) : '';
  const paymentReference = action === 'paid' ? requireString(request.data?.paymentReference, 'paymentReference', 100) : '';
  if (!/^[A-Za-z0-9_-]+$/.test(requestId) || !['paid', 'rejected'].includes(action)) {
    throw new HttpsError('invalid-argument', 'Invalid withdrawal review action.');
  }
  const withdrawal = db.collection('withdrawal_requests').doc(requestId);
  return db.runTransaction(async (tx) => {
    const requestSnap = await tx.get(withdrawal);
    if (!requestSnap.exists) throw new HttpsError('not-found', 'Withdrawal request not found.');
    const item = requestSnap.data();
    if (item.status === action) return { success: true, duplicate: true, status: item.status };
    if (item.status !== 'pending') throw new HttpsError('failed-precondition', 'This withdrawal request was already reviewed.');
    const wallet = walletRef(item.uid);
    const sourceLedger = walletLedgerRef(item.uid, requestId);
    const [walletSnap, sourceLedgerSnap, lockSnap] = await Promise.all([
      tx.get(wallet), tx.get(sourceLedger), tx.get(accountDeletionRef(item.uid)),
    ]);
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (!walletSnap.exists) throw new HttpsError('failed-precondition', 'Wallet record is missing.');
    const balance = Number(walletSnap.data().balancePaise || 0);
    if (action === 'rejected') {
      const newBalance = balance + Number(item.amountPaise);
      tx.update(wallet, { balancePaise: newBalance, updatedAt: new Date() });
      const refundLedger = walletLedgerRef(item.uid, `refund_${requestId}`);
      tx.create(refundLedger, {
        transactionId: `refund_${requestId}`, type: 'withdrawal_refund',
        amountPaise: Number(item.amountPaise), balanceAfterPaise: newBalance,
        status: 'completed', withdrawalRequestId: requestId, createdAt: new Date(),
      });
    } else {
      tx.update(wallet, {
        lifetimeWithdrawnPaise: Number(walletSnap.data().lifetimeWithdrawnPaise || 0) + Number(item.amountPaise),
        updatedAt: new Date(),
      });
    }
    tx.update(withdrawal, {
      status: action, adminNote, ...(action === 'paid' ? { paymentReference } : {}), reviewedAt: new Date(), reviewedBy: request.auth.uid, updatedAt: new Date(),
    });
    if (sourceLedgerSnap.exists) tx.update(sourceLedger, { status: action === 'paid' ? 'completed' : 'refunded' });
    return { success: true, status: action };
  });
});

async function removeUserInteractions(collectionName, ownerField, uid) {
  // Re-query small pages until empty. Each item is deleted transactionally with its counter
  // adjustment, so a retry cannot decrement twice: missing interaction documents are skipped.
  while (true) {
    const snapshot = await db.collectionGroup(collectionName)
      .where(ownerField, '==', uid).limit(DELETION_BATCH_SIZE).get();
    if (snapshot.empty) break;
    for (const interaction of snapshot.docs) {
      const postRef = interaction.ref.parent.parent;
      if (!postRef) {
        await interaction.ref.delete();
        continue;
      }
      await db.runTransaction(async (tx) => {
        const [postSnap, interactionSnap] = await Promise.all([tx.get(postRef), tx.get(interaction.ref)]);
        if (!interactionSnap.exists) return;
        if (!postSnap.exists) { tx.delete(interaction.ref); return; }
        const post = postSnap.data();
        if (collectionName === 'likes') {
          tx.update(postRef, { likesCount: Math.max(0, Number(post.likesCount || 0) - 1), updatedAt: new Date() });
        } else if (collectionName === 'comments') {
          tx.update(postRef, { commentsCount: Math.max(0, Number(post.commentsCount || 0) - 1), updatedAt: new Date() });
        } else if (collectionName === 'votes') {
          const optionIndex = Number(interactionSnap.data().optionIndex);
          const options = Array.isArray(post.pollOptions) ? post.pollOptions.map((option, index) => ({
            ...option,
            votes: Math.max(0, Number(option.votes || 0) - (index === optionIndex ? 1 : 0)),
          })) : [];
          tx.update(postRef, { pollOptions: options, totalVotes: Math.max(0, Number(post.totalVotes || 0) - 1), updatedAt: new Date() });
        }
        tx.delete(interaction.ref);
      });
    }
  }
}

async function removeFollowEdgesForAccount(uid) {
  const ownProfileRef = db.collection('users').doc(uid);

  // Process bounded pages; deleting each edge makes the next query progress on retries.
  while (true) {
    const ownFollowers = await ownProfileRef.collection('followers').limit(DELETION_BATCH_SIZE).get();
    if (ownFollowers.empty) break;
    for (const edge of ownFollowers.docs) {
      const followerUid = edge.id;
      const followerProfile = db.collection('users').doc(followerUid);
      await db.runTransaction(async (tx) => {
        const [edgeSnap, profileSnap] = await Promise.all([tx.get(edge.ref), tx.get(followerProfile)]);
        if (!edgeSnap.exists) return;
        tx.delete(edge.ref);
        if (profileSnap.exists) tx.update(followerProfile, { followingCount: Math.max(0, Number(profileSnap.data().followingCount || 0) - 1), updatedAt: new Date() });
        tx.delete(db.collection('users').doc(followerUid).collection('following').doc(uid));
      });
    }
  }

  while (true) {
    const ownFollowing = await ownProfileRef.collection('following').limit(DELETION_BATCH_SIZE).get();
    if (ownFollowing.empty) break;
    for (const edge of ownFollowing.docs) {
      const targetUid = edge.id;
      const targetProfile = db.collection('users').doc(targetUid);
      const targetFollowerEdge = targetProfile.collection('followers').doc(uid);
      await db.runTransaction(async (tx) => {
        const [edgeSnap, targetEdgeSnap, targetSnap] = await Promise.all([tx.get(edge.ref), tx.get(targetFollowerEdge), tx.get(targetProfile)]);
        if (edgeSnap.exists) tx.delete(edge.ref);
        if (targetEdgeSnap.exists) {
          tx.delete(targetFollowerEdge);
          if (targetSnap.exists) tx.update(targetProfile, { followersCount: Math.max(0, Number(targetSnap.data().followersCount || 0) - 1), updatedAt: new Date() });
        }
      });
    }
  }

  // Also clean orphaned/legacy references. Canonical edge cleanup above updates counters.
  await deleteCollectionGroupByField('followers', 'followerId', uid);
  await deleteCollectionGroupByField('following', 'followingId', uid);
}

async function deleteCollectionGroupByField(collectionName, fieldName, uid) {
  while (true) {
    const snapshot = await db.collectionGroup(collectionName).where(fieldName, '==', uid).limit(DELETION_BATCH_SIZE).get();
    if (snapshot.empty) break;
    const batch = db.batch();
    snapshot.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
  }
}

async function recursivelyDeleteIfExists(ref) {
  const snap = await ref.get();
  if (snap.exists) await db.recursiveDelete(ref);
}

async function markOwnedPostsDeleting(uid) {
  // Stable document-id pagination ensures every owned post is marked without loading
  // the whole account at once or repeatedly selecting the same already-marked page.
  let lastDoc = null;
  while (true) {
    let query = db.collection('posts').where('authorId', '==', uid)
      .orderBy(FieldPath.documentId()).limit(DELETION_BATCH_SIZE);
    if (lastDoc) query = query.startAfter(lastDoc);
    const posts = await query.get();
    if (posts.empty) break;
    const batch = db.batch();
    posts.docs.forEach((post) => batch.set(post.ref, { deleting: true, updatedAt: new Date() }, { merge: true }));
    await batch.commit();
    lastDoc = posts.docs[posts.docs.length - 1];
  }
}

async function deleteOwnedPostsInChunks(uid) {
  while (true) {
    const posts = await db.collection('posts').where('authorId', '==', uid).limit(10).get();
    if (posts.empty) break;
    for (const post of posts.docs) await db.recursiveDelete(post.ref);
  }
}

async function deleteWalletAndWithdrawalData(uid) {
  // Remove wallet ledger and withdrawal records containing UPI identifiers before account deletion completes.
  await recursivelyDeleteIfExists(walletRef(uid));
  for (const collectionName of ['withdrawal_requests', 'reward_claims', 'reward_claim_daily', 'quiz_sessions', 'verified_quiz_reward_daily']) {
    while (true) {
      const requests = await db.collection(collectionName).where('uid', '==', uid).limit(DELETION_BATCH_SIZE).get();
      if (requests.empty) break;
      const batch = db.batch();
      requests.docs.forEach((doc) => batch.delete(doc.ref));
      await batch.commit();
    }
  }
  // Remove referral records that reference this account and its issued code.
  for (const [collectionName, field] of [['referrals', 'referredUid'], ['referrals', 'referrerUid']]) {
    while (true) {
      const refs = await db.collection(collectionName).where(field, '==', uid).limit(DELETION_BATCH_SIZE).get();
      if (refs.empty) break;
      const batch = db.batch(); refs.docs.forEach(doc => batch.delete(doc.ref)); await batch.commit();
    }
  }
  const codeRef = db.collection('referral_codes').doc(uid);
  const codeSnap = await codeRef.get();
  if (codeSnap.exists) {
    const code = codeSnap.data().code;
    await codeRef.delete();
    if (code) await db.collection('referral_code_lookup').doc(code).delete().catch(() => {});
  }
}

const DELETION_STAGES = [
  'mark_posts', 'remove_likes', 'remove_votes', 'remove_comments',
  'delete_owned_posts', 'delete_reports', 'remove_follows', 'delete_wallet_data', 'delete_profile', 'delete_auth', 'complete',
];



// --- Secure server-verified quiz sessions ---
// Admins seed quiz_questions and published_quizzes using the Admin SDK/import script.
exports.startVerifiedQuizSession = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const quizId = requireString(request.data?.quizId, 'quizId', 100);
  if (!/^[A-Za-z0-9_-]+$/.test(quizId)) throw new HttpsError('invalid-argument', 'Invalid quiz ID.');
  const quizSnap = await db.collection('published_quizzes').doc(quizId).get();
  if (!quizSnap.exists || quizSnap.data().published !== true) throw new HttpsError('not-found', 'Quiz is not published.');
  const quiz = quizSnap.data();
  const ids = Array.isArray(quiz.questionIds) ? [...new Set(quiz.questionIds)] : [];
  const count = Math.min(100, Math.max(5, Number(quiz.questionCount || 10)));
  if (ids.length < count || ids.length > 5000) throw new HttpsError('failed-precondition', 'Quiz question bank is not ready.');
  for (let i = ids.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [ids[i], ids[j]] = [ids[j], ids[i]]; }
  const chosen = ids.slice(0, count);
  const questionSnaps = await db.getAll(...chosen.map(id => db.collection('quiz_questions').doc(String(id))));
  if (questionSnaps.some(x => !x.exists)) throw new HttpsError('failed-precondition', 'Published quiz contains missing questions.');
  const questions = questionSnaps.map((snap, i) => {
    const q = snap.data();
    if (typeof q.question !== 'string' || !Array.isArray(q.options) || q.options.length < 2 || !Number.isInteger(q.correctIndex) || q.correctIndex < 0 || q.correctIndex >= q.options.length) throw new HttpsError('failed-precondition', 'Question bank has an invalid record.');
    return { id: snap.id, question: q.question, options: q.options, correctIndex: q.correctIndex, explanation: String(q.explanation || '').slice(0, 1000), order: i };
  });
  const sessionId = require('node:crypto').randomUUID();
  const now = Date.now();
  await db.collection('quiz_sessions').doc(sessionId).create({
    sessionId, uid, quizId, questionData: questions, questionCount: questions.length,
    status: 'started', startedAt: new Date(now), expiresAt: new Date(now + 30 * 60 * 1000), createdAt: new Date(now),
  });
  return { sessionId, quizId, questions: questions.map(({id, question, options, order}) => ({id, question, options, order})), expiresAt: now + 30 * 60 * 1000 };
});

exports.submitVerifiedQuizSession = onCall({ enforceAppCheck }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const sessionId = requireString(request.data?.sessionId, 'sessionId', 128);
  const answers = request.data?.answers;
  if (!answers || typeof answers !== 'object' || Array.isArray(answers)) throw new HttpsError('invalid-argument', 'Answers are required.');
  const sessionRef = db.collection('quiz_sessions').doc(sessionId);
  const sessionSnap = await sessionRef.get();
  if (!sessionSnap.exists || sessionSnap.data().uid !== uid) throw new HttpsError('not-found', 'Quiz session not found.');
  const session = sessionSnap.data();
  if (session.status !== 'started') throw new HttpsError('failed-precondition', 'Quiz session has already been submitted.');
  const now = Date.now();
  if (!session.expiresAt || session.expiresAt.toMillis() < now) throw new HttpsError('deadline-exceeded', 'Quiz session expired.');
  const durationSeconds = Math.floor((now - session.startedAt.toMillis()) / 1000);
  if (durationSeconds < 5 || durationSeconds > 1800) throw new HttpsError('failed-precondition', 'Invalid quiz duration.');
  const questionData = session.questionData || [];
  let correct = 0;
  const results = questionData.map(q => {
    const given = answers[q.id];
    const isCorrect = Number.isInteger(given) && given === q.correctIndex;
    if (isCorrect) correct++;
    return { id: q.id, selectedIndex: Number.isInteger(given) ? given : null, correctIndex: q.correctIndex, isCorrect, explanation: q.explanation };
  });
  const quizSnap = await db.collection('published_quizzes').doc(session.quizId).get();
  if (!quizSnap.exists || quizSnap.data().published !== true) throw new HttpsError('failed-precondition', 'Quiz is no longer published.');
  const quiz = quizSnap.data();
  const minPercent = Math.min(100, Math.max(50, Number(quiz.rewardMinPercent || 80)));
  const rewardPaise = Number(quiz.rewardPaise || 0);
  if (!Number.isSafeInteger(rewardPaise) || rewardPaise < 0 || rewardPaise > 10000) throw new HttpsError('failed-precondition', 'Invalid quiz reward configuration.');
  const qualifies = rewardPaise > 0 && (correct * 100 / questionData.length) >= minPercent;
  const day = new Date().toISOString().slice(0, 10);
  const dailyRef = db.collection('verified_quiz_reward_daily').doc(`${uid}_${day}`);
  const wallet = walletRef(uid);
  const ledgerId = `verified_quiz_${sessionId}`;
  let credited = 0;
  await db.runTransaction(async tx => {
    const [freshSession, dailySnap, walletSnap, ledgerSnap, lockSnap] = await Promise.all([tx.get(sessionRef), tx.get(dailyRef), tx.get(wallet), tx.get(walletLedgerRef(uid, ledgerId)), tx.get(accountDeletionRef(uid))]);
    if (!freshSession.exists || freshSession.data().status !== 'started') throw new HttpsError('already-exists', 'Quiz session already used.');
    if (lockSnap.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    let balance = Number(walletSnap.data()?.balancePaise || 0);
    if (qualifies && !dailySnap.exists && !ledgerSnap.exists) {
      balance += rewardPaise; credited = rewardPaise;
      tx.set(wallet, { uid, balancePaise: balance, lifetimeEarnedPaise: Number(walletSnap.data()?.lifetimeEarnedPaise || 0) + rewardPaise, lifetimeWithdrawnPaise: Number(walletSnap.data()?.lifetimeWithdrawnPaise || 0), updatedAt: new Date() }, { merge: true });
      tx.create(walletLedgerRef(uid, ledgerId), { transactionId: ledgerId, type: 'verified_quiz_reward', amountPaise: rewardPaise, balanceAfterPaise: balance, status: 'completed', referenceId: sessionId, quizId: session.quizId, createdAt: new Date() });
      tx.create(dailyRef, { uid, sessionId, quizId: session.quizId, createdAt: new Date() });
    }
    tx.update(sessionRef, { status: 'submitted', correctAnswers: correct, durationSeconds, rewardPaise: credited, submittedAt: new Date(), questionData: [] });
  });
  return { success: true, total: questionData.length, correct, percent: Math.round(correct * 100 / questionData.length), durationSeconds, rewardPaise: credited, results };
});

// Referral codes are server-issued. Rewards remain pending until admin review to limit abuse.
exports.getMyReferralCode = onCall({ enforceAppCheck }, async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const codeRef = db.collection('referral_codes').doc(uid);
  const existing = await codeRef.get();
  if (existing.exists) return { code: existing.data().code };
  const code = require('node:crypto').randomBytes(6).toString('hex').toUpperCase();
  await db.runTransaction(async tx => {
    const fresh = await tx.get(codeRef);
    if (fresh.exists) return;
    tx.create(codeRef, { uid, code, createdAt: new Date() });
    tx.create(db.collection('referral_code_lookup').doc(code), { uid, createdAt: new Date() });
  });
  const result = await codeRef.get();
  return { code: result.data().code };
});

exports.redeemReferralCode = onCall({ enforceAppCheck }, async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in.');
  const uid = request.auth.uid;
  const code = requireString(request.data?.code, 'referral code', 20).toUpperCase();
  const lookup = await db.collection('referral_code_lookup').doc(code).get();
  if (!lookup.exists) throw new HttpsError('not-found', 'Referral code not found.');
  const referrerUid = lookup.data().uid;
  if (referrerUid === uid) throw new HttpsError('invalid-argument', 'You cannot use your own referral code.');
  const profile = await db.collection('users').doc(uid).get();
  if (!profile.exists) throw new HttpsError('failed-precondition', 'Create your profile before redeeming a referral.');
  const referralRef = db.collection('referrals').doc(uid);
  await db.runTransaction(async tx => {
    const [existing, lock] = await Promise.all([tx.get(referralRef), tx.get(accountDeletionRef(uid))]);
    if (lock.exists) throw new HttpsError('failed-precondition', 'Account deletion is in progress.');
    if (existing.exists) throw new HttpsError('already-exists', 'A referral code has already been used on this account.');
    tx.create(referralRef, { referredUid: uid, referrerUid, code, status: 'pending_review', createdAt: new Date() });
  });
  return { success: true, status: 'pending_review', message: 'Referral submitted. Rewards are pending eligibility and admin review.' };
});

exports.adminReviewReferral = onCall({ enforceAppCheck }, async request => {
  requireAdmin(request);
  const referredUid = requireString(request.data?.referredUid, 'referred UID', 128);
  const decision = requireString(request.data?.decision, 'decision', 20);
  if (!['approve', 'reject'].includes(decision)) throw new HttpsError('invalid-argument', 'Decision must be approve or reject.');
  const rewardPaise = Number(request.data?.rewardPaise ?? 2000); // ₹20 default, capped at ₹20 each.
  if (!Number.isSafeInteger(rewardPaise) || rewardPaise < 1 || rewardPaise > 2000) throw new HttpsError('invalid-argument', 'Reward must be ₹0.01–₹20 per user.');
  const ref = db.collection('referrals').doc(referredUid);
  await db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError('not-found', 'Referral not found.');
    const r = snap.data();
    if (r.status !== 'pending_review') throw new HttpsError('failed-precondition', 'Referral already reviewed.');
    if (decision === 'reject') { tx.update(ref, { status: 'rejected', reviewedAt: new Date(), reviewedBy: request.auth.uid }); return; }
    const referrerWallet = walletRef(r.referrerUid), referredWallet = walletRef(r.referredUid);
    const referrerLedger = walletLedgerRef(r.referrerUid, `referral_${referredUid}`), referredLedger = walletLedgerRef(r.referredUid, `welcome_${referredUid}`);
    const [rw, ew, rl, el, rlock, elock] = await Promise.all([tx.get(referrerWallet), tx.get(referredWallet), tx.get(referrerLedger), tx.get(referredLedger), tx.get(accountDeletionRef(r.referrerUid)), tx.get(accountDeletionRef(r.referredUid))]);
    if (rlock.exists || elock.exists) throw new HttpsError('failed-precondition', 'One account is being deleted.');
    if (rl.exists || el.exists) throw new HttpsError('already-exists', 'Referral reward already credited.');
    const rb = Number(rw.data()?.balancePaise || 0) + rewardPaise, eb = Number(ew.data()?.balancePaise || 0) + rewardPaise;
    tx.set(referrerWallet, { uid: r.referrerUid, balancePaise: rb, lifetimeEarnedPaise: Number(rw.data()?.lifetimeEarnedPaise || 0) + rewardPaise, updatedAt: new Date() }, { merge: true });
    tx.set(referredWallet, { uid: r.referredUid, balancePaise: eb, lifetimeEarnedPaise: Number(ew.data()?.lifetimeEarnedPaise || 0) + rewardPaise, updatedAt: new Date() }, { merge: true });
    tx.create(referrerLedger, { transactionId: `referral_${referredUid}`, type: 'referral_reward', amountPaise: rewardPaise, balanceAfterPaise: rb, status: 'completed', referenceId: referredUid, createdAt: new Date(), createdBy: request.auth.uid });
    tx.create(referredLedger, { transactionId: `welcome_${referredUid}`, type: 'referral_welcome_reward', amountPaise: rewardPaise, balanceAfterPaise: eb, status: 'completed', referenceId: referredUid, createdAt: new Date(), createdBy: request.auth.uid });
    tx.update(ref, { status: 'approved', rewardPaise, reviewedAt: new Date(), reviewedBy: request.auth.uid });
  });
  return { success: true, status: decision === 'approve' ? 'approved' : 'rejected' };
});

// RazorpayX UPI payout adapter. Configure secrets and verify RazorpayX activation before use.
const razorpayKeyId = defineSecret('RAZORPAYX_KEY_ID');
const razorpayKeySecret = defineSecret('RAZORPAYX_KEY_SECRET');
const razorpayAccountNumber = defineSecret('RAZORPAYX_ACCOUNT_NUMBER');
exports.adminInitiateUpiPayout = onCall({ enforceAppCheck, secrets: [razorpayKeyId, razorpayKeySecret, razorpayAccountNumber], timeoutSeconds: 60 }, async request => {
  requireAdmin(request);
  const requestId = requireString(request.data?.requestId, 'withdrawal request ID', 128);
  const ref = db.collection('withdrawal_requests').doc(requestId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError('not-found', 'Withdrawal request not found.');
  const item = snap.data();
  if (!(item.status === 'pending' || (item.status === 'payout_error' && !item.payoutProviderId))) throw new HttpsError('failed-precondition', `Withdrawal is ${item.status}; only pending requests or unresolved provider submissions can be submitted.`);
  if (!razorpayKeyId.value() || !razorpayKeySecret.value() || !razorpayAccountNumber.value()) throw new HttpsError('failed-precondition', 'RazorpayX secrets are not configured.');
  const profileSnap = await db.collection('users').doc(item.uid).get();
  const profile = profileSnap.data() || {};
  const displayName = String(profile.displayName || profile.name || 'GK Swal User').slice(0, 80);
  const auth = 'Basic ' + Buffer.from(`${razorpayKeyId.value()}:${razorpayKeySecret.value()}`).toString('base64');
  const api = async (path, body, headers = {}) => {
    const response = await fetch(`https://api.razorpay.com/v1${path}`, { method: 'POST', headers: { Authorization: auth, 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body), signal: AbortSignal.timeout(15000) });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(`RazorpayX ${path} failed (${response.status}): ${JSON.stringify(data).slice(0, 500)}`);
    return data;
  };
  // Reserve the request state before external calls. The provider idempotency key prevents duplicate payout.
  await db.runTransaction(async tx => {
    const fresh = await tx.get(ref);
    if (!fresh.exists || !(fresh.data().status === 'pending' || (fresh.data().status === 'payout_error' && !fresh.data().payoutProviderId))) throw new HttpsError('failed-precondition', 'Withdrawal is no longer eligible for provider submission.');
    tx.update(ref, { status: 'processing', payoutProvider: 'razorpayx', payoutAttemptedAt: new Date(), updatedAt: new Date(), reviewedBy: request.auth.uid });
  });
  try {
    const contact = await api('/contacts', { name: displayName, type: 'customer', reference_id: `gkswal_${item.uid}`.slice(0, 40), notes: { uid: item.uid, app: 'GK Swal' } });
    const fund = await api('/fund_accounts', { contact_id: contact.id, account_type: 'vpa', vpa: { address: item.upiId } });
    const payout = await api('/payouts', {
      account_number: razorpayAccountNumber.value(), fund_account_id: fund.id,
      amount: item.amountPaise, currency: 'INR', mode: 'UPI', purpose: 'payout',
      queue_if_low_balance: true, reference_id: `gkswal_${requestId}`.slice(0, 40),
      narration: 'GK Swal reward withdrawal', notes: { requestId, uid: item.uid }
    }, { 'X-Payout-Idempotency': requestId });
    await ref.update({ status: 'processing', payoutProviderId: payout.id, payoutFundAccountId: fund.id, payoutContactId: contact.id, payoutStatus: payout.status || 'queued', updatedAt: new Date() });
    return { success: true, requestId, provider: 'razorpayx', payoutId: payout.id, payoutStatus: payout.status || 'queued', message: 'Payout submitted to provider; do not mark as paid until provider confirms success.' };
  } catch (error) {
    await ref.update({ status: 'payout_error', payoutError: String(error.message || error).slice(0, 500), updatedAt: new Date() }).catch(() => {});
    console.error('RazorpayX payout submission failed', { requestId, error: String(error.message || error).slice(0, 300) });
    throw new HttpsError('unavailable', 'Payout submission failed or is uncertain. Check the RazorpayX dashboard before retrying; do not issue a manual duplicate payment.');
  }
});



exports.adminSyncRazorpayXPayout = onCall({ enforceAppCheck, secrets: [razorpayKeyId, razorpayKeySecret], timeoutSeconds: 30 }, async request => {
  requireAdmin(request);
  const requestId = requireString(request.data?.requestId, 'withdrawal request ID', 128);
  const ref = db.collection('withdrawal_requests').doc(requestId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError('not-found', 'Withdrawal request not found.');
  const item = snap.data();
  if (item.payoutProvider !== 'razorpayx' || !item.payoutProviderId) throw new HttpsError('failed-precondition', 'No RazorpayX payout ID is attached to this request.');
  if (!['processing', 'payout_error'].includes(item.status)) throw new HttpsError('failed-precondition', `Cannot sync payout in ${item.status} state.`);
  if (!razorpayKeyId.value() || !razorpayKeySecret.value()) throw new HttpsError('failed-precondition', 'RazorpayX API secrets are not configured.');
  const auth = 'Basic ' + Buffer.from(`${razorpayKeyId.value()}:${razorpayKeySecret.value()}`).toString('base64');
  const response = await fetch(`https://api.razorpay.com/v1/payouts/${encodeURIComponent(item.payoutProviderId)}`, { headers: { Authorization: auth }, signal: AbortSignal.timeout(12000) });
  const provider = await response.json().catch(() => ({}));
  if (!response.ok) throw new HttpsError('unavailable', `Could not retrieve payout status (${response.status}).`);
  const providerStatus = String(provider.status || 'unknown').toLowerCase();
  await db.runTransaction(async tx => {
    const fresh = await tx.get(ref);
    if (!fresh.exists) throw new HttpsError('not-found', 'Withdrawal request not found.');
    const current = fresh.data();
    if (current.status === 'paid' || current.status === 'failed') return;
    const wallet = walletRef(current.uid);
    const withdrawalLedger = walletLedgerRef(current.uid, current.requestId || requestId);
    const [walletSnap, ledgerSnap] = await Promise.all([tx.get(wallet), tx.get(withdrawalLedger)]);
    if (providerStatus === 'processed') {
      tx.update(ref, { status: 'paid', payoutStatus: providerStatus, paymentReference: provider.utr || provider.id || current.payoutProviderId, providerProcessedAt: provider.processed_at ? new Date(provider.processed_at * 1000) : new Date(), updatedAt: new Date() });
      if (walletSnap.exists) tx.set(wallet, { lifetimeWithdrawnPaise: Number(walletSnap.data().lifetimeWithdrawnPaise || 0) + Number(current.amountPaise || 0), updatedAt: new Date() }, { merge: true });
      if (ledgerSnap.exists) tx.update(withdrawalLedger, { status: 'completed', providerStatus, paymentReference: provider.utr || provider.id || current.payoutProviderId, updatedAt: new Date() });
    } else if (['failed', 'reversed', 'cancelled'].includes(providerStatus)) {
      const refundLedger = walletLedgerRef(current.uid, `refund_${requestId}`);
      const refundSnap = await tx.get(refundLedger);
      const balance = Number(walletSnap.data()?.balancePaise || 0);
      if (!refundSnap.exists) {
        tx.set(wallet, { uid: current.uid, balancePaise: balance + Number(current.amountPaise || 0), lifetimeEarnedPaise: Number(walletSnap.data()?.lifetimeEarnedPaise || 0), lifetimeWithdrawnPaise: Number(walletSnap.data()?.lifetimeWithdrawnPaise || 0), updatedAt: new Date() }, { merge: true });
        tx.create(refundLedger, { transactionId: `refund_${requestId}`, type: 'withdrawal_refund', amountPaise: Number(current.amountPaise || 0), balanceAfterPaise: balance + Number(current.amountPaise || 0), status: 'completed', withdrawalRequestId: requestId, reason: `RazorpayX payout ${providerStatus}`, createdAt: new Date(), createdBy: request.auth.uid });
      }
      if (ledgerSnap.exists) tx.update(withdrawalLedger, { status: 'failed', providerStatus, updatedAt: new Date() });
      tx.update(ref, { status: 'failed', payoutStatus: providerStatus, payoutFailure: String(provider.failure_reason || provider.error_description || providerStatus).slice(0, 300), updatedAt: new Date() });
    } else {
      tx.update(ref, { status: 'processing', payoutStatus: providerStatus, updatedAt: new Date() });
    }
  });
  return { success: true, requestId, payoutStatus: providerStatus, withdrawalStatus: ['processed'].includes(providerStatus) ? 'paid' : ['failed', 'reversed', 'cancelled'].includes(providerStatus) ? 'failed' : 'processing', providerId: provider.id };
});

exports.deleteMyAccount = onCall({ enforceAppCheck, timeoutSeconds: 540, memory: '1GiB' }, async (request) => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Please sign in again before deleting your account.');
  const uid = request.auth.uid;
  const jobRef = accountDeletionRef(uid);
  const leaseId = require('node:crypto').randomUUID();
  const now = Date.now();
  try {
    const acquired = await db.runTransaction(async (tx) => {
      const job = await tx.get(jobRef);
      if (job.exists && job.data().status === 'complete') return { complete: true };
      const leaseUntil = job.exists ? Number(job.data().leaseUntilMs || 0) : 0;
      if (job.exists && job.data().status === 'in_progress' && leaseUntil > now) {
        throw new HttpsError('aborted', 'Account deletion is already running. Retry after it finishes.');
      }
      if (!job.exists) {
        tx.create(jobRef, { uid, status: 'in_progress', stage: 'mark_posts', createdAt: new Date(), updatedAt: new Date(), leaseId, leaseUntilMs: now + 600000 });
      } else {
        tx.update(jobRef, { status: 'in_progress', leaseId, leaseUntilMs: now + 600000, updatedAt: new Date() });
      }
      return { complete: false };
    });
    if (acquired.complete) return { success: true };

    const renewLeaseAndSetStage = async (stage) => {
      await db.runTransaction(async (tx) => {
        const job = await tx.get(jobRef);
        if (!job.exists || job.data().leaseId !== leaseId) throw new HttpsError('aborted', 'Deletion lease expired; retry the deletion request.');
        tx.update(jobRef, { stage, leaseUntilMs: Date.now() + 600000, updatedAt: new Date() });
      });
    };
    const jobSnap = await jobRef.get();
    const savedStage = jobSnap.data()?.stage || 'mark_posts';
    let startAt = DELETION_STAGES.indexOf(savedStage);
    if (startAt < 0) startAt = 0;

    for (let i = startAt; i < DELETION_STAGES.length; i++) {
      const stage = DELETION_STAGES[i];
      await renewLeaseAndSetStage(stage);
      switch (stage) {
        case 'mark_posts': await markOwnedPostsDeleting(uid); break;
        case 'remove_likes': await removeUserInteractions('likes', 'userId', uid); break;
        case 'remove_votes': await removeUserInteractions('votes', 'userId', uid); break;
        case 'remove_comments': await removeUserInteractions('comments', 'authorId', uid); break;
        case 'delete_owned_posts': await deleteOwnedPostsInChunks(uid); break;
        case 'delete_reports': await deleteCollectionGroupByField('reports', 'reporterId', uid); break;
        case 'remove_follows': await removeFollowEdgesForAccount(uid); break;
        case 'delete_wallet_data': await deleteWalletAndWithdrawalData(uid); break;
        case 'delete_profile': await recursivelyDeleteIfExists(db.collection('users').doc(uid)); break;
        case 'delete_auth':
          try { await getAuth(app).deleteUser(uid); }
          catch (error) { if (error.code !== 'auth/user-not-found') throw error; }
          break;
        case 'complete':
          await jobRef.set({ status: 'complete', stage: 'complete', leaseId: null, leaseUntilMs: 0, completedAt: new Date(), updatedAt: new Date() }, { merge: true });
          return { success: true };
      }
    }
    return { success: true };
  } catch (error) {
    console.error('deleteMyAccount failed', { uid, code: error.code || 'unknown' });
    // Keep the durable lock and current stage; release only the execution lease so retry can resume.
    await db.runTransaction(async (tx) => {
      const job = await tx.get(jobRef);
      if (job.exists && job.data().leaseId === leaseId) {
        tx.update(jobRef, { status: 'in_progress', leaseId: null, leaseUntilMs: 0, lastErrorCode: error.code || 'unknown', updatedAt: new Date() });
      }
    }).catch(() => {});
    if (error instanceof HttpsError && error.code === 'aborted') throw error;
    throw new HttpsError('internal', 'Account deletion is still in progress. Retry deletion to continue safely.');
  }
});
