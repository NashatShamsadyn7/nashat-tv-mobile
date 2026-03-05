const { initializeApp } = require('firebase/app');
const { getDatabase, ref, set } = require('firebase/database');
const { getAuth, signInAnonymously } = require('firebase/auth');
const fs = require('fs');

const firebaseConfig = {
  apiKey: "AIzaSyB_Lkz_W0VJwLRSk1uMEbCH_29Qu7mKGM",
  authDomain: "nashat-tv.firebaseapp.com",
  databaseURL: "https://nashat-tv-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "nashat-tv",
  storageBucket: "nashat-tv.firebasestorage.app",
  messagingSenderId: "775486339076",
  appId: "1:775486339076:android:77e0cc17f5b9ce0dbb8ec1"
};

const app = initializeApp(firebaseConfig);
const db = getDatabase(app);

async function upload() {
  const data = JSON.parse(fs.readFileSync('channels_firebase_final.json', 'utf-8'));
  console.log(`Channels to upload: ${Object.keys(data).length}`);
  
  // Upload in batches of 50 to avoid hitting limits
  const keys = Object.keys(data);
  const batchSize = 50;
  
  for (let i = 0; i < keys.length; i += batchSize) {
    const batch = {};
    const batchKeys = keys.slice(i, i + batchSize);
    for (const key of batchKeys) {
      batch[key] = data[key];
    }
    
    try {
      // Set each batch under channels/
      const updates = {};
      for (const key of batchKeys) {
        await set(ref(db, `channels/${key}`), data[key]);
      }
      console.log(`Batch ${Math.floor(i/batchSize) + 1}: uploaded ${batchKeys.length} channels (${i+1}-${Math.min(i+batchSize, keys.length)})`);
    } catch (error) {
      console.error(`Batch error at ${i}: ${error.message}`);
    }
  }
  
  console.log('Upload complete!');
  process.exit(0);
}

upload();
