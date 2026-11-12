// File: ircs-frontend/src/utils/powSolver.js
function bufferToHex(buffer) {
    return Array.from(new Uint8Array(buffer))
        .map(b => b.toString(16).padStart(2, '0'))
        .join('');
}

async function sha256Hex(value) {
    const encoder = new TextEncoder();
    const hashBuffer = await crypto.subtle.digest('SHA-256', encoder.encode(value));
    return bufferToHex(hashBuffer);
}

export async function solvePoW(challenge, options = {}) {
    if (!challenge?.id || !challenge?.nonce) {
        throw new Error('Invalid PoW challenge received');
    }

    const scope = challenge.scope || 'admin.login';
    const prefix = '0'.repeat(challenge.difficulty || 0);
    let counter = 0;
    const MAX_ATTEMPTS = 10_000_000;

    while (counter < MAX_ATTEMPTS) {
        const hashHex = await sha256Hex(`${challenge.id}:${scope}:${challenge.nonce}:${counter}`);

        if (hashHex.startsWith(prefix)) {
            const headers = {
                'X-IRCS-POW-Challenge': challenge.id,
                'X-IRCS-POW-Counter': counter.toString(),
                'X-IRCS-POW-Digest': hashHex,
            };

            if (challenge.captchaRequired) {
                headers['X-IRCS-POW-Captcha-Answer'] = options.captchaAnswer?.trim() || '';
            }

            return {
                counter: counter.toString(),
                digest: hashHex,
                headers,
            };
        }

        counter++;

        if (counter % 1000 === 0) {
            await new Promise(resolve => setTimeout(resolve, 0));
        }
    }

    throw new Error('PoW solver timeout: unable to find counter within limit');
}
