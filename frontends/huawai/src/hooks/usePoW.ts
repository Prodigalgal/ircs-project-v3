"use client";

import { useState } from "react";
import { authApi, solvePoWChallenge, type PoWChallenge, type PoWHeaders, type PoWScope } from "@/lib/api/auth";

export function usePoW() {
    const [isCalculating, setIsCalculating] = useState(false);

    const solve = async (
        scope: PoWScope = "portal.login",
        captchaAnswer?: string,
        challengeOverride?: PoWChallenge
    ): Promise<PoWHeaders> => {
        setIsCalculating(true);

        try {
            const challenge = challengeOverride ?? await authApi.getPoWChallenge(scope);
            const startCalc = performance.now();
            const headers = await solvePoWChallenge(challenge, captchaAnswer);
            const duration = (performance.now() - startCalc).toFixed(2);
            console.log(`[PoW] Solved scope=${challenge.scope ?? scope} difficulty=${challenge.difficulty} duration=${duration}ms`);
            return headers;
        } finally {
            setIsCalculating(false);
        }
    };

    return {
        solve,
        isCalculating
    };
}
