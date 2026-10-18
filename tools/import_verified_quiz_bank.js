#!/usr/bin/env node
/**
 * Import vetted server quiz data using GOOGLE_APPLICATION_CREDENTIALS.
 * Input JSON schema:
 * {"questions":[{"id":"q1","question":"...","options":["..."],"correctIndex":0,"explanation":"..."}],
 *  "quizzes":[{"id":"daily-general","title":"Daily General GK","questionIds":["q1"],"questionCount":10,"rewardPaise":100,"rewardMinPercent":80,"published":true}]}
 * Run only after independently fact-checking every answer. This does not certify factual accuracy.
 */
const fs = require('node:fs');
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const DATABASE_ID = 'ai-studio-android-gkswal-00669d47-2b46-46f6-b28a-6db36873372b';
async function main() {
  const input = process.argv[2];
  if (!input) throw new Error('Usage: GOOGLE_APPLICATION_CREDENTIALS=/secure/service-account.json node tools/import_verified_quiz_bank.js vetted-quiz-bank.json');
  const data = JSON.parse(fs.readFileSync(input, 'utf8'));
  if (!Array.isArray(data.questions) || !Array.isArray(data.quizzes) || data.questions.length < 5) throw new Error('Expected questions and quizzes arrays; at least five questions are required.');
  const questionIds = new Set();
  for (const q of data.questions) {
    if (!/^[A-Za-z0-9_-]{1,100}$/.test(q.id) || questionIds.has(q.id)) throw new Error(`Invalid/duplicate question id: ${q.id}`);
    questionIds.add(q.id);
    if (typeof q.question !== 'string' || q.question.trim().length < 5 || !Array.isArray(q.options) || q.options.length < 2 || q.options.length > 8 || !Number.isInteger(q.correctIndex) || q.correctIndex < 0 || q.correctIndex >= q.options.length || q.options.some(x => typeof x !== 'string' || !x.trim())) throw new Error(`Invalid question record: ${q.id}`);
    if (q.factCheckStatus !== 'VERIFIED' || typeof q.sourceUrl !== 'string' || !q.sourceUrl.startsWith('https://') || typeof q.verifiedBy !== 'string' || !q.verifiedBy.trim() || typeof q.verifiedOn !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(q.verifiedOn)) throw new Error(`Question ${q.id} must have VERIFIED status, an HTTPS source, reviewer, and verification date before importing.`);
  }
  for (const quiz of data.quizzes) {
    if (!/^[A-Za-z0-9_-]{1,100}$/.test(quiz.id) || typeof quiz.title !== 'string' || !Array.isArray(quiz.questionIds) || quiz.questionIds.some(id => !questionIds.has(id))) throw new Error(`Invalid quiz or missing question reference: ${quiz.id}`);
    if (!Number.isInteger(quiz.questionCount) || quiz.questionCount < 5 || quiz.questionCount > 100 || quiz.questionIds.length < quiz.questionCount) throw new Error(`questionCount must be 5–100 and available ids must be sufficient: ${quiz.id}`);
    if (!Number.isInteger(quiz.rewardPaise) || quiz.rewardPaise < 0 || quiz.rewardPaise > 10000 || !Number.isFinite(quiz.rewardMinPercent) || quiz.rewardMinPercent < 50 || quiz.rewardMinPercent > 100) throw new Error(`Invalid reward policy: ${quiz.id}`);
  }
  initializeApp({ credential: applicationDefault() });
  const db = getFirestore(undefined, DATABASE_ID);
  let batch = db.batch(), writes = 0;
  const commitIfNeeded = async () => { if (writes >= 400) { await batch.commit(); batch = db.batch(); writes = 0; } };
  for (const q of data.questions) { batch.set(db.collection('quiz_questions').doc(q.id), { question: q.question.trim(), options: q.options.map(x => x.trim()), correctIndex: q.correctIndex, factCheckStatus: q.factCheckStatus, sourceUrl: q.sourceUrl, reviewedBy: q.verifiedBy.trim(), verifiedOn: q.verifiedOn, explanation: String(q.explanation || '').trim().slice(0, 1000), importedAt: new Date() }); writes++; await commitIfNeeded(); }
  for (const q of data.quizzes) { batch.set(db.collection('published_quizzes').doc(q.id), { title: q.title.trim(), questionIds: q.questionIds, questionCount: q.questionCount, rewardPaise: q.rewardPaise, rewardMinPercent: q.rewardMinPercent, published: q.published === true, updatedAt: new Date() }); writes++; await commitIfNeeded(); }
  if (writes) await batch.commit();
  console.log(`Imported ${data.questions.length} server questions and ${data.quizzes.length} quiz definitions. Import success is not a fact-check certificate.`);
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
