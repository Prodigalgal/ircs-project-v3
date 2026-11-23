package com.prodigalgal.ircs.aggregation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SourceRecordGraphClusterer {

    static final double EDGE_THRESHOLD = 0.85;
    static final double CONSISTENCY_THRESHOLD = 0.90;

    private final AggregationMatchingStrategy matchingStrategy;

    public List<SourceRecordAggregationCluster> cluster(List<SourceRecordAggregationRecord> sourceRecords) {
        return cluster(sourceRecords, List.of());
    }

    public List<SourceRecordAggregationCluster> cluster(
            List<SourceRecordAggregationRecord> sourceRecords,
            List<CatalogEntryAggregationCandidate> contextCatalogEntries) {
        List<SourceRecordAggregationRecord> candidates = sourceRecords == null
                ? List.of()
                : sourceRecords.stream()
                        .filter(java.util.Objects::nonNull)
                        .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<CatalogEntryAggregationCandidate> contextCandidates = contextCatalogEntries == null
                ? List.of()
                : contextCatalogEntries.stream()
                        .filter(java.util.Objects::nonNull)
                        .toList();
        if (candidates.size() == 1 && contextCandidates.isEmpty()) {
            SourceRecordAggregationRecord only = candidates.getFirst();
            return List.of(new SourceRecordAggregationCluster(only, List.of(only)));
        }

        Map<UUID, Integer> inputOrder = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            inputOrder.put(candidates.get(i).id(), i);
        }

        Graph<AggregationGraphCandidate, DefaultWeightedEdge> graph =
                new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        List<AggregationGraphCandidate> nodes = new ArrayList<>();
        candidates.stream()
                .map(AggregationGraphCandidate::raw)
                .forEach(nodes::add);
        contextCandidates.stream()
                .map(AggregationGraphCandidate::unified)
                .forEach(nodes::add);
        nodes.forEach(graph::addVertex);
        connectSimilarCandidates(graph, nodes, matchingStrategy.prepareSimilaritySession(nodes));

        ConnectivityInspector<AggregationGraphCandidate, DefaultWeightedEdge> inspector =
                new ConnectivityInspector<>(graph);
        return inspector.connectedSets().stream()
                .flatMap(component -> decomposeToStars(graph, component, inputOrder).stream())
                .sorted(Comparator.comparingInt(cluster -> cluster.members().stream()
                        .map(SourceRecordAggregationRecord::id)
                        .map(inputOrder::get)
                        .min(Integer::compareTo)
                        .orElse(Integer.MAX_VALUE)))
                .toList();
    }

    private void connectSimilarCandidates(
            Graph<AggregationGraphCandidate, DefaultWeightedEdge> graph,
            List<AggregationGraphCandidate> candidates,
            AggregationMatchingStrategy.SimilaritySession similaritySession) {
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                AggregationGraphCandidate left = candidates.get(i);
                AggregationGraphCandidate right = candidates.get(j);
                if (left instanceof AggregationGraphCandidate.UnifiedNode
                        && right instanceof AggregationGraphCandidate.UnifiedNode) {
                    continue;
                }
                double score = similaritySession.calculateSimilarity(left, right);
                if (score >= EDGE_THRESHOLD) {
                    DefaultWeightedEdge edge = graph.addEdge(left, right);
                    if (edge != null) {
                        graph.setEdgeWeight(edge, score);
                    }
                }
            }
        }
    }

    private List<SourceRecordAggregationCluster> decomposeToStars(
            Graph<AggregationGraphCandidate, DefaultWeightedEdge> graph,
            Set<AggregationGraphCandidate> component,
            Map<UUID, Integer> inputOrder) {
        if (component.stream().noneMatch(AggregationGraphCandidate.RawNode.class::isInstance)) {
            return List.of();
        }
        List<SourceRecordAggregationCluster> clusters = new ArrayList<>();
        Set<AggregationGraphCandidate> remaining = new LinkedHashSet<>(sortByInputOrder(component, inputOrder));
        int safeguard = 0;
        int maxIterations = component.size() + 1;

        while (!remaining.isEmpty() && safeguard++ < maxIterations) {
            if (remaining.size() == 1) {
                addClusterIfRaw(clusters, remaining);
                break;
            }

            AggregationGraphCandidate leader = electLeader(remaining, inputOrder);
            List<AggregationGraphCandidate> members = new ArrayList<>();
            members.add(leader);
            remaining.remove(leader);

            for (AggregationGraphCandidate candidate : sortByInputOrder(new HashSet<>(remaining), inputOrder)) {
                double score = edgeScore(graph, leader, candidate);
                // Connected components already guarantee a >= EDGE_THRESHOLD path to the leader, but a
                // path is transitive: A-B and B-C can both clear the bar while A-C does not. Peeling as
                // a star is only meaningful if every member independently agrees with the candidate at
                // merge strength, otherwise a long chain of vaguely similar titles collapses into one
                // cluster (production had a single cluster of 1,870 unrelated fixtures).
                if (score >= CONSISTENCY_THRESHOLD
                        && members.stream().allMatch(member ->
                                edgeScore(graph, member, candidate) >= CONSISTENCY_THRESHOLD)) {
                    members.add(candidate);
                    remaining.remove(candidate);
                }
            }
            addClusterIfRaw(clusters, members);
        }
        return clusters;
    }

    private double edgeScore(Graph<AggregationGraphCandidate, DefaultWeightedEdge> graph,
            AggregationGraphCandidate left, AggregationGraphCandidate right) {
        DefaultWeightedEdge edge = graph.getEdge(left, right);
        // Every retained edge was scored once during graph construction. Missing edges cannot meet
        // the stricter consistency threshold, so peeling need not rebuild title/name fingerprints.
        return edge == null ? 0.0 : graph.getEdgeWeight(edge);
    }

    private AggregationGraphCandidate electLeader(
            Set<AggregationGraphCandidate> candidates,
            Map<UUID, Integer> inputOrder) {
        return candidates.stream()
                .filter(AggregationGraphCandidate.UnifiedNode.class::isInstance)
                .findFirst()
                .orElseGet(() -> candidates.stream()
                .max(Comparator
                        .comparingInt((AggregationGraphCandidate candidate) -> hasAnyExternalId(candidate) ? 1 : 0)
                        .thenComparingInt(this::titleLength)
                        .thenComparingInt(candidate -> -inputOrder.getOrDefault(candidate.id(), Integer.MAX_VALUE)))
                .orElseGet(() -> candidates.iterator().next()));
    }

    private List<AggregationGraphCandidate> sortByInputOrder(
            Set<AggregationGraphCandidate> candidates,
            Map<UUID, Integer> inputOrder) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingInt((AggregationGraphCandidate candidate) -> candidate instanceof AggregationGraphCandidate.UnifiedNode ? 1 : 0)
                        .thenComparingInt(candidate -> inputOrder.getOrDefault(candidate.id(), Integer.MAX_VALUE))
                        .thenComparing(candidate -> candidate.id().toString()))
                .toList();
    }

    private void addClusterIfRaw(
            List<SourceRecordAggregationCluster> clusters,
            Iterable<AggregationGraphCandidate> members) {
        List<SourceRecordAggregationRecord> rawMembers = new ArrayList<>();
        List<UUID> unifiedIds = new ArrayList<>();
        for (AggregationGraphCandidate member : members) {
            if (member instanceof AggregationGraphCandidate.RawNode rawNode) {
                rawMembers.add(rawNode.sourceRecord());
            } else if (member instanceof AggregationGraphCandidate.UnifiedNode unifiedNode) {
                unifiedIds.add(unifiedNode.catalogEntry().id());
            }
        }
        if (rawMembers.isEmpty()) {
            return;
        }
        clusters.add(new SourceRecordAggregationCluster(rawMembers.getFirst(), rawMembers, unifiedIds));
    }

    private boolean hasAnyExternalId(AggregationGraphCandidate candidate) {
        if (candidate instanceof AggregationGraphCandidate.RawNode rawNode) {
            SourceRecordAggregationRecord raw = rawNode.sourceRecord();
            return matchingStrategy.isUsableExternalId("douban", raw.doubanId())
                    || matchingStrategy.isUsableExternalId("tmdb", raw.tmdbId())
                    || matchingStrategy.isUsableExternalId("imdb", raw.imdbId())
                    || matchingStrategy.isUsableExternalId("rt", raw.rottenTomatoesId());
        }
        if (candidate instanceof AggregationGraphCandidate.UnifiedNode unifiedNode) {
            CatalogEntryAggregationCandidate unified = unifiedNode.catalogEntry();
            return matchingStrategy.isUsableExternalId("douban", unified.doubanId())
                    || matchingStrategy.isUsableExternalId("tmdb", unified.tmdbId())
                    || matchingStrategy.isUsableExternalId("imdb", unified.imdbId())
                    || matchingStrategy.isUsableExternalId("rt", unified.rottenTomatoesId());
        }
        return false;
    }

    private int titleLength(AggregationGraphCandidate candidate) {
        String title = null;
        if (candidate instanceof AggregationGraphCandidate.RawNode rawNode) {
            title = rawNode.sourceRecord().title();
        } else if (candidate instanceof AggregationGraphCandidate.UnifiedNode unifiedNode) {
            title = unifiedNode.catalogEntry().title();
        }
        return StringUtils.hasText(title) ? title.length() : 0;
    }

}
