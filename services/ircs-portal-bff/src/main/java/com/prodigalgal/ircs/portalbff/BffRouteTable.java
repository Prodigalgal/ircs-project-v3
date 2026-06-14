package com.prodigalgal.ircs.portalbff;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

class BffRouteTable {

    private final List<BffRoute> routes;

    BffRouteTable(List<BffRoute> routes) {
        this.routes = routes.stream()
                .sorted(Comparator.comparingInt((BffRoute route) -> route.requestPrefix().length()).reversed())
                .toList();
    }

    Optional<ResolvedRoute> resolve(String path) {
        return routes.stream()
                .filter(route -> route.matches(path))
                .findFirst()
                .map(route -> route.resolve(path));
    }
}
