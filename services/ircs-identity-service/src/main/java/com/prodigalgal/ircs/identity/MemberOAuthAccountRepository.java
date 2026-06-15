package com.prodigalgal.ircs.identity;

import java.util.Optional;

interface MemberOAuthAccountRepository {

    Optional<MemberOAuthAccountRecord> findByProviderAndSubject(String provider, String subject);

    MemberOAuthAccountRecord insert(MemberOAuthAccountRecord account);

    MemberOAuthAccountRecord update(MemberOAuthAccountRecord account);
}
