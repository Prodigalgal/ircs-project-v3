package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.metadata.catalog.AuthorityCatalogEvidence.CreditSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
class CatalogPeopleRepository {
    private static final String TMDB = "TMDB";
    private static final String CREDIT_INPUT = """
            with incoming as (
                select * from jsonb_to_recordset(cast(:credits as jsonb)) as credit(
                    season_number integer, episode_number integer, credit_kind text, complete boolean,
                    external_id text, external_credit_id text, credited_name text, original_name text,
                    department text, job text, character_name text, billing_order integer, source_order integer
                )
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    void store(UUID work, List<CreditSet> sets) {
        if (sets.isEmpty()) return;
        var params = Map.of("work", work, "provider", TMDB, "credits", payload(sets));
        storePeople(params);
        pruneReplacedCredits(params);
        upsertCredits(params);
    }

    private void storePeople(Map<String, ?> params) {
        // Reserve new provider IDs in a consistent order. The deferred FK is checked when the work transaction commits.
        jdbc.update(CREDIT_INPUT + """
                insert into catalog_person_external_ids(provider, external_id, person_id)
                select :provider, pending.external_id, uuidv7()
                from (select distinct external_id from incoming) pending
                where not exists (
                    select 1 from catalog_person_external_ids known
                    where known.provider=:provider and known.external_id=pending.external_id
                )
                order by pending.external_id
                on conflict (provider, external_id) do nothing
                """, params);
        jdbc.update(CREDIT_INPUT + """
                , names as (
                    select distinct on (identity.person_id) identity.person_id, fact.credited_name, fact.original_name
                    from incoming fact
                    join catalog_person_external_ids identity on identity.provider=:provider and identity.external_id=fact.external_id
                    order by identity.person_id, fact.season_number nulls first, fact.episode_number nulls first,
                        fact.original_name nulls last, fact.source_order, fact.external_credit_id
                )
                insert into catalog_people(id, display_name, original_name)
                select names.person_id, names.credited_name, names.original_name
                from names left join catalog_people known on known.id=names.person_id
                where known.id is null or (known.display_name, known.original_name) is distinct from
                    (names.credited_name, coalesce(names.original_name,known.original_name))
                order by names.person_id
                on conflict (id) do update set display_name=excluded.display_name,
                    original_name=coalesce(excluded.original_name,catalog_people.original_name),updated_at=now()
                where (catalog_people.display_name,catalog_people.original_name) is distinct from
                    (excluded.display_name,coalesce(excluded.original_name,catalog_people.original_name))
                """, params);
    }

    private void pruneReplacedCredits(Map<String, ?> params) {
        jdbc.update(CREDIT_INPUT + """
                , complete_scopes as (
                    select distinct season_number, episode_number, credit_kind from incoming where complete
                )
                delete from catalog_person_credits existing using complete_scopes scope
                where existing.work_id=:work and existing.provider=:provider and existing.credit_kind=scope.credit_kind
                    and existing.season_number is not distinct from scope.season_number
                    and existing.episode_number is not distinct from scope.episode_number
                    and not exists (
                        select 1 from incoming fact
                        where fact.credit_kind=existing.credit_kind and fact.external_credit_id=existing.external_credit_id
                            and fact.season_number is not distinct from existing.season_number
                            and fact.episode_number is not distinct from existing.episode_number
                    )
                """, params);
    }

    private void upsertCredits(Map<String, ?> params) {
        jdbc.update(CREDIT_INPUT + """
                insert into catalog_person_credits as existing(work_id,season_number,episode_number,person_id,provider,
                    external_credit_id,credit_kind,credited_name,department,job,character_name,billing_order,source_order)
                select :work,fact.season_number,fact.episode_number,identity.person_id,:provider,
                    fact.external_credit_id,fact.credit_kind,fact.credited_name,fact.department,fact.job,
                    fact.character_name,fact.billing_order,fact.source_order
                from incoming fact
                join catalog_person_external_ids identity on identity.provider=:provider and identity.external_id=fact.external_id
                on conflict (work_id,season_number,episode_number,provider,credit_kind,external_credit_id) do update
                    set person_id=excluded.person_id,credited_name=excluded.credited_name,department=excluded.department,
                        job=excluded.job,character_name=excluded.character_name,billing_order=excluded.billing_order,
                        source_order=excluded.source_order,updated_at=now()
                where (existing.person_id,existing.credited_name,existing.department,existing.job,
                        existing.character_name,existing.billing_order,existing.source_order) is distinct from
                    (excluded.person_id,excluded.credited_name,excluded.department,excluded.job,
                        excluded.character_name,excluded.billing_order,excluded.source_order)
                    and exists (
                        select 1 from incoming fact where fact.complete
                            and fact.credit_kind=excluded.credit_kind and fact.external_credit_id=excluded.external_credit_id
                            and fact.season_number is not distinct from excluded.season_number
                            and fact.episode_number is not distinct from excluded.episode_number
                    )
                """, params);
    }

    private String payload(List<CreditSet> sets) {
        var input = mapper.createArrayNode();
        for (var set : sets) {
            for (var fact : set.credits()) {
                input.addObject().put("season_number", set.seasonNumber()).put("episode_number", set.episodeNumber())
                        .put("credit_kind", set.kind().name()).put("complete", set.complete())
                        .put("external_id", fact.tmdbPersonId()).put("external_credit_id", fact.tmdbCreditId())
                        .put("credited_name", fact.creditedName()).put("original_name", fact.originalName())
                        .put("department", fact.department()).put("job", fact.job()).put("character_name", fact.characterName())
                        .put("billing_order", fact.billingOrder()).put("source_order", fact.sourceOrder());
            }
        }
        return input.toString();
    }
}
