import crypto from 'crypto';
const key = crypto.randomBytes(32);
console.log('APP_CRYPTO_KEY=' + key.toString('base64'));
